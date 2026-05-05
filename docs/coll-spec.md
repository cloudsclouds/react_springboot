# 文档与协同技术方案及接口文档

## 1. 目标

本方案用于支撑当前项目的文档管理、多人协同编辑、权限控制及版本快照功能，并保证前端页面、React 接口封装与 Spring Boot 后端的实现保持一致。当前阶段已完成手动保存版本的协作能力，下一阶段将切换为基于 WebSocket 的实时协同。

## 2. 技术方案

### 2.1 总体架构

- 前端：React + React Router + Yjs + WebSocket Client
- 后端：Spring Boot + MyBatis + Redis + WebSocket
- 数据库：MySQL
- 缓存：Redis
- 通信方式：HTTP JSON（业务接口）+ WebSocket（协同数据）

### 2.2 实施阶段

#### 阶段一：手动保存版本协作

1. 用户编辑文档内容。
2. 前端在用户点击保存时，将当前内容提交给 Spring Boot。
3. Spring Boot 持久化当前文档快照，并写入版本历史。
4. 用户可基于版本记录进行查看和回滚。

#### 阶段二：WebSocket 实时协作

1. 前端建立 WebSocket 连接并完成 Token 鉴权。
2. 服务端根据文档 ID、用户身份与成员角色确认访问权限。
3. 服务端向客户端下发最新文档状态。
4. 各客户端通过 WebSocket 实时同步编辑增量。
5. 服务端定期或在关键事件后将合并结果落库。

### 2.3 流程设计

#### 创建文档流程

1. 用户在前端页面点击“新建文档”。
2. 前端调用 Spring Boot 创建文档接口。
3. Spring Boot 在 MySQL 中创建文档元数据（`documents` 表），并自动将创建者设为 Owner 写入 `document_members`。
4. 返回文档 ID 后，前端跳转至文档编辑页。

#### 手动保存版本流程

1. 用户编辑完成后点击“保存”。
2. 前端将当前文档内容提交给 Spring Boot。
3. Spring Boot 更新 `documents.latest_snapshot` 并写入 `document_versions`。
4. 前端刷新保存状态并展示最新版本号。

#### WebSocket 协同编辑流程

1. 前端获取到文档 ID 及认证 Token 后，连接至 Spring Boot 提供的 WebSocket 协同端点。
2. Spring Boot WebSocket 服务在握手阶段校验 Token，并根据文档 ID、用户身份与成员角色检查编辑/阅读权限。
3. 服务端从数据库加载该文档的最新 Yjs 状态数据并下发给客户端。
4. 用户开始编辑，Yjs 通过 WebSocket 实时广播 changes 给同一 Room 下的其他连接用户。
5. Spring Boot 协同服务通过 debounce 机制，将合并后的最新 Snapshot 定期回存入 MySQL。

#### 版本快照与回滚流程

1. 用户手动或系统定期触发“保存快照”。
2. Spring Boot 接口记录当前文档快照，存入 `document_versions` 表。
3. 如需回滚，前端调用“回滚版本”接口。
4. Spring Boot 提取指定版本的快照，覆盖当前 `documents` 中的最新状态，并通知在线协同会话刷新内容。

### 2.4 当前实现说明

- 鉴权依赖现有的 `users` 表。现有 `users` 表包含字段：`id`, `username`, `email`, `password`, `nickname`, `created_at`。
- 文档创建时会写入 `owner_name`，并初始化一份结构化的 `latest_snapshot`，用于页面和测试环境直接预览正文内容。
- 多人协同依赖 WebSocket 建立实时连接。文档的文本状态（State Vector）由 Yjs 进行 CRDT 合并。
- 业务控制与协同状态同步均由 Spring Boot 提供，后端不再拆分 Node 协同服务。

### 2.5 数据存储

#### 数据库表结构

**1. documents（文档表）**

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| title | VARCHAR(200) | 文档标题 |
| owner_id | BIGINT | 拥有者 ID（外键关联 users.id） |
| owner_name | VARCHAR(100) | 拥有者名称 |
| latest_snapshot | LONGTEXT | 最新 Yjs 快照数据 |
| version | INT | 乐观锁/版本号 |
| status | TINYINT | 状态（0-正常，1-回收站） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**2. document_members（协作者表）**

