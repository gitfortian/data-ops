# Ticket 76b → 77：数据访问审计流水

**目标**：数据级访问/授权/脱敏施加的留痕与安全视角查询统计（配置生命周期复用 audit，数据访问走本模块流水）。

**表**：`yak_dsec_access_log`。
**行为**：
- `record(AccessAuditEntry)` 写流水（供裁决/脱敏链路调用）。
- 分页查询（按 actor/resource/decision/时间/等级）。
- 统计：TOP 访问者、拒绝次数、敏感读取次数（服务端聚合，禁无界 list 内存统计 → 用 SQL 聚合）。
**复用**：配置 CRUD 生命周期仍走 `BusinessAuditService`（统一操作审计菜单跳转）。
**错误码**：45070~45079。
**验收**：单测覆盖记录与查询参数组装、统计聚合委托 repository。
