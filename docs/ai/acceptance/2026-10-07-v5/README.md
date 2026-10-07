# V5 验证记录

日期：2026-10-07

范围：[F-013](../../../product/features/F-013-agent-rule-adoption-review.md)、[V5 计划](../../AGENT_V5_PLAN.md)、[交付说明](../../IMPLEMENTATION_V5.md)

## 工程结果

| 检查 | 结果 |
|---|---|
| 完整前端 Jest | 127 suites / 584 tests 通过，新增 26 个行为用例 |
| TypeScript 债务门禁 | 通过，139 条既有诊断，无新增；未扩大基线 |
| 生产前端构建 | 通过，生成前端制品 manifest |
| Product baseline | 通过，15 份 Feature specs |
| Java / 前端依赖边界 | 通过，77 reactor entries / 3152 production Java files；前端 1 条既有走廊 |
| Node architecture / release tests | 14 通过，包括现有 AI 题集与制品门禁 |
| PR CI | 待提交后核验；真实 RA 场景仍全部 PENDING |

日志 `.task-ai-v5-*.log` 为本机忽略文件，不作为已提交的真实环境证据。仅前端代码改变；后端源 API/Policy/CAS 的回归由 PR 全仓 CI 再确认，不把 UI 替身当作数据库集成结果。

## RA 场景与证据边界

| ID | 自动化覆盖 | 真实验收 |
|---|---|---|
| RA01 | ruleComparison：名称/启用/枚举顺序、模板/字段大小写/阈值等差异；Panel 当前条件对照 | PENDING |
| RA02 | Panel 重新生成、删除后再次带入；Hook 重复拒绝与独立不同条件追加 | PENDING |
| RA03 | Hook 校验期间禁止保存、保存开始即锁定、双提交拒绝、冻结规则/原指纹 | PENDING |
| RA04 | Hook 校验后按最新表单拒绝重复、保留其他编辑、拒绝/空返回不追加 | PENDING |
| RA05 | Panel 权限禁用、停止/切目标/HITL；Hook 晚校验失效 | PENDING |
| RA06 | Hook 保存冲突保留编辑、不导航不报成功；正常保存导航 | PENDING：需原源域落库/审计与详情回读 |
| RA07 | Hook 停用追加，保留旧规则/设置，带入无 update 请求；源服务端行为复用既有回归 | PENDING：需确认真实调度、任务版本、执行副作用 |
| RA08 | Comparison 表单来源、条件、启用状态与截断，模板单位沿用原显示 | PENDING：真实模型、专家与用户核对 |

自动化均为确定性工程证据。UI 服务替身不证明真实源域授权、事务写入或模型语义；不虚构采纳率、任务完成率、节省时间或费用。

## 真实操作记录要求

使用已登录的模型测试环境，记录部署 commit、实际模型/参数/Skill 哈希、账号权限/项目、monitorId、源快照指纹、Agent turnId 和候选证据。分别保存生成、带入、人工保存、详情回读、人工运行的结果与源审计，不将按钮点击当作完成。真实密钥使用环境配置，不进入报告或聊天。

先准备一个已有同条件规则与一个不同阈值规则的监控，再按 RA01～RA08 操作；覆盖并发修改定义、权限拒绝、校验延迟、停止/切对象和重新生成。没有对应真实 trace/审计的场景保持 PENDING；同模型/同源条件比较有用性后再决定开放范围。

既有 [G 验收](../../NEXT_PHASE_BACKLOG.md)、[T 验收](../../acceptance/2026-10-05-v3/README.md)、[QP 验收](../2026-10-07-v4/README.md) 未完成项继续保留。当前无已提供的可登录模型环境；Feature 状态保持 IMPLEMENTING。
