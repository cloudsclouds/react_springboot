# 用户注册与登录技术方案及接口文档

## 1. 目标

本方案用于支撑当前项目的用户注册、验证码获取、登录能力，并保证前端页面、React 接口封装和 Spring Boot 后端实现保持一致。

## 2. 技术方案

### 2.1 总体架构

- 前端：React + React Router
- 后端：Spring Boot + MyBatis + Redis
- 数据库：MySQL
- 缓存：Redis
- 通信方式：HTTP JSON

### 2.2 认证流程设计

#### 注册流程

1. 用户在注册页填写邮箱。
2. 前端调用“获取验证码”接口。
3. 后端校验邮箱是否已注册。
4. 后端基于 Redis 生成并保存验证码，设置有效期和频率限制。
5. 开发环境会直接把验证码返回给前端；正式环境改为邮件发送，不回传明文验证码。
6. 用户填写昵称、密码、验证码后提交注册。
7. 后端校验验证码、写入 MySQL 用户表，成功后删除 Redis 中的验证码。

#### 登录流程

1. 用户输入邮箱和密码。
2. 前端调用登录接口。
3. 后端根据邮箱查询用户。
4. 后端比对密码。
5. 登录成功后返回用户 ID、昵称及 JWT Token。
6. 前端将 Token 等登录信息写入 `localStorage`，并在后续接口请求中通过 Header 携带 Bearer Token 进行身份认证。

### 2.3 当前实现说明

- 前端登录成功后会把 Token 及用户信息存到 `localStorage` 的 `paperdesk-user`。
- 后端通过 `JwtInterceptor` 统一拦截 `/api/**` 请求（过滤登录和注册接口），解析 Token 获取当前用户身份。
- 主题色配置存到 `localStorage` 的 `blocknote-theme`。
- 当前密码校验为明文比对，适合开发阶段；生产环境建议改为 BCrypt 哈希。
- 注册验证码当前由后端直接返回前端，适合开发调试；生产环境建议替换为邮件发送。

### 2.4 数据存储

#### 用户表

当前 MySQL 用户表字段包括：

- `id`
- `username`
- `email`
- `password`
- `nickname`
- `avatar`
- `created_at`

#### Redis Key 设计

- `auth:register:code:{email}`：注册验证码，TTL 5 分钟
- `auth:register:cooldown:{email}`：验证码发送冷却标记，TTL 60 秒
- `auth:register:count:{email}`：1 小时内发送次数计数

## 3. 接口约定

### 3.1 通用约定

- Base URL：`http://localhost:8080/api`
- 请求方式：`POST`
- 请求头：`Content-Type: application/json`
- 鉴权方式：请求头中携带 `Authorization: Bearer <token>`
- 返回格式：统一返回 JSON 对象

### 3.2 登录接口

#### 接口信息

- URL：`POST /api/auth/login`
- 功能：校验邮箱和密码，返回登录结果和 JWT Token

#### 请求参数

```json
{
  "email": "admin@test.com",
  "password": "123456"
}
```

#### 参数说明


| 字段     | 类型   | 必填 | 说明                                 |
| -------- | ------ | ---- | ------------------------------------ |
| email    | string | 是   | 用户邮箱，不能为空且必须符合邮箱格式 |
| password | string | 是   | 登录密码                             |

#### 返回参数

```json
{
  "success": true,
  "message": "登录成功",
  "token": "eyJhbGciOiJIUzI1NiIsInR5c...",
  "userId": 1,
  "nickname": "Admin"
}
```


| 字段     | 类型          | 说明         |
| -------- | ------------- | ------------ |
| success  | boolean       | 是否成功     |
| message  | string        | 结果说明     |
| token    | string\| null | JWT 认证令牌 |
| userId   | number\| null | 用户 ID      |
| nickname | string\| null | 用户昵称     |

#### 失败示例

```json
{
  "success": false,
  "message": "密码错误",
  "token": null,
  "userId": null,
  "nickname": null
}
```

### 3.3 获取注册验证码接口

#### 接口信息

- URL：`POST /api/auth/register/code`
- 功能：生成注册验证码并写入 Redis

#### 请求参数

```json
{
  "email": "team@paperdesk.app"
}
```

#### 参数说明


