# 纯知识库（KB）技术方案与接口文档

> 本文档只定义“知识库（Knowledge Base）”域能力：文章管理、版本管理、文档入库、切片与检索。

## 1. 目标

建设一套稳定的个人知识库基础设施，覆盖：

- 文章的创建、编辑、保存、删除
- 文章版本历史与回滚
- 文档上传与文章化
- 基于切片与向量索引的基础检索（供 AI 或其他上层能力复用）

本文档不定义 AI 会话、SSE 对话流、智能体编排。

---

## 2. 范围

- 知识库文章列表与详情
- 文章创建/保存/删除
- 版本列表与回滚
- 文档上传并自动入库
- 指定文章重新入库
- 向量基础召回检索

---

## 3. 技术方案

### 3.1 总体架构

- 前端：React + Tiptap（编辑器）+ KnowledgeBase 页面
- 后端：Spring Boot + MyBatis
- 数据库：MySQL（文章、版本、切片元数据）
- 向量索引：Redis Stack（embedding 向量与 KNN 检索）
- 通信：HTTP JSON

### 3.2 页面与交互（KB 视角）

1. 左侧知识库列表：展示标题、摘要、更新时间、状态
2. 右侧编辑器：编辑正文、保存、切换文章
3. 版本面板：查看版本并回滚
4. 入库操作：上传文档、手动触发重新入库

### 3.3 后端模块（KB）

- `kb/controller`
- `kb/service`
- `kb/service/impl`
- `kb/dto`
- `kb/entity`
- `kb/mapper`
- `kb/config`
- `kb/constant`
- `kb/util`

---

## 4. 核心流程

### 4.1 文章编辑与自动保存流程

1. 前端获取文章详情并初始化编辑器
2. 用户在前端维护撤销 / 重做栈，编辑过程中的临时修改仅保留在客户端状态中
3. 前端每分钟检查一次是否存在未保存变更；若存在则触发自动保存
4. 用户手动点击保存时也可立即触发保存
5. 后端接收保存请求后，比较标题 / 摘要 / 正文是否与最新已保存版本一致
6. 若内容发生变化，则更新文章并新增一条版本快照；若无变化，则返回 `changed=false`，不生成新版本
7. AI 相关编辑操作若触发了内容落库，也必须生成版本并写入 AI 操作日志

### 4.2 版本回滚流程

1. 查询版本列表
2. 选择目标 `versionNo`
3. 后端将对应快照恢复到文章正文
4. 写入新的版本记录，来源统一标记为 `rollback`

### 4.3 文档入库流程

1. 上传文件创建文章
2. 解析为统一文本结构（优先结构化）
3. 切片（见切片规则）
4. 生成 embedding
5. 向量写入 Redis Stack，元数据落 MySQL

### 4.4 基础召回流程

1. 接收 `query`（可选 `articleId`）
2. 生成 query embedding
3. 在 Redis Stack 做相似度检索
4. 返回 chunk 列表（含分数、来源）

---

## 5. 存储设计

### 5.1 文章表 `knowledge_articles`


| 字段名        | 类型           | 说明              |
| ---------- | ------------ | --------------- |
| id         | BIGINT       | 主键              |
| user_id    | BIGINT       | 所属用户            |
| title      | VARCHAR(200) | 标题              |
| summary    | VARCHAR(500) | 摘要              |
| content    | LONGTEXT     | Tiptap JSON     |
| status     | TINYINT      | 状态（0 正常 / 1 删除） |
| created_at | TIMESTAMP    | 创建时间            |
| updated_at | TIMESTAMP    | 更新时间            |


### 5.2 版本表 `knowledge_article_versions`

| 字段名        | 类型          | 说明                         |
| ---------- | ----------- | -------------------------- |
| id         | BIGINT      | 主键                         |
| article_id | BIGINT      | 文章 ID                      |
| version_no | INT         | 版本号                        |
| snapshot   | LONGTEXT    | 快照                         |
| source     | VARCHAR(20) | 来源（manual / autosave / ai / rollback） |
| created_by | BIGINT      | 创建人                        |
| created_at | TIMESTAMP   | 创建时间                       |

