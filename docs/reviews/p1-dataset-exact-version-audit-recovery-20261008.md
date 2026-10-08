# P1 · Dataset 精确不可变版本的历史成功查询审计补偿（2026-10-08）

> 本批开发基线：`main@1dff6c68893e1f92720c43f8eec80be1260f4631`（PR #413 merged）。
> 依据：PD-002 ACCEPTED、F-004 APPROVED、产品化路线 #180 E3。
> 本文记录代码实现和待验收场景，**不代替 #336 真实部署验证**。

## 真实用户问题

1. Data Service 的精确修订审计补偿已在 #413 解决“新版本 200+ 成功调用遮蔽旧版本”的问题。
2. 但 Dataset 的 `ConsumerImpactService.view(..., sourceVersionIdentity)` 之前仍调用 `DatasetUsageEvidenceSynchronizer.synchronizeRecentByProduct(datasetId,limit)`。源读取只使用 `Dataset + SUCCESS + LIMIT 200`，不按 immutable `dataset_version_id` 预过滤。
3. 旧版 V1 的成功 Query 确实保留在 `yak_dataset_query_performance`，当时 Usage 归一化失败或没有执行；新版 V2 有大量成功查询后，旧版查询被最近来源窗口挤走。单查 normalized Usage 不能补救从未成功归一化的 V1。
4. 用户在变更 V1 前需要看见仍保存于 Dataset 原始来源里的真实成功消费，而不能将“窗口中没有”解释为“无人使用”。

## 实施与事实边界

- **唯一原始审计 Owner：** Dataset `DatasetQueryPerformanceStoreAdapter / DatasetDaoImpl`，继续使用已有持久化查询诊断表、已有 MyBatis-Plus mapper；无新表、Mapper XML、原始审计复制。
- **SQL 过滤次序：** `project_id = P AND dataset_id = D AND dataset_version_id = V AND status = SUCCESS`，然后 `started_at DESC, id DESC, LIMIT <= 200`，四项过滤全部在源数据库执行，严禁全局/最近 Dataset 查询后在 Java 过滤。
- **读取边界：** `DatasetQueryPerformanceReader.recentSuccessfulByDatasetAndVersion` 需要当前 Project，验证正数 Dataset / DatasetVersion ID，持久化 provider 缺失或抛错必须失败，绝不用仅存在本机的 diagnostics buffer 当历史事实。
- **归一化：** Consumption 新增 `synchronizeRecentByProductAndVersion`，复用 `DatasetUsageEvidenceNormalizer` 与 `UsageEvidenceService` 原有成功 Usage 去重、Project、ConsumerRef、SourceVersionRef 的严格归属。无法确认 subject / version 时继续 GAP。
- **Impact：** 仅 Dataset 请求精确 `sourceVersionIdentity` 才触发此路径，来源同步优先于 `UsageEvidenceRepository.listByVersion`；不指定版本的普通概览仍从整个 Dataset 最近成功窗口补偿；Data Service #413 行为完全不变。
- **版本与异常：** `sourceVersionIdentity` 必须是 canonical 正 Long 的精确不可变身份（不是显示名），溢出/非规范格式不能误读别的版本；仍可读取以前持久的 normalized Usage，但在 `sourceReadUnavailable`/Coverage 明确显示来源缺口。
- **页面：** 版本核对入口明确表达选择旧版会读取并补偿精确来源审计；审计和 normalized Usage 各有独立 200 条窗口，默认不宣称全量历史。

## 测试与验收证据

代码内的定向单测覆盖：
- `DatasetExactVersionSuccessAuditQueryTest`：捕获 MyBatis-Plus SQL segment、参数和有界顺序，确保 Project/Dataset/Version/SUCCESS 都在 LIMIT 前；非法身份无 SQL。
- `DatasetQueryPerformanceReaderTest`：旧版精确持久审计、200 上限、忽略进程内 buffer、无 store fail closed、非法身份不读取 Project。
- `DatasetUsageEvidenceSynchronizerTest`：按精确版本且当前 Project 归一化，不能回退 Dataset 最近整个来源窗口，缺口保留。
- `ConsumerImpactServiceTest`：sync-first → exact-version persisted Usage；大于 JavaScript 安全整数的 Long 身份不走浮点；非法显示版本返回 incomplete，不伪造证据。

**CI：PR 创建时尚未得到本批最终 GitHub Actions 成功结论。** 以 PR 最终 HEAD 的 Product Guard、Consumption Checks、Architecture Checks 为准，不提前标通过。

## #336 Golden E2E（PENDING，须真实部署）

`sceneId: J3-DATASET-EXACT-VERSION-OLD-AUDIT-RECOVERY`

1. 记录真实环境 `mainSHA / buildHash / deployedCommit / actorRole / projectId`，不要归档凭证。
2. Project P 的 Dataset D 发布 V1，使用真实 USER 完成一次成功 Query Q1；构造可控“原来源审计持久化、normalized Usage 缺失”的场景（仅隔离 Golden Sample，可清理）。
3. 发布 V2，并真实成功执行至少 200 次 V2 Query，确保整个 Dataset 的最近成功诊断窗口不再包含 V1 Q1。
4. 请求 `GET /api/v1/consumption/impact?productKey=DATASET:D&sourceVersionIdentity=V1`；必须查出 V1 Q1，并恢复 V1 的相同 Consumer/成功 Usage，不混入 V2。
5. 重复请求确保未写入重复 normalized Usage，且同一 Project 的精确 Usage 包含 Q1 的真实 Query ID/Version。
6. Project Q 的隔离身份不能查到 P 的来源审计或 Usage；无权读取、持久 store 故障、缺少 subject / version、未知旧版本都不得伪装 EMPTY/无消费者。
7. 按 #336 证据模板归档返回、脱敏审计和使用证据、重试/失败、结果 `PASS/FAIL/BLOCKED/PENDING`。

## 明确未覆盖

- **同一旧版本超过 200 条**时仍只补偿该版本最近的 200 次成功查询；全历史游标扫描/幂等分批恢复需要独立设计，不能无限扩张普通 Impact GET。
- 不推断已删除/保留期外来源审计、未登记外部消费者或全量历史。
- 不新增申请、审批、自动通知、确认、来源退休或发布阻断，PD-005/006/007/008 未正式批准的行为不随本批偷跑。
