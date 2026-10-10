# F-039 #495 — Metadata 来源证据与 PLAN.md 核对门禁

Status: ENGINEERING_PROPOSAL / NOT_EXPOSED, 2026-10-10.

## 本批工程落实

- Metadata 自有的 `PhysicalScopeEvidenceQueryApi` / `PhysicalScopeEvidenceQueryService` 以可信 CurrentProject 选择 1–20 个显式资产键，仅通过既有 `CatalogQueryService` 的项目内、未撤销行读取，不读源库行/连接信息。表必须为 METADATA/HARVESTED 物理实体，且表结构 contentHash、列数、采集任务和时间存在。按表读最多 501 条子列用于超限检测，拒绝 PARTIAL、不完整列数、跨数据源/Schema/采集轮、列父级/坐标不匹配。投影以长度前缀 SHA-256 绑定采集和列指纹。
- `CatalogQueryService.childrenBounded` 是原目录查询的有界变体，共用 `yak_metadata_asset` 行映射、项目谓词与类型注册表，不新建 source truth 或第二套仓储。
- Agent 侧 `SourceSemanticPlanDocumentGuard` 精确读取服务端可信 workspace 的 `plans/PLAN.md`，至多 64KiB，拒绝丢失、损坏、符号链接、空白、非法 UTF-8 和内容漂移；`SourceSemanticPlanApprovalGate` 先核对文件与冻结 scope/hash 才调用上一批 CAS Ledger 批准或恢复。
- 同步 JUnit 覆盖项目权限缺失、采集/来源漂移、缺列、列归属污染、计划文件修改/缺失/过大/符号链接和审批恢复 fail-closed。

## 未完成的产品门禁与限制

这些新类型仍仅为 Metadata 内部 API 和 Agent 纯普通对象，**未通过 Spring Controller 或新 Agent Tool 对外开放**。Project 级 Metadata 可见范围并不自动授予 DataSource 使用权限；在接入生产入口之前必须补充当前用户的 DataSource ACL 检查并明确采集生命周期与时间覆盖。同一个采集任务号不等于多个表的完全一致快照，刷新时必须重新读 source fingerprint。SDK PLAN.md 不保证跨节点共享，本守卫只校验已由应用可信绑定的本地 workspace，不提供任意路径读取。

`SourceSemanticPlanApprovalGate` 防止伪造计划修订，但并不代替用户身份/项目认证、当前来源 ACL 或 SDK 的 ConfirmResult 授权链。原 AgentTurn/Executor/Registry 仍保留业务执行、pending 和取消 owner；尚未接通多轮实际分片推进、UI、SSE/重启 read model、全部 token/time 工具额度和真实 E2E。

PD-009 仍 PROPOSED，F-039 仍 DRAFT。该 PR 是可合并的基础工程，不会启用正式来源推理或语义写操作；完整 #495 验收待继续推进。
