# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Spring Boot 3.5.16、Java 21 和 LangChain4j 的企业知识库问答后端，同时保留了一组早期的 LangChain4j 聊天/RAG 演示接口。后端使用 PostgreSQL + pgvector 保存业务数据和向量，Redis 保存问答记忆；相邻目录 `D:\project\java\langchain4j-vue` 是配套 Vue 3 前端。

主要模型配置如下：

- 智谱 BigModel：OpenAI 兼容聊天接口，用于同步聊天模型。
- DeepSeek：同步及流式聊天模型，企业知识库问答使用流式模型。
- 阿里云 DashScope Qwen：仅用于生成 1536 维 Embedding。

## 本地依赖与配置

启动完整应用前需要：

- Redis：`localhost:6379`
- PostgreSQL/pgvector：`localhost:5432`，数据库 `xufg_db`，用户 `postgres`
- 环境变量 `KB_JWT_SECRET`：JWT 签名密钥，至少 32 字节；未配置时企业登录不可用
- 模型 API Key（按实际使用配置）：`ZHIPU_API_KEY`、`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`

默认配置位于 `src/main/resources/application.yaml`：数据库密码可由 `DB_PASSWORD` 覆盖，默认本地值为 `123456`；文档上传目录为 `./uploads`；后端端口为 8080。模型名称、检索阈值、Redis 记忆 TTL 和异步入库对账周期也在该文件中配置。

## 常用命令

后端命令在仓库根目录执行。Windows 使用 `mvnw.cmd`，类 Unix 环境使用 `./mvnw`。

```text
mvnw.cmd compile
mvnw.cmd spring-boot:run
mvnw.cmd test
mvnw.cmd test -Dtest=KbChatServiceTest
mvnw.cmd test -Dtest=KbChatServiceTest#methodName
mvnw.cmd package
```

配套前端命令：

```text
cd ..\langchain4j-vue
npm install
npm run dev
npm run build
```

`Langchain4jJavaApplicationTests` 会加载完整 Spring 上下文，需要 PostgreSQL/pgvector 和 Redis 可用；其余服务测试多数为 Mockito/单元测试。项目没有单独配置 lint 脚本，前端 `package.json` 当前提供 `dev`、`build`、`preview`。

初始化数据库（本地 Docker 容器名为 `local-postgres`）可执行：

```text
docker exec -i local-postgres psql -U postgres -d xufg_db -f /dev/stdin < sql/schema.sql
```

`sql/schema.sql` 可重复执行；`sql/init_data.sql` 提供本地角色、权限和初始数据。

## 代码结构与请求链路

- 根应用类是 `src/main/java/com/xufg/Langchain4jJavaApplication.java`。
- `controller` 负责 HTTP 路由、参数绑定和部分方法权限声明；`service` 负责业务规则、权限校验、事务边界和异步任务；`mapper` 使用 MyBatis-Plus 访问 PostgreSQL；`entity` 映射数据库表；`dto` 定义接口请求/响应契约。
- `common/Result` 是统一返回包装；`BizException` 表示业务错误；`GlobalExceptionHandler` 负责将业务异常、校验异常、认证/授权异常、非法请求体和不支持的方法转换为统一业务码。
- `SecurityConfig` 配置无状态 Spring Security、CORS 和公开认证路由。`JwtAuthFilter` 解析 Bearer JWT、填充认证上下文并查询用户状态。系统权限通过 `@PreAuthorize` 控制，知识库内权限由 `KbPermissionService` 按 OWNER/EDITOR/VIEWER 再次校验。
- `UserContext` 提供当前请求用户 ID。需要在事务中判断成员是否存在时使用 `KbPermissionService.findRoleOrNull`，不要捕获已抛出的权限异常后继续提交同一事务，否则事务可能已被标记为 rollback-only。

## LangChain4j 与 RAG 架构

- `config/ChainConfig` 创建智谱同步模型、DeepSeek 同步/流式模型，以及内存和 Redis 两种 `ChatMemoryProvider`。Redis provider 使用 `RedisChatMemoryStore`，企业会话 ID 同时作为 `qa_session.id` 和 LangChain4j memory ID。
- `config/PgVectorConfig` 创建 DashScope `EmbeddingModel` 和 `PgVectorEmbeddingStore`。Embedding 维度必须与 `application.yaml` 和 `vector_store` 表结构一致；Bean 名称和 `@Order` 不要随意修改，因为 `RagConfig` 依赖这些 Bean。
- `KbIngestService` 在 `kbIngestExecutor` 异步线程池中处理 PDF/DOC/DOCX：解析 → 递归分段 → 批量 embedding → 写入 pgvector。每个分段 metadata 必须包含字符串形式的 `kbId`、`docId`、`fileName`，以保证跨库检索隔离和按文档清理。
- 文档状态为 `0=处理中`、`1=成功`、`2=失败`。解析和外部 embedding 调用放在事务外，状态更新使用短事务；`KbIngestRecoveryTask` 定时对账处理中记录并补偿清理孤儿向量。覆盖上传、删除和重建索引时要保持文档记录、文件和向量的一致性。
- `KbChatService` 执行企业问答：校验成员和会话归属 → 按 `kbId` 过滤 pgvector → 组装编号上下文 → 调用流式聊天模型。响应通过 SSE 输出 `token`、`sources`、`done` 或 `error` 事件；无命中时直接返回固定提示，不调用模型。回答完成、模型失败或客户端取消时，问题和回答记忆/历史应成对写入。
- `QaHistoryService` 异步保存 `qa_history`，来源列表以 JSONB 保存；`QaFeedbackService` 对 `(history_id, user_id)` 做点赞/点踩/取消 upsert。

