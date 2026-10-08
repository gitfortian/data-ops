# Lineage Dependencies

本文定义 Lineage 当前有效的 package、跨模块与 Maven 依赖方向。

## Active Dependency Graph

```text
controller ──→ query ───────────→ repository ──→ dao ──→ config
     │          └───────────────→ domain
     └──────→ registration ─────→ repository / domain

maintenance ────────────────────→ repository
analysis ───────────────────────→ domain
repository ─────────────────────→ domain
```

| Source | May depend on |
| --- | --- |
| `controller` | `query`, `registration`, `domain`, transport-local dto/vo/converter |
| `query` | `repository`, `domain` |
| `registration` | `repository`, `domain` |
| `maintenance` | `repository` |
| `analysis` | `domain` |
| `repository` | `dao`, `domain`, repository-local support |
| `dao` | `config`, DAO-local mapper/model/support |
| `config` | declared external persistence configuration only |
| `domain` | no Lineage application/infrastructure package |

实际 top-level package 必须与矩阵精确一致，声明图与 import 图均保持无环。

## Public API Corridors

```text
analysis.sql.SqlProjectionLineageAnalyzer

domain.LineageAsset
domain.LineageAssetType
domain.LineageDirection
domain.LineageGraph
domain.LineageRelation
domain.LineageRelationType

query.LineageQueryService
registration.LineageRegistrationService
maintenance.LineageMaintenanceService
```

跨模块禁止 import Controller、Config、Repository、DAO、PO、`LineageAssetDraft`、`LineageRelationDraft`。

当前真实 corridor：

```text
Data Development lineage/analysis -> SqlProjectionLineageAnalyzer
Dataset gateway/lineage            -> Analyzer + selected Domain + role facade
Analysis gateway/lineage           -> selected Domain + role facade
Dashboard gateway/lineage          -> selected Domain + role facade
```

## Role Corridors

```text
query.LineageQueryService
  -> LineageAssetReader / LineageGraphReader
  -> LineageRepository

registration.LineageRegistrationService
  -> LineageAssetRegistrar / LineageRelationRegistrar
  -> LineageRegistrationDraftFactory
  -> LineageRepository

maintenance.LineageMaintenanceService
  -> LineageEvidenceReplacementCoordinator / LineageRevisionGuard
  -> LineageRepository
```

事务留在三个 facade；内部组件不自行定义新的事务边界。

## SQL Analysis Corridor

`analysis.sql.SqlProjectionLineageAnalyzer` 保持 source-neutral。Data Development 持有具体 parser 实现，Dataset 通过自身 Gateway Adapter 使用 contract；Lineage 不反向依赖 Data Development parser。

## Persistence Corridor

```text
query / registration / maintenance
    -> LineageRepository
    -X-> DAO / Mapper / PO / Config

repository -> dao / domain
dao -> config
```

Lineage 唯一允许的外部 business-module import 是 Datasource 配置 corridor，并且只存在于 `config/ConditionalOnLineagePersistence` 和 `config/LineagePersistenceConfiguration`。

## Maven Boundary

保持单一 `data-ops-business-lineage` Maven module。本模块只持有自身编译/API 所需依赖；数据库 driver、Flyway vendor、JSqlParser runtime 和 Swagger UI 由实际 runtime owner 提供。只有真实 SDK 隔离、依赖冲突、可选装载或独立发布需求出现时，才拆 Maven artifact。

## Extension Corridor

当前没有 `collector` 顶层 package。首个真实平台 Collector 必须在同一 PR 同时定义 package、dependency edge、evidence ownership、SDK boundary、行为测试和文档，不能先扩白名单再等待实现。

## Root Package Rule

根包 production type 永久禁止。公共 contract 必须归属 `analysis`、`domain`、`query`、`registration` 或 `maintenance` 的明确角色路径。

## Governance

- `LineageDependencyBoundaryTest`：精确 package 集合与依赖图；
- `LineageMavenDependencyBoundaryTest`：直接依赖和 runtime owner；
- `LineagePublicApiBoundaryTest`：跨模块类型根和签名纯度；
- `LineageCodeStyleConventionTest`：角色位置；
- `LineageDocumentationContractTest`：文档 contract。

## F-035 下游关系计数

原 query.LineageQueryService 增加 downstreamRelationCount：经 LineageGraphReader 校验当前项目可见根，再由原 Repository/DAO 按当前项目及 sourceAssetId 执行 COUNT；缺少项目拒绝。返回一跳有向边条数，不去重消费者，不含全图/表达式，不推断 observed usage。Asset USAGE 是实际调用方；无新 package、依赖边或事务 owner。
