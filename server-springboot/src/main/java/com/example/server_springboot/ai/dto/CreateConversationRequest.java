package com.example.server_springboot.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConversationRequest {

  @Size(max = 200, message = "标题长度不能超过 200 个字符")
  private String title;
}
