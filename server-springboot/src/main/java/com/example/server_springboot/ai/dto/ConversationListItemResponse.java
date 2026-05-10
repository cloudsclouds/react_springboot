package com.example.server_springboot.ai.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationListItemResponse {
  private Long conversationId;
  private String title;
  private String summary;
  private LocalDateTime lastMessageAt;
  private Long messageCount;
}
