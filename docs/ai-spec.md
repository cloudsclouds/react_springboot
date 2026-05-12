# AI 能力技术方案与接口文档（AI 对话 + AI 智能体）

> 本文档只定义 AI 域能力：
>
> 1. 独立 AI 对话（会话、流式、停止、RAG 增强）
> 2. 编辑器内 AI 智能体（多智能体路由与任务执行）

## 1. 目标

在现有 React + Spring Boot 项目中建设两条并行 AI 能力线：

1. **AI 对话线（独立页面）**：会话管理、流式回复、停止生成、RAG 引用返回。
2. **AI 智能体线（编辑器内）**：面向写作场景的多智能体能力（续写、润色、翻译、总结、Mermaid 等）。

---

## 2. 范围与边界

- `/api/ai/`** 全部接口
- AI 会话、消息、流式协议
- AI 停止控制与状态落库
- RAG 增强问答（先检索再生成）
- 编辑器内多智能体编排与回填策略

---

## 3. 总体架构

- 前端：React（独立 AI 页面 + KnowledgeBase 编辑器交互）
- 后端：Spring Boot + Spring AI + MyBatis
- 数据：MySQL（会话/消息/日志） + Redis（停止标记/短期状态）
- 检索依赖：复用 KB 域的 Redis Stack 向量检索能力
- 通信：HTTP JSON + SSE

AI 调用链路：

`FrontEnd -> AI Gateway (/api/ai/**) -> AI Orchestrator -> Router Agent -> Task Agent / Chat Workflow -> LLM(+RAG)`

---

## 4. 模块设计

### 4.1 AI 对话模块

- `ai/controller`：会话、流式、停止等 API
- `ai/service`：会话管理、消息持久化
- `ai/workflow/chat`：对话编排（RAG + 生成）

### 4.2 AI 智能体模块

- `ai/router`：意图识别与任务路由
- `ai/workflow/agent`：各任务工作流
- `ai/prompt`：模板与输出约束
- `ai/tooling`：格式修复、Mermaid 校验等工具层

---

## 5. 数据模型

### 5.1 会话表 `ai_conversation`


| 字段名        | 类型           | 说明              |
| ---------- | ------------ | --------------- |
| id         | BIGINT       | 主键              |
| user_id    | BIGINT       | 会话所属用户          |
| title      | VARCHAR(200) | 会话标题            |
| summary    | VARCHAR(255) | 摘要              |
| status     | TINYINT      | 状态（0 正常 / 1 删除） |
| use_rag    | TINYINT(1)   | 默认 RAG 开关       |
| created_at | DATETIME     | 创建时间            |
| updated_at | DATETIME     | 更新时间            |


### 5.2 消息表 `ai_conversation_message`


| 字段名             | 类型          | 说明                                        |
| --------------- | ----------- | ----------------------------------------- |
| id              | BIGINT      | 主键                                        |
| conversation_id | BIGINT      | 会话 ID                                     |
| role            | VARCHAR(20) | user / assistant / system                 |
| content         | LONGTEXT    | 消息内容                                      |
| status          | VARCHAR(20) | GENERATING / COMPLETED / STOPPED / FAILED |
| request_id      | VARCHAR(64) | 请求唯一标识                                    |
| created_at      | DATETIME    | 创建时间                                      |
| updated_at      | DATETIME    | 更新时间                                      |


### 5.3 编辑器 AI 操作日志 `kb_ai_operation_log`


| 字段名           | 类型          | 说明                                   |
| ------------- | ----------- | ------------------------------------ |
| id            | BIGINT      | 主键                                   |
| user_id       | BIGINT      | 用户 ID                                |
| article_id    | BIGINT      | 文章 ID                                |
| request_id    | VARCHAR(64) | 请求 ID                                |
| intent        | VARCHAR(50) | polish/summary/translate/mermaid/... |
| entry_point   | VARCHAR(50) | selection/context-menu/toolbar       |
| input_text    | LONGTEXT    | 输入                                   |
| selected_text | LONGTEXT    | 选中文本                                 |
| output_text   | LONGTEXT    | 输出                                   |
| result_action | VARCHAR(30) | replace/insertAfter/appendBlock/...  |
| status        | VARCHAR(20) | SUCCESS/FAILED/STOPPED               |
| created_at    | TIMESTAMP   | 创建时间                                 |


### 5.4 Redis Key

- `ai:stop:{conversationId}`：独立 AI 会话停止标识
- `kb:ai:stop:{articleId}:{requestId}`：编辑器 AI 任务停止标识

---

## 6. AI 对话（独立页面）

### 6.1 路由约定

- 基础路由：`/ai`
- 会话路由：`/ai/:conversationId`

行为：

1. 进入 `/ai/:conversationId`：先查会话，存在则加载；不存在则新建并跳转
2. 进入 `/ai`：无有效会话则自动创建并跳转
3. 切换会话时 URL 与当前会话保持一致

### 6.2 流式请求主流程

1. 前端提交 `conversationId + message + requestId (+ useRag/topK/articleId)`
2. 后端写入 user 消息
3. 创建 assistant 草稿消息（`GENERATING`）
4. 若开启 RAG：先检索（调用 KB 检索能力），再注入上下文
5. 启动 LLM 流式生成并推送 SSE
6. 完成：assistant 消息更新为 `COMPLETED`
7. 停止：更新为 `STOPPED` 并保留已生成内容
8. 异常：更新为 `FAILED`

