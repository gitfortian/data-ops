# P1 · Data Service 精确修订原始成功审计补偿（2026-10-08）

> 基线 `data-ops/main af11b0f9df509535df15b9928516fe26d2cee668`（PR #411 merged）。
> Product Gate: PD-002 ACCEPTED / F-004 APPROVED。PD-008 PROPOSED / NOT_STARTED、PD-007 PROPOSED，**本轮不创造 Consumer 确认/通知/审批/发布门禁**。

## 真实缺口与代码链路

- PR #408 已把 Data Service 产品级补偿改为 **Project + API + success=true 再 LIMIT 200**，不会被失败请求挤占。
- PR #411 已为精确 `SourceVersion.identity` 查询增加 `UsageEvidenceRepository.listByVersion(Project,Product,Revision,limit)`，但**仅能恢复以前成功归一化并写入 Consumption Usage 表的历史记录**。
- 实际代码 `ConsumerImpactService.view(ProductKey,usageLimit,sourceVersionIdentity)` 在调用 `usage.listByVersion` 之前仍使用 `DataServiceUsageEvidenceSynchronizer.synchronizeRecentByProduct(apiId,limit)`；当旧版调用始终保留在 Data Service 原始成功调用审计，但从未成功归一化，同时后续新版本的成功调用超过 200 时，产品级 source window 不包含旧版，自然无法补偿旧版。此时精确修订查询展示为空，但历史成功调用确实在来源审计中。
- Data Service `DataServiceCallLogRepositoryAdapter` 是来源拥有者，已经具备 `projectId` 隔离、`apiId` 隔离及调用成功条件，数据库 `source_revision_id` 列真实存在。正确恢复必须让 `source_revision_id` 也进入 SQL WHERE，而非先查最近整个 API 再在 Java 过滤。

## 实施范围（只读来源 + 已批准的归一化写入）

1. `DataServiceCallLogRepository.recentSuccessfulByApiAndRevision(apiId,sourceRevisionId,limit)`：使用源域 `CurrentProject.requireProjectId()`；参数为正 Long。MyBatis-Plus `Wrappers.lambdaQuery()` 一次性约束 `ProjectId`、`ApiId`、`SourceRevisionId`、`success=true`，然后 `createTime DESC,id DESC`，最后 `LIMIT<=1000`（Consumption reader 再限制 200）。不写 SQL/XML，不选择全局审计后再过滤。不改变普通 `recent`、`recentByApi`、`recentSuccessfulByApi` 或按 ID 精确审计的诊断合同。
2. `DataServiceCallLogReader.recentSuccessfulByApiAndRevision`：提供具明确有界限制的来源成功审计读取。
3. `DataServiceUsageEvidenceSynchronizer.synchronizeRecentByProductAndRevision`：仅对请求的 API/Revision 消费审计应用既有 `DataServiceUsageEvidenceNormalizer`、`UsageEvidenceService` 的 Source Revision + ConsumerRef 精确归属和 deduplication 合同；不重新发明 Consumer，人无法归属的成功执行继续 GAP、provider 失败继续 UNAVAILABLE；已有同一 invocation 的归一化应保持幂等。
4. `ConsumerImpactService.view(...,exactVersion)`：DATA_SERVICE 指定版本时先走按 revision 成功审计补偿，再执行已有的 `usage.listByVersion`，可读到此次补偿写入的历史 Usage；**不指定版本仍走 API 最近成功窗口**。DATASET 保留现有查询和已有的来源补偿，不伪称已支持 Dataset revision-scoped historical audit recovery。
5. `sourceVersionIdentity` 是十进制正 Long 不可变身份，不是显示版本号；无法无损解析时保留可能已存在的持久 normalized Usage，但标注 source evidence 不可用，不拿 R1 的数据充作 R2。
6. 覆盖文案明确：Data Service 精确修订源审计查询仍只返回该修订最近最多 200 条成功调用；持久化 Usage 查询单独有同样窗口上限；旧于来源保留期的记录和外部调用无法凭此发现，**不代表全量历史已覆盖**。

## 回归与真实 E2E

- Source repository Mockito + MyBatis-Plus metadata SQL test：捕获真正 SQL segment/绑定值，四重约束 Project+API+SourceRevision+Success 均在 LIMIT 之前；按 observed time、ID 倒序；非法 API/Revision 不访问当前 Project 或 mapper；超限被 bounded。
- Source Reader 测试：revision-only 200 上限且不回退最近全 API 查询。
- Consumption Synchronizer 测试：`synchronizeRecentByProductAndRevision` 只归一化指定来源修订的结果，缺属性仍 GAP，不调用其它 revision window。
- ConsumerImpactService 测试：先执行 exact revision sync，再读同版本 normalized Usage；旧版 Long > JavaScript MAX_SAFE_INTEGER 以字符串精确 identity 测试；旧版无法转换 Long 的情况标记 SOURCE_UNAVAILABLE 而不是删除现有已持久化 Usage。
- Golden E2E #336（**PENDING**）：在 Project P、API A、旧版 R1 先有**真实成功调用 X，但 Consumption Usage 尚未归一化**；随后新版 R2 有至少 200 次成功调用。GET Impact `sourceVersionIdentity=R1` 应通过来源 DB 精确筛选 X 再按既有服务归一化，响应显示 C1、真实 Invocation ID 和 R1；重复读取不会新增重复 Usage。Project Q 的相同 API/Revision 不得看到 P 的审计或 Usage。调用故障仍在源域日志；来源审计丢失/故障须明确覆盖缺口。记录 deployedSHA、actor+Project、原始 Product/Consumer/Revision/Invocation ID、来源 DB/API、返回状态及 PASS/FAIL/BLOCKED。
- GitHub Checks（Product Guard / Consumption / Architecture）是 CI，**不代替 #336 真实部署 Golden E2E**。

## 非目标

不建立全历史分页/批处理、无前端额外状态、无数据源/SourceLifecycle/Approval/Consumer ACK 新域状态；不把本次有限成功审计窗口中的 EMPTY 认定为零历史 Consumer。
