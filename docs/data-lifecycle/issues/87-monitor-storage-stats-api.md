# Ticket 87：TTL 监控 + 存储统计 API（快照任务）

**对应需求：** 3.7 / 3.8 | **阶段：** P2 | **模块：** lifecycle

**What to build：** 监控 API：按模型输出状态机（UNSET/APPLIED/DRIFT/FAILED）+ 过滤分页 + KPI 汇总 + 异常区（EXHAUSTED/数据源不可达）；存储统计 API：每日 02:00 对各层库 `SHOW DATA` 落快照，页面 API 输出各层存储量、热/冷/待清分布、近 30 天趋势、成本估算（单价可配置，未配置返回 null→前端"—"）。

**Blocked by：** 85, 86

**验收清单**
- [ ] `monitor/TtlMonitorService`：resolve 链 + 每模型最近记录推导状态（单测覆盖四态）
- [ ] `GET /monitor/summary`、`POST /monitor/models/page`（复用 82 解析 + 85 流水）
- [ ] `stats/StorageSnapshotService` + 每日闹钟（同 86 框架，独立 handler）：按层→`SHOW DATA FROM db`→upsert 快照；单库失败不中断整体
- [ ] `GET /storage/stats`（按层聚合+热冷分布：用分区归类快照或最近 preview 缓存推算，标 estimated）、`GET /storage/trend?days=30`、`GET/PUT /storage/setting`
- [ ] 单测：状态推导、趋势聚合、单价缺省
