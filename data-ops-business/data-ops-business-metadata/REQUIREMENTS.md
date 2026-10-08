# Metadata Requirements

> 只描述模块需要什么，不描述怎么实现。按 ticket 追加；行为变更先改本文件再写代码（契约 diff 先于代码 diff）。

## Ticket 110：模块骨架 + 契约文件集

- Maven 模块接线（`data-ops-business/pom.xml` `<modules>`、BOM `dependencyManagement`、`data-ops-boot` dependency）。
- 持久化装配：Flyway `db/migration/yak-metadata`、历史表 `flyway_schema_history_metadata`、bean `yakMetadataFlyway`、
  `@MapperScan` 指向 `yakBusinessSqlSessionFactory`；调度 namespace `yak-ops-metadata`。
- 错误码 `MetadataErrorCode` 49001~49099；权限码 `data-metadata:read/create/update/delete`；
  `MetadataException` + `MetadataExceptionHandler`（`basePackages` = 本模块 controller 包）。
- 本目录 6 份契约文件。
- **验收要求**：真机打一个必失败请求，确认 49001 不被兜成 999（`basePackages` 写错是本仓库真实事故形态）。

## Ticket 111：菜单与权限接入

- 一级菜单组"元数据"（group `data-metadata`）+ 4 子页：采集任务 / 目录浏览 / 统一搜索 / 概览；4 个权限 + root 授权，幂等。
- 菜单迁移取 yak-security 链 `V2033__register_data_metadata_menu.sql`（V2030 security / V2031 lifecycle / V2032 asset 已占用）。
- 未授权账号看不到该组；`securityMenuCodes.ts` / `navigation.ts` / SQL 三处权限码字符串逐字节一致。
- 前端 4 个空态页路由可达；`PROJECT_REQUEST_RULES` 登记 `/api/v1/metadata` = `PROJECT_REQUIRED`
  （**漏则整个模块所有接口 999**）。

## Ticket 112：存储层（自持 7 张）

- 建 `yak_md_asset_extension` / `_collect_job` / `_collect_run` / `_register_retry` / `_change` / `_label` / `_task`。
- `_collect_job`：作用域（数据源 / 库 / 表 pattern）+ cron + 开关 + `provider_type`；
  **物理采集与投影对账共用这两张运行表**（不新开第三张历史表）。
- `_collect_run`：`cnt_new` / `cnt_changed` / `cnt_unchanged` / `cnt_gone` 四计数 + `SUSPECT` / `FAILED` 状态 + dry-run 标记 + 游标水位。
- `_register_retry`：唯一键 `(project_id, type_name, asset_key, source_updated_at)`——**键在"变更"上不在 `status` 上**；
  状态退回普通列，`DONE`/`DEAD` 行可清理（审计在 `_change`）。
- `_change`：**append-only**，代码层不提供 UPDATE/DELETE 路径。
- `_label`：`label_type` × `state` 双维度 + `reason` + `derived_from` + `expires_at`；`asset_id` 对所有实体类型同构引用。
- `_task`：`open_marker` 去重位 + `CHECK ((open_marker = 0) = (resolved_at IS NULL))`；
  **禁用 `UNIQUE (…, 可空列)` 表达"只允许一条开放行"**（MySQL 里 NULL 彼此相异，方向正好写反；生成列方案被 `3109` 拒）。
- PO 落 common `bean.po.metadata`，枚举/常量落 `constant.metadata`；无物理外键。

## Ticket 128：元模型两张表 + 两个 registry

- `yak_md_type_def`（含 `search_default_weight` / `search_include_by_default`，随建表一起出，不留后补 ALTER）
  + `yak_md_field_def`，V1 基线 INSERT **一期 8 类实体**：
  `databaseService` / `database` / `table` / `tableColumn`（collectible=1）
  + `dataModel` / `standardField` / `domain` / `metric`（collectible=0）。
- **键与 FQN 必须复刻源域既有格式**（`modeling:model:{id}`、`semantic:field:{id}`、`metric:{id}`、
  `table:[unresolved:]{dsId}:{db}.{schema}.{tbl}`…），不得另起一套——新造键不会报错，只会让同一实体在图里裂成两个节点。
- `tableColumn` 默认不进检索面（`search_include_by_default=0`）；表 > 列的乘性权重由 `search_default_weight` 表达。
- `MetadataTypeRegistry`：类型/字段解析 + 失效缓存（新增 `field_def` **不重启即生效**）。
- `MetadataSlotRegistry`：7 个提槽集中登记，两类型抢同槽 → 报错，不静默复用。
- 层级用类型对 `(table, tableColumn, refersTo)` + 实体行 `parent_asset_id`，不靠 `parent_types`。

