package com.example.server_springboot.user.controller;

import com.example.server_springboot.user.dto.LoginRequest;
import com.example.server_springboot.user.dto.LoginResponse;
import com.example.server_springboot.user.dto.RegisterCodeRequest;
import com.example.server_springboot.user.dto.RegisterCodeResponse;
import com.example.server_springboot.user.dto.RegisterRequest;
import com.example.server_springboot.user.dto.RegisterResponse;
import com.example.server_springboot.user.service.AuthService;
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