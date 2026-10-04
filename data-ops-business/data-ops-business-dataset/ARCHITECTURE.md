# Dataset Architecture

Console transport identity is adapted in Boot from verified Yak CurrentUser to Servlet Principal.
Role codes are read from the owning RoleService; incomplete identity/roles cannot enter a query.
DatasetController retains its existing Principal-to-DatasetQuerySubject boundary and Security Gateway.
This adds no Dataset-to-IAM persistence or authentication implementation dependency.

本文件描述 Dataset 当前长期架构。需求看 `REQUIREMENTS.md`，领域事实看 `DOMAIN.md`，依赖矩阵看 `DEPENDENCIES.md`，统一规范看 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。

## 1. Principles

1. `Dataset / DatasetVersion / DatasetField` ownership 显式分离；
2. package 表达业务子系统，不恢复通用 `service/`；
3. 三个稳定 `@Service` facade 只负责 Application API；
4. 内部角色使用 Reader / Manager / Publisher / Coordinator / Adapter 等明确名称；
5. 外部模块能力停在 Dataset-owned Gateway/Adapter 或明确 Runtime Adapter；
6. DatasetVersion append-only；
7. Query 和 Lineage 永远消费 exact version snapshot；
8. Repository / DAO 保持 persistence boundary；
9. 文档依赖图必须和 executable guard 一致。

## 2. Package Map

```text
io.yak.ops.business.dataset
├── controller
├── definition
├── publication
├── schema
├── query
│   └── adapter
├── observability
├── development
├── lineage
├── gateway
│   ├── taskcatalog
│   ├── datasource
│   └── lineage
├── repository  DatasetRepository / persistence adapters · DatasetDataSourceReferenceProvider(deletion-guard SPI)
├── dao
├── config
└── root public API / domain values
```

root package 暂时同时承载历史兼容的 public records/enums 和三个稳定 facade。Stage 2 dependency graph 只把明确的业务子 package 当作内部节点；root public contract 由 facade/architecture tests 单独保护。

## 3. Stable Facades

当前只有：

```text
DatasetService
DatasetQueryService
DevelopmentDatasetFacade
```

`DatasetService` 面向 HTTP、Analysis 和 release 等兼容调用者；内部委托 Definition / Publication / Development。

`DatasetQueryService` 面向 Dashboard/Chart 等查询消费者；内部委托 Query Coordinator 和 Observability Reader。

`DevelopmentDatasetFacade` 是 Data Development Dataset Node 的稳定跨模块 API。
Dataset 自有的来源追溯端点也经此 Facade 读取 DevelopmentNode 归属，Controller 不接触 development 内部角色。

Facade 不直接依赖 Repository / DAO / TaskCatalogService / Datasource implementation / LineageService。

## 4. Definition

```text
DatasetReader
DatasetManager
DatasetBindingPolicy
```

- Reader：Dataset identity/current version/schema read side；
- Manager：ONLINE/OFFLINE lifecycle；
- BindingPolicy：Analysis 对当前 ONLINE schema 的绑定校验。

Status 变化通过 `DatasetLineageRefreshPublisher` 请求派生血缘刷新，但 Lineage 不反向进入 Definition Reader。

## 5. Publication

```text
DatasetPublisher
    -> DatasetReader
    -> DatasetTaskCatalogGateway
    -> DatasetSchemaDiscovery
    -> DatasetFieldNormalizer
    -> DatasetVersionWriter
    -> DatasetLineageRefreshPublisher
```

主路径：

```text
validate exact upstream source and discover schema outside the write transaction
 -> lock the Project + TaskAsset publication key
 -> recheck the exact source Revision
 -> freeze the prepared schema and revision
 -> append immutable DatasetVersion
 -> update currentVersionId
 -> after-commit lineage refresh request
```

`yak_dataset_source_publication_lock` 只用于同 Project、同 TaskAsset 的事务串行化，不拥有 Dataset 或 TaskAsset 的业务状态。已有 DatasetVersion append 先锁 Dataset identity 行，再分配 `version_no`，避免并发追加冲突。