## Ticket 129：类型自省与级联校验

- `GET /api/v1/metadata/types`：返回全部类型 + 各自字段定义（含 display_name/icon/color/searchable/storage_slot/权重），
  是前端目录页、详情面板、筛选项的**唯一驱动源**（前端零类型常量）。
- `type_def` 保存校验：`kind=ENTITY` 必填 `key_prefix`/`fqn_pattern`/`lineage_asset_type`；
  `lineage_asset_type` **必须是 `LineageAssetType.values()` 里的常量名**（错值会在 lineage 读行 `valueOf` 时抛异常，炸别人的血缘查询）；
  `key_prefix` 与 `fqn_pattern` 自洽。
- `field_def` 保存校验：`searchable=1` 而无 `storage_slot` → 49xxx 直接拒、不落库（"配了不生效"是最难查的 bug）；
  `base_type=ENTITY_REFERENCE` 校验 `entity_type_ref` 指向的类型存在。
- **可扩展性的硬证明**：插一行 `field_def`（`searchable=1` + 空闲槽位）后不重启、不改代码，
  该字段立刻可被 `queryFilter=attr.x=…` 过滤、可进 `q` 命中；未登记字段被 49xxx 拒。

## Ticket 130：写时登记主通道（`MetadataRegistrationApi` + outbox + 重试 worker）

- `api/MetadataRegistrationApi`：`register(RegisterCommand)` / `unregister(typeName, sourceId[, assetKey])`。
  **源域只 import 本 api 包**；`RegisterCommand` 与 `EntityProjection`（ticket 135）共用同一个 DTO，不长两套投影定义。
- **API 永不向源域抛异常**（plan §9 T20 四种错误形状都不许出现）：
  落库成功即返回；任何失败（含命令不合法这类确定性错误）→ 整份命令落
  `yak_md_register_retry`（PENDING），由 worker 重放；连排队都失败（库不可达）只留 ERROR 日志——
  兜底是对账副通道（135），不是重试队列。
- `projectId` **不在 DTO 里**：登记与重放都取服务端可信上下文（`CurrentProject`），杜绝前端/源域自报归属（§0.9）。
- **与采集走同一个 upsert 与软删入口**（`AssetUpsertRepository.write/markGone`），本票不开第二条共表写路径；
  `provider_type='REGISTERED'`、`content_hash` 恒 NULL、CHANGED 只比 `source_hash`（指纹分岔复用既有实现）。
- **保序**：预读目录行的 `source_updated_at`，命令更旧 → 只 `touchPresence` 刷在场时间、不改内容（等价 `writeIfLatest`）。
  `sourceUpdatedAt` 缺失直接 49015 拒（唯一键列不允许 NULL，T17）。
- **键校验**：非空 / ≤512 / 前缀 = `type_def.key_prefix`（49006）；元数据不替源域拼键。
  属性只走 `MetadataAttributeCodec`（未登记字段 49012 拒）。
- `unregister` 走软删（`gone_at`），**只处理本模块写的行**（`source_type='METADATA'` 且 `provider_type='REGISTERED'`）；
  认领的遗留行（source_type=MODELING 等）归 lineage 自己的撤销链路，本模块不越界。
  与规划接口的唯一偏差：加了第三参 `assetKey`（outbox 行 NOT NULL 需要它；源域删除时手里就有键），两参重载保留。
- **worker**：`@Scheduled(fixedDelayString = "${yak.metadata.register-retry.poll-delay-ms:1000}")`，
  不经平台调度器；`due(20)` → `claim`（原子改 IN_PROGRESS，崩溃 10 分钟后可重捞）→ 干活 → `complete`(DONE)/`fail`；
  退避 `min(3600, 1<<min(12,attempts))`，`attempts≥12` 转 `DEAD`（终态，出口=对账每轮捞回）；
  执行前 `projectScope.run(new ProjectContext(projectId,null), …)` 恢复上下文（T10）。
- 排队幂等：并发撞 `uk_yak_md_retry_change` → catch 成"已排队"，不上抛（键在"变更"上不在 `status` 上）。
- 共表目录列补齐：`upsertAssets` 开始写 `owner_user`/`domain_ids`/`layer_code`（UPDATE 用
  `IF(VALUES(x) IS NULL, x, VALUES(x))` 守卫——HARVESTED 交空值不得清掉投影侧的值）。

---

