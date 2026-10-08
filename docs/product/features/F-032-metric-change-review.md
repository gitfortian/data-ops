# F-032 — 指标发布前版本变更解释

Status: IMPLEMENTING
Approval: 用户于 2026-10-08 确认 V22 规划并要求“好的 开始实施”。
Product basis: ACCEPTED PD-002/PD-003；APPROVED F-005；IMPLEMENTING F-020/F-025/F-027/F-031。

## 用户、问题与结果

User：指标设计与审核人员。Problem：版本差异、验证与已知引用分散，用户仍需手工拼接本次变更的含义和待核查项。Capability：在原指标详情，对已保存当前草稿与当前 active publication 的固定版本对生成只读、可回源的变更说明。Journey：保存草稿 → 原详情准备版本对 → 明确生成 → 核对差异/验证/覆盖缺口 → 原编辑、验证、显式发布。Expected Outcome：理解变更和真实证据范围，不把 AI 结束当作发布获准；收益待真实试点。

Truth Owner：Metric 持定义、版本、Validation、Publication、声明引用，Lineage/Consumption 持各自事实；turn/StateStore 持生命周期与消息，Skill 管理持方法版本。Producer：Metric-owned 授权、有界只读投影与原 SDK；Consumer：原指标详情和原会话只读交付。复用项目/RBAC、快照 digest、scoped Skill、原预算/停止/历史、原专业命令；无新业务表、导航、状态机或 Truth Owner。

## 固定合同

- 新场景 METRIC_CHANGE_REVIEW，固定 Skill `metric-change-review`。一个当前项目指标、当前已保存版本与 active publication 精确版本/event identity；核对重点≤512 UTF-16 units。目标包含准备时上下文指纹，用户文本不能改变目标或权限。无 active publication、版本相同或白名单事实无变化时不调用模型；未保存表单不在比较范围，详情仅说明已保存事实。
- Metric READ 先于任何源读取；实际执行线程重判权。复用 F-025/F-027 两份不可变白名单事实（每份快照≤65536，单事实≤4096、事实值总计≤16000），确定性字段差异由服务端计算。新上下文序列化总量≤24000；公式不截断。目标版本、事件、digest、证据指纹和 Skill 活版本在生成准备及交付前再次核对；漂移拒绝交付并要求显式重读。读取时点不进入一致性指纹，不承诺跨域原子快照。
- Validation 只读该精确当前版本的最新一次证据，保留 evidence identity、provider/result、digest、checkedAt；不拿历史通过冒充当前通过。最多20条问题、每条≤512，原始问题载荷>32768时不可用；非 READY provider 只投影固定说明。读取不触发 Validation。
- 声明引用按稳定记录顺序在数据库最多读取21条，展示前20条并明确覆盖限制；仅声明记录 ID、类型、消费对象 ID、引用版本及记录时间，不展开下游定义或推断消费权限。未知版本仍未知。
- 当前完整 readiness / dependency / lineage / observed 接口尚无本场景的安全有界投影时，不直接送模型：分别保留 UNAVAILABLE 和原专业页面入口。未注册 observed provider 时为 NOT_APPLICABLE，注册但未接入安全有界读取为 UNAVAILABLE。这些是首版明确缺口，不是假空，也不代表门禁通过。原完整治理面板仍可显式读取真实 readiness，本场景不修改发布规则。
- 输出为一份只读核对结果：1–5条变更说明、0–5条人工检查步骤、0–3条待确认问题，各≤512；说明/检查每项引用1–4个本轮事实 key，服务端重新装配标签和值。交付 JSON≤60000，重复大事实使交付超限时拒绝而不截断；历史 parser 上限64000含外层文案。非 READY 覆盖仅有固定说明，不提供异常原文/SQL/连接配置/数据行。引用合法不证明自然语言正确；页面明确 AI 解读需人工核对，不输出发布授权。
- 无采纳、复制进表单、保存、验证、发布、取数或修复工具；下一步仍原专业入口。原会话只呈现最新 COMPLETED、唯一关联、完整新目标一致的授权历史；SSE不升级为结果，历史结果标注生成时依据且未核验当前有效性。
- 输入/对象/项目/权限变化隔离旧回调；停止/断线/未知终态复用 F-020，活动或未确认轮不重复生成。待确认项为完成轮问题清单，补充后显式新轮，不增加场景 HITL。

## 领域、架构与依赖影响

Aggregate：原 Metric/MetricVersion/Validation/Publication、原 Session/Turn；生命周期及 owning truth 不变。Requirement/Domain Gap：新增只读版本对上下文及说明协议，由本 Feature 收敛。依赖沿用 `runtime → toolset → gateway → metric.api`；Metric 内部 catalog 投影经原 repository 读精确版本、最新验证与有界引用，不反向依赖 Agent。稳定入口仍原 AgentChatService/Runtime 与 Metric-owned query adapter。无新跨域依赖边、SDK 白名单或循环。

## 验收

沿用 [V22 计划 MR01–MR12](../../ai/AGENT_V22_PLAN.md#7-计划验收矩阵)：权限先行、三类指标版本对、缺基准/无差异零模型、精确验证与有界引用、漂移/撤权/超限/坏引用、停止/历史与只读零业务命令、原流程不退化。自动化、类型/构建、架构/Product Guard 属工程证据；真实模型/登录用户/源审计/完整 J2 与人工收益按用户要求继续 PENDING，不标 SHIPPED。无需用户提供环境即可先完成工程交付。
