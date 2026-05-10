package com.example.server_springboot.kb.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeArticleDetailResponse {
  private Long articleId;
  private String title;
  private String summary;
  private JsonNode content;
  private LocalDateTime updatedAt;
}
