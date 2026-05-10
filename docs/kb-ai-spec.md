# 知识库 AI 编辑平台技术方案及接口文档

## 1. 目标

本方案用于在当前 React + Spring Boot 项目中建设一套面向“个人知识库”的 AI 编辑平台，围绕 **Tiptap 编辑器**、**知识库文章列表** 和 **AI 编辑能力** 构建统一工作台，帮助用户对自己的文章进行梳理、编辑、总结、翻译、扩写和知识增强。

知识库场景的核心交互包括：

- 左侧知识库文章列表
- 右侧 Tiptap 编辑器主工作区
- 编辑器选中文本后的 AI 操作
- 右键菜单 `Ask AI`
- 统一接入 `AI Gateway API`
- 支持 Mermaid 图自动生成并回填编辑器

AI 调用链路如下：

`AI Gateway API`
↓
`SpringBoot AI Orchestrator`
↓
`Intent Router`
↓
`Workflow`
↓
`LLM / RAG / Tools`

其中：

- `Intent Router` 负责识别用户在知识库中的操作意图
- `Workflow` 负责编排具体任务，例如扩写、润色、总结、翻译、Mermaid 生成
- `RAG` 用于检索用户知识库内相关文章片段，增强输出质量
- `Tools` 用于调用结构化处理工具，例如标题提取、段落切分、Markdown 转换、Mermaid 校验

---

## 2. 适用范围

### 2.1 已覆盖能力

- 知识库文章列表展示
- 文章创建、编辑、保存、删除
- Tiptap 编辑器内容管理
- 选中文本后触发 AI 操作
- 编辑器右键 `Ask AI`
- AI 结果替换、插入、追加、生成块
- Mermaid 图自动生成与回填
- 基于知识库文章的上下文检索增强
- AI 操作记录与审计

### 2.2 典型场景

1. **意识识别**：识别当前选中文本的主题、段落角色、结构意图。
2. **扩写**：对一句话、要点、提纲进行补充与展开。
3. **润色**：改善表达、逻辑、语气与可读性。
4. **总结**：生成段落摘要、文章摘要、要点列表。
5. **翻译**：按指定目标语言进行翻译。
6. **Mermaid 图自动生成**：根据文章内容生成流程图、时序图、类图等。
7. **知识增强问答**：基于知识库文章检索相关内容并生成回答。

---

## 3. 技术方案

### 3.1 总体架构

- 前端：React + Tiptap + 知识库列表 + 选区浮层 + 右键菜单
- 后端：Spring Boot + Spring AI + MyBatis
- 数据库：MySQL
- 缓存：Redis
- 检索：基于 Redis Stack 的向量数据库能力实现 RAG 检索，文章切片可保留在 MySQL 作为原始文本与版本追踪
- 通信方式：HTTP JSON + SSE 流式响应

### 3.2 前端页面结构

知识库页面建议拆分为两个主要区域：

1. **知识库列表区**
  - 展示文章标题、摘要、更新时间、标签/状态
  - 支持搜索、筛选、创建新文章
  - 支持切换当前编辑文章
2. **编辑器工作区**
  - 基于 Tiptap 编辑文章正文
  - 支持选区 AI 操作
  - 支持右键菜单 `Ask AI`
  - 支持 AI 结果预览、替换、插入、追加

### 3.3 后端模块划分

建议在后端新增独立的 AI 编排模块，并扩展知识库模块：

- `kb/controller`
- `kb/service`
- `kb/service/impl`
- `kb/dto`
- `kb/entity`
- `kb/mapper`
- `kb/config`
- `kb/constant`
- `kb/util`
- `ai/orchestrator`
- `ai/router`
- `ai/workflow`
- `ai/workflow/impl`
- `ai/dto`
- `ai/entity`
- `ai/mapper`

### 3.4 核心流程

#### 1. 知识库进入流程

1. 用户进入知识库页面。
2. 前端请求文章列表接口，加载左侧列表。
3. 用户选择文章后，右侧加载 Tiptap 编辑器内容。
4. 编辑器进入可编辑状态，支持选区 AI、右键 AI、保存与版本回写。

#### 2. AI 意图识别流程

