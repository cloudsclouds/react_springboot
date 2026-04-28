# 文档与协同技术方案及接口文档

## 1. 目标

本方案用于支撑当前项目的文档管理、多人协同编辑、权限控制及版本快照功能，并保证前端页面、React 接口封装、Spring Boot 后端及 Node.js 协同服务的实现保持高度一致。

## 2. 技术方案

### 2.1 总体架构

- 前端：React + React Router + Yjs + HocuspocusProvider
- 业务后端：Spring Boot + MyBatis + Redis
- 实时协同后端：Node.js + Hocuspocus (基于 Yjs)
- 数据库：MySQL
- 缓存：Redis
- 通信方式：HTTP JSON (业务接口) + WebSocket (协同数据)

### 2.2 流程设计

#### 创建文档流程

1. 用户在前端页面点击“新建文档”。
2. 前端调用 Spring Boot “创建文档” API。
3. Spring Boot 在 MySQL 中创建文档元数据（`documents`表），并自动将创建者设为 Owner 写入 `document_members`。
4. 返回文档 ID 后，前端跳转至文档编辑页并建立 WebSocket 连接。

#### 多人协同编辑流程

1. 前端获取到文档 ID 及认证 Token 后，连接至 Node 协同服务（WebSocket）。
2. Node 协同服务通过 Hook 校验 Token，并调用 Spring Boot 内部接口检查用户对该文档的编辑/阅读权限。
3. Node 服务从 Spring Boot / 数据库加载该文档的最新 Yjs 状态数据。
4. 用户开始编辑，Yjs 通过 WebSocket 实时广播 changes 给同一 Room 下的其他连接用户。
5. Node 协同服务通过 debounce 机制，将合并后的最新 Snapshot 定期回存入 MySQL。

#### 版本快照与回滚流程

1. 用户手动或系统定期触发“保存快照”。
2. Spring Boot 接口记录当前文档快照，存入 `document_versions` 表。
3. 如需回滚，前端调用“回滚版本”接口。
4. Spring Boot 提取指定版本的快照，覆盖当前 `documents` 中的最新状态，并通知 Node 服务刷新内存态。

### 2.3 当前实现说明

- 鉴权依赖现有的 `users` 表。现有 `users` 表包含字段：`id`, `username`, `email`, `password`, `nickname`, `created_at`。
- 文档创建时会写入 `owner_name`，并初始化一份结构化的 `latest_snapshot`，用于页面和测试环境直接预览正文内容。
- 多人协同依赖 WebSocket 建立实时连接。文档的文本状态（State Vector）由 Yjs 进行 CRDT 合并。
- 业务控制由 Spring Boot 提供 REST API，协同状态同步则专职由 Node 负责。

### 2.4 数据存储

#### 数据库表结构

**1. documents (文档表)**

| 字段名          | 类型         | 说明                         |
| --------------- | ------------ | ---------------------------- |
| id              | BIGINT       | 主键                         |
| title           | VARCHAR(200) | 文档标题                     |
| owner_id        | BIGINT       | 拥有者 ID (外键关联 users.id)|
| owner_name      | VARCHAR(100) | 拥有者名称                   |
| latest_snapshot | LONGTEXT     | 最新 Yjs 快照数据            |
| version         | INT          | 乐观锁/版本号                |
| status          | TINYINT      | 状态 (0-正常, 1-回收站)      |
| created_at      | DATETIME     | 创建时间                     |
| updated_at      | DATETIME     | 更新时间                     |

**2. document_members (协作者表)**

多人协作权限核心：

| 字段名      | 类型        | 说明                                    |
| ----------- | ----------- | --------------------------------------- |
| id          | BIGINT      | 主键                                    |
| document_id | BIGINT      | 文档 ID                                 |
| user_id     | BIGINT      | 用户 ID (外键关联 users.id)             |
| role        | VARCHAR(20) | 角色 (owner, editor, viewer, commenter) |
| joined_at   | DATETIME    | 加入时间                                |

**3. document_versions (文档版本快照表)**

| 字段名      | 类型     | 说明                         |
| ----------- | -------- | ---------------------------- |
| id          | BIGINT   | 主键                         |
| document_id | BIGINT   | 文档 ID                      |
| version_no  | INT      | 版本序号                     |
| snapshot    | LONGTEXT | 快照数据                     |
| created_by  | BIGINT   | 创建人 ID (外键关联 users.id)|
| created_at  | DATETIME | 创建时间                     |

**4. share_links (分享链接表)**

| 字段名      | 类型         | 说明                      |
| ----------- | ------------ | ------------------------- |
| id          | BIGINT       | 主键                      |
| document_id | BIGINT       | 文档 ID                   |
| share_token | VARCHAR(128) | 分享 Token                |
| permission  | VARCHAR(20)  | 授予权限 (viewer, editor) |
| expire_time | DATETIME     | 过期时间                  |