## Ticket 117：跨类型统一搜索（`query` 包 + `GET /api/v1/metadata/search`）

- 接缝 `MetadataSearchBackend`（一期唯一实现 `MysqlMetadataSearchBackend`，NamedParameterJdbcTemplate 直连
  `yakBusinessDataSource`）；服务层 `MetadataSearchService` 只做 HTTP↔计划翻译，SQL 形状全部出自 `SearchConditionBuilder`。
- 参数取 OM `/search/query` 子集：`q/index/queryFilter/postFilter/sortField/sortOrder/searchAfter/from/size/getHierarchy/explain`
  （`includeFields/excludeFields/trackTotalHits` 收下一期裁剪字段面）。命名纪律：对外只说 `typeName`/`attr.<field>`，
  `type_id`/生成列名不出现在响应里；**从不按 `asset_type` 过滤**。
- **一次查询出全部类型**：行数恒 3~4（行 + `GROUP BY type_id` facet + 列命中 rollup + 可选父实体批量补取），
  与类型数无关；`total` 直接取 facet 求和（`totalSource=facet-aggregation`，不开 COUNT(*)）。
- `queryFilter` 参与 facet 计数、`postFilter` 不参与（两条谓词列表分开装配，rollup 用 base+queryFilter+q）——
  分界线在 `SearchPlan` 的字段上，不在 if 里。
- `attr.<field>` 两道门：field_def 未登记 → 49012；登记未提槽 → 49024。条件形状只由 `match_type` 数据驱动
  （text/like→LIKE、exact→等值、range→两端 `>=`/`<=`，`from..to` 语法），新类型加字段零代码分支。
- `q`：BOOLEAN MODE 转义集中在 `BooleanModeEscaper`（10 个保留字符全枚举单测）；ngram token_size=2 →
  单字查询降级 LIKE 并在 `explain.notes` 如实标注；可检索槽位以 OR 并入同一 MATCH 计划。
- 排序：相关性 = `type_def.search_default_weight` 乘性 CASE（权重值来自数据，非硬编码分支）；
  显式 sort 白名单外 → 49023；`searchAfter` = `(sortValue,id)` 游标，游标不进 facet；
  相关性排序 + 游标同用 → 49037（无稳定游标可给）；`from` 上限 5000，`size` 钳到 200。
- `tableColumn` 默认面排除（`search_include_by_default=0`），命中以"表行命中 N 列"rollup 回示；
  显式 `index=tableColumn` 时 rollup 自动解除。
- 项目域：`@ProjectScope(PROJECT_REQUIRED)` + `MetadataPermissionCode.READ`；`explain=1` 只回**绑参占位**后的 SQL，永不回字面值。
- 验证状态：编译 + 279 单测全绿（含 `BooleanModeEscaperTest`/`SearchConditionBuilderTest`/
  `MysqlMetadataSearchBackendTest` 与分层守卫）；端到端已随工单 123 真机走查通过（混排/facet/列命中 rollup/
  游标翻页/降级 LIKE/explain/层级回查），走查逼出并修掉一处执行期缺陷：聚合语句的一参 lambda 静默绑到
  `ResultSetExtractor`（只对空游标跑一次即抛 SQLException → 999），现统一显式 `(RowCallbackHandler)`。

---

## Ticket 118：实体详情聚合与批量取实体（`detail` 包 + `GET /api/v1/metadata/entities[/{id}]`）

- 资产详情的技术元数据分区由 Metadata 实现 `SectionProvider` 提供物理表与列事实；通过本模块既有 `MetadataQueryApi` 读取，
  不复制目录查询逻辑或建立第二份事实。Asset 仅负责分区编排、状态传递与展示。未登记的物理表返回 `EMPTY`，列读取失败由 Asset 分区边界转为 `UNAVAILABLE`。

- `GET /entities/{id}` 出「目录事实 + 分区」：`entity`（`typeName` + `facts` + `attributes` + `slotValues`）
  与 `sections`，块序固定为 `stats → children → history → labels → lineage → source`，每块一个
  `SectionState{status(OK/EMPTY/UNAVAILABLE), code, message, data}`。`GET /entities?ids=` 批量取，
  供选择器与列表内联，**一条 IN，不做 N 次单取**，入参按 `CatalogQueryService.MAX_BATCH`（200）去重截断。
