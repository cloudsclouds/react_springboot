package com.example.server_springboot.websocket;

import com.example.server_springboot.collabdocument.dto.DocumentResponse;
import com.example.server_springboot.collabdocument.service.DocumentService;
import com.example.server_springboot.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CollaborationWebSocketHandler extends BinaryWebSocketHandler {

  // 当房间内累计了足够多的 update 之后，触发一次快照保存。
  // 这样可以避免只依赖增量日志，降低文档恢复成本。
  private static final int AUTO_SAVE_THRESHOLD = 20;

  // Jackson 用于读写 WebSocket JSON 消息。
  private final ObjectMapper objectMapper;
  // 文档服务负责校验权限、读取文档元数据和最新快照。
  private final DocumentService documentService;
  // 会话管理器负责维护“文档房间 -> WebSocketSession / userId”的映射。
  private final CollaborationSessionManager sessionManager;
  // 增量日志服务负责记录协作 update，便于追踪、回放和调试。
  private final CollaborationUpdateLogService updateLogService;
  // 快照服务负责把当前文档状态持久化到数据库或其他存储介质。
  private final CollaborationSnapshotService snapshotService;
  // Awareness 服务负责保存在线协作者的临时状态，比如光标、昵称、颜色、活跃状态。
  private final CollaborationAwarenessService awarenessService;

  public CollaborationWebSocketHandler(ObjectMapper objectMapper,
                                       DocumentService documentService,
                                       CollaborationSessionManager sessionManager,
                                       CollaborationUpdateLogService updateLogService,
                                       CollaborationSnapshotService snapshotService,
                                       CollaborationAwarenessService awarenessService) {
    this.objectMapper = objectMapper;
    this.documentService = documentService;
    this.sessionManager = sessionManager;
    this.updateLogService = updateLogService;
    this.snapshotService = snapshotService;
    this.awarenessService = awarenessService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    // 握手完成后，从 session 属性里取出鉴权阶段已经写入的文档 ID 和用户 ID。
    Long docId = (Long) session.getAttributes().get("docId");
    Long userId = (Long) session.getAttributes().get("userId");

    // 把当前连接登记到房间中，后续广播 update / awareness / 在线人数都会依赖这里的会话列表。
    sessionManager.addSession(docId, session);
    sessionManager.addUser(docId, userId);

    // 读取该用户可访问的文档元数据，主要用于首次进入时的初始化同步。
    ApiResponse<DocumentResponse> response = documentService.getDocumentMetadataForUser(docId, userId);
    Map<String, Object> payload = new HashMap<>();
    if (response.isSuccess() && response.getData() != null) {
      // snapshot / latestSnapshot：用于前端首屏恢复文档内容，通常来源于数据库里的文档快照字段。
      payload.put("snapshot", response.getData().getLatestSnapshot());
      payload.put("latestSnapshot", response.getData().getLatestSnapshot());
      // 文档基础信息，供前端展示标题、版本号等。
      payload.put("title", response.getData().getTitle());
      payload.put("version", response.getData().getUpdatedAt());
      // stateVector 用于标记当前文档的协作状态基线，帮助前端做增量同步。
      payload.put("stateVector", buildStateVector(docId));
      // awareness 是协作者在线状态的快照，不属于正文内容，只用于协作 UI。
      payload.put("awareness", awarenessService.snapshot(docId));
      // recentUpdates 用于把房间近期发生的增量记录一并发给前端，便于补偿和调试。
      payload.put("recentUpdates", updateLogService.recent(docId).stream().map(record -> Map.of(
          "type", record.type(),
          "userId", record.userId(),
          "requestId", record.requestId(),
          "origin", record.origin(),
          "payload", record.payload(),
          "createdAt", record.createdAt(),
          "metadata", record.metadata()
      )).toList());
    } else {
      // 如果文档读取失败，直接把错误信息返回给前端，让页面可以展示失败状态。
      payload.put("message", response.getMessage());
    }

    // 首次连接时先发送 sync 消息，把快照和协作上下文推给前端。
    sendText(session, Map.of(
        "type", "sync",
        "docId", docId,
        "payload", payload
    ));
    // 再发送一次在线人数，保证前端立刻拿到最新的房间人数。
    sendOnlineCount(docId, session);
    // 也广播给房间里其他成员，让他们感知有人加入。
    broadcastOnlineCount(docId, session);
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, org.springframework.web.socket.BinaryMessage message) throws Exception {
    // 二进制消息走的是 Yjs update 协议，正文协作内容会以 byte[] / Uint8Array 的形式传输。
    Long docId = (Long) session.getAttributes().get("docId");
    Long userId = (Long) session.getAttributes().get("userId");

    // 为了便于审计、排障和回放，把二进制 update 编成 base64 后持久化到增量日志中。
    String encodedUpdate = java.util.Base64.getEncoder().encodeToString(toBytes(message.getPayload()));

    updateLogService.append(docId, new CollaborationUpdateLogService.CollaborationUpdateRecord(
        "update",
        userId,
        null,
        "binary",
        encodedUpdate,
        LocalDateTime.now(),
        Map.of("binary", true)
    ));

    // 当前协作者发来的 update 需要广播给同房间其他客户端，保证最终一致性。
    broadcastBinary(docId, message, session);

    // 当增量积累到阈值后，触发一次快照保存，避免恢复时完全依赖日志重放。
    if (updateLogService.recent(docId).size() >= AUTO_SAVE_THRESHOLD) {
      snapshotService.saveSnapshot(docId, extractSnapshotFromMetadata(docId));
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    // 文本消息承载的是协同协议控制信息，当前实现里主要用 JSON 来表达。
    try {
      Long docId = (Long) session.getAttributes().get("docId");
      Long userId = (Long) session.getAttributes().get("userId");
      Map<String, Object> incoming = objectMapper.readValue(message.getPayload(), Map.class);
      String type = String.valueOf(incoming.getOrDefault("type", ""));

      if ("ping".equalsIgnoreCase(type)) {
        // 心跳消息用于检测连接是否仍然可用，同时帮助前端和后端做保活。
        sendText(session, Map.of("type", "pong", "docId", docId, "payload", Map.of("ts", System.currentTimeMillis())));
        return;
      }

      if ("sync".equalsIgnoreCase(type)) {
        // sync 用于首次进入房间或重连时补齐快照、状态向量等协作上下文。
        handleSync(session, docId, userId, incoming);
        return;
      }

      if ("awareness".equalsIgnoreCase(type)) {
        // awareness 保存的是在线状态，不进入正文持久化，只用于协作者可视化展示。
        handleAwareness(docId, userId, incoming, session);
        return;
      }

      if ("save".equalsIgnoreCase(type)) {
        // save 允许前端主动触发一次快照持久化。
        handleSave(session, docId, incoming);
        return;
      }

      if ("rollback".equalsIgnoreCase(type)) {
        // rollback 本质上也是一个广播型控制消息，通知房间里的其他客户端同步处理。
        broadcastText(docId, enrichIncoming(incoming, userId), session);
        sendOnlineCount(docId, session);
        broadcastOnlineCount(docId, session);
        return;
      }

      if ("update".equalsIgnoreCase(type)) {
        // JSON 形式的 update 仍然兼容支持，但正文协作推荐走 binary update。
        handleJsonUpdate(session, docId, userId, incoming);
        return;
      }

      // 未知消息类型直接回一个 error，避免前端误以为请求被正常处理。
      sendText(session, Map.of(
          "type", "error",
          "docId", docId,
          "payload", Map.of("code", "UNSUPPORTED_TYPE", "message", "不支持的消息类型")
      ));
    } catch (Exception e) {
      try {
        Long docId = (Long) session.getAttributes().get("docId");
        sendText(session, Map.of(
            "type", "error",
            "docId", docId,
            "payload", Map.of("code", "TEXT_MESSAGE_ERROR", "message", e.getMessage())
        ));
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    // 连接关闭时，要把 session、在线用户和 awareness 状态一起清掉，避免房间状态残留。
    Long docId = (Long) session.getAttributes().get("docId");
    Long userId = (Long) session.getAttributes().get("userId");
    if (docId != null) {
      sessionManager.removeSession(docId, session);
      sessionManager.removeUser(docId, userId);
      awarenessService.removeUser(docId, userId);
      try {
        // 通知房间里的其他客户端：这个用户已经离线。
        broadcastText(docId, Map.of(
            "type", "awareness",
            "docId", docId,
            "payload", Map.of(
                "userId", userId,
                "active", false,
                "lastSeenAt", System.currentTimeMillis()
            )
        ), session);
      } catch (Exception ignored) {
      }
    }
  }

  private void handleSync(WebSocketSession session, Long docId, Long userId, Map<String, Object> incoming) throws IOException {
    // 前端发起 sync 时，会带上自己的状态向量，后端据此决定返回哪些快照/增量。
    Map<String, Object> payload = extractPayload(incoming);
    Map<String, Object> snapshotPayload = buildSyncPayload(docId, userId);
    if (payload.containsKey("stateVector")) {
      snapshotPayload.put("stateVector", payload.get("stateVector"));
    }
    if (payload.containsKey("clientId")) {
      snapshotPayload.put("clientId", payload.get("clientId"));
    }
    // 将同步结果回推给发起者：包括快照、状态、在线成员等初始化信息。
    sendText(session, Map.of(
        "type", "sync",
        "docId", docId,
        "payload", snapshotPayload
    ));
    sendOnlineCount(docId, session);
    broadcastOnlineCount(docId, session);
  }

  private Map<String, Object> buildSyncPayload(Long docId, Long userId) {
    // 这个 payload 是前端首次同步时的“初始化包”，把恢复文档所需的信息一次性准备好。
    Map<String, Object> payload = new HashMap<>();
    ApiResponse<DocumentResponse> response = documentService.getDocumentMetadataForUser(docId, userId);
    if (response.isSuccess() && response.getData() != null) {
      // snapshot / latestSnapshot 通常来自数据库里的文档快照字段。
      payload.put("snapshot", response.getData().getLatestSnapshot());
      payload.put("latestSnapshot", response.getData().getLatestSnapshot());
      payload.put("title", response.getData().getTitle());
      payload.put("version", response.getData().getUpdatedAt());
      // 把当前房间的 awareness 一并发给前端，便于恢复在线成员列表和协作光标。
      payload.put("awareness", awarenessService.snapshot(docId));
      // recentUpdates 记录最近一段时间的协作增量，用于重连补偿、排障和数据追踪。
      payload.put("recentUpdates", updateLogService.recent(docId).stream().map(record -> Map.of(
          "type", record.type(),
          "userId", record.userId(),
          "requestId", record.requestId(),
          "origin", record.origin(),
          "payload", record.payload(),
          "createdAt", record.createdAt(),
          "metadata", record.metadata()
      )).toList());
    }
    payload.put("stateVector", buildStateVector(docId));
    payload.put("connectedClients", sessionManager.getOnlineCount(docId));
    return payload;
  }

  private void handleJsonUpdate(WebSocketSession session, Long docId, Long userId, Map<String, Object> incoming) throws IOException {
    // 兼容 JSON 形式的 update：如果前端还没切到 binary，也能正常同步。
    Map<String, Object> payload = extractPayload(incoming);
    String encodedUpdate = stringValue(payload.get("update"));
    if (encodedUpdate.isBlank()) {
      sendText(session, Map.of(
          "type", "error",
          "docId", docId,
          "payload", Map.of("code", "EMPTY_UPDATE", "message", "update 不能为空")
      ));
      return;
    }
    // 记录这条 update，后续可以用来审计、排障、重放或做快照合并。
    updateLogService.append(docId, new CollaborationUpdateLogService.CollaborationUpdateRecord(
        "update",
        userId,
        stringValue(incoming.get("requestId")),
        stringValue(payload.getOrDefault("origin", "local")),
        encodedUpdate,
        LocalDateTime.now(),
        Map.of("binary", false)
    ));
    // 广播给同房间其他客户端，让它们合并这条正文增量。
    broadcastText(docId, enrichIncoming(incoming, userId), session);
  }

  private void handleAwareness(Long docId, Long userId, Map<String, Object> incoming, WebSocketSession session) throws IOException {
    // awareness 只保存“临时状态”，例如光标位置、昵称、颜色、活跃状态等。
    Map<String, Object> payload = extractPayload(incoming);
    payload.putIfAbsent("userId", userId);
    payload.putIfAbsent("lastSeenAt", System.currentTimeMillis());
    awarenessService.upsert(docId, userId, payload);
    Map<String, Object> message = new HashMap<>(incoming);
    message.put("payload", payload);
    // 把最新 awareness 广播给房间里的其他成员，驱动前端协作者列表和光标展示更新。
    broadcastText(docId, message, session);
    sendOnlineCount(docId, session);
    broadcastOnlineCount(docId, session);
  }

  private void handleSave(WebSocketSession session, Long docId, Map<String, Object> incoming) throws IOException {
    // save 允许前端显式触发一次持久化，通常用于手动保存或自动保存。
    Map<String, Object> payload = extractPayload(incoming);
    String latestSnapshot = stringValue(payload.get("snapshot"));
    if (latestSnapshot.isBlank()) {
      latestSnapshot = extractSnapshotFromMetadata(docId);
    }
    if (latestSnapshot.isBlank()) {
      sendText(session, Map.of(
          "type", "error",
          "docId", docId,
          "payload", Map.of("code", "EMPTY_SNAPSHOT", "message", "快照不能为空")
      ));
      return;
    }
    snapshotService.saveSnapshot(docId, latestSnapshot);
    sendText(session, Map.of(
        "type", "save",
        "docId", docId,
        "payload", Map.of("status", "saved")
    ));
    broadcastText(docId, incoming, session);
  }

  private String buildStateVector(Long docId) {
    // 这里的 stateVector 是一个简化实现，作用是让前端和服务端有一个协作状态的基准标识。
    // 在完整 Yjs 协议里，它通常用于判断双方缺失了哪些增量更新。
    List<CollaborationUpdateLogService.CollaborationUpdateRecord> recent = updateLogService.recent(docId);
    return "state-vector:" + docId + ":" + recent.size();
  }

  private String extractSnapshotFromMetadata(Long docId) {
    // 兜底读取文档表中的最新快照。
    // 当主动 save 没有带 snapshot 时，这里会直接从数据库里拿当前版本作为保存来源。
    try {
      ApiResponse<DocumentResponse> response = documentService.getDocumentMetadataForInternal(docId);
      if (response.isSuccess() && response.getData() != null) {
        return response.getData().getLatestSnapshot();
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private Map<String, Object> enrichIncoming(Map<String, Object> incoming, Long userId) {
    Map<String, Object> payload = new HashMap<>(incoming);
    payload.putIfAbsent("payload", Map.of());
    payload.putIfAbsent("userId", userId);
    return payload;
  }

  private Map<String, Object> extractPayload(Map<String, Object> incoming) {
    // JSON 消息统一采用 { type, docId, payload } 结构，这里负责把 payload 拿出来并转成可用的 Map。
    Object payload = incoming.get("payload");
    if (payload instanceof Map<?, ?> map) {
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        result.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      return result;
    }
    return new HashMap<>();
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private void sendOnlineCount(Long docId, WebSocketSession session) throws IOException {
    // 给当前连接者单独发送一份在线人数，方便前端立即渲染房间状态。
    sendText(session, Map.of(
        "type", "onlineCount",
        "docId", docId,
        "payload", Map.of(
            "count", sessionManager.getOnlineCount(docId),
            "userIds", sessionManager.getUserIdsByDoc(docId),
            "docId", docId,
            "status", "connected"
        )
    ));
  }

  private void broadcastOnlineCount(Long docId, WebSocketSession source) throws IOException {
    // 房间在线人数发生变化时，广播给其他人，让成员列表和状态条实时刷新。
    Map<String, Object> payload = Map.of(
        "count", sessionManager.getOnlineCount(docId),
        "userIds", sessionManager.getUserIdsByDoc(docId),
        "docId", docId,
        "status", "connected"
    );
    for (WebSocketSession docSession : sessionManager.getSessions(docId)) {
      if (docSession.isOpen() && !docSession.getId().equals(source.getId())) {
        sendText(docSession, Map.of("type", "onlineCount", "docId", docId, "payload", payload));
      }
    }
  }

  private void broadcastText(Long docId, Map<String, Object> message, WebSocketSession source) throws IOException {
    // 把 JSON 控制消息广播给同房间其他客户端，例如 awareness、rollback、save 等。
    for (WebSocketSession docSession : sessionManager.getSessions(docId)) {
      if (docSession.isOpen() && !docSession.getId().equals(source.getId())) {
        sendText(docSession, message);
      }
    }
  }

  private void broadcastBinary(Long docId, org.springframework.web.socket.BinaryMessage message, WebSocketSession source) throws IOException {
    // 二进制广播只用于正文协作增量，保证所有在线客户端最终看到相同的文档状态。
    for (WebSocketSession docSession : sessionManager.getSessions(docId)) {
      if (docSession.isOpen() && !docSession.getId().equals(source.getId())) {
        docSession.sendMessage(message);
      }
    }
  }

  private void sendText(WebSocketSession session, Map<String, Object> message) throws IOException {
    // 统一把 Map 序列化成 JSON 文本再发出，保证前端可以按协议解析。
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
  }

  private byte[] toBytes(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }
}
