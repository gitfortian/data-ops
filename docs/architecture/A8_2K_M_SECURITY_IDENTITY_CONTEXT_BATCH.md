# A8.2k–A8.2m｜Security Identity / Context / Authentication 合同集中迁移

> [A8 总计划 #412](https://github.com/gitfortian/data-ops/issues/412) · 集中开发批次，遵守“多个任务在非 PR 分支先实现，再统一创建一个 Draft PR”策略。基于仍是 Draft、但精确 HEAD `1f73821c7f9979b8a51f53a0489f3a80cd010c65` 四组 GitHub CI 已通过的 [A8.2h–j #451](https://github.com/gitfortian/data-ops/pull/451)；祖先依次是 #446→#440→#436→#432→#430→#428。最终 PR 以 main 为目标触发全仓 CI，临时包含全部堆叠祖先，**不能将本 PR 独立或乱序合并**。

## A8.2k｜认证与授权值合同实际物理迁移

从旧 `data-ops-framework/data-security` **保留原 Java FQCN 搬到**唯一 `data-ops-platform-security-contract`（同 Java package/FQCN（仅 AuthorizationSnapshot import 指向 Platform 权威权限码））：

- `authentication.AuthenticationManager`：原 `login`、`logout`、`isLogin`、`getLoginUserId`、`logoutUser`、`getLoginUsername` 接口及默认方法完全保留。上层可继续自定义实现，无需导入 Sa-Token。
- `context.AuthorizationSnapshot`：role、permission、menu、Project 授权事实的不变集合、稳定去重、ROOT 权限绕过和空 Project 禁止。
- `service.PermissionCache`：原 custom 缓存 SPI、snapshot default fallback 和撤销/角色失效方法不变。
- `service.impl.MenuSelectionCodec`：原权限树正/负 ID 编码与顺序去重算法原文迁移，原 FQCN 不变。

原 Security Starter 的对应源文件已删除，**不会生成双份 class**。纯接口/值对象在 Platform 下编译，Sa-Token 登录管理、Redis 存储选项、Caffeine 实现与权限加载仍归旧 Starter。

## A8.2l｜当前用户上下文与用户/部门模型归属迁移

同样物理迁入：

- `context.CurrentUser`、`context.DefaultCurrentUser`、`context.YakSecurityContext`：沿用原 Java FQCN、公开 API、ThreadLocal 作用域、包可见 ImmutableCurrentUser 合同及 `clear()`；不改变 Servlet Filter 的注册或操作顺序。后台 `TrustedUserScope` 继续在旧 Starter 内读取数据库和加载快照，仍调用同名静态 Context API。
- `common.entity.user.User` / `UserBrief`：保留 User→Platform BaseEntity 继承、Lombok 字段/无参构造、默认 status=1、敏感 pw/salt 的 `@ToString.Exclude`。
- `common.entity.dept.Dept` / `DeptBrief`：原用户部门简要实体和完整部门属性。

**共 11 个生产类型唯一源码归属迁移**；未新建 Security module，不增任何 Product→Framework 反向依赖。

## A8.2m｜跨 Jar ABI、安全语义与异常路径回归

- Platform `SecurityPlatformIdentityContextTest`：权限/角色/菜单/Project 去重与不可变，非 Root 越权拒绝，Root 权限兼容及 null Project 拒绝，历史 CurrentUser/FQCN、ThreadLocal 并发线程隔离及清理。
- Platform `SecurityPlatformAuthApiContractTest`：自定义 AuthenticationManager 默认 userName/logoutUser 兼容、不缓存 PermissionCache 默认快照、菜单权限 ID 负数编码与异常行为。
- Platform `SecurityPlatformUserDeptModelTest`：User/Dept Bean 属性、BaseEntity 继承、默认状态与 pw/salt 不进入日志字符串。
- Starter `SecurityIdentityRelocationIntegrationTest`：原路径 11 个类型（及 CurrentUser 内部实现）均恰好一个 Runtime .class；原 SaTokenAuthenticationManager 实现迁移后的 AuthenticationManager，CaffeinePermissionCache 实现迁移后的 Cache SPI；旧 Servlet Filter 和后台 TrustedUserScope 继续连接 Context。
- 原 `YakSecurityContextFilterTest` 增加 Controller/FilterChain 失败时总会清理 ThreadLocal 的回归。既有 TrustedUserScope、SaTokenAuthenticationManager、授权快照加载与缓存测试继续在完整 CI 中执行。
- `scripts/architecture/check-security-identity-context-owner.mjs` + 11 项正反防回退 Node 测试：唯一 FQCN、权限 Snapshot root/Project、ThreadLocal 清理、用户敏感字段、SPI 默认方法、菜单编码和不反向依赖旧 Starter；接入 Architecture Checks 既有工作流而非新增 CI 工作流。

## 明确的兼容与验收边界

**不改变**旧 SQL、MySQL/PG Flyway locations/history、Service 事务/DAO、Spring Bean/AutoConfiguration imports、Sa-Token/StpLogic、Redis DAO 配置、401/403 或 Token 过期策略。移动了类型的**JAR 物理所有权**，外部绕过 Maven 直接加载旧 Starter JAR 的消费者必须确保 Runtime classpath 包含 Platform contract JAR；A8.5 完整发版/程序集测试仍为门槛。

这批回归验证 Spring Starter 到新 Platform 值对象的调用与 Context 清理，但**尚未执行真实 Redis 登录态在多实例间恢复、Sa-Token Redisx 后端验证、完整历史 Flyway pre-consolidation 升级、401/403 HTTP 网关端到端测试**；这些不能用 Mockito、平台单测或前一批数据库烟测替代。A8.6 仍需联合 #415/#417/#420/#423/#424 的架构守卫正序合成。

回滚：将 11 份 Java 源码恢复回原 Starter 旧路径、从 Platform 同 FQCN 路径删除，同时撤销本批回归和单一 Owner 守卫；**任何时刻不得在两个 Jar 同时保留相同类**。所有架构 PR 均保持中文 `⛔【暂勿合并｜架构重构 A8.x】` 标题、Open + Draft + `do-not-merge`，未经用户明确授权不得合并。
