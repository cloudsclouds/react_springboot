package com.example.server_springboot.ai.memory.service;

import com.example.server_springboot.ai.memory.dto.MemoryWriteCommand;
import com.example.server_springboot.ai.memory.entity.MemoryRecord;
import java.util.List;

public interface MemoryStorageService {
  MemoryRecord write(MemoryWriteCommand command);

  List<MemoryRecord> recentByConversation(Long conversationId, int limit);

  List<MemoryRecord> searchL3(Long userId, String query, int limit);

  void cleanupExpired();
}
