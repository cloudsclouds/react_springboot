package com.example.server_springboot.ai.memory.service;

import com.example.server_springboot.ai.memory.dto.MemoryContext;
import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;

public interface MemoryOrchestratorService {
  MemoryContext buildContext(Long userId, Long conversationId, String query);

  void ingestTurn(Long userId, Long conversationId, String requestId, String userMessage, String assistantMessage, String previousSummary);

  void write(MemoryWriteCommand command);
}
