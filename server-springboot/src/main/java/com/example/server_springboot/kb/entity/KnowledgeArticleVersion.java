package com.example.server_springboot.kb.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeArticleVersion {
  private Long id;
  private Long articleId;
  private Integer versionNo;
  private String snapshot;
  private String source;
  private Long createdBy;
  private LocalDateTime createdAt;
}
