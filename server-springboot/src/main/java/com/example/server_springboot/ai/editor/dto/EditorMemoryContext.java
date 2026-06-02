package com.example.server_springboot.ai.editor.dto;

import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EditorMemoryContext {
  private String recentWindow;
  private String rollingSummary;
  private List<MemoryRecord> longTermMemories;
}
