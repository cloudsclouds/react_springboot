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
import com.example.server_springboot.ai.dto.ChatCitationDto;
import com.example.server_springboot.ai.dto.ChatRequest;
import com.example.server_springboot.ai.dto.ChatResponse;
import com.example.server_springboot.ai.dto.ChatStreamRequest;
import com.example.server_springboot.ai.entity.AiConversation;
import com.example.server_springboot.ai.entity.AiConversationMessage;
import com.example.server_springboot.ai.mapper.AiConversationMapper;
import com.example.server_springboot.ai.mapper.AiConversationMessageMapper;
import com.example.server_springboot.ai.service.AiChatService;
import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.service.KnowledgeArticleChunkService;
import com.example.server_springboot.kb.service.KnowledgeArticleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

  private static final String REDIS_CONVERSATION_HISTORY_KEY_PREFIX = "ai:conversation:history:";
  private static final int REDIS_HISTORY_LIMIT = 20;
  private static final String REDIS_STOP_KEY_PREFIX = "ai:chat:stop:";
  private static final ConcurrentHashMap<Long, String> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

  private final AiConversationMapper aiConversationMapper;
  private final AiConversationMessageMapper aiConversationMessageMapper;
  private final Generation generation;
  private final AiProperties aiProperties;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final KnowledgeArticleService knowledgeArticleService;
  private final KnowledgeArticleChunkService knowledgeArticleChunkService;

  @Override
  public ChatResponse chat(ChatRequest request, Long userId) {
    AiConversation conversation = verifyConversation(request.getConversationId(), userId);
    List<AiConversationMessage> history = loadConversationHistory(conversation.getId());

    AiConversationMessage userMessage = createMessage(conversation.getId(), "user", request.getMessage(), "COMPLETED", request.getRequestId());
    aiConversationMessageMapper.insert(userMessage);
    history.add(userMessage);
    cacheHistoryToRedis(conversation.getId(), history);

    List<ChatCitationDto> citations = buildCitations(request.getUseRag(), request.getMessage(), request.getArticleId(), request.getTopK(), userId);
    String prompt = buildPrompt(request.getMessage(), citations);
    List<Message> messages = buildMessages(history, prompt);
    String answer = generateOnce(messages);
    return new ChatResponse(answer, citations, !citations.isEmpty());
  }

  @Override
  public SseEmitter streamChat(ChatStreamRequest request, Long userId) {
    SseEmitter emitter = new SseEmitter(0L);
    String requestId = StringUtils.hasText(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString();
    Long conversationId = request.getConversationId();
    ACTIVE_REQUESTS.put(conversationId, requestId);
    stringRedisTemplate.delete(stopKey(conversationId));

    new Thread(() -> {
      try {
        AiConversation conversation = verifyConversation(conversationId, userId);
        AiConversationMessage userMessage = createMessage(conversation.getId(), "user", request.getMessage(), "COMPLETED", requestId);
        aiConversationMessageMapper.insert(userMessage);

        List<AiConversationMessage> history = loadConversationHistory(conversation.getId());
        history.add(userMessage);
        cacheHistoryToRedis(conversation.getId(), history);

        List<ChatCitationDto> citations = buildCitations(request.getUseRag(), request.getMessage(), request.getArticleId(), request.getTopK(), userId);
        String prompt = buildPrompt(request.getMessage(), citations);
        List<Message> messages = buildMessages(history, prompt);

        emitter.send(SseEmitter.event().name("rag-start").data(jsonEvent("rag-start", "", conversationId, requestId, null, null, List.of())));
        emitter.send(SseEmitter.event().name("rag-result").data(jsonEvent("rag-result", "", conversationId, requestId, null, null, citations)));

        AiConversationMessage assistantMessage = createMessage(conversation.getId(), "assistant", "", "GENERATING", requestId);
        aiConversationMessageMapper.insert(assistantMessage);
        emitter.send(SseEmitter.event().name("message-start").data(jsonEvent("message-start", "", conversationId, requestId, null, null, citations)));

        StringBuilder assistantContent = new StringBuilder();
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        GenerationParam param = buildGenerationParam(messages);

        generation.streamCall(param, new ResultCallback<GenerationResult>() {
          @Override
          public void onEvent(GenerationResult result) {
            try {
              if (isStopped(conversationId, requestId)) {
                stopped.set(true);
                onComplete();
                return;
              }
              String chunk = extractContent(result);
              if (!StringUtils.hasText(chunk)) {
                return;
              }
              String delta = chunk;
              String current = assistantContent.toString();
              if (chunk.startsWith(current)) {
                delta = chunk.substring(current.length());
              }
              if (!StringUtils.hasText(delta)) {
                assistantContent.setLength(0);
                assistantContent.append(chunk);
                return;
              }
              assistantContent.append(delta);
              emitter.send(SseEmitter.event().name("message-delta").data(jsonEvent("message-delta", delta)));
            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onComplete() {
            if (completed.getAndSet(true)) {
              return;
            }
            try {
              assistantMessage.setContent(assistantContent.toString());
              assistantMessage.setStatus(stopped.get() ? "STOPPED" : "COMPLETED");
              assistantMessage.setCitations(serializeCitations(citations));
              assistantMessage.setUpdatedAt(LocalDateTime.now());
              aiConversationMessageMapper.updateById(assistantMessage);
              String eventType = stopped.get() ? "message-stop" : "message-end";
              String status = stopped.get() ? "STOPPED" : "COMPLETED";
              emitter.send(SseEmitter.event().name(eventType)
                  .data(jsonEvent(eventType, "", conversationId, requestId, assistantMessage.getId(), status, citations)));
              emitter.complete();
            } catch (IOException e) {
              emitter.completeWithError(e);
            } finally {
              cleanupActiveRequest(conversationId, requestId);
              stringRedisTemplate.delete(stopKey(conversationId));
              latch.countDown();
            }
          }

          @Override
          public void onError(Exception e) {
            if (completed.getAndSet(true)) {
              return;
            }
            try {
              assistantMessage.setContent(assistantContent.toString());
              assistantMessage.setStatus("FAILED");
              assistantMessage.setCitations(serializeCitations(citations));
              assistantMessage.setUpdatedAt(LocalDateTime.now());
              aiConversationMessageMapper.updateById(assistantMessage);
              emitter.send(SseEmitter.event().name("message-error").data(jsonEvent("message-error", e.getMessage())));
            } catch (IOException ignored) {
            } finally {
              cleanupActiveRequest(conversationId, requestId);
              stringRedisTemplate.delete(stopKey(conversationId));
              latch.countDown();
            }
            emitter.completeWithError(e);
          }
        });

        latch.await(60, TimeUnit.SECONDS);
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

  @Override
  public void stopGeneration(Long conversationId, Long userId) {
    AiConversation conversation = aiConversationMapper.selectById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
      throw new IllegalArgumentException("会话不存在或无权限停止生成");
    }
    stringRedisTemplate.opsForValue().set(stopKey(conversationId), "1", Duration.ofMinutes(10));
  }

  private AiConversation verifyConversation(Long conversationId, Long userId) {
    AiConversation conversation = aiConversationMapper.selectById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
      throw new IllegalArgumentException("会话不存在或无权限访问");
    }
    return conversation;
  }

  private boolean isArticleVisible(Long articleId, Long userId) {
    if (articleId == null) {
      return false;
    }
    var articleResponse = knowledgeArticleService.getArticle(articleId, userId);
    return articleResponse != null && articleResponse.isSuccess() && articleResponse.getData() != null;
  }

  private List<Long> resolveRagArticleIds(Long articleId, Long userId) {
    if (articleId != null) {
      return isArticleVisible(articleId, userId) ? List.of(articleId) : List.of();
    }
    return knowledgeArticleService.listArticles(userId).getData().stream()
        .filter(article -> article.getStatus() == null || article.getStatus() == 0)
        .map(article -> article.getArticleId())
        .toList();
  }

  private List<ChatCitationDto> buildCitations(Boolean useRag, String query, Long articleId, Integer topK, Long userId) {
    if (useRag == null || !useRag) {
      return List.of();
    }

    List<Long> targetArticleIds = resolveRagArticleIds(articleId, userId);
    if (targetArticleIds.isEmpty()) {
      return List.of();
    }

    List<ChatCitationDto> citations = new ArrayList<>();
    int remaining = topK == null || topK <= 0 ? 5 : topK;
    for (Long targetArticleId : targetArticleIds) {
      if (remaining <= 0) {
        break;
      }
      var searchResponse = knowledgeArticleChunkService.searchChunks(userId, query, targetArticleId, remaining);
      List<KnowledgeChunkSearchResponse> chunks = searchResponse == null ? List.of() : searchResponse.getData();
      if (chunks == null || chunks.isEmpty()) {
        continue;
      }
      for (KnowledgeChunkSearchResponse chunk : chunks) {
        if (!isArticleVisible(chunk.getArticleId(), userId)) {
          continue;
        }
        String articleTitle = "知识库文章";
        var articleResponse = knowledgeArticleService.getArticle(chunk.getArticleId(), userId);
        if (articleResponse != null && articleResponse.isSuccess() && articleResponse.getData() != null) {
          articleTitle = articleResponse.getData().getTitle();
        }
        citations.add(new ChatCitationDto(
            "c" + (citations.size() + 1),
            chunk.getArticleId(),
            articleTitle,
            chunk.getChunkId(),
            chunk.getChunkIndex(),
            chunk.getChunkText(),
            chunk.getScore()));
        remaining--;
        if (remaining <= 0) {
          break;
        }
      }
    }
    return citations;
  }

  private String buildPrompt(String question, List<ChatCitationDto> citations) {
    if (citations == null || citations.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("你需要优先根据以下检索到的知识库引用回答问题。\n");
    builder.append("引用编号格式必须严格使用 [c1]、[c2] 这种形式，不要输出其他格式的引用编号。\n");
    builder.append("如果回答中的某个结论或事实来自引用内容，必须在对应句子末尾追加相应引用编号，例如 [c1] 或 [c1][c3]。\n");
    builder.append("如果没有引用支撑，不要编造结论，直接说明无法从知识库中确认。\n");
    builder.append("下面是可用引用：\n");
    for (ChatCitationDto citation : citations) {
      builder.append("[").append(citation.getCitationId()).append("] ")
          .append(citation.getArticleTitle())
          .append(" - ")
          .append(citation.getChunkText())
          .append("\n");
    }
    builder.append("请只基于以上引用回答问题。问题：").append(question);
    return builder.toString();
  }

  private String generateOnce(List<Message> messages) {
    StringBuilder answer = new StringBuilder();
    try {
      GenerationParam param = buildGenerationParam(messages);
      CountDownLatch latch = new CountDownLatch(1);
      generation.streamCall(param, new ResultCallback<GenerationResult>() {
        @Override
        public void onEvent(GenerationResult result) {
          answer.append(extractContent(result));
        }

        @Override
        public void onComplete() {
          latch.countDown();
        }

        @Override
        public void onError(Exception e) {
          latch.countDown();
          throw new RuntimeException(e);
        }
      });
      latch.await(60, TimeUnit.SECONDS);
      return answer.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private GenerationParam buildGenerationParam(List<Message> messages) {
    return GenerationParam.builder()
        .apiKey(aiProperties.getApiKey())
        .model(aiProperties.getModelName())
        .messages(messages)
        .temperature(aiProperties.getTemperature() == null ? null : aiProperties.getTemperature().floatValue())
        .maxTokens(aiProperties.getMaxTokens())
        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
        .build();
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

  private List<AiConversationMessage> loadConversationHistory(Long conversationId) {
    String cacheKey = REDIS_CONVERSATION_HISTORY_KEY_PREFIX + conversationId;
    ListOperations<String, String> listOps = stringRedisTemplate.opsForList();
    List<String> cached = listOps.range(cacheKey, 0, -1);
    if (cached != null && !cached.isEmpty()) {
      List<AiConversationMessage> messages = new ArrayList<>();
      for (String item : cached) {
        try {
          messages.add(objectMapper.readValue(item, AiConversationMessage.class));
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

  private List<Message> buildMessages(List<AiConversationMessage> history, String systemPrompt) {
    List<Message> messages = new ArrayList<>();
    messages.add(Message.builder().role(Role.SYSTEM.getValue()).content("You are a helpful assistant.").build());
    if (StringUtils.hasText(systemPrompt)) {
      messages.add(Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build());
    }
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

  private String jsonEvent(String type, String message) {
    return jsonEvent(type, message, null, null, null, null, List.of());
  }

  private String jsonEvent(String type, String content, Long conversationId, String requestId, Long messageId, String status, List<ChatCitationDto> citations) {
    StringBuilder json = new StringBuilder("{");
    json.append("\"type\":").append(jsonString(type));
    json.append(",\"content\":").append(jsonString(content));
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
    json.append(",\"citations\":[");
    if (citations != null && !citations.isEmpty()) {
      for (int i = 0; i < citations.size(); i++) {
        ChatCitationDto citation = citations.get(i);
        if (i > 0) {
          json.append(',');
        }
        json.append("{")
            .append("\"citationId\":").append(jsonString(citation.getCitationId())).append(',')
            .append("\"articleId\":").append(citation.getArticleId()).append(',')
            .append("\"articleTitle\":").append(jsonString(citation.getArticleTitle())).append(',')
            .append("\"chunkId\":").append(citation.getChunkId()).append(',')
            .append("\"chunkIndex\":").append(citation.getChunkIndex()).append(',')
            .append("\"chunkText\":").append(jsonString(citation.getChunkText())).append(',')
            .append("\"score\":").append(citation.getScore())
            .append("}");
      }
    }
    json.append(']');
    json.append('}');
    return json.toString();
  }

  private String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    return '"' + escapeJson(value) + '"';
  }

  private String serializeCitations(List<ChatCitationDto> citations) {
    try {
      return objectMapper.writeValueAsString(citations == null ? List.of() : citations);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
