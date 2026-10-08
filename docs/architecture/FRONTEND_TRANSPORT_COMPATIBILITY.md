# A5.2 前端 HTTP 与 Agent SSE 传输边界兼容性

> 架构治理 [#368](https://github.com/gitfortian/data-ops/issues/368)。独立于尚未合并的 A5.1 [#377](https://github.com/gitfortian/data-ops/pull/377)；不复制其静态检查代码。

## 已阅读的真实代码

- `data-ops-ui/src/utils/request.tsx`：`umi-request.extend` 默认 `credentials: include`，`applyCurrentProjectHeader` 请求拦截，401/2001 跳登录态失效出口，403 作为权限不足而不是登录过期，`skipErrorHandler` 静默通知但仍拒绝 Promise。
- `data-ops-ui/src/utils/HttpUtils.tsx`：信封兼容返回与 `*Data` 业务异常拒绝双模式。
- `data-ops-ui/src/utils/security/projectContext.ts`：按 URL 最长匹配决定 PROJECT_REQUIRED / PROJECT_OPTIONAL / LEGACY_GLOBAL，业务域 Header 常量为 `X-YAK-SECURITY-PROJECT-ID`；平台级运行时接口不得被强制绑定 Project。
- `data-ops-ui/src/app.tsx`：Umi 应用级 `RequestConfig` 同样调用 Header 适配器；不能误删。
- `data-ops-ui/src/services/agent/api.ts`：Agent SSE 使用原生 `fetch`，需 Cookie、Project Header、`Accept: text/event-stream`、订阅游标及 AbortSignal，**不能换成普通 JSON 请求**。

## 本批实现

- 扩充既有 `utils/request.test.tsx`，从实际请求客户端发起请求，回归 `PROJECT_REQUIRED` Header、`LEGACY_GLOBAL` 不附加 Header、Cookie、HTTP 401 统一登录失效、HTTP 403 权限错误、不静默成功，以及 `skipErrorHandler` 禁止全局通知但仍 reject。
- 新增 `services/agent/transport-boundary.test.ts`，直接调用 `streamTurnEvents`，模拟 SSE event stream：回归原生 fetch Cookie + Project Header + event cursor 续播参数 + 回调；验证 HTTP 403 由流消费者收到错误而不误触发重试/完成；Abort 为用户取消而非网络重试。
- 两组 Jest 契约测试由现有 Architecture Checks 的前端测试链执行；完全不需要改 Umi/Vite、Ant Design、API、Controller、Project ID / RBAC。
- 本工作包**只增加测试与文档，不修改生产请求行为**。

## 验收

```bash
cd data-ops-ui
yarn jest src/utils/request.test.tsx src/services/agent/transport-boundary.test.ts --runInBand
```

- [ ] 普通 HTTP Project Header、401/403、错误处理通过
- [ ] SSE Cookie/Project/cursor/403/Abort 回归通过
- [ ] Frontend Jest / Architecture CI / Product Guard 通过
- [ ] 与 [#377](https://github.com/gitfortian/data-ops/pull/377) 合并前在 A7 组合分支重新运行

**所有架构 PR 保留 Draft + `do-not-merge` + `architecture-refactor`，严禁提前合并。**