多人协作权限核心：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| user_id | BIGINT | 用户 ID（外键关联 users.id） |
| role | VARCHAR(20) | 角色（owner, editor, viewer, no_access） |
| joined_at | DATETIME | 加入时间 |

**3. document_versions（文档版本快照表）**

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| version_no | INT | 版本序号 |
| snapshot | LONGTEXT | 快照数据 |
| created_by | BIGINT | 创建人 ID（外键关联 users.id） |
| created_at | DATETIME | 创建时间 |

**4. share_links（分享链接表）**

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| document_id | BIGINT | 文档 ID |
| share_token | VARCHAR(128) | 分享 Token |
| permission | VARCHAR(20) | 授予权限（viewer, editor） |
| expire_time | DATETIME | 过期时间 |

#### Redis Key 设计

- `collab:online:users:{docId}`：文档当前在线协作者列表。
- `collab:lock:doc:{docId}`：文档特殊操作（如回滚、删除）时的分布式防并发锁。
- `collab:session:{docId}`：文档实时协同会话元数据。
- `collab:last-save:{docId}`：最近一次自动保存时间点。

## 3. 接口约定

### 3.1 通用约定

- Base URL：`http://localhost:8080/api`
- WebSocket Base：`ws://localhost:8080/ws/collaboration`
- 请求方式：`POST` / `GET` / `PUT` / `DELETE`
- 请求头：`Content-Type: application/json`
- 鉴权信息：携带 Cookie / JWT Header / localStorage 中的 Token
- 返回格式：统一返回 JSON 对象

### 3.2 文档管理接口

#### 3.2.1 创建文档

- URL：`POST /api/documents`
- 请求参数：

```json
{
  "title": "我的新文档"
}
```

- 返回参数：

```json
{
  "success": true,
  "message": "创建成功",
  "documentId": 1001
}
```

#### 3.2.2 获取文档列表

- URL：`GET /api/documents`
- 功能：获取当前用户拥有或参与协作的文档列表
- 返回参数：

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "title": "我的新文档",
      "ownerId": 1,
      "ownerName": "管理员",
      "role": "owner",
      "updatedAt": "2023-10-01T12:00:00Z"
    }
  ]
}
```

#### 3.2.3 获取文档元数据

- URL：`GET /api/documents/{id}`
- 功能：获取文档的基础信息、作者名称、正文快照与权限
- `latestSnapshot` 仅存储内容本体，并尽量保持为 Tiptap 可消费的结构，例如：

```json
{
  "type": "doc",
  "content": [
    {
      "type": "heading",
      "attrs": { "level": 1 },
      "content": [{ "type": "text", "text": "产品需求文档" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "这是一个用于测试的产品需求文档快照。" }]
    },
    {
      "type": "bulletList",
      "content": [
        {
          "type": "listItem",
          "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "支持标题编辑" }] }]
        }
      ]
    }
  ]
}
```

- 返回参数：

```json
{
  "success": true,
  "data": {
    "id": 1001,
    "title": "我的新文档",
    "ownerId": 1,
    "ownerName": "管理员",
    "latestSnapshot": "{\"state\":\"...\"}",
    "role": "owner",
    "updatedAt": "2023-10-01T12:00:00Z"
  }
}
```

#### 3.2.4 修改文档标题

- URL：`PUT /api/documents/{id}`
- 请求参数：

```json
{
  "title": "重命名后的文档"
}
```

#### 3.2.5 删除文档

- URL：`DELETE /api/documents/{id}`
- 功能：将文档移入回收站或彻底删除。仅 Owner 可操作。

### 3.3 协作权限接口

#### 3.3.1 邀请协作者 / 修改权限

- URL：`POST /api/documents/{id}/members`
- 功能：添加协作者或更新现有协作者角色
- 请求参数：

```json
{
  "userId": 2,
  "role": "editor"
}
```

#### 3.3.2 获取协作者列表

- URL：`GET /api/documents/{id}/members`
- 返回参数：

```json
{
  "success": true,
  "data": [
    {
      "userId": 1,
      "nickname": "Admin",
      "role": "owner"
    },
    {
      "userId": 2,
      "nickname": "Paperdesk Team",
      "role": "editor"
    }
  ]
}
```

#### 3.3.3 移除协作者

- URL：`DELETE /api/documents/{id}/members/{userId}`
- 功能：移除某用户的协作权限

### 3.4 版本快照接口

#### 3.4.1 获取版本历史

- URL：`GET /api/documents/{id}/versions`

#### 3.4.2 手动保存当前版本

- URL：`POST /api/documents/{id}/snapshot`
- 功能：将当前文档内容保存为一个版本快照
- 请求参数：

```json
{
  "content": {
    "type": "doc",
    "content": []
  }
}
```

#### 3.4.3 回滚版本

- URL：`POST /api/documents/{id}/rollback/{versionId}`
- 功能：将文档状态回滚至指定版本

### 3.5 WebSocket 协同接口

#### 3.5.1 建立协同连接

- URL：`ws://localhost:8080/ws/collaboration?token=<TOKEN>&docId=<DOC_ID>`
- 鉴权：握手阶段校验 Token，并验证当前用户对文档的访问权限。
- 连接建立后，服务端会下发文档最新状态及协同会话信息。