1. 用户选中文本或在文章中触发 AI 操作。
2. 前端提交当前文本、上下文、入口类型、文章 ID、目标操作类型。
3. 后端通过 `Intent Router` 判断是否需要直接执行预设工作流，或进入通用推理流程。
4. Router 选择对应 Workflow。
5. Workflow 结合 LLM、RAG、Tools 完成任务。
6. 结果通过 SSE 或 JSON 返回前端。
7. 前端根据响应类型执行替换、插入、追加或生成块。

#### 3. 文章编辑流程

1. 用户在 Tiptap 中编辑文章。
2. 前端支持自动保存或显式保存。
3. 后端保存文章正文快照。
4. 如果开启版本历史，则同步写入版本表。

#### 4. AI 内容回填流程

1. AI 返回结果后，前端根据 `action` 决定回填位置。
2. `replace`：替换选中文本。
3. `insertAfter`：在选区后插入内容。
4. `appendBlock`：在文章末尾追加内容块。
5. `insertMermaid`：插入 Mermaid 代码块或渲染块。
6. 前端可在回填前预览并支持二次编辑。

### 3.5 知识文档上传与 RAG 基础检索

用户可通过文件上传或创建知识库文章的方式进入 RAG 流程。系统在上传、创建、保存、回滚后都会自动完成文章切片、embedding 生成、Redis Stack 向量入库，并提供基础召回接口。为了支持“先上传文档，再做后续 RAG/问答”的流程，建议新增文档入库接口：

- `POST /api/kb/rag/upload`：上传一个或多个文档并自动创建文章、切片入库
- `POST /api/kb/rag/articles/{articleId}/ingest`：对指定文章重新切片并写入向量索引
- `GET /api/kb/rag/search?query=...&articleId=...&topK=...`：按关键词从已入库切片中进行基础召回

基础召回阶段采用 MySQL 保存切片原文与元信息，Redis Stack 保存向量索引与相似度检索能力。

### 3.6 意图识别与工作流设计

建议将知识库 AI 能力拆分为以下意图：

- `recognize`：意识识别 / 结构识别
- `expand`：扩写
- `polish`：润色
- `summary`：总结
- `translate`：翻译
- `rewrite`：重写
- `mermaid`：Mermaid 图生成
- `qa`：知识增强问答

路由规则建议如下：

1. 若前端显式传入 `intent`，优先按前端意图处理。
2. 若前端仅传入原始文本，则由 `Intent Router` 识别意图。
3. 若识别结果置信度过低，则进入通用问答或澄清流程。
4. 若输入内容包含结构化指令，例如“生成流程图”“转成时序图”，优先进入 `mermaid` 工作流。

### 3.6 工作流建议

#### 1. 意识识别工作流

- 输入：选中文本 + 上下文 + 文章标题
- 输出：主题、结构、段落作用、核心问题
- 用途：帮助用户理解当前片段的写作意图

#### 2. 扩写工作流

- 输入：原文片段 + 扩写目标 + 风格要求
- 输出：扩展后的正文
- 用途：补充观点、案例、解释

#### 3. 润色工作流

- 输入：原文片段 + 语气/风格要求
- 输出：润色后的内容
- 用途：改善表达、减少口语化、增强逻辑

#### 4. 总结工作流

- 输入：原文片段或全文
- 输出：摘要、要点清单、结论
- 用途：生成文章概览或章节摘要

#### 5. 翻译工作流

- 输入：原文片段 + 目标语言
- 输出：翻译结果
- 用途：多语言知识整理

#### 6. Mermaid 生成工作流

- 输入：结构化文本或文章内容
- 输出：Mermaid 源码 + 图类型 + 说明
- 用途：将逻辑关系可视化

#### 7. RAG 问答工作流

- 输入：用户问题 + 当前文章上下文 + Redis Stack 向量检索结果
- 输出：综合回答 + 引用片段
- 用途：基于个人知识库给出更准确的回答

---

## 4. 配置设计

### 4.1 大模型配置

建议统一通过配置文件管理 AI 参数：

- `ai.api-key`
- `ai.base-url`
- `ai.model-name`
- `ai.temperature`
- `ai.max-tokens`
- `ai.timeout-ms`
- `ai.stream-enabled`

示例：

