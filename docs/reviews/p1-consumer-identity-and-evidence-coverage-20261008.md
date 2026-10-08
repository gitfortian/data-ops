# P1 · Consumer 可信确认主体与取证覆盖审查（2026-10-08）

> 类别：Evidence / Review，不是 Product Truth、不是新的业务需求。
> 基线：data-ops main `482bc83d39ae4865cc779b2eb84dce29deedac46`（PR #399 merged）。
> 依据：PD-002 ACCEPTED / F-004 APPROVED；正式跨域变更确认仍为 PD-008 PROPOSED；PD-007 PROPOSED。
> 入口：#180 P1 E3、#336 Golden E2E。

## 1. 本轮源码证据（已逐文件读取）

| 模块 / 实际类 | 当前可证明的能力 | 不可由当前信息推断的事实 |
| --- | --- | --- |
| `consumption/relationship/ConsumerImpactController` | `GET /api/v1/consumption/impact`，`@ProjectScope(PROJECT_REQUIRED)`，资产 READ 权限，接受 productKey 与 usageLimit | 查询权限不等于任何 Consumer 的代确认权；列表可能只是可见窗口 |
| `consumption/relationship/ConsumerRef` | `consumerType:sourceDomain:sourceIdentity` 是稳定身份；`displayHint` 仅展示 | 无 verified contact / owning human / delegation |
| `consumption/relationship/ConsumerImpactService` | 取 ACTIVE Subscription + 归一化成功 Usage，按精确 `SourceVersionRef.identity` 分组；source audit/normalization 与 local usage.list 分别受 1..200 的窗口约束 | 不等于完整历史；达到上限不等于故障；Gap/Unavailable 与 EMPTY 分离 |
| `dataservice/access/DataServiceConsumer` / `DataServiceConsumerManager` | 外部调用方有 `id/projectId/name/description/accessScope/enabled/rateLimit`；Key / API 授权独立维护 | 该类**未存可信 Console user owner**，API Key 不是合法人工确认主体 |
| `dashboard/dao/model/DashboardPO` | Dashboard 有 `id/projectId/name`、精确版本/发布时间 | 没有可证明的 dashboard owner user ID 或责任人代理权限 |
| `sync/offline/domain/OfflineJobDefinition` 与 `sync/realtime/dao/model/RealtimeJobDefinitionPO` | 定义具有 `id/projectId`、发布版本、运行状态/时间 | 这些模型没有稳定可信的人工 Owner 字段；不能用任务创建人或 Project Owner 猜测代理确认权 |
| `boot/project/YakSecurityProjectAccessGuard` | 使用现有 Security 的 Project Owner / Member 列表判断 Project 可见，外部错误防枚举 | Member 或 Project Owner 身份不能单独证明其有权代表某个 JOB/DASHBOARD/API Consumer 承诺兼容 |
| `approval/api/ApprovalApi` 与 `ApprovalFlowHandler` | 正式审批需要 flowCode + 业务 handler，同事务 callback；状态与真实业务变更分开 | Approval Step 不能取代任意 Consumer 意见收集；Flow APPROVED 不代表所有 Consumer 已回复 |
| `audit/BusinessAuditService` | 正式业务审计入口，支持操作 handle 与鉴权留痕 | 剪贴板复制或“拟变更内容”未产生已送达/已确认的可信审计事实 |

**结论：** 目前存在统一 ConsumerRef 身份和 Project 可见性，但未发现一条经过批准且跨 Consumer Type 都成立的“ConsumerRef → 可信应答 Console 账号/委托/收件地址”的读取与授权契约。因此 PD-008 的身份和回执不能靠 UI 自动填充、猜测一个 Owner 或简单新增确认接口完成。

## 2. 当前可以实施的正确性缺口（PD-002 / F-004 内）

对一个来源对象拉取最近 `usageLimit` 记录时，`ConsumerImpactService.synchronizeSource` 旧实现用 `results.size() < limit` 断言“来源可用”；如果恰好返回 200 条成功/忽略记录，就错误地把 Usage Provider 标成 `UNAVAILABLE`。同样 `usage.list(..., limit)` 返回 `limit` 行却没有明确提示归一化窗口也已截断。

本轮实现：
1. 将「来源读取失败/归一化 GAP 或 UNAVAILABLE」与「窗口达到上限」拆分；满窗仍返回真实 `READY/EMPTY` 证据状态，**不假装完整**。
2. 由 API 额外投影非持久 `coverage`：请求上限、来源记录数、归一化已知数、两种满窗、归一化缺口数、是否来源读取失败。
3. 在 Canonical Consumption 已知影响/版本协同草稿/工作清单中显示每一个取证缺口，保留原始精确 BIGINT 和 Consumer 证据，不新建 Usage Truth。
4. 后端和前端新增独立用例：满窗正常、归一化满窗、GAP、真实审计故障、旧版 DTO 无 coverage、同显示版本不同 ID。

以上是 existing F-004 观察窗口**真实性与可用性**修正；没有新增 consumer confirmation、通知、数据库、审批或来源发布门禁。

## 3. PD-008 决策前建议的单向技术审查包

**身份：** 必须逐 Consumer Type 指定 owning business reader、可信 actor mapping、Project ownership 校验和可委托行为；不允许沿用 `ConsumerRef.displayHint`，不允许用 API Key 直接代表签署人。

**提案：** 先让 Dataset/Data Service 各自决定不可变“拟变更 source revision/definition”快照来源，明确提案指纹和旧版本失效 CAS；不要在 Consumption 重新定义来源版本。

**确认：** 选择单独 Consumer Response Truth Owner 与 audit retention，或明确不首期做确认；任何 ACK 必须绑定 actor、Project、ConsumerRef、proposal fingerprint、时间和幂等键。Approval 只管正式决定，不兼做多人逐一意见。

**通知：** 消费者没有 verified contact 时仅保留人工沟通；没有 Delivery Provider 成功回执则不显示“已发送/已读”。

**状态：** PD-008 仍 `PROPOSED / NOT_STARTED`、PD-007 仍 `PROPOSED`。除非批准，不编写响应数据表、消息接口、自动迁移或发布阻断逻辑。

## 4. Golden E2E 待验收

需在真实部署验证 Dataset 与 Data Service：Source 审计窗口恰好满 200 但 Provider 可读，Usage.READY 与 coverage.windowLimitReached=true 同时成立；模拟 GAP、Provider 失联时 Usage.UNAVAILABLE 与已知历史成功使用仍存在；同版本显示名但不同 `SourceVersion.identity` 的 BIGINT 证据不混用；跨 Project/无权角色不能读取其它 Project 的 Consumer。保存 deployedCommit/角色/Project/精确 source IDs/实际 API UI logs/结果。

**当前没有真实部署 E2E 结果，保持 PENDING**；PR CI 和单测不能替代。
