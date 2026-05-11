# AI 对话技术方案及接口文档

## 1. 目标

本方案用于在当前 Spring Boot 后端中新增 AI 对话能力，支持会话创建、流式回复、会话记忆、停止生成、会话查询以及对生成中断场景的记录补全，保证前端页面、接口封装与后端实现保持一致。

当前阶段重点实现：

1. 项目集成 AI
2. 新建会话
3. 流式会话
4. 大模型配置统一通过配置文件管理
5. 停止生成
6. 会话记忆与会话查询
7. 解决停止生成后不存储记录的问题
8. 停止生成标识容器使用 Redis
9. 基于 MySQL 实现对话记忆

## 2. 技术方案

### 2.1 总体架构

- 前端：React
- 后端：Spring Boot + Spring AI + MyBatis
- 数据库：MySQL
- 缓存：Redis
- 通信方式：HTTP JSON + SSE 流式响应

### 2.2 模块划分

建议新增一个独立的 AI 模块，例如：

- `ai/controller`
- `ai/service`
- `ai/service/impl`
- `ai/dto`
- `ai/entity`
- `ai/mapper`
- `ai/config`
- `ai/constant`
- `ai/util`

### 2.3 实施流程

#### 1. 会话创建

1. 用户进入 AI 页面。
2. 前端调用创建会话接口。
3. 后端在 MySQL 中创建一条会话记录。
4. 会话默认归属当前登录用户，标题未传时使用默认标题。
5. 返回会话 ID，前端进入对话页。

#### 2. 流式对话

1. 用户发送消息。
2. 前端将会话 ID、用户输入和本次 `useRag` 选择提交给后端。
3. 后端先将本次 `useRag` 写回会话配置（按会话记忆）。
4. 后端将用户消息落库。
5. 后端根据会话历史拼接上下文。
6. 若会话 `useRag=1`，先执行知识检索并注入引用；否则直接调用大模型。
7. 后端通过 SSE 持续推送增量结果给前端。
8. 生成结束后，将 assistant 回复落库。

#### 3. 停止生成

1. 前端点击“停止生成”。
2. 后端向 Redis 写入停止标识。
3. 正在流式输出的请求检测到停止标识后中断生成。
4. 后端把已生成内容补写入数据库，避免丢失记录。

#### 4. 会话记忆

1. 每次请求时，后端根据会话 ID 查询 MySQL 中的历史消息。
2. 取最近若干轮消息作为上下文。
3. 拼接 system prompt、历史消息和当前用户输入，发送给大模型。

## 3. 配置设计

### 3.1 大模型配置

当前后端已改为按百炼 DashScope Java SDK 方式接入，配置统一放在 `application.properties` 中：

- `ai.api-key`
- `ai.base-url`
- `ai.model-name`
- `ai.temperature`
- `ai.max-tokens`

示例：

```properties
ai.api-key=${DASHSCOPE_API_KEY:}
ai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
ai.model-name=qwen-plus
ai.temperature=0.7
ai.max-tokens=2048
```

### 3.2 SDK 版本管理

DashScope SDK 版本建议使用 `2.12.0` 或更高版本，以保证与示例代码和百炼接口兼容。

业务依赖直接声明坐标和版本：

```xml
<dependency>
  <groupId>com.alibaba</groupId>
  <artifactId>dashscope-sdk-java</artifactId>
  <version>2.12.0</version>
</dependency>
```

## 4. 数据存储

### 4.1 会话表 `ai_conversation`

用于存储对话会话信息。


| 字段名        | 类型           | 说明             |
| ---------- | ------------ | -------------- |
| id         | BIGINT       | 主键             |
| user_id    | BIGINT       | 用户 ID，表示会话所属人  |
| title      | VARCHAR(200) | 会话标题           |
| summary    | VARCHAR(255) | 内容摘要，默认值为“空对话” |
| status     | TINYINT      | 状态（0-正常，1-删除）  |
| use_rag    | TINYINT(1)   | 是否启用 RAG（0-关闭，1-开启） |
| created_at | DATETIME     | 创建时间           |
| updated_at | DATETIME     | 更新时间           |


### 4.1.1 新建会话规则

- 会话创建时必须绑定当前登录用户。
- 前端不传标题时，后端使用默认标题，例如“新对话”。
- 新建会话时摘要默认设置为“空对话”，与初始化数据保持一致。
- 创建成功后返回会话 ID，供前端跳转到对话页。

