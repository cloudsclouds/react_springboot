package com.example.server_springboot.ai.controller;

import com.example.server_springboot.ai.dto.ChatStreamRequest;
import com.example.server_springboot.ai.dto.CreateConversationRequest;
import com.example.server_springboot.ai.dto.CreateConversationResponse;
import com.example.server_springboot.ai.service.AiChatService;
import com.example.server_springboot.ai.service.AiConversationService;
import com.example.server_springboot.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

  private final AiConversationService aiConversationService;
  private final AiChatService aiChatService;

  @PostMapping("/conversations")
  public CreateConversationResponse createConversation(@Valid @RequestBody CreateConversationRequest request) {
    return aiConversationService.createConversation(request, UserContext.getUserId());
  }

  @PostMapping(value = "/chat/stream", produces = "text/event-stream")
  public SseEmitter streamChat(@Valid @RequestBody ChatStreamRequest request) {
    return aiChatService.streamChat(request, UserContext.getUserId());
  }
}
