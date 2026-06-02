package com.example.server_springboot.ai.memory.service;

public interface MemorySummaryService {
  String updateRollingSummary(Long conversationId, String previousSummary, String newMessages, Long userId);
}