```properties
ai.api-key=${DASHSCOPE_API_KEY:}
ai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
ai.model-name=qwen-plus
ai.temperature=0.7
ai.max-tokens=2048
ai.timeout-ms=60000
ai.stream-enabled=true
```

### 4.2 意图与工作流配置

建议维护一份可配置的意图映射表：

- `recognize`：意识识别
- `expand`：扩写
- `polish`：润色
- `summary`：总结
- `translate`：翻译
- `rewrite`：重写
- `mermaid`：Mermaid 图生成
- `qa`：问答

可进一步支持：

- prompt 模板配置
- 文章级别上下文开关
- RAG 检索开关
- 工具调用开关
- 流式与非流式切换

---

## 5. 数据存储

### 5.1 知识库文章表 `kb_articles`

用于存储用户自己的文章内容、标题、摘要、状态与编辑器快照。


| 字段名          | 类型           | 说明             |
| ------------ | ------------ | -------------- |
| id           | BIGINT       | 主键             |
| user_id      | BIGINT       | 文章所属用户 ID      |
| title        | VARCHAR(200) | 文章标题           |
| summary      | VARCHAR(500) | 文章摘要           |
| content      | LONGTEXT     | Tiptap JSON 内容 |
| content_text | LONGTEXT     | 文章纯文本索引内容      |
| status       | TINYINT      | 状态（0-正常，1-删除）  |
| created_at   | DATETIME     | 创建时间           |
| updated_at   | DATETIME     | 更新时间           |


### 5.2 知识库文章版本表 `kb_article_versions`

用于保存文章历史版本，支持手动保存、AI 编辑前后对比与回滚。


| 字段名        | 类型          | 说明                                    |
| ---------- | ----------- | ------------------------------------- |
| id         | BIGINT      | 主键                                    |
| article_id | BIGINT      | 文章 ID                                 |
| version_no | INT         | 版本号                                   |
| snapshot   | LONGTEXT    | 文章快照                                  |
| created_by | BIGINT      | 创建人 ID                                |
| source     | VARCHAR(30) | 来源（manual / ai / autosave / rollback） |
| created_at | DATETIME    | 创建时间                                  |


### 5.3 AI 操作记录表 `kb_ai_operation_log`

用于记录知识库中的 AI 操作，便于审计、回溯和体验优化。


| 字段名             | 类型          | 说明                                                                      |
| --------------- | ----------- | ----------------------------------------------------------------------- |
| id              | BIGINT      | 主键                                                                      |
| user_id         | BIGINT      | 操作用户 ID                                                                 |
| article_id      | BIGINT      | 文章 ID                                                                   |
| conversation_id | BIGINT      | 可选，会话 ID                                                                |
| request_id      | VARCHAR(64) | 请求标识                                                                    |
| intent          | VARCHAR(50) | 意图类型（recognize / expand / polish / summary / translate / mermaid / qa）  |
| entry_point     | VARCHAR(50) | 入口（selection / context-menu / toolbar / command-palette）                |
| input_text      | LONGTEXT    | 输入文本                                                                    |
| selected_text   | LONGTEXT    | 选中文本                                                                    |
| output_text     | LONGTEXT    | 输出文本                                                                    |
| result_action   | VARCHAR(30) | 回填动作（replace / insertAfter / appendBlock / insertMermaid / previewOnly） |
| status          | VARCHAR(20) | 状态（SUCCESS / FAILED / STOPPED）                                          |
| created_at      | DATETIME    | 创建时间                                                                    |


### 5.4 检索片段表 `kb_article_chunks`

文章切片表用于保存 RAG 的原始文本、摘要与索引信息；向量本身存储在 Redis Stack 中。


| 字段名           | 类型           | 说明                  |
| ------------- | ------------ | ------------------- |
| id            | BIGINT       | 主键                  |
| article_id    | BIGINT       | 文章 ID               |
| chunk_index   | INT          | 片段序号                |
| chunk_text    | LONGTEXT     | 片段内容                |
| chunk_summary | VARCHAR(500) | 片段摘要                |
| embedding_id  | VARCHAR(128) | Redis Stack 向量索引 ID |
| created_at    | DATETIME     | 创建时间                |


### 5.5 Redis Stack 向量检索设计

