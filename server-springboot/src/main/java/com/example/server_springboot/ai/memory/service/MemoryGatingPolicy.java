package com.example.server_springboot.ai.memory.service;

import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;

public interface MemoryGatingPolicy {
  boolean shouldWrite(MemoryWriteCommand command);
}
