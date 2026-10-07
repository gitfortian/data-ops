# V7 验证记录

日期：2026-10-07

范围：[F-015](../../../product/features/F-015-agent-session-follow.md)、[V7 计划](../../AGENT_V7_PLAN.md)、[交付说明](../../IMPLEMENTATION_V7.md)

## 工程结果

| 检查 | 结果 |
|---|---|
| Agent 及上游 Maven test | 通过；Agent 242 项，241 通过、0 失败/错误、1 跳过（AgentToolBudgetMysqlTest，本机无测试数据库，须由 CI 实跑） |
| 前端定向 Jest | 3 suites / 37 tests 通过，覆盖恢复页面与跟随调度 |
| 完整前端 Jest | 130 suites / 641 tests 通过 |
| TypeScript 债务门禁 | 通过，139 条既有诊断，无新增，未扩大基线 |
| 生产前端构建 | 通过，生成前端制品 manifest |
| Product baseline | 通过，17 份 Feature specs |
| Java / 前端依赖边界 | 通过，77 reactor entries / 3153 production Java files，前端 1 条既有走廊 |
| Node architecture / release tests | 14 项通过，包含 AI 离线题集与发行制品门禁 |
| PR CI | 合并前必须通过最终 head 的 Product Guard、后端/前端/发行制品与 Architecture Gate，真实场景不因检查通过改为 PASS |

本地 `.task-ai-v7-*.log` 为忽略文件，不是已提交的真实环境证据。Maven 通过 SDK 与本机 HTTP 模型替身验证；数据库跳过不算通过，真实模型语义不由工程替身证明。

## AF 场景与证据

| ID | 自动化覆盖 | 真实验收 |
|---|---|---|
| AF01 | Hook 首次调度/有界读取、活动变化不重置预算；页面自动读取无 submit/全文流订阅 | PENDING |
| AF02 | Hook 完成/失败/取消/中断/待答/新轮只触发一次恢复；页面加载最终历史与原澄清/toolResults | PENDING |
| AF03 | Hook 慢请求不重叠、20 次上限、隐藏暂停与可见恢复保留次数、显式刷新重新建立预算 | PENDING |
| AF04 | Hook 网络/身份/投影失败停止调度；页面保留历史、锁定发送、手动刷新 | PENDING |
| AF05 | Hook 切换/禁用/卸载/刷新晚响应失效；页面停止晚应答不覆盖新选择，原 V6 A/B/新建回归 | PENDING |
| AF06 | AgentTurnStopTest 精确排队/运行、旧轮/终态请求不影响后来句柄、WAITING 拒绝；页面固定原 ID | PENDING |
| AF07 | StopTest 轮次及会话的用户/项目、缺登录/会话拒绝；20 组取消与认领并发；SDK HTTP 替身 E2E 指定轮次停止 | PENDING |
| AF08 | 页面停止处理中禁用、未确认不报停止/刷新核对、权限禁用；无本机运行句柄不猜终态；完整契约保留原查询/候选/预算/续播 | PENDING |

并发测试使用真实 Registry 和原子状态替身，证明本机调度顺序；多节点句柄路由和真实数据库/SDK 并发不由该替身代替。隐藏/卸载控制的是客户端读取，不能证明模型已取消。

## 真实操作记录

准备已登录模型服务、隔离的两个项目、长任务/排队任务与可触发澄清的会话。记录部署 commit、账号权限/项目、模型/参数/Skill 哈希、sessionId/turnId、前后状态、工具预算及源读取审计。

按 AF01～AF08 重开活动会话、隐藏/重新可见、完成/澄清恢复、达到检查上限、读取断网/撤权、切换/新建/卸载、停止与并发完成/认领/新轮提交。模拟停止应答丢失，确认刷新读取真实结果；重复旧轮停止不能影响后来新轮。保留页面、服务响应、trace 与源审计，业务专家核对真实回答。密钥仅用环境变量。

当前无可登录模型环境，所有真实 AF 均 PENDING；既有 [G](../../NEXT_PHASE_BACKLOG.md)、[T](../2026-10-05-v3/README.md)、[QP](../2026-10-07-v4/README.md)、[RA](../2026-10-07-v5/README.md)、[SC](../2026-10-07-v6/README.md) 待办保留，F-009～F-015 不标 SHIPPED。
