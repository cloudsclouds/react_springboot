package com.example.server_springboot.kb.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeArticleChunk {
  private Long id;
  private Long articleId;
  private Integer chunkIndex;
  private String chunkText;
  private String chunkSummary;
  private String embeddingId;
  private LocalDateTime createdAt;
}