设计说明：

- 该表只记录“最终可回滚版本”，不记录编辑过程。
- 每次内容真正保存成功且内容有变化时，必须新增一条版本记录。
- 自动保存、手动保存、AI 落库保存、回滚保存都属于生成版本的场景，其中自动保存的 `saveSource` 约定为 `autosave`，手动保存约定为 `manual`。
- 前端撤销 / 重做栈切换本身不生成版本，只有触发保存并落库时才生成版本。
- 若本次保存内容与最新版本一致，返回 `changed=false`，不生成新版本。


### 5.3 切片表 `knowledge_article_chunks`


| 字段名           | 类型           | 说明            |
| ------------- | ------------ | ------------- |
| id            | BIGINT       | 主键            |
| article_id    | BIGINT       | 文章 ID         |
| chunk_index   | INT          | 片段序号          |
| chunk_text    | LONGTEXT     | 片段内容          |
| chunk_summary | VARCHAR(500) | 片段摘要          |
| embedding_id  | VARCHAR(128) | Redis 向量记录 ID |
| created_at    | TIMESTAMP    | 创建时间          |

## 5.5 Redis/向量索引约定

- `kb:rag:vector:{embeddingId}`：单向量记录
- `kb:rag:index:{articleId}`：文章维度索引集合
- embedding 推荐：`qwen-text-embedding-v3`
- Redis 不可用时允许应用层降级排序（保障可用性）

---

## 6. 切片规则（Tiptap JSON）

- `heading` 作为章节边界，维护标题路径
- `paragraph` 作为基础语义单元
- `list` / `bulletList` / `orderedList` 保留列表语义
- `codeBlock` / `blockquote` 保留块边界
- 目标长度约 1100 字符（最小 500，最大 1500）
- 按句号/换行优先断句，必要时按长度硬切
- 相邻 chunk 保留约 120 字符重叠
- 结构化失败时回退纯文本切片

---

## 7. 接口约定

### 7.1 通用

- Base URL：`http://localhost:8080/api`
- 返回：`ApiResponse<T>`
- 鉴权：需要登录

### 7.2 统一返回

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

---

## 8. KB 接口文档

### 8.1 获取文章列表

- `GET /api/kb/articles`

返回示例：

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

### 8.2 获取文章详情

- `GET /api/kb/articles/{articleId}`

返回示例：

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

### 8.3 创建文章

- `POST /api/kb/articles`

请求示例：

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

返回示例：

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

### 8.4 保存文章

- `PUT /api/kb/articles/{articleId}`
- 说明：仅在内容变化时新增版本

请求示例：

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

返回示例：

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

补充说明：

- 标题、摘要或正文任一变化都会触发新版本
- 若无变化，返回 `changed: false`，`versionNo` 维持当前最新值

### 8.5 删除文章

- `DELETE /api/kb/articles/{articleId}`

返回示例：

```json
{
  "success": true,
  "message": "删除成功",
  "data": null
}
```

### 8.6 查询版本列表

- `GET /api/kb/articles/{articleId}/versions`

返回示例：

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

### 8.7 回滚版本

- `POST /api/kb/articles/{articleId}/rollback`

请求示例：

```json
{
  "versionNo": 12
}
```

返回示例：

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

## 9. RAG 基础检索接口（KB 域）

### 9.1 上传并自动入库

- `POST /api/kb/rag/upload`
- `multipart/form-data`

请求参数：


| 字段名   | 类型     | 必填  | 说明         |
| ----- | ------ | --- | ---------- |
| files | File[] | 是   | 一个或多个待上传文件 |


返回示例：

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

说明：

