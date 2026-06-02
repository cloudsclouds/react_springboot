package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.dto.MemoryContext;
import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import com.example.server_springboot.ai.memory.service.MemoryRetrievalService;
import com.example.server_springboot.ai.memory.service.MemoryStorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认记忆检索服务。
 *
 * <p>该服务负责把分散存储在不同层级中的记忆，整理成 AI 可直接使用的上下文：
 * <ul>
 *   <li>recentWindow：最近会话窗口中的短期记忆；</li>
 *   <li>rollingSummary：从最近记录中提炼出的滚动摘要；</li>
 *   <li>longTermMemories：从 L3 长期记忆中按用户和查询检索出的内容。</li>
 * </ul>
 *
 * <p>最终返回的 `MemoryContext` 会被上层对话/编辑服务拼接进提示词中，帮助模型获取历史上下文。
 */
@Service
@RequiredArgsConstructor
public class DefaultMemoryRetrievalService implements MemoryRetrievalService {
  private final MemoryStorageService memoryStorageService;

  /**
   * 构建给模型使用的记忆上下文。
   *
   * <p>处理步骤：
   * <ol>
   *   <li>先获取当前会话最近的若干条记忆记录；</li>
   *   <li>再根据用户与查询内容检索长期记忆；</li>
   *   <li>将最近记录拼接成窗口文本；</li>
   *   <li>从最近记录中筛出 L2 摘要，形成滚动摘要；</li>
   *   <li>最后封装成 `MemoryContext` 返回。</li>
   * </ol>
   *
   * @param userId 用户 ID
   * @param conversationId 会话 ID
   * @param query 当前查询内容，用于检索长期记忆
   * @return 组装完成的记忆上下文
   */
  @Override
  public MemoryContext buildContext(Long userId, Long conversationId, String query) {
    List<MemoryRecord> recent = memoryStorageService.recentByConversation(conversationId, 8);
    List<MemoryRecord> longTerm = StringUtils.hasText(query)
        ? memoryStorageService.searchL3(userId, query, 5)
        : List.of();
    String recentWindow = recent.stream()
        .map(record -> "[" + record.getLevel() + "] " + safe(record.getContent()))
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
    String rollingSummary = recent.stream()
        .filter(record -> record.getLevel() != null && record.getLevel().name().equals("L2"))
        .map(record -> safe(record.getSummary()))
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
    return MemoryContext.builder()
        .recentWindow(recentWindow)
        .rollingSummary(rollingSummary)
        .longTermMemories(longTerm)
        .build();
  }

  /**
   * 安全返回字符串，避免空值直接参与拼接。
   */
  private String safe(String value) {
    return StringUtils.hasText(value) ? value : "";
  }
}
