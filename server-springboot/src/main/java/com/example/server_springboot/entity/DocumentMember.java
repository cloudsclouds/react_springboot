package com.example.server_springboot.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentMember {
  private Long id;
  private Long documentId;
  private Long userId;
  private String role;
  private LocalDateTime joinedAt;
}
