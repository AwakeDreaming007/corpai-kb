# 仓库指南

## 项目结构与模块组织

本仓库包含 LangChain4j 知识库应用的 Vue 3 前端。应用代码位于 `src`：

- `src/api`：Axios 和 Server-Sent Events 客户端。
- `src/components`：共享 UI 组件。
- `src/directives`：自定义指令，例如权限检查。
- `src/layout`、`src/router` 和 `src/stores`：应用外壳、路由与路由守卫、Pinia 状态管理。
- `src/styles`：全局设计令牌和样式。
- `src/utils`：格式化、Markdown 渲染和流式传输辅助函数。
- `src/views`：路由级页面，例如 `KbListView.vue` 和 `ChatView.vue`。

静态资源放在 `public`。不要编辑或提交 `dist` 中的生成文件。Vite 别名 `@` 映射到 `src`。

## 构建、测试与开发命令

- `npm install`：安装依赖。需要 Node `^22.18.0` 或 `>=24.12.0`。
- `npm run dev`：启动 Vite 开发服务器（端口 `5173`）并启用热更新。
- `npm run build`：在 `dist` 中生成生产环境构建产物。
- `npm run preview`：在本地预览生产构建结果。

开发服务器会将 `/api` 请求代理到 `http://localhost:8080`。在测试需要认证的功能前，请先启动兼容的后端服务。

## 编码风格与命名约定

Vue 组件使用 JavaScript 和 `<script setup>`。遵循现有的两空格缩进，并省略分号，除非语法要求必须使用。优先使用 ES 模块；跨主要目录导入时使用现有的绝对路径别名。组件、Store、API 模块和工具函数应使用描述性名称，例如 `KbDetailView.vue`、`useUserStore` 和 `sse.js`。API 封装保留在 `src/api`；共享客户端状态和本地存储逻辑放在 `src/stores`。Element Plus 是标准 UI 组件库；应复用 `src/styles/index.css` 中的全局设计令牌，而不是引入零散的颜色或间距值。

## 测试指南

当前尚未配置测试框架、覆盖率阈值或 lint 格式化工具。提交变更前，请运行 `npm run build`，并通过 `npm run dev` 手动验证受影响的路由和交互。如果新增测试，可将其放在对应代码附近，或创建 `tests` 目录；测试文件使用 `*.spec.js` 命名，并在 `package.json` 中配置测试命令。

## 提交与 Pull Request 指南

此检出副本没有可参考的 Git 历史来确认现有提交规范。请使用简短的祈使句提交标题，推荐遵循 Conventional Commits，例如 `feat: add source drawer pagination` 或 `fix: preserve active chat session`。

Pull Request 应包含简要说明、相关 Issue 或任务链接（如适用）、已执行的验证，以及用户界面变更的截图。保持变更聚焦，并注明任何后端、权限或环境依赖。
