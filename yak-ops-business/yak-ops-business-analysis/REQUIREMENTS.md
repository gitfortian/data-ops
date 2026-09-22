# Analysis Requirements

本文记录 `yak-ops-business-analysis` 当前必须长期保持的业务 contract。它描述行为，不描述某一次重构过程。

## 1. Scope

Analysis 是一个**可复用分析定义**。一个 Analysis 指向一个 Dataset，并声明：

- 使用哪些 dimension；
- 使用哪些 metric 与 aggregation；
- 使用哪些 filter / sort；
- 使用哪种 chart type；
- chart-local visual config；
- query limit / timeout 语义。

Analysis 可以被 Dashboard 引用。

## 2. Definition Lifecycle

当前只存在“当前定义”生命周期：

```text
create
update
read/list
delete
```

创建和更新必须先完成定义标准化与 Dataset binding 校验，再写入 `yak_analysis`。

删除前必须执行所有 `AnalysisDeletionGuard`。任一 guard 拒绝时不得删除 Analysis。

当前**没有**：

```text
Draft -> Publish
AnalysisVersion
Online / Offline
ExecutionInstance
QueryResult state
```

不要为了代码结构对称而引入不存在的状态机。

## 3. Dataset Binding

Analysis 只保存 Dataset reference，不拥有 Dataset truth。

保存 Analysis 时：

- `datasetId > 0`；
- Dataset 必须满足 Dataset owner 定义的可绑定条件；
- 当前条件包括 ONLINE、存在 current version、引用 fieldId 属于当前 schema；
- Analysis 通过 `AnalysisDatasetGateway` 请求校验，不直接读取 Dataset Repository/DAO。

Dataset 后续生命周期与版本策略仍由 Dataset 模块负责。

## 4. Query Semantics

`AnalysisQuerySpec` 是声明式语义，不是 SQL、执行计划或 Runtime execution。

标准化规则保持：

```text
limit default           = 500
limit max               = 1000
timeoutSeconds default  = 30
timeoutSeconds max      = 120
filters max             = 50
sorts max               = 5
fieldId max length      = 64
single string filter    <= 4000 chars
```

Dimension fieldId 不允许重复。Metric 按 `fieldId + aggregation` 不允许重复。

Sort 必须引用已经存在于 dimensions 或 metrics 中的字段；维度排序不能指定 aggregation；未指定方向时默认 ASC。

SQL 特定语义属于 Dataset Query Runtime，不进入 Analysis semantic operator。

## 5. Chart Binding

必须保持：

- `METRIC`：0 dimension，且恰好 1 metric；
- `PIE`：恰好 1 dimension + 1 metric；
- `BAR` / `LINE`：至少 1 dimension + 1 metric；
- `TABLE`：dimension 与 metric 至少存在一种。

## 6. Visual Configuration

VisualConfig 只描述 Analysis 图表自身展示语义，不描述 Dashboard layout。

未提供 VisualConfig 时保持当前默认：

- PIE 默认显示 legend；
- LINE 默认 smooth；
- BAR / LINE 默认显示 grid/axis 类展示；
- data label 默认关闭。

Dashboard 的位置、尺寸、主题、交互和 Dashboard Version 不进入 Analysis。

## 7. Reference Contract

跨模块只需要确认 Analysis 是否存在时，通过稳定窄接口 `AnalysisReferenceService`。

Dashboard 等下游模块不能为了引用校验直接依赖 Analysis Repository/DAO。

## 8. Lineage Projection

Analysis create/update/delete 成功后发布 Analysis-owned change fact。

Lineage 刷新必须在业务事务 commit 后发生：

```text
Analysis mutation
    -> DB commit
    -> AnalysisLineageRefreshListener
    -> AnalysisLineageSynchronizer
```

Lineage projection 使用独立事务收敛；刷新失败不得把已经提交的 Analysis 业务结果回滚。

必须保持：

- evidence source：`ANALYSIS_BINDING`；
- Analysis Chart asset key：`chart:analysis:{analysisId}`；
- Dataset asset key：`dataset:{datasetId}`；
- DatasetField asset key：`dataset-field:{datasetId}:{fieldId}`；
- Dataset -> Chart：`CONSUMES`；
- DatasetField -> Chart：`CONSUMES`；
- field usage role：DIMENSION / METRIC / FILTER / SORT；
- metric/sort aggregation evidence 可追踪。

Lineage graph 是 Lineage 模块的 truth；Analysis 只维护派生投影输入与收敛逻辑。

## 9. Persistence Compatibility

保持：

- 表：`yak_analysis`；
- 字段：`dataset_id`、`chart_type`、`query_spec_json`、`visual_config_json` 等现有 schema；
- Flyway baseline 不在架构治理中重写；
- Repository 不向 Application 暴露 PO / JSON string / MyBatis 类型。

## 10. HTTP Compatibility

保持 `/api/v1/analyses/**` 路径、现有 request/response 字段和 long-id 字符串序列化语义。

## 11. Non-goals

本模块不拥有：

- Dataset schema/version/query execution；
- SQL compiler/runtime；
- Dashboard layout/version；
- Lineage graph truth；
- Workflow execution；
- Analysis historical version store；
- Analysis execution/result cache state。

新增以上能力前应先形成明确需求与新的 Domain Contract，而不是塞入现有 Manager/Normalizer。