| 字段  | 类型   | 必填 | 说明                                 |
| ----- | ------ | ---- | ------------------------------------ |
| email | string | 是   | 注册邮箱，不能为空且必须符合邮箱格式 |

#### 返回参数

```json
{
  "success": true,
  "message": "验证码已生成",
  "code": "123456"
}
```


| 字段    | 类型          | 说明                                     |
| ------- | ------------- | ---------------------------------------- |
| success | boolean       | 是否成功                                 |
| message | string        | 结果说明                                 |
| code    | string\| null | 验证码；开发环境返回，生产环境建议不返回 |

#### 失败示例

```json
{
  "success": false,
  "message": "该邮箱已注册",
  "code": null
}
```

### 3.4 用户注册接口

#### 接口信息

- URL：`POST /api/auth/register`
- 功能：校验验证码并创建用户

#### 请求参数

```json
{
  "email": "team@paperdesk.app",
  "nickname": "Paperdesk Team",
  "password": "123456",
  "code": "123456"
}
```

#### 参数说明


| 字段     | 类型   | 必填 | 说明       |
| -------- | ------ | ---- | ---------- |
| email    | string | 是   | 注册邮箱   |
| nickname | string | 是   | 用户昵称   |
| password | string | 是   | 登录密码   |
| code     | string | 是   | 邮箱验证码 |

#### 返回参数

```json
{
  "success": true,
  "message": "注册成功",
  "userId": 2
}
```


| 字段    | 类型          | 说明        |
| ------- | ------------- | ----------- |
| success | boolean       | 是否成功    |
| message | string        | 结果说明    |
| userId  | number\| null | 新建用户 ID |

#### 失败示例

```json
{
  "success": false,
  "message": "验证码错误",
  "userId": null
}
```

## 4. 前端接入方式

### 4.1 接口封装

前端目前通过 `src/api/user.ts` 统一封装认证接口：

- `loginUser(payload)`
- `getRegisterCode(payload)`
- `registerUser(payload)`

底层请求封装位于 `src/api/http.ts`。当本地存在 JWT Token 时，请求拦截器会自动加上 Header：
`Authorization: Bearer <token>`

### 4.2 登录后的前端状态

登录成功后前端写入：

```json
{
  "token": "eyJhb...",
  "userId": 1,
  "nickname": "Admin",
  "email": "admin@test.com"
}
```

存储位置：`localStorage.paperdesk-user`

退出登录时清理该键，并跳转到 `/login`。

## 5. 校验与错误处理

### 5.1 前端校验

- 登录：邮箱不能为空、邮箱格式正确、密码不能为空
- 注册：邮箱不能为空、邮箱格式正确、昵称不能为空、密码不能为空、验证码不能为空

### 5.2 后端校验

- 使用 `jakarta.validation` 对请求 DTO 做基础校验
- 邮箱不存在、密码错误、验证码过期、验证码频率限制等，都由后端返回明确 message
- 没有 Token 或 Token 无效时，请求其它接口统一返回 `401 Unauthorized`

### 5.3 常见错误信息

- `用户不存在`
- `密码错误`
- `该邮箱已注册`
- `请先获取验证码`
- `验证码错误`
- `无权限访问 / Token 过期` (401)

## 6. 安全建议

1. 生产环境中不要把验证码直接返回给前端，改为邮件发送。
2. 生产环境中不要使用明文密码比对，应该使用 BCrypt 或同类哈希算法。
3. 建议增加注册验证码图形校验或行为校验，降低被刷接口风险。
4. 建议对登录、验证码请求做更严格的限流和审计日志。

## 7. 后续可扩展项

- JWT 双 Token（Access Token + Refresh Token）刷新机制
- 退出登录接口（Token 黑名单机制）
- 找回密码流程
- 邮箱验证码邮件发送
- 用户头像、角色、状态管理
- 第三方登录（如 GitHub / Google）

## 8. 附：当前接口清单


| 功能           | 方法 | 路径                    | 说明                   |
| -------------- | ---- | ----------------------- | ---------------------- |
| 登录           | POST | /api/auth/login         | 根据邮箱和密码登录     |
| 获取注册验证码 | POST | /api/auth/register/code | 生成验证码并写入 Redis |
| 注册           | POST | /api/auth/register      | 校验验证码并创建用户   |
