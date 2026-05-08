package com.example.server_springboot.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StopGenerationRequest {

  @NotNull(message = "conversationId 不能为空")
  private Long conversationId;
}
