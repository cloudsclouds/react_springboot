package com.example.server_springboot.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final CollaborationWebSocketHandler collaborationWebSocketHandler;
  private final CollaborationHandshakeInterceptor collaborationHandshakeInterceptor;

  public WebSocketConfig(CollaborationWebSocketHandler collaborationWebSocketHandler,
                         CollaborationHandshakeInterceptor collaborationHandshakeInterceptor) {
    this.collaborationWebSocketHandler = collaborationWebSocketHandler;
    this.collaborationHandshakeInterceptor = collaborationHandshakeInterceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(collaborationWebSocketHandler, "/ws/collaboration")
        .addInterceptors(collaborationHandshakeInterceptor)
        .setAllowedOrigins("http://localhost:3000");
  }
}
