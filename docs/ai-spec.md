# AI 能力技术方案与接口文档

> 本文档定义项目中的 AI 域能力，包括：
>
> 1. 独立 AI 对话（会话、流式、停止、RAG 增强）
> 2. 编辑器内 AI 智能体（多智能体路由与任务执行）

## 1. 目标

在现有 React + Spring Boot 项目中建设两条并行 AI 能力线：

1. **AI 对话线（独立页面）**：支持会话管理、流式回复、停止生成、RAG 引用返回。
2. **AI 智能体线（编辑器内）**：面向写作场景的多智能体能力，覆盖续写、润色、翻译、总结、Mermaid 等任务。

---

## 2. 范围与边界

- `/api/ai/` 下的全部接口
- AI 会话、消息、流式协议
- AI 停止控制与状态落库
- RAG 增强问答（先检索再生成）
- 编辑器内多智能体编排与回填策略

---

## 3. 总体架构

### 3.1 基础架构

- 前端：React（独立 AI 页面 + KnowledgeBase 编辑器交互）
- 后端：Spring Boot + Spring AI + MyBatis
- 数据：MySQL（会话/消息/日志）+ Redis（停止标记/短期状态）
- 检索依赖：复用 KB 域的 Redis Stack 向量检索能力
- 通信：HTTP JSON + SSE

### 3.2 AI 调用链路

```text
FrontEnd -> AI Gateway (/api/ai/**) -> AI Orchestrator -> Router Agent -> Task Agent / Chat Workflow -> LLM(+RAG)
```

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

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| user_id | BIGINT | 会话所属用户 |
| title | VARCHAR(200) | 会话标题 |
| summary | VARCHAR(255) | 摘要 |
| status | TINYINT | 状态（0 正常 / 1 删除） |
| use_rag | TINYINT(1) | 默认 RAG 开关 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 5.2 消息表 `ai_conversation_message`

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| conversation_id | BIGINT | 会话 ID |
| role | VARCHAR(20) | user / assistant / system |
| content | LONGTEXT | 消息内容 |
| status | VARCHAR(20) | GENERATING / COMPLETED / STOPPED / FAILED |
| request_id | VARCHAR(64) | 请求唯一标识 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 5.3 通用操作日志表 `knowledge_article_operation_log`

> 仅记录 AI 相关操作，不记录普通手动编辑。该表用于追踪 AI 编辑请求、响应、状态、耗时与回滚链路。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| user_id | BIGINT | 操作用户 ID |
| article_id | BIGINT | 文章 ID |
| conversation_id | BIGINT | 可选，会话 ID（AI 对话场景） |
| request_id | VARCHAR(64) | 可选，请求标识（AI 场景） |
| operation_type | VARCHAR(30) | 操作类型（AI_APPLY / AI_GENERATE / AI_CANCEL / AI_RETRY / UNDO / REDO） |
| change_mode | VARCHAR(20) | 变更模式（SNAPSHOT / DELTA） |
| intent | VARCHAR(50) | 可选，AI 意图（polish/summary/...） |
| entry_point | VARCHAR(50) | 入口（selection/context-menu/toolbar/editor） |
| input_text | LONGTEXT | 可选，AI 输入 |
| selected_text | LONGTEXT | 可选，选中文本 |
| output_text | LONGTEXT | 可选，AI 输出 |
| result_action | VARCHAR(30) | 可选，回填动作 |
| before_snapshot | LONGTEXT | 变更前快照（局部或整文） |
| after_snapshot | LONGTEXT | 变更后快照（局部或整文） |
| delta_json | LONGTEXT | 增量内容（JSON Patch 或自定义 diff） |
| ref_operation_id | BIGINT | 关联原操作（UNDO/REDO 使用） |
| status | VARCHAR(20) | 状态（SUCCESS / FAILED / STOPPED） |
| error_message | VARCHAR(500) | 失败原因 |
| latency_ms | INT | 处理耗时 |
| created_at | TIMESTAMP | 创建时间 |

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

### 7.1 能力目标

在 Tiptap 编辑器中，支持右键/选区触发 AI 操作，并将结果回填到文档。

首批能力：

- 续写（continue）
- 润色（polish）
- 头脑风暴（brainstorm）
- 翻译（translate）
- 总结（summary）
- Mermaid 生成（mermaid）

