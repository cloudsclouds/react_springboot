package com.example.server_springboot.ai.service;

import com.example.server_springboot.ai.dto.ChatStreamRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {
  SseEmitter streamChat(ChatStreamRequest request, Long userId);

  void stopGeneration(Long conversationId, Long userId);
}