#### Redis Key 设计

- `collab:online:users:{docId}`：文档当前在线协作者列表。
- `collab:lock:doc:{docId}`：文档特殊操作（如回滚、删除）时的分布式防并发锁。

## 3. 接口约定

### 3.1 通用约定

- Base URL：`http://localhost:8080/api`
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

#### 3.4.2 回滚版本

- URL：`POST /api/documents/{id}/rollback/{versionId}`
- 功能：将文档状态回滚至指定版本

### 3.5 内部服务接口 (仅供 Node.js 调用)

#### 3.5.1 校验权限并加载快照

- URL：`GET /internal/docs/{id}/snapshot`
- 鉴权：内部调用白名单或内部 Secret
- 返回文档的最新的 Yjs snapshot 数据及用户的操作权限。

#### 3.5.2 增量/全量保存快照

- URL：`POST /internal/docs/saveSnapshot`
- 请求参数：

```json
{
  "docId": 1001,
  "snapshot": "..."
}
```

## 4. 前端与 Node 接入方式

### 4.1 接口封装

前端目前通过 `src/api/document.ts` 等文件统一封装文档与权限相关的 REST API：

- `createDocument(payload)`
- `fetchDocuments()`
- `updateDocumentTitle(id, payload)`
- `addMember(id, payload)`

### 4.2 Node 协同服务与前端连接

连接方式为 WebSocket：`ws://<node-server>/collab?token=<TOKEN>&docId=<DOC_ID>`

前端基于 Yjs 与 Hocuspocus 进行连接：

```javascript
import { HocuspocusProvider } from '@hocuspocus/provider'
import * as Y from 'yjs'

const ydoc = new Y.Doc()
const provider = new HocuspocusProvider({
  url: 'ws://localhost:1234/collab',
  name: `document-${docId}`,
  document: ydoc,
  token: '用户的认证凭证'
})
```

- Node 服务端接收到连接请求后，利用 Hooks (`onAuthenticate`, `onLoadDocument`) 调用 Spring Boot 的内部接口获取快照和验证权限。

## 5. 校验与错误处理

### 5.1 权限校验

- 在进行编辑、删除、邀请成员、回滚版本等操作前，后端必须校验当前用户在该文档下的 `role`。
- `owner`：拥有所有权限（包括删除文档、修改他人权限）。
- `editor`：只能修改内容。
- `viewer`：只读，无法修改内容。

### 5.2 常见错误信息

- `文档不存在或已被删除`
- `无权限执行此操作`
- `角色类型无效`
- `版本不存在`

## 6. 安全与优化建议

1. **Token 鉴权**：WebSocket 建立连接时需严格校验传入的 Token，避免未授权用户加入 Room。
2. **频率限制与 Debounce**：Node 端向 Spring Boot 保存快照时，应使用节流（Debounce）策略，如每隔几秒才持久化一次，降低数据库写入压力。
3. **分布式锁**：当用户请求文档版本回滚时，应锁定该文档的协同更新，等待 Node 服务刷新状态后再放开，防止数据错乱。
4. **内部接口安全**：Spring Boot 提供给 Node 的 `/internal/` 接口不能对外暴露，可以通过内网路由或带 Secret Key 校验。

## 7. 后续可扩展项

- 分享链接模块（通过分享 Token 实现免登录或匿名用户的只读/编辑能力）
- 操作日志（Operation Log）与详细审计
- 针对块（Block）的划线与评论功能
- 文档模板中心
- 文件夹结构归类

## 8. 附：当前接口清单

| 功能           | 方法   | 路径                                     | 说明                       |
| -------------- | ------ | ---------------------------------------- | -------------------------- |
| 创建文档       | POST   | /api/documents                           | 新建文档                   |
| 文档列表       | GET    | /api/documents                           | 获取我的/协作文档          |
| 文档元数据     | GET    | /api/documents/{id}                      | 获取文档基础信息           |
| 重命名文档     | PUT    | /api/documents/{id}                      | 修改文档标题               |
| 删除文档       | DELETE | /api/documents/{id}                      | 逻辑或物理删除文档         |
| 邀请/修改权限  | POST   | /api/documents/{id}/members              | 增改协作者权限             |
| 移除协作者     | DELETE | /api/documents/{id}/members/{userId}     | 移除特定用户的协作权限     |
| 协作者列表     | GET    | /api/documents/{id}/members              | 查看当前所有协作者         |
| 版本历史       | GET    | /api/documents/{id}/versions             | 获取文档快照历史           |
| 回滚版本       | POST   | /api/documents/{id}/rollback/{versionId} | 回滚至历史版本             |
| 获取快照(内部) | GET    | /internal/docs/{id}/snapshot             | Node加载文档快照并校验权限 |
| 保存快照(内部) | POST   | /internal/docs/saveSnapshot              | Node定期保存最新状态       |
