package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeChunkSearchResponse;
import com.example.server_springboot.kb.dto.KnowledgeRetrievalDiagnosticsResponse;
import com.example.server_springboot.kb.dto.KnowledgeRetrievalEvalResponse;
import com.example.server_springboot.kb.service.KnowledgeArticleChunkService;
import com.example.server_springboot.kb.service.RetrievalEvalService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetrievalEvalServiceImpl implements RetrievalEvalService {
  private final KnowledgeArticleChunkService chunkService;

  @Override
  public KnowledgeRetrievalDiagnosticsResponse diagnose(Long userId, String query, Long articleId, Integer topK) {
    int limit = topK == null || topK <= 0 ? 5 : topK;
    String compressed = query == null ? "" : query.trim().replaceAll("\\s+", " ");
    List<String> subQueries = buildSubQueries(compressed);
    List<KnowledgeRetrievalEvalResponse> evaluations = new ArrayList<>();
    List<KnowledgeChunkSearchResponse> allCandidates = new ArrayList<>();
    for (String subQuery : subQueries) {
      ApiResponse<List<KnowledgeChunkSearchResponse>> response = chunkService.searchChunksAdvanced(userId, subQuery, articleId, limit, true);
      List<KnowledgeChunkSearchResponse> candidates = response == null || response.getData() == null ? List.of() : response.getData();
      allCandidates.addAll(candidates);
      double avgScore = candidates.stream().mapToDouble(item -> item.getScore() == null ? 0d : item.getScore()).average().orElse(0d);
      double confidence = computeConfidence(avgScore, candidates.size(), limit);
      evaluations.add(new KnowledgeRetrievalEvalResponse(articleId, subQuery, candidates.size(), Math.min(limit, candidates.size()), avgScore, confidence, candidates));
    }
    boolean lowConfidence = allCandidates.isEmpty() || allCandidates.stream().mapToDouble(item -> item.getScore() == null ? 0d : item.getScore()).average().orElse(0d) < 0.35d;
    String refusalReason = lowConfidence ? "检索证据不足，建议补充更明确的关键词或相关文档。" : null;
    return new KnowledgeRetrievalDiagnosticsResponse(query, compressed, subQueries, lowConfidence, refusalReason, evaluations);
  }

  private List<String> buildSubQueries(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    result.add(query);
    String[] parts = query.split("[，,。；;？?！!\\n\\r\\t]");
    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.length() >= 4) {
        result.add(trimmed);
      }
      if (result.size() >= 3) {
        break;
      }
    }
    return new ArrayList<>(new HashSet<>(result));
  }

  private double computeConfidence(double avgScore, int selectedCount, int limit) {
    double coverage = limit <= 0 ? 0d : Math.min(1d, (double) selectedCount / (double) limit);
    return Math.max(0d, Math.min(1d, avgScore * 0.7d + coverage * 0.3d));
  }
}
