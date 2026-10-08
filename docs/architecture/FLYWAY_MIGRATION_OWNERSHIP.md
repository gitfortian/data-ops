# A4 · Flyway 迁移归属契约（渐进架构重构）

> 关联 [Issue #368](https://github.com/gitfortian/data-ops/issues/368)。独立从 `main` 创建分支；**所有架构 PR 保持 Draft，禁止提前合并**。

## 为什么做

基于当前真实代码，`data-ops` 的迁移不是单一历史：

- `data-ops-business-asset/.../AssetPersistenceConfiguration.java`：`yakAssetFlyway` / `flyway_schema_history_asset` / `db/migration/yak-asset`。
- `data-ops-business-metric/.../MetricPersistenceConfiguration.java`：`yakMetricFlyway` / `flyway_schema_history_metric` / `db/migration/yak-metric`，并依赖 Dataset 首先迁移。
- `data-ops-business-metadata/.../MetadataPersistenceConfiguration.java`：元数据独立 history；关联的共享物理表仍受 Lineage 迁移 steward 约束。
- `data-ops-business-job/.../JobRuntimeFlywayConfiguration.java`：`flyway_schema_history_job_runtime`；`data-ops-boot/.../QuartzDatabaseConfiguration.java` 独立持有 `yak_quartz_schema_history`。
- `data-ops-business-modeling/.../ModelingPersistenceConfiguration.java`：history 和 location 在本地 `static final String` 常量中，不允许误判成没有声明。
- `scripts/db/check-migration-history.mjs` 已校验锁定 SQL 的 SHA-256 和 MySQL/PostgreSQL 镜像文件，**但不检查 history 表重名、迁移目录是否被两个 Spring Bean 声明、配置是否读到其他 Maven 模块的 SQL**。

所以，不能套用新版 Yak 单一 Flyway history 的产品形态，更不能合并既有版本化迁移或重写历史。

## 本批交付

新增纯 Node 审计器 `scripts/db/flyway-ownership.mjs`、CLI `scripts/db/check-flyway-ownership.mjs`，整合到原 Architecture Checks 的静态检查 Job：

1. 从仓库已跟踪的生产 Java 文件读取显式 `@Bean Flyway` / `Flyway.configure()` 注册；不要求把 domain 的 Flyway Bean 迁入 Boot。
2. 提取 Bean 方法、模块 Maven 根目录、history table、`JdbcDatabase.migrationLocation` 的资源命名空间（支持字面量及同类内静态字符串常量）。
3. 防止不同注册者使用相同 history 表、相同迁移目录或重复 Bean 名，保护各业务域独立迁移链。
4. 校验每个声明的 `db/migration/<namespace>` 及 `db/migration-postgresql/<namespace>` SQL 均由其所属模块拥有，避免跨模块资源误绑定。
5. 动态 history / location 表达式明确失败并要求审查，不静默忽略；低于 15 条可读迁移链也会失败，防止审计失效。
6. 增加 Node 正/反测试：静态常量、独立归属、重复 history/目录、跨模块资源、缺 PG 镜像、动态表达式与 legacy 排除。

运行方式：

```bash
node --test scripts/architecture/flyway-ownership.test.mjs
node scripts/db/check-flyway-ownership.mjs
node scripts/db/check-flyway-ownership.mjs --json
```

## 不变量与约束

- **不修改** Flyway Bean、启动顺序、`@DependsOn`、DataSource 或事务管理器；不触碰历史 SQL。
- **不合并** MySQL/PostgreSQL 目录；不变更应用数据表、表字段或用户权限。
- 尊重 Metadata/Lineage 的共表 steward 约定；归属脚本检查不等于跨域共享表字段的 owner 判定。
- 和既有 `check-migration-history.mjs`、数据库 MySQL/PostgreSQL Smoke 保持互补关系。本批属于**静态已声明 Flyway Bean 的检查**，不冒充运行时迁移执行证明。
- 对动态 Flyway/bootstrap、Spring 条件启用、Mapper / XML 与外部 framework starter 的历史表仍需真实数据库及 Spring 容器验证。

## PR 验收清单

- [ ] Node 单元测试与实际仓库审计通过。
- [ ] CI Architecture impact / backend / frontend / distribution 和 Product Guard 检查结果真实可查。
- [ ] 现存各域 `@Bean` / history / migration 路径维持原样。
- [ ] 本 PR 仅涉及架构工具、测试、CI 调用和文档；保持 Draft 与 `do-not-merge`。