### 6.3 SSE 事件协议

- `rag-start`
- `rag-result`
- `message-start`
- `message-delta`
- `message-end`
- `message-stop`
- `message-error`

---

## 7. AI 智能体（编辑器内，多智能体）

## 7.1 能力目标

在 Tiptap 编辑器中，支持右键/选区触发 AI 操作，并将结果回填到文档。

首批能力：

- 续写（continue）
- 润色（polish）
- 头脑风暴（brainstorm）
- 翻译（translate）
- 总结（summary）
- Mermaid 生成（mermaid）

### 7.2 多智能体架构

- `Router Agent`：识别意图、选择任务智能体、组装统一上下文
- `Continue Agent`
- `Polish Agent`
- `Brainstorm Agent`
- `Translate Agent`
- `Summary Agent`
- `Mermaid Agent`

### 7.3 输入与输出协议

输入（示例）：

```json
{
  "articleId": 10001,
  "requestId": "req-30001",
  "entryPoint": "selection",
  "action": "polish",
  "selectedText": "原始选中文本",
  "surroundingContext": "光标附近上下文",
  "options": {
    "tone": "professional",
    "targetLanguage": "en"
  }
}
```

输出（示例）：

```json
{
  "intent": "polish",
  "outputType": "markdown",
  "outputText": "润色后的文本",
  "resultAction": "replace",
  "meta": {
    "confidence": 0.94
  }
}
```

### 7.4 回填策略

- `replace`：替换选区
- `insertAfter`：插入到选区后
- `appendBlock`：追加到文末
- `insertMermaid`：插入 Mermaid 块
- `previewOnly`：只预览不落文档

### 7.5 MVP 实施优先级

1. 打通单能力闭环（建议先 `polish`）
2. 扩展六个能力统一协议
3. 增加质量校验与格式修复（后处理 Agent/Tools）

---

## 8. 接口约定

### 8.1 通用

- Base URL：`http://localhost:8080/api`
- Content-Type：`application/json`
- 鉴权：需要登录
- 返回：`ApiResponse<T>`（SSE 接口除外）

### 8.2 统一响应示例

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

---

## 9. AI 对话接口文档

### 9.1 创建会话

- `POST /api/ai/conversations`

### 9.2 查询会话列表

- `GET /api/ai/conversations`

### 9.3 查询会话详情

- `GET /api/ai/conversations/{conversationId}/detail`

### 9.4 同步会话（可选）

- `POST /api/ai/chat`
- 默认先检索（可通过 `useRag=false` 关闭）

### 9.5 流式会话

- `POST /api/ai/chat/stream`
- `text/event-stream`

### 9.6 停止生成

- `POST /api/ai/chat/stop`

---

## 10. 编辑器 AI 智能体接口文档（建议）

> 若当前尚未完全落地，可先按此契约实现，逐步替换旧调用。

### 10.1 编辑器 AI 同步请求

- `POST /api/ai/editor/execute`
- 功能：执行一次编辑器 AI 任务并返回结果

### 10.2 编辑器 AI 流式请求

- `POST /api/ai/editor/stream`
- 功能：流式返回编辑器 AI 任务结果（大段润色/总结推荐）

### 10.3 停止编辑器 AI

- `POST /api/ai/editor/stop`
- 功能：停止指定 `articleId + requestId` 的任务

### 10.4 查询编辑器 AI 日志

- `GET /api/kb/articles/{articleId}/ai-logs`
- 功能：查看任务记录、失败原因、回填动作

---

## 11. 错误处理

### 11.1 常见错误

- 会话不存在 / 无权限
- 请求内容为空
- RAG 检索失败
- LLM 调用失败
- 流式中断
- 停止失败或重复停止
- Mermaid 语法无效

### 11.2 原则

- user 消息成功后必须可追踪
- assistant 生成中断也要保留已生成片段
- 停止标识在任务结束后及时清理
- 智能体任务失败要返回可展示错误文案与 requestId

---

## 12. 后续扩展

- 多模型路由（按任务类型选择模型）
- Prompt 模板配置中心
- Agent 质量评估器（自动重试/重写）
- 会话长期记忆（摘要压缩）
- 速率限制、配额、用量统计

---

## 13. 附：AI 接口清单


| 能力            | 方法   | 路径                                            | 说明        |
| ------------- | ---- | --------------------------------------------- | --------- |
| 创建会话          | POST | /api/ai/conversations                         | 新建对话会话    |
| 会话列表          | GET  | /api/ai/conversations                         | 当前用户会话    |
| 会话详情          | GET  | /api/ai/conversations/{conversationId}/detail | 会话 + 消息   |
| 同步问答          | POST | /api/ai/chat                                  | 同步返回答案    |
| 流式问答          | POST | /api/ai/chat/stream                           | SSE 流式输出  |
| 停止对话生成        | POST | /api/ai/chat/stop                             | 停止当前会话    |
| 编辑器 AI 执行（建议） | POST | /api/ai/editor/execute                        | 智能体任务同步执行 |
| 编辑器 AI 流式（建议） | POST | /api/ai/editor/stream                         | 智能体任务流式执行 |
| 编辑器 AI 停止（建议） | POST | /api/ai/editor/stop                           | 停止编辑器任务   |
| 编辑器 AI 日志     | GET  | /api/kb/articles/{articleId}/ai-logs          | 查询任务记录    |