#### 3.5.2 协同消息格式

服务端与前端通过 WebSocket 传递 JSON 消息，建议统一使用如下结构：

```json
{
  "type": "sync|awareness|save|rollback|error",
  "docId": 1001,
  "requestId": "uuid-optional",
  "payload": {}
}
```

#### 3.5.3 消息类型定义

##### sync

用于同步文档的初始状态或增量状态。

```json
{
  "type": "sync",
  "docId": 1001,
  "payload": {
    "snapshot": "...",
    "stateVector": "..."
  }
}
```

##### awareness

用于同步在线用户状态，例如光标、选区、正在输入标记。

```json
{
  "type": "awareness",
  "docId": 1001,
  "payload": {
    "userId": 2,
    "nickname": "Alice",
    "cursor": {
      "from": 10,
      "to": 14
    }
  }
}
```

##### save

用于服务端或客户端触发保存快照。

```json
{
  "type": "save",
  "docId": 1001,
  "payload": {
    "reason": "manual|autosave|snapshot"
  }
}
```

##### rollback

用于通知当前会话执行版本回滚并刷新状态。

```json
{
  "type": "rollback",
  "docId": 1001,
  "payload": {
    "versionId": 12
  }
}
```

##### error

用于返回权限不足、文档不存在、消息格式错误等异常信息。

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

#### 3.5.4 WebSocket 事件流

##### 连接建立

1. 客户端发起 WebSocket 握手。
2. 服务端校验 Token 与文档权限。
3. 服务端返回首屏快照与在线状态。

##### 编辑同步

1. 客户端编辑产生增量。
2. 客户端将增量发送给服务端。
3. 服务端合并增量并广播给其他在线用户。
4. 服务端按节流策略持久化最新内容。

##### 心跳机制

1. 客户端定期发送 `ping` 消息。
2. 服务端回复 `pong`。
3. 若多次未响应，则认为连接已断开并进行重连。

##### 断线重连

1. 客户端在断线后按退避策略重连。
2. 重连成功后重新请求最新快照。
3. 服务端根据版本号判断是否需要补发增量。

## 4. 前端与 Spring Boot 接入方式

### 4.1 接口封装

前端目前通过 `src/api/document.ts` 等文件统一封装文档与权限相关的 REST API：

- `createDocument(payload)`
- `fetchDocuments()`
- `fetchDocumentDetail(id)`
- `updateDocumentTitle(id, payload)`
- `addMember(id, payload)`
- `saveDocumentSnapshot(id, payload)`
- `rollbackDocumentVersion(id, versionId)`

### 4.2 WebSocket 协同连接

连接方式为 WebSocket：`ws://localhost:8080/ws/collaboration?token=<TOKEN>&docId=<DOC_ID>`

前端基于 Yjs 通过 WebSocket 与 Spring Boot 协同服务连接：

```javascript
import * as Y from 'yjs'

const ydoc = new Y.Doc()
const ws = new WebSocket('ws://localhost:8080/ws/collaboration?token=用户的认证凭证&docId=1001')

ws.onopen = () => {
  ws.send(JSON.stringify({ type: 'sync', docId: 1001, payload: {} }))
}
```

