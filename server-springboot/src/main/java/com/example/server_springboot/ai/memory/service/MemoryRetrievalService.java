package com.example.server_springboot.ai.memory.service;

import com.example.server_springboot.ai.memory.dto.MemoryContext;

public interface MemoryRetrievalService {
  MemoryContext buildContext(Long userId, Long conversationId, String query);
}
