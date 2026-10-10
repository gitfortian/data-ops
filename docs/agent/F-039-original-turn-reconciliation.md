# F-039 / #495 — Agent 长任务与原 AgentTurn 状态协调

**Status:** ENGINEERING_INTEGRATION / NOT EXPOSED TO PRODUCTION UI (2026-10-10)  
**Depends:** merged #499 SDK Plan research, #500 immutable chunk/CAS budget, #501 Metadata coverage and materialized plan review

## 核心落地

- `SourceSemanticTaskState.sessionId` 冻结原 Agent 会话身份。上次内核建立的旧 StateStore 记录该字段缺失（null），允许读取和原单测继续运行，但 **不允许进入分片执行适配器**，严禁按最新会话或消息时间猜测绑定。
- `SourceSemanticTaskLedger.create(taskId, ownerId, sessionId, scope, ...)` 新任务在首次 CAS 写入时就固定会话；旧构造保持向后兼容，属于只读/未绑定内核路径。
- `conversation.SourceSemanticOriginalTurnReconciler` 消费真实 `AgentTurnRepository.findByTurnId` 作为**原轮次事实**。QUEUED/RUNNING/WAITING_INPUT 一律不冒充完成，WAITING_INPUT 不复制 SDK pending。FAILED/CANCELLED/INTERRUPTED 走 CAS 标记任务 INTERRUPTED（原 turn 真相保留），恢复须另建新 turn、重新扣减预算，不退款。
- 原 turn COMPLETED **不足以标记片段完成**，必须同时具备新鲜源 ACL+Metadata 完整性投影、真实 PLAN.md 原字节 hash 以及由可信持久化 Artifact owner 提供的「原 turn / chunk / session / project / user / frozen scope / reviewed plan / immutable digest」八字段回执。任一缺失/变更拒绝推进；收到有效回执才调用已实现的 `TaskLedger.completeTurn` CAS。
- `inspect` 是刷新/断线恢复的有限只读进度视图，包含任务状态 + 只读原轮状态；找不到原轮只报 `ORIGINAL_TURN_MISSING`，不自动推断队列已经插入或进程崩溃。停止时先关闭 task CAS 额度入口，再通过注入的 **原 AgentChatService.cancelTurn** 精确停止冻结 turnId；取消后任何迟到结果都不能使任务重新完成。
- 新增完整正反例测试：等待态、需要正式回执、权限/来源/计划文件漂移、跨会话/跨项目、取消竞态、FAILED/INTERRUPTED 与缺失原轮安全降级。

## 仍未接通/未证明

本组件是 **普通 Java 对象，未注册新 @Service、Controller 或 Agent 工具**。FreshSourceEvidence、VerifiedResultReceipts、ExactTurnStop 三个可信端口必须由 Owner 认可的实现注入，**不能**拿模型字符串、浏览器回执、未经 DataSource ACL 的项目目录投影或仅 LLM 完成帧伪造结果。新任务的 `create` 必须在已授权 Source/Session 下进行，不能直接将任意 HTTP sessionId 绑定为可信事实。

最重要的剩余块：持久化完整来源选择/采集版本及当前用户 DataSource ACL、官方 Artifact Store、PlanMode HITL 与持久 workspace、多节点原 turn **reserve→queue** 可靠入队及恢复协调、实际后台多片编排、累计 Token/时间、UI/SSE、SI E2E。原 SDK StateStore 不能代替这些持久化 owner；暂不接入可执行生产入口。合并本 PR 不代表 #495 全量验收，也不改变 PD-009 PROPOSED、F-039 DRAFT 状态。
