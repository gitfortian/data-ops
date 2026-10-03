# Home Requirements

## Purpose

Yak Ops 首页只读取并组合各能力域已经拥有的事实，不成为新的业务真相中心。Home 的职责是稳定 read model、查询预算和 HTTP projection；业务生命周期仍由 Datasource、Dataset、Lineage、Sync、Workflow、Quality、Schedule 等 owning subsystem 管理。

## External API compatibility

本模块迁移与重构不得改变现有 API 地址：

```text
GET /api/v1/home/cockpit
GET /api/v1/home/data-center/overview?period=...
GET /api/v1/home/data-center/recent
GET /api/v1/home/data-center/schedule?period=...
GET /api/v1/home/assets/overview
GET /api/v1/home/quality/overview
GET /api/v1/home/schedule-center/calendar?month=...
```

现有响应字段保持兼容；Project Scope 与质量权限边界保持兼容。

## Data semantics

- 查询成功且没有业务记录：返回真实 `0` 或空集合。
- 模块未启用、权限不足或查询失败：已有 nullable read model 必须保持“不可用”语义，不凭空制造业务事实。
- 无法证明的指标不使用其他字段替代。
- 首页“今日”按应用所在时区的自然日窗口计算。
- 最近列表和关系图必须有明确上限。

> Cockpit 的历史 numeric 字段继续使用 `0` 降级以兼容旧消费者。2026-10-03 用户批准架构方案 A09 后新增 `dataSourceAvailable`、`runningAvailable`、`observedAt` 和逐来源 `sources`。真实零值必须 available=true；部分失败时聚合 runningAvailable=false，来源观测只表示读取可用性，不成为业务状态或新 Truth Owner。新前端在不可用时展示占位，不把部分合计展示成完整数量。

## Query budget

- 不允许为了首页统计调用无界 `list()` 后在 JVM 中全量计数或排序。
- 不引入 N+1 详情请求。
- Datasource 通过 `DataSourceReader.summary()` 获取聚合结果。
- Dataset 使用 bounded overview facade。
- Lineage 使用 bounded overview/query contract。
- Offline Sync / Workflow / Quality 使用各自 overview Reader。
- Schedule 读取 Yak Schedule 统一快照，不重新读取业务调度表。

## Security and workspace isolation

- 所有 `/api/v1/home/**` Controller 必须参与 `@ProjectScope`。
- Home 不自行信任前端 project id；CurrentProject 由统一 Project Scope interceptor 建立。
- `GET /api/v1/home/quality/overview` 继续要求 `quality:execution:read`。
- Home 不绕过 sibling subsystem 自有安全边界。

## Non-goals

- 不新增 Home 数据库表或 Flyway。
- 不把 Home 变成跨模块 command facade。
- 不修改 sibling domain state machine。
- 不修改现有 REST 路径。
- 不在本次结构重构中重新定义指标含义。
