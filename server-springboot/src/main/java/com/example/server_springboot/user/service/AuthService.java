package com.example.server_springboot.user.service;

import com.example.server_springboot.user.dto.LoginRequest;
import com.example.server_springboot.user.dto.LoginResponse;
import com.example.server_springboot.user.dto.RegisterCodeRequest;
import com.example.server_springboot.user.dto.RegisterCodeResponse;
import com.example.server_springboot.user.dto.RegisterRequest;
import com.example.server_springboot.user.dto.RegisterResponse;

public interface AuthService {
  LoginResponse login(LoginRequest request);

  RegisterCodeResponse generateRegisterCode(RegisterCodeRequest request);

  RegisterResponse register(RegisterRequest request);
}