# Analysis Dependencies

本文定义 Analysis production package 的允许依赖方向。根 package 的 `AnalysisService / AnalysisReferenceService / AnalysisDeletionGuard` 是兼容 API，不作为内部 subsystem bucket。

## 1. Top-level Matrix

| Source | Allowed Analysis packages |
|---|---|
| `controller` | `domain`, `query`, `visualization` + root stable facade |
| `definition` | `domain`, `gateway`, `query`, `repository`, `visualization` + root deletion extension point |
| `domain` | `query`, `visualization` |
| `query` | `visualization` |
| `visualization` | none |
| `reference` | `definition` |
| `lineage` | `definition`, `domain`, `gateway`, `query` |
| `gateway` | none inside Analysis |
| `repository` | `dao`, `domain`, `query`, `visualization` |
| `dao` | none above persistence |
| `config` | none inside Analysis |

目标图必须保持无环。

## 2. Primary Direction

```text
Controller
    -> stable facade
    -> Definition
    -> Repository
    -> DAO

Definition
    -> Query / Visualization
    -> AnalysisDatasetGateway

Lineage
    -> Definition read/change facts
    -> AnalysisLineageGraphGateway
```

低层不能反向进入 Controller/Manager。

## 3. Dataset Corridor

唯一业务 corridor：

```text
AnalysisDefinitionNormalizer
    -> AnalysisDatasetGateway
    -> DatasetAnalysisAdapter
    -> DatasetBindingPolicy
```

禁止 Definition/Query/Controller/Repository 直接依赖：

```text
DatasetService
DatasetRepository
DatasetDao
Dataset internal implementation
```

Dataset adapter 可以依赖 Dataset owner 暴露的稳定 binding capability。

## 4. Lineage Corridor

唯一业务 corridor：

```text
AnalysisLineageSynchronizer
    -> AnalysisLineageGraphGateway
    -> LineageAnalysisAdapter
    -> Lineage module
```

禁止 Synchronizer/Definition/Controller 直接依赖：

```text
LineageService
LineageMaintenanceService
LineageAsset
Lineage mapper/repository
```

Lineage adapter 是共享 Lineage model/protocol 转换边界。

## 5. Query / Visualization Direction

```text
query -> visualization
visualization -> nothing above
```

`AnalysisQueryNormalizer` 可以调用 `AnalysisChartBindingPolicy`，但 Visualization 不应反向依赖 Query。

Semantic value object 不依赖 Spring、MyBatis、HTTP DTO/VO 或 persistence model。

## 6. Reference Direction

```text
AnalysisReferenceService
    -> reference.AnalysisReferenceReader
    -> definition.AnalysisReader
```

Reference read-side 不依赖 Manager，也不拥有 mutation。

## 7. Persistence Direction

```text
AnalysisRepository (Domain contract)
    <- Definition / Reader

AnalysisRepositoryAdapter
    -> DAO
    -> PO / Mapper
```

Repository contract 不允许出现：

```text
controller DTO / VO
AnalysisPO
Mapper
MyBatis type
JSON storage string as business contract
```

`AnalysisJsonCodec` 只能属于 persistence boundary。

## 8. Controller Boundary

Controller 与 HTTP Mapper 不直接进入：

```text
repository
dao
gateway adapter
lineage synchronizer
Dataset / Lineage implementation
```

Controller 通过 root stable `AnalysisService` 进入 use-case。

## 9. Datasource Infrastructure

Analysis POM 当前直接依赖 `yak-ops-business-datasource`，因为 config / DAO / repository adapter 复用业务 DataSource enablement 与 wiring。

这条依赖只属于 infrastructure；不得扩散为 Definition/Query/Domain 对 Datasource business service 的依赖。

## 10. Forbidden Buckets

production 不重新引入：

```text
service/
support/
common/
helper/
utils/
util/
base/
persistence/
```

稳定 Facade 是明确命名的 root class，不需要一个通用 `service` package。

## 11. Change Rule

如果确实需要改变以上 dependency graph：

1. 先确认新的 truth owner；
2. 修改 `ARCHITECTURE.md` / 本文；
3. 修改 executable architecture tests；
4. 再修改生产代码。

不能只删除或放宽 guard 让新依赖“通过”。