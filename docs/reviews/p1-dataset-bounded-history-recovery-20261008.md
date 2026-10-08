# P1 — Dataset 精确版本成功审计：有界历史恢复游标

日期：2026-10-08。上位契约：ACCEPTED PD-002 / APPROVED F-004；产品工作包 #180；真实 Golden 验收 #336。前序：PR #413（Data Service 精确版本）、#416（Dataset 精确版本）、#419（当前样本 Golden）。

## 用户问题与当前行为

同一个旧 DatasetVersion 如果累计超过 200 条已保留成功审计，`GET /api/v1/consumption/impact?productKey=DATASET:...&sourceVersionIdentity=...` 仍只协调该版本最近最多 200 条来源审计。较早的成功查询即使仍在原始持久化审计表里，其 Usage 归一化缺失也不能被当前浏览窗口恢复。

本批新增 **显式手动分页恢复**，不暗中无限扫描、不修改普通 Impact 行为。恢复仅来自 Dataset 真正持有的持久化 `yak_dataset_query_performance` 成功记录，写入仍由 Consumption 的既有幂等 UsageEvidenceService 完成。

## API / Cursor

```http
GET /api/v1/consumption/impact/dataset-version-recovery?productKey=DATASET:101&sourceVersionIdentity=9007199254740993&limit=200
X-YAK-SECURITY-PROJECT-ID: <由已认证用户选择的现有 Project>
```

**首请求不传** `beforeAuditId`；当 `nextBeforeAuditId` 非空且 `retryRequired=false` 时，用它发下一次请求：

```http
GET /api/v1/consumption/impact/dataset-version-recovery?productKey=DATASET:101&sourceVersionIdentity=9007199254740993&beforeAuditId=701&limit=200
```

响应为已有 `Result<DatasetAuditRecoveryView>` 包装；业务字段包括 `productKey`、`sourceVersionIdentity`、`requestedBeforeAuditId`、`requestedLimit`、`visitedAuditCount`、`normalizedOrAlreadyPresentCount`、`normalizationGapCount`、`normalizationUnavailableCount`、`nextBeforeAuditId`、`retryRequired` 和 `retainedAuditExhausted`。

- `beforeAuditId` 是**数据库持久化成功审计行 ID 的排他上界**，不是 QueryId，不是版本号，也不是按时间构造的 OFFSET。按 `id DESC` 顺序翻页，后续新插入的较大 ID 不改变旧页边界。
- SQL 先限定当前 Project、精确 Dataset、精确不可变 DatasetVersion、`SUCCESS` 和可选 `id < beforeAuditId`，再执行 `ORDER BY id DESC LIMIT <=200`。跨 Project 没有审计可读；无 Project、非法 ID、无数据库 provider 均不假装为空成功。
- 对每行复用原有 QueryId 去重键和幂等插入；重复同页可以安全重试，不会产生多份成功 Usage。
- `NORMALIZED` 表示这次调用得到了规范化证据，**包括已经存在的幂等记录**，不是“新插入数量”。
- `GAP`、`UNAVAILABLE` 或意外 `IGNORED` 使 `retryRequired=true`、`nextBeforeAuditId=null`，必须修复原因后使用**同一个请求游标**重试；不许因某一条失败就跳到更老的一页。
- 当返回行数不足请求页大小且没有 GAP 时，`retainedAuditExhausted=true` 只意味着**当前保留的数据库审计**已达到终点，**不宣称审计清理之前的全历史完整**。
- 普通 Impact 的单次 200 条 source/Usage 视图依旧独立有界；分页补偿写入成功后，Impact 仍可能只展示规范化 Usage 的最新 200 条，不应当把窗口内的 Count 当作真实累计消费量。
- 保持现有 Consumption Controller 的登录态、Project REQUIRED、Asset READ 权限。只用于只读视图带来的幂等审计投影补齐，不操作 Dataset Query/Version 生命周期、Consumer 授权或 Source audit 内容。

## 验证与未完成

本批有 DAO SQL predicate+cursor+limit 单测、持久化 Reader 的 fail-closed 单测、200 行分页越界/续接测试、Consumer Impact 的合法身份/幂等结果/断点不可前进等单测。独立 PR 进入 CI 验证。

#336 中需要真实隔离环境构造 V1 的 **200+** 历史成功审计（在旧版 Usage 缺失前提下）并从第一页翻至较早的行；检查归一化前后持久化 Usage 的唯一性、相同页重试、跨 Project、错误游标、SQL 查询计划/超时与未保留历史边界。**未运行此 E2E 之前状态 PENDING**。

Data Service 同版本 >200 的审计游标不在本 PR 范围；不新增定时任务、后台 Dispatcher、无上限补偿、Schema、持久化 Cursor 状态、通知/审批规则或新的 Product Decision。