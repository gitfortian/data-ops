# 03: 变更影响分析页入口接线

**对应需求:** 模型工作台盘点 §6 缺失能力（P1）| 阶段: P1

**What to build:** `/modeling/impact` 页面与 `ModelingImpactController`（按标准字段/按来源列查各层落地）均已上线，但**全站无入口**——路由 `modeling-impact` 在 `navigation.ts:161` hidden，且无任何 `history.push('/modeling/impact')` 跳转代码，能力闲置。低成本接线：在主线视图（`/modeling/mainline`）过程节点、表结构编辑器标准字段列加"影响分析"跳转链接（带字段/来源列参数直达结果）。

**模块归属:** modeling（纯前端接线，后端零改动）

**Blocked by:** 无

**Status:** 已实现（2026-09-22，代码与 tsc 已过，实机验证待 dev server/后端启动后补做）

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md)；不改 navigation 菜单结构（保持 hidden 路由+页内跳转口径，与菜单重设计决议一致）；impact 页需支持 URL 参数预填查询。

- [x] 主线视图过程节点/覆盖单元格出"查看影响"跳转（携带业务过程/域参数）
- [x] 表结构编辑器绑定了标准字段的列出"影响分析"入口（按字段反查各层落地）
- [x] impact 页支持 query 参数直接进入查询结果态
- [x] 反向出口：影响分析结果可跳回对应模型详情

**实现注记（纯前端，后端零改动，navigation 未动）:**
- `pages/modeling/impact/index.tsx`:URL 参数三通道——`processFieldId` 直接进入按标准字段结果态；`processId(+processName)` 调既有 `listSemanticProcessSources`(ticket 36)取 MAIN 源表(无 MAIN 取第一张)预填并自动按来源分析，顶部 Alert 说明选表依据/多源表提示/未绑定源表提示；`datasourceId/database/table/column` 直填并按来源分析。
- `pages/modeling/mainline/index.tsx`:操作列加「查看影响」→ `/modeling/impact?processId=&processName=`。
- `pages/modeling/detail.tsx`:「数据标准」列绑定标准字段(`stdFieldId`)的行尾加「影响」链接 → `/modeling/impact?processFieldId=`。注:标准字段 ID 与 impact `processFieldId`、分层映射 processFieldId 同一 ID 空间（语义中心标准字段，ticket 35/38/43/44 链路）。
- 反向出口:受影响模型列原本即 `history.push('/modeling/models/{id}')`(unified 页)。
- 过程级影响=「过程主源表→按来源分析」的映射：后端 impact 只有标准字段/来源列两个维度（`ImpactAnalysisService`），不改后端的前提下这是唯一可落地的过程入口。
- 验证:`npx tsc --noEmit` 182 错误(≤183 基线)、三个改动文件零错误;实机回归(主线跳转/字段跳转/query 直入/跳回详情)待 :8000+:8080 启动后补做。
