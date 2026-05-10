package com.example.server_springboot.kb.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeArticleVersionResponse {
  private Integer versionNo;
  private String source;
  private LocalDateTime createdAt;
}
