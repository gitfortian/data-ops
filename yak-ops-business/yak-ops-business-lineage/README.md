# Lineage

Lineage 是 Yak Ops 的统一元数据血缘模块，负责稳定资产、带来源证据的有向关系，以及有边界的上下游图查询。

## Read First

本目录只维护当前有效 contract，历史演进以 Git / Pull Request 为准。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块能力和非目标 |
| [`DOMAIN.md`](./DOMAIN.md) | Asset / Relation / Evidence / Graph 语义 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 角色 package 与边界 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package、跨模块与 Maven 依赖方向 |
| [`REVIEW.md`](./REVIEW.md) | 评审和拒绝标准 |
| [`CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 工程规范 |

## Current Contract

```text
HTTP / neighboring module
          ↓
   role-owned facade
          ↓
   role component(s)
          ↓
     Repository
          ↓
         DAO
```

```text
query/LineageQueryService
  ├── LineageAssetReader
  ├── LineageGraphReader
  └── LineageOverviewReader

registration/LineageRegistrationService
  ├── LineageAssetRegistrar
  ├── LineageRelationRegistrar
  └── LineageRegistrationDraftFactory

maintenance/LineageMaintenanceService
  ├── LineageEvidenceReplacementCoordinator
  └── LineageRevisionGuard
```

`@Service` 只用于三个稳定 facade；Reader、Registrar、Coordinator、Guard、DraftFactory 使用专业角色表达职责。

## Active Package Shape

```text
io.yak.ops.business.lineage
├── analysis
├── config
├── controller
├── dao
├── domain
├── maintenance
├── query
├── registration
└── repository
```

根包不承载 production Java 类型。`service / common / helper / utils / base` 不允许成为新的业务大桶。

## Public API

邻接业务模块只允许依赖：

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

上述 facade/Analyzer 的公开嵌套类型跟随所属类型根。Controller、Config、Repository、DAO、PO 和 Domain Draft 属于模块内部实现。

## Core Model

```text
LineageAsset
    │ upstream source -> downstream target
    ▼
LineageRelation
    │
    └── evidence(sourceType + sourceId + version + observedAt)
```

关系方向始终是上游资产指向下游资产，角色拆分不改变领域事实。

## Extension Protocol

当前没有活动 Collector。真实 Flink、Spark、Hadoop 来源出现时，先定义 evidence ownership、重放/replacement 语义和 SDK adapter 边界，再新增角色实现；平台接入复用统一 Domain 与 registration/maintenance 边界，不复制平行业务层。

## Architecture Guards

```text
LineageArchitectureTest
LineageDependencyBoundaryTest
LineageMavenDependencyBoundaryTest
LineagePublicApiBoundaryTest
LineageCodeStyleConventionTest
LineageDocumentationContractTest
```

新 package、新公开类型或新依赖必须在同一个 PR 中同步修改代码、文档与 guard。
