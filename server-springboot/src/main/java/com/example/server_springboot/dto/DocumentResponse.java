package com.example.server_springboot.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentResponse {
  private Long id;
  private String title;
  private Long ownerId;
  private String myRole; // For document details or list
  private LocalDateTime updatedAt;
}
