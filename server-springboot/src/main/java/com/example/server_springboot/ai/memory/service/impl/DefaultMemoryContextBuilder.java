package com.example.server_springboot.ai.memory.service.impl;

import com.example.server_springboot.ai.memory.dto.MemoryContext;
import com.example.server_springboot.ai.memory.service.MemoryContextBuilder;
import com.example.server_springboot.ai.memory.service.MemoryOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultMemoryContextBuilder implements MemoryContextBuilder {
  private final MemoryOrchestratorService orchestratorService;

  @Override
  public MemoryContext build(Long userId, Long conversationId, String query) {
    return orchestratorService.buildContext(userId, conversationId, query);
  }
}
