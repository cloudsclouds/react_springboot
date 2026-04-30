package com.example.server_springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareLink {
  private Long id;
  private Long documentId;
  private String shareToken;
  private String permission;
  private LocalDateTime expireTime;
  private LocalDateTime createdAt;
}
