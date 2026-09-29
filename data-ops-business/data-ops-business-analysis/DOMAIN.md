# Analysis Domain

## 1. Core Model

```text
AnalysisAsset
    = identity
    + current AnalysisDefinition
    + create/update time

AnalysisDefinition
    = name / description
    + datasetId
    + chartType
    + AnalysisQuerySpec
    + AnalysisVisualConfig
```

`AnalysisAsset` 表示已经持久化的当前 reusable Analysis；`AnalysisDefinition` 表示标准化后准备持久化的当前定义。

## 2. Truth Ownership

| Truth | Owner |
|---|---|
| Analysis identity / current definition | Analysis |
| Analysis name / description | Analysis |
| Dataset reference chosen by Analysis | Analysis |
| Dataset lifecycle / current version / fields | Dataset |
| Query semantic bindings | Analysis |
| Physical SQL/query execution | Dataset Query capability |
| Chart type / chart-local visual config | Analysis |
| Dashboard layout / widget placement / Dashboard version | Dashboard |
| Lineage graph / asset/relation persistence | Lineage |
| Analysis -> Lineage projection input | Analysis |

只有一个 owner 的 truth 才能稳定演进。Adapter、Controller、Dashboard、Lineage projection 都不能反向成为 Analysis Definition owner。

## 3. Definition Is Not Draft Lifecycle

当前领域关系是：

```text
AnalysisDefinition
    -> persist/update
    -> current AnalysisAsset
```

不是：

```text
Draft -> Publish -> Version
```

历史名字 `AnalysisDraft` 已被移除，就是为了避免错误暗示。

如果未来需要历史版本，需要单独设计 `AnalysisVersion` 的 identity、immutable snapshot、发布/切换和引用冻结语义，不能复用当前 `AnalysisDefinition` 假装实现。

## 4. Query Semantics

`AnalysisQuerySpec` 描述 Analysis 如何消费 Dataset：

```text
dimensions
metrics(fieldId, aggregation)
filters(fieldId, operator, value)
sorts(fieldId, aggregation, direction)
limit
timeoutSeconds
```

它不是 SQL，也不是 execution plan。

Query semantic value object 位于 `query`，Query normalizer 负责 canonical form，Chart binding policy 负责 chart cardinality constraint。

## 5. Visualization

`AnalysisChartType` 与 `AnalysisVisualConfig` 属于 reusable Analysis definition。

它们只描述 chart-local presentation；Dashboard theme、布局、坐标、widget size 与交互不属于 Analysis。

## 6. Dataset Binding

Analysis 只持有 `datasetId` 和引用的 fieldId。

```text
AnalysisDefinitionNormalizer
    -> AnalysisDatasetGateway
    -> Dataset owner
```

Analysis 不复制 Dataset Status、Version、Field metadata 作为自己的 truth。

Dataset binding 校验是 command-time invariant，不意味着 Analysis 获得 Dataset lifecycle ownership。

## 7. Reference And Deletion

`AnalysisReferenceService` 是给下游模块的窄 read contract：确认一个 Analysis reference 当前是否存在。

`AnalysisDeletionGuard` 是下游引用阻止删除的扩展点：

```text
Analysis delete
    -> all deletion guards
    -> delete only when every guard accepts
```

Guard 可以阻止 command，但不能修改 Analysis Definition。

## 8. Change Fact

`AnalysisChangedEvent` 是 Analysis mutation 已发生的事实通知：

```text
REFRESH / DELETE semantic fact
```

它不是 Lineage command，也不包含 Lineage 内部类型。Definition 因此不依赖 Lineage implementation。

## 9. Lineage Is Derived Projection

```text
committed Analysis truth
      ↓
AnalysisChangedEvent
      ↓ after commit
AnalysisLineageSynchronizer
      ↓
AnalysisLineageGraphGateway
      ↓
Lineage graph
```

Lineage projection 可以重建/收敛；Analysis business truth 不依赖某次 projection 是否成功。

字段 usage 是 Analysis QuerySpec 的确定性派生：

```text
fieldId -> DIMENSION / METRIC / FILTER / SORT
```

它用于 Lineage evidence，但不是新的可变业务实体。

## 10. Persistence Representation

`yak_analysis` 当前持久化 current definition。

`query_spec_json` 和 `visual_config_json` 是 persistence representation，不是 Repository API。JSON codec 属于 Repository boundary。

## 11. Explicit Non-entities

当前不存在这些 Analysis Domain Entity：

```text
AnalysisVersion
AnalysisExecution
AnalysisAttempt
AnalysisResult
AnalysisSchedule
AnalysisRuntime
```

如果新需求需要它们，应先定义 owner、identity、lifecycle 与和现有 AnalysisDefinition 的关系。