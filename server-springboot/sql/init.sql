DROP TABLE IF EXISTS knowledge_article_chunks;
DROP TABLE IF EXISTS knowledge_article_versions;
DROP TABLE IF EXISTS knowledge_articles;
DROP TABLE IF EXISTS ai_conversation_message;
DROP TABLE IF EXISTS ai_conversation;
DROP TABLE IF EXISTS share_links;
DROP TABLE IF EXISTS document_versions;
DROP TABLE IF EXISTS document_members;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS users;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(100) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS documents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  owner_id BIGINT NOT NULL,
  owner_name VARCHAR(100) NOT NULL,
  latest_snapshot LONGTEXT,
  version INT DEFAULT 1,
  status TINYINT DEFAULT 0 COMMENT '0-正常, 1-回收站',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL COMMENT 'owner, editor, viewer, no_access',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_doc_user (document_id, user_id)
);

CREATE TABLE IF NOT EXISTS document_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot LONGTEXT NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS share_links (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  share_token VARCHAR(128) NOT NULL UNIQUE,
  permission VARCHAR(20) NOT NULL COMMENT 'viewer, editor',
  expire_time DATETIME,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '会话所属用户 ID',
  title VARCHAR(200) NOT NULL COMMENT '会话标题',
  summary VARCHAR(255) NOT NULL DEFAULT '空对话' COMMENT '内容摘要',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0-正常, 1-删除',
  use_rag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 RAG（0-关闭，1-开启）',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_ai_conversation_user_id (user_id),
  KEY idx_ai_conversation_status (status)
);

CREATE TABLE IF NOT EXISTS ai_conversation_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  role VARCHAR(20) NOT NULL COMMENT 'user 或 assistant',
  content LONGTEXT NOT NULL COMMENT '消息内容',
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED、GENERATING、FAILED、STOPPED',
  request_id VARCHAR(64) DEFAULT NULL COMMENT '一次请求的唯一标识',
  citations LONGTEXT DEFAULT NULL COMMENT '引用片段 JSON',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_ai_conversation_message_conversation_id (conversation_id),
  KEY idx_ai_conversation_message_request_id (request_id)
);

CREATE TABLE IF NOT EXISTS knowledge_articles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '文章所属用户 ID',
  title VARCHAR(200) NOT NULL COMMENT '文章标题',
  summary VARCHAR(500) DEFAULT NULL COMMENT '文章摘要',
  content LONGTEXT NOT NULL COMMENT 'Tiptap JSON 内容',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0-正常, 1-删除',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_knowledge_articles_user_id (user_id),
  KEY idx_knowledge_articles_status (status)
);

CREATE TABLE IF NOT EXISTS knowledge_article_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL COMMENT '文章 ID',
  version_no INT NOT NULL COMMENT '版本号',
  snapshot LONGTEXT NOT NULL COMMENT '版本快照',
  source VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT 'manual, ai',
  created_by BIGINT NOT NULL COMMENT '创建人',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_article_version (article_id, version_no),
  KEY idx_knowledge_article_versions_article_id (article_id)
);

CREATE TABLE IF NOT EXISTS knowledge_article_chunks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL COMMENT '文章 ID',
  chunk_index INT NOT NULL COMMENT '片段序号',
  chunk_text LONGTEXT NOT NULL COMMENT '片段内容',
  chunk_summary VARCHAR(500) DEFAULT NULL COMMENT '片段摘要',
  embedding_id VARCHAR(128) NOT NULL COMMENT 'Redis Stack 向量 ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_knowledge_article_chunks_article_chunk (article_id, chunk_index),
  UNIQUE KEY uk_knowledge_article_chunks_embedding_id (embedding_id),
  KEY idx_knowledge_article_chunks_article_id (article_id)
);

-- 测试数据 (Test Data)
INSERT INTO users (id, username, email, password, nickname) VALUES 
(1, 'admin', 'admin@test.com', '123456', '管理员'),
(2, 'test_user', 'user@test.com', '123456', '测试用户'),
(3, 'editor_user', 'editor@test.com', '123456', '编辑用户');

INSERT INTO documents (id, title, owner_id, owner_name, latest_snapshot, version, status) VALUES 
(1, '产品需求文档', 1, '管理员', '{"blocks":[{"type":"heading","level":1,"text":"产品需求文档"},{"type":"paragraph","text":"这是一个用于测试的产品需求文档快照。"},{"type":"paragraph","text":"作者：管理员"},{"type":"list","ordered":false,"items":["支持标题编辑","支持作者信息展示","支持快照加载测试"]}]}', 1, 0),
(2, '前端架构设计', 2, '测试用户', '{"blocks":[{"type":"heading","level":1,"text":"前端架构设计"},{"type":"paragraph","text":"这是一个用于测试的前端架构文档快照。"},{"type":"paragraph","text":"作者：测试用户"},{"type":"list","ordered":false,"items":["组件拆分","状态管理","接口联调"]}]}', 1, 0);

INSERT INTO document_members (document_id, user_id, role) VALUES 
(1, 1, 'owner'),
(1, 2, 'editor'),
(1, 3, 'viewer'),
(2, 2, 'owner'),
(2, 1, 'no_access');

INSERT INTO document_versions (document_id, version_no, snapshot, created_by) VALUES 
(1, 1, '{"state": "test snapshot 1"}', 1),
(2, 1, '{"state": "test snapshot 2"}', 2);

INSERT INTO share_links (document_id, share_token, permission, expire_time) VALUES 
(1, 'token1234567890', 'viewer', '2030-12-31 23:59:59'),
(2, 'token0987654321', 'editor', '2030-12-31 23:59:59');

INSERT INTO ai_conversation (id, user_id, title, summary, status, use_rag) VALUES 
(1, 1, '新对话', '空对话', 0, 0),
(2, 1, '产品方案讨论', '空对话', 0, 1);

INSERT INTO knowledge_articles (id, user_id, title, summary, content, status) VALUES
(10001, 1, '我的第一篇文章', '整理一篇产品思考笔记', '{"type":"doc","content":[{"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"我的第一篇文章"}]},{"type":"paragraph","content":[{"type":"text","text":"这里是文章正文。"}]}]}', 0),
(10002, 1, 'Redis Stack RAG 方案', '记录向量检索设计思路', '{"type":"doc","content":[{"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"Redis Stack RAG 方案"}]},{"type":"paragraph","content":[{"type":"text","text":"这里是关于 Redis Stack 作为向量数据库的设计说明。"}]}]}', 0),
(10003, 2, '团队周报模板', '适合团队协作的文章模板', '{"type":"doc","content":[{"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"团队周报模板"}]},{"type":"paragraph","content":[{"type":"text","text":"这是测试用户的文章示例。"}]}]}', 0);

INSERT INTO knowledge_article_versions (id, article_id, version_no, snapshot, source, created_by) VALUES
(20001, 10001, 1, '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"初始版本内容"}]}]}', 'manual', 1),
(20002, 10002, 1, '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Redis Stack 方案初稿"}]}]}', 'manual', 1),
(20003, 10003, 1, '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"团队周报模板初稿"}]}]}', 'manual', 2);

INSERT INTO knowledge_article_chunks (id, article_id, chunk_index, chunk_text, chunk_summary, embedding_id) VALUES
(30001, 10001, 1, '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"初始版本内容"}]}]}', '初始版本内容', '10001-1-demo'),
(30002, 10002, 1, '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Redis Stack 方案初稿"}]}]}', 'Redis Stack 方案初稿', '10002-1-demo'),
(30003, 10003, 1, '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"团队周报模板初稿"}]}]}', '团队周报模板初稿', '10003-1-demo');