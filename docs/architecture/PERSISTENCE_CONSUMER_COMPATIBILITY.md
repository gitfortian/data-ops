# A2.2 共享持久化 Bean 消费者与兼容性验收

> 架构治理：[Issue #368](https://github.com/gitfortian/data-ops/issues/368)。
> 本工作包独立以 `main@4f7c533bf7b3823a03c60ee2e6218feb0985d65e` 建分支，不依赖未合并 A2.1 #375；严禁提前合并。

## 真实代码锚点

- `data-ops-boot/.../BusinessDatabaseConfiguration.java` 在 main 中拥有四类 `@Primary` Bean，每类暴露 1 个主名 + 3 个兼容别名。
- `data-ops-boot/.../MybatisPlusFactorySupport.java` 定义驼峰映射、NULL JDBC type、禁用 MyBatis 缓存、关闭 Banner。
- `data-ops-boot/.../BusinessDatabaseConfigurationTest.java` 已测 H2 Session 的 PO 别名、事务回滚与部分旧 Bean 名。
- 当前实际 Domain 如 `data-ops-business-asset/.../AssetPersistenceConfiguration.java` 与 `data-ops-business-metric/.../MetricPersistenceConfiguration.java` 的 `@MapperScan(sqlSessionFactoryRef="yakBusinessSqlSessionFactory")` 使用 Boot 装配；Job 等模块同样引用共享 factory。
- MySQL 与 PostgreSQL 的存储差异既有 `DatabaseMigrationSmokeTest` 和 `PostgresqlStorageSmokeTest`；后者需独立 PostgreSQL 测试数据库环境，不能用本地 H2 断言替代。

## 本 PR 实际交付

1. 新增 `scripts/architecture/persistence-consumers.mjs`：定义**16 个历史 Bean 名**的接口契约；在生产 Java 中扫描 `@Qualifier`、`@Resource`、`@MapperScan`、`@DependsOn`、`getBean` 等显式引用，列出真实路径、行号、所属 Maven 模块。审计明确是静态显式消费者清单，无法枚举反射/配置文件隐式消费者。
2. 新增 `scripts/architecture/check-persistence-consumers.mjs`：基于仓库 `git ls-files` 实际生产源码重新生成模块与引用列表。检测 Boot 声明的 16 个历史别名缺失或重复；支持 `--json` 方便后续审查。
3. 新增 `scripts/architecture/persistence-consumers.test.mjs`：测试消费者提取、别名分组、重复/缺失、合法排除，并读取 main 原有 Boot 配置做源码级回归。
4. 新增 `PersistenceBeanCompatibilityContractTest`：真实 Spring `ApplicationContextRunner` + H2，完整检查四组 Bean 主名/兼容别名同一实例、Primary 注入、事务与会话工厂依赖链、MyBatis 映射/缓存/JDBC NULL/分页插件、Hikari 池配置及关闭数据库后的全部 Bean 缺席。
5. 在原 Architecture Checks 工作流新增显式消费者清单输出与别名守卫。不修改数据库或生产 Bean，不替换 Starter 或将 DAO 集中化。

## 历史兼容 Bean 一览

| Bean 角色 | 当前主名 | 需要保留的历史别名 |
|---|---|---|
| DataSource | `yakBusinessDataSource` | `opsDataSource`, `opsResourceDataSource`, `offlineSyncDataSource` |
| TransactionManager | `yakBusinessTransactionManager` | `opsDataSourceTransactionManager`, `opsResourceTransactionManager`, `offlineSyncTransactionManager` |
| SqlSessionFactory | `yakBusinessSqlSessionFactory` | `opsDataSourceSqlSessionFactory`, `opsResourceSqlSessionFactory`, `offlineSyncSqlSessionFactory` |
| SqlSessionTemplate | `yakBusinessSqlSessionTemplate` | `opsDataSourceSqlSessionTemplate`, `opsResourceSqlSessionTemplate`, `offlineSyncSqlSessionTemplate` |

## CI / 验收

```bash
node --test scripts/architecture/persistence-consumers.test.mjs
node scripts/architecture/check-persistence-consumers.mjs
node scripts/architecture/check-persistence-consumers.mjs --json
bash ./mvnw -B -ntp -pl data-ops-boot -am -Dtest=PersistenceBeanCompatibilityContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 静态别名契约及消费者收集 PASS
- [ ] Spring H2 兼容性测试 PASS
- [ ] Architecture Checks 全流程 PASS
- [ ] 与 #375 在集中合并后的组合分支测试仍需 A7 补做
- [ ] MySQL 与 PostgreSQL 的真实生产级回归，以全仓集成 CI 和独立 PG 验收证据为准

## 合并规则

**仅提交 Draft PR + `do-not-merge` / `architecture-refactor`，不合并到 main。**
