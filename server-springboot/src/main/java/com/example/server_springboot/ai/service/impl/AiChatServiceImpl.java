package com.example.server_springboot.ai.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.example.server_springboot.ai.config.AiProperties;
import com.example.server_springboot.ai.dto.ChatStreamRequest;
import com.example.server_springboot.ai.entity.AiConversation;
import com.example.server_springboot.ai.entity.AiConversationMessage;
import com.example.server_springboot.ai.mapper.AiConversationMapper;
import com.example.server_springboot.ai.mapper.AiConversationMessageMapper;
import com.example.server_springboot.ai.service.AiChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 流式聊天服务实现类
 * 这个类承担的是“对话主流程”能力，核心职责可以拆成四步：
 * 1. 校验会话权限，确保当前用户只能访问自己的会话，避免越权读取或写入别人的对话记录
 * 2. 维护会话上下文，先从 Redis 读取最近的历史消息，Redis 没有时回退到 MySQL，将历史消息和当前用户输入拼成上下文，发给大模型
 * 3. 调用 DashScope 流式接口，大模型每输出一小段内容，就通过 SSE 立刻推送给前端，这样前端能实现“逐字 / 逐段显示”的打字机效果
 * 4. 落库与缓存同步，用户消息先入库，assistant 消息先创建草稿，再在结束时更新状态和内容，同时把最近的会话历史写回 Redis，减少下一轮查询成本
 */
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    /** Redis 中会话历史的 key 前缀。 */
  private static final String REDIS_CONVERSATION_HISTORY_KEY_PREFIX = "ai:conversation:history:";

  /** Redis 中最多保留的历史消息数量，避免缓存过长 */
  private static final int REDIS_HISTORY_LIMIT = 20;

  private final AiConversationMapper aiConversationMapper;
  private final AiConversationMessageMapper aiConversationMessageMapper;
  private final Generation generation;
  private final AiProperties aiProperties;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;

  /**
   * 发起一次流式聊天。
   */
  @Override
  public SseEmitter streamChat(ChatStreamRequest request, Long userId) {
    // 0L 表示不设置超时时间，让前端在长对话场景下可以持续接收流式数据。
    SseEmitter emitter = new SseEmitter(0L);

    // 每次请求都生成一个唯一 requestId，便于日志追踪和数据库关联。
    String requestId = UUID.randomUUID().toString();

    // 使用独立线程执行流式调用，避免阻塞 Web 请求线程。
    new Thread(() -> {
      try {
        // 先校验会话是否存在，并且当前用户是否有权限访问。
        AiConversation conversation = aiConversationMapper.selectById(request.getConversationId());
        if (conversation == null || !conversation.getUserId().equals(userId)) {
          emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", "会话不存在或无权限访问")));
          emitter.complete();
          return;
        }

        // 先把用户消息落库，便于后续上下文拼接和审计。
        AiConversationMessage userMessage = createMessage(
            request.getConversationId(),
            "user",
            request.getContent(),
            "COMPLETED",
            requestId
        );
        aiConversationMessageMapper.insert(userMessage);

        // 从 Redis 优先读取历史，如果 Redis 没有则回退到 MySQL。
        List<AiConversationMessage> history = loadConversationHistory(request.getConversationId());
        // 把当前用户消息加入历史，作为下一轮大模型上下文的一部分。
        history.add(userMessage);
        // 写回 Redis，保持缓存和数据库的最新状态同步。
        cacheHistoryToRedis(request.getConversationId(), history);

        // 把历史消息转换为 DashScope 的 Message 列表。
        List<Message> messages = buildMessages(history);

        // 组装 DashScope 流式调用参数。
        GenerationParam param = GenerationParam.builder()
            .apiKey(aiProperties.getApiKey())
            .model(aiProperties.getModelName())
            .messages(messages)
            .temperature(aiProperties.getTemperature() == null ? null : aiProperties.getTemperature().floatValue())
            .maxTokens(aiProperties.getMaxTokens())
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .build();

        // 先插入一条 assistant 草稿消息，状态为 GENERATING。
        // 这样即使生成中断，也可以保留“已经生成到哪一步”的内容。
        AiConversationMessage assistantMessage = createMessage(
            request.getConversationId(),
            "assistant",
            "",
            "GENERATING",
            requestId
        );
        aiConversationMessageMapper.insert(assistantMessage);

        // 通知前端：开始生成。
        emitter.send(SseEmitter.event().name("message-start").data(jsonEvent("message-start", "", request.getConversationId(), requestId, null, null)));

        // 这里保存 assistant 已经生成到的完整内容。
        StringBuilder assistantContent = new StringBuilder();
        // 记录上一次已经推送给前端的完整内容，用于计算本次增量。
        StringBuilder lastEmittedContent = new StringBuilder();

        // 调用 DashScope 真正的流式接口。
        generation.streamCall(param, new ResultCallback<GenerationResult>() {
          @Override
          public void onEvent(GenerationResult result) {
            try {
              // 从当前结果里提取模型已经输出的内容。
              String currentContent = extractContent(result);
              if (!StringUtils.hasText(currentContent)) {
                return;
              }

              // DashScope 某些流式返回可能会带“累计文本”，所以这里做一次增量裁剪。
              String delta = currentContent;
              if (currentContent.startsWith(lastEmittedContent.toString())) {
                delta = currentContent.substring(lastEmittedContent.length());
              }

              // 如果裁剪后没有新增内容，就不推送。
              if (!StringUtils.hasText(delta)) {
                return;
              }

              // 累积完整内容，用于最终入库。
              assistantContent.append(delta);
              // 更新已经发给前端的完整内容快照。
              lastEmittedContent.setLength(0);
              lastEmittedContent.append(currentContent);

              // 通过 SSE 推送真正的增量片段。
              emitter.send(SseEmitter.event().name("message-delta").data(jsonEvent("message-delta", delta)));
            } catch (Exception e) {
              // 流式回调里发生异常时，交给统一错误处理。
              onError(e);
            }
          }

          @Override
          public void onComplete() {
            try {
              // 生成完成后，写入最终内容和状态。
              assistantMessage.setContent(assistantContent.toString());
              assistantMessage.setStatus("COMPLETED");
              assistantMessage.setUpdatedAt(LocalDateTime.now());
              aiConversationMessageMapper.updateById(assistantMessage);

              // 重新读取历史并把最终 assistant 消息写回 Redis。
              List<AiConversationMessage> refreshedHistory = loadConversationHistory(request.getConversationId());
              refreshedHistory.add(assistantMessage);
              cacheHistoryToRedis(request.getConversationId(), refreshedHistory);

              // 通知前端：本轮消息结束。
              emitter.send(SseEmitter.event().name("message-end").data(jsonEvent("message-end", "", request.getConversationId(), requestId, assistantMessage.getId(), "COMPLETED")));
              emitter.complete();
            } catch (IOException e) {
              emitter.completeWithError(e);
            }
          }

          @Override
          public void onError(Exception e) {
            try {
              // 如果流式过程中出错，也把已生成内容保存下来，并标记失败。
              assistantMessage.setContent(assistantContent.toString());
              assistantMessage.setStatus("FAILED");
              assistantMessage.setUpdatedAt(LocalDateTime.now());
              aiConversationMessageMapper.updateById(assistantMessage);

              // 失败时也刷新一份 Redis，避免缓存和数据库不一致。
              List<AiConversationMessage> refreshedHistory = loadConversationHistory(request.getConversationId());
              refreshedHistory.add(assistantMessage);
              cacheHistoryToRedis(request.getConversationId(), refreshedHistory);

              // 告诉前端本次生成失败。
              emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
          }
        });
      } catch (IOException | ApiException | NoApiKeyException | InputRequiredException e) {
        try {
          emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
        } catch (IOException ignored) {
        }
        emitter.completeWithError(e);
      } catch (Exception e) {
        try {
          emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
        } catch (IOException ignored) {
        }
        emitter.completeWithError(e);
      }
    }).start();

    return emitter;
  }

  /**
   * 构造一条 AI 消息实体。
   *
   */
  private AiConversationMessage createMessage(Long conversationId, String role, String content, String status, String requestId) {
    AiConversationMessage message = new AiConversationMessage();
    message.setConversationId(conversationId);
    message.setRole(role);
    message.setContent(content);
    message.setStatus(status);
    message.setRequestId(requestId);
    message.setCreatedAt(LocalDateTime.now());
    message.setUpdatedAt(LocalDateTime.now());
    return message;
  }

  /**
   * 从 Redis 或 MySQL 读取会话历史。
   *
   * 优先级：
   * 1. 先读 Redis，降低数据库压力；
   * 2. Redis 没有时回退 MySQL；
   * 3. 如果是从 MySQL 读出来的，再回写 Redis，形成缓存闭环。
   */
  private List<AiConversationMessage> loadConversationHistory(Long conversationId) {
    String cacheKey = REDIS_CONVERSATION_HISTORY_KEY_PREFIX + conversationId;
    ListOperations<String, String> listOps = stringRedisTemplate.opsForList();
    List<String> cached = listOps.range(cacheKey, 0, -1);

    // Redis 命中时，直接把 JSON 反序列化成消息列表。
    if (cached != null && !cached.isEmpty()) {
      List<AiConversationMessage> messages = new ArrayList<>();
      for (String item : cached) {
        try {
          AiConversationMessage message = objectMapper.readValue(item, AiConversationMessage.class);
          messages.add(message);
        } catch (JsonProcessingException ignored) {
        }
      }
      if (!messages.isEmpty()) {
        return messages;
      }
    }

    // Redis 没命中时，回退数据库。
    List<AiConversationMessage> dbHistory = aiConversationMessageMapper.selectByConversationId(conversationId);
    cacheHistoryToRedis(conversationId, dbHistory);
    return dbHistory;
  }

  /**
   * 将会话历史写回 Redis。
   *
   * 这里采用 Redis List 存储：每条消息一个 JSON 字符串，按时间顺序 rightPush，只保留最近 N 条，设置 TTL，防止缓存长期占用内存
   */
  private void cacheHistoryToRedis(Long conversationId, List<AiConversationMessage> messages) {
    String cacheKey = REDIS_CONVERSATION_HISTORY_KEY_PREFIX + conversationId;
    ListOperations<String, String> listOps = stringRedisTemplate.opsForList();
    stringRedisTemplate.delete(cacheKey);

    List<AiConversationMessage> recentMessages = messages.size() > REDIS_HISTORY_LIMIT
        ? messages.subList(Math.max(0, messages.size() - REDIS_HISTORY_LIMIT), messages.size())
        : messages;

    for (AiConversationMessage message : recentMessages) {
      try {
        listOps.rightPush(cacheKey, objectMapper.writeValueAsString(message));
      } catch (JsonProcessingException ignored) {
      }
    }

    stringRedisTemplate.expire(cacheKey, Duration.ofHours(2));
  }

  /**
   * 把消息列表转换为 DashScope 的输入消息格式。
   *
   * 规则：先放 system prompt，控制模型回答风格；再按顺序把历史 user / assistant 消息塞进去；system / 非对话角色会被忽略，避免污染上下文。
   */
  private List<Message> buildMessages(List<AiConversationMessage> history) {
    List<Message> messages = new ArrayList<>();
    messages.add(Message.builder().role(Role.SYSTEM.getValue()).content("You are a helpful assistant.").build());

    for (AiConversationMessage message : history) {
      if (!StringUtils.hasText(message.getContent())) {
        continue;
      }
      if (!"user".equalsIgnoreCase(message.getRole()) && !"assistant".equalsIgnoreCase(message.getRole())) {
        continue;
      }
      messages.add(Message.builder().role(message.getRole()).content(message.getContent()).build());
    }
    return messages;
  }

  /**
   * 从 DashScope 的流式结果中提取当前文本内容。
   *
   */
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

  /**
   * 生成简单的 SSE JSON 事件体。
   *
   * 当前为了减少依赖，这里没有再额外引入 JSON 工具类，而是手工拼接一个小型 JSON 字符串。
   * 只要字段值经过转义，就可以安全传给前端解析。
   */
  private String jsonEvent(String type, String message) {
    return jsonEvent(type, message, null, null, null, null);
  }

  /**
   * 通用 SSE JSON 构造器。
   *
   * 支持在不同阶段输出不同字段：
   * - message-start：conversationId / requestId
   * - message-delta：content
   * - message-end：conversationId / requestId / messageId / status
   * - message-error：content
   */
  private String jsonEvent(String type, String content, Long conversationId, String requestId, Long messageId, String status) {
    StringBuilder json = new StringBuilder("{");
    json.append("\"type\":").append(jsonString(type));
    if (content != null) {
      json.append(",\"content\":").append(jsonString(content));
    }
    if (conversationId != null) {
      json.append(",\"conversationId\":").append(conversationId);
    }
    if (requestId != null) {
      json.append(",\"requestId\":").append(jsonString(requestId));
    }
    if (messageId != null) {
      json.append(",\"messageId\":").append(messageId);
    }
    if (status != null) {
      json.append(",\"status\":").append(jsonString(status));
    }
    json.append("}");
    return json.toString();
  }

  /**
   * 把普通字符串包装成 JSON 字符串字面量。
   */
  private String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    return '"' + escapeJson(value) + '"';
  }

  /**
   * 最小化 JSON 转义，避免 SSE 事件体中的引号、换行等字符破坏 JSON 结构。
   */
  private String escapeJson(String value) {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
