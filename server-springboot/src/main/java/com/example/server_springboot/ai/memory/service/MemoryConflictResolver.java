package com.example.server_springboot.ai.memory.service;

import com.example.server_springboot.ai.memory.entity.MemoryRecord;

public interface MemoryConflictResolver {
  ResolutionResult resolve(MemoryRecord existing, MemoryRecord candidate);

  enum ResolutionResult {
    REPLACE,
    KEEP_EXISTING,
    MERGE,
    IGNORE
  }
}
