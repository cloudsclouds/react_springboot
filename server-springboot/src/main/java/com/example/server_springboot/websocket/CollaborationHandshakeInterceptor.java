package com.example.server_springboot.websocket;

import com.example.server_springboot.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class CollaborationHandshakeInterceptor implements HandshakeInterceptor {

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }

    HttpServletRequest httpRequest = servletRequest.getServletRequest();
    String token = httpRequest.getParameter("token");
    String docId = httpRequest.getParameter("docId");

    if (token == null || token.isBlank() || docId == null || docId.isBlank()) {
      return false;
    }

    Long userId = JwtUtils.verifyToken(token);
    if (userId == null) {
      return false;
    }

    try {
      attributes.put("userId", userId);
      attributes.put("docId", Long.valueOf(docId));
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
  }
}