- Redis Stack 承担向量存储与相似度检索职责
- 文章切片入库时同步写入原始文本、摘要、embedding 向量和索引集合
- embedding 由阿里千问的 `qwen-text-embedding-v3` 生成；后端严格按 DashScope embedding 返回结构解析向量；当外部 embedding 接口不可用时，后端会降级到本地确定性向量，以保证入库流程可用
- 检索时优先使用 Redis Stack 原生 KNN 检索按 query embedding 与 chunk embedding 的余弦相似度排序；若 Redis 原生向量命令不可用，则降级到应用层相似度排序
- `embedding_id` 用于关联 MySQL 里的切片记录和 Redis Stack 里的向量记录
- Redis Key 采用 `kb:rag:vector:{embeddingId}` 保存单条向量，`kb:rag:index:{articleId}` 保存文章维度索引集合

### 5.6 Tiptap JSON 切片规则

- `heading`：作为章节边界，切片时会保留标题文本，并作为后续内容的上下文前缀
- `paragraph`：作为正文基础单元，会与同一章节下的相邻内容合并成更大的 chunk
- `list` / `bulletList` / `orderedList`：列表项会保留语义并并入当前章节内容，不单独拆成过细片段
- 目标 chunk 更偏向“章节级 / 小节级”，单片长度建议在 800~1600 字符范围内
- 其他节点：尝试提取文本；无法识别时回退为纯文本切片
- 当结构化切片结果为空时，才回退到纯文本按长度切片

### 5.6 Redis Key 设计

Redis 主要用于停止生成、临时上下文、幂等控制、编辑锁，以及 Redis Stack 向量检索索引。

- `kb:ai:stop:{articleId}:{requestId}`：知识库 AI 编辑停止标识
- `kb:ai:lock:{articleId}`：文章 AI 编辑临时锁
- `kb:ai:context:{articleId}`：知识库文章短期上下文缓存
- `kb:ai:req:{requestId}`：请求幂等标识
- `kb:rag:chunks:{articleId}`：文章切片缓存
- `kb:rag:index:{articleId}`：Redis Stack 向量索引或检索标识

---

## 6. 接口约定

### 6.1 通用约定

- Base URL：`http://localhost:8080/api`
- 请求方式：`POST` / `GET` / `PUT` / `DELETE`
- 请求头：`Content-Type: application/json`
- 返回格式：统一返回 JSON
- 流式接口：使用 `text/event-stream`
- 鉴权：需要登录，默认按当前用户权限过滤数据

### 6.2 统一响应格式

建议沿用项目里的 `ApiResponse<T>` 风格：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

---

## 7. 知识库接口文档

### 7.1 获取文章列表

- URL：`GET /api/kb/articles`
- 功能：获取当前用户可见的知识库文章列表
- 鉴权：需要登录

#### 返回参数

```json
{
  "success": true,
  "message": "查询成功",
  "data": [
    {
      "articleId": 10001,
      "title": "我的第一篇文章",
      "summary": "整理一篇产品思考笔记",
      "updatedAt": "2026-05-10T10:00:00",
      "status": 0
    }
  ]
}
```

### 7.2 获取文章详情

- URL：`GET /api/kb/articles/{articleId}`
- 功能：获取文章基础信息和 Tiptap 内容
- 鉴权：需要登录，仅允许查看当前用户可见文章

#### 返回参数

```json
{
  "success": true,
  "message": "查询成功",
  "data": {
    "articleId": 10001,
    "title": "我的第一篇文章",
    "summary": "整理一篇产品思考笔记",
    "content": {
      "type": "doc",
      "content": [
        {
          "type": "heading",
          "attrs": { "level": 1 },
          "content": [{ "type": "text", "text": "我的第一篇文章" }]
        },
        {
          "type": "paragraph",
          "content": [{ "type": "text", "text": "这里是文章正文。" }]
        }
      ]
    },
    "updatedAt": "2026-05-10T10:00:00"
  }
}
```

### 7.3 创建文章

- URL：`POST /api/kb/articles`
- 功能：新建文章并返回文章 ID
- 鉴权：需要登录

#### 请求参数

```json
{
  "title": "新文章",
  "summary": "文章摘要",
  "content": {
    "type": "doc",
    "content": []
  }
}
```

#### 返回参数

