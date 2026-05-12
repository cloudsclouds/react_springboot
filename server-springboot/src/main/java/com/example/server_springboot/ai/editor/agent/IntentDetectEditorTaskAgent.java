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
public class IntentDetectEditorTaskAgent implements EditorTaskAgent {
  private static final List<String> SUPPORTED_ACTIONS = List.of("continue", "polish", "summary", "translate", "mermaid", "unknown");

  private final Generation generation;
  private final AiProperties aiProperties;

  @Override
  public String action() {
    return "intent-detect";
  }

  @Override
  public String intent() {
    return "intent-detect";
  }

  @Override
  public String outputType() {
    return "text";
  }

  @Override
  public String resultAction() {
    return "previewOnly";
  }

  @Override
  public String generate(EditorAiExecuteRequest request) {
    String prompt = buildPrompt(request);
    String modelOutput = callModel(prompt);
    return normalizeAction(modelOutput);
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    return Map.of(
        "action", request.getAction(),
        "intent", "auto-detect",
        "supportedActions", SUPPORTED_ACTIONS);
  }

  private String buildPrompt(EditorAiExecuteRequest request) {
    String selectedText = safeText(request.getSelectedText());
    String surroundingContext = safeText(request.getSurroundingContext());
    String chatInput = safeText(request.getChatInput());
    return "你是编辑器 AI 的意图识别器。\n"
        + "请根据用户输入判断最合适的动作，只能从以下六个值中选择一个：continue / polish / summary / translate / mermaid / unknown。\n"
        + "输出要求：\n"
        + "1. 只能输出一个动作英文单词。\n"
        + "2. 不要输出解释、标点、代码块、JSON。\n"
        + "3. 优先根据 chatInput、selectedText 和 surroundingContext 的真实语义判断。\n"
        + "4. 如果诉求是继续往下写、补全后续内容，输出 continue。\n"
        + "5. 如果内容主要是改写、润色、优化表达，输出 polish。\n"
        + "6. 如果内容主要是压缩概括、提炼要点，输出 summary。\n"
        + "7. 如果内容主要是翻译成另一种语言，输出 translate。\n"
        + "8. 如果内容主要是让模型生成流程图、时序图、类图或 Mermaid 语法，输出 mermaid。\n"
        + "9. 如果不属于上述任一类，输出 unknown。\n\n"
        + "chatInput:\n" + chatInput + "\n\n"
        + "selectedText:\n" + selectedText + "\n\n"
        + "surroundingContext:\n" + surroundingContext + "\n\n"
        + "请直接输出一个动作：";
  }

  private String callModel(String prompt) {
    try {
      GenerationParam param = GenerationParam.builder()
          .apiKey(aiProperties.getApiKey())
          .model(aiProperties.getModelName())
          .messages(List.of(
              Message.builder().role(Role.SYSTEM.getValue()).content("You are a strict intent classifier for editor actions.").build(),
              Message.builder().role(Role.USER.getValue()).content(prompt).build()))
          .temperature(0.0f)
          .maxTokens(16)
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
      latch.await(20, TimeUnit.SECONDS);
      return answer.get();
    } catch (Exception e) {
      return "";
    }
  }

  private String normalizeAction(String output) {
    if (!StringUtils.hasText(output)) {
      return "unknown";
    }
    String normalized = output.toLowerCase().trim();
    for (String action : SUPPORTED_ACTIONS) {
      if (normalized.equals(action) || normalized.contains(action)) {
        return action;
      }
    }
    if (normalized.contains("续写") || normalized.contains("继续写") || normalized.contains("continue")) {
      return "continue";
    }
    if (normalized.contains("翻译")) {
      return "translate";
    }
    if (normalized.contains("总结") || normalized.contains("概括")) {
      return "summary";
    }
    if (normalized.contains("流程图") || normalized.contains("mermaid")) {
      return "mermaid";
    }
    return "polish";
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

  private String safeText(String text) {
    return StringUtils.hasText(text) ? text.trim() : "(empty)";
  }
}
