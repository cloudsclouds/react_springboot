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

/**
 * 续写智能体
 */
@Component
@RequiredArgsConstructor
public class ContinueEditorTaskAgent implements EditorTaskAgent {
  private final Generation generation;
  private final AiProperties aiProperties;

  // 动作
  @Override
  public String action() { return "continue"; }

  // 意图
  @Override
  public String intent() { return "continue"; }

  // 输出类型
  @Override
  public String outputType() { return "markdown"; }

  // 回填动作
  @Override
  public String resultAction() { return "insertAfter"; }

  // 生成
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
    // 调用模型
    return callModel(prompt, request);
  }

  @Override
  public Map<String, Object> meta(EditorAiExecuteRequest request) {
    return new java.util.HashMap<>(Map.of(
        "action", request.getAction() == null ? "" : request.getAction(),
        "intent", intent()));
  }

  /**
   * 调用模型
   *
   * @param prompt 提示词
   * @param request 请求
   * @return 生成结果
   */
  private String callModel(String prompt, EditorAiExecuteRequest request) {
    // 创建一个倒计时锁，用于等待生成结果
    CountDownLatch latch = new CountDownLatch(1);
    // 创建一个原子引用，用于存储生成结果，初始值为空字符串
    AtomicReference<String> answerRef = new AtomicReference<>("");

    try {
      // 构建生成参数
      GenerationParam param =
          GenerationParam.builder()
              .apiKey(aiProperties.getApiKey()) // 设置 API 密钥
              .model(aiProperties.getModelName()) // 设置模型名称
              .messages(  // 设置消息
                  List.of(
                      Message.builder()
                          .role(Role.SYSTEM.getValue())
                          .content("You continue writing with consistent style.")
                          .build(),
                      Message.builder()
                          .role(Role.USER.getValue())
                          .content(prompt)
                          .build())) 
              .temperature(  // 设置温度
                  aiProperties.getTemperature() == null
                      ? 0.7f
                      : aiProperties.getTemperature().floatValue())
              .maxTokens(aiProperties.getMaxTokens()) // 设置最大令牌数
              .resultFormat(GenerationParam.ResultFormat.MESSAGE) // 设置结果格式
              .build();

      // 调用模型
      generation.streamCall(  // 流式调用
          param,
          new ResultCallback<>() {  // 结果回调
            @Override
            public void onEvent(GenerationResult result) {  // 事件回调
              // 提取生成结果的内容
              String content = extract(result);
              // 如果生成结果的内容不为空，则设置生成结果
              if (StringUtils.hasText(content)) {
                answerRef.set(content.trim());
              }
            }

            // 完成回调
            @Override
            public void onComplete() {
              latch.countDown();
            }

            // 错误回调
            @Override
            public void onError(Exception e) {
              latch.countDown();
            }
          });

      // 等待生成结果，如果生成结果为空，则返回空字符串
      boolean finished = latch.await(30, TimeUnit.SECONDS);
      // 如果生成结果为空，则返回空字符串
      if (!finished || !StringUtils.hasText(answerRef.get())) {
        return fallback(request);
      }
      // 返回生成结果
      return answerRef.get();
    } catch (Exception e) {
      // 错误回调
      return fallback(request);
    }
  }
  /**
   * 回退
   * @param request 请求
   * @return 回退结果
   */
  private String fallback(EditorAiExecuteRequest request) {
    return "";
  }

  /**
   * 安全处理字符串，如果字符串为空，则返回空字符串
   * @param s 字符串
   * @return 安全处理后的字符串
   */
  private String safe(String s) {
    return StringUtils.hasText(s) ? s.trim() : "(empty)";
  }

  /**
   * 提取生成结果的内容，如果生成结果为空，则返回空字符串
   * @param r 生成结果
   * @return 生成结果的内容
   */
  private String extract(GenerationResult r) {
    // 如果生成结果为空，则返回空字符串
    if (r == null
        || r.getOutput() == null
        || r.getOutput().getChoices() == null
        || r.getOutput().getChoices().isEmpty()) {
      return "";
    }
    // 获取生成结果的第一个选择，如果选择为空，则返回空字符串
    var choice = r.getOutput().getChoices().get(0);
    if (choice == null || choice.getMessage() == null) {
      return "";
    }
    return choice.getMessage().getContent() == null ? "" : choice.getMessage().getContent();
  }
}
