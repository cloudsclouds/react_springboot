package com.example.server_springboot.entity;

import lombok.Data;

@Data
public class UserAccount {
  private Long id;
  private String username;
  private String email;
  private String password;
  private String nickname;
}