- **块组成由 `type_def` 的数据决定**：有 `collectible` 才出统计块、有子级类型（别的类型的 `parent_types`
  含本类型）才出子级块、配了 `provider_bean` 且实体是 `REGISTERED` 才出源域块——代码里无一处
  `if ("table".equals(typeName))`，加一类实体不改本模块。字段顺序（`ordinal`）与 `deprecated=1` 不上面板
  是前端读 `/types` 的事（工单 129 仍是唯一渲染配置源），后端不下面板。
- **统计块只读采集时落袋的那三份**：`rowCountApprox`/`partitioned`/`lastDdlTime` 取自 `attributes`，
  配 `approximate=true` 与 `lastCollectAt` 说明新鲜度；三样全无时报 `EMPTY` 并写原因，**不实时再读一次系统视图**
  （同一事实两个时刻，对账比的就不是同一把尺子）。
- **分区容错是硬要求**：任一块取数失败（provider 未注册、源域 500、历史表读失败）只把**该块**标成
  `UNAVAILABLE` 并带 49025（带码的失败保留原码，如 provider 未装配给 49021）与原因，响应仍 200；
  只有实体本身不存在才整体 49001。缺块必须**可见**，静默吞成空数组等于把"没接上"伪装成"没有"。
- `GET /entities/{id}/changes?pageNo&pageSize`：变更时间线的单独翻页口（详情聚合里只给一屏 20 条，
  `EntityDetailService.HISTORY_PAGE_SIZE`），出参 `PagingData<ChangeView>`，不是 detail 的第二次全量聚合。
- `api/MetadataQueryApi`（plan §5.2）：`search/getEntity/listEntities/listChildren/listPhysicalColumns/findPhysicalTable`。
  除 `search` 走检索计划外其余**共用同一条目录行读路径**（`query` 包里唯一的 `SELECT_COLUMNS` 与行映射，搜索后端也用它）：
  两个便捷方法只是 `type_id`/`parent_asset_id` 过滤的语法糖，**实现里禁止出现第二张表、第二个 DTO、第二套行映射**。
  入参 `EntityQuery` 用**页码**分页（出参是框架 `PagingData`，它只有页码一种形状），进程内检索与 HTTP 检索
  共用 `MetadataSearchService` 的同一份 `SearchRequest`，不各拼一遍查询计划。
  `EntityDTO` 带 `typeName + attributes + slotValues`（槽键翻回字段名），字段清单权威来源是 `/types`，不是常量类。
- 变更历史读 `yak_md_change`（本票只加读侧，写侧自 ticket 114 起已在 upsert/GONE 路径里）；
  标签读 `yak_md_label`（**写侧归工单 124**，本票不开打标签的口子）。标签展示名不在本模块解析——
  码表归 asset，为个显示名引它的内部包不值。
- **血缘只给入口，不做内部调用**：本模块不调 lineage、也不为它加 Maven 边（共表 `id` 与 lineage `assetId` 同源是
  §2.3 的既有事实），详情只回 `assetKey` + 血缘图入口路径 `/data-analysis/lineage?assetKey=<urlencoded>`（图的页面只有
  这一个入参形态），跳转由前端发起。类型还没登记 `lineage_asset_type` 时报 `UNAVAILABLE`（"还不能"≠"没有血缘"）。
- **存储量只读 lifecycle 快照**：本模块不建 mapper 查 `yak_lc_*`（守卫已加此断言）、无 `SHOW DATA`、无
  `information_schema` 字节量查询（既有 grep 守护继续生效）。按表快照由 lifecycle 侧只读端点提供
  （`GET /api/v1/lifecycle/storage/table?datasourceId=&databaseName=&tableName=`），**必须同时按
  `database_name` 过滤**（T16：该表唯一键不含库名，同数据源多库会互相覆盖），
  详情页显示快照日期并标"近似"。T16 属 lifecycle 侧缺陷，如实上报，不在元数据模块偷偷补偿。
- **投影实体（`dataModel`/`standardField`/`domain`/`metric`）实时读源域**：`api/EntityProvider` SPI
  （plan §3.2b 定名，与 asset 的 `AssetProvider` 同构：`typeName()` + `Optional<EntityProjection> refresh(sourceId)`；
  本模块只收集只调用，**绝不 import 源域内部包**），实现类放各源域、由源域反向依赖本模块的 api 包
  （本期只有 modeling 的 `modelEntityProvider`）。provider 寻址取 `type_def.provider_bean`（V2 已种 `modelEntityProvider`
  等四个 bean 名），不是代码里的类型常量；取到 bean 后还核对它的 `typeName()`——名字对上类型接错了会把模型清单
  贴到指标详情上，宁可降级不可错贴。源域已无此实体时报 `EMPTY` 并写明"等对账通道（ticket 135）撤销"，不是 `UNAVAILABLE`。
  `EntityProvider.cursorList` 与 `EntityProjection`/`RegisterCommand` 合流共用同一个 DTO 是工单 135 的第一步，本票不抢跑。
  改源域数据后**刷新即变**，不等一轮对账、不重跑登记；列清单/公式这类源域事实只出现在 `refresh` 的 `extra` 里、
  **目录一个字节都不存**（§10 测试 10：断言投影实体的 `attributes` 不含源域业务内容）。