### 7.2 多智能体架构（Router + Planner + Workers + Critic）

> 目标：在不改变前端调用协议（`/execute`、`/stream`）的前提下，把当前“单路由 + 单执行”升级为“可编排、可并行、可评估”的多 Agent 体系，提升复杂指令下的稳定性与可解释性。

#### 7.2.1 角色分层

- `Lead Orchestrator（主控）`
  - 统一接入请求，生成 `traceId / requestId`，维护本次任务上下文
  - 负责任务状态流转（`INIT -> ROUTED -> PLANNED -> EXECUTING -> MERGING -> DONE/FAILED`）
  - 控制超时、重试、降级与停止信号传播

- `Router Agent（意图路由）`
  - 判断是否直接命中 `action`，或基于 `chatInput + selectedText + surroundingContext` 做意图识别
  - 输出 `primaryIntent`、`secondaryIntents`、`confidence`
  - 当置信度低于阈值时，触发澄清或降级到 `previewOnly`

- `Planner Agent（任务编排）`
  - 将任务拆解为 `Plan DAG`（有向无环图）
  - 决策哪些子任务串行，哪些可并行（如术语抽取、事实约束、风格改写并行）
  - 为每个子任务绑定 `skill`、输入、输出契约与超时预算

- `Worker Agents（执行层）`
  - 领域 Worker：`Continue / Polish / Brainstorm / Translate / Summary / Mermaid`
  - 通用 Worker：`Retriever Worker`（检索上下文）、`Style Worker`（风格规范）、`Constraint Worker`（格式/长度/术语约束）
  - 支持并行执行，统一产出结构化结果

- `Critic Agent（质量评估）`
  - 对 Worker 输出进行规则校验与 LLM 评估（事实一致性、指令遵循度、可读性）
  - 不达标则触发一次有限重写（最多 1 次），避免无限循环

- `Merger Agent（汇总回填）`
  - 汇总多 Worker 结果，生成最终 `outputText + resultAction`
  - 维护冲突解决策略（优先级：硬约束 > 用户指令 > 风格偏好）

#### 7.2.2 执行模式

- `Fast Path（快速路径）`
  - 条件：单一明确意图 + 短文本 + 高置信度
  - 流程：Router -> 单 Worker -> Critic（轻量）-> 返回
  - 目标：低延迟返回

- `Swarm Path（协作路径）`
  - 条件：复杂指令、多约束、长文本、低置信度
  - 流程：Router -> Planner -> 多 Worker 并行/串行 -> Critic -> Merger
  - 目标：提升复杂场景质量与稳定性

#### 7.2.3 并行与收敛策略

- 并行扇出上限：`maxParallelWorkers = 3`（MVP 建议）
- 单 Worker 超时：`workerTimeoutMs = 8000`（可配置）
- 全链路超时：`totalTimeoutMs = 20000`（可配置）
- 收敛规则：
  - 达到质量阈值立即收敛
  - 超时时采用“已完成结果优先”合并并返回（带 `degraded=true`）
  - 任一关键节点失败时，降级到主 Worker 单路返回

#### 7.2.4 防失控机制

- 最大推理轮次：`maxReasoningTurns = 4`
- 最大重试次数：`maxRetry = 1`
- 工具调用预算：`toolBudget = 3`
- 出现以下情况立即中断并返回可解释错误：
  - 循环调用同一 skill
  - 连续两轮质量分下降
  - 超出总预算/超时

#### 7.2.5 可观测性与日志字段

在 AI 操作日志中补充以下扩展字段（写入 `meta` 或扩展列）：

- `traceId`：一次多 Agent 编排链路 ID
- `routeDecision`：路由结果与置信度
- `planSummary`：DAG 摘要（节点数、并行度）
- `workerStats`：各 Worker 耗时、状态、重试次数
- `criticScore`：质量评分（0~1）
- `degraded`：是否降级返回

#### 7.2.6 配置驱动（YAML）

通过 YAML 管理 Agent 编排策略，避免硬编码，示例：

```yaml
ai:
  editor:
    orchestration:
      mode: auto            # auto | fast | swarm
      thresholds:
        routeConfidence: 0.78
        criticPassScore: 0.82
      limits:
        maxParallelWorkers: 3
        maxReasoningTurns: 4
        toolBudget: 3
        maxRetry: 1
      timeout:
        workerTimeoutMs: 8000
        totalTimeoutMs: 20000
      fallback:
        enableDegrade: true
        degradeAction: previewOnly
```

