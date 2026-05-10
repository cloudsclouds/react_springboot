package com.example.server_springboot.kb.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeArticleListItemResponse {
  private Long articleId;
  private String title;
  private String summary;
  private LocalDateTime updatedAt;
  private Integer status;
}
