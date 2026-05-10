package com.example.server_springboot.kb.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RollbackKnowledgeArticleRequest {
  @NotNull
  private Integer versionNo;
}
