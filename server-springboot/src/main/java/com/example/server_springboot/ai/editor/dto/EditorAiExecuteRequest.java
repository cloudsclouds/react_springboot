package com.example.server_springboot.ai.editor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EditorAiExecuteRequest {
  @NotNull(message = "articleId 不能为空")
  private Long articleId;

  @NotBlank(message = "requestId 不能为空")
  private String requestId;

  @NotBlank(message = "entryPoint 不能为空")
  private String entryPoint;

  private String action;

  private String selectedText;
  private String surroundingContext;
  private String chatInput;
}
