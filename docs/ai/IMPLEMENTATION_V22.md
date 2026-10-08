# V22 指标发布前版本变更解释

2026-10-08，用户确认 V22 规划后授权实施。基线为 main `0088fb8c`，前版 #354 已手动合并。当前合同 [F-032](../product/features/F-032-metric-change-review.md)，沿用 ACCEPTED PD-002/PD-003；真实模型与测试环境验收仍延期。

User：指标设计与审核人员。Problem：差异、验证和已知引用需手工拼接，难以明确本次变更及核对范围。Capability：固定当前已保存草稿与 active publication，提供只读变更说明和人工检查步骤。Journey：保存 → 原详情准备 → 明确生成 → 核对原事实/覆盖缺口 → 原编辑、验证及显式发布。Expected Outcome：理解变更与下一步，不把生成完成或旧验证当作发布获准；真实效果待评审。

Truth Owner：Metric 持版本、验证、发布及声明引用，Lineage/Consumption 各持事实，turn/StateStore 持生命周期及消息。Producer 是 Metric-owned 授权只读投影与原 SDK，Consumer 是原详情及原会话；复用原项目/RBAC、Skill、预算、精确停止、历史唯一关联与专业命令，无第二业务真相。

## 使用

1. 管理员核对 [metric-change-review](skills/metric-change-review/README.md) 后，经原 Skill 管理登记方法包；部署不自动启用。
2. 在原指标详情核对“AI 指标版本变更核对”的发布版本、已保存草稿和确定性差异，可填写最多512字核对重点，明确点击“解释版本变更”。未保存编辑不参与比较。
3. 结果展示1–5条 AI 说明、0–5条人工检查及0–3条待确认项，逐项带源事实。保持原会话查看/停止/刷新；生成或未知终态期间不替换输入/来源。回看完成历史前重读源指纹，漂移撤下结果并允许显式重新准备。
4. 回原编辑器、治理面板和影响页面完成后续步骤；本场景没有采纳、保存、验证、发布或取数动作。原会话卡只回看生成时依据，不自动源读取或恢复表单。

## 已接入与明确缺口

- 两份白名单不可变版本事实与确定性差异；Metric READ 先于源读取。核对 active pointer、publication ledger、版本 ID/digest，准备/交付再次读取。没有发布基准或无白名单差异时不调用模型。
- 该精确草稿版本**最新一次** Validation：独立有界读取，不拿旧通过替代最新失败；保留身份、provider/result、时间和最多20条问题。损坏/超限/不可读不伪装验证通过。
- 数据库有界读取21条声明记录，只展示前20条并标覆盖限制、未知引用版本。声明引用不证明实际调用或下游访问权。
- 完整 readiness、当前依赖健康及血缘尚无本场景的安全有界投影，明确 UNAVAILABLE，并保留原专业页面。observed provider 未注册为 NOT_APPLICABLE，已注册但未接入有界读取为 UNAVAILABLE。原完整门禁保持原规则及入口，不被本场景替代。
- 合并读取不是跨域原子快照；读取时间不进入指纹，证据身份/结果、版本及发布事件进入指纹。单次上下文≤24000，交付≤60000，不截断公式；超限明确拒绝。

## 工程边界

Domain Impact：沿用 Metric/Version/Validation/Publication、Session/Turn；仅新只读投影与说明协议，无生命周期改变。Architecture/Dependency Impact：`runtime → toolset → gateway → metric.api`，Metric catalog 经原 repository；新增 GET 准备上下文，复用原 submit/events/cancel/history，不新增 Agent 写命令或依赖反向边。

独立场景 `METRIC_CHANGE_REVIEW` / Skill `metric-change-review` / 交付标记 `yak-metric-change-review`。完整目标包含版本对、发布事件、准备指纹及核对重点；授权/漂移/事实引用与 Skill 活版本复核后才保存交付至同一 SDK 消息。四个既有场景、原 F-025 采纳合同和 F-031 只读回看保持。

验证结果见 [V22 验证记录](acceptance/2026-10-08-v22/README.md)。真实模型、登录态、源审计/发布与完整 J2、专家语义及人工收益仍 PENDING；Feature 不标 SHIPPED。
