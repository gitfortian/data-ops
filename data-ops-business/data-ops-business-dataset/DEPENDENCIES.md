# Dataset Dependencies

本文件定义 Dataset production package 的允许依赖方向、窄 corridor 与外部模块入口。原则：**显式、窄、无环**。

## 1. Top-level Package Matrix

root public contract 不作为内部图节点；以下是明确业务子 package：

| Source | Allowed Dataset packages |
| --- | --- |
| `controller` | none |
| `definition` | `lineage`, `repository` |
| `development` | `definition`, `gateway`, `lineage`, `publication`, `repository`, `schema` |
| `publication` | `definition`, `gateway`, `lineage`, `repository`, `schema` |
| `schema` | `gateway`, `repository` |
| `query` | `gateway`, `observability`, `repository` |
| `observability` | `repository` |
| `lineage` | `gateway`, `repository` |
| `gateway` | none |
| `repository` | `dao` |
| `dao` | none |
| `config` | none |

同一 top-level package 内部可协作。声明图和实际 import 图都必须无环。

## 2. Root Public Contract

root package 中三个稳定 facade：

```text
DatasetService
DatasetQueryService
DevelopmentDatasetFacade
```

以及现有 public Dataset records/enums 是兼容 API，不进入内部 cycle graph。

Facade 可以依赖内部 Application role，但内部 role 不应为了方便回调 facade。

## 3. Definition -> Lineage

Definition 进入 Lineage 只允许：

```text
DatasetManager
 -> DatasetLineageRefreshPublisher
```

Lineage 不允许反向 import `definition.*`。

Lineage 读取 Dataset current snapshot 通过：

```text
DatasetLineageSnapshotReader
 -> DatasetRepository
```

这样避免 `definition <-> lineage` cycle。

## 4. Publication Corridors

Publication 可以进入：

```text
DatasetReader
DatasetTaskCatalogGateway
DatasetSchemaDiscovery / DatasetFieldNormalizer
DatasetRepository
DatasetLineageRefreshPublisher
```

Publication 不直接依赖 TaskCatalogService、Datasource execution SPI、Lineage Service/Analyzer。

## 5. Development Corridors

`DevelopmentDatasetManager` 是组合型 Dataset-side role，可以复用 Definition / Publication / Schema / Repository / Lineage refresh。

Development 对 Repository 的窄 corridor：

```text
DevelopmentDatasetManager
 -> DatasetRepository.findDatasetByDevelopmentNodeId
 -> DatasetRepository.insertDevelopmentNodeDataset
 -> DatasetRepository.updateDraft / loadDraftSource
 -> DatasetRepository.saveDraftFields / loadDraftFields
```

Data Development 模块本身只能通过 `DevelopmentDatasetFacade` 进入 Dataset；不能跨模块直接依赖 `development.DatasetManager`、Repository 或 DAO。

## 6. Query Corridors

Query Coordinator：

```text
DatasetQueryCoordinator
 -> DatasetRepository
 -> DatasetSourceQueryRegistry
 -> DatasetQueryPerformanceRecorder
```

Query 的 TaskCatalog 访问只允许 `QueryRevisionDatasetSourceAdapter -> DatasetTaskCatalogGateway`。

Source adapters 可直接依赖 `io.yak.ops.core.execution.sql.*`；它们本身就是 Dataset Query Runtime adapter 边界。Coordinator / Registry / Compiler 不允许直接依赖 Core SQL Runtime。

Observability 持久化只允许通过一个窄 Repository port：

```text
DatasetQueryPerformanceRecorder / DatasetQueryPerformanceReader
 -> DatasetQueryPerformanceStore
 -> DatasetQueryPerformanceStoreAdapter
 -> DatasetDao
 -> DatasetQueryPerformanceMapper / PO
```

`observability` 不直接 import DAO / Mapper / PO；Repository Adapter 也不直接使用 MyBatis，继续遵守 `repository -> dao -> mapper/PO` 的底层方向。

## 7. Schema Corridors

Schema 只通过 Dataset-owned Gateway 使用外部来源：

```text
DatasetSchemaDiscovery
 -> DatasetTaskCatalogGateway
 -> DatasetSchemaSqlGateway
```

`DatasetFieldNormalizer` 只可向下依赖 DatasetRepository 和 FieldIdentity。

## 8. Lineage Corridors

Lineage projection 只通过：

```text
DatasetLineageSourceResolver -> DatasetTaskCatalogGateway
DatasetLineageSynchronizer   -> DatasetProjectionAnalyzerGateway
DatasetLineageSynchronizer   -> DatasetLineageGraphGateway
DatasetLineageSnapshotReader -> DatasetRepository
```

Lineage 不直接依赖 TaskCatalogService、DataSourceCatalogReader、共享 SQL Analyzer contract、Lineage application Service 或 LineageMaintenanceService。

## 9. External Module Boundaries

外部实现只允许从以下文件进入：

### Task Catalog

```text
gateway/taskcatalog/TaskCatalogDatasetAdapter.java
 -> io.yak.ops.business.taskcatalog.*
```

### Datasource Catalog

```text
gateway/datasource/DataSourceDatasetCatalogAdapter.java
 -> io.yak.ops.business.datasource.catalog.DataSourceCatalogReader
```

### Datasource SQL SPI

```text
gateway/datasource/DataSourceSchemaSqlAdapter.java
 -> io.yak.ops.spi.datasource.execution.*
```

### Shared Lineage

```text
gateway/lineage/LineageProjectionAnalyzerAdapter.java
 -> io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer

gateway/lineage/LineageGraphDatasetAdapter.java
 -> io.yak.ops.business.lineage.domain.*
 -> io.yak.ops.business.lineage.service.*
```

### Core SQL Runtime

```text
query/adapter/QueryRevisionDatasetSourceAdapter.java
query/adapter/SqlQueryDatasetSourceAdapter.java
 -> io.yak.ops.core.execution.sql.*
```

### Infrastructure Config

```text
config/DatasetPersistenceConfiguration.java
dao/impl/DatasetDaoImpl.java
 -> io.yak.ops.business.datasource.config.*
```

前者负责共享 business database / Flyway wiring；后者沿用既有 datasource-enabled DAO 条件。这个 corridor 仅限基础设施开关，不是 Dataset 业务能力调用。

## 10. Persistence Boundary

Dataset aggregate：

```text
Business role
 -> DatasetRepository
 -> DatasetRepositoryAdapter
 -> DatasetDao
 -> PO / Mapper / MyBatis
```

Query observability read model：

```text
Observability role
 -> DatasetQueryPerformanceStore
 -> DatasetQueryPerformanceStoreAdapter
 -> DatasetDao
 -> DatasetQueryPerformanceMapper / PO / MyBatis
```

Repository contract 不暴露：

- Controller DTO/VO；
- DAO PO/Mapper；
- MyBatis 类型；
- 外部 TaskCatalog/Datasource/Lineage 对象；
- stable facade 类型。

## 11. No Cycles

不允许通过以下方式掩盖 cycle：

- `@Lazy`；
- ApplicationContext lookup；
- 静态 Service Locator；
- 把接口随意搬到 `common/helper/base`；
- 直接扩大 dependency-test 白名单。

发现新环先明确能力 owner，再建立窄 Reader/Gateway/Publisher corridor。

## 12. Adding a Dependency

新增不在矩阵中的 import 时：

1. 先判断类是否放错 package；
2. 判断现有 facade/Reader/Gateway/Repository 是否可表达；
3. 判断是否缺 Dataset-owned narrow contract；
4. 只有架构真的变化时，才同步更新本文件和 executable dependency test。
