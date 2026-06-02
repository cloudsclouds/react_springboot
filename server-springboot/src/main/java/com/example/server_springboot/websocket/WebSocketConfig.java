package com.example.server_springboot.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  // 协作编辑的 WebSocket 处理器会在这里注册到 `/ws/collaboration` 路由上。
  // 它不会被业务代码手动 new 出来调用，而是由 Spring WebSocket 在运行时自动分发消息。
  private final CollaborationWebSocketHandler collaborationWebSocketHandler;
  private final CollaborationHandshakeInterceptor collaborationHandshakeInterceptor;
  private final String corsAllowedOrigins;

  public WebSocketConfig(CollaborationWebSocketHandler collaborationWebSocketHandler,
                         CollaborationHandshakeInterceptor collaborationHandshakeInterceptor,
                         @Value("${app.cors.allowed-origins:http://localhost:3000}") String corsAllowedOrigins) {
    this.collaborationWebSocketHandler = collaborationWebSocketHandler;
    this.collaborationHandshakeInterceptor = collaborationHandshakeInterceptor;
    this.corsAllowedOrigins = corsAllowedOrigins;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(collaborationWebSocketHandler, "/ws/collaboration")
        .addInterceptors(collaborationHandshakeInterceptor)
        .setAllowedOrigins(corsAllowedOrigins.split(","));
  }
}
