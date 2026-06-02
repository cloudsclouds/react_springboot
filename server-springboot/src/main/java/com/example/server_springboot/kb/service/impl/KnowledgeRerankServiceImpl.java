package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.service.KnowledgeRerankService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeRerankServiceImpl implements KnowledgeRerankService {
  @Override
  public List<KnowledgeChunkSearchResponse> rerank(String query, List<KnowledgeChunkSearchResponse> candidates, int limit) {
    // 候选集为空或无需返回时，直接给出空结果，避免后续排序和打分开销。
    if (candidates == null || candidates.isEmpty() || limit <= 0) {
      return List.of();
    }
    List<KnowledgeChunkSearchResponse> sorted = new ArrayList<>(candidates);
    // 按综合得分从高到低排序，保留更适合大模型上下文的证据片段。
    sorted.sort(Comparator.comparingDouble((KnowledgeChunkSearchResponse item) -> score(query, item)).reversed());
    return sorted.stream().limit(limit).toList();
  }

  private double score(String query, KnowledgeChunkSearchResponse chunk) {
    // 基础分来自召回阶段，文本和摘要命中用于补充重排权重。
    double base = chunk.getScore() == null ? 0d : chunk.getScore();
    double textScore = StringUtils.hasText(chunk.getChunkText()) ? lexical(query, chunk.getChunkText()) : 0d;
    double summaryScore = StringUtils.hasText(chunk.getChunkSummary()) ? lexical(query, chunk.getChunkSummary()) * 0.25d : 0d;
    return base * 0.6d + textScore * 0.3d + summaryScore * 0.1d;
  }

  private double lexical(String query, String text) {
    if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
      return 0d;
    }
    String normalizedQuery = query.toLowerCase();
    String normalizedText = text.toLowerCase();
    String[] parts = normalizedQuery.split("\\s+");
    int hit = 0;
    for (String part : parts) {
      // 过滤过短词，减少停用词或无意义 token 对排序的干扰。
      if (part.length() >= 2 && normalizedText.contains(part)) {
        hit++;
      }
    }
    return parts.length == 0 ? 0d : (double) hit / (double) parts.length;
  }
}
