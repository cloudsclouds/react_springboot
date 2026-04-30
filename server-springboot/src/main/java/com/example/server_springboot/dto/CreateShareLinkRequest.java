package com.example.server_springboot.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateShareLinkRequest {
  @Pattern(regexp = "editor|viewer", message = "分享权限无效")
  private String permission = "viewer";
}