- 文件名默认作为文章标题
- `.json` 优先按结构化内容解析
- `.md` / `.txt` 包装为 Tiptap 文档结构
- 建议单文件/单请求体控制在 20MB 内，超限返回 413

### 9.2 指定文章重新入库

- `POST /api/kb/rag/articles/{articleId}/ingest`

返回示例：

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

说明：

- 先删除旧 chunk 与对应向量引用
- 再按最新正文重新切片并入库

### 9.3 基础召回

- `GET /api/kb/rag/search?query=...&articleId=...&topK=...`
- 参数说明：
  - `query`：必填，检索关键词
  - `articleId`：选填；不传时在当前用户可访问的全部知识文章范围内检索，传入时仅在指定文章内检索
  - `topK`：选填，返回候选数量，默认 `5`

返回示例：

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

说明：该接口只负责“检索返回候选片段”，不负责“生成最终答案”。答案生成在 AI 域处理（见 `docs/ai-spec.md`）。

---

## 10. 路由约定（KB 页面）

- 基础路由：`/knowledge-base`
- 文章路由：`/knowledge-base/:articleId`

行为要求：

1. 直接访问 `/knowledge-base/:articleId` 时优先恢复该文章
2. 点击文章列表同步更新地址栏
3. 新建文章后自动跳转到新文章路由
4. 进入 `/knowledge-base` 且存在文章时，可默认选中第一篇并同步路由

---

## 11. 错误处理

### 11.1 常见错误

- 文章不存在 / 已删除
- 无权限访问
- 请求参数非法
- 入库失败 / embedding 失败
- 检索失败
- 保存失败

### 11.2 原则

- 保存失败时保留前端编辑内容
- 入库失败返回明确失败原因与可重试建议
- 检索失败允许前端降级提示，不影响文章编辑主流程

---

## 12. 后续扩展

- 标签与分类体系
- 批量导入与批量归档
- 知识图谱关系抽取（KB 域）
- 更细粒度的检索过滤（时间、标签、作者）

---

## 13. 附：KB 接口清单


| 功能       | 方法     | 路径                                                             | 说明              |
| -------- | ------ | -------------------------------------------------------------- | --------------- |
| 获取文章列表   | GET    | /api/kb/articles                                               | 获取当前用户知识库文章列表   |
| 获取文章详情   | GET    | /api/kb/articles/{articleId}                                   | 获取正文与元信息        |
| 创建文章     | POST   | /api/kb/articles                                               | 新建文章            |
| 保存文章     | PUT    | /api/kb/articles/{articleId}                                   | 保存并按需新增版本       |
| 删除文章     | DELETE | /api/kb/articles/{articleId}                                   | 逻辑删除            |
| 查询版本列表   | GET    | /api/kb/articles/{articleId}/versions                          | 版本历史            |
| 回滚版本     | POST   | /api/kb/articles/{articleId}/rollback                          | 回滚到目标版本         |
| 查询文章操作日志 | GET    | /api/kb/articles/{articleId}/operation-logs                    | 分页查询操作历史        |
| 查询操作日志详情 | GET    | /api/kb/articles/{articleId}/operation-logs/{operationId}      | 查询单次操作完整记录      |
| 记录一次操作日志 | POST   | /api/kb/articles/{articleId}/operation-logs                    | 写入一条操作日志        |
| 撤销指定操作   | POST   | /api/kb/articles/{articleId}/operation-logs/{operationId}/undo | 恢复到操作前状态并记 UNDO |
| 重做指定操作   | POST   | /api/kb/articles/{articleId}/operation-logs/{operationId}/redo | 应用操作后状态并记 REDO  |
| 上传并入库    | POST   | /api/kb/rag/upload                                             | 上传文件并自动切片入库     |
| 文章重新入库   | POST   | /api/kb/rag/articles/{articleId}/ingest                        | 覆盖旧索引重新入库       |
| 基础召回检索   | GET    | /api/kb/rag/search                                             | 返回候选片段与分数       |


