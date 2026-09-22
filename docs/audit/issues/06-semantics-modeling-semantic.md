# Ticket 06：语义补齐第一批——modeling 余量 + semantic 缺口（P2）

**对应需求：** 全量审计方案 M2 | **优先级：** P2 | **阻塞于：** 02,03 | **模块：** modeling + semantic

**What to build：** 把变更最频繁的建模域从"34 个写接口只有 5 个类有审计"补到全语义覆盖。原则：**未埋点接口加 `@Auditable`；已有手工埋点的接口要改语义直接改 `start()` 的 request 字段，两者不叠加**。

**范围（调研点名的无审计 Service）：**
- modeling：`DdlService`、`ModelDeriveService`、`ModelVersionService`、`MappingService`、`LayerFieldMappingService`、`ReverseImportService`、`ImpactAnalysisService`、`ModelPublishApprovalService`、`ModelingStandardApplyService`、`SourceChangeDetectionService`、`MainlineViewService`（约对应 ModelingModelController 15 个写接口等）
- semantic：`StandardUsageService`、`StandardRecommendationService`、`StandardPublishApprovalService`

**做法（逐 Controller 过一遍写接口，三选一）：**
1. 单接口单动作 → Controller 方法加 `@Auditable(name,type,resourceType)`，需要看改了什么再开 `recordPayload=true`（如 DdlService 执行 DDL、ReverseImport 提交）；
2. 一次 HTTP 内多资源动作（如批量派生）→ 保留/新增 Service 手工 `start()` 拿事件粒度，Controller 侧 `@Auditable(ignore=true)` 防兜底混入；
3. 已有手工埋点但 operation_name 语义差 → 直接修 request 字段。
- 类型码统一进 01 的 `AuditOperationTypes` 常量池，不散写字符串。
- 完成后把本模块 baseline gap 数下调并提 `docs/audit/coverage-baseline.json`（联动 05）。

**验收清单**
- [ ] modeling/semantic 写接口逐个具备三类之一，`@Auditable` 无魔法字符串
- [ ] 审计中心真机抽查 ≥6 条：名称可读、resource_id 可点回详情（如可用）
- [ ] 无任一接口双记（同请求 2 条 operation）
- [ ] baseline gap 下调，05 守卫绿
