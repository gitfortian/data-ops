# A8.2g — Project / Role Membership PO/DTO-free Port 分离

> A8 总计划 [#412](https://github.com/gitfortian/data-ops/issues/412)。本 PR 从尚未合并的 #440 修复后 HEAD `a2af75ad6e055b44461e3bdf284df80c0fd114f7` 堆叠派生；#440 依赖 #436 → #432 → #430 → #428。目标是 main 用于完整 CI，但 diff 暂包含堆叠祖先。**禁止单独、乱序或未经授权合并**。#440 的 Product Guard / Consumption Checks / Architecture Checks / Framework Integration 已在此轮确认全部通过。

## 生产改造——真实转移了什么

在已存在的 **`data-ops-platform-security-contract`** 模块新增两个纯 Platform 端口：

- `io.yak.ops.platform.security.port.UserProjectMembershipPort`：8 个现有的 Project 成员 ID / `UserProject` 关系查询、插入、删除方法，明确保留 **userType** 参数，使负责人(1)与普通成员(0)区分；不引入旧 UserProjectPO、DTO、MyBatis 或数据库依赖。
- `io.yak.ops.platform.security.port.UserRoleAssignmentPort`：5 个现有的用户-角色关系 ID 查询、插入、删除与计数方法，仅消费 #440 已迁入 Platform 的 `UserRole` 模型。

旧 `UserProjectDao`、`UserRoleDao` 均 **extends** 新 Platform 端口，保留原有 FQCN、原有 **全部 10 和 7 个方法的原始声明**，包括依赖旧 `UserProjectDTO` / `UserProjectPO` 和 `UserRolePO` 的查询。这保证已编译消费者、`getDeclaredMethod` 反射 API、已有 `@Repository` MyBatis 实现的类型不被直接删除或重命名。

两个旧 Service 的构造器签名、Spring Bean 注解未变化：接收旧 DAO 时，同时将 **同一个 DAO 实例** 赋给新 Platform port-typed 字段；纯 Project / Role 关系操作改经新端口调用，旧 DTO/PO 查询继续使用旧 DAO。不会新建第二个 DAO Bean，不会改变 Mapper、连接池、缓存失效或独立 Security 事务管理器。

**此阶段不宣称已整体搬迁旧 UserProjectDao / UserRoleDao**：旧两接口和 DAO 实现仍在 Framework，Platform 拥有的是新稳定子接口及真实服务调用路径。下个阶段才适合安全地继续拆除旧 PO/DTO 的返回值或定义独立 adapter，不能把本次视作 Framework 完全消失。

## 验收保护

- Platform `SecurityMembershipPortsTest` 对 8 / 5 个端口逐项反射检查方法签名与 DTO/PO-free 边界。
- Security Starter `MembershipPortWiringTest` 证明两个旧 DAO 仍实现新端口，旧 DAO 各自仍声明全部原方法，两个 Service 的构造器及端口引用实际使用同一对象；Project 负责人关系 userType=1 与用户去重/缓存行为不变；角色更新先撤销再写入，空更新只删除，事务仍绑定 `yakSecurityTransactionManager`。
- 新增 `check-security-membership-ports.mjs` 和 9 项 Node 正反测试及 Architecture Checks CI hook，禁止退回旧接口、平台倒流依赖、重复运行时 Bean 或断裂旧 PO/DTO 查询。
- 原有 #428→#440 的静态、Java 和产品构建门槛继续生效。

## 风险、硬性后续门槛与回滚

这次没有移动 MyBatis PO / DTO、Flyway location 或 SQL history，也不修改 RBAC 认证、Token / Session、401/403、Project 可见性及缓存事务行为。**项目 MySQL/PostgreSQL 实库隔离、历史 Flyway 校验和旧用户授权迁移尚未执行**，不以 Mockito 与 CI 编译替代实库验收。

外部二进制兼容约束：原 DAO 保持所有声明及 FQCN；新增 Platform 端口是新的运行时 superinterface，最终发行必须带着 Platform JAR 一起加载，后续 A8.5 需核对实际分发 classpath。

撤销时将旧 DAO 的 `extends` 与 Service 的 Port 字段/调用路径恢复，移除新 Platform 两个接口及对应测试/守卫；上游 #440/436/432/430/428 仍保持独立。未经明确授权，所有架构 PR 继续 Open + Draft + `architecture-refactor` + `do-not-merge`，不合并 main。
