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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 流式聊天服务实现类。
 *
 * 主要职责：
 * 1. 校验会话权限；
 * 2. 读取并缓存会话历史；
 * 3. 调用 DashScope 流式接口；
 * 4. 通过 SSE 向前端推送增量内容；
 * 5. 在生成结束或中断时同步更新数据库和 Redis。
 */
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

  /** Redis 中会话历史的 key 前缀。 */
  private static final String REDIS_CONVERSATION_HISTORY_KEY_PREFIX = "ai:conversation:history:";

  /** Redis 中保存的会话历史最多条数，避免缓存过长。 */
  private static final int REDIS_HISTORY_LIMIT = 20;

  /** Redis 中停止生成标记的 key 前缀。 */
  private static final String REDIS_STOP_KEY_PREFIX = "ai:chat:stop:";

  /**
   * 保存“当前正在生成中的请求”，用于 stop 接口触发后快速定位。
   * key = conversationId，value = 当前 requestId。
   */
  private static final ConcurrentHashMap<Long, String> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

  private final AiConversationMapper aiConversationMapper;
  private final AiConversationMessageMapper aiConversationMessageMapper;
  private final Generation generation;
  private final AiProperties aiProperties;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;

  /**
   * 发起一次流式聊天。
   *
   * 处理流程：
   * 1. 校验会话归属；
   * 2. 保存用户消息；
   * 3. 读取历史并拼接上下文；
   * 4. 创建 assistant 草稿消息；
   * 5. DashScope 真正流式输出；
   * 6. SSE 推送增量；
   * 7. 结束后落库并刷新 Redis。
   */
  @Override
  public SseEmitter streamChat(ChatStreamRequest request, Long userId) {
    SseEmitter emitter = new SseEmitter(0L);
    String requestId = UUID.randomUUID().toString();
    Long conversationId = request.getConversationId();

    // 先把当前对话的 requestId 记录下来，stop 接口会用到。
    ACTIVE_REQUESTS.put(conversationId, requestId);
    // 清理旧的 stop 标识，避免上一次停止影响下一次请求。
    stringRedisTemplate.delete(stopKey(conversationId));

    new Thread(() -> {
      try {
        AiConversation conversation = aiConversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
          emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", "会话不存在或无权限访问")));
          emitter.complete();
          cleanupActiveRequest(conversationId, requestId);
          return;
        }

        AiConversationMessage userMessage = createMessage(conversationId, "user", request.getContent(), "COMPLETED", requestId);
        aiConversationMessageMapper.insert(userMessage);

        List<AiConversationMessage> history = loadConversationHistory(conversationId);
        history.add(userMessage);
        cacheHistoryToRedis(conversationId, history);

        List<Message> messages = buildMessages(history);

        GenerationParam param = GenerationParam.builder()
            .apiKey(aiProperties.getApiKey())
            .model(aiProperties.getModelName())
            .messages(messages)
            .temperature(aiProperties.getTemperature() == null ? null : aiProperties.getTemperature().floatValue())
            .maxTokens(aiProperties.getMaxTokens())
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .build();

        AiConversationMessage assistantMessage = createMessage(conversationId, "assistant", "", "GENERATING", requestId);
        aiConversationMessageMapper.insert(assistantMessage);

        emitter.send(SseEmitter.event().name("message-start").data(jsonEvent("message-start", "", conversationId, requestId, null, null)));

        StringBuilder assistantContent = new StringBuilder();
        StringBuilder lastEmittedContent = new StringBuilder();
        AtomicBoolean stopped = new AtomicBoolean(false);

        generation.streamCall(param, new ResultCallback<GenerationResult>() {
          @Override
          public void onEvent(GenerationResult result) {
            try {
              if (isStopped(conversationId, requestId)) {
                stopped.set(true);
                onComplete();
                return;
              }

              String currentContent = extractContent(result);
              if (!StringUtils.hasText(currentContent)) {
                return;
              }

              String delta = currentContent;
              if (currentContent.startsWith(lastEmittedContent.toString())) {
                delta = currentContent.substring(lastEmittedContent.length());
              }

              if (!StringUtils.hasText(delta)) {
                return;
              }

              assistantContent.append(delta);
              lastEmittedContent.setLength(0);
              lastEmittedContent.append(currentContent);
              emitter.send(SseEmitter.event().name("message-delta").data(jsonEvent("message-delta", delta)));
            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onComplete() {
            try {
              assistantMessage.setContent(assistantContent.toString());
              assistantMessage.setUpdatedAt(LocalDateTime.now());
              if (stopped.get()) {
                assistantMessage.setStatus("STOPPED");
              } else {
                assistantMessage.setStatus("COMPLETED");
              }
              aiConversationMessageMapper.updateById(assistantMessage);

              List<AiConversationMessage> refreshedHistory = loadConversationHistory(conversationId);
              refreshedHistory.add(assistantMessage);
              cacheHistoryToRedis(conversationId, refreshedHistory);

              String status = stopped.get() ? "STOPPED" : "COMPLETED";
              String eventType = stopped.get() ? "message-stop" : "message-end";
              emitter.send(SseEmitter.event().name(eventType).data(jsonEvent(eventType, "", conversationId, requestId, assistantMessage.getId(), status)));
              emitter.complete();
            } catch (IOException e) {
              emitter.completeWithError(e);
            } finally {
              cleanupActiveRequest(conversationId, requestId);
              stringRedisTemplate.delete(stopKey(conversationId));
            }
          }

          @Override
          public void onError(Exception e) {
            try {
              assistantMessage.setContent(assistantContent.toString());
              assistantMessage.setStatus("FAILED");
              assistantMessage.setUpdatedAt(LocalDateTime.now());
              aiConversationMessageMapper.updateById(assistantMessage);

              List<AiConversationMessage> refreshedHistory = loadConversationHistory(conversationId);
              refreshedHistory.add(assistantMessage);
              cacheHistoryToRedis(conversationId, refreshedHistory);

              emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
            } catch (IOException ignored) {
            } finally {
              cleanupActiveRequest(conversationId, requestId);
              stringRedisTemplate.delete(stopKey(conversationId));
            }
            emitter.completeWithError(e);
          }
        });
      } catch (IOException | ApiException | NoApiKeyException | InputRequiredException e) {
        try {
          emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
        } catch (IOException ignored) {
        } finally {
          cleanupActiveRequest(conversationId, requestId);
          stringRedisTemplate.delete(stopKey(conversationId));
        }
        emitter.completeWithError(e);
      } catch (Exception e) {
        try {
          emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
        } catch (IOException ignored) {
        } finally {
          cleanupActiveRequest(conversationId, requestId);
          stringRedisTemplate.delete(stopKey(conversationId));
        }
        emitter.completeWithError(e);
      }
    }).start();

    return emitter;
  }

  /**
   * 停止指定会话的生成。
   *
   * 这里的策略是：
   * 1. 写入 Redis stop 标记；
   * 2. 生成中的回调每次收到增量时都会检查该标记；
   * 3. 一旦发现停止标记，就尽快结束生成流程；
   * 4. 已生成内容会照常补写到 MySQL 和 Redis。
   */
  @Override
  public void stopGeneration(Long conversationId, Long userId) {
    AiConversation conversation = aiConversationMapper.selectById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
      throw new IllegalArgumentException("会话不存在或无权限停止生成");
    }

    stringRedisTemplate.opsForValue().set(stopKey(conversationId), "1", Duration.ofMinutes(10));
  }

  private boolean isStopped(Long conversationId, String requestId) {
    String currentRequestId = ACTIVE_REQUESTS.get(conversationId);
    if (currentRequestId == null || !currentRequestId.equals(requestId)) {
      return true;
    }
    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(stopKey(conversationId)));
  }

  private void cleanupActiveRequest(Long conversationId, String requestId) {
    ACTIVE_REQUESTS.remove(conversationId, requestId);
  }

  private String stopKey(Long conversationId) {
    return REDIS_STOP_KEY_PREFIX + conversationId;
  }

  /**
   * 构造一条 AI 消息实体。
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
   */
  private List<AiConversationMessage> loadConversationHistory(Long conversationId) {
    String cacheKey = REDIS_CONVERSATION_HISTORY_KEY_PREFIX + conversationId;
    ListOperations<String, String> listOps = stringRedisTemplate.opsForList();
    List<String> cached = listOps.range(cacheKey, 0, -1);
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

    List<AiConversationMessage> dbHistory = aiConversationMessageMapper.selectByConversationId(conversationId);
    cacheHistoryToRedis(conversationId, dbHistory);
    return dbHistory;
  }

  /**
   * 将会话历史写回 Redis。
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
   */
  private String jsonEvent(String type, String message) {
    return jsonEvent(type, message, null, null, null, null);
  }

  /**
   * 通用 SSE JSON 构造器。
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

  private String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    return '"' + escapeJson(value) + '"';
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