```json
{
  "success": true,
  "message": "创建成功",
  "data": {
    "articleId": 10002,
    "title": "新文章"
  }
}
```

### 7.4 保存文章

- URL：`PUT /api/kb/articles/{articleId}`
- 功能：保存文章内容、标题、摘要；仅在内容或元数据发生变化时创建新版本
- 鉴权：需要登录，仅允许编辑当前用户可编辑文章

#### 请求参数

```json
{
  "title": "更新后的标题",
  "summary": "更新后的摘要",
  "content": {
    "type": "doc",
    "content": []
  },
  "saveSource": "manual"
}
```

#### 返回参数

```json
{
  "success": true,
  "message": "保存成功",
  "data": {
    "articleId": 10001,
    "versionNo": 12,
    "changed": true
  }
}
```

#### 说明

- 只有当标题、摘要或正文发生变化时，后端才会创建一个新版本
- 如果内容没有变化，则不会新增版本，返回 `changed: false`，`versionNo` 保持当前最新版本号

### 7.5 删除文章

- URL：`DELETE /api/kb/articles/{articleId}`
- 功能：将文章标记为删除
- 鉴权：需要登录，仅允许删除当前用户文章

#### 返回参数

```json
{
  "success": true,
  "message": "删除成功",
  "data": null
}
```

### 7.6 查询文章版本列表

- URL：`GET /api/kb/articles/{articleId}/versions`
- 功能：查询文章历史版本
- 鉴权：需要登录

#### 返回参数

```json
{
  "success": true,
  "message": "查询成功",
  "data": [
    {
      "versionNo": 12,
      "source": "manual",
      "createdAt": "2026-05-10T10:05:00"
    }
  ]
}
```

### 7.7 回滚文章版本

- URL：`POST /api/kb/articles/{articleId}/rollback`
- 功能：回滚到指定版本
- 鉴权：需要登录，仅允许文章拥有者操作

#### 请求参数

```json
{
  "versionNo": 12
}
```

#### 返回参数

```json
{
  "success": true,
  "message": "回滚成功",
  "data": {
    "articleId": 10001,
    "versionNo": 12
  }
}
```

---

## 8. RAG 接口文档

### 8.1 上传并自动入库

- URL：`POST /api/kb/rag/upload`
- 功能：上传一个或多个文件，自动创建知识库文章并完成切片、embedding 和向量入库
- 鉴权：需要登录
- 请求方式：`multipart/form-data`

#### 请求参数

| 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| files | File[] | 是 | 一个或多个待上传文件 |

#### 返回参数

```json
{
  "success": true,
  "message": "上传并入库成功",
  "data": [
    {
      "articleId": 10021,
      "title": "需求说明",
      "chunkCount": 6
    }
  ]
}
```

#### 说明

- 文件名会作为文章标题的默认值
- `.json` 文件会按结构化内容解析
- `.md` / `.txt` 会包装成 Tiptap 文档结构
- 其他类型先走兜底文本包装
- 成功后会自动触发文章切片与向量入库
- 单个文件和整个请求体默认建议控制在 20MB 以内，超限时后端会返回 413 Payload Too Large

### 8.2 指定文章重新入库

- URL：`POST /api/kb/rag/articles/{articleId}/ingest`
- 功能：对指定文章重新切片、重新生成 embedding，并覆盖原有 chunk 和向量索引
- 鉴权：需要登录，仅允许文章所属用户操作

#### 路径参数

| 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| articleId | Long | 是 | 文章 ID |

#### 返回参数

```json
{
  "success": true,
  "message": "入库成功",
  "data": {
    "articleId": 10001,
    "chunkCount": 8,
    "embeddingIds": ["10001-1-xxx", "10001-2-yyy"]
  }
}
```

#### 说明

- 会先删除该文章旧的 chunk 记录
- 重新按 Tiptap 结构切片
- 对每个 chunk 调用 embedding 接口
- 将向量写入 Redis Stack 索引集合

### 8.3 基础召回检索

- URL：`GET /api/kb/rag/search`
- 功能：基于文章向量索引做相似度召回，返回最相关的 chunk 列表
- 鉴权：需要登录

#### 查询参数

| 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| query | String | 是 | 检索问题或关键词 |
| articleId | Long | 是 | 目标文章 ID |
| topK | Integer | 否 | 返回条数，默认 5 |

