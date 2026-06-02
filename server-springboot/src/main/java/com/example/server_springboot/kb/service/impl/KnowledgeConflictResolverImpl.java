package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.service.KnowledgeConflictResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeConflictResolverImpl implements KnowledgeConflictResolver {
  @Override
  public List<KnowledgeChunkSearchResponse> resolve(String query, List<KnowledgeChunkSearchResponse> candidates) {
    // 没有候选片段时，直接返回空列表，避免后续去重逻辑执行无效遍历。
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    Map<Long, KnowledgeChunkSearchResponse> bestByChunkId = new HashMap<>();
    for (KnowledgeChunkSearchResponse candidate : candidates) {
      if (candidate == null || candidate.getChunkId() == null) {
        continue;
      }
      // 同一个 chunk 可能来自多个召回源，保留综合分更高的那个版本。
      KnowledgeChunkSearchResponse existing = bestByChunkId.get(candidate.getChunkId());
      if (existing == null || compareCandidate(query, candidate, existing) > 0) {
        bestByChunkId.put(candidate.getChunkId(), candidate);
      }
    }
    return new ArrayList<>(bestByChunkId.values()).stream()
        // 去重后按原始召回分数排序，保证输出结果仍然稳定可解释。
        .sorted(Comparator.comparingDouble((KnowledgeChunkSearchResponse item) -> item.getScore() == null ? 0d : item.getScore()).reversed())
        .toList();
  }

  private int compareCandidate(String query, KnowledgeChunkSearchResponse left, KnowledgeChunkSearchResponse right) {
    double leftScore = adjustedScore(query, left);
    double rightScore = adjustedScore(query, right);
    return Double.compare(leftScore, rightScore);
  }

  private double adjustedScore(String query, KnowledgeChunkSearchResponse candidate) {
    // 冲突消解时优先使用基础分，再叠加摘要与问题的轻量文本重合度。
    double base = candidate.getScore() == null ? 0d : candidate.getScore();
    double summaryBoost = StringUtils.hasText(candidate.getChunkSummary()) && StringUtils.hasText(query)
        ? overlap(query, candidate.getChunkSummary()) * 0.05d
        : 0d;
    return base + summaryBoost;
  }

  private double overlap(String query, String text) {
    if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
      return 0d;
    }
    String[] queryParts = query.toLowerCase().split("\\s+");
    int hit = 0;
    for (String part : queryParts) {
      // 同样过滤过短 token，降低噪声词导致的误判。
      if (part.length() >= 2 && text.toLowerCase().contains(part)) {
        hit++;
      }
    }
    return queryParts.length == 0 ? 0d : (double) hit / (double) queryParts.length;
  }
}
