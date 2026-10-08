# A5 第一批：前端领域 Service 的 HTTP 传输边界

> 当前架构工作总线：[Issue #368](https://github.com/gitfortian/data-ops/issues/368)。
> 本 PR 由 `main@64109d1861a0b650a8ed4ee81aeb5c3954ca0939` 独立建立，**仅提交不合并，标为 Draft / do-not-merge**。

## 真实代码依据

- `data-ops-ui/src/utils/request.tsx` 封装共享 `umi-request`，负责 Cookie、Project Header、认证失效、统一业务错误处理及 `skipErrorHandler`。
- `data-ops-ui/src/utils/HttpUtils.tsx` 提供业务服务的统一调用入口；实际的 `metric/api.ts`、`semantic/api.ts`、`data-source/api.ts`、`data-development/api.ts` 已使用该入口。
- `data-ops-ui/src/services/workflow/instances.ts` 和 `schedules.ts` 通过 `@/utils/request` 调用共享客户端；`security/client.ts` 也使用共享客户端。
- `data-ops-ui/src/app.tsx` 内的 `request: RequestConfig` 是 **Umi 应用级运行时配置**，具有独立的 Project Header 拦截器；不属于需禁止的业务 Service import。
- 已有 `scripts/architecture/check-frontend-boundaries.mjs` 防止跨页面非法依赖，但原来的 Service Umi request 检查依赖在 source 中回溯匹配，不能正确处理跨行 import / import alias 等形式。

## 本轮架构改造

新建纯 Node（无前端依赖）的 `frontend-service-transport.mjs`，由现有 Frontend Boundary Gate 调用：

1. 对 `src/services/**/*.ts(x)` 生产代码阻止新出现的 `import { request }`、别名 `request as ...`、默认 `request` 从 `@umijs/max` / `umi` 直接导入；
2. 保留 `@/utils/request`、`@/utils/HttpUtils`、types-only import 和 App bootstrap 等合法情况；
3. 为违反边界的文件报告精确路径、行号和 import 源，避免难以定位的笼统错误；
4. 加入正反 Node 单测，覆盖 multiline import、alias、type-only、注释、合法共享客户端、Service / Page / App 的职责区别；
5. 在既有 `architecture-checks.yml` 的静态检查命令中生效；**不修改 CI 工作流、路由、API 或任何前端业务代码**。

## 非目标与兼容边界

- 不迁移 Yak UI 的 React Router / Vite 技术栈，也不改 Umi Max。
- 不修改页面、Service 方法签名、网络协议、Project Header、401/403、Token、错误呈现。
- 不把 SSE/流式处理一刀切禁成 `fetch`；Agent SSE 等专业传输保留现有执行语义。
- 本轮目标仅是阻止某个明确的跨层访问方式，后续是否把更多 HTTP Client 迁到 `services/http` 应由真实消费者审计决定。

## 验收

```bash
node --test scripts/architecture/frontend-service-transport.test.mjs
node --test scripts/architecture/*.test.mjs
node scripts/architecture/check-frontend-boundaries.mjs
```

没有执行的 CI 不记为 PASS；通过当前仓库 Architecture Checks / Product Guard 再结束本工作包。**本 PR 保持草稿、不合并。**
