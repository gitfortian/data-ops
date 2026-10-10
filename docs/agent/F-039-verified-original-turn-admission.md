# F-039 / #495 — 冻结来源清单与预留额度后的原 turn 入队

**状态：独立工程适配与隔离验收，生产未装配**（2026-10-10）。对应 Issue #495、epic #493；依赖已合并 PR #499-#502。

## 实施内容

本阶段在既有 **AgentScope Java 2.0.3** versioned CAS Task Ledger 中增加来源的完整 **SourceSemanticScope**（项目、数据源、库、schema、采集 ID、全部显式选定表/列）和按确定性 planner 生成的 `frozenChunks`。构造/反序列化时比对任务和分片指纹、项目与完整 ID 列表，范围不足或受破坏时 fail-closed。旧记录仍可读取，但没有 `sessionId` / 完整选源清单的任务不能用新适配器继续执行。真实 CI MySQL Store 关闭/重开测试校验嵌套 Manifest 正确还原，不以 JUnit 内存 store 推论持久化成功。

新增普通类 `conversation.SourceSemanticVerifiedTurnAdmission`（**没有 @Service、Controller 或 Agent Tool 注册**）：在额度扣减前要求当前登录用户/项目与现有会话归属一致，向可信 Source/Metadata owner 端口重新核实 Datasource ACL、目录完整性和持久冻结选源清单，再读可信 workspace 的 `plans/PLAN.md` 原字节比对用户批准 hash，选择 CAS 清单中的**下一片**；服务端在 16KiB 有界预算内将白名单表/列标识 JSON 编成只读、仅生成候选证据的原 turn 消息，先 CAS reserve 一个 turnId/调用额度，再调用原 `AgentChatService.enqueueReservedSourceSemanticTurn` 将这一 turnId、真实 user/project/session 写入同一 `yak_agent_turn` 队列并 kick 原 Dispatcher。方法使用原普通对话提交 stripe、session owner 校验、同一 `TurnInputCodec`、单飞检查，不创建第二个执行器。

**不确定入队/超时不得自动重试或退款**：可能发生“SDK CAS reservation 已提交，但原 turn insert 成功后响应丢失”。返回 `[F039_RESERVED_ADMISSION_OUTCOME_UNCERTAIN]`，持久保留 activeTurnId；使用 #502 的 OriginalTurnReconciler 对照原 `AgentTurnRepository.findByTurnId` 明确 `ORIGINAL_TURN_MISSING` 或 QUEUED/RUNNING/WAITING_INPUT，绝不直接推断成功、创建新 turn 或重复消费预算。部分失败需要原 owner 的运维核对/调和后才能重新推进，不可悄悄退款。

## 仍未满足的生产门禁

这是技术集成，不是 #495 E2E 完工。**这段代码未装配为用户可调用入口**，因仍缺 Owner 批准的 Metadata+Datasource user ACL/采集覆盖联查与授权 `AuthorizedSnapshot` 实现、客户可审阅的 PLAN.md 持久 workspace、多节点安全 turn 入队/取消栅栏、可信不可变 Artifact Store 和成果回执、PlanMode SDK 原生 HITL 确认与应用层授权绑定、后台串行片段调度、累计 Token/时间预算及 UI/SSE E2E。

尤其是两个事务真相（SDK StateStore CAS 与 `yak_agent_turn` INSERT）没有跨库原子提交：**reserve→insert 的失败保守中断可避免重复；但取消在 CAS 与 INSERT 之间抢先发生时，只靠两次状态读取不能保证原 worker 在取消之后绝不调用模型**。在打开实际入口之前必须实施数据库 outbox / 执行器在首次调用模型和每次工具调用前校验当前 task CAS fence，并以多节点竞态测试确认。此次**不**以纯 JUnit Mock 测试冒充生产多节点一致性。

Owner 核心约束不变：Metadata 只负责来源元数据事实，DataSource owner 负责当前用户真实授权；原 AgentTurn/Executor/Registry 拥有推理和取消真相，AgentStateStore 拥有消息/pending；Agent 仅保存辅助有限任务和分析证据。不会自动发布/创建模型、指标、标准，或执行 SQL/Python/Shell。PD-009 仍 PROPOSED、F-039 仍 DRAFT，正常 Agent 工程 PR 可手动合并但不构成业务能力批准。
