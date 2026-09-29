# Analysis Review Contract

Analysis PR 评审重点不是“类名是否更漂亮”，而是 Definition、Dataset、Dashboard、Lineage 的 truth 是否仍然只有一个 owner。

## Requirement

- 新功能是否仍属于 reusable Analysis Definition？
- 是否无意引入了 Version / Publish / Execution / Result state？
- REST / DB / Query semantics 变化是否被明确写成需求，而不是混在重构里？

## Domain

- `AnalysisDefinition` 是否仍表达 current definition？
- `AnalysisQuerySpec` 是否仍是 declarative semantics，而不是 SQL/runtime state？
- Dataset schema/version 是否仍由 Dataset owner 管理？
- Dashboard layout/version 是否仍留在 Dashboard？
- Lineage graph 是否仍由 Lineage owner 管理？

## Definition Lifecycle

- create/update 是否先 normalize + Dataset binding validate 再 persist？
- delete 是否执行全部 deletion guard？
- Manager 是否只拥有 mutation lifecycle，没有吞掉 Query/Dataset/Lineage 实现？
- Reader 是否保持 read-only role？

## Query / Visualization

- limit/timeout/filter/sort 约束是否被行为测试保护？
- 新 chart type 是否明确其 dimension/metric cardinality？
- Visual config 是否只描述 chart-local 语义？
- 是否把 Dashboard layout 偷偷放进 AnalysisVisualConfig？

## Dataset Boundary

- Definition 是否只依赖 `AnalysisDatasetGateway`？
- Dataset 具体类型是否只停在 `gateway/dataset` adapter？
- 是否复制 Dataset status/version/field metadata 成 Analysis truth？

## Reference / Delete

- 下游引用校验是否使用 `AnalysisReferenceService` 等窄 contract？
- Dashboard 删除约束是否通过 `AnalysisDeletionGuard` 表达？
- Guard 是否只阻止删除，而不是反向修改 Analysis？

## Lineage

- 刷新是否仍为 AFTER_COMMIT？
- Projection failure 是否仍与已提交 Analysis 隔离？
- Synchronizer 是否只依赖 `AnalysisLineageGraphGateway`？
- `ANALYSIS_BINDING` evidence 与 asset key 是否兼容？
- field usage role/aggregation evidence 是否保持确定性？

## Persistence

- Repository contract 是否只暴露 Analysis Domain？
- JSON/PO/MyBatis 是否停在 Repository/DAO boundary？
- 是否无意修改 `yak_analysis` schema 或 Flyway history？
- Datasource 依赖是否只用于 infrastructure wiring？

## Package / Role

- 新类是否进入准确 subsystem？
- `@Service` 是否只用于稳定 Facade？
- 内部角色是否使用 Manager/Reader/Normalizer/Policy/Gateway/Adapter/Synchronizer 等明确词汇？
- 是否重新出现 `service/support/helper/utils/common/base`？
- dependency graph 是否保持无环？

## Compatibility

至少确认：

```text
/api/v1/analyses/**
request/response JSON
long id serialization
yak_analysis
Dataset binding rules
query defaults/limits
chart rules
visual defaults
Dashboard deletion guard
Lineage evidence and relation semantics
```

## Tests

结构调整至少同时需要：

- 相关行为回归测试；
- `AnalysisDependencyBoundaryTest`；
- `AnalysisLayeringConventionTest`；
- `AnalysisCodeStyleConventionTest`；
- `AnalysisRoleConventionTest`；
- `AnalysisArchitectureDocumentationTest`。

如果某条规则只能靠“大家记住不要这么写”，应优先把它变成可执行 guard。