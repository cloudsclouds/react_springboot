package com.example.server_springboot.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Document {
  private Long id;
  private String title;
  private Long ownerId;
  private String ownerName;
  private String latestSnapshot;
  private Integer version;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