`DatasetVersionWriter` 只负责 append version + move pointer，不负责 source validation。

## 6. Schema

```text
DatasetSchemaDiscovery
DatasetFieldNormalizer
DatasetFieldIdentity
DatasetFieldSpec
```

Schema Discovery 通过 Dataset-owned TaskCatalog/SQL gateway 获取来源 evidence。

FieldNormalizer 负责稳定 field contract；FieldIdentity 集中 deterministic fieldId 规则。

Preview 不写持久化 field identity。

`DatasetFieldSpec` 继续作为 Schema、Publication、Development 与稳定 Facade 共用的输入契约。草稿持久化通过 Repository 自有的 `DraftField` 值进入 Repository，保持依赖图无环且不改变 Facade 类型。

## 7. Query Runtime

```text
DatasetQueryService @Service
 -> DatasetQueryCoordinator
      -> DatasetRepository
      -> DatasetSourceQueryRegistry
      -> DatasetQuerySecurityGate
           -> DatasetProjectionAnalyzerGateway
           -> DatasetSecurityGateway
      -> exact source adapter
      -> DatasetQueryPerformanceRecorder
```

查询执行前按 SQL 投影血缘构造物理列自然键并执行 Security 决策。结果返回前执行所需脱敏;成功消费后记录实际访问结果。血缘不可用或映射不完整时拒绝查询。

Adapters：

```text
QUERY_REVISION -> QueryRevisionDatasetSourceAdapter
SQL_QUERY      -> SqlQueryDatasetSourceAdapter
```

Query adapters 本身就是 Runtime boundary，可以直接调用 `io.yak.ops.core.execution.sql.*`；业务 Coordinator 不直接依赖 Core SQL Runtime。
`DatasetQueryResult` 复用 Core 中 JDBC 中立的 `SqlExecutionColumn` 值类型，让查询消费方共用稳定的列元数据形状；它不暴露执行运行时。
Source Adapter 将 Core SQL policy violation 转成 Dataset-owned `DatasetQueryRejectedException`，Coordinator 只按 Dataset 查询状态分类，并保留原始异常类型用于诊断。

每次 Query attempt 在进入业务校验前生成 `queryId`，并最终形成以下一种终态：

```text
SUCCESS / REJECTED / FAILED / TIMEOUT
```

失败会保留可定位的 `failureStage`，但原业务异常仍按原语义抛给调用者。

## 8. Observability

```text
DatasetQueryPerformanceRecorder
 -> privacy-safe SQL evidence
 -> DatasetQueryPerformanceStore
 -> DatasetQueryPerformanceStoreAdapter
 -> DatasetDao
 -> DatasetQueryPerformanceMapper / PO / MyBatis

DatasetQueryPerformanceReader
 -> persisted cross-instance evidence
 + bounded local fallback
```

规则：

- 正常情况下 Query trace 持久化，应用重启或切换实例后仍可查询；
- 持久化异常时退化到最多 500 条的 process-local fallback；
- Observability 写入、读取或清理失败不能反向改变 Dataset Query 业务结果；
- Project Context 严格隔离：有项目只读该项目，无项目只读 `project_id IS NULL`；
- SQL 仅保存去注释、去字面量后的 preview 和 SHA-256 fingerprint，不持久化原始过滤值；
- 默认保留 7 天，清理周期和批量大小通过 `yak.dataset.query-observability.*` 调整。

## 9. Development

```text
DevelopmentDatasetFacade @Service
 -> DevelopmentDatasetManager
      -> DatasetReader
      -> DatasetPublisher
      -> DatasetSchemaDiscovery
      -> DatasetVersionWriter
      -> DatasetRepository (draft load/save)
      -> DatasetLineageRefreshPublisher
```

Manager 拥有 DevelopmentNode -> stable Dataset identity 的 Dataset-side lifecycle。

### 9a. Draft Lifecycle

