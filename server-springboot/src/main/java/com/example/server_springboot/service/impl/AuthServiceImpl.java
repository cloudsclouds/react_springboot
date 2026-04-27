package com.example.server_springboot.service.impl;

import com.example.server_springboot.dto.LoginRequest;
import com.example.server_springboot.dto.LoginResponse;
import com.example.server_springboot.entity.UserAccount;
import com.example.server_springboot.mapper.UserAccountMapper;
import com.example.server_springboot.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserAccountMapper userAccountMapper;

  @Override
  public LoginResponse login(LoginRequest request) {
    UserAccount user = userAccountMapper.findByEmail(request.getEmail());

    if (user == null) {
      return new LoginResponse(false, "用户不存在", null, null);
    }

    // 当前先做明文比对，后续建议替换成 BCrypt。
    if (!user.getPassword().equals(request.getPassword())) {
      return new LoginResponse(false, "密码错误", null, null);
    }

    return new LoginResponse(true, "登录成功", user.getId(), user.getNickname());
  }
}