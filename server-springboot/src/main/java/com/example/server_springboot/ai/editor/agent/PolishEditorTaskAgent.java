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
public class PolishEditorTaskAgent implements EditorTaskAgent {
  private final Generation generation;
  private final AiProperties aiProperties;

  @Override
  public String action() {
    return "polish";
  }

  @Override
  public String intent() {
    return "polish";
  }

  @Override
  public String outputType() {
    return "markdown";
  }

  @Override
  public String resultAction() {
    return "replace";
  }

  @Override
  public String generate(EditorAiExecuteRequest request) {
    String prompt = buildPrompt(request);
    return callModel(prompt, request);
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    Map<String, Object> meta = new java.util.HashMap<>();
    meta.put("action", request == null ? null : request.getAction());
    meta.put("entryPoint", request == null ? null : request.getEntryPoint());
    meta.put("selectedTextLength", request == null || request.getSelectedText() == null ? 0 : request.getSelectedText().length());
    meta.put("surroundingContextLength", request == null || request.getSurroundingContext() == null ? 0 : request.getSurroundingContext().length());
    meta.put("chatInputLength", request == null || request.getChatInput() == null ? 0 : request.getChatInput().length());
    return meta;
  }

  private String buildPrompt(EditorAiExecuteRequest request) {
    String selectedText = safeText(request.getSelectedText());
    String surroundingContext = safeText(request.getSurroundingContext());
    String chatInput = safeText(request.getChatInput());
    return "你是编辑器中的润色智能体，只负责润色文本，不要改动原意。\n"
        + "输出要求：\n"
        + "1. 可以使用 Markdown 输出（可保留段落/列表等结构）。\n"
        + "2. 只输出润色后的正文，不要输出解释、前缀、JSON。\n"
        + "2. 保持原文事实不变，优化措辞、语气、结构和可读性。\n"
        + "3. 默认保持和原文一致的语言风格；如果原文是中文，就输出中文。\n"
        + "4. 如果输入过短，请尽量保持简洁自然。\n"
        + "5. 优先使用 chatInput 里的用户需求描述；如果 chatInput 没有明确要求，再结合 selectedText 与 surroundingContext。\n"
        + "6. 如果 selectedText 存在，优先润色 selectedText；否则参考 surroundingContext。\n\n"
        + "chatInput:\n" + chatInput + "\n\n"
        + "selectedText:\n" + selectedText + "\n\n"
        + "surroundingContext:\n" + surroundingContext + "\n\n"
        + "请直接输出润色后的结果：";
  }

  private String callModel(String prompt, EditorAiExecuteRequest request) {
    try {
      GenerationParam param = GenerationParam.builder()
          .apiKey(aiProperties.getApiKey())
          .model(aiProperties.getModelName())
          .messages(List.of(
              Message.builder().role(Role.SYSTEM.getValue()).content("You are a professional editor that rewrites text clearly and naturally.").build(),
              Message.builder().role(Role.USER.getValue()).content(prompt).build()))
          .temperature(aiProperties.getTemperature() == null ? 0.7f : aiProperties.getTemperature().floatValue())
          .maxTokens(aiProperties.getMaxTokens())
          .resultFormat(GenerationParam.ResultFormat.MESSAGE)
          .build();

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<String> answer = new AtomicReference<>("");
      generation.streamCall(param, new ResultCallback<GenerationResult>() {
        @Override
        public void onEvent(GenerationResult result) {
          String content = extractContent(result);
          if (StringUtils.hasText(content)) {
            answer.set(content.trim());
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
      latch.await(60, TimeUnit.SECONDS);
      String output = answer.get();
      return StringUtils.hasText(output) ? output : fallback(request);
    } catch (Exception e) {
      return fallback(request);
    }
  }

  private String fallback(EditorAiExecuteRequest request) {
    String text = request.getSelectedText();
    if (!StringUtils.hasText(text)) {
      text = request.getSurroundingContext();
    }
    return StringUtils.hasText(text) ? text.trim() : "";
  }

  private String safeText(String text) {
    return StringUtils.hasText(text) ? text.trim() : "(empty)";
  }

  private String extractContent(GenerationResult result) {
    if (result == null || result.getOutput() == null || result.getOutput().getChoices() == null || result.getOutput().getChoices().isEmpty()) {
      return "";
    }
    if (result.getOutput().getChoices().get(0) == null || result.getOutput().getChoices().get(0).getMessage() == null) {
      return "";
    }
    String content = result.getOutput().getChoices().get(0).getMessage().getContent();
    return content == null ? "" : content;
  }
}