#### 7.2.7 MVP 落地顺序（只改多 Agent）

1. **Phase-1（最小升级）**：`Router + Lead Orchestrator + Polish Worker + Critic（规则版）`
2. **Phase-2（并行能力）**：引入 `Planner`，支持 `Polish + Constraint + Style` 并行
3. **Phase-3（全量能力）**：接入其余 Worker（continue/summary/translate/mermaid/brainstorm）+ Merger
4. **Phase-4（稳定性）**：补齐降级、超时预算、日志指标与质量看板

#### 7.2.8 Retrieval Gate（是否启用知识库）

> 目标：在“事实性与延迟”之间做动态平衡，避免“该检索不检索”与“不该检索硬检索”。

决策位置：`Router` 完成意图判断后，进入 `Retrieval Gate`；Gate 输出 `retrievalUsed=true/false`，再交由 `Planner/Worker` 执行。

决策输入：

- `intent`（continue/polish/summary/translate/mermaid/...）
- `chatInput`（用户显式指令）
- `selectedText + surroundingContext`（上下文充分性）
- `riskLevel`（场景风险等级：low/medium/high）

评分规则（MVP）：

定义 `retrievalScore`（0~1），按加权求和：

- 任务类型分（`taskScore`）
  - 事实解释、政策问答、术语定义：0.7~1.0
  - 纯润色、语气改写：0.0~0.3
- 指令特征分（`queryScore`）
  - 出现关键词：`根据资料/引用来源/最新规则/是否合规/给出处` 加分
- 上下文充分性分（`contextLackScore`）
  - 上下文缺失、信息不全时加分
- 风险分（`riskScore`）
  - high 风险场景（如医疗/法务/财务）加分

建议阈值：

- `retrievalScore >= 0.55`：启用 KB 检索
- `retrievalScore < 0.55`：不启用检索，直接生成

执行策略：

- 启用检索：`Retriever Worker` 执行 `topK` 检索并返回证据片段，再注入主 Worker 提示词
- 不启用检索：跳过检索链路，走 Fast 生成
- 检索失败：不中断主流程，标记 `retrievalFallback=true` 后降级生成

参数建议：

```yaml
ai:
  editor:
    retrievalGate:
      enabled: true
      threshold: 0.55
      defaultTopK: 5
      timeoutMs: 1200
      riskBoost:
        low: 0.0
        medium: 0.08
        high: 0.15
      keywordBoost:
        - "根据资料"
        - "引用来源"
        - "最新规则"
        - "是否合规"
        - "给出处"
```

返回字段扩展（meta）：

```json
{
  "retrievalUsed": true,
  "retrievalScore": 0.73,
  "retrievalReason": "high-risk + source-required",
  "retrievalTopK": 5,
  "retrievalFallback": false
}
```

### 7.3 记忆架构（L1 + L2）

> 当前阶段仅建设两层记忆：L1 会话工作记忆 + L2 用户事实记忆。先不引入更重的长期语义记忆库，控制实现复杂度。

#### 7.3.1 L1：会话工作记忆（Session Working Memory）

定位：面向“当前会话连续性”的短期记忆，保障多轮上下文不丢失。

- 存储介质：Redis + 消息表快照（双写）
- 数据来源：`ai_conversation_message` 的最近 N 轮 + 摘要压缩
- 维护策略：
  - 滑动窗口（建议保留最近 `8~12` 轮）
  - 当 token 预算逼近阈值时，触发 `rolling summary`，把早期轮次压缩为结构化摘要
  - 停止/异常时也保留当前已生成片段，写入 L1
- 读取时机：每次 `Router/Planner/Worker` 执行前注入
- TTL 建议：`24h`（可配置）

建议结构：

```json
{
  "conversationId": 10001,
  "recentTurns": [
    {"role": "user", "content": "..."},
    {"role": "assistant", "content": "..."}
  ],
  "rollingSummary": "用户正在写项目方案，偏好正式、条理化表达",
  "updatedAt": "2026-05-26T10:20:30Z"
}
```

Redis Key 建议：

- `ai:mem:l1:{conversationId}`

#### 7.3.2 L2：用户事实记忆（User Fact Memory）

