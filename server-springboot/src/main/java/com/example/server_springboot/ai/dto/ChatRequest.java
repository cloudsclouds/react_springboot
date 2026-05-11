package com.example.server_springboot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequest {
  @NotNull(message = "conversationId 不能为空")
  private Long conversationId;

  @NotBlank(message = "message 不能为空")
  private String message;

  private Boolean stream = false;
  private Boolean useRag = true;
  private String ragScope = "knowledge_base";
  private Integer topK = 5;
  private Long articleId;

  @NotNull(message = "requestId 不能为空")
  private String requestId;
}
