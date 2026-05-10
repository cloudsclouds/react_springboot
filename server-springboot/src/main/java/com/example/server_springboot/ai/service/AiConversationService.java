package com.example.server_springboot.ai.service;

import com.example.server_springboot.ai.dto.ConversationDetailResponse;
import com.example.server_springboot.ai.dto.ConversationListItemResponse;
import com.example.server_springboot.ai.dto.CreateConversationRequest;
import com.example.server_springboot.ai.dto.CreateConversationResponse;
import java.util.List;

public interface AiConversationService {
  CreateConversationResponse createConversation(CreateConversationRequest request, Long userId);

  List<ConversationListItemResponse> listConversations(Long userId);

  ConversationDetailResponse getConversationDetail(Long conversationId, Long userId);
}
