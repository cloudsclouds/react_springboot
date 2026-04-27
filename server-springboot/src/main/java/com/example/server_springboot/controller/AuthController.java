package com.example.server_springboot.controller;

import com.example.server_springboot.dto.LoginRequest;
import com.example.server_springboot.dto.LoginResponse;
import com.example.server_springboot.dto.RegisterCodeRequest;
import com.example.server_springboot.dto.RegisterCodeResponse;
import com.example.server_springboot.dto.RegisterRequest;
import com.example.server_springboot.dto.RegisterResponse;
import com.example.server_springboot.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/register/code")
  public RegisterCodeResponse generateRegisterCode(@Valid @RequestBody RegisterCodeRequest request) {
    return authService.generateRegisterCode(request);
  }

  @PostMapping("/register")
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }
}