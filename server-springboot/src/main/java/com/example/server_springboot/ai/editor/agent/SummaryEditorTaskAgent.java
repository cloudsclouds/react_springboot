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
public class SummaryEditorTaskAgent implements EditorTaskAgent {
  private final Generation generation;
  private final AiProperties aiProperties;

  @Override
  public String action() { return "summary"; }

  @Override
  public String intent() { return "summary"; }

  @Override
  public String outputType() { return "markdown"; }

  @Override
  public String resultAction() { return "appendBlock"; }

  @Override
  public String generate(EditorAiExecuteRequest request) {
    String prompt = "你是总结智能体。请提炼核心信息，输出结构化总结。\n"
        + "要求：\n"
        + "1. 可以使用 Markdown 输出，优先使用列表组织要点。\n"
        + "2. 优先依据 chatInput 指令；简洁、准确、不杜撰。\n"
        + "3. 只输出总结正文，不要解释过程。\n\n"
        + "chatInput:\n" + safe(request.getChatInput()) + "\n\n"
        + "selectedText:\n" + safe(request.getSelectedText()) + "\n\n"
        + "surroundingContext:\n" + safe(request.getSurroundingContext()) + "\n\n"
        + "请输出总结：";
    return callModel(prompt);
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    return new java.util.HashMap<>(Map.of(
        "action", request.getAction() == null ? "" : request.getAction(),
        "intent", intent()));
  }

  private String callModel(String prompt) {
    try {
      GenerationParam param = GenerationParam.builder()
          .apiKey(aiProperties.getApiKey())
          .model(aiProperties.getModelName())
          .messages(List.of(
              Message.builder().role(Role.SYSTEM.getValue()).content("You summarize content into key points.").build(),
              Message.builder().role(Role.USER.getValue()).content(prompt).build()))
          .temperature(0.4f)
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
      return ans.get();
    } catch (Exception e) {
      return "";
    }
  }

  private String safe(String s) { return StringUtils.hasText(s) ? s.trim() : "(empty)"; }
  private String extract(GenerationResult r) {
    if (r == null || r.getOutput() == null || r.getOutput().getChoices() == null || r.getOutput().getChoices().isEmpty()) return "";
    var c = r.getOutput().getChoices().get(0);
    if (c == null || c.getMessage() == null) return "";
    return c.getMessage().getContent() == null ? "" : c.getMessage().getContent();
  }
}