### 4.1.2 当前实现说明

- 后端已提供 `POST /api/ai/conversations` 接口。
- 创建成功后返回 `conversationId`、`title`、`summary` 和 `userId`。
- 数据持久化表为 `ai_conversation`，由 MyBatis 负责插入记录并回填主键。

### 4.2 消息表 `ai_conversation_message`

用于存储会话中的所有消息，包括用户消息和 AI 回复。


| 字段名             | 类型          | 说明                                              |
| --------------- | ----------- | ----------------------------------------------- |
| id              | BIGINT      | 主键                                              |
| conversation_id | BIGINT      | 会话 ID                                           |
| role            | VARCHAR(20) | 角色（user / assistant / system）                   |
| content         | LONGTEXT    | 消息内容                                            |
| status          | VARCHAR(20) | 消息状态（GENERATING / COMPLETED / STOPPED / FAILED） |
| request_id      | VARCHAR(64) | 本次请求唯一标识                                        |
| created_at      | DATETIME    | 创建时间                                            |
| updated_at      | DATETIME    | 更新时间                                            |


### 4.3 Redis Key 设计

Redis 主要用于停止生成标识。

- `ai:stop:{conversationId}`：当前会话是否需要停止生成
- value：`1`
- TTL：建议 10～30 分钟

### 4.4 设计说明

- MySQL 用于保存会话和消息历史，承担“记忆”职责
- Redis 仅用于“停止生成”的临时控制，不参与长期存储
- 停止标识应在生成结束后主动清理，避免影响下一次对话

## 5. 接口约定

### 5.1 通用约定

- Base URL：`http://localhost:8080/api`
- 请求方式：`POST` / `GET`
- 请求头：`Content-Type: application/json`
- 返回格式：统一返回 JSON
- 流式接口：使用 `text/event-stream`

### 5.2 统一响应格式

建议继续沿用你项目里的 `ApiResponse<T>` 风格：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

## 6. 接口文档

### 6.1 创建会话

- URL：`POST /api/ai/conversations`
- 功能：创建一个新的 AI 会话
- 鉴权：需要登录，使用当前用户作为会话归属人

#### 请求参数

```json
{
  "title": "新对话"
}
```

#### 返回参数

```json
{
  "success": true,
  "message": "创建成功",
  "data": {
    "conversationId": 10001,
    "title": "新对话",
    "userId": 1
  }
}
```

### 6.2 查询会话列表

- URL：`GET /api/ai/conversations`
- 功能：查询当前用户的会话列表
- 鉴权：需要登录，仅返回当前用户的数据

#### 返回参数

```json
{
  "success": true,
  "message": "查询成功",
  "data": [
    {
      "conversationId": 10001,
      "title": "新对话",
      "lastMessageAt": "2026-05-08T10:00:00",
      "summary": "空"
    }
  ]
}
```

#### 当前实现说明

- 后端已提供 `GET /api/ai/conversations` 接口。
- 接口返回当前登录用户的会话列表。
- 当前实现优先返回 `conversationId`、`title`、`lastMessageAt`、`summary`，其中 `lastMessageAt` 先使用会话更新时间。

### 6.3 查询会话详情

- URL：`GET /api/ai/conversations/{conversationId}/detail`
- 功能：查询单个会话的基础信息，并返回该会话下全部消息
- 鉴权：需要登录，仅允许查看当前用户自己的会话
- 前端行为：点击历史会话列表项时，前端应主动请求该详情接口并刷新当前对话区域的消息内容

#### 返回参数

```json
{
  "conversationId": 10001,
  "title": "新对话",
  "summary": "空对话",
  "createdAt": "2026-05-08T09:00:00",
  "updatedAt": "2026-05-08T10:00:00",
  "messages": [
    {
      "messageId": 90001,
      "role": "user",
      "content": "你好",
      "status": "COMPLETED",
      "requestId": "req-001",
      "createdAt": "2026-05-08T09:01:00",
      "updatedAt": "2026-05-08T09:01:00"
    },
    {
      "messageId": 90002,
      "role": "assistant",
      "content": "你好，有什么可以帮你？",
      "status": "COMPLETED",
      "requestId": "req-001",
      "createdAt": "2026-05-08T09:01:02",
      "updatedAt": "2026-05-08T09:01:02"
    }
  ]
}
```

#### 当前实现说明

