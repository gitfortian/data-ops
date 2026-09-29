# Data Service Dependency Contract

> 本文件定义 `data-ops-business-data-service` 生产代码的**允许依赖方向**。对应规则由 `src/test/.../architecture` 下 executable guard 校验。

## 1. 原则

1. 顶层 package graph 必须无环。
2. Domain 不依赖 Spring、MyBatis、HTTP、Repository、Runtime 或上游业务模块。
3. Mapper/PO/JdbcTemplate 只能存在于 `dao` / `repository` persistence corridor。
4. Controller 不直接访问 Repository/DAO/PO。
5. Publication/Management/Execution/Access/Runtime/Observability 业务角色依赖 Repository port，不依赖持久化实现。
6. `publication.source` 是跨模块 Source Provider 的 JDK-only extension contract。
7. Data Service 不反向依赖 Data Development implementation。
8. SQL 物理执行只走 `data-ops-core` `SqlExecutionRuntime`。
9. Project-aware management 只通过 `data-ops-core` `CurrentProject` 获取受信 Project identity。
10. Global read / coordination 必须是显式窄 corridor，不能把 management repository 退化成 global。
11. 不通过 `service/common/helper/utils/util/base` 等模糊 package 绕过依赖矩阵。

## 2. 顶层依赖图

```text
controller
   +--> publication / management / documentation / execution / observability / query / runtime / access

publication --> documentation / execution / management / query
management  --> access / domain / query / repository / runtime
documentation --> domain / execution / query / repository
execution   --> access / domain / query / repository / runtime
observability --> domain / query / repository
access      --> domain / query / repository
runtime     --> domain / query / repository
query       --> domain / repository
repository  --> dao / domain
```

`repository` 还可以依赖基础设施 contract，例如 `CurrentProject` 与 DataSource/transaction support；其他业务 package 不直接依赖 ORM/SQL persistence type。

## 3. Allowed Top-level Matrix

| Source package | Allowed Data Service target packages |
| --- | --- |
| `controller` | `access`, `documentation`, `domain`, `execution`, `management`, `observability`, `publication`, `query`, `runtime` |
| `publication` | `documentation`, `domain`, `execution`, `management`, `query` |
| `management` | `access`, `domain`, `query`, `repository`, `runtime` |
| `documentation` | `domain`, `execution`, `query`, `repository` |
| `execution` | `access`, `domain`, `query`, `repository`, `runtime` |
| `observability` | `domain`, `query`, `repository` |
| `access` | `domain`, `query`, `repository` |
| `runtime` | `domain`, `query`, `repository` |
| `query` | `domain`, `repository` |
| `repository` | `dao`, `domain` |
| `dao` | none |
| `config` | none |
| `domain` | none |

## 4. Query / Runtime cycle avoidance

`query.DataServiceParameterNameReader` 是 Query 需要的窄 port，`execution.DataServiceSqlCompiler` 提供实现，避免 `query -> execution -> query`。

`DataServiceQueryResponse` 位于 Domain，避免 `execution -> runtime -> execution`。

## 5. Persistence / Project corridor

普通 Management persistence：

```text
Business Role
  -> Repository port
  -> RepositoryAdapter
  -> CurrentProject predicate
  -> Mapper/PO or JdbcTemplate
```

`findById/findByPath/findBySource/findAll/save/delete` 必须绑定 CurrentProject。

两个平台级 global read 是显式例外：

```text
Public Invocation
  -> DataServiceReader.requireByPath
  -> DataServiceRepository.findByRuntimePath

Home Cockpit aggregate
  -> DataServiceReader.count
  -> DataServiceRepository.count
```

`count` 只返回数量，不暴露 API identity/path/SQL/source；不能被 Management 页面当成 global catalog corridor。

## 6. Stage 3 cluster coordination corridor

多实例共享状态仍走业务 port，不让 Access/Runtime/Observability 直接持有 JDBC：

```text
access.DataServiceRateLimiter
  -> DataServiceRateLimitRepository
  -> DataServiceRateLimitRepositoryAdapter
  -> MySQL CAS window

runtime.DataServiceRuntimePolicyManager
  -> DataServiceRuntimeMetricsRepository
  -> DataServiceRuntimeMetricsRepositoryAdapter
  -> raw audit + hourly rollup

observability.DataServiceObservabilityMaintenance
  -> DataServiceObservabilityMaintenanceRepository
  -> Adapter / TransactionTemplate
  -> rollup + raw delete
```

Repository Adapter 可以使用 `JdbcTemplate` / `TransactionTemplate`，因为 CAS、union aggregation、`INSERT ... SELECT` rollup 属于明确 persistence responsibility。

禁止把这些基础设施 type 上移到：

```text
controller
publication
management
execution
runtime
access
documentation
observability
domain
```

## 7. Source Provider Corridor

跨模块只暴露：

```text
io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider
```

允许：`Data Development -> DataServiceSourceProvider`、`MDM -> DataServiceSourceProvider`
（R5 分发复用：`MdmDataServiceSourceProvider` 以 `sourceType=MDM_DISTRIBUTION`、
`sourceRef=distributionId` 暴露发布来源，并只经 `DataServicePublisher` /
`DataServicePublicationReader` 做发布与状态反查）。

禁止：Data Service 反向依赖 Data Development implementation；Provider 反向依赖 Data Service repository/manager/runtime。

Source-managed definition 的 owner-context permission 由上游 authoring context 负责，Data Service Project/RBAC 不能绕过。

## 8. Datasource / Core Corridor

允许的基础跨模块依赖：

```text
io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled
io.yak.ops.business.datasource.api.DataSourceReferenceProvider
io.yak.ops.core.execution.sql.*
io.yak.ops.core.project.*
```

`DataSourceReferenceProvider` 只允许由 `repository` package 内的适配器实现；该适配器通过本模块 DAO 检查 Data Service 引用。物理 SQL 通过 core execution contract；Project type 只承担受信 Management scope。

禁止 Data Service 生产代码依赖 Datasource DAO/Repository/Gateway implementation 或 Data Development implementation。

## 9. HTTP Boundary

Management Controller 可以使用 MVC、`Result`、`@ProjectScope(PROJECT_REQUIRED)`、action-specific permission、capability-owned input/view。

`DataServiceInvocationController` 是 Public Invocation Controller：禁止添加 Yak Project Scope / Console RBAC。

Controller 不应构造 MyBatis Wrapper、访问 Mapper/PO/Repository、编译 SQL、实现 Key hash/rate-limit/circuit/rollup 规则。

## 10. Domain Boundary

Domain 只表达业务事实：

```text
DataServiceDefinition(projectId, runtimeGeneration)
DataServiceSettings
PublishedRuntimeSnapshot
SourceReference
RuntimePolicy
DataServiceQueryResponse
DataServiceApiKey
DataServiceDocumentation
InvocationRecord(projectId snapshot)
```

Domain 不依赖 Spring/MyBatis/Controller/Repository/DAO/Publication/Execution/Runtime implementation。

## 11. No Service Facade by Default

当前没有通用 `DataServiceService` facade。新增 Application facade 必须先证明跨 capability 稳定 API 的必要性，并同步更新 Architecture/Dependencies/Guard。

## 12. Dependency Change Protocol

```text
Dependency Impact
- source package:
- target package:
- why current owner cannot handle it:
- cycle check:
- persistence / source / datasource / project / cluster-coordination impact:
- DEPENDENCIES.md updated: yes/no
```

Guard 失败时优先检查职责是否放错，不默认扩大 allowlist。
