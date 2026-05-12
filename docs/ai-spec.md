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

- `/api/ai/` 全部接口
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

- `ai/editor/controller`：编辑器 AI 接口
- `ai/editor/router`：按 action 路由到具体智能体
- `ai/editor/agent`：各任务智能体实现（MVP 先落地 `polish`）
- `ai/editor/service`：执行、流式、停止、日志查询
- `ai/editor/mapper`：AI 操作日志持久化
- `ai/editor/tooling`：后续可扩展格式修复、Mermaid 校验等工具层

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


### 5.3 通用操作日志表 `knowledge_article_operation_log`

> 仅记录 AI 相关操作，不记录普通手动编辑。该表用于追踪 AI 编辑请求、响应、状态、耗时与回滚链路。


| 字段名              | 类型           | 说明                                                                |
| ---------------- | ------------ | ----------------------------------------------------------------- |
| id               | BIGINT       | 主键                                                                |
| user_id          | BIGINT       | 操作用户 ID                                                           |
| article_id       | BIGINT       | 文章 ID                                                             |
| conversation_id  | BIGINT       | 可选，会话 ID（AI 对话场景）                                                 |
| request_id       | VARCHAR(64)  | 可选，请求标识（AI 场景）                                                    |
| operation_type   | VARCHAR(30)  | 操作类型（AI_APPLY / AI_GENERATE / AI_CANCEL / AI_RETRY / UNDO / REDO） |
| change_mode      | VARCHAR(20)  | 变更模式（SNAPSHOT / DELTA）                                            |
| intent           | VARCHAR(50)  | 可选，AI 意图（polish/summary/...）                                      |
| entry_point      | VARCHAR(50)  | 入口（selection/context-menu/toolbar/editor）                         |
| input_text       | LONGTEXT     | 可选，AI 输入                                                          |
| selected_text    | LONGTEXT     | 可选，选中文本                                                           |
| output_text      | LONGTEXT     | 可选，AI 输出                                                          |
| result_action    | VARCHAR(30)  | 可选，回填动作                                                           |
| before_snapshot  | LONGTEXT     | 变更前快照（局部或整文）                                                      |
| after_snapshot   | LONGTEXT     | 变更后快照（局部或整文）                                                      |
| delta_json       | LONGTEXT     | 增量内容（JSON Patch 或自定义 diff）                                        |
| ref_operation_id | BIGINT       | 关联原操作（UNDO/REDO 使用）                                               |
| status           | VARCHAR(20)  | 状态（SUCCESS / FAILED / STOPPED）                                    |
| error_message    | VARCHAR(500) | 失败原因                                                              |
| latency_ms       | INT          | 处理耗时                                                              |
| created_at       | TIMESTAMP    | 创建时间                                                              |


设计说明：

