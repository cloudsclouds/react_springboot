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

@Component
@RequiredArgsConstructor
public class TranslateEditorTaskAgent implements EditorTaskAgent {
  private final Generation generation;
  private final AiProperties aiProperties;

  @Override
  public String action() { return "translate"; }

  @Override
  public String intent() { return "translate"; }

  @Override
  public String outputType() { return "markdown"; }

  @Override
  public String resultAction() { return "replace"; }

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
    builder.append("你是翻译智能体。\n")
        .append("要求：\n")
        .append("1. 可以使用 Markdown 输出，保持原段落/列表/标题层级结构。\n")
        .append("2. 仅输出翻译结果，不输出解释；术语前后一致。\n")
        .append("3. 若 chatInput 中指定了目标语言，请严格遵守。\n\n");
    appendMemory(builder, memoryContext);
    builder.append("chatInput:\n").append(safe(request.getChatInput())).append("\n\n")
        .append("selectedText:\n").append(safe(request.getSelectedText())).append("\n\n")
        .append("surroundingContext:\n").append(safe(request.getSurroundingContext())).append("\n\n")
        .append("请输出翻译结果：");
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
    try {
      GenerationParam param = GenerationParam.builder()
          .apiKey(aiProperties.getApiKey())
          .model(aiProperties.getModelName())
          .messages(List.of(
              Message.builder().role(Role.SYSTEM.getValue()).content("You are a precise translator.").build(),
              Message.builder().role(Role.USER.getValue()).content(prompt).build()))
          .temperature(0.2f)
          .maxTokens(aiProperties.getMaxTokens())
          .resultFormat(GenerationParam.ResultFormat.MESSAGE)
          .build();
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> ans = new AtomicReference<>("");
      generation.streamCall(param, new ResultCallback<GenerationResult>() {
        @Override public void onEvent(GenerationResult r) { String c = extract(r); if (StringUtils.hasText(c)) ans.set(c.trim()); }
        @Override public void onComplete() { latch.countDown(); }
        @Override public void onError(Exception e) { latch.countDown(); }
      });
      latch.await(60, TimeUnit.SECONDS);
      return StringUtils.hasText(ans.get()) ? ans.get() : fallback(request);
    } catch (Exception e) {
      return fallback(request);
    }
  }

  private String fallback(EditorAiExecuteRequest request) {
    return StringUtils.hasText(request.getSelectedText()) ? request.getSelectedText().trim() : "";
  }

  private String safe(String s) { return StringUtils.hasText(s) ? s.trim() : "(empty)"; }
  private String extract(GenerationResult r) {
    if (r == null || r.getOutput() == null || r.getOutput().getChoices() == null || r.getOutput().getChoices().isEmpty()) return "";
    var c = r.getOutput().getChoices().get(0);
    if (c == null || c.getMessage() == null) return "";
    return c.getMessage().getContent() == null ? "" : c.getMessage().getContent();
  }
}
