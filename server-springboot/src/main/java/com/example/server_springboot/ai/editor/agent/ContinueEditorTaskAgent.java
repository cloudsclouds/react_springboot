package com.example.server_springboot.ai.editor.agent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.example.server_springboot.ai.config.AiProperties;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorMemoryContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 续写智能体
 */
@Component
@RequiredArgsConstructor
public class ContinueEditorTaskAgent implements EditorTaskAgent {
  private final Generation generation;
  private final AiProperties aiProperties;

  @Override
  public String action() { return "continue"; }

  @Override
  public String intent() { return "continue"; }

  @Override
  public String outputType() { return "markdown"; }

  @Override
  public String resultAction() { return "insertAfter"; }

  @Override
  public String generate(EditorAiExecuteRequest request) {
    return callModel(buildPrompt(request), request);
  }

  @Override
  public String generate(EditorAiExecuteRequest request, EditorMemoryContext memoryContext) {
    return callModel(buildPrompt(request, memoryContext), request);
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    return new java.util.HashMap<>(Map.of(
        "action", request.getAction() == null ? "" : request.getAction(),
        "intent", intent()));
  }

  private String buildPrompt(EditorAiExecuteRequest request) {
    return buildPrompt(request, null);
  }

  private String buildPrompt(EditorAiExecuteRequest request, EditorMemoryContext memoryContext) {
    StringBuilder builder = new StringBuilder();
    builder.append("你是续写智能体。请基于用户需求与上下文继续写作。\n")
        .append("可以要求：\n")
        .append("1. 使用 Markdown 输出（可用标题、列表、引用、代码块）。\n")
        .append("2. 只输出续写正文；不要解释你的思路。\n")
        .append("3. 与原文风格一致，避免重复已给内容。\n\n");
    appendMemory(builder, memoryContext);
    builder.append("chatInput:\n").append(safe(request.getChatInput())).append("\n\n")
        .append("selectedText:\n").append(safe(request.getSelectedText())).append("\n\n")
        .append("surroundingContext:\n").append(safe(request.getSurroundingContext())).append("\n\n")
        .append("请直接输出续写内容：");
    return builder.toString();
  }

  private void appendMemory(StringBuilder builder, EditorMemoryContext memoryContext) {
    if (memoryContext == null) {
      return;
    }
    if (StringUtils.hasText(memoryContext.getRecentWindow())) {
      builder.append("L1 最近原文:\n").append(memoryContext.getRecentWindow()).append("\n\n");
    }
    if (StringUtils.hasText(memoryContext.getRollingSummary())) {
      builder.append("L2 滚动摘要:\n").append(memoryContext.getRollingSummary()).append("\n\n");
    }
    if (memoryContext.getLongTermMemories() != null && !memoryContext.getLongTermMemories().isEmpty()) {
      builder.append("L3 长期记忆:\n");
      memoryContext.getLongTermMemories().forEach(memory -> builder.append("- ")
          .append(StringUtils.hasText(memory.getSummary()) ? memory.getSummary() : memory.getContent())
          .append(" (confidence=").append(memory.getConfidence() == null ? "" : memory.getConfidence()).append(")\n"));
      builder.append("\n");
    }
  }

  private String callModel(String prompt, EditorAiExecuteRequest request) {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> answerRef = new AtomicReference<>("");

    try {
      GenerationParam param = GenerationParam.builder()
          .apiKey(aiProperties.getApiKey())
          .model(aiProperties.getModelName())
          .messages(List.of(
              Message.builder().role(Role.SYSTEM.getValue()).content("You continue writing with consistent style.").build(),
              Message.builder().role(Role.USER.getValue()).content(prompt).build()))
          .temperature(aiProperties.getTemperature() == null ? 0.7f : aiProperties.getTemperature().floatValue())
          .maxTokens(aiProperties.getMaxTokens())
          .resultFormat(GenerationParam.ResultFormat.MESSAGE)
          .build();

      generation.streamCall(param, new ResultCallback<>() {
        @Override
        public void onEvent(GenerationResult result) {
          String content = extract(result);
          if (StringUtils.hasText(content)) {
            answerRef.set(content.trim());
          }
        }

        @Override
        public void onComplete() {
          latch.countDown();
        }

        @Override
        public void onError(Exception e) {
          latch.countDown();
        }
      });

      boolean finished = latch.await(30, TimeUnit.SECONDS);
      if (!finished || !StringUtils.hasText(answerRef.get())) {
        return fallback(request);
      }
      return answerRef.get();
    } catch (Exception e) {
      return fallback(request);
    }
  }

  private String fallback(EditorAiExecuteRequest request) {
    return "";
  }

  private String safe(String s) {
    return StringUtils.hasText(s) ? s.trim() : "(empty)";
  }

  private String extract(GenerationResult r) {
    if (r == null || r.getOutput() == null || r.getOutput().getChoices() == null || r.getOutput().getChoices().isEmpty()) {
      return "";
    }
    var choice = r.getOutput().getChoices().get(0);
    if (choice == null || choice.getMessage() == null) {
      return "";
    }
    return choice.getMessage().getContent() == null ? "" : choice.getMessage().getContent();
  }
}
