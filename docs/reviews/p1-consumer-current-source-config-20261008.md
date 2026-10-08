# P1 · 已知 Consumer 当前来源配置核对（2026-10-08）

> Evidence / Review；代码基线 `data-ops main f15329dd4606d128aa17d13f651b30f85a6073ed`（PR #403 merged）。
> 生效范围：PD-002 ACCEPTED / F-004 APPROVED 的 Source-owned Consumer stable backlink、Access/Subscription/Usage facts 分离。
> **PD-008 仍 PROPOSED / NOT_STARTED，PD-007 仍 PROPOSED**；本次没有正式变更提案、Consumer ACK、通知、Approval 流程或发布门禁。

## 用户问题与当前源码

PR #403 允许 Owner 在已知 Consumer 列表跳转到真正 Data Service 调用方配置，但 Owner 仍需逐个打开页面才能确认此调用方当前是否启用、授权了此 API 或具有有效 Key。更严重的是原 `ConsumptionDetailPage` 在管理列表中用 `Number(product.productKey.sourceIdentity)` 对比 API 授权 ID：当 Long 超出 JavaScript 安全整数时，可能把来源 ID 近似到别的 API；若没有 `data-service:access`，页面仍请求需要该权限的来源管理接口，失败后还可能显示与零已配置类似的空状态。

已经逐文件读取以下源码及合同：
- `data-ops-business-consumption/relationship/ConsumerImpactController`、`ConsumerImpactService`：Project/Asset READ 下的已知声明依赖与成功 Usage（只限窗口）；是历史观察的 read-side truth。
- `data-ops-business-data-service/controller/v1/DataServiceConsumerController`：`@ProjectScope(PROJECT_REQUIRED)` + `@RequiresPermission(DataServicePermissionCode.ACCESS)`；`GET /api/v1/data-service/consumers` 返回项目内 `ConsumerView`。
- `data-ops-business-data-service/access/DataServiceConsumerManager`：来源拥有 enabled、accessScope ALL/SELECTED、apiIds、activeKeyCount、IP rules、rate limit 和 Key 管理权限；这些是**当前来源配置**，不是 Consumer 人工负责人。
- `data-ops-ui/src/pages/data-analysis/consumption/detail.tsx`：原直接 Number coercion 枚举可声明依赖的 Consumer，且任何展示 Data Service 的身份都尝试读取 ACCESS 来源管理列表。
- `data-ops-ui/src/config/consumer-source-navigation.ts`：PR #403 已冻结的精确 Consumer Type/Domain/ID 来源跳转；安全整数以上拒绝近似导航。

## 实施边界及用户操作

1. **真实来源状态**（只有合法 DATA_SERVICE / DATA_SERVICE_CONSUMER 的稳定身份才核对）：
   - `NOT_READABLE`：权限不足、来源故障或仍在加载；不推断 Consumer 不存在。
   - `UNSAFE_ID`：来源 API ID 或 Consumer ID 不能被现有数值型 API 无损表达；不执行近似匹配。
   - `NOT_FOUND`：来源已成功响应的当前 Project 列表中确实找不到此 Consumer；保留历史 Usage。
   - `DISABLED`：当前调用方禁用。
   - `NOT_GRANTED`：已启用，但当前 ALL/SELECTED API 范围不覆盖该产品。
   - `NO_ACTIVE_KEYS`：来源显示此 API 范围已配置、但当前有效 Key 为 0；不能推断所有认证方式都不可用。
   - `CONFIGURED`：来源显示 Enabled + API Grant + 至少一个有效 Key；不保证 IP、源服务 Runtime 或网络通路正常。
   - `NOT_APPLICABLE`：Dataset、Dashboard、JOB、USER、TEAM 等尚无此来源管理合同的对象，**不伪造来源状态**。

2. **消费依赖正确性**：从 `Number(sourceIdentity)` 改为 strict decimal + `Number.isSafeInteger` + canonical round-trip 校验，既检查目标 API ID，也检查管理 Consumer ID 与当前 grant；无法无损比对时不选择 ALL grant 或错误 Consumer，更不展示“没有授权 Consumer”假结论。

3. **权限隔离**：仅 `data-service:access` 身份读取来源调用方列表；没权限的人仍能在 F-004 已批准的 Consumption READ 内看到历史影响事实，但不能枚举来源授权列表，也不能断言来源无 Consumer。

4. **Owner 页面与协同草稿**：Canonical Consumption 已知 Consumer、Version Impact Review 逐 Consumer 行显示来源配置核对标签；单人沟通草稿与整份人工工作包附只读来源状态（或显式“未核对”），**不被记录为已联系/已确认/批准**。

5. 全部使用现有 read API 和纯函数，不增后端写入、MyBatis Mapper、XML、SQL 或通知系统。

## 回归与真实 E2E

自动测试：
- 来源 managed Consumer enabled/granted/active key、已禁用、scope NONE/SELECTED 不匹配、0 Key、Project 列表真实查无此 ID；
- FORBIDDEN、UNAVAILABLE、LOADING 始终不等于 NOT_FOUND；
- 错 sourceDomain、USER/TEAM/JOB/DASHBOARD/DATASET 均不推断管理身份；
- ID 为超安全整数、前导零或非正整数时，不错误关联另一个 API 或调用方；SELECTED 授权 API ID 不得被 Number 近似匹配；
- 复制工作包仅带当前已知配置说明，不成为已通知、已同意或审计。

Golden E2E（#336，**仍 PENDING**）：
- 相同消费历史下，在来源配置执行启用/禁用、变更 API Grant、撤销 Key 后刷新，核查 source 状态与 Usage/Subscription 各自正确。
- 不具有 ACCESS 的阅读者、跨 Project 阅读者和来源接口故障者不得获得来源调用方列表、不得看到假“无 Consumer”。
- 超出 JS 安全整数的 Long Source/Consumer ID 必须不发生错误对象定位。
- 记录 deployedCommit、actorRole/Project、准确 SourceIdentity、真实 API/Network/界面与结果。PR CI PASS 不等于 Golden E2E PASS。

## PD-008 未解决的决策

当前 DataServiceConsumer 没有可信 `ConsumerRef → 有权代表确认的 Console actor` 契约；Enabled、API Grant、Active Key 绝不等同于已找到联系人或收到正式 Consumer 回复。正式 ACK/Proposal/Audit Owner 必须经 PD-008 ACCEPTED + APPROVED Feature + 来源 Domain 冻结后才可开发。