定位：跨会话复用的“稳定事实”，避免每轮重复提供个人偏好与固定背景。

- 存储介质：MySQL（结构化）
- 记忆内容：
  - 写作偏好（语气、长度、格式）
  - 固定身份信息（岗位、行业、常用术语）
  - 明确的长期约束（如“默认中文输出”“避免口语化”）
- 入库策略（MVP）：
  - 仅提取“高置信、可验证、低风险”事实
  - 需要满足 `出现次数阈值`（如 2 次以上）再写入
  - 支持人工删除/纠错（避免错误长期污染）
- 读取时机：会话开始、Router 判定复杂任务时优先注入
- 更新频率：按会话结束或每 N 轮批量更新

建议表（可新建）：`ai_user_memory_fact`

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| fact_key | VARCHAR(64) | 事实键（tone/lang/domain...） |
| fact_value | VARCHAR(500) | 事实值 |
| confidence | DECIMAL(4,3) | 置信度 |
| source_conversation_id | BIGINT | 来源会话 |
| hit_count | INT | 命中次数 |
| status | TINYINT | 0 生效 / 1 失效 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

Redis 可选缓存：

- `ai:mem:l2:user:{userId}`（热数据缓存，TTL 建议 6h）

#### 7.3.3 L1 + L2 注入优先级

- 提示词组装优先级：`系统安全约束 > L2 稳定事实 > L1 会话上下文 > 当前用户输入`
- 冲突处理：
  - 若 L2 与当前用户本轮指令冲突，以“本轮显式指令”为准
  - 若 L1 近期上下文与 L2 冲突，触发一次澄清或临时覆盖

#### 7.3.4 与多 Agent、Retrieval Gate 的关系

- Router：使用 L1 判定当前意图连续性，使用 L2 判定用户长期偏好
- Retrieval Gate：当 L1/L2 信息不足时，提高 `contextLackScore`，更倾向启用 KB
- Worker：生成前统一注入 L1 + L2，减少“重复解释用户偏好”

### 7.4 输入与输出协议