- 语义回流一期口径：详情页把"该列注释"与"关联标准字段"并排（`extra.columns` 同时给 `comment` 与 `stdFieldId`），
  人工点「沉淀为标准字段」调 **semantic 既有接口**，本模块不新造标准字段写入路径，**不做 NLP 自动抽取**（错误沉淀不可逆）。
- 权限 `MetadataPermissionCode.READ` + `PROJECT_REQUIRED`（`/api/v1/metadata` 前缀工单 111 已登记）。
- 验证状态：编译 + 元数据模块 **310 单测全绿**（新增 `EntityDetailServiceTest` 18 条分区容错、
  `CatalogQueryServiceTest` 4 条行映射、`MetadataQueryApiImplTest` 5 条分页换算，守卫加 `yak_lc_*` 断言）、
  modeling **147 全绿**（新增 `ModelEntityProviderTest` 4 条分袋契约）、lifecycle **43 全绿**（按表快照端点）。
  端到端**待用户重启后端**后随工单 123 的详情页走查一并验。

## Phase 7 / Issue #194：Metadata 技术运维总览

- `GET /api/v1/metadata/overview` 只服务元数据采集与技术目录运维，不提供与 Asset 平级的通用发现入口；用户从这里查看采集配置并进入已有 Asset 目录发现资产。
- 所有目录、任务、运行统计按 `CurrentProject.requireProjectId()` 过滤。目录统计只读 Metadata steward 的目录列；不访问 `yak_metadata_asset.properties`、其他治理域表或源端系统视图。
- 返回实体类型与当前目录实体数、物理采集/源域对账任务数及启用/待预演数、最近 5 次运行的状态/计数/耗时、未办结 Metadata 待办数（含待补注释、待确认标签等任务分类）。`SUSPECT` 明确展示为熔断的疑似坍塌运行。
- 目录实体数量是已登记实体的事实计数，不具备源端全量对象分母时不展示为覆盖率百分比。
- 服务最多执行 4 次有界查询，运行记录固定限制为最近 5 条；读权限沿用 `MetadataPermissionCode.READ`，路由使用 `PROJECT_REQUIRED`。
- 前端区分接口不可用、暂无运行记录、未配置任务和无待办；只有实际读取到 `EMPTY` 的数据才展示为空，不用空响应掩盖服务错误。

---

## 待补（后续 ticket 开工第一步追加）

133 共表目录列 · 113 抽 `LineageRegistrationApi` · 134 键生成器下沉与枚举加值 ·
114~116 采集/GONE/调度 · 131+135 源域挂钩与投影对账 ·
119~121 三方消费 · 122/123/132 前端 · 124~127 治理与守卫

## P0-B02：Metadata 采集缺席判定策略必须诚实呈现

- 现有 HARVESTED Presence 判定沿用全局 `yak.metadata.harvest.gone-collapse-ratio` 与连续两次有效采集缺席。完成一次完整采集且证据充分才可 GONE；空集、dry-run、失败/不完整作用域、骤减 SUSPECT 继续保护，不修改软删与血缘撤销规则。
- 旧任务字段 `collapse_threshold_pct`、`missing_rounds` 已可保存但未参与执行，属于兼容历史存储，**不得再作为可编辑、已生效的任务级策略提供**。新建/更新若传入与当前有效策略不同的数值，明确拒绝而不是静默保存。
- 增加受 `data-metadata:read` 权限和 Project Scope 保护的只读当前有效策略查询；前端由服务端查询后显示，无法读取时标记策略暂不可用，不硬编码“30% 必定生效”。
- 本次不开放自定义缺席轮数。若未来正式支持不同任务策略，先冻结 Product/Domain 契约，规定作用域、历史有效轮次、配置变更、运行证据和默认优先级，再修改 Presence 执行语义及历史结构。
- 验收：不同全局熔断值下查询返回实际值；非有效自定义值被拒；UI 无可误导的任务级输入；旧任务更新不会把历史遗留配置当成当前生效策略；原有 Presence 保护用例保持通过。
