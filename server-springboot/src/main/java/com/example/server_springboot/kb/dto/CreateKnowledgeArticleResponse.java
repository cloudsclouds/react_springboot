package com.example.server_springboot.kb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateKnowledgeArticleResponse {
  private Long articleId;
  private String title;
}
