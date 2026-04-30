package com.example.server_springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyShareLinkRequest {
  @NotBlank(message = "shareToken 不能为空")
  private String shareToken;
}
