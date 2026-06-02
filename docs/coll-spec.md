# 文档与协同技术方案及接口文档

## 1. 目标

本方案用于支撑项目的文档管理、多人协同编辑、权限控制与版本快照功能，并保证前端页面、React 接口封装与 Spring Boot 后端实现保持一致。

当前项目已经具备**基础 WebSocket 协同外壳**，下一阶段的目标是把协同编辑升级为**基于 Yjs 的增量协同**，补齐 update 同步、awareness 在线状态、离线重连与持久化日志链路。

---

## 2. 当前实现

### 2.1 已完成能力

#### 前端

- 文档编辑页已接入 Tiptap 编辑器。
- 已有文档列表、文档详情、成员管理、版本快照、回滚等页面能力。
- 协同编辑入口已在前端侧预留，支持通过 WebSocket 连接服务端。
- 当前编辑器组件已具备多人协作的 UI 承载能力，包括在线人数展示、协作者头像区域、光标/状态展示入口。

#### 服务端

- 已提供 WebSocket 协议入口：`/ws/collaboration`。
- 已实现握手阶段鉴权：通过 `token + docId` 校验用户身份和文档访问参数。
- 已实现房间级 session 管理、在线用户统计、在线用户 ID 列表维护。
- 已实现连接后下发文档快照、在线人数广播、心跳响应、保存入口与断开清理。

#### 数据层

- 已有文档、协作者、版本快照、分享链接等基础表结构。
- 已有文档持久化与版本历史能力。

### 2.2 当前实现的边界

当前协同能力仍以**自定义 JSON 消息 + 快照保存**为主，尚未完全接入标准 Yjs 协议。因此当前系统更适合定义为：

- 已实现：WebSocket 在线协作外壳
- 正在补齐：Yjs 增量协同、awareness、重连补偿、增量持久化
- 尚未完成：标准 CRDT update 同步闭环

---

## 3. 待补齐能力

以下能力是本方案下一阶段的补齐重点。

### 3.1 Yjs 增量协同

- 接入 `Y.Doc` 作为文档状态容器。
- 使用 Yjs update 作为编辑增量的标准传输格式。
- 在前端编辑器与共享文档之间建立绑定关系，避免手写复杂的操作转换。
- 支持增量 update 的合并、去重与最终一致性收敛。

### 3.2 Awareness 在线状态

- 独立同步在线用户的光标、选区、昵称、颜色与活跃状态。
- awareness 状态不进入正文持久化，不污染文档内容。
- 支持节流、过期清理与断线失活处理。

### 3.3 增量日志与快照

- 记录协同 update 增量日志，而不是只保存全文快照。
- 定期将增量日志合并为最新 snapshot，降低恢复成本。
- 支持版本回滚、重放与异常恢复。

### 3.4 异常与边界处理

- 网络抖动后的自动重连。
- 断线期间的本地编辑缓存。
- 重连后的状态补偿与缺失 update 补发。
- 非法消息、超大消息、重复消息、空消息的校验与拦截。
- 高并发房间下的广播顺序与消息压力控制。

---

## 4. 协同协议

### 4.1 总体通信方式

- 业务接口：HTTP JSON
- 协同接口：WebSocket
- 协同数据：Yjs update + awareness

### 4.2 WebSocket 连接信息

- Base URL：`ws://localhost:8080/ws/collaboration`
- 连接参数：
  - `token`：用户鉴权凭证
  - `docId`：文档 ID

示例：

```text
ws://localhost:8080/ws/collaboration?token=<TOKEN>&docId=<DOC_ID>
```

### 4.3 握手协议

握手阶段需要完成：

1. 解析 `token`。
2. 解析 `docId`。
3. 验证用户身份。
4. 校验用户对文档是否具备访问或编辑权限。
5. 将 `userId`、`docId` 写入会话上下文。

### 4.4 消息类型

协同消息建议统一采用如下结构：

```json
{
  "type": "sync | update | awareness | ping | pong | save | rollback | error",
  "docId": 1001,
  "requestId": "uuid-optional",
  "payload": {}
}
```

#### 4.4.1 `sync`

用于首次进入房间、重连后的状态同步、缺失状态补齐。

```json
{
  "type": "sync",
  "docId": 1001,
  "payload": {
    "snapshot": "...",
    "stateVector": "...",
    "version": 12
  }
}
```

#### 4.4.2 `update`

用于传输 Yjs 增量更新。

```json
{
  "type": "update",
  "docId": 1001,
  "requestId": "req-001",
  "payload": {
    "update": "base64-or-binary-encoded-yjs-update",
    "clientId": 2,
    "origin": "local",
    "seqNo": 18
  }
}
```

