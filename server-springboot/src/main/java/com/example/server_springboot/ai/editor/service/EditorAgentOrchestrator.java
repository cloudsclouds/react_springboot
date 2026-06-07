package com.example.server_springboot.ai.editor.service;

import com.example.server_springboot.ai.editor.agent.EditorTaskAgent;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteResponse;
import com.example.server_springboot.ai.editor.dto.EditorMemoryContext;
import com.example.server_springboot.ai.editor.dto.EditorOrchestrationResult;
import com.example.server_springboot.ai.editor.dto.EditorRouteDecision;
import com.example.server_springboot.ai.editor.dto.EditorWorkerStat;
import com.example.server_springboot.ai.editor.router.EditorAgentRouter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EditorAgentOrchestrator {
  private static final double FAST_PATH_CONFIDENCE = 0.78d;
  private static final int FAST_PATH_TEXT_LIMIT = 420;
  private static final double CRITIC_PASS_SCORE = 0.72d;

  private final EditorAgentRouter router;
  private final List<EditorTaskAgent> agents;
  private final Executor executor = Executors.newFixedThreadPool(3);

  public EditorOrchestrationResult run(EditorAiExecuteRequest request, EditorMemoryContext memoryContext) {
    String traceId = StringUtils.hasText(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString();
    EditorRouteDecision routeDecision = router.routeDecision(request);
    EditorTaskAgent primaryAgent = router.route(routeDecision);
    String executionMode = decideExecutionMode(request, routeDecision);
    List<EditorTaskAgent> plannedWorkers = planWorkers(primaryAgent, routeDecision, executionMode);

    List<WorkerOutput> outputs = executeWorkers(plannedWorkers, request, memoryContext, executionMode);
    WorkerOutput primaryOutput = outputs.stream()
        .filter(output -> output.agent().action().equalsIgnoreCase(primaryAgent.action()))
        .findFirst()
        .orElseGet(() -> new WorkerOutput(primaryAgent, "", "FAILED", 0L));

    String mergedText = mergeOutputs(primaryAgent, primaryOutput, outputs);
    double criticScore = criticScore(primaryAgent.action(), request, mergedText);
    boolean degraded = criticScore < CRITIC_PASS_SCORE || !StringUtils.hasText(mergedText);

    String finalText = mergedText;
    String finalResultAction = primaryAgent.resultAction();
    String finalOutputType = primaryAgent.outputType();
    if (!StringUtils.hasText(finalText)) {
      finalResultAction = "previewOnly";
      finalOutputType = "text";
      finalText = "这次没有生成出稳定结果。你可以缩小选区，或更明确说明要续写、润色、总结还是翻译。";
      degraded = true;
    }

    List<Map<String, Object>> workerStats = outputs.stream()
        .map(output -> new EditorWorkerStat(
            output.agent().action(),
            output.status(),
            output.latencyMs(),
            0,
            output.output() == null ? 0 : output.output().length()
        ).toMeta())
        .toList();

    Map<String, Object> orchestrationMeta = new LinkedHashMap<>();
    orchestrationMeta.put("traceId", traceId);
    orchestrationMeta.put("executionMode", executionMode);
    orchestrationMeta.put("routeDecision", routeDecision.toMeta());
    orchestrationMeta.put("planSummary", Map.of(
        "workerCount", plannedWorkers.size(),
        "parallelizableWorkers", Math.max(0, plannedWorkers.size() - 1),
        "workerActions", plannedWorkers.stream().map(EditorTaskAgent::action).toList()
    ));
    orchestrationMeta.put("workerStats", workerStats);
    orchestrationMeta.put("criticScore", criticScore);
    orchestrationMeta.put("degraded", degraded);
    orchestrationMeta.put("stageHistory", List.of(
        "INIT",
        "ROUTED:" + routeDecision.getPrimaryIntent(),
        "PLANNED:" + executionMode,
        "EXECUTING:" + plannedWorkers.size(),
        "MERGING",
        degraded ? "DONE_DEGRADED" : "DONE"
    ));

    Map<String, Object> responseMeta = new LinkedHashMap<>();
    responseMeta.putAll(primaryAgent.meta(request));
    responseMeta.putAll(orchestrationMeta);

    EditorAiExecuteResponse response = new EditorAiExecuteResponse(
        primaryAgent.intent(),
        finalOutputType,
        finalText,
        finalResultAction,
        responseMeta
    );
    return new EditorOrchestrationResult(primaryAgent, response, orchestrationMeta);
  }

  private String decideExecutionMode(EditorAiExecuteRequest request, EditorRouteDecision routeDecision) {
    int textLength = textSize(request);
    boolean explicit = routeDecision.isExplicitAction();
    boolean highConfidence = routeDecision.getConfidence() >= FAST_PATH_CONFIDENCE;
    boolean shortText = textLength <= FAST_PATH_TEXT_LIMIT;
    boolean singleIntent = routeDecision.getSecondaryIntents() == null || routeDecision.getSecondaryIntents().isEmpty();
    return explicit || (highConfidence && shortText && singleIntent) ? "fast" : "swarm";
  }

  private List<EditorTaskAgent> planWorkers(EditorTaskAgent primaryAgent, EditorRouteDecision routeDecision, String executionMode) {
    Queue<String> plan = new ArrayDeque<>();
    plan.offer(primaryAgent.action());
    if ("swarm".equalsIgnoreCase(executionMode)) {
      for (String secondary : routeDecision.getSecondaryIntents()) {
        plan.offer(secondary);
      }
      if ("polish".equalsIgnoreCase(primaryAgent.action())) {
        plan.offer("summary");
      }
      if ("continue".equalsIgnoreCase(primaryAgent.action())) {
        plan.offer("polish");
      }
    }

    LinkedHashSet<String> deduped = new LinkedHashSet<>();
    while (!plan.isEmpty()) {
      String next = plan.poll();
      if (StringUtils.hasText(next) && !"intent-detect".equalsIgnoreCase(next)) {
        deduped.add(next);
      }
    }

    List<EditorTaskAgent> result = new ArrayList<>();
    for (String action : deduped) {
      EditorTaskAgent agent = findAgent(action);
      if (agent != null && !"unknown".equalsIgnoreCase(agent.action())) {
        result.add(agent);
      }
    }
    if (result.isEmpty()) {
      result.add(primaryAgent);
    }
    return result;
  }

  private List<WorkerOutput> executeWorkers(List<EditorTaskAgent> workers, EditorAiExecuteRequest request, EditorMemoryContext memoryContext, String executionMode) {
    if (workers.size() <= 1 || "fast".equalsIgnoreCase(executionMode)) {
      return workers.stream().map(agent -> executeWorker(agent, request, memoryContext)).toList();
    }

    List<CompletableFuture<WorkerOutput>> futures = workers.stream()
        .map(agent -> CompletableFuture.supplyAsync(() -> executeWorker(agent, request, memoryContext), executor))
        .toList();
    return futures.stream().map(CompletableFuture::join).toList();
  }

  private WorkerOutput executeWorker(EditorTaskAgent agent, EditorAiExecuteRequest request, EditorMemoryContext memoryContext) {
    long start = System.currentTimeMillis();
    try {
      String output = agent.generate(request, memoryContext);
      long latency = System.currentTimeMillis() - start;
      return new WorkerOutput(agent, output, StringUtils.hasText(output) ? "SUCCESS" : "EMPTY", latency);
    } catch (Exception e) {
      long latency = System.currentTimeMillis() - start;
      return new WorkerOutput(agent, "", "FAILED", latency);
    }
  }

  private String mergeOutputs(EditorTaskAgent primaryAgent, WorkerOutput primaryOutput, List<WorkerOutput> outputs) {
    if (StringUtils.hasText(primaryOutput.output())) {
      return primaryOutput.output().trim();
    }

    return outputs.stream()
        .map(WorkerOutput::output)
        .filter(StringUtils::hasText)
        .map(String::trim)
        .findFirst()
        .orElse("");
  }

  private double criticScore(String action, EditorAiExecuteRequest request, String output) {
    double score = 0d;
    String baseline = firstNonBlank(request.getSelectedText(), request.getSurroundingContext(), request.getChatInput());
    if (StringUtils.hasText(output)) {
      score += 0.45d;
    }
    if (StringUtils.hasText(output) && output.trim().length() >= Math.min(24, Math.max(8, baseline.length() / 4))) {
      score += 0.15d;
    }
    if (StringUtils.hasText(output) && !normalize(output).equals(normalize(baseline))) {
      score += 0.15d;
    }
    if (!StringUtils.hasText(output) || !output.contains("作为AI")) {
      score += 0.1d;
    }
    if ("mermaid".equalsIgnoreCase(action)) {
      if (output.contains("graph") || output.contains("flowchart") || output.contains("sequenceDiagram")) {
        score += 0.15d;
      }
    } else if (StringUtils.hasText(output)) {
      score += 0.15d;
    }
    return Math.min(1d, score);
  }

  private EditorTaskAgent findAgent(String action) {
    return agents.stream()
        .filter(agent -> agent.action().equalsIgnoreCase(action))
        .findFirst()
        .orElse(null);
  }

  private int textSize(EditorAiExecuteRequest request) {
    String all = firstNonBlank(request.getSelectedText(), "") + firstNonBlank(request.getSurroundingContext(), "") + firstNonBlank(request.getChatInput(), "");
    return all.length();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
  }

  private record WorkerOutput(EditorTaskAgent agent, String output, String status, long latencyMs) {
  }
}
