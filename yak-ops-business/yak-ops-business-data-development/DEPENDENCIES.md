# Data Development Dependencies

本文件定义 `io.yak.ops.business.development` 的允许依赖方向。它不是 Maven 依赖清单，而是业务 package contract。

## Top-level Direction

```text
controller
   -> node / directory / task / execution / dataset / dataservice / release / standard / editor
   -> lineage preview read facade / model

node        -> domain + repository -> dao
directory   -> domain + repository -> dao
task        -> domain + repository -> dao
            -> lineage (outbox only)
execution   -> domain + task + repository + shared Task Runtime
              └── model = execution read/response projection

dataset     -> domain + repository + Dataset / Task Catalog
dataservice -> domain + repository + adjacent Data Service publication/runtime boundary
release     -> domain + repository + Task Catalog
              └── model = release read projection

lineage     -> domain + repository + frozen SQL parser implementation
            -> shared Lineage analysis/write contracts
            -> ProjectContextScope for persisted background project restoration
editor      -> repository
domain      -> framework-light truth/value types only
```

依赖必须总体向边界流动，不能通过扩大白名单掩盖循环。

## Package Matrix

| From | Allowed module-internal targets |
|---|---|
| `controller` | `node`, `directory`, `task`, `execution`, `dataset`, `dataservice`, `release`, `standard`, `editor`, `lineage` preview facade/model |
| `node` | `domain`, `repository` |
| `directory` | `domain`, `repository` |
| `task` | `domain`, `repository`, `lineage`, compatibility exception corridor |
| `execution` | `domain`, `task`, `repository`; `execution.model` 只能作为 read/response projection |
| `dataset` | `domain`, `repository` |
| `dataservice` | `domain`, `repository`; adjacent Data Service publication/runtime application boundary |
| `release` | `domain`, `repository`; `release.model` 只能作为 read projection |
| `lineage` | `domain`, `repository`, frozen legacy SQL parser implementation; `lineage.model` may expose shared Lineage read types |
| `standard` | `domain`, `repository`, frozen legacy SQL parser implementation; semantic `api` SPI (see Cross-module Corridors) |
| `editor` | `repository` |
| `repository` | `domain`, `dao`, persistence primitives |
| `dao` | MyBatis persistence primitives |
| `domain` | JDK + compatibility serialization annotations |

## Read Model Rule

Read Model 跟随所属业务子系统，但不能反向成为领域 truth owner：

```text
controller -> release.model
release.model -> immutable domain facts (read only)

controller -> execution.model
execution.model -> JDK / SPI response vocabulary

domain -X-> release.model / execution.model
```

新增 `Page / Summary / Detail / View / Response` 类型时，先判断它是否拥有业务不变量；如果只是组合查询结果，默认不能进入 `domain`。

## Cross-module Corridors

Data Development 可以通过以下明确边界访问邻接模块：

```text
Task authoring  -> TaskPluginRegistry / Task Catalog
Execution       -> shared TaskExecutionGateway
Dataset         -> DevelopmentDatasetFacade
Data Service    -> Data Service publication/runtime boundary
Lineage         -> Lineage application boundary / analysis contract / DataSource Catalog
Standard check  -> semantic StandardRecommendApi (api 包 SPI, 只读提示 fail-open, 不阻断保存/发布)
Background work -> ProjectContextScope using persisted project_id
Node metadata   -> Task Catalog metadata projection
```

Data Service Runtime 的 truth 仍由 `yak-ops-business-data-service` 持有。Data Development 只拥有 source-managed publication 的 owner boundary：通用 Data Service 管理 API 不得直接 mutate Data Development 来源的 Runtime projection；上线、更新上线和下线必须经 `dataservice.DevelopmentDataServicePublicationService`。

SQL projection 方向固定为：

```text
lineage.analysis.DevelopmentSqlProjectionLineageAnalyzer
    -> io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer
    -> local frozen SQL parser
```

共享 Lineage 模块不允许反向进入 Data Development parser 实现。

Scheduled / Reconciler / Outbox 工作方向固定为：

```text
persisted project_id
    -> ProjectContextScope
    -> project-scoped Repository / adjacent context IO
```

后台任务不得因为没有 HTTP Header 而退化成 global repository read/write。

新增跨模块依赖前先回答：

```text
Dependency Impact Analysis
- New edge:
- Owner of the target truth:
- Existing corridor or new corridor:
- Can a local gateway/port make the boundary narrower:
- Cycle impact:
- DEPENDENCIES / guard updated: yes/no
```

## Persistence Boundary

Application role 只依赖 Repository contract，JDBC/MyBatis 细节留在 adapter/DAO：

```text
application role -> repository contract -> JDBC/MyBatis adapter -> database
```

Stage 3 已把 Execution history、Editor settings、Lineage Outbox 三个历史 direct-JDBC 边界全部下沉到 Repository adapter。`execution`、`editor`、`lineage` 不再直接 import `JdbcTemplate`，architecture test 会阻止这种依赖重新出现。

Repository contract 可以使用所属业务事实或自己的 persistence record，但不得反向依赖 application service，从而避免 `repository <-> execution/editor/lineage` package cycle。

## Forbidden Shortcuts

- Controller 不直接访问 Repository / DAO。
- Domain 不依赖 Controller、Repository、DAO、Task Runtime、Lineage Service、Spring JDBC、MyBatis，也不依赖 release/execution read model。
- Task / Node / Directory / Execution / Dataset / Data Service publication / Release / Lineage / Editor 不直接进入本模块 DAO。
- Application role 不直接持有 `JdbcTemplate`；只有 Repository adapter 可以拥有 SQL persistence primitive。
- Query/read 行为不得顺手修改 Draft/Revision/Execution truth。
- 不创建 `common/helper/utils/base/service` 作为新功能的默认落点。
- 不通过 `service` legacy island 作为“方便的中转层”形成新依赖。
- Analyzer contract 不携带 Data Development parser、Spring 或持久化类型。
- Data Development 来源的 Data Service Runtime 不允许通过通用管理 API 绕过 authoring-context permission。

## Legacy Corridors

当前剩余 legacy `service` 主要是 SQL Lineage Parser 大实现、`DevelopmentDataServiceNodeService` 大应用入口和两个兼容异常。

`DevelopmentDataServiceNodeSourceProvider` 已迁入 `dataservice`；`DevelopmentDataServiceSqlCompiler` 的核心实现也已迁入 `dataservice`，旧包只保留无业务逻辑的 Spring compatibility shell，等待 Data Service Node 大入口在独立机械迁移中归位。

`DevelopmentSqlProjectionLineageAnalyzer` 已迁入 `lineage.analysis`，不再属于 legacy allowlist。这些剩余项是**固定债务，不是新代码模板**；架构测试会精确限制该目录文件集合，新增文件即失败。