- Spring Boot 服务端接收到连接请求后，在握手和消息处理阶段完成权限校验、文档加载、状态广播与快照持久化。
- 前端在收到 `sync` 消息后初始化编辑器内容，在收到 `awareness` 消息后渲染在线协作者状态。

## 5. Spring Boot 实现建议

### 5.1 模块划分

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

#### 持久化模块

- `DocumentMapper`
- `DocumentMemberMapper`
- `DocumentVersionMapper`

### 5.2 处理流程

#### 握手阶段

1. 解析 `token` 与 `docId`。
2. 校验用户身份。
3. 校验当前用户是否有访问权限。
4. 建立 Session 并加入对应文档房间。

#### 消息处理阶段

1. 解析消息类型。
2. 根据消息类型执行同步、保存、回滚或状态广播。
3. 写入 Redis 在线用户集合。
4. 按策略更新数据库。

#### 状态持久化阶段

1. 接收客户端增量或完整快照。
2. 合并为最新文档内容。
3. 写入 `documents.latest_snapshot`。
4. 在必要时写入 `document_versions`。

### 5.3 关键实现建议

- WebSocket 建议按 `docId` 建房间，避免跨文档消息污染。
- 增量同步建议使用消息序列号或版本号，减少重复处理。
- 保存快照建议使用异步任务或节流队列，降低数据库写入压力。
- 回滚操作建议先加锁，再广播刷新消息，最后释放锁。
- 在线协作者状态建议保存在 Redis 中，断开时及时清理。

## 6. 校验与错误处理

### 6.1 权限校验

- 在进行编辑、删除、邀请成员、回滚版本等操作前，后端必须校验当前用户在该文档下的 `role`。
- `owner`：拥有所有权限（包括删除文档、修改他人权限）。
- `editor`：可读写。
- `viewer`：只读，无法修改内容。
- `no_access`：无权限访问文档。

### 6.2 常见错误信息

- `文档不存在或已被删除`
- `无权限执行此操作`
- `角色类型无效`
- `版本不存在`
- `WebSocket 鉴权失败`
- `文档协同会话已关闭`

## 7. 安全与优化建议

1. **Token 鉴权**：WebSocket 建立连接时需严格校验传入的 Token，避免未授权用户加入 Room。
2. **频率限制与 Debounce**：Spring Boot 协同端在保存快照时，应使用节流（Debounce）策略，如每隔几秒才持久化一次，降低数据库写入压力。
3. **分布式锁**：当用户请求文档版本回滚时，应锁定该文档的协同更新，等待服务端刷新状态后再放开，防止数据错乱。
4. **接口安全**：所有协同与业务接口都应通过统一鉴权、权限校验与日志审计进行保护。
5. **断线重连**：前端应在网络异常时自动重连，并恢复到最新文档状态。
6. **消息幂等**：服务端应对重复消息做幂等处理，避免重复保存或重复广播。

## 8. 后续可扩展项

- 分享链接模块（通过分享 Token 实现免登录或匿名用户的只读/编辑能力）
- 操作日志（Operation Log）与详细审计
- 针对块（Block）的划线与评论功能
- 文档模板中心
- 文件夹结构归类

## 9. 附：当前接口清单

| 功能 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 创建文档 | POST | /api/documents | 新建文档 |
| 文档列表 | GET | /api/documents | 获取我可编辑/只读的文档 |
| 文档元数据 | GET | /api/documents/{id} | 获取文档基础信息 |
| 重命名文档 | PUT | /api/documents/{id} | 修改文档标题 |
| 删除文档 | DELETE | /api/documents/{id} | 逻辑或物理删除文档 |
| 邀请/修改权限 | POST | /api/documents/{id}/members | 增改协作者权限 |
| 移除协作者 | DELETE | /api/documents/{id}/members/{userId} | 移除特定用户的协作权限 |
| 协作者列表 | GET | /api/documents/{id}/members | 查看当前所有协作者 |
| 版本历史 | GET | /api/documents/{id}/versions | 获取文档快照历史 |
| 手动保存快照 | POST | /api/documents/{id}/snapshot | 保存当前文档最新状态 |
| 回滚版本 | POST | /api/documents/{id}/rollback/{versionId} | 回滚至历史版本 |
| WebSocket 协同 | WS | /ws/collaboration?token=&docId= | 建立实时协同连接 |
