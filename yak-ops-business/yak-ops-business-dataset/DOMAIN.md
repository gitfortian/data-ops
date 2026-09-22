# Dataset Domain

本文件定义 Dataset 当前领域事实、生命周期与 truth ownership。历史重构过程不属于 Domain contract。

## 1. Core Identity

```text
Dataset
    != DatasetVersion
    != DatasetField
    != Dataset Query
    != Query Performance
    != Dataset Lineage
```

字段相似不代表可以共用一个通用状态对象。

## 2. Dataset

`Dataset` 是长期稳定的业务身份，拥有 name / description / status / currentVersionId 等 identity-level 状态。

`currentVersionId` 是**选择指针**，不是“版本内容”。

因此：

- Dataset 上线/下线不修改历史版本；
- 更新 currentVersionId 不修改旧版本；
- 同一个 DevelopmentNode 重复保存保持同一个 Dataset identity。

## 3. DatasetVersion

`DatasetVersion` 是 immutable source snapshot。

QUERY_REVISION 版本冻结：

```text
sourceTaskAssetId
sourceTaskRevisionId
sourceTaskRevisionNo
schema snapshot
```

SQL_QUERY 版本冻结：

```text
dataSourceId
SQL
schema snapshot
```

版本创建后，上游 TaskAsset current revision、Data Development editor、Datasource metadata 后续变化都不能改写该版本。

## 4. DatasetField

`DatasetField` 属于某个确定 DatasetVersion 的 schema contract。

fieldId 是 Dataset 下游绑定的稳定标识；physicalName 是该版本实际输出字段身份。

硬规则：

- preview field 没有持久化身份；
- 保存版本时分配 fieldId；
- compatible physical field 应复用已有 fieldId；
- fieldId 不能因为 displayName/description 改变而无故漂移；
- DatasetField 不属于 Dataset identity 本身，而属于 version schema。

## 5. Publication

Publication 的领域顺序：

```text
exact source evidence (from draft or TaskAsset)
 -> normalized schema
 -> append DatasetVersion
 -> move currentVersionId
```

不存在"修改 V3 使它变成 V4"。

SQL_QUERY publish 在 draft source 与 current version 完全相同时保持幂等：

```text
same dataSourceId AND same SQL AND same field contract
 -> no new version appended
```

QUERY_REVISION publish 在 current TaskRevision 未变化时保持幂等。

## 6. Current Source vs Version Snapshot

```text
current upstream source
    !=
existing DatasetVersion source snapshot
```

例如：

```text
TaskRevision V10 -> DatasetVersion 3
TaskRevision later becomes V11
```

查询/血缘读取 DatasetVersion 3 时仍必须使用 V10。

## 7. Query Truth

一次 Dataset Query 的业务 truth 是：

```text
Dataset identity
+ exact DatasetVersion
+ request
+ source adapter execution result
```

Query Runtime 不允许：

- 指定 V1 后偷偷改用 current V2；
- QUERY_REVISION 重新解析 TaskAsset current revision；
- SQL_QUERY 回读 editor current SQL；
- sourceType 未接入时猜测另一个 adapter。

## 8. Query Performance

`DatasetQueryPerformance` 是 observability evidence。

```text
Query Performance != Query Result != Dataset truth
```

它可以描述一次 Query attempt 的：

```text
queryId
terminal status (SUCCESS / REJECTED / FAILED / TIMEOUT)
failure stage / error evidence
latency breakdown
rows / truncation
privacy-safe SQL preview / fingerprint
```

这些证据只用于定位运行问题，不参与 Dataset/Version 生命周期，也不能反向改变 QueryResult 或原业务异常。

## 9. DevelopmentNode Binding

Data Development Dataset Node 与 Dataset 的长期关系：

```text
DevelopmentNode
    -> stable Dataset identity
        -> draft state (mutable)
        -> version history (immutable)
```

Dataset 拥有可选的 draft state，与 immutable version 独立：

- draft 包含 `dataSourceId + SQL + draftFields`，是编辑器当前工作状态；
- 保存草稿只写入 draft state，不创建 DatasetVersion；
- 发布从 draft 冻结为 immutable DatasetVersion，然后更新 currentVersionId；
- draft state 可以多次覆写，不影响任何已有版本。

## 9a. Draft vs Version

```text
draft state          != DatasetVersion
draft dataSourceId   != version dataSourceId
draft SQL            != version SQL
draft field contract != version field contract
```

draft 是 mutable working copy，DatasetVersion 是 immutable frozen snapshot。

Query Runtime / Analysis / Lineage 只消费 immutable version snapshot，不读 draft。

## 10. Lineage Projection

Dataset Lineage 是从当前 immutable DatasetVersion 派生的 graph projection。

```text
DatasetVersion truth
      |
      v
Derived lineage evidence
```

Lineage 不允许反向成为 DatasetVersion 的配置来源。

派生失败语义必须区分：

```text
SKIPPED     = analyzer/capability unavailable or no analyzable SQL
FAILED      = analysis attempted but failed
PARTIAL     = only part of projection resolved
UNRESOLVED  = no useful mapping resolved
SUCCESS     = mapping resolved without unresolved references
```

这些状态是 lineage evidence，不是 DatasetStatus。

## 11. External Ownership

Dataset 不拥有：

- Task Catalog current Task truth；
- Datasource connection/catalog truth；
- shared Lineage graph truth；
- Core SQL Execution Runtime。

这些能力只能从 Dataset 明确边界进入；Dataset 内部只能保存自己需要的 immutable snapshot/value。

## 12. Repository Truth

Repository 负责 Dataset aggregate 的持久化 contract：

```text
Dataset identity
DatasetVersion append
DatasetField schema
currentVersion pointer
Dataset draft state (dataSourceId + SQL + draftFields)
```

Draft state 属于 Dataset identity 级别的 mutable working data，不属于 DatasetVersion。

Query Performance 虽然通过独立 Repository port 持久化，但仍只是 observability read model，不属于 Dataset aggregate truth。

Repository 不拥有 Publication policy、Query routing 或 Lineage behavior。

## 13. Domain Gap Rule

出现以下需求时先报告 Domain Gap：

- 修改已经发布的 DatasetVersion 内容；
- 一个版本动态跟随 current TaskRevision；
- fieldId 根据 displayName 自动重建；
- Query 指定版本但允许自动漂移；
- Lineage 结果反向决定 DatasetVersion；
- DevelopmentNode 同时绑定多个"当前 Dataset identity"；
- Query Performance 变成业务状态机；
- Query Runtime / Analysis / Lineage 直接消费 draft state。

模型表达不了的需求，先更新 Domain/Requirement 与测试，不通过隐藏 flag 绕过。
