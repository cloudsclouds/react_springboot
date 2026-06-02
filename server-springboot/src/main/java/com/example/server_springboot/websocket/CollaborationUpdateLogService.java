package com.example.server_springboot.websocket;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationUpdateLogService {

  private static final int MAX_LOGS_PER_DOC = 500;

  private final ConcurrentHashMap<Long, Deque<CollaborationUpdateRecord>> logs = new ConcurrentHashMap<>();

  public void append(Long docId, CollaborationUpdateRecord record) {
    if (docId == null || record == null) {
      return;
    }
    logs.compute(docId, (key, queue) -> {
      Deque<CollaborationUpdateRecord> next = queue == null ? new ArrayDeque<>() : queue;
      next.addLast(record);
      while (next.size() > MAX_LOGS_PER_DOC) {
        next.removeFirst();
      }
      return next;
    });
  }

  public List<CollaborationUpdateRecord> recent(Long docId) {
    Deque<CollaborationUpdateRecord> queue = logs.get(docId);
    if (queue == null) {
      return List.of();
    }
    return new ArrayList<>(queue);
  }

  public record CollaborationUpdateRecord(
      String type,
      Long userId,
      String requestId,
      String origin,
      String payload,
      LocalDateTime createdAt,
      Map<String, Object> metadata
  ) {
  }
}
