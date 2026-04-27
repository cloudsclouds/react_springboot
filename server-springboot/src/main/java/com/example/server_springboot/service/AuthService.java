package com.example.server_springboot.service;

import com.example.server_springboot.dto.LoginRequest;
import com.example.server_springboot.dto.LoginResponse;

public interface AuthService {
  LoginResponse login(LoginRequest request);
}