package com.example.server_springboot.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationSessionManager {

  private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  public void addSession(Long docId, WebSocketSession session) {
    sessions.computeIfAbsent(docId, key -> ConcurrentHashMap.newKeySet()).add(session);
  }

  public void removeSession(Long docId, WebSocketSession session) {
    Set<WebSocketSession> docSessions = sessions.get(docId);
    if (docSessions == null) {
      return;
    }
    docSessions.remove(session);
    if (docSessions.isEmpty()) {
      sessions.remove(docId);
    }
  }

  public Set<WebSocketSession> getSessions(Long docId) {
    return sessions.getOrDefault(docId, Set.of());
  }
}
