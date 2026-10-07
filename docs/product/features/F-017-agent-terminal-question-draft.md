# F-017 — 终态说明与重新提问准备

Status: IMPLEMENTING
Approval: 用户于 2026-10-07 要求多版本规划后依次完成，授权 V9～V11 顺序实施。
Product basis: ACCEPTED PD-001/PD-002；IMPLEMENTING F-014/F-015/F-016。

## 用户、问题与结果

治理及问数用户在停止/失败/中断后，需要知道真实结果及下一步。现有直接重试用页面全局最后问题，恢复后不可用且不证明对应轮次。提供最新已确认终态的固定说明与原问题草稿；用户核对范围、补充背景后明确发送新的 START，不原地续跑终态旧轮。

## 旅程、归属与复用

原会话 → 本人/当前项目校验 → 最新 continuation → 填入原问题 → 编辑 → 原提交新轮。turn 拥有生命周期与原提交，StateStore 拥有消息/pending，源域拥有业务事实。Query 生产只读投影，原页面消费；原 ChatService 执行新发送。复用原引用、RBAC、预算、HITL、trace 与回链，不增加第二份业务真相。

## 冻结行为

- 最新终态只投影白名单错误码 TIMEOUT / USER_ERROR / PROVIDER_ERROR / GUARD_REJECTED / GENERIC，未知映为 GENERIC；固定保守说明，不回显异常、不推断具体根因。推理完成不代表质量通过。
- START 草稿来自原持久化 message；原 StateStore 中若有该 turnId USER 引用，必须唯一且一致。读取失败不提供草稿。RESUME 仅从唯一精确原 USER 引用获取问题，绝不使用工具反馈/最近问题/文本猜配。
- 草稿最多 8000 UTF-16 code units，不截断，超限/缺失/冲突返回固定不可用说明；不改变原消息通用上限。活动/待答不读草稿；损坏输入阻止继续。
- 填入前重读最新上下文；必须仍是所见同一终态、同一目标。非空输入不覆盖；切换/卸载/新建忽略晚响应。填写零模型和源工具调用。
- 新发送可携带 expectedLatestTurnId；原提交互斥区核对最新 ID/归属/终态及与输入的原目标相等，否则拒绝。普通发送保持兼容，HITL 不携带该字段。仅进程内互斥，不承诺多节点强并发。
- 移除历史气泡对全局问题的直接重发；提交双击同步保护，未确认保留输入并要求刷新。停止仍以原精确命令和实际读取为准。

无新配置、表、状态机、源域能力、导航或框架迁移。模型配置入口复用原权限；未授权用户只得到核对/联系管理员提示。

## 领域与架构检查

Aggregate 为原 Session/Turn/Input；生命周期不变，草稿不成为消息 truth。Requirement Gap 已由本获批 Feature 和更新的 Requirements/Domain 收敛。目标为 conversation.query/read-side、原 command 及原页面；稳定入口仍是 AgentSessionQueryService / AgentChatService。无新依赖边、SDK 走廊或 cycle，DEPENDENCIES 和护栏无需扩展。行为测试保护原消息唯一性、陈旧来源、互斥与降级；已知多节点互斥与真实环境验收仍待独立完成。

## 验收与成功信号

[V9 规划](../../ai/AGENT_V9_PLAN.md) NR01～NR10 与 [连续路线](../../ai/AGENT_V9_V11_ROADMAP.md)。自动化保护只读、草稿证据、状态/目标竞态、权限与旧链路；真实登录/模型/源审计独立待验收，旧待办不关闭。成功信号是用户能区分结束原因并核对后完成有用的新任务，当前无真实基线。无阻断产品问题；真实环境缺失只阻断真实验收，按用户指令先工程交付，Feature 不标 SHIPPED。
