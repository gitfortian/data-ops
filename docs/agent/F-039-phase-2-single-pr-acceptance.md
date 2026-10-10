# #495 / F-039 2/5 — 单 PR 集中交付门禁

Decision 2026-10-10：**从现有已合并 #499-#503 继续，余量只保留同一个阶段 PR**。此处是 PR 内逐步实施的工作说明，不等同于完整上线或验收报告。直到所有项目级真实 E2E 证据完成，不能关闭 #495。

阶段剩余不可拆散的验收矩阵：

1. **真实来源入口**：元数据/来源页面选择 1–20 张表/至多 500 列，业务背景与确认范围，未采集/部分采集真实错误信息，任务回链。
2. **权限与来源**：DataSource owner 的当前登录用户 READ 授权、Metadata Project-scoped 同源结构/采集时点和逐列指纹，撤权或漂移前拒绝创建下一片，防跨项目/跨会话。
3. **人工 PlanMode**：官方 Harness PlanMode 2.0.3 确认语义 + 可信 PLAN.md 持久 workspace，修改/拒绝/恢复均从文件回读和预算复核。
4. **可靠任务协调**：任务 CAS + 原 yak_agent_turn QUEUED 的关联、跨节点取消→入队竞态 executor/tool fence、失败及重复调用不退款不伪完成、服务器连续多片投递和真实产物回执。
5. **预算与恢复**：跨 turn/HITL/重试 tool+token+time 总预算、暂停/取消/排队/重启/晚回执及 SSE 恢复；INTERRUPTED 另建新 turn 并核来源。
6. **端到端证据**：原页面→真实 API→后端长任务→多片产物→刷新恢复→后续 #496 候选交接，权限/中断/超额负例，在真实 MySQL 和模型环境按 SI 测试记录；模型实测未覆盖不得声称已验证。

本阶段新增 `SourceSemanticMetadataEvidenceBinder` 以 Metadata Owner 的查询 API 生成 Agent immutable source scope：包含逐列 hash，不再仅列名，并在新任务/恢复时通过注入的 Datasource 权限端口做有效性重核。该端口不提供生产授权实现，因此**不能成为上线放行条件**。

核心 Truth 保持：Metadata 是物理来源事实 owner；DataSource 是用户访问控制 owner；AgentTurn/SDK StateStore 原有执行、pending 为真相，F-039 task CAS 仅辅助关联。无 SQL/Python/数据行读取、无 Semantic 写入。

当前 PD-009 PROPOSED / F-039 DRAFT 的业务合同尚未获批准；可并入工程代码不代表产品业务能力获批准。
