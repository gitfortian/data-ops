# 05: 标签挂载 UI + 列表按标签筛选

**对应需求:** 指标中心盘点 §3.9/§6(P1)| 阶段: P1

**What to build:** 标签本体 CRUD 已在 /metric/service 可用,`POST /tags/assign/{metricId}`、`DELETE /tags/remove/{metricId}/{tagId}` 后端与前端 api.ts 封装**齐备但无任何页面调用**,详情页"管理标签"抽屉是占位空壳(detail/index.tsx:360-362)——有标签体系却无处打标,闭环断在前端最后一步。本单:①详情页标签 Tab 接真"管理标签"(标签多选挂载/移除,能选不填);②指标列表加标签筛选列/筛选项(后端 `page` 现无 tagId 参数,需 repository/mapper 加 join 或子查询)。

**模块归属:** metric(前端为主 + 列表筛选一处后端)

**Blocked by:** 无

**Status:** 已完成(2026-09-22；页面实测随批次统一验收)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);assign/remove 复用现有端点不改契约;打标操作写审计;列表筛选须保持 project_id 服务端可信过滤。

- [x] 详情页"管理标签"抽屉实装(已挂载可移除、可加新挂,调现有 assign/remove)
- [x] 后端 page 支持 tagIds 过滤(repository/mapper + 单测)
- [x] 列表页标签筛选器(多选下拉,选项来自标签本体)
- [x] service 页标签列表反向显示"已挂载指标数"并可跳筛选后列表
