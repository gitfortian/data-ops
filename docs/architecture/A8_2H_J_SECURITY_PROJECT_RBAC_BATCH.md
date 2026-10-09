# A8.2h–A8.2j｜Security Project/RBAC 集中迁移与双数据库回归

> 总追踪 [A8 #412](https://github.com/gitfortian/data-ops/issues/412)。**批次策略**：按用户要求，将 2h、2i、2j 三个关联任务在**同一个非 PR 分支**先完成，集中检查后仅创建一个独立 Draft PR。基线为已四项 CI 全绿但仍未合并的 [A8.2g #446](https://github.com/gitfortian/data-ops/pull/446)，精确 HEAD `5171493894bd541f090b5b972c6a4874440d4ffd`，其祖先依次 #440 → #436 → #432 → #430 → #428。最终 PR 目标 main 只用于运行全仓 CI；其 diff 暂包含前置 Draft，不得跳过祖先乱序合并。

## A8.2h｜六个生产类型物理迁移

旧 `data-ops-framework/data-security` 内六个文件**原文迁至** `data-ops-platform/data-ops-platform-security-contract`，并从旧源码树删除：

- `io.yak.framework.security.common.entity.BaseEntity`：id、createTime、updateTime、isDelete 公共父实体合同
- `io.yak.framework.security.common.entity.project.Project` / `ProjectBrief`
- `io.yak.framework.security.common.entity.role.Role` / `RoleBrief`
- `io.yak.framework.security.common.dto.user.UserProjectDTO`

原包名、Lombok Bean、继承层次和公开 Java ABI 不变，不产生相同 FQCN 的双份 .class。Platform module 已具备 optional Lombok；Project/Role 与 BaseEntity 一起搬，避免下层源码反向引用上层父类。MyBatis PO/Mapper、Spring Bean、Security Flyway 原地不变。Platform `ProjectRoleModelRelocationTest` 与 7 组架构负例守卫保护这些合同。

## A8.2i｜DAO PO/DTO 读取隔离与兼容桥接

上游 #446 已建立两个 DTO/PO-free Platform 端口。本批继续真实改动服务的查询通路：

- Project 端口新增 `UserProjectCriteria`（id、userId、userType、projectId、isDelete）与 `selectMembershipsByCriteria`；`UserProjectServiceImpl` 从旧 DTO 构造 stable criteria，原 `UserProjectDaoImpl` 转回同字段旧 DTO，再使用**完全相同的**原 MyBatis `select(dto)` 过滤实现。
- Role 端口新增 `selectAssignmentsByRoleIds`、`selectAssignmentsByUserIds`，返回 Platform 已拥有的 `UserRole`。`UserRoleServiceImpl` 不再直接消费 PO 型旧 DAO 方法及 `CopyBeanUtil`；旧 `UserRoleDaoImpl` 负责执行同一原 DAO 查询及 PO→UserRole 拷贝。
- 原 `UserProjectDao`、`UserRoleDao` **保留全部 10 / 7 条原方法声明和旧 DTO/PO 兼容方法**，既有 `@Repository`、Mapper、SQL、缓存及 `yakSecurityTransactionManager` 均未改。新端口是新 superinterface；同一个 DAO 实例同时服务新旧调用，没有第二个 Repository Bean。
- `LegacyMembershipReadAdapterTest` 验证完整查询字段、owner userType、角色 DTO/PO 拷贝、服务分流；现有 Project/RBAC 服务与 Controller 合同测试保留。更新 Platform 端口反射方法数量为 **9 / 7**，以及架构 Owner 守卫，不将旧兼容查询误识别为回退。

## A8.2j｜真实 MySQL 与 PostgreSQL Flyway smoke

Architecture Checks 的后端 job 继续使用 MySQL 8，并**新增 PostgreSQL 16** 服务（原四大顶层工作流不增加运行次数）。`SecurityProjectRbacFlywayJdbcTest` 在真实服务上：

1. 创建隔离 database（MySQL）/schema（PG），先建宿主表模拟非空共享 schema；
2. 用历史配置约束 `baselineOnMigrate(true)` + baseline version `0` + `outOfOrder(true)` 跑**原有** Security 合并 V1 SQL（两个 vendor 原路径/原文件均不改）；
3. 断言 `security:project:read` 权限种子，插入并校验普通成员 userType=0、负责人 userType=1，以及按 app_name 隔离的第三方成员；
4. 插入角色–权限授权，验证 JDBC 事务 `ROLLBACK` 不残留成员；
5. 重跑 Flyway 要求 0 新迁移、validate success，并核对 Project 成员和角色授权仍存在；
6. 每次运行都清理专用 database/schema；不接触其它测试数据。

CI 两个 vendor 通过环境变量启用此测试；无数据库服务的本地/Framework Integration jobs 使用 JUnit Assumptions 跳过实库测试，但仍编译它。另以 `check-security-rbac-db-matrix.mjs` + 6 个 Node 正反测试，防止 PostgreSQL 服务、MySQL 服务、Flyway 运行、Project 用户类型/应用隔离和 RBAC 断言被默默移除。

**验收定义必须严格**：上述是*真实双库首次安装（已有宿主 schema）+ 同版本重复启动 smoke*，不是全旧版 Flyway V1/V2/V3 历史表到合并 V1 的升级复现，不涵盖业务级 HTTP 401/403、Sa-Token/Redis Session 运行时、生产数据回填或所有 Project/RBAC 服务端到端权限决策；这些继续列为 A8.2 后续硬门槛。不能将本轮数据库测试通过等同于 Security 迁移完成。

## 回滚、外部 ABI 与组合顺序

本批无生产 SQL、Flyway history、HTTP API 或配置属性变更。回滚需将上述六类恢复旧 Framework 原路径，并删除 Platform 同 FQCN 源码；撤回新 Port 读方法、DAO 映射转换、Service 调用以及数据库 CI 测试；不能在两个 JAR 中保留相同 class。外部直接使用旧 Starter jar 而没有 Maven 传递依赖的消费者，需要额外验证其运行时 classpath 含 Platform contract jar。

**组合**：#428 → #430 → #432 → #436 → #440 → #446 → 本批，之后仍需与 #415/#417/#420/#423/#424 的架构保护链做正序去重组合。全部架构 PR 必须保留 `⛔【暂勿合并｜架构重构 A8.x】` 标题、Draft + `architecture-refactor` + `do-not-merge`，未经明确批准**不合并 main、不提前关闭或重定向**。
