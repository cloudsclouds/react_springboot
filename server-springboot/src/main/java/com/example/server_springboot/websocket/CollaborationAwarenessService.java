package com.example.server_springboot.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationAwarenessService {

  private static final long AWARENESS_TTL_MS = 5 * 60 * 1000L;

  private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AwarenessState>> awarenessByDoc = new ConcurrentHashMap<>();

  public void upsert(Long docId, Long userId, Map<String, Object> payload) {
    if (docId == null || userId == null) {
      return;
    }
    awarenessByDoc.computeIfAbsent(docId, key -> new ConcurrentHashMap<>())
        .put(userId, new AwarenessState(payload, System.currentTimeMillis()));
  }

  public void removeUser(Long docId, Long userId) {
    if (docId == null || userId == null) {
      return;
    }
    ConcurrentHashMap<Long, AwarenessState> docAwareness = awarenessByDoc.get(docId);
    if (docAwareness == null) {
      return;
    }
    docAwareness.remove(userId);
    if (docAwareness.isEmpty()) {
      awarenessByDoc.remove(docId);
    }
  }

  public Map<Long, Map<String, Object>> snapshot(Long docId) {
    ConcurrentHashMap<Long, AwarenessState> docAwareness = awarenessByDoc.get(docId);
    if (docAwareness == null) {
      return Map.of();
    }
    Map<Long, Map<String, Object>> result = new ConcurrentHashMap<>();
    docAwareness.forEach((userId, state) -> {
      if (!state.isExpired()) {
        result.put(userId, state.payload());
      }
    });
    return result;
  }

  public Map<String, Object> get(Long docId, Long userId) {
    ConcurrentHashMap<Long, AwarenessState> docAwareness = awarenessByDoc.get(docId);
    if (docAwareness == null) {
      return null;
    }
    AwarenessState state = docAwareness.get(userId);
    if (state == null || state.isExpired()) {
      return null;
    }
    return state.payload();
  }

  @Scheduled(fixedDelay = 60_000L)
  public void cleanupExpired() {
    awarenessByDoc.forEach((docId, docAwareness) -> {
      docAwareness.entrySet().removeIf(entry -> entry.getValue().isExpired());
      if (docAwareness.isEmpty()) {
        awarenessByDoc.remove(docId);
      }
    });
  }

  private record AwarenessState(Map<String, Object> payload, long updatedAt) {
    private boolean isExpired() {
      return System.currentTimeMillis() - updatedAt > AWARENESS_TTL_MS;
    }
  }
}
