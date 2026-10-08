# A3.2 Project / RBAC 请求生命周期的安全清理

> 跟踪 [Issue #368](https://github.com/gitfortian/data-ops/issues/368)。独立从 `main@d8faa120767c7e0fe609bdd4af7e999c218eb8e4` 建立；不依赖未合并的 A3.1 #379。本 PR 保持 Draft / do-not-merge，禁止合并。

## 真实源码发现的问题

`data-ops-boot/project/ProjectScopeInterceptor.java` 的 `preHandle` 负责：

- 请求开始 `authorizationAudit.beginRequest()` 和清理上一个 CurrentProject。
- 解析 `X-YAK-SECURITY-PROJECT-ID`（Long 正整数），从 Yak Security 授权服务取得已验证 ProjectContext。
- 拒绝缺失 / 非法 Project 后向 HTTP Response 写入既有 400/404 业务错误码，并返回 `false`。
- 过去仅在 `afterCompletion` 调用 `authorizationAudit.endRequest()` 和 `currentProject.clear()`。

**Spring MVC 的生命周期关键点：** 拦截器 `preHandle` 返回 `false` 时，其 `afterCompletion` 不会被调用；在 `preHandle` 抛异常时也不能依赖它清理。因而拒绝路径产生的审计决策可能残留在复用的 HTTP 线程中，直到后续请求再次清理。ProjectContext 在处理前被清掉，但缺少拒绝路径的显式 finally 清理保证。

## 本批代码修改

1. 将现有 Spring MVC 分支判定提取至同类私有 `evaluateRequest(...)`（保持原处理顺序和返回值），`preHandle` 包裹 `Exception | Error` 清理并原样重新抛出，避免异常阻断后留下线程上下文。
2. `ProjectContextException` 对应已有响应 400/404 + 业务码保持不变；**返回 false 前使用 finally 清理**当前审计上下文与 ProjectContext。
3. `afterCompletion` 仍保留现有 `try / finally` 正常请求清理，不影响 Project/RBAC 的授权流程。
4. 扩展原 `ProjectScopeInterceptorTest`：缺失 ID、非法 ID、认证 provider 抛异常和 Project Guard 抛异常时，都**刻意不模拟调用 afterCompletion**，直接核实 `authorizationAudit.endRequest` 和 Project 清理。
5. 不涉及 Project Header、ID 类型、权限 Grant/Deny 判定、角色成员、ProjectContext API、业务 Controller/Service、数据库或前端。

## 与 A3.1 的关系

- A3.1 #379 在 `data-ops-core` 提炼 ThreadLocal context 存储机制，Boot 层适配继续持有 trusted bind/clear。
- A3.2 专门修正 Boot HTTP interceptor 的异常/拒绝请求清理；**没有复制、继承或改动尚未合并的 Core 实现**。
- 未来一起合并时需要 A7 组合分支再次运行 Spring MVC / Security / Audit 回归。

## 验收

```bash
bash ./mvnw -B -ntp -pl data-ops-boot -am -Dtest=ProjectScopeInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 既有缺失 header/非法 header/成功授权的响应与审计测试继续通过
- [ ] 被拒绝时无需 `afterCompletion` 也执行清理
- [ ] 抛异常时清理并原样传播
- [ ] 全部 CI Architecture / Product Guard 验证
- [ ] 保持 Draft、`do-not-merge`、`architecture-refactor`

这是**请求安全边界正确性修复**，不改变用户可见的允许/拒绝策略和响应码。
