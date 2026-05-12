package com.example.server_springboot.ai.editor.agent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.example.server_springboot.ai.config.AiProperties;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
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
    String prompt = "你是续写智能体。请基于用户需求与上下文继续写作。\n"
        + "可以要求：\n"
        + "1. 使用 Markdown 输出（可用标题、列表、引用、代码块）。\n"
        + "2. 只输出续写正文；不要解释你的思路。\n"
        + "3. 与原文风格一致，避免重复已给内容。\n\n"
        + "chatInput:\n" + safe(request.getChatInput()) + "\n\n"
        + "selectedText:\n" + safe(request.getSelectedText()) + "\n\n"
        + "surroundingContext:\n" + safe(request.getSurroundingContext()) + "\n\n"
        + "请直接输出续写内容：";
    return callModel(prompt, request);
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    return new java.util.HashMap<>(Map.of(
        "action", request.getAction() == null ? "" : request.getAction(),
        "intent", intent()));
  }

  private String callModel(String prompt, EditorAiExecuteRequest request) {
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

  private String fallback(EditorAiExecuteRequest request) { return ""; }
  private String safe(String s) { return StringUtils.hasText(s) ? s.trim() : "(empty)"; }
  private String extract(GenerationResult r) {
    if (r == null || r.getOutput() == null || r.getOutput().getChoices() == null || r.getOutput().getChoices().isEmpty()) return "";
    var c = r.getOutput().getChoices().get(0);
    if (c == null || c.getMessage() == null) return "";
    return c.getMessage().getContent() == null ? "" : c.getMessage().getContent();
  }
}
