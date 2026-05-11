package com.example.server_springboot.ai.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiConversation {
  private Long id;
  private Long userId;
  private String title;
  private String summary;
  private Integer status;
  private Boolean useRag;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
