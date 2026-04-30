package com.example.server_springboot.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentMemberResponse {
  private Long userId;
  private String nickname;
  private String role;
  private LocalDateTime joinedAt;
  private boolean editable;
  private boolean commentable;
}
