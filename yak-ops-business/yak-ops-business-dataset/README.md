# Yak Ops Dataset

Dataset 是 Yak Ops 的稳定数据消费契约层：把上游确定的数据来源冻结成不可变 `DatasetVersion`，向 Analysis / Dashboard / Chart / Data Development 提供稳定 schema、查询运行时和派生血缘。

核心关系：

```text
Upstream source snapshot
        -> DatasetVersion (immutable)
        -> Dataset.currentVersionId
        -> Dataset Query / downstream binding
```

## Read First

本目录只维护**当前有效 contract**。历史 Stage / Wave / 重构过程通过 Git / PR 追溯。

建议按顺序阅读：

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | Dataset 必须保持哪些行为 |
| [`DOMAIN.md`](./DOMAIN.md) | Dataset / Version / Field / Query / Lineage 的真相边界 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 代码角色和调用主路径 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 允许依赖谁、外部模块从哪里进入 |
| [`../../CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 仓库统一工程规范 |
| [`REVIEW.md`](./REVIEW.md) | Dataset PR 如何 Review |

## Stable Application APIs

Dataset 目前保留三个稳定 `@Service` facade：

```text
DatasetService
DatasetQueryService
DevelopmentDatasetFacade
```

它们面向 HTTP 或跨模块调用者；内部业务角色使用 Manager / Reader / Publisher / Coordinator / Adapter 等明确职责，不建立通用 `service/` 大桶。

## Package Shape

```text
dataset/
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
├── repository
├── dao
├── config
└── root public contract/domain values
```

## Truth Ownership

```text
Dataset                  = stable business identity
DatasetVersion           = immutable source snapshot
Dataset.currentVersionId = selected-version pointer
DatasetField             = one version's schema contract
Dataset Query            = execution against an exact version
Query Performance        = observability evidence
Dataset Lineage          = derived projection
```

硬规则：

```text
Dataset != DatasetVersion != DatasetField
current upstream source != existing DatasetVersion snapshot
preview schema != persisted version schema
lineage / performance != Dataset business truth
```

## Main Boundaries

Publication：

```text
DatasetPublisher
 -> exact upstream snapshot
 -> schema discovery/normalization
 -> append immutable DatasetVersion
 -> move currentVersionId
 -> request derived lineage refresh
```

Query：

```text
DatasetQueryService
 -> DatasetQueryCoordinator
 -> exact DatasetVersion
 -> DatasetSourceQueryRegistry
 -> source adapter
 -> DatasetQueryResult
```

每次 Query attempt 都会生成唯一 `queryId`，并形成 `SUCCESS / REJECTED / FAILED / TIMEOUT` 之一的诊断终态。正常 datasource 模式下诊断 evidence 持久化并可跨实例读取；持久化异常时仅退化到 bounded local fallback，不改变原查询结果或原业务异常。SQL 只保存去字面量后的 preview 与 SHA-256 shape fingerprint。

诊断入口：

```text
GET /api/v1/datasets/query-performance
```

支持 datasetId / queryId / terminal status / 最小总耗时过滤。

Data Development：

```text
Data Development
 -> DevelopmentDatasetFacade
 -> DevelopmentDatasetManager
 -> stable Dataset identity + immutable versions
```

Lineage：

```text
Dataset commit
 -> AFTER_COMMIT event
 -> REQUIRES_NEW lineage projection
 -> exact current DatasetVersion source
```

完整依赖矩阵和外部 corridor 见 `DEPENDENCIES.md`。