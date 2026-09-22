# Ticket 07：语义补齐第二批——metadata + quality（P2）

**对应需求：** 全量审计方案 M2 | **优先级：** P2 | **阻塞于：** 02,03 | **模块：** metadata + quality

**What to build：** 两个零审计域（metadata 13、quality 18 个写接口，`BusinessAuditService` 引用为 0）补上语义注解。做法与判定三选一**完全沿用 06**，此处不再重复；两模块当前无任何手工埋点，默认全部走 Controller `@Auditable` 路径，个别多步动作才引 SDK。

**范围重点：**
- metadata：采集任务增删改/启停、元数据模型与字典维护、采集执行触发（执行结果属领域日志，不混入操作审计）
- quality：规则/监控对象 CRUD、启停、手动触发检查、处置操作。注意 quality 自有 `OperationLog`（`QualityWorkspaceController.java:57`）是**质检执行记录**口径，保持独立不动。

**验收清单**
- [ ] 两域写接口全覆盖（05 守卫本模块 gap 归零或入"人工确认"清单）
- [ ] 类型码入 `AuditOperationTypes` 常量池（METADATA_*、QUALITY_* 段）
- [ ] 真机抽查 ≥4 条可读记录；quality 执行记录页无回归
- [ ] baseline 联动更新
