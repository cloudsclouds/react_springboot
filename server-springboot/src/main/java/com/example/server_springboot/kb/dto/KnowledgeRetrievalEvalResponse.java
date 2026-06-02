package com.example.server_springboot.kb.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeRetrievalEvalResponse {
  private Long articleId;
  private String query;
  private int candidateCount;
  private int selectedCount;
  private double averageScore;
  private double confidence;
  private List<KnowledgeChunkSearchResponse> candidates;
}