#### 返回参数

```json
{
  "success": true,
  "message": "查询成功",
  "data": [
    {
      "chunkId": 30001,
      "articleId": 10001,
      "chunkIndex": 1,
      "chunkText": "标题\n正文内容",
      "chunkSummary": "正文内容",
      "embeddingId": "10001-1-xxx",
      "score": 0.9321
    }
  ]
}
```

#### 说明

- 检索前会先将 `query` 转成 embedding
- 再与 Redis 中文章索引集合里的 chunk 向量做余弦相似度计算
- 结果按 score 从高到低排序
- 返回的是当前文章内最相关的片段

---

## 10. AI Gateway 接口文档

### 8.1 统一 AI 编辑请求

- URL：`POST /api/ai/gateway`
- 功能：接收来自知识库编辑器的统一 AI 请求，由 Orchestrator 进行意图识别、工作流编排和结果生成
- 返回方式：普通 JSON 或 SSE，取决于请求参数 `stream`

#### 请求参数

```json
{
  "requestId": "req-10001",
  "articleId": 10001,
  "conversationId": null,
  "entryPoint": "selection",
  "intent": "polish",
  "stream": true,
  "selectedText": "这段话写得不够清楚。",
  "inputText": "这段话写得不够清楚。",
  "context": {
    "title": "我的第一篇文章",
    "summary": "整理一篇产品思考笔记",
    "beforeText": "前文上下文",
    "afterText": "后文上下文",
    "language": "zh-CN",
    "targetLanguage": "en-US",
    "style": "formal"
  },
  "options": {
    "preserveOriginal": false,
    "resultAction": "replace"
  }
}
```

#### 字段说明


| 字段名            | 类型      | 必填  | 说明                                                         |
| -------------- | ------- | --- | ---------------------------------------------------------- |
| requestId      | String  | 是   | 请求唯一标识，用于幂等和追踪                                             |
| articleId      | Long    | 是   | 当前文章 ID                                                    |
| conversationId | Long    | 否   | 关联会话 ID，若无则为空                                              |
| entryPoint     | String  | 是   | 入口类型（selection / context-menu / toolbar / command-palette） |
| intent         | String  | 否   | 显式意图，若为空则由 Router 自动识别                                     |
| stream         | Boolean | 是   | 是否使用流式输出                                                   |
| selectedText   | String  | 否   | 当前选中文本                                                     |
| inputText      | String  | 是   | 实际输入文本                                                     |
| context        | Object  | 否   | 上下文信息                                                      |
| options        | Object  | 否   | 运行选项                                                       |


#### 返回参数（非流式）

```json
{
  "success": true,
  "message": "处理成功",
  "data": {
    "requestId": "req-10001",
    "articleId": 10001,
    "intent": "polish",
    "resultAction": "replace",
    "outputText": "这段话的表达不够清晰，可以进一步优化。",
    "summary": "润色完成",
    "references": []
  }
}
```

### 8.2 流式 AI 编辑

- URL：`POST /api/ai/gateway/stream`
- 功能：知识库 AI 编辑流式输出
- 返回方式：`text/event-stream`

#### 请求参数

与 `POST /api/ai/gateway` 一致。

#### SSE 事件格式

##### 1. 开始事件

```json
{
  "type": "ai-start",
  "requestId": "req-10001",
  "articleId": 10001,
  "intent": "polish"
}
```

##### 2. 增量输出

```json
{
  "type": "ai-delta",
  "content": "这段话"
}
```

##### 3. 结果事件

```json
{
  "type": "ai-result",
  "requestId": "req-10001",
  "resultAction": "replace",
  "outputText": "这段话的表达不够清晰，可以进一步优化。"
}
```

##### 4. 结束事件

```json
{
  "type": "ai-end",
  "requestId": "req-10001",
  "status": "SUCCESS"
}
```

##### 5. 停止事件

```json
{
  "type": "ai-stop",
  "requestId": "req-10001",
  "status": "STOPPED"
}
```

##### 6. 错误事件

```json
{
  "type": "ai-error",
  "message": "模型调用失败"
}
```

### 8.3 停止知识库 AI 生成

- URL：`POST /api/ai/gateway/stop`
- 功能：停止当前文章的 AI 生成任务

