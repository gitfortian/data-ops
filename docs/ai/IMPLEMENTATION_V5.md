# Agent 第五版交付说明

日期：2026-10-07

实施契约：[F-013](../product/features/F-013-agent-rule-adoption-review.md)（IMPLEMENTING）

计划：[第五版计划](./AGENT_V5_PLAN.md)

验证：[第五版记录](./acceptance/2026-10-07-v5/README.md)

## 用户可以做什么

在现有质量监控编辑器的 AI 建议卡里，查看每条候选与当前表单同模板、同字段规则的对照。对照包括未保存编辑，显示条件和启用状态；多于五条时明确提示还有多少条需回编辑器核对。候选依据已保存定义，表单对照不冒充历史或实时源事实。

相同条件的规则不能通过 AI 重复带入，重新生成不会绕过检查；名称不同、启用状态不同、枚举顺序不同不视为新条件。不同阈值可以作为独立规则追加，不修改旧规则。删除带入条目后可再次选择。字段大小写、不同模板、别名和条件逻辑不做等价推断，普通手工配置能力保持原样。

每次带入仍在源域重新校验，返回后再次比较最新表单；候选固定停用。校验期间保存按钮不可用，保存期间不能带入，快速重复点击也受同一互斥约束；停止或切换对象会使晚结果失效。校验失败/空结果不追加，保存冲突保留未保存编辑，不显示成功或跳转。

只有点击原“保存配置”并获服务器成功响应才完成保存；继续携带原服务器定义指纹，通过 Quality 原 Policy/事务/审计。运行、调度、通知和任务版本仍由源域拥有；Agent 无业务写工具。保存成功回原监控详情，规则启用和运行仍需人工独立操作。

## 实现与归属

- `GovernanceSuggestionPanel` 复用现有轮次、HITL、SSE、取消和候选协议，接入当前表单对照，阻止重复采纳；Asset 描述路径继续保留。
- `QualityRuleComparison` 与 `services/data-quality/ruleComparison` 提供临时表单呈现及保守条件比较，不持久化、不查询源表。
- 原 `useMonitorEditorPage` 拥有采纳/保存的本地互斥，保存前冻结规则列表，校验后检查最新表单及目标有效性。`MonitorEditorPage` 复用原权限、模板单位与保存入口。
- 仅更新 Agent/Quality Domain、Requirements 的相关行为；无新 Java 依赖、REST、表、状态机、导航、共享框架或后台工作流。继续使用仓库 AgentScope Java 2.0.2，未做框架迁移或性能收益声明。

Domain Impact Analysis：Aggregate 为现有 Monitor/Rules/Settings 与 Agent 候选；不改源域生命周期，Domain Gap 为 no；新增表达是原编辑器临时投影。Architecture / Dependency Impact：稳定入口是原编辑器、Quality 快照/校验/保存 API；runtime truth owner 不变，依赖方向和既有 corridor 不变，无新循环或护栏白名单。

Domain Compliance Report：实现 F-013 的核对、停用追加、重复拒绝与保存互斥，测试保护取消、最新表单、保存冲突和原定义指纹。已知缺口仍为真实模型语义、登录态与源审计；不存在长期采纳统计，未声明用户收益。

## 验收状态

本版工程检查与 CI 结果在验证记录逐项登记。真实 RA01～RA08，以及既有 G1～G12、T1～T9、QP01～QP16 的未验收项继续 PENDING，不以替身测试或合并替代。遵循用户“先完成代码与 CI，真实模型验收单独记录待完成”的要求，F-009～F-013 均不因工程交付标记 SHIPPED。
