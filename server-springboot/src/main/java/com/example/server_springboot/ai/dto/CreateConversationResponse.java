package com.example.server_springboot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateConversationResponse {
  private Long conversationId;
  private String title;
  private Long userId;
}
