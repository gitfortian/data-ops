# A8.2k–A8.2m｜Security 身份上下文与授权事实的集中归属迁移

> 继 [A8.2h–j #451](https://github.com/gitfortian/data-ops/pull/451) 的**精确四项绿灯 HEAD** `1f73821c7f9979b8a51f53a0489f3a80cd010c65`。本批先在非 PR 分支完成三类迁移、测试和架构守卫，最后仅创建一个 Draft PR，以减少 CI 次数。前置分支 #428 → #430 → #432 → #436 → #440 → #446 → #451 **均未合并**，不可独立或乱序合并。

## A8.2k — 身份认证及授权缓存纯接口的真实物理迁移

从 `data-ops-framework/data-security` 移动到现有唯一 `data-ops-platform-security-contract`，并删除原 Starter 源文件：

- `io.yak.framework.security.authentication.AuthenticationManager`（登录、登出、身份读取、默认用户名/全设备登出兼容）。
- `io.yak.framework.security.service.PermissionCache`（授权事实缓存、失效行为与对旧自定义实现的 default 快照方法兼容）。
- `io.yak.framework.security.context.AuthorizationSnapshot`（不可变角色、权限、菜单和 Project 授权快照，ROOT 特权判断规则不变）。

**所有旧 FQCN、public 签名及默认行为原文保留**，代码物理唯一 Owner 为 Platform Jar。不引入 Sa-Token、Redis、Spring Web、数据库或业务模块的反向依赖。

## A8.2l — 当前请求身份上下文真实物理迁移

原文物理迁入同一个 Platform 模块并从旧 Starter 删除：

- `CurrentUser`（Spring 注入的业务身份接口）。
- `DefaultCurrentUser`（委托静态上下文的兼容适配）。
- `YakSecurityContext`（线程级当前身份、匿名回退、ROOT/Project 授权访问，含 package-private `ImmutableCurrentUser`）。

旧 `YakSecurityContextFilter` 仍在 Starter，继续调用相同 FQCN 的 `setCurrentUser`/finally-`clear`；因此安全过滤链及 ThreadLocal 不制造第二个副本。旧 `TrustedUserScope` 仍在 Starter，同包访问权限与公共类型名保持兼容。

## A8.2m — 认证/授权与租户范围行为保护

- Platform 新增 `SecurityContextPlatformContractTest`：匿名拒绝、ROOT 权限/Project 范围（null Project 始终拒绝）、快照去重/不可变、ThreadLocal 跨线程隔离与清理、旧 FQCN 加载、`AuthenticationManager` 默认登录/注销 ABI。
- Node Guard `check-security-authn-context-owner.mjs` 及 8 项正反测试：禁止旧 Framework 同 FQCN 类复活、缺失 Platform 类、ROOT 检查退化、ThreadLocal clear 丢失、缓存 invalidation 丢失、Sa-Token 适配器或 Filter 失联，以及 Platform→旧 Framework/运行时依赖回流。
- Guard 接入 `Architecture Checks`，继续运行 #451 的 **MySQL 8 + PostgreSQL 16 双库 Flyway/Project/RBAC 实库回归**、原 Starter Sa-Token Mockito 单测与 Framework Integration 整仓验证。

## 非目标、风险与硬门槛

本批**不移动** Sa-Token 实际实现、Redis/Memory Session DAO、`YakSecurityContextFilter`、`TrustedUserScope`（直接依赖旧 UserDao/Snapshot Service）、Project/RBAC SQL/Mapper、独立 Security Spring AutoConfiguration/DataSource/Flyway。HTTP 401/403 和真实 Redis Session 的端到端组合、历史版 MySQL Flyway 迁移链/非合并版本 history 仍在 A8.2/A8.6 硬门槛中，不能因为静态和单测全绿而宣布完成。

兼容风险：从旧 Starter jar 中移走类文件后，依赖其旧 Maven 坐标而绕过传递依赖直接加载单 JAR 的外部消费者可能缺 Platform classpath。最终 A8.5 发行构建必须证明稳定 classpath。回滚时必须恢复上述六个源文件到旧 Starter 路径并删除 Platform 中同名源码，**严禁两端同时出现同一 Java 类**。

⛔ 全部架构 PR 继续 Open + Draft + `architecture-refactor` + `do-not-merge`，未经明确授权禁止合并。
