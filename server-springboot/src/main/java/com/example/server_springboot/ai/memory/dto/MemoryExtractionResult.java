package com.example.server_springboot.ai.memory.dto;

import com.example.server_springboot.ai.memory.entity.MemoryLevel;
import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemoryExtractionResult {
  private List<MemoryRecord> extractedMemories;
  private MemoryLevel targetLevel;
  private String reason;
}