#### 4.4.3 `awareness`

用于同步光标、选区、用户在线状态。

```json
{
  "type": "awareness",
  "docId": 1001,
  "payload": {
    "userId": 2,
    "nickname": "Alice",
    "color": "#8b5cf6",
    "cursor": {
      "from": 10,
      "to": 14
    },
    "active": true,
    "lastSeenAt": 1716790000000
  }
}
```

#### 4.4.4 `ping` / `pong`

用于心跳保活、连接存活检测。

```json
{ "type": "ping", "docId": 1001, "payload": { "ts": 1716790000000 } }
```

```json
{ "type": "pong", "docId": 1001, "payload": { "ts": 1716790000000 } }
```

#### 4.4.5 `save`

用于触发快照持久化或手动保存。

```json
{
  "type": "save",
  "docId": 1001,
  "payload": {
    "reason": "manual | autosave | snapshot"
  }
}
```

#### 4.4.6 `rollback`

用于回滚到指定版本。

```json
{
  "type": "rollback",
  "docId": 1001,
  "payload": {
    "versionId": 12
  }
}
```

#### 4.4.7 `error`

用于返回权限不足、文档不存在、消息格式错误等异常。

```json
{
  "type": "error",
  "docId": 1001,
  "payload": {
    "code": "FORBIDDEN",
    "message": "无权限执行此操作"
  }
}
```

---

## 5. 协同流程

### 5.1 首次进入文档

1. 前端先通过 HTTP 获取文档元数据和最新快照。
2. 前端建立 WebSocket 连接并完成握手鉴权。
3. 服务端验证用户权限后，将房间最新状态下发给客户端。
4. 前端初始化 `Y.Doc` 或编辑器状态，并开始接收协同消息。

### 5.2 实时编辑

1. 用户在编辑器内输入内容。
2. 前端将编辑动作转换为 Yjs transaction。
3. Yjs 生成增量 update。
4. 前端将 update 通过 WebSocket 发给服务端。
5. 服务端广播给同房间其他客户端。
6. 各客户端合并 update，最终收敛为一致状态。

### 5.3 在线状态同步

1. 用户移动光标或选区变化。
2. 前端更新 awareness 状态。
3. 服务端将 awareness 广播给同房间其他用户。
4. 其他客户端渲染协作者光标与昵称。

### 5.4 保存与落库

1. 服务端按时间或操作次数触发自动保存。
2. 先将 update 增量写入日志。
3. 再按周期合并为 snapshot 写入 `documents.latest_snapshot`。
4. 必要时写入 `document_versions` 形成历史版本。

### 5.5 断线重连

1. 客户端检测到连接断开。
2. 客户端执行指数退避重连。
3. 重连成功后重新同步 state vector。
4. 服务端补发缺失 update。
5. 客户端恢复本地编辑状态与 awareness 状态。

---

## 6. 表结构

### 6.1 `documents`（文档表）

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| title | VARCHAR(200) | 文档标题 |
| owner_id | BIGINT | 拥有者 ID |
| owner_name | VARCHAR(100) | 拥有者名称 |
| latest_snapshot | LONGTEXT | 最新文档快照 |
| version | INT | 乐观锁 / 版本号 |
| status | TINYINT | 状态（0-正常，1-回收站） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 6.2 `document_members`（协作者表）

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| user_id | BIGINT | 用户 ID |
| role | VARCHAR(20) | 角色（owner/editor/viewer/no_access） |
| joined_at | DATETIME | 加入时间 |

### 6.3 `document_versions`（版本快照表）

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| version_no | INT | 版本序号 |
| snapshot | LONGTEXT | 文档快照 |
| created_by | BIGINT | 创建人 ID |
| created_at | DATETIME | 创建时间 |

### 6.4 `document_collab_updates`（协同增量日志表，建议新增）

> 该表用于记录 Yjs update 增量，支撑重放、恢复、审计与快照合并。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| user_id | BIGINT | 操作用户 ID |
| client_id | VARCHAR(64) | 客户端标识 |
| request_id | VARCHAR(64) | 请求标识 |
| seq_no | BIGINT | 房间内递增序号 |
| update_payload | LONGBLOB / LONGTEXT | Yjs 增量数据 |
| update_format | VARCHAR(20) | 数据格式（binary/base64/json） |
| origin | VARCHAR(20) | 来源（local/remote/system） |
| status | VARCHAR(20) | 状态（APPLIED/PENDING/FAILED） |
| created_at | DATETIME | 创建时间 |

### 6.5 `document_collab_snapshots`（协同快照表，建议新增）

