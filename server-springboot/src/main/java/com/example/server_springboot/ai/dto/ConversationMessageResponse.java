package com.example.server_springboot.ai.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationMessageResponse {
  private Long messageId;
  private String role;
  private String content;
  private String status;
  private String requestId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
