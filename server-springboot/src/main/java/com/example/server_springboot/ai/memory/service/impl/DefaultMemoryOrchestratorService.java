package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.dto.MemoryContext;
import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;
import com.example.server_springboot.ai.memory.entity.MemoryLevel;
import com.example.server_springboot.ai.memory.entity.MemoryScope;
import com.example.server_springboot.ai.memory.entity.MemorySourceType;
import com.example.server_springboot.ai.memory.service.MemoryOrchestratorService;
import com.example.server_springboot.ai.memory.service.MemoryRetrievalService;
import com.example.server_springboot.ai.memory.service.MemoryStorageService;
import com.example.server_springboot.ai.memory.service.MemorySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DefaultMemoryOrchestratorService implements MemoryOrchestratorService {
  /** 负责将记忆写入底层存储，并在必要时进行去重、合并和 TTL 处理。 */
  private final MemoryStorageService memoryStorageService;
  /** 负责按会话 / 用户上下文检索出当前生成需要的记忆。 */
  private final MemoryRetrievalService memoryRetrievalService;
  /** 负责把新增对话滚动成 L2 摘要。 */
  private final MemorySummaryService memorySummaryService;

  /**
   * 构建当前生成所需的记忆上下文。
   *
   * 会同时返回 L1 最近原文、L2 滚动摘要和 L3 长期记忆，
   * 供 chat / editor agent 在提示词里统一使用。
   */
  @Override
  public MemoryContext buildContext(Long userId, Long conversationId, String query) {
    return memoryRetrievalService.buildContext(userId, conversationId, query);
  }

  /**
   * 吸收一轮对话，把用户输入和模型输出分别沉淀到 L1 / L2 / L3。
   *
   * 写入规则：
   * - 用户原文优先进入 L1；
   * - 当前轮的新增内容滚动更新到 L2；
   * - 对长期稳定偏好或事实，按门控条件提升到 L3。
   */
  @Override
  public void ingestTurn(Long userId, Long conversationId, String requestId, String userMessage, String assistantMessage, String previousSummary) {
    if (userId == null || conversationId == null) {
      return;
    }
    if (StringUtils.hasText(userMessage)) {
      memoryStorageService.write(MemoryWriteCommand.builder()
          .userId(userId)
          .conversationId(conversationId)
          .level(MemoryLevel.L1)
          .scope(MemoryScope.SESSION)
          .content(userMessage)
          .sourceType(MemorySourceType.SYSTEM_MIGRATION)
          .sourceTurn(requestId)
          .confidence(1.0D)
          .ttlDays(2)
          .build());
    }
    String combined = String.join("\n", StringUtils.hasText(userMessage) ? userMessage : "", StringUtils.hasText(assistantMessage) ? assistantMessage : "");
    String summary = memorySummaryService.updateRollingSummary(conversationId, previousSummary, combined, userId);
    memoryStorageService.write(MemoryWriteCommand.builder()
        .userId(userId)
        .conversationId(conversationId)
        .level(MemoryLevel.L2)
        .scope(MemoryScope.SESSION)
        .content(combined)
        .summary(summary)
        .sourceType(MemorySourceType.AGENT_EXTRACTION)
        .sourceTurn(requestId)
        .confidence(0.9D)
        .ttlDays(30)
        .build());
    if (StringUtils.hasText(userMessage) && shouldPromoteToL3(userMessage)) {
      memoryStorageService.write(MemoryWriteCommand.builder()
          .userId(userId)
          .conversationId(conversationId)
          .level(MemoryLevel.L3)
          .scope(MemoryScope.USER)
          .content(normalize(userMessage))
          .summary(summary)
          .factsJson("{\"source\":\"turn\",\"type\":\"user_preference\"}")
          .sourceType(MemorySourceType.USER_CONFIRMATION)
          .sourceTurn(requestId)
          .confidence(0.95D)
          .ttlDays(30)
          .build());
    }
  }

  @Override
  public void write(MemoryWriteCommand command) {
    memoryStorageService.write(command);
  }

  /**
   * 判断是否有必要把当前用户输入提升为 L3 长期记忆。
   *
   * 这里采用简单关键词门控，后续可以继续升级成更严格的分类器或规则引擎。
   */
  private boolean shouldPromoteToL3(String text) {
    String normalized = normalize(text);
    return normalized.contains("记住") || normalized.contains("偏好") || normalized.contains("以后") || normalized.contains("默认") || normalized.contains("不要再") || normalized.contains("一直") || normalized.contains("总是");
  }

  /** 去掉首尾空白，避免重复存储时因为格式不同导致去重失败。 */
  private String normalize(String text) {
    return text == null ? "" : text.trim();
  }
}