> 如果希望保留更细粒度的快照历史，建议独立快照表，而不是仅复用 `document_versions`。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| base_update_id | BIGINT | 对应的增量日志基线 |
| snapshot | LONGTEXT | 合并后的快照 |
| state_vector | LONGTEXT | 快照对应的状态向量 |
| created_by | BIGINT | 创建人 ID |
| created_at | DATETIME | 创建时间 |

### 6.6 `share_links`（分享链接表）

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| share_token | VARCHAR(128) | 分享 Token |
| permission | VARCHAR(20) | 授予权限（viewer/editor） |
| expire_time | DATETIME | 过期时间 |

---

## 7. Redis Key 设计

- `collab:online:users:{docId}`：文档当前在线协作者列表。
- `collab:session:{docId}`：文档实时协同会话元数据。
- `collab:last-save:{docId}`：最近一次自动保存时间点。
- `collab:lock:doc:{docId}`：文档回滚、删除、快照合并等操作的分布式锁。
- `collab:cursor:{docId}:{userId}`：协作者光标和选区的临时状态。
- `collab:update:queue:{docId}`：房间内待落库的增量队列（可选）。

---

## 8. 接口约定

### 8.1 文档管理接口

本部分沿用现有文档接口，不展开重复说明。

- `POST /api/documents`
- `GET /api/documents`
- `GET /api/documents/{id}`
- `PUT /api/documents/{id}`
- `DELETE /api/documents/{id}`

### 8.2 协作者接口

- `POST /api/documents/{id}/members`
- `GET /api/documents/{id}/members`
- `DELETE /api/documents/{id}/members/{userId}`

### 8.3 版本快照接口

- `GET /api/documents/{id}/versions`
- `POST /api/documents/{id}/snapshot`
- `POST /api/documents/{id}/rollback/{versionId}`

### 8.4 WebSocket 协同接口

- `ws://localhost:8080/ws/collaboration?token=<TOKEN>&docId=<DOC_ID>`

#### 8.4.1 连接后事件顺序建议

1. 握手鉴权。
2. 服务端下发 `sync`。
3. 客户端回传或请求缺失 update。
4. 客户端开始发送 `update` 与 `awareness`。
5. 服务端定时或触发 `save`。

---

## 9. Spring Boot 实现建议

### 9.1 模块划分

#### WebSocket 模块

- `WebSocketConfig`
- `CollaborationWebSocketHandler`
- `CollaborationHandshakeInterceptor`
- `CollaborationSessionManager`
- `CollaborationMessageDispatcher`

#### 文档协同模块

- `DocumentCollaborationService`
- `DocumentSnapshotService`
- `DocumentPermissionService`
- `DocumentVersionService`
- `DocumentUpdateLogService`

#### 持久化模块

- `DocumentMapper`
- `DocumentMemberMapper`
- `DocumentVersionMapper`
- `DocumentCollabUpdateMapper`
- `DocumentCollabSnapshotMapper`

### 9.2 处理流程

#### 握手阶段

1. 解析 `token` 与 `docId`。
2. 校验用户身份。
3. 校验文档权限。
4. 建立 session 并加入房间。
5. 记录在线用户信息。

#### 消息处理阶段

1. 解析消息类型。
2. 若为 `update`，写入增量日志并广播到房间其他客户端。
3. 若为 `awareness`，仅更新在线临时状态并广播。
4. 若为 `save`，触发快照合并与持久化。
5. 若为 `rollback`，从版本表恢复并通知所有客户端刷新。
6. 若消息异常，返回 `error` 并记录日志。

#### 断线处理阶段

1. 从在线会话与在线用户集合移除该连接。
2. 清理对应 awareness 状态。
3. 若需要，保留短暂重连窗口。
4. 重连成功后执行状态补齐。

---

## 10. 需要继续补充的实现点

如果后续要把方案彻底做成 Yjs 标准协同，建议继续补充：

- 前端 Yjs provider 封装。
- 服务端对 binary update 的兼容。
- 增量日志的序列号与幂等控制。
- state vector / diff sync 的协议细节。
- awareness 过期与节流策略。
- 自动快照合并与版本清理策略。
- 离线编辑本地缓存与恢复流程。

---

## 11. 结论

当前项目已经具备“协同编辑基础设施”，但距离完整 Yjs 协同还有补齐空间。建议后续实现按以下顺序推进：

1. 先统一协议为 Yjs update + awareness。
2. 再补增量日志与快照合并。
3. 最后补异常、重连、离线恢复和性能优化。

这样能保证文档、前端和后端在同一个协同方案下持续演进。