# Ticket 09：异步/调度链 AuditContext 传播工具化（P2）

**对应需求：** 全量审计方案 M2 | **优先级：** P2 | **阻塞于：** 02 | **模块：** data-ops-business-audit + 调度/工作流/同步侧

**What to build：** 目前全仓只有 2 处做跨线程传递（`WorkflowExecutionAuditBridge.java:84`、`OfflineExecutionCoordinator.java:411`），其余异步执行（各类线程池、事件监听、调度触发的二级任务）审计链断裂——操作事件丢失或 actor 退化 SYSTEM。本票把传递做成 SDK 能力并接入主要执行器。

**机制：**
- `AuditContext` 增补 capture/restore API：`AuditScope AuditContext.capture()`（快照 operation_id/actor/project）+ try-with-resources `scope.attach()`（工作线程内恢复）；语义对齐既有 `resume()` 契约（`BusinessAuditService.java:4`）。
- 提供 `AuditExecutors.wrap(Executor)` / `Runnable wrap(Runnable)` 薄工具，执行器注册点逐个替换（只包线程池，不重构调度）。
- 接入清单（开工先盘点实际存在跨线程审计需求的点）：workflow 执行器、offline 二级任务、同步任务事件监听、审批回调链。
- 调度触发型操作的 actor 语义显式化：确认 `JdbcBusinessAuditService.loadCarrier`（`:428`）对 actor 空值 → SYSTEM 的推断有测试锁定（调度告警在内存、无 UI，属项目记忆既有事实，不新建入口）。

**验收清单**
- [ ] capture/attach 单测：跨线程后 event 挂到同一 operation_id、actor/project 不丢
- [ ] 至少 workflow + offline 两条真实执行链改走 wrap 工具，行为不回归
- [ ] 审计详情时间线（`GET /operations/{operationId}`，`AuditQueryController.java:55`）可见子线程事件
- **验证边界：** 执行链真机验证需 Link-Up/调度环境，先探活 127.0.0.1:18080（项目记忆）。
