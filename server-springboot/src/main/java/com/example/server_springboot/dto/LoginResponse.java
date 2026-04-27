package com.example.server_springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
  private boolean success;
  private String message;
  private Long userId;
  private String nickname;
}