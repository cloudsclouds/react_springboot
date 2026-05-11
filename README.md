# 前端启动

`pnpm start`

# 后端启动

```
cd server-springboot 
./mvnw spring-boot:run
```

邮箱：[admin@test.com](mailto:admin@test.com) 密码：123456

## 数据库

```
mysql -u root -p
Enter password: 123456
```

1. 数据库名：platform

```
mysql -u root -p platform  < server-springboot/sql/init.sql
Enter password: 123456
```

## 启动redis

```
brew services start redis
redis-cli ping
```

## 实现功能

1. 认证与账户：支持用户名密码登录、注册。
2. 文档编辑与协同：支持 Tiptap 块编辑、多人协同、权限控制、协作光标、只读模式、搜索、模板插入。文档服务支持创建、删除、重命名、复制、移动、保存内容、创建分享链接、访问分享文档、下载文档。
3. 文档历史与导出发布：支持快照历史、恢复、删除、清空历史；支持复制为 HTML、导出 PDF。
4. 分享访问：支持分享链接访问、密码校验、过期/不存在/网络异常处理。
5. AI 能力：一条是知识库编辑器内 AI，右键 AI 菜单，支持续写、润色、头脑风暴、AI 翻译、AI 总结 、AI Mermaid，设计成一个多智能体协同工作； 另一条是独立 AI 页面，默认先做知识库向量检索，再进行流式聊天，并展示引用。
6. 知识库：支持创建知识库、上传文件、添加 URL、获取知识库列表和详情、删除知识库。
7. 组织管理：支持创建组织、编辑组织、删除组织、成员列表、邀请成员、申请加入、审批加入申请、更新成员角色、移除成员、退出组织、查看邀请与待处理项。