- 后端已提供 `GET /api/ai/conversations/{conversationId}/detail` 接口。
- 接口会校验会话归属，仅允许当前登录用户访问。
- 返回会话基础信息和该会话的全部消息列表，消息按创建时间升序排列。

### 6.4 流式会话

- URL：`POST /api/ai/chat/stream`
- 功能：基于会话上下文发起流式问答
- 返回方式：`text/event-stream`

#### 请求参数

```json
{
  "conversationId": 10001,
  "message": "帮我总结一下今天的工作",
  "requestId": "req-20260511-001",
  "useRag": true,
  "topK": 5
}
```

#### SSE 事件格式

##### 1. 开始事件

```json
{
  "type": "message-start",
  "conversationId": 10001,
  "requestId": "req-xxx"
}
```

##### 2. 增量输出

```json
{
  "type": "message-delta",
  "content": "今天"
}
```

##### 3. 结束事件

```json
{
  "type": "message-end",
  "conversationId": 10001,
  "messageId": 90001,
  "status": "COMPLETED"
}
```

##### 4. 停止事件

```json
{
  "type": "message-stop",
  "conversationId": 10001,
  "messageId": 90001,
  "status": "STOPPED"
}
```

##### 5. 错误事件

```json
{
  "type": "message-error",
  "message": "模型调用失败"
}
```

### 6.5 停止生成

- URL：`POST /api/ai/chat/stop`
- 功能：停止当前会话的流式生成

#### 请求参数

```json
{
  "conversationId": 10001
}
```

#### 处理逻辑

1. 后端写入 Redis 停止标识
2. 正在生成的流式请求轮询或检查标识
3. 命中标识后立即终止输出
4. 将已生成内容补写到 MySQL

#### 返回参数

```json
{
  "success": true,
  "message": "已请求停止生成",
  "data": null
}
```

## 7. 核心实现建议

### 7.1 会话记忆实现

每次调用 AI 前，从 MySQL 查询当前会话最近若干条消息，组成上下文：

- system prompt
- 历史 user 消息
- 历史 assistant 消息
- 当前用户输入

建议只取最近 N 轮，避免上下文过长。

### 7.2 停止生成实现

- Redis key 作为停止信号
- 流式输出过程中周期性检查 Redis 标识
- 一旦停止，立即中断流并结束 SSE 推送

### 7.3 解决停止后不落库的问题

建议采用“草稿消息 + 最终补写”策略：

1. 用户发送消息后，先插入 user 消息
2. 再插入一条 assistant 草稿消息，状态为 `GENERATING`
3. 流式过程中不断累积内容
4. 若正常结束，更新为 `COMPLETED`
5. 若被停止，更新为 `STOPPED`，并保存当前已生成内容

这样可以保证即使停止生成，也不会丢失记录。

### 7.4 配置管理建议

- 大模型参数统一放配置文件
- 后续你自己补充 `api-key`、`base-url`、`model-name`
- 当前版本先不接 Nacos，不做配置热更新

## 8. 错误处理

### 8.1 常见错误

- `会话不存在`
- `会话已删除`
- `请求内容为空`
- `模型调用失败`
- `停止生成失败`
- `消息保存失败`

### 8.2 失败处理原则

- 用户消息入库失败时，直接返回错误
- 模型调用失败时，assistant 消息标记为 `FAILED`
- 停止后要保证当前生成内容至少部分落库
- Redis 标识在结束后要及时清理

## 9. 后续可扩展项

- 多模型切换
- system prompt 可配置化
- 会话标题自动生成
- 消息编辑与重新生成
- 长上下文摘要记忆
- 多轮对话限流与配额控制
- AI 使用量统计

## 10. 附：接口清单


| 功能     | 方法   | 路径                                              | 说明         |
| ------ | ---- | ----------------------------------------------- | ---------- |
| 创建会话   | POST | /api/ai/conversations                           | 新建 AI 对话会话 |
| 查询会话列表 | GET  | /api/ai/conversations                           | 获取当前用户会话列表 |
| 查询会话详情 | GET  | /api/ai/conversations/{conversationId}/detail   | 获取会话基础信息   |
| 查询会话消息 | GET  | /api/ai/conversations/{conversationId}/messages | 获取会话消息记录   |
| 流式会话   | POST | /api/ai/chat/stream                             | 发起流式 AI 对话 |
| 停止生成   | POST | /api/ai/chat/stop                               | 停止当前会话生成   |
| 查询停止状态 | GET  | /api/ai/chat/stop-status/{conversationId}       | 查询会话是否停止生成 |


