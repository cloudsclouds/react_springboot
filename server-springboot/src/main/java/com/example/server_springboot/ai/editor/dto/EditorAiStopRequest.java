package com.example.server_springboot.ai.editor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EditorAiStopRequest {
  @NotNull(message = "articleId 不能为空")
  private Long articleId;

  @NotBlank(message = "requestId 不能为空")
  private String requestId;
}
