package com.example.server_springboot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
  private boolean success;
  private String message;
  private String token;
  private Long userId;
  private String nickname;
}