package com.example.server_springboot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterCodeResponse {
  private boolean success;
  private String message;
  private String code;
}