package com.example.server_springboot.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationSessionManager {

  private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Set<Long>> onlineUserIds = new ConcurrentHashMap<>();

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

  public void addUser(Long docId, Long userId) {
    if (docId == null || userId == null) {
      return;
    }
    onlineUserIds.computeIfAbsent(docId, key -> ConcurrentHashMap.newKeySet()).add(userId);
  }

  public void removeUser(Long docId, Long userId) {
    if (docId == null || userId == null) {
      return;
    }
    Set<Long> users = onlineUserIds.get(docId);
    if (users == null) {
      return;
    }
    users.remove(userId);
    if (users.isEmpty()) {
      onlineUserIds.remove(docId);
    }
  }

  public Set<Long> getOnlineUsers(Long docId) {
    return onlineUserIds.getOrDefault(docId, Set.of());
  }

  public int getOnlineCount(Long docId) {
    return getOnlineUsers(docId).size();
  }

  public Set<WebSocketSession> getSessions(Long docId) {
    return sessions.getOrDefault(docId, Set.of());
  }

  public Set<Long> getUserIdsByDoc(Long docId) {
    return new HashSet<>(getOnlineUsers(docId));
  }
}
