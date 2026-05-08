package com.example.server_springboot.collabdocument.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareLinkResponse {
  private Long documentId;
  private String shareToken;
  private String shareUrl;
  private String permission;
  private LocalDateTime expireTime;
}