- 仅当操作属于 AI 生成、AI 应用、AI 撤销/重做、AI 取消或 AI 重试时写入该表。
- 普通编辑、普通删除、普通剪切/粘贴、前端撤销/重做栈切换都不写入该表。
- `UNDO` / `REDO` 记录通过 `ref_operation_id` 指向被影响的原操作。
- 若只做快照，可只写 `before_snapshot` / `after_snapshot`，忽略 `delta_json`。
- AI 操作若最终未落库，仅记录过程日志；若落库成功，也应在版本表生成对应版本。

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
  "chatInput": "请把这段话润色得更专业"
}
```

说明：

- `action` 可传，也可为空；为空时由 `Router Agent` 先做意图识别，再路由到具体智能体
- `Router Agent` 意图识别候选集合：`continue / polish / summary / translate / mermaid`
- `surroundingContext` 建议传选区前后局部内容，MVP 可控制在 200～500 字左右
- `chatInput` 为用户输入框指令，优先级高于上下文
- `options` 先删除，所有能力按固定协议实现

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

#### 7.3.0 polish 示例

输入：

```json
{
  "articleId": 10001,
  "requestId": "req-20260512-0001",
  "entryPoint": "selection",
  "action": "polish",
  "selectedText": "天蓝水绿",
  "surroundingContext": "九寨沟风景很美",
  "chatInput": "请润色这段话，写成100字，让它表达更优美。"
}
```

输出：

```json
{
    "success": true,
    "message": "执行成功",
    "data": {
        "intent": "polish",
        "outputType": "markdown",
        "outputText": "九寨沟风光如画，天穹湛蓝如洗，湖水碧绿似玉，山色空蒙，水光潋滟，仿佛天地间最纯净的蓝与最鲜活的绿在此悄然相融，令人沉醉于这方人间仙境。",
        "resultAction": "replace",
        "meta": {
            "entryPoint": "selection",
            "chatInputLength": 22,
            "surroundingContextLength": 7,
            "action": "",
            "selectedTextLength": 4
        }
    }
}
```

#### 7.3.1 continue 示例

输入：

```json
{
  "articleId": 10001,
  "requestId": "req-continue-001",
  "entryPoint": "selection",
  "action": "continue",
  "selectedText": "我们将在下个季度重点推进用户增长策略。",
  "surroundingContext": "这就是增长目标和核心指标。",
  "chatInput": "请续写下一段，强调执行路径和风险控制。"
}
```

输出：

```json
{
    "success": true,
    "message": "执行成功",
    "data": {
        "intent": "continue",
        "outputType": "markdown",
        "outputText": "具体执行路径分为三步：首先，完成现有用户分层模型的迭代优化，确保高潜力用户识别准确率提升至92%以上；其次，基于分层结果，在7月启动定向裂变激励实验，覆盖TOP 20%活跃用户群，并同步上线个性化邀请链路；最后，8月中旬起将验证有效的策略模块化，嵌入自动化运营平台，实现全渠道触达、实时效果归因与动态策略调优闭环。",
        "resultAction": "insertAfter",
        "meta": {
            "intent": "continue",
            "action": ""
        }
    }
}
```

#### 7.3.2 summary 示例

输入：

```json
{
  "articleId": 10001,
  "requestId": "req-summary-001",
  "entryPoint": "selection",
  "action": "summary",
  "selectedText": "我们将在下个季度重点推进用户增长策略。(String), 具体执行路径分为三步：首先，完成现有用户分层模型的迭代优化，确保高潜力用户识别准确率提升至92%以上；其次，基于分层结果，在7月启动定向裂变激励实验，覆盖TOP 20%活跃用户群，并同步上线个性化邀请链路；最后，8月中旬起将验证有效的策略模块化，嵌入自动化运营平台，实现全渠道触达、实时效果归因与动态策略调优闭环。",
  "surroundingContext": "我们将在下个季度重点推进用户增长策略。(String), 具体执行路径分为三步：首先，完成现有用户分层模型的迭代优化，确保高潜力用户识别准确率提升至92%以上；其次，基于分层结果，在7月启动定向裂变激励实验，覆盖TOP 20%活跃用户群，并同步上线个性化邀请链路；最后，8月中旬起将验证有效的策略模块化，嵌入自动化运营平台，实现全渠道触达、实时效果归因与动态策略调优闭环。",
  "chatInput": "请总结要点，尽可能精简"
}
```

输出：

```json
{
    "success": true,
    "message": "执行成功",
    "data": {
        "intent": "summary",
        "outputType": "markdown",
        "outputText": "下季度用户增长策略三步走：  \n1. 迭代优化用户分层模型，高潜力用户识别准确率≥92%；  \n2. 7月启动定向裂变激励实验（覆盖TOP 20%活跃用户），同步上线个性化邀请链路；  \n3. 8月中旬起模块化有效策略，嵌入自动化运营平台，实现全渠道触达、实时归因与动态调优闭环。",
        "resultAction": "appendBlock",
        "meta": {
            "intent": "summary",
            "action": ""
        }
    }
}
```

#### 7.3.3 translate 示例

输入：

```json
{
  "articleId": 10001,
  "requestId": "req-translate-001",
  "entryPoint": "selection",
  "action": "translate",
  "selectedText": "本方案将显著提升团队协作效率。",
  "surroundingContext": "该段位于技术方案总结部分。",
  "chatInput": "翻译成英文，保持正式语气。"
}
```

输出：

```json
{
    "success": true,
    "message": "执行成功",
    "data": {
        "intent": "translate",
        "outputType": "markdown",
        "outputText": "This solution will significantly enhance team collaboration efficiency.",
        "resultAction": "replace",
        "meta": {
            "intent": "translate",
            "action": ""
        }
    }
}
```

#### 7.3.4 unknown 示例

输入：

```json
{
  "articleId": 10001,
  "requestId": "req-translate-001",
  "entryPoint": "selection",
  "action": "",
  "selectedText": "hello",
  "surroundingContext": "world",
  "chatInput": "who are you?"
}
```

输出：

```json
{
    "success": true,
    "message": "执行成功",
    "data": {
        "intent": "unknown",
        "outputType": "markdown",
        "outputText": "我暂时无法判断你的意图。你可以明确说明要我做哪一种：续写（continue）、润色（polish）、总结（summary）、翻译（translate）或 Mermaid（mermaid）。",
        "resultAction": "previewOnly",
        "meta": {
            "intent": "unknown",
            "action": ""
        }
    }
}
```

### 7.4 回填策略

- `replace`：替换选区
- `insertAfter`：插入到选区后
- `appendBlock`：追加到文末
- `insertMermaid`：插入 Mermaid 块
- `previewOnly`：只预览不落文档

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

## 10. 编辑器 AI 智能体接口文档

> 若当前尚未完全落地，可先按此契约实现，逐步替换旧调用。

### 10.1 编辑器 AI 同步请求

- `POST /api/ai/editor/execute`
- 功能：执行一次编辑器 AI 任务并返回结果
- 请求体：

```json
{
  "articleId": 10001,
  "requestId": "req-20260512-0001",
  "entryPoint": "selection",
  "action": "polish",
  "selectedText": "这段话写得很口语化，不够正式。",
  "surroundingContext": "请把这段话改得更像产品文档",
  "chatInput": "请润色这段话，让它更像产品文档的表达。"
}
```

- 说明：
  - `requestId`：一次 AI 请求唯一编号，用于停止任务、日志追踪、重试幂等
  - `action`：可为空；为空时由 Router 自动识别意图并路由智能体
  - `selectedText`：用户选中的文本
  - `surroundingContext`：选中文本前后局部上下文，MVP 建议约 200～500 字
  - `chatInput`：用户输入框里的需求描述
- 响应体：`ApiResponse<EditorAiExecuteResponse>`，`data` 包含 `intent / outputType / outputText / resultAction / meta`

### 10.2 编辑器 AI 流式请求

- `POST /api/ai/editor/stream`
- 功能：流式返回编辑器 AI 任务结果（大段润色/总结推荐）
- SSE 事件：`message-start` / `message-delta` / `message-end` / `message-stop` / `message-error`

### 10.3 停止编辑器 AI

- `POST /api/ai/editor/stop`
- 功能：停止指定 `articleId + requestId` 的任务
- 停止标识写入 Redis：`kb:ai:stop:{articleId}:{requestId}`

### 10.4 查询编辑器 AI 日志

- `GET /api/ai/editor/kb/articles/{articleId}/ai-logs`
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

## 12. 附：AI 接口清单


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


