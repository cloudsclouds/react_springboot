package com.example.server_springboot.kb.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeArticle {
  private Long id;
  private Long userId;
  private String title;
  private String summary;
  private String content;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
