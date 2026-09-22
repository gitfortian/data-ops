# Ticket 118：实体详情聚合与批量取实体

**对应需求：** 统一检索 / 发现层 | **阶段：** P2 | **模块：** metadata

**What to build：** `GET /api/v1/metadata/entities/{id}`（详情）与 `GET /api/v1/metadata/entities`（批量，供选择器与列表内联）。同一接口服务物理实体与投影实体，**按 `typeName` 出不同面板**，面板构成由 `field_def` 驱动。

**Blocked by：** 117、129

**验收清单**
- [ ] 物理侧（`table`/`tableColumn`）：属性、子级列、变更历史、标签、血缘入口、统计（行数/分区，标"近似"）
- [ ] 投影侧（`dataModel`/`standardField`/`domain`/`metric`）：**实时**调源域取列清单/公式（`EntityProvider.refresh(sourceId)`），目录里**绝不存**这些内容（plan §1.3；§10 测试 10）。改源域数据后刷新即变，**无需等一轮对账或重跑登记**
- [ ] 存储量**只读 lifecycle 快照**并显示快照日期，同时按 `database_name` 过滤（plan §9 T16）；grep 守护：本模块无 `SHOW DATA`、无 `information_schema` 字节量查询、不建 mapper 查 `yak_lc_*`
- [ ] **分区容错**：任一块（血缘/统计/标签/源域实时）失败只让该块降级，**不整体 500**
- [ ] `MetadataQueryApi`（plan §5.2）：`search/getEntity/listChildren/listPhysicalColumns/findPhysicalTable`。后两个**只是 `type_name` 过滤的语法糖，实现里禁止出现独立表**
- [ ] `EntityDTO` 携带 `typeName + attributes + slotValues`；字段清单权威来源是 `/types`，**不是常量类**（否则"加字段免改表"会在 API 边界上被重新写死）
- [ ] 语义回流一期口径：详情页把"该列注释"与"关联标准字段"并排，人工点"沉淀为标准字段"走 **semantic 既有接口**；**不做 NLP 自动抽取**（错误沉淀不可逆，plan §5.5）

**落地状态（2026-09-21）**

已由代码级证据结清：

- [x] 存储量只读 lifecycle 快照 —— 端点 `GET /api/v1/lifecycle/storage/table`（按 `database_name` 过滤），本模块新增守卫断言"不查 `yak_lc_*`"，与既有 `SHOW DATA`/字节量断言同条测试
- [x] 分区容错 —— `EntityDetailServiceTest` 18 条：一块失败只降该块、带码失败保留原码（provider 未装配=49021，其余 49025）、只有实体不在场才整体 49001
- [x] `MetadataQueryApi` 不换读法 —— 目录行的 `SELECT_COLUMNS`/行映射收在 `query/CatalogRowRead` 一处，`MetadataQueryApiImpl` 本身零 SQL
- [x] `EntityDTO` 三袋 + 槽键翻回字段名 —— `CatalogQueryServiceTest` 4 条

**未打勾的不是没做，是还没在真机上验过**（按各条注明）：

- 物理侧与投影侧面板：抽屉已改接真接口（`AssetDetailDrawer` 按 `sections` 逐块渲三态），但端到端**待用户重启后端**。投影侧另有一道数据缺口：目录里没有 `dataModel` 行（工单 131 的登记挂钩未开工），走查前需手插一条 `REGISTERED` 行
- 语义回流：只做到"列注释与 `stdFieldId` 并排"（`extra.columns` 已带这两键）；「沉淀为标准字段」按钮未接，标准字段写侧不在本票范围

本轮改动文件：`metadata` 的 `api/{EntityDTO,EntityQuery,EntityProjection,EntityProvider,MetadataQueryApi}`、`query/{CatalogRowRead,CatalogQueryService,MetadataQueryApiImpl,MetadataSearchService}`、`detail/{EntityDetailService,SectionState}`、`governance/MetadataGovernanceQueryService`、`controller/v1/MetadataEntityController`；`modeling` 的 `metadata/ModelEntityProvider` + pom 加 metadata 依赖；`lifecycle` 的 `stats/StorageStatsService` + `controller/v1/TtlStorageController`；前端 `services/{metadata,data-lifecycle}` 与 `pages/data-metadata/components/AssetDetailDrawer`。

离线门禁：metadata **310/310**、modeling **147/147**、lifecycle **43/43**、全 reactor `compile` 通过、`npx tsc --noEmit` **199**（与基线同数，本工单文件零新增错误）。
