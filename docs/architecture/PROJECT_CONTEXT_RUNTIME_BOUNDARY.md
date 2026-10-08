# A3 第一批：Project Context 平台运行时解耦（无业务行为变更）

> 关联 [#368 架构治理](https://github.com/gitfortian/data-ops/issues/368)。
> 源分支来自 `main@1d7bde09c2b3ba4a383a21ddd8404e49579045d6`，与 #370、#373、#375、#377 并行；全部只提交，不合并。

## 现状与职责

已核实的实际代码：

- `data-ops-core/src/main/java/io/yak/ops/core/project/CurrentProject.java` 定义只读的当前 Project 契约。
- `data-ops-core/src/main/java/io/yak/ops/core/project/ProjectContextScope.java` 定义后台执行的显式 Project 范围契约，要求恢复可信上下文。
- `data-ops-boot/.../ProjectContextRuntime.java` 既承担 Spring Bean，又自行维护 `ThreadLocal<ProjectContext>`，嵌套调用时还原上一层状态。
- `data-ops-boot/.../ProjectScopeInterceptor.java` 负责读取可信请求 Header、获取当前用户、调用 `ProjectAccessGuard` 检查并绑定已授权 Project，完成时清理。
- `data-ops-boot/.../YakSecurityProjectAccessGuard.java` 负责调用既有 Security Project/Membership，授权语义不可重写。

这是典型的 **Core 运行机制 / Boot Framework Adapter** 混合问题，可以用最小改动进行架构收敛。

## 本批改动

```text
Business / Runtime
       |
       | CurrentProject / ProjectContextScope（接口不变）
       v
data-ops-core
  ThreadLocalProjectContext
  ├── current() / call() 纯 JDK 实现
  ├── finally 还原上一 Project
  └── protected trusted binding（不开放到公共接口）
       ^
       | extends
data-ops-boot
  @Component ProjectContextRuntime
  ├── bind(context)（仍仅 Boot package 可见）
  └── clear()（仍仅 Boot package 可见）
       ^
       | ProjectScopeInterceptor / background wiring
       |
Yak Security → ProjectAccessGuard（完全不变）
```

将上下文保存、嵌套执行与 `finally` 恢复放在无 Spring 依赖的 Core 实现；保留原 `ProjectContextRuntime` 的**类名、Spring Bean 身份、公开接口与包内方法**。这不是新增业务层或新的 Project Context Truth，只是把已有机制从启动模块中解耦。

## 明确保持不变的契约

1. `X-YAK-SECURITY-PROJECT-ID`、Long Project ID、认证顺序、项目归属、ProjectMembership、权限检查和拒绝审计；
2. HTTP 的 `bind/clear` 行为与 `afterCompletion` 清理；
3. 当前 `CurrentProject.current()/requireProjectId()` 与 `ProjectContextScope.call()/run()` 方法签名；
4. 同步 ThreadLocal 而非 InheritableThreadLocal；不会隐式传递 Project 到新线程；
5. 嵌套作用域正常返回和抛异常都恢复原上下文；
6. 不改任何应用层业务 Service、Controller、迁移脚本、数据库或前端。

## 验收

- [ ] `mvn -pl data-ops-boot -am test`，项目既有 Scope/Interceptor/Security 测试继续通过。
- [ ] 新 Core 单测：嵌套恢复、无父上下文清理、异常恢复、非法入参、跨线程隔离。
- [ ] Boot Adapter 单测：请求范围内运行后台任务抛异常后仍可访问原 Project，随后请求清理。
- [ ] 全仓 Architecture Checks / Product Guard。
- [ ] CI 不通过时仅修改此独立分支，禁止合并。

**注意：** 这是当前单 JVM ThreadLocal 的明确边界。未来异步队列/跨进程/分布式 Worker 仍必须显式恢复持久化 Project Context，不得通过自动继承 ThreadLocal 偷换安全前提。
