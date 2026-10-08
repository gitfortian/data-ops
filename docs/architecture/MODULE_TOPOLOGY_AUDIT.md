# A1 · data-ops 实际 Maven / Java 依赖拓扑审计

> 状态：只读架构审计工具，独立 PR，**未获批准前不得修改现有 Maven 依赖和业务功能**。
>
> 跟踪：[架构治理 Issue #368](https://github.com/gitfortian/data-ops/issues/368)，对照 [产品母路线 #180](https://github.com/gitfortian/data-ops/issues/180)。
>
> 基线：本分支由 `main@1fc7b51b201551e507196f79a80af8b24a8a67b3` 建立（2026-10-08）。后续运行结果以实际 checkout 中的 POM、Java imports 为准，避免把某次静态报告误认为永久事实。

## 目的

新版 Yak 供参考的是 **模块职责清晰、Boot 负责最终装配、技术能力只通过明确的 Port/Adapter 连接**，不是其 Service 实现、数据模型、权限语义、业务模块数量或物理 DAO 目录。

在做任何真实重构之前，必须先回答：现在有哪些 **实际** Maven 模块？谁编译依赖谁？哪些 import 发生在跨模块边界？哪些依赖是正常的产品装配，哪些才是潜在架构问题？

## 产物

新增 `scripts/architecture/module-topology.mjs`，通过现有 Maven POM 和 **生产 Java 源文件**生成随当前分支自动更新的证据：

1. 从根 POM 的 `<modules>` 递归收集真实 Reactor 节点（包括 Framework、聚合器和插件）。若子 POM 缺失则失败，不静默漏项。
2. 为每个节点输出 `directory/groupId/artifactId/packaging/role`。Role 仅用于架构分组，不改变产品能力/Service 角色。
3. 读取内部 Maven 依赖方向，记录 `scope`、`optional`；不要把测试依赖混同生产运行依赖。
4. 读取 Git 实际跟踪的 `src/main/java`，将 package/import 解析成已知 Java 类所属模块，记录跨模块 `from→to` 和示例源码路径。
5. 输出稳定排序的 JSON，供架构评审、CI 与后续真实重构计划使用。聚合器不可吞并 legacy/non-reactor 源码。

使用方式：

```bash
node scripts/architecture/module-topology.mjs
node scripts/architecture/module-topology.mjs --json > /tmp/data-ops-module-topology.json
node --test scripts/architecture/module-topology.test.mjs
```

工具**只读**，不会改 Maven POM、业务源码、Flyway、接口或生成文件写回仓库；JSON 可以在 PR 检查时作为证据收集，不应作为必须每次手工更新的快照。

## 已从真实代码确认的边界

| 代码位置 | 已确认事实 | 对下一批重构的含义 |
| --- | --- | --- |
| `pom.xml` | 根 Maven Reactor 聚合 Framework、BOM、Core、Business、Plugins、Boot、UI、Dist | 不允许用上游目录树直接替换现有 Reactor |
| `data-ops-business/pom.xml` | 多领域子模块独立注册 | 以业务 owner 为边界，不把所有 Service 聚成单模块 |
| `data-ops-dist/pom.xml` | 发布装配模块编译依赖 Boot | Dist → Boot 为合法且必要的单向打包依赖 |
| `scripts/architecture/check-boundaries.mjs` | 已有 Maven cycle、跨域持久化和插件契约守卫 | A1 是 **审计**，不是替换原有 CI Gate |
| `data-ops-boot/.../BusinessDatabaseConfiguration.java` | 统一业务 DataSource、事务、MyBatis 会话工厂的手工装配及兼容别名 | A2 之前必须枚举所有 Bean/Qualifier/MapperScan/Flyway 消费者 |
| `docs/architecture/PROJECT_SCOPE.md` | 既有 Project ID、权限、数据归属、安全恢复约束 | 不迁入上游 Workspace 数据模型 |

## 如何阅读审计结果

- `mavenEdges` 是 **声明的**内部 Reactor 依赖；它不会替代 Maven Effective POM 或 Dependency Tree。
- `javaImportEdges` 是已跟踪源码的 **静态显式 import**；反射、Spring 自动注入、XML Mapper、运行时 SPI 和动态类加载不在此范围。
- `scope: test` 不是生产依赖；`optional: true` 不是模块边界完全解耦的证明。
- `moduleCount` 包括聚合 POM；`businessModuleCount` 只统计业务域下非聚合 POM。
- Import 无法唯一解析时不会凭名称猜归属；需要结合 Maven、Spring 运行装配和真实调用现场核实。

## A1 → A2 验收门槛

下一步要基于当前分支生成的真实 JSON，并配合以下源码逐项核验，才能决定是否重构：

- 每条准备修改的 Maven 边：from/to、scope、消费者、兼容性、依赖替代 API、对应测试；
- Boot：Bean 的名称、别名、类型、Qualifier 与应用上下文实际注入点；
- Persistence：SQL Session/TransactionManager/MapperScan/TypeHandler、多数据库方言与所有 Flyway history；
- 安全与运行：Project Context、RBAC、外部执行状态、恢复/不确定性语义；
- 前端：调用的 API 与页面权限，以及 User Journey 回归路径。

**明确不做：** 不为“迁移到新 Yak”直接复制上游 Service/DTO/DB/页面；不先创建 `data-ops-platform` 或 `data-ops-dao` 空壳；不把全仓格式/代码整洁化处理掺入本架构 PR。

## 测试与证据口径

`module-topology.test.mjs` 以最小独立 Maven Reactor / Java import fixture 校验模块归属、编译及测试依赖、发行包例外、静态 import、缺失子 POM 的 fail-closed 行为。合并与否由仓库 Owner 决定；**当前所有架构相关 PR 只提交，不合并**。
