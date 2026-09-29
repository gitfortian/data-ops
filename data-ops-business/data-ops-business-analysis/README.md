# Yak Ops Analysis

Analysis 模块负责**可复用分析定义**：把一个 Dataset 的字段以维度、指标、过滤、排序和图表语义组合成可被 Dashboard 引用的 Analysis。

Analysis 不拥有 Dataset 数据/Schema/Version，不拥有 Dashboard 布局，也不拥有 Lineage 图真相，更不是 Query Runtime。

## Read First

修改 Analysis 前建议按顺序阅读：

1. [`REQUIREMENTS.md`](./REQUIREMENTS.md)：长期必须保持的业务行为与非目标；
2. [`DOMAIN.md`](./DOMAIN.md)：Analysis Truth Ownership 与核心对象关系；
3. [`ARCHITECTURE.md`](./ARCHITECTURE.md)：package、角色和主要调用链；
4. [`DEPENDENCIES.md`](./DEPENDENCIES.md)：允许的依赖方向和跨模块 corridor；
5. [`REVIEW.md`](./REVIEW.md)：PR 评审与最终一致性检查。

## Architecture at a Glance

```text
Controller
    -> AnalysisService (stable facade)
        -> AnalysisManager / AnalysisReader

AnalysisManager
    -> AnalysisDefinitionNormalizer
        -> AnalysisQueryNormalizer
        -> AnalysisVisualPolicy
        -> AnalysisDatasetGateway
    -> AnalysisRepository
    -> AnalysisChangedEvent

AnalysisChangedEvent -- after commit --> AnalysisLineageRefreshListener
    -> AnalysisLineageSynchronizer
    -> AnalysisLineageGraphGateway
    -> Lineage adapter
```

主要 package：

```text
analysis
├── controller/v1
├── definition
├── domain
├── query
├── visualization
├── reference
├── lineage
├── gateway/dataset
├── gateway/lineage
├── repository/codec
├── dao
└── config
```

仓库根部保留 `AnalysisService`、`AnalysisReferenceService`、`AnalysisDeletionGuard` 作为兼容边界，不把新的业务实现继续堆在根 package。

## Truth Ownership

```text
Analysis
    owns current reusable analytical definition

Dataset
    owns lifecycle / current version / schema / query capability

Dashboard
    owns layout / widget placement / dashboard versions

Lineage
    owns lineage graph

Analysis lineage code
    owns only the derived projection from committed Analysis truth
```

`AnalysisDefinition` 是当前可复用定义，不是 Draft 生命周期；当前没有 `AnalysisVersion`、Publish/Online 状态、Execution Instance 或 Query Result truth。

## Role Vocabulary

| Role | Responsibility |
|---|---|
| `Service` | 对外稳定 compatibility facade |
| `Manager` | create/update/delete 生命周期 |
| `Reader` | 当前 Analysis read side |
| `Normalizer` | 输入标准化与组合 |
| `Policy` | 图表/视觉等业务约束 |
| `Collector` / `Extractor` | 确定性语义提取 |
| `Gateway` | Analysis-owned 跨模块 Port |
| `Adapter` | Dataset/Lineage/持久化边界翻译 |
| `Synchronizer` | 让派生 Lineage 投影收敛 |
| `Repository` | Analysis Domain 持久化 Port |
| `DAO` | MyBatis persistence access |
| `Codec` | `query_spec_json` / `visual_config_json` 边界编解码 |
| `Mapper` | HTTP request/view 转换 |

新增代码不要重新创建 `service/support/helper/utils/common/base` 兜底角色。

## Stable Semantics

- Query 默认 `limit = 500`、`timeoutSeconds = 30`；最大分别为 `1000`、`120`；
- Filter 最多 50 个，Sort 最多 5 个；
- METRIC / PIE / BAR / LINE / TABLE 的维度指标约束保持不变；
- Dataset binding 由 Dataset owner 校验 ONLINE、current version 和 field identity；
- Dashboard 可以通过 `AnalysisDeletionGuard` 阻止删除被引用 Analysis，但不能修改 Analysis truth；
- Analysis mutation commit 后再刷新 Lineage；Lineage 失败只记录，不回滚已提交 Analysis；
- Lineage evidence 继续使用 `ANALYSIS_BINDING`。

## Persistence

当前 Analysis 只维护：

```text
yak_analysis
```

`query_spec_json` 与 `visual_config_json` 是 Analysis 当前定义的持久化表示。Repository 对 Application 暴露 Domain，不暴露 JSON 字符串、PO 或 Mapper。

## Architecture Guards

```text
AnalysisDependencyBoundaryTest
AnalysisLayeringConventionTest
AnalysisCodeStyleConventionTest
AnalysisRoleConventionTest
AnalysisArchitectureDocumentationTest
```

架构需要变化时，应同时修改代码、文档和 executable guard，不单独放宽测试。