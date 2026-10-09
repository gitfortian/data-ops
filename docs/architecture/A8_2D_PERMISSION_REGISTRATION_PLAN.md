# A8.2d — Permission Registration 归一化 Owner 迁移

> 追踪 [A8 总计划 #412](https://github.com/gitfortian/data-ops/issues/412)。
>
> **堆叠依赖**：本批从尚未合并的 [A8.2c PR #430](https://github.com/gitfortian/data-ops/pull/430) 修复后 HEAD `e0dbbd67ae45f1603ee2ad960ea1dbf6ae2c89c9` 派生，#430 又依赖 #428；新 PR 以 `main` 为目标运行完整 CI，diff 中暂包含 #428 / #430，**不能作为独立可乱序合并的补丁**。所有架构 PR 继续 Draft + `do-not-merge`。

## 本次真实迁移范围

**迁移权限声明的确定性归一化算法，而非把旧 DAO 整类复制入新模块。**

- 在现有 `data-ops-platform-security-contract` 增加 `io.yak.framework.security.permission.PermissionDeclarationPlan`（新 API；不替换现存消费者的 FQCN），由 Platform 拥有按声明生成待写入权限计划的逻辑。
- 原 `PermissionRegistrationService.synchronize(Collection<PermissionDefinition>)` 继续使用 `@Transactional(transactionManager = "yakSecurityTransactionManager")`，保留 `PermissionDao.synchronizeDeclared(List<Permission>)` 调用；现在只将 Plan 条目映射为已有 `Permission` 实体。**不会改变 DB/Role Grant 的实际持久化 owner**。
- Plan 按声明顺序输出组（`level=1,leaf=false`）和子权限（`level=2,leaf=true,parentCode=group`）；`LinkedHashMap.putIfAbsent` 保持历史 first-wins 语义：同 code、name 和 leaf 的重复条目不改首条 description/menuCode；name 或 leaf 冲突抛原文 `Conflicting permission declaration: <code>`。
- `active=true`、`declared=true`、parentCode、menuCode 和完整的旧同步 API 在 Starter 保持不变；空定义集仍调用 DAO 的 `synchronizeDeclared(empty list)`，以保留原有旧声明失活语义。
- Platform 不引用 Framework Security DAO/Entity、Spring Transaction、Data-Ops Common/Business/Boot；不新建重复 Security 平台模块。

## 验证与责任分界

- Platform 新增 `PermissionDeclarationPlanTest`，检验组先于叶、Project 权限 parent code、字段 trim、first-wins、冲突异常与空集。
- Starter 扩充 `PermissionRegistrationServiceTest`，抓取实际 DAO payload 对比旧数据语义、确保空集仍写入、直接读取 `@Transactional` 管理器。
- Architecture Checks 新增仓库级守卫和七项正反测试：禁止回退内联算法、丢失事务/parentCode、Platform 逆向引用 DAO/Framework 或破坏 first-wins。
- 并继续运行 #428 / #430 的权限码及 ABI 合同测试和其他现有 Security/Project 控制器回归。

## 风险边界、下一步与回滚

本 PR **没有完成** `PermissionRegistrationService`、`PermissionRegistrationInitializer`、`PermissionDaoImpl`、Project/RBAC 领域服务、Security Starter 或认证流程的整体物理迁移。登录、401/403、Token/Session（内存/Redis）、Bean 自动配置、MySQL/PostgreSQL Flyway 路径/SQL/历史版本均未改动。新旧 API 与测试通过不等于历史实例升级、安全缓存失效、完整 RBAC 或真实 Project 隔离通过；它们仍是 A8.2 / A8.6 的硬门槛。

回滚本批：恢复旧 `PermissionRegistrationService` 内的 `LinkedHashMap` + `putUnique` 算法，删除 Platform 中新增的 Plan 和测试/守卫；保留已经经过批准的 #428/#430 迁移（它们目前也尚未获授权合并）。未经用户明确授权，**不合并、关闭或重定向架构 Draft**。
