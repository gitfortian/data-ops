# A8.2f — Project Membership / RBAC Relation 端口物理迁移

> 总跟踪 [A8 #412](https://github.com/gitfortian/data-ops/issues/412)，基线链：#428（权限码）→ #430（声明）→ #432（注册规划）→ #436（Permission/DAO）。本分支从尚未合并的 #436 最新已通过 HEAD `eda915acae8dfd02a62aba894dd5c08db1cf0c9b` 派生。全部架构分支继续 Draft + `do-not-merge`；以 main 为 PR target 运行全量 CI，但实际差分包含堆叠祖先，不能单独或乱序合并。

## 本轮生产迁移

旧 `data-ops-framework/data-security/src/main/java/io/yak/framework/security/` 物理移出四个现有生产类至 `data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/`，**原 package、public FQCN、成员字段、Lombok 注解、接口方法都按原文本保留**：

| 源文件（相对 security 包） | 新 Owner 责任 |
|---|---|
| `common/entity/UserProject.java` | 用户 ID + 用户类型 + Project ID 三维项目成员关系合同 |
| `common/entity/UserRole.java` | 用户–角色绑定合同（含两参数构造和无参构造） |
| `common/entity/RolePermission.java` | 角色–权限授予的关系合同 |
| `dao/RolePermissionDao.java` | 角色权限授予、撤销、查询的五个稳定端口方法 |

旧 Starter 对应 Java 源文件已经全部删除；原有类 FQCN 现在唯一由 Platform JAR 提供，Starter 通过前置 #428 已建立的单向 Maven 依赖消费它们。**不产生第二份同名类，不通过重命名包名掩盖迁移风险**。

## 项目和 RBAC 的保护边界

- `UserProjectDao` 直接依赖旧 `UserProjectPO` 与 `UserProjectDTO`，`UserRoleDao` 依赖 `UserRolePO`；这两个接口及所有 MyBatis 实现、Project/RBAC 事务服务仍由旧 Starter 执行。
- `RolePermissionDaoImpl` 与原 `RolePermissionMapper` 保持插入、角色/权限撤销、权限 ID 查询的原有 SQL 表达；`RolePermissionServiceImpl` 保持 `yakSecurityTransactionManager` 与既有缓存失效策略。
- Project 成员关系的 `userType` 字段保留，不将 Data Ops Project 与其它安全领域的项目模型混淆；本轮不切换 TenantLineInterceptor、当前用户项目选择、Session 或 401/403。
- 用户角色、权限关联等旧数据库 PO、Flyway migration path 和历史 checksum 均**没有修改**；并未重新建表、回填或删除角色授权记录。

## 自动保护与证据

- Platform 新增 `SecurityRelationPortContractTest`，校验 4 个旧 FQCN、五个 RolePermissionDao 反射方法、Lombok POJO 与 Project 成员关系三维字段/构造方法保持兼容。
- Starter 新增 `SecurityRelationPortCompatibilityTest`，核对旧 MyBatis DAO 仍实现迁移后的端口；角色权限覆盖时先删后增、重复 ID 去重、空权限集只删不加、缓存失效和独立事务管理器仍保持。
- `scripts/architecture/check-security-relations-owner.mjs` 及 9 项正反测试，检测重复旧类、丢失新类型、变更 userType / grant 删除接口、Platform 逆向依赖及角色缓存失效策略回退；实际在 `Architecture Checks` 执行。
- 本批只提供编译和 Mockito / 静态合同回归，**不是**真实 MySQL/PostgreSQL 的 Project 隔离与历史 Flyway 升级证据。

## 后续严格门槛

下一批应针对受 PO/DTO 耦合的 ProjectDao / UserProjectDao / UserRoleDao 与相应运行时服务设计层次迁移，同时形成 A8.2 的真实 MySQL、PostgreSQL 安装/旧数据升级、RBAC 加入退出、403 拒绝、role grant 保留、权限失活、事务回滚以及 Memory/Redis Session 组合验证。A8.6 正序组合必须核对 #415 架构守卫对历史 FQCN 的过渡处理，且**不能以扩大白名单方式假装没有 Framework 依赖**。

回滚该批：从 Platform 删除以上四份源码，再将原始字节内容恢复到旧 Starter 相应路径；保留上游已批准的其他合同迁移。任何时候严禁两端同时存在同一个 Java FQCN。所有架构 PR 均保持 Open + Draft + `architecture-refactor` + `do-not-merge`，无明确用户授权禁止合并。
