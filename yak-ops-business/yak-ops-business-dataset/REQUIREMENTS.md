# Dataset Requirements

本文件定义 Dataset 当前必须保持的行为 contract。领域不变量看 `DOMAIN.md`，代码职责看 `ARCHITECTURE.md`，依赖方向看 `DEPENDENCIES.md`。

## 1. Dataset Identity

- `Dataset` 是稳定业务身份；
- 上游来源变化不能通过覆盖旧 `DatasetVersion` 表达；
- `currentVersionId` 只负责选择当前版本；
- DevelopmentNode 重复保存必须继续绑定同一个 Dataset identity。

## 2. Immutable Version Publication

发布 QUERY_REVISION Dataset 时必须：

1. 来源必须是 Data Development 的 ONLINE SQL TaskAsset；
2. 固定当前 exact TaskRevision id / revisionNo；
3. 发现或接收字段 schema；
4. append 新的 immutable `DatasetVersion`；
5. 更新 `currentVersionId`；
6. 事务提交后请求派生血缘刷新。

同一个 current TaskRevision 的 release publish 保持幂等，不重复追加版本。

## 3. SQL_QUERY Version

Data Development 的 standalone Dataset 必须把：

```text
dataSourceId + SQL + field contract
```

冻结在 DatasetVersion 中。

### 3a. Draft Save

SQL_QUERY Dataset 的编辑器保存必须：

1. 将 `dataSourceId + SQL + fields` 写入 Dataset draft state（mutable）；
2. draft state 不创建 DatasetVersion，不修改 currentVersionId；
3. draft fields 写入 `yak_dataset_draft_field` 表，拥有 stable fieldId；
4. 保存后 Facade 返回的 NodeDataset 必须包含 draft 字段，以便编辑器恢复工作状态。

### 3b. Draft Publish

SQL_QUERY Dataset 的发布必须：

1. 从 draft state 读取 dataSourceId、SQL、draftFields；
2. draft 缺少有效 dataSourceId 或 SQL 时显式拒绝；
3. normalize field contract 后冻结为 immutable DatasetVersion；
4. 更新 currentVersionId；
5. 事务提交后请求派生血缘刷新。

### 3c. SQL_QUERY Publish Idempotency

发布时如果 current version 的 `dataSourceId + SQL + field contract` 与 draft 完全相同，保持幂等，不重复追加版本。

## 4. Schema Contract

- preview schema 不拥有持久化 fieldId；
- 版本保存时才分配稳定 fieldId；
- compatible physical field 跨版本保持稳定 fieldId；
- 输出字段名必须继续满足当前安全 identifier 约束；
- 重复字段、非法字段、不可识别 result set 必须显式失败；
- schema discovery 只能执行只读 SQL。

## 5. Dataset Lifecycle

- Dataset 支持 ONLINE / OFFLINE；
- status 改变只修改 Dataset identity 状态，不重写任何 DatasetVersion；
- status 改变后请求派生血缘刷新；
- OFFLINE Dataset 不能通过 Query Runtime 查询。

## 6. Query Runtime

Dataset 查询必须绑定 exact DatasetVersion：

- 未指定 versionNo -> 使用当前 `currentVersionId`；
- 指定 versionNo -> 使用该 exact immutable version；
- 不能因为 currentVersion 后续变化而漂移；
- sourceType 必须有显式 `DatasetSourceQueryAdapter`；
- QUERY_REVISION 必须解析版本中固定的 TaskRevision；
- SQL_QUERY 必须使用版本中固定的 dataSourceId + SQL；
- SQL 保持单条、只读、安全字段/filter/limit 规则；
- 每次 Query attempt 必须生成唯一 queryId，并形成 `SUCCESS / REJECTED / FAILED / TIMEOUT` 之一的终态诊断；
- Query Performance 记录、读取或清理失败不能改变查询业务真相，也不能覆盖原始业务异常。

## 7. Analysis Binding

Analysis 绑定 Dataset 时必须：

- Dataset 为 ONLINE；
- Dataset 存在 current version；
- 所有 fieldId 都属于当前 schema；
- 空/未知 fieldId 显式拒绝。

现有 `DatasetService.validateAnalysisBinding(...)` 跨模块 contract 保持兼容。

## 8. Data Development Boundary

`DevelopmentDatasetFacade` 是 Data Development 的稳定入口。

必须保持现有：

- find by developmentNodeId；
- standalone SQL preview / previewQuery / save；
- legacy TaskAsset preview / save；
- publishVersion；
- public nested record shape。

NodeDataset 必须包含 draft 字段（`draftDataSourceId` / `draftSql` / `draftFields`），以便编辑器在保存后恢复工作状态。

Data Development 不直接调用 Dataset Repository / DAO / internal Manager。

## 9. Derived Lineage

Dataset lineage 是派生投影，不是 Dataset transaction truth。

必须保持：

```text
Dataset transaction commit
 -> AFTER_COMMIT refresh
 -> REQUIRES_NEW lineage transaction
```

- QUERY_REVISION 解析 exact source revision；
- SQL_QUERY 不依赖 TaskCatalog；
- analyzer 不可用 -> SKIPPED；
- analyzer 异常 -> FAILED evidence；
- parse failure 仍保留 Dataset / DatasetField structural assets；
- Datasource Catalog 仅用于提高 schema/ordinal 解析精度；
- lineage refresh 失败不能回滚已经提交的 Dataset。

## 10. Observability

Query Performance 是派生诊断 evidence，不属于 Dataset / Version / QueryResult 的业务状态，并必须满足：

- 正常路径持久化，重启和多实例切换后仍可查询；
- newest first，查询窗口有上限；
- 支持 datasetId / queryId / terminal status / 最小总耗时过滤；
- Project Context 严格隔离，有项目只读该项目，无项目只读 legacy/null scope；
- 只保存脱敏 SQL preview 和 SQL shape fingerprint，不持久化原始字面量过滤值；
- 持久化不可用时允许退化到 bounded process-local fallback；
- fallback 不能跨 project scope 泄漏；
- 诊断保留期必须有界，默认保留 7 天并支持配置；
- Observability 自身异常不得影响 Dataset Query 结果。

## 11. Compatibility

除非 Requirement 明确演进，否则不得顺手改变：

- `/api/v1/datasets/**` 既有 route 语义；
- `DatasetService` public methods / compatibility records；
- `DevelopmentDatasetFacade` public API；
- persisted sourceType/status values；
- exact DatasetVersion SQL runtime behavior；
- Lineage shared graph API。

允许 additive contract 演进时必须同步 Requirement / Domain / migration / tests，例如新增：

- Query Performance 可选过滤参数；
- Query Performance VO 的诊断字段；
- observability read-model Flyway 表；
- Repository/DAO 的窄 observability persistence contract；
- Dataset draft columns 和 draft_field 表；
- DevelopmentDatasetFacade 的 draft 字段和 publishVersion API；
- SQL_QUERY publish idempotency 比对规则。

这类演进必须保持现有 Query/Analysis/Dashboard 调用兼容，不能混入无关业务语义变化。
