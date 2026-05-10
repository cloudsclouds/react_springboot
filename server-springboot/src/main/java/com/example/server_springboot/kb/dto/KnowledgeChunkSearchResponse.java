package com.example.server_springboot.kb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeChunkSearchResponse {
  private Long chunkId;
  private Long articleId;
  private Integer chunkIndex;
  private String chunkText;
  private String chunkSummary;
  private String embeddingId;
  private Double score;
}