#### 请求参数

```json
{
  "articleId": 10001,
  "requestId": "req-10001"
}
```

#### 返回参数

```json
{
  "success": true,
  "message": "已请求停止生成",
  "data": null
}
```

### 8.4 获取 AI 操作记录

- URL：`GET /api/kb/articles/{articleId}/ai-logs`
- 功能：查询文章相关 AI 操作历史
- 鉴权：需要登录

#### 返回参数

```json
{
  "success": true,
  "message": "查询成功",
  "data": [
    {
      "requestId": "req-10001",
      "intent": "polish",
      "entryPoint": "selection",
      "status": "SUCCESS",
      "createdAt": "2026-05-10T10:10:00"
    }
  ]
}
```

---

## 9. 前端交互约定

### 9.1 选中文本 AI

1. 用户在 Tiptap 中选中文本。
2. 前端展示悬浮操作栏。
3. 用户点击 `AI`。
4. 前端提交选中文本、上下文和目标意图给 `AI Gateway`。
5. 返回结果后，前端按 `resultAction` 执行替换或插入。

### 9.2 右键 Ask AI

1. 用户在编辑器内容区域右键。
2. 前端展示上下文菜单。
3. 用户选择 `Ask AI`。
4. 前端将当前段落、光标上下文和文章信息提交后端。
5. 后端根据意图路由生成结果。

### 9.3 Mermaid 插入

1. 用户触发 Mermaid 生成。
2. 后端返回 Mermaid 源码和图类型。
3. 前端在编辑器中插入 Mermaid 代码块。
4. 如渲染失败，允许用户回退为纯文本。

---

## 11. 错误处理

### 10.1 常见错误

- `文章不存在`
- `文章已删除`
- `无权限访问文章`
- `请求内容为空`
- `AI 生成失败`
- `停止生成失败`
- `文章保存失败`
- `Mermaid 解析失败`

### 10.2 失败处理原则

- 用户保存失败时，前端应提示并保留编辑区内容
- AI 调用失败时，应保留选中文本原样不变
- 流式生成中断时，应将已生成内容尽量落库或保留在前端草稿区
- Redis 停止标识在结束后应主动清理，避免污染后续请求

---

## 12. 后续可扩展项

- 多模型切换
- Prompt 模板管理
- 文章标签与知识图谱
- 文章级别向量检索
- AI 结果一键应用 / 撤销
- 文章段落级评论与协作批注
- AI 使用量统计与额度控制
- 批量文章整理与自动归档

---

## 14. 附：接口清单


| 功能       | 方法     | 路径                                      | 说明                       |
| -------- | ------ | --------------------------------------- | ------------------------ |
| 获取文章列表   | GET    | /api/kb/articles                        | 获取当前用户知识库文章列表            |
| 获取文章详情   | GET    | /api/kb/articles/{articleId}            | 获取文章正文与元信息               |
| 创建文章     | POST   | /api/kb/articles                        | 新建知识库文章                  |
| 保存文章     | PUT    | /api/kb/articles/{articleId}            | 保存文章内容                   |
| 删除文章     | DELETE | /api/kb/articles/{articleId}            | 删除文章                     |
| 查询版本列表   | GET    | /api/kb/articles/{articleId}/versions   | 查询文章历史版本                 |
| 回滚版本     | POST   | /api/kb/articles/{articleId}/rollback   | 回滚到指定版本                  |
| RAG 文章入库 | POST   | /api/kb/rag/articles/{articleId}/ingest | 重新切片并写入 Redis Stack 向量索引，返回 chunk 数量与 embeddingId 列表 |
| RAG 基础召回 | GET    | /api/kb/rag/search                      | 基于已入库切片进行基础召回，返回命中的 chunk、分数与来源文章           |
| AI 统一入口  | POST   | /api/ai/gateway                         | 知识库 AI 编辑统一入口            |
| AI 流式入口  | POST   | /api/ai/gateway/stream                  | 知识库 AI 流式输出              |
| 停止 AI 生成 | POST   | /api/ai/gateway/stop                    | 停止当前知识库 AI 任务            |
| 查询 AI 日志 | GET    | /api/kb/articles/{articleId}/ai-logs    | 查询文章 AI 操作记录             |


