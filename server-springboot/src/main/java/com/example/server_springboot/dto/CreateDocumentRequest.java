package com.example.server_springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDocumentRequest {
  @NotBlank(message = "文档标题不能为空")
  private String title;
}
