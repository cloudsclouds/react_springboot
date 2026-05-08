package com.example.server_springboot.ai.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiConversationMessage {
  private Long id;
  private Long conversationId;
  private String role;
  private String content;
  private String status;
  private String requestId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
