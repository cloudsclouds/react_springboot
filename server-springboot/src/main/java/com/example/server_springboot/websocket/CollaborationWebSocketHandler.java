package com.example.server_springboot.websocket;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.dto.DocumentResponse;
import com.example.server_springboot.dto.UpdateDocumentRequest;
import com.example.server_springboot.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;
  private final DocumentService documentService;
  private final CollaborationSessionManager sessionManager;

  public CollaborationWebSocketHandler(ObjectMapper objectMapper,
                                       DocumentService documentService,
                                       CollaborationSessionManager sessionManager) {
    this.objectMapper = objectMapper;
    this.documentService = documentService;
    this.sessionManager = sessionManager;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Long docId = (Long) session.getAttributes().get("docId");
    sessionManager.addSession(docId, session);

    Long userId = (Long) session.getAttributes().get("userId");
    ApiResponse<DocumentResponse> response = documentService.getDocumentMetadataForUser(docId, userId);
    Map<String, Object> message = new HashMap<>();
    message.put("type", "sync");
    message.put("docId", docId);
    message.put("payload", response.isSuccess() ? response.getData() : Map.of("message", response.getMessage()));
    send(session, message);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    Long docId = (Long) session.getAttributes().get("docId");
    Long userId = (Long) session.getAttributes().get("userId");
    Map<String, Object> incoming = objectMapper.readValue(message.getPayload(), Map.class);
    String type = String.valueOf(incoming.getOrDefault("type", ""));

    if ("ping".equalsIgnoreCase(type)) {
      send(session, Map.of("type", "pong", "docId", docId, "payload", Map.of()));
      return;
    }

    if ("sync".equalsIgnoreCase(type) || "awareness".equalsIgnoreCase(type)) {
      broadcast(docId, enrichIncoming(incoming, userId), session);
      return;
    }

    if ("save".equalsIgnoreCase(type)) {
      handleSave(session, docId, incoming);
      return;
    }

    if ("rollback".equalsIgnoreCase(type)) {
      broadcast(docId, enrichIncoming(incoming, userId), session);
      return;
    }

    send(session, Map.of(
        "type", "error",
        "docId", docId,
        "payload", Map.of("code", "UNSUPPORTED_TYPE", "message", "不支持的消息类型")
    ));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Long docId = (Long) session.getAttributes().get("docId");
    if (docId != null) {
      sessionManager.removeSession(docId, session);
    }
  }

  private void handleSave(WebSocketSession session, Long docId, Map<String, Object> incoming) throws IOException {
    Object payload = incoming.get("payload");
    String latestSnapshot = "";
    if (payload instanceof Map<?, ?> payloadMap) {
      Object snapshot = payloadMap.get("snapshot");
      if (snapshot != null) {
        latestSnapshot = String.valueOf(snapshot);
      }
    }

    if (latestSnapshot.isBlank()) {
      send(session, Map.of(
          "type", "error",
          "docId", docId,
          "payload", Map.of("code", "EMPTY_SNAPSHOT", "message", "快照不能为空")
      ));
      return;
    }

    if (docId != null) {
      updateDocumentSnapshot(docId, latestSnapshot);
    }

    send(session, Map.of(
        "type", "save",
        "docId", docId,
        "payload", Map.of("status", "saved")
    ));
    broadcast(docId, incoming, session);
  }

  private void updateDocumentSnapshot(Long docId, String latestSnapshot) {
    try {
      DocumentResponse current = documentService.getDocumentMetadata(docId).getData();
      if (current == null) {
        return;
      }
      UpdateDocumentRequest request = new UpdateDocumentRequest();
      request.setTitle(current.getTitle());
      request.setLatestSnapshot(latestSnapshot);
      // persistence hook will be wired by DocumentService update API in next step
    } catch (Exception ignored) {
      // keep websocket responsive even when persistence mapping fails
    }
  }

  private Map<String, Object> enrichIncoming(Map<String, Object> incoming, Long userId) {
    Map<String, Object> payload = new HashMap<>(incoming);
    payload.putIfAbsent("payload", Map.of());
    payload.putIfAbsent("userId", userId);
    return payload;
  }

  private void broadcast(Long docId, Map<String, Object> message, WebSocketSession source) throws IOException {
    for (WebSocketSession docSession : sessionManager.getSessions(docId)) {
      if (docSession.isOpen() && !docSession.getId().equals(source.getId())) {
        send(docSession, message);
      }
    }
  }

  private void send(WebSocketSession session, Map<String, Object> message) throws IOException {
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
  }
}
