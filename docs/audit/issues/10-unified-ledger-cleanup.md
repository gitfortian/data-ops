# Ticket 10：一本账收尾——oplog 桥接 + 前端清理 + RUNNING 巡检（P2）

**对应需求：** 全量审计方案 M3 | **优先级：** P2 | **阻塞于：** 02 | **模块：** yak-ops-boot + yak-ops-ui

**What to build：** 收拢"四套账"里最容易困惑的两套：让 Security 操作日志（`yak_security_oplog`，现仅项目/角色管理在写）同时进入统一业务审计，并清理前端双页面与脏记录盲区。

**机制：**
- **oplog 桥接**：外部安全框架留了扩展点 `OperationLogExtend`，yak-ops 侧无人实现、走空实现 `NoOpOperationLogExtend.record(){}`。在 boot 实现该 SPI 并 `@Component` 顶掉空实现：`record(OplogDTO)` → `BusinessAuditService` 写一条 `operation_type=OPLOG_{原operate_type}`、`source='SECURITY_OPLOG'` 的记录（actor/resource 从 DTO 映射）。业务代码**不**改调 saveOplog，存量两条读路径都不动。
- **前端清理**：
  - 删除遗留空页 `yak-ops-ui/src/pages/system/operation-logs/index.tsx`（一行占位，与真页 `oplogs` 并存）；确认菜单/路由无引用后删。
  - 审计中心（`pages/system/oplogs`）业务 Tab 增加 `source` 筛选与标签列（WEB 兜底 / SDK 业务 / SECURITY_OPLOG 桥接），配合 02 的 `/options` 新筛选项。
- **RUNNING 巡检**：两段式埋点漏调 `success()/failure()` 会留永久 RUNNING 脏行。新增只读统计 `GET /api/v1/audit/health`（或并入 /options）：RUNNING 且 started_at 超阈值（如 1h）计数 + 最老样本清单；不自建告警通道（调度告警在内存的项目事实），先供页面/日志可见。

**验收清单**
- [ ] 项目/角色管理一次操作后，业务审计与 security oplog 两处各得一条、内容一致（桥接不改写源）
- [ ] 空页删除后 tsc/构建绿，菜单无死链
- [ ] 审计中心可按 source 过滤；兜底记录名称可读
- [ ] /health 端点单测（造 RUNNING 陈旧行计数）
- **验证边界：** SPI 顶掉 NoOp 是否生效依赖真机（外部 jar 装配顺序），列入重启后验证清单；UI 按项目惯例真浏览器验证。
