package com.example.server_springboot.service;

import com.example.server_springboot.dto.LoginRequest;
import com.example.server_springboot.dto.LoginResponse;
import com.example.server_springboot.dto.RegisterCodeRequest;
import com.example.server_springboot.dto.RegisterCodeResponse;
import com.example.server_springboot.dto.RegisterRequest;
import com.example.server_springboot.dto.RegisterResponse;

public interface AuthService {
  LoginResponse login(LoginRequest request);

  RegisterCodeResponse generateRegisterCode(RegisterCodeRequest request);

  RegisterResponse register(RegisterRequest request);
}