> 对外 API 保持兼容；新增编排信息放入 `meta`，不影响现有前端解析。

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
    "confidence": 0.94,
    "traceId": "trace-20260526-0001",
    "executionMode": "fast",
    "routeDecision": {
      "primaryIntent": "polish",
      "score": 0.94
    },
    "criticScore": 0.88,
    "degraded": false
  }
}
```

#### 7.4.1 polish 示例

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

#### 7.4.2 continue 示例

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

#### 7.4.3 summary 示例

输入：

```json
{
  "articleId": 10001,
  "requestId": "req-summary-001",
  "entryPoint": "selection",
  "action": "summary",
  "selectedText": "我们将在下个季度重点推进用户增长策略。\n\n具体执行路径分为三步：首先，完成现有用户分层模型的迭代优化，确保高潜力用户识别准确率提升至92%以上；其次，基于分层结果，在7月启动定向裂变激励实验，覆盖TOP 20%活跃用户群，并同步上线个性化邀请链路；最后，8月中旬起将验证有效的策略模块化，嵌入自动化运营平台，实现全渠道触达、实时效果归因与动态策略调优闭环。",
  "surroundingContext": "我们将在下个季度重点推进用户增长策略。\n\n具体执行路径分为三步：首先，完成现有用户分层模型的迭代优化，确保高潜力用户识别准确率提升至92%以上；其次，基于分层结果，在7月启动定向裂变激励实验，覆盖TOP 20%活跃用户群，并同步上线个性化邀请链路；最后，8月中旬起将验证有效的策略模块化，嵌入自动化运营平台，实现全渠道触达、实时效果归因与动态策略调优闭环。",
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

#### 7.4.4 translate 示例

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

#### 7.4.5 unknown 示例

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

### 7.5 回填策略

- `replace`：替换选区
- `insertAfter`：插入到选区后
- `appendBlock`：追加到文末
- `insertMermaid`：插入 Mermaid 块
- `previewOnly`：只预览不落文档

---

## 8. 实现落地清单

> 目标：将本方案拆为后端、前端、数据库、接口四部分，按“先打通主链路、再补齐编排与治理能力”的顺序落地。

### 8.1 后端任务清单

#### 8.1.1 AI 对话主链路

- 新建 `ai/controller`、`ai/service`、`ai/workflow/chat` 相关实现
- 实现会话创建、会话列表、会话详情查询
- 实现同步问答与流式问答两种入口
- 实现 user / assistant 消息的落库、状态流转与 requestId 追踪
- 实现 stop 逻辑：写入停止标记、流式中断、已生成内容保留、任务结束后清理标记
- 实现 RAG 开关与检索注入逻辑，默认可由会话级配置覆盖
- 统一封装 SSE 事件输出：`rag-start` / `rag-result` / `message-start` / `message-delta` / `message-end` / `message-stop` / `message-error`

#### 8.1.2 编辑器 AI 智能体主链路

- 新建 `ai/editor/controller`、`ai/editor/router`、`ai/editor/service`、`ai/editor/agent`、`ai/editor/tooling` 实现
- 先落地 `Router Agent + 单 Worker + Critic(规则版)` 的最小闭环
- 保持 `/execute` 与 `/stream` 对外协议稳定，内部逐步替换为多 Agent 编排
- 支持 `continue / polish / summary / translate / mermaid / brainstorm` 意图识别与路由
- 实现 `Fast / Swarm` 双通道，复杂任务进入 Planner + Workers + Critic + Merger
- 实现任务停止、失败降级、超时预算、重试上限、反思循环上限等治理逻辑
- 实现 AI 操作日志写入，包括 `traceId`、`routeDecision`、`criticScore`、`degraded` 等扩展信息

#### 8.1.3 RAG 与记忆能力

- 复用 KB 域 Redis Stack 向量检索能力，封装检索调用与上下文注入
- 实现 Retrieval Gate：按任务类型、上下文充分性、风险等级决定是否检索
- 实现 Query Rewrite、BM25 + 向量混合召回、rerank、context compression
- 实现 L1 会话工作记忆的读写与滚动摘要
- 实现 L2 用户事实记忆的读取、写入、纠错与失效机制
- 保证记忆与检索结果能注入到 Router / Planner / Worker 的统一提示词拼装层

#### 8.1.4 质量与异常治理

- 为 Critic Agent 配置规则校验：Markdown、引用格式、主题相关性、Mermaid 语法等
- 触发 Reflection Loop 时支持有限次数回溯修正
- 处理 LLM 超时、检索失败、流式中断、停止重复提交、意图未知等异常
- 为所有关键请求生成可追踪 requestId / traceId，并贯穿日志、SSE 与数据库记录
- 统一返回可展示错误文案，避免前端只拿到纯技术异常

### 8.2 前端任务清单

#### 8.2.1 AI 对话页面

- 建立 `/ai` 与 `/ai/:conversationId` 的路由和会话切换逻辑
- 实现会话列表、会话详情、消息展示、流式增量渲染
- 支持“停止生成”按钮、停止状态展示与已生成内容保留
- 实现 RAG 开关、输入区、发送态、重试态、失败态等交互状态
- 根据 SSE 事件类型分别处理引用、文本增量、结束、停止、异常
- 保持消息滚动、输入焦点、长文本截断、Markdown 渲染一致性

#### 8.2.2 编辑器 AI 交互

- 在 Tiptap 编辑器中接入右键、选区、工具栏触发入口
- 支持 `selection / context-menu / toolbar / editor` 等入口的统一请求构造
- 根据 `resultAction` 执行回填：`replace / insertAfter / appendBlock / insertMermaid / previewOnly`
- 实现 AI 结果预览、确认应用、取消应用、失败重试
- 对 Mermaid、Markdown、长文本、代码块等结果进行前端展示与安全处理
- 在流式模式下展示编排阶段状态，如路由、计划、worker、critic、merge

#### 8.2.3 交互与体验补齐

- 处理空内容、超长内容、选区为空、意图未知等边界情况
- 为停止、失败、重试、降级返回提供明确反馈
- 对流式中断时的局部结果保留和二次应用提供 UI 支持
- 保证编辑器回填后光标、滚动位置、选区状态正确恢复
- 统一加载态、按钮禁用态、骨架屏/占位反馈与文案

### 8.3 数据库任务清单

#### 8.3.1 对话数据表

- 完善 `ai_conversation` 与 `ai_conversation_message` 的字段定义、索引与状态枚举
- 明确会话标题、摘要、默认 RAG 开关、消息状态流转规则
- 为 `conversation_id + request_id` 增加必要的唯一性或查询索引，保证幂等与检索效率
- 设计消息表对流式增量、停止状态、失败状态的存储方式

#### 8.3.2 编辑器 AI 日志表

- 细化 `knowledge_article_operation_log` 的写入边界，仅记录 AI 相关操作
- 明确 `operation_type`、`change_mode`、`result_action`、`status` 等枚举值
- 保留 `before_snapshot / after_snapshot / delta_json` 三种回填记录方式
- 为 `article_id / user_id / request_id / conversation_id / created_at` 建立查询索引
- 支持 `UNDO / REDO` 的 `ref_operation_id` 链路追踪

#### 8.3.3 记忆与停止标记

- 落地 `ai:stop:{conversationId}` 与 `kb:ai:stop:{articleId}:{requestId}` 的 Redis Key 规范
- 落地 L1 记忆 `ai:mem:l1:{conversationId}` 的结构、TTL 与更新策略
- 落地 L2 记忆表 `ai_user_memory_fact`，支持用户事实、置信度、来源与状态管理
- 为长期记忆增加必要的热缓存或同步策略，避免频繁访问数据库

#### 8.3.4 索引与约束

- 为高频查询字段补齐索引：会话列表、会话详情、消息时间线、AI 日志查询
- 为状态字段、用户字段、请求字段统一约束取值范围，减少脏数据
- 若后续新增表或字段，必须同步更新 SQL 初始化与迁移脚本

### 8.4 接口任务清单

#### 8.4.1 AI 对话接口

- 完成 `POST /api/ai/conversations`
- 完成 `GET /api/ai/conversations`
- 完成 `GET /api/ai/conversations/{conversationId}/detail`
- 完成 `POST /api/ai/chat`
- 完成 `POST /api/ai/chat/stream`
- 完成 `POST /api/ai/chat/stop`
- 统一返回 `ApiResponse<T>`，并确保错误码、消息、数据结构前后一致

#### 8.4.2 编辑器 AI 接口

- 完成 `POST /api/ai/editor/execute`
- 完成 `POST /api/ai/editor/stream`
- 完成 `POST /api/ai/editor/stop`
- 完成 `GET /api/ai/editor/kb/articles/{articleId}/ai-logs`
- 保持输入协议兼容：`articleId / requestId / entryPoint / action / selectedText / surroundingContext / chatInput`
- 保持输出协议稳定：`intent / outputType / outputText / resultAction / meta`

#### 8.4.3 SSE 事件协议

- 固定基础事件：`message-start` / `message-delta` / `message-end` / `message-stop` / `message-error`
- 编辑器流式补充编排事件：`route-selected` / `plan-created` / `worker-start` / `worker-end` / `critic-result` / `merge-done`
- 明确每个事件的数据结构、顺序约束与前端消费规则

#### 8.4.4 兼容性与演进

- 维持旧调用方式兼容，新增字段默认可选，不破坏现有前端
- 所有新增接口字段、枚举、错误码都必须同步更新文档
- 若后续引入更多 Worker、更多记忆层或新的回填方式，接口需优先保持向后兼容

---

## 9. 接口约定

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
- SSE 事件：
  - 基础事件：`message-start` / `message-delta` / `message-end` / `message-stop` / `message-error`
  - 编排事件（新增，可选消费）：`route-selected` / `plan-created` / `worker-start` / `worker-end` / `critic-result` / `merge-done`

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

| 能力 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 创建会话 | POST | /api/ai/conversations | 新建对话会话 |
| 会话列表 | GET | /api/ai/conversations | 当前用户会话 |
| 会话详情 | GET | /api/ai/conversations/{conversationId}/detail | 会话 + 消息 |
| 同步问答 | POST | /api/ai/chat | 同步返回答案 |
| 流式问答 | POST | /api/ai/chat/stream | SSE 流式输出 |
| 停止对话生成 | POST | /api/ai/chat/stop | 停止当前会话 |
| 编辑器 AI 执行（建议） | POST | /api/ai/editor/execute | 智能体任务同步执行 |
| 编辑器 AI 流式（建议） | POST | /api/ai/editor/stream | 智能体任务流式执行 |
| 编辑器 AI 停止（建议） | POST | /api/ai/editor/stop | 停止编辑器任务 |
| 编辑器 AI 日志 | GET | /api/ai/editor/kb/articles/{articleId}/ai-logs | 查询任务记录 |
