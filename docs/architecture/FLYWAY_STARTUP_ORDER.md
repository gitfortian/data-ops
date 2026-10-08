# A4.2 Flyway 模块迁移启动顺序契约

> 跟踪：[Issue #368](https://github.com/gitfortian/data-ops/issues/368)。独立基于 `main@d8faa120767c7e0fe609bdd4af7e999c218eb8e4`，与未合并的 A4.1 #380 并行。**仅提交，不合并。**

## 真实代码查证

各业务模块拥有自有 Flyway Bean 和独立历史表，部分模块存在有意义的 Spring `@DependsOn` 依赖：

| 迁移链后继 | 必须先执行 | 真实代码来源 |
|---|---|---|
| `yakMetricFlyway` | `yakDatasetFlyway` | MetricPersistenceConfiguration |
| `yakAgentFlyway` | `yakDatasetFlyway` | AgentPersistenceConfiguration |
| `yakAnalysisFlyway` | `yakDatasetFlyway` | AnalysisPersistenceConfiguration |
| `yakDashboardFlyway` | `yakAnalysisFlyway` | DashboardPersistenceConfiguration |
| `yakModelingFlyway` | `yakSemanticFlyway` | ModelingPersistenceConfiguration |
| `dataServiceFlyway` | `opsDataSourceFlyway` | DataServiceFlywayConfiguration、DataSourceConfiguration |
| `consumptionFlyway` | `opsDataSourceFlyway` | ConsumptionFlywayConfiguration、DataSourceConfiguration |

这说明 Flyway 的业务迁移历史不适合直接合并成一条；执行顺序不是偶然的类名顺序。

## 此批实施

- 新增 `scripts/architecture/flyway-startup-order.mjs`：审计仓库真实 Business/Boot 的 `@Bean Flyway` Bean 名、`@DependsOn` 直接引用，生成静态依赖图；阻止 Bean 名重复、目标缺失、自依赖、循环及上述必需迁移链被意外删除。
- 新增 `scripts/architecture/flyway-startup-order.test.mjs`：正反测试，**实际读取 git 已追踪的生产 Java 文件**，确保不少于现有 15 条显式链，且上述真实依赖均存在。
- 测试由当前 `.github/workflows/architecture-checks.yml` 已有 `node --test scripts/architecture/*.test.mjs` 自动运行，因此不改 CI YAML，避免与 A2.2/#380 并行分支发生额外冲突。
- Framework Security 的独立 datasource/Flyway 不属于 Business/Boot 自有迁移 Bean 依赖图，保留其现有环境兼容及历史迁移策略。

## 保留的不变量

- 不修改 `@DependsOn` 本身、Flyway Config、SQL、Checksum、Schema、MySQL/PostgreSQL 分支逻辑，也不更改应用启动实际行为。
- A4.1 `#380` 负责 history table 和迁移目录归属；本 PR 专门验证 **启动先后依赖**，两批职责互补。
- 静态检查不能替代 Spring 容器生命周期测试，更不能替代数据库真实迁移/重启 Smoke。数据库集成测试持续由已有 `DatabaseMigrationSmokeTest` 和 PostgreSQL 资源环境承担。

## 验收

```bash
node --test scripts/architecture/flyway-startup-order.test.mjs
node --test scripts/architecture/*.test.mjs
```

- [ ] 真实域 Flyway 依赖边无缺失
- [ ] 检测重复/缺失/循环/执行顺序被删的回归
- [ ] Architecture CI / Product Guard 通过
- [ ] Draft + `do-not-merge` + `architecture-refactor`
