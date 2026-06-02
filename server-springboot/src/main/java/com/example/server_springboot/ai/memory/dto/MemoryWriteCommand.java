package com.example.server_springboot.ai.memory.dto;

import com.example.server_springboot.ai.memory.entity.MemoryLevel;
import com.example.server_springboot.ai.memory.entity.MemoryScope;
import com.example.server_springboot.ai.memory.entity.MemorySourceType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemoryWriteCommand {
  private Long userId;
  private Long conversationId;
  private MemoryLevel level;
  private MemoryScope scope;
  private String content;
  private String summary;
  private String factsJson;
  private MemorySourceType sourceType;
  private String sourceTurn;
  private Double confidence;
  private Integer ttlDays;
}
