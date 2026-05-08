package com.example.server_springboot.ai.service;

import com.example.server_springboot.ai.dto.CreateConversationRequest;
import com.example.server_springboot.ai.dto.CreateConversationResponse;

public interface AiConversationService {
  CreateConversationResponse createConversation(CreateConversationRequest request, Long userId);
}
