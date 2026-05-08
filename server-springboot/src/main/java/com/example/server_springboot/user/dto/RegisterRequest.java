package com.example.server_springboot.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不正确")
  private String email;

  @NotBlank(message = "密码不能为空")
  private String password;

  @NotBlank(message = "昵称不能为空")
  private String nickname;

  @NotBlank(message = "验证码不能为空")
  private String code;
}