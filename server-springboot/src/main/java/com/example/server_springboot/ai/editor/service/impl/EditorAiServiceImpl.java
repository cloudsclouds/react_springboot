package com.example.server_springboot.ai.editor.service.impl;

import com.example.server_springboot.ai.editor.agent.EditorTaskAgent;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteRequest;
import com.example.server_springboot.ai.editor.dto.EditorAiExecuteResponse;
import com.example.server_springboot.ai.editor.dto.EditorAiStreamRequest;
import com.example.server_springboot.ai.editor.dto.EditorAiStopRequest;
import com.example.server_springboot.ai.editor.entity.KnowledgeArticleOperationLog;
import com.example.server_springboot.ai.editor.mapper.KnowledgeArticleOperationLogMapper;
import com.example.server_springboot.ai.editor.router.EditorAgentRouter;
import com.example.server_springboot.ai.editor.service.EditorAiService;
import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.mapper.KnowledgeArticleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 编辑器 AI 服务实现
 */
@Service
@RequiredArgsConstructor
public class EditorAiServiceImpl implements EditorAiService {
  // 停止请求前缀
  private static final String STOP_KEY_PREFIX = "kb:ai:stop:";
  // 活跃请求映射
  private static final ConcurrentHashMap<String, String> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

  private final EditorAgentRouter router; // 编辑器任务智能体路由
  private final KnowledgeArticleMapper articleMapper; // 知识库文章映射
  private final KnowledgeArticleOperationLogMapper logMapper; // 知识库文章操作日志映射
  private final StringRedisTemplate redisTemplate; // Redis 模板
  private final ObjectMapper objectMapper; // 对象映射器

  /**
   * 执行编辑器 AI
   * @param request 请求
   * @param userId 用户 ID
   * @return 响应
   */
  @Override
  public ApiResponse<EditorAiExecuteResponse> execute(EditorAiExecuteRequest request, Long userId) {
    verifyArticle(request.getArticleId(), userId);
    // 路由编辑器任务智能体
    EditorTaskAgent agent = router.route(request);
    // 如果编辑器任务智能体为空，则抛出异常
    if (agent == null) {
      throw new IllegalArgumentException("不支持的编辑器 AI action: " + request.getAction());
    }
    // 生成输出
    String output = agent.generate(request);
    // 保存日志
    saveLog(userId, request, agent, output, "SUCCESS", null, 0, request.getRequestId());
    return ApiResponse.success("执行成功", new EditorAiExecuteResponse(agent.intent(), agent.outputType(), output, agent.resultAction(), agent.meta(request)));
  }

  @Override
  public SseEmitter stream(EditorAiStreamRequest request, Long userId) {
    verifyArticle(request.getArticleId(), userId);
    SseEmitter emitter = new SseEmitter(0L);
    String requestId = StringUtils.hasText(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString();
    String requestKey = requestKey(request.getArticleId(), requestId);
    ACTIVE_REQUESTS.put(requestKey, requestId);
    redisTemplate.delete(stopKey(request.getArticleId(), requestId));

    new Thread(() -> {
      try {
        EditorAiExecuteRequest executeRequest = new EditorAiExecuteRequest();
        executeRequest.setArticleId(request.getArticleId());
        executeRequest.setRequestId(requestId);
        executeRequest.setEntryPoint(request.getEntryPoint());
        executeRequest.setAction(request.getAction());
        executeRequest.setSelectedText(request.getSelectedText());
        executeRequest.setSurroundingContext(request.getSurroundingContext());
        executeRequest.setChatInput(request.getChatInput());
        EditorTaskAgent agent = router.route(executeRequest);

        emitter.send(SseEmitter.event().name("message-start").data(json(Map.of("type", "message-start", "requestId", requestId, "articleId", request.getArticleId()))));
        String output = agent.generate(executeRequest);
        if (isStopped(request.getArticleId(), requestId)) {
          saveLog(userId, executeRequest, agent, output, "STOPPED", null, 0, requestId);
          emitter.send(SseEmitter.event().name("message-stop").data(json(Map.of("type", "message-stop", "requestId", requestId, "articleId", request.getArticleId()))));
          emitter.complete();
          return;
        }
        emitter.send(SseEmitter.event().name("message-delta").data(json(Map.of("type", "message-delta", "content", output, "requestId", requestId, "articleId", request.getArticleId()))));
        saveLog(userId, executeRequest, agent, output, "SUCCESS", null, 0, requestId);
        emitter.send(SseEmitter.event().name("message-end").data(json(Map.of("type", "message-end", "outputText", output, "requestId", requestId, "articleId", request.getArticleId()))));
        emitter.complete();
      } catch (Exception e) {
        try {
          emitter.send(SseEmitter.event().name("message-error").data(json(Map.of("type", "message-error", "message", e.getMessage(), "requestId", requestId, "articleId", request.getArticleId()))));
        } catch (Exception ignored) {
        }
        emitter.completeWithError(e);
      } finally {
        cleanup(request.getArticleId(), requestId);
        redisTemplate.delete(stopKey(request.getArticleId(), requestId));
      }
    }).start();

    return emitter;
  }

  @Override
  public ApiResponse<String> stop(Long articleId, String requestId, Long userId) {
    verifyArticle(articleId, userId);
    redisTemplate.opsForValue().set(stopKey(articleId, requestId), "1");
    return ApiResponse.success("已停止", null);
  }

  @Override
  public ApiResponse<List<KnowledgeArticleOperationLog>> listLogs(Long articleId, Long userId) {
    verifyArticle(articleId, userId);
    return ApiResponse.success("查询成功", List.of());
  }

  /**
   * 验证文章
   * @param articleId 文章 ID
   * @param userId 用户 ID
   */
  private void verifyArticle(Long articleId, Long userId) {
    // 查询文章
    var article = articleMapper.selectById(articleId);
    // 如果文章不存在或用户无权访问，则抛出异常
    if (article == null || !userId.equals(article.getUserId())) {
      throw new IllegalArgumentException("文章不存在或无权访问");
    }
  }

  private void saveLog(Long userId, EditorAiExecuteRequest request, EditorTaskAgent agent, String outputText, String status, String errorMessage, Integer latencyMs, String requestId) {
    KnowledgeArticleOperationLog log = new KnowledgeArticleOperationLog();
    log.setUserId(userId);
    log.setArticleId(request.getArticleId());
    log.setRequestId(requestId);
    log.setOperationType("AI_GENERATE");
    log.setChangeMode("SNAPSHOT");
    log.setIntent(agent.intent());
    log.setEntryPoint(request.getEntryPoint());
    log.setInputText(buildInputText(request));
    log.setSelectedText(request.getSelectedText());
    log.setOutputText(outputText);
    log.setResultAction(agent.resultAction());
    log.setStatus(status);
    log.setErrorMessage(errorMessage);
    log.setLatencyMs(latencyMs);
    log.setCreatedAt(LocalDateTime.now());
    logMapper.insert(log);
  }

  /**
   * 构建输入文本
   * @param request 请求
   * @return 输入文本
   */
  private String buildInputText(EditorAiExecuteRequest request) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "entryPoint", request.getEntryPoint(),
          "action", request.getAction(),
          "chatInput", request.getChatInput(),
          "surroundingContext", request.getSurroundingContext()));
    } catch (Exception e) {
      return request.getAction();
    }
  }

  /**
   * 是否停止
   * @param articleId 文章 ID
   * @param requestId 请求 ID
   * @return 是否停止
   */
  private boolean isStopped(Long articleId, String requestId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(stopKey(articleId, requestId)))
        || !requestId.equals(ACTIVE_REQUESTS.get(requestKey(articleId, requestId)));
  }

  /**
   * 清理请求
   * @param articleId 文章 ID
   * @param requestId 请求 ID
   */
  private void cleanup(Long articleId, String requestId) {
    ACTIVE_REQUESTS.remove(requestKey(articleId, requestId));
  }

  /**
   * 请求键
   * @param articleId 文章 ID
   * @param requestId 请求 ID
   * @return 请求键
   */
  private String requestKey(Long articleId, String requestId) {
    return articleId + ":" + requestId;
  }

  /**
   * 停止键
   * @param articleId 文章 ID
   * @param requestId 请求 ID
   * @return 停止键
   */
  private String stopKey(Long articleId, String requestId) {
    return STOP_KEY_PREFIX + articleId + ":" + requestId;
  }

  /**
   * JSON 转换
   * @param payload 负载
   * @return JSON 字符串
   */
  private String json(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      return payload.toString();
    }
  }
}
