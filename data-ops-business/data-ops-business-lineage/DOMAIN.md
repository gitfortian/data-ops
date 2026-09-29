# Lineage Domain

本文定义 Lineage 的核心领域语义。结构重构不能因为类移动或角色拆分改变这些含义。

## Asset

`LineageAsset` 是参与元数据图的稳定对象身份。

当前支持的类型包括：

```text
TABLE
COLUMN
SQL_TASK
DATASET
DATASET_FIELD
CHART
DASHBOARD
METRIC
```

`assetKey` 是业务可稳定寻址的键；`id` 是持久化标识。`sourceType/sourceId` 表达资产的来源或 ownership 线索，不等价于通用 Asset identity。

表、字段类资产可以携带 `dataSourceId / databaseName / schemaName / tableName / columnName`；`parentAssetId` 表达层级包含关系的父资产引用。扩展属性通过 `properties` 承载，但不能替代需要稳定查询和约束的核心字段。

## Relation

`LineageRelation` 是从 upstream source asset 指向 downstream target asset 的有向边。

```text
sourceAssetId  ───────────────>  targetAssetId
       upstream                    downstream
```

当前关系类型：

- `READS_FROM`
- `WRITES_TO`
- `DERIVES_FROM`
- `CONSUMES`
- `CONTAINS`

关系类型描述 target 如何使用、派生或包含 source。调用方不能通过交换 source/target 来表达相反语义。

## Evidence

关系可以携带：

```text
sourceType
sourceId
expression
confidence
version
observedAt
properties
```

`sourceType + sourceId` 是自动生成血缘的重要 evidence scope，用于替换同一来源重新计算出的关系。`version` 用于区分来源版本；`observedAt` 表达观测时间；`confidence` 范围固定为 `[0, 1]`。

Evidence 说明“为什么存在这条边”，不负责重新定义 Asset identity。

## Graph

`LineageGraph` 是一次有边界的图查询结果，而不是新的持久化事实。

```text
root
+ direction(UPSTREAM / DOWNSTREAM / BOTH)
+ requested depth
+ deduplicated assets
+ deduplicated relations
```

当前最大遍历深度为 10。图读取不能修改关系或资产状态。

## Replacement

自动生成来源重新发布血缘时采用 evidence-scoped replacement：

```text
beginReplacement
  -> capture old evidence endpoints
  -> delete old evidence relations
  -> publish new assets / relations
  -> delete only unreferenced assets owned by the caller
```

清理必须保守。无法证明 ownership 或仍被其他边/子资产引用时，宁可保留资产，也不能误删其他来源共享的元数据。

## Revision Ordering

同一资产来源存在 revision 时，新 revision 已提交后，旧 revision 不能覆盖新结果。revision ordering 是领域安全规则，不应依赖请求恰好串行。

## SQL Projection Contract

`SqlProjectionLineageAnalyzer` 负责把只读 SQL projection 映射到物理来源字段，输出 source table、source column、output column、mapping kind、表达式与 ordinal 等信息。

核心模块只拥有 contract。Schema provider 和具体 parser 是边界实现；无法解析的引用通过 unresolved count 暴露，而不是静默制造错误的物理字段血缘。
