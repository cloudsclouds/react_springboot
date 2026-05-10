package com.example.server_springboot.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationDetailResponse {
  private Long conversationId;
  private String title;
  private String summary;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<ConversationMessageResponse> messages;
}