```text
Editor save
 -> Manager.saveSqlQuery
      -> Repository.updateDraft (dataSourceId, SQL)
      -> Repository.saveDraftFields (fields)
      -> NO version creation

Editor publish
 -> Manager.publishVersion
      -> Repository.loadDraftSource (dataSourceId, SQL)
      -> Repository.loadDraftFields
      -> FieldNormalizer.normalize
      -> VersionWriter.append*SqlQuery
      -> LineageRefreshPublisher.request
```

Facade 的 `toNodeDataset` 从 Manager 提供的 `DraftContext` 填充 draft 字段，不直接依赖 Repository。

### 9b. Draft Persistence

Draft state 持久化层：

```text
DatasetRepository.updateDraft / loadDraftSource
 -> DatasetRepositoryAdapter
 -> DatasetDao

DatasetRepository.saveDraftFields / loadDraftFields
 -> DatasetRepositoryAdapter
 -> DatasetDao
 -> yak_dataset_draft_field
```

## 10. Lineage

```text
DatasetLineageRefreshPublisher
    -> Spring event

DatasetLineageRefreshListener
    -> DatasetLineageSnapshotReader
    -> DatasetLineageTransactionRunner
         -> DatasetLineageSynchronizer
              -> DatasetLineageSourceResolver
              -> DatasetProjectionAnalyzerGateway
              -> DatasetLineageGraphGateway
```

`DatasetLineageSnapshotReader` 直接读取 Repository，是为了保持 Lineage derived projection 在 Definition 下游，避免 `definition <-> lineage` package cycle。

Lineage 是 AFTER_COMMIT + REQUIRES_NEW 的 best-effort projection。

## 11. Gateways

Task Catalog：

```text
DatasetTaskCatalogGateway
 <- TaskCatalogDatasetAdapter
 <- TaskCatalogService/domain
```

Datasource Schema SQL：

```text
DatasetSchemaSqlGateway
 <- DataSourceSchemaSqlAdapter
 <- Datasource execution SPI
```

Datasource Catalog（optional lineage evidence）：

```text
DatasetCatalogGateway
 <- DataSourceDatasetCatalogAdapter
 <- DataSourceCatalogReader
```

Lineage：

```text
DatasetProjectionAnalyzerGateway
 <- LineageProjectionAnalyzerAdapter
 <- SqlProjectionLineageAnalyzer

DatasetLineageGraphGateway
 <- LineageGraphDatasetAdapter
 <- LineageService / LineageMaintenanceService
```

## 12. Persistence

Dataset business truth：

```text
Application role
 -> DatasetRepository
 -> DatasetRepositoryAdapter
 -> DatasetDao
 -> mapper / PO / MyBatis
```

Query observability read model：

```text
Observability role
 -> DatasetQueryPerformanceStore
 -> DatasetQueryPerformanceStoreAdapter
 -> DatasetDao
 -> DatasetQueryPerformanceMapper / PO / MyBatis
```

Repository contract 只暴露 Dataset-owned domain/value，不暴露 Controller DTO/VO、PO、Mapper 或 MyBatis 类型。

## 13. Config

`DatasetPersistenceConfiguration` 只负责 Dataset Flyway 和共享业务数据库 wiring。

Dataset Flyway 按增量方式维护，当前依次为 `V1__baseline_dataset.sql`、`V2__dataset_source_publication_lock.sql` 与 `V3__dataset_query_subject_attribution.sql`。

共享业务数据库、会话工厂与事务管理器由 Boot 装配；本模块只声明 Mapper/Flyway 和既有持久化条件，不导入 sibling 的数据库配置。

## 14. Change Rule

新增依赖前依次回答：

1. 属于哪个 Dataset 子系统？
2. 它拥有 truth 还是只读取/投影？
3. 是否已有 Reader/Publisher/Gateway/Repository 可表达？
4. 新 import 是否符合 `DEPENDENCIES.md`？
5. 是否会让 exact DatasetVersion 漂移到 current upstream state？
6. 是否会形成 package cycle？
7. 哪个行为测试和架构测试保护它？

答不清楚时不要创建新的 Helper/Common/ServiceImpl。
