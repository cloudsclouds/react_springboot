package com.example.server_springboot.ai.memory.service;

import com.example.server_springboot.ai.memory.dto.MemoryContext;

public interface MemoryContextBuilder {
  MemoryContext build(Long userId, Long conversationId, String query);
}
