package com.example.server_springboot.kb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeRagSearchRequest {
  @NotBlank(message = "查询内容不能为空")
  private String query;

  private Long articleId;
  private Integer topK;
}
