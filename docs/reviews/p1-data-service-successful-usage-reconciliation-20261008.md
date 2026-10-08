# P1 · Data Service Usage 消费证据成功调用窗口饥饿修复（2026-10-08）

> Code-backed review / implementation evidence，基线 `data-ops main d515cbafdd9f656cb3b6b96de79afcfd3c6008de`（PR #407 已合并）。
> 产品归属：PD-002 `ACCEPTED` / F-004 `APPROVED`。PD-008 / PD-007 均仍 `PROPOSED`，不涉及 Consumer ACK、通知、Approval、Lifecycle 或发布 Gate。
> 本次是来源证据查询与 Usage 正确性治理，不新增前端“已成功”状态，不将零行解释为全量历史无消费者。

## 一、读取最新代码后确认的缺陷

真实调用链（现状）：

```text
GET canonical ConsumerImpact(ProductKey DATA_SERVICE:<apiId>, limit <= 200)
  → ConsumerImpactService.view()
  → DataServiceUsageEvidenceSynchronizer.synchronizeRecentByProduct(apiId, limit)
  → DataServiceCallLogReader.recentByApi(apiId, boundedLimit)
  → DataServiceCallLogRepositoryAdapter.recentByApi()
  → MyBatis-Plus projectId + apiId + order by create_time DESC, id DESC + LIMIT
  → Java stream normalizer: failed calls → IGNORED, successful calls → NORMALIZED/GAP/UNAVAILABLE
```

Data Service 的 **API 调用日志** 是完整诊断域，应包含成功与失败；Consumption **Usage Evidence** 只表示成功发生的消费。原读取先按“全部调用”截取前 200 条，再在 Java 里丢弃失败调用，因此高失败率（例如连续 200 个最新调用失败）会使本应在成功消费证据窗口内的较早成功调用被挡在窗口之外。于是 Owner 看不到其版本/Consumer 的真实成功证据，而 source window 又达到上限，不能得出可靠的依赖范围结论。

这不是“有没有发送消费者通知”的问题，也不能通过增加状态文案解决：必须将 **成功条件放在来源数据库层，在 LIMIT 前筛选**。成功仍可能没有 Consumer ID 或精确 SourceRevision，应保留 `GAP`，不能伪造成合法 Consumer。

## 二、代码更改与 Source Truth

1. `DataServiceCallLogRepository.recentSuccessfulByApi(apiId,limit)` 新增专用于 Usage 补偿的只读源查询。 `DataServiceCallLogRepositoryAdapter` 用 MyBatis-Plus LambdaQueryWrapper 同一查询同时约束 `projectId`、`apiId`、`success=true`，然后按 `createTime DESC,id DESC` 排序并应用有上限的 LIMIT。**没有 SQL 接口逻辑、没有 XML**；Project 未通过、非法 API ID 继续 fail-closed。
2. `DataServiceCallLogReader.recentSuccessfulByApi` 公开可执行的 1～200 有界读取，保留原 `recentByApi` 和 `recent` 的“失败与成功都可见”诊断能力，保留 `findByApiAndId` 精确原始审计查看能力。
3. 仅 `DataServiceUsageEvidenceSynchronizer.synchronizeRecentByProduct` 切换到 successful-only 来源读取；`normalize(InvocationRecord)` 对失败调用的 IGNORED 防御仍在；`synchronizeRecent` 的其他用途未变。Data Service InvocationRecord / Source Revision / ConsumerRef / deduplicationKey 均按现有 normalizer 处理。
4. `ConsumerImpactService` 聚合 Subscription 与归一化 Usage 的事实 Owner 逻辑不变；“sourceRecordCount/sourceWindowLimitReached”现在对 Data Service 指此 API 的**成功源调用记录窗口**，不再被失败调用占满，但窗口仍然有界且不保证所有历史消费者。
5. 来源读取失败继续 `UNAVAILABLE`；成功但来源版本/Consumer 无法归属继续 `GAP`；真实消费同步仍由 Consumption 负责生成 normalized successful Usage，不能用 Data Service 原始失败日志构造 Consumption Usage。

## 三、测试与验收

- Source Repository 单测捕获 MyBatis-Plus 真正构造的 SQL segment / 绑定值，要求同一查询同时存在 projectId、apiId、`success=true`、倒序与有界 LIMIT；非法 API ID 不触发 Project 或 mapper 读取。
- DataServiceCallLogReader 回归：成功专用查询和普通 `recentByApi` 分离、limit<=200。
- UsageSynchronizer 回归：按 ProductKey 查询仅调用 `recentSuccessfulByApi`；仍对成功但 consumer/revision 缺失标 GAP；无成功调用时不调用 normalizer。
- 既有 Golden test 的 InMemoryCallLogRepository 保持与新增 repository 合同一致；仍验证失败 invocation 不产生 Usage，正常执行证据保留精准 SourceRevision 与 ConsumerRef。
- GitHub Checks 必须查看 Product Guard、Consumption Checks、Architecture Checks **对应最终 HEAD**。本轮不允许借静态测试声称完整运行环境 Golden E2E PASS。

## 四、#336 真实 Golden E2E 待验

在项目 P 中为 API A 创建成功调用（包含已知 Consumer 和精确 SourceRevision），再制造大量后续失败调用（建议至少超过当前 limit；原窗口会挤走成功）；打开数据产品 Consumer Impact，必须仍能观察到该成功使用、正确 Consumer 与原始 SourceRevision、正确 invocation evidenceId，且不会把失败调用计入 successUsageCount。跨 Project Q，不得读取 P 的原始审计或归一化 Usage；来源执行审计页仍能看到失败调用。再执行无 Consumer ID 的 PUBLIC 成功请求，核对 GAP/UNAVAILABLE、完整性说明。

现场留 deployed SHA、actor/Project、API、caller ID、invocation/revision 精确 ID、实际 Network/API/数据库来源审计与 PASS/FAIL/BLOCKED。**真实 Golden E2E 仍 PENDING。**

## 五、产品边界

F-004 规定 Subscription、Usage、Access、Lineage 是不同事实：本次改进的是 Usage 成功证据的回溯覆盖质量；不能因此认定全部历史被完整遍历、推断没有外部 Consumer、替代授权或落实 PD-008 Consumer 正式确认。并且数据治理仍依赖来源域真实 Audit，不复制第二套 Data Service 调用日志。
