# FE-A2 / 1｜来源表 ↔ ODS 精确接续（2026-10-14）

> 状态：**IMPLEMENTED_DRAFT / CI_PENDING / LIVE_E2E_PENDING**。关联 [#513](https://github.com/gitfortian/data-ops/issues/513)、[#539](https://github.com/gitfortian/data-ops/issues/539)、[#559](https://github.com/gitfortian/data-ops/issues/559)、[#555](https://github.com/gitfortian/data-ops/pull/555)。本文件只记录 FE-A2 第一条真实生产 React 交互，不将 FE-A2 全域误标完工。

## 用户故事与入口

- [x] Project 内进入已有 DataSource → “即时探查”，选择 **TABLE**，展开实时报表/列；只有列读取成功、具有 DataSource READ 与 Modeling READ 时才显示可执行的“打开或创建 ODS”。**VIEW 不提供这条 ODS 新建入口**，不把“找不到”错误充作 0 条候选。
- [x] 入口形成 `/modeling?sourceJourney=ods&origin=live-source&datasourceId=&database=&schema=&table=` 精确路由。来源是四元组（DataSourceId / Database / Schema / Table），不是仅表名。仅构造合法、安全、非空的 URL 参数；任何 `returnTo` 外部网址不参与跳转。
- [x] 复用已有 `OdsSourceJourney` 中 `listAllDataSources`、`getOdsSourceCandidates`、精准 0/1/N 候选/Unverified、实时源列预览、`createModelingModel` 和 `commitOdsManualImport` 带乐观锁的原子回执，**不复制写接口、不放宽异常或 Project 授权**。
- [x] 从这条 DataSource 入口进入时，ODS 的“返回来源物理表”及“保存并返回来源”跳到 `/data-source?sourceId=&database=&schema=&table=`，并且 DataSource 页必须由 **DataSource Owner `getDataSource` + `listAllDataSources` 双重证明当前项目持有该来源** 后才打开 Workspace；没有授权/来源失效/深链参数畸形时仅报错并允许回列表。
- [x] 恢复时从 *exact* 库/Schema/表重新执行 Catalog 列读取和表选择；不自动跳到另一个 Schema 的同名表。历史来自 Asset Catalog 的入口仍返回 Asset Catalog，无行为回退。
- [x] 已有 ODS 候选进入模型详情时携带来源四元组；详情页必须通过 Modeling Owner 返回的 ODS sourceDatasourceId/database/schema/table 四字段精确核验，才显示“返回来源表”。历史 Schema 缺失、不一致及非 ODS 不显示。\n- [x] 旧数据源列表、连接检测、Metadata 采集信息、保存/预演/运行和资产纳管各自保留原操作；**不存在先资产 WRITE 才能建 ODS 的条件**。

## 真正的服务与授权合同

| 步骤 | Owner / 原 API | 正确性边界 |
|---|---|---|
| 实时库/Schema/表/列 | DataSource Catalog APIs | Project 当前来源、表类型、精确 Schema/列读取有效 |
| 精确 ODS 复用 | Modeling `GET /api/v1/modeling/source-journey/ods-candidates` | `UNVERIFIED` 不当作空候选、已有 ODS 不自动新建 |
| 源列表证据 | DataSource `GET /api/v1/data-source/all` | ODS 操作仅信当前项目来源 |
| ODS 首建及保存 | Modeling `createModelingModel` → `commitOdsManualImport` | draftId/If-Match/source fingerprint/原子回执，保存失败不重复盲写 |
| 返回来源 | DataSource `GET /api/v1/data-source/:id` + `/all` → Catalog | URL 是导航提示；必须由当前 Project 重新核验才能读取 |
| Metadata 采集 | Metadata 原有 `collectDefinitionPath` + `findCoveringCollectionJobs` | 并行入口，保存/预演/正式运行仍归原 Owner |

## 重点负例与可验收证据

- `trade_order` 与 `trade_order_detail` 的来源粒度不能合并，同名表不同 Schema 不能错误命中。
- 测试非法 SourceId（非安全整数）、空表/库、不可信 URL；VIEW/列读取失败不显示可执行的 ODS CTA；精确来源返回恢复，同一个表用 Asset 来源进入仍返回 Asset。
- 模型候选接口失败/Unverified、模型创建后断线、源列漂移、无 Modeling CREATE、没有 Asset WRITE、跨 Project、撤权均应由原有 Owner 拒绝或显示恢复提示；不能以 UI 路由可见证明后端允许写。
- GitHub CI Product Guard、Architecture、Consumption + Jest `liveSourceOdsJourney.test.ts`、`LiveSchemaExplorer.test.tsx`、`OdsSourceJourney.test.tsx`；真实环境须在 320/390/768/1440 屏幕和双角色下跑跨项目、真实保存/返回与重复打开。

## 剩余 FE-A2 工作（不在本 PR 内声称完成）

1. 采集任务从精确源范围接续“保存 → 预演 → 正式运行 → 结果下一步”全链路可用性；尤其运行超时、重新载入、回源与 Run/Job 对账。
2. 已存在 ODS 的详情已接入来源上下文，仍需在真实环境中确认各发布/历史版本与原域匹配，以及详情编辑状态下的未保存修改返回提示。
3. 真实双 Project 跨 DataSource/Metadata/Modeling 权限拒绝、被动撤权/字段漂移、会话切换验收；这些仍属 #559 UX 与 G2 实景门禁。

本 PR **不调整**导航数据库/menuCode、后端/DB、新的 MappingSet 或 TaskRevision 写操作，也不代 Product/UX 签署。