## 企业知识库 API

企业接口均位于 `/api/**`，除登录/注册外需要 JWT：

- `/api/auth/login`、`/api/auth/register`、`/api/auth/me`：认证和当前用户信息。
- `/api/sys/users`、`/api/sys/roles`、`/api/sys/permissions`：用户、角色、权限管理，分别受 `sys:user:manage` 或 `sys:role:manage` 保护。
- `/api/kb`：知识库分页、创建、更新、删除；创建需要 `kb:create`，更新/删除由 OWNER 或 `kb:manage` 放行。没有单独的 `GET /api/kb/{id}` 详情路由，前端详情页从当前用户可见列表中定位知识库。
- `/api/kb/{kbId}/members`：OWNER 管理成员；库内角色为 OWNER、EDITOR、VIEWER，唯一 OWNER 和转让流程有保护。
- `/api/kb/{kbId}/docs`：成员查看文档，EDITOR 及以上上传、删除、重建索引。
- `/api/kb/{kbId}/sessions` 和 `/api/kb/{kbId}/chat/stream`：创建/查询会话及 SSE 问答，VIEWER 及以上可用。
- `/api/history` 和 `/api/feedback`：当前用户的历史和反馈；历史列表会裁剪 `answer`/`sources` 大字段，`sessionId` 查询参数可选。

## 旧版演示接口

`ChatController` 中的 `/chat`、`/streaming/chat`、`/chatMemory`、`/storeText`、`/storeTextPDF`、`/findSimilarTexts` 是早期演示接口，与企业 `/api/**` 链路分开。新增企业功能应优先使用企业 Controller/Service、JWT 权限和知识库隔离逻辑，不要绕过这些服务直接操作向量库。

## 配套前端

前端工程位于 `D:\project\java\langchain4j-vue`，使用 Vue 3、Vite、Element Plus、Pinia 和 Vue Router：

- `src/api` 封装后端请求，`src/views` 是页面，`src/components` 是可复用组件，`src/stores` 管理登录用户状态。
- `src/router/permission.js` 负责登录守卫和权限码守卫，`v-perm` 指令控制按钮显示。
- `src/utils/sse.js` 使用 `fetch`、`ReadableStream`、`AbortController` 和 Bearer token 解析 SSE；EventSource 不能满足当前鉴权要求。
- Vite 将 `/api` 代理到后端 8080。问答页切换会话时加载历史，发送完成后只更新当前消息；不要无条件重新请求会话列表，否则会导致列表闪烁并重复播放入场动画。

## 数据库

DDL 位于 `sql/schema.sql`，初始化数据位于 `sql/init_data.sql`。核心业务表分为三组：

- RBAC：`sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`
- 知识库：`kb_base`、`kb_member`、`kb_document`
- 问答：`qa_session`、`qa_history`、`qa_feedback`

`vector_store` 由 LangChain4j 使用；`sql/schema.sql` 为 `metadata->>'kbId'` 和 `metadata->>'docId'` 建立表达式索引，并为问答历史补充知识库/会话外键。分页参数在服务层统一限制为 `page >= 1`、`size <= 100`。

## 编辑约定

- 用中文回答，新增文档使用中文；新增 Java 类和方法应补充与现有代码风格一致的注释。
- 修改模型配置、向量 metadata、事务边界、异步线程池、JWT 过滤器或 SSE 生命周期时，应同时检查对应服务、Controller、配置和测试，避免只改单点造成契约不一致。
- 前端动画应优先使用 opacity/transform，遵守现有 reduced-motion 规则，不要让动画改变问答、上传轮询或会话切换的业务时序。

## 项目工作流

以下工作流仅在用户明确请求或调用对应命令时启用，不要默认执行：

- 需要先锁定 OpenSpec 需求时优先使用 `openspec-superpowers-workflow`，完成 `tasks.md` 后先总结并等待用户确认，再询问是否继续开发。
- 用户确认后接受 `继续开发` / `continue-dev`；开发和验证完成后，再询问是否 `继续审查` / `continue-review`。
- 如果归档是下一步，接受 `继续归档` / `continue-archive` 或使用 `$openspec-archive-change`。
- 若仓库存在 `.superpowers-memory/`，按工作流维护其中的共享项目记忆。
