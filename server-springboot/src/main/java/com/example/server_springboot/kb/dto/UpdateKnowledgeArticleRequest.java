package com.example.server_springboot.kb.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateKnowledgeArticleRequest {
  @NotBlank
  private String title;
  private String summary;
  private JsonNode content;
  private String saveSource;
}
