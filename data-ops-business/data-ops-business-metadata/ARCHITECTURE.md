# Metadata Architecture

## 包结构

```
io.yak.ops.business.metadata
├── asset/      MetadataTableAssetProvider · MetadataAssetSectionProvider（物理表资产投影与技术元数据分区读适配器）
├── config/       ConditionalOnMetadataPersistence · MetadataPersistenceConfiguration(Flyway/MapperScan)
│                 · MetadataSchedulingConfiguration(@EnableScheduling，可单独摘除)
├── api/          MetadataRegistrationApi + RegisterCommand(源域调用，ticket 130 已落地；
│                   RegisterCommand 即 135 的 EntityProjection 共用 DTO)
│                 · EntityProvider + EntityProjection(源域实现，118 已落地：modeling 的 modelEntityProvider)
│                   EntityPage 仍归 135(与 cursorList 合流时一并收口)
│                 · MetadataQueryApi + EntityDTO/EntityQuery(对外提供，118 已落地，119 起为消费方)
│                   · MetadataStatsProvider(harvest/stats，按方言路由)
├── metamodel/    MetadataTypeRegistry(类型/字段解析+缓存失效) · MetadataSlotRegistry(7 槽集中登记)
│                 · MetadataKeyCodec(**fqn_hash / fully_qualified_name 的唯一生成处**) · TypeDefAppService/FieldDefAppService(级联校验)
├── harvest/      MetadataHarvestService(游标 ≤500) · MetadataFingerprint(纯函数，字段清单写死在常量集合)
│                 · AssetUpsertRepository(两条入口共用的唯一 upsert) · GoneDetector + CollapseBreaker
├── register/     RegistrationAppService(api 实现：永不向源域抛异常，失败一律 enqueue) · MetadataRegistrationService(落库/保序/软删)
│                 · RegisterRetryStore(outbox) · RegisterRetryWorker(@Scheduled fixedDelay)   —— 以上 ticket 130 已落地
├── reconcile/    EntityProviderRegistry(多 bean 收集) · ProjectionReconcileService(补漏/刷 source_hash/判 GONE)
├── query/        MetadataSearchBackend(接缝) · MysqlMetadataSearchBackend · SearchConditionBuilder(**由 field_def 生成，无类型分支**)
│                 · BooleanModeEscaper · CatalogRowRead + CatalogQueryService(**目录行读路径的唯一出处**，搜索与按键直读共用)
├── detail/       EntityDetailService(分区容错 fan-out：一块一个来源，本包一条 SQL 都不写) · SectionState(OK/EMPTY/UNAVAILABLE)
├── governance/   MetadataGovernanceQueryService(变更历史 + 标签的读侧，118)
│                 · LabelService(溯源/继承) · GovernanceTaskService(open_marker) · CertificationScanner —— 写侧仍归 124
├── repository/   MetadataDataSourceReferenceProvider(数据源删除守卫 SPI 适配器)
├── stat/         MetadataOverviewService(≤8 查询预算) · WatchdogQueries(共表在场行断言)
├── schedule/     MetadataScheduleEngineBridge + MetadataCollectScheduleHandler
├── exception/    MetadataException + MetadataExceptionHandler(49001~49099)
├── dao/mapper/   自持 9 张表的 BaseMapper
└── controller/v1/ MetadataSearchController · MetadataTypeController · MetadataEntityController
                  · MetadataCollectJobController · MetadataGovernanceController · MetadataOverviewController + dto/
```

**纯函数优先**：指纹计算、BOOLEAN MODE 转义、槽位冲突判定、坍塌比例判定均为 static 纯函数，单测不打 Spring 上下文。

## 持久化

- 自持 Flyway：`classpath:db/migration/yak-metadata`，历史表 `flyway_schema_history_metadata`，bean `yakMetadataFlyway`。
- **自持 9 张**：`yak_md_type_def` / `_field_def` / `_asset_extension` / `_collect_job` / `_collect_run`
  / `_register_retry` / `_change` / `_label` / `_task`。
- **共管 1 张**：`yak_metadata_asset` —— 表基线与既有列归 lineage，目录列由本方案追加。
  **目录列的 `ALTER` 落在 `db/migration/yak-lineage` 的*新版本*文件**（绝不改 `V1__baseline_lineage.sql`，
  改已应用文件 = checksum mismatch 启不来）。
- 共享数据源 Boot 的 `config.persistence.BusinessDatabaseConfiguration` 应用装配；事务 `@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO 由本模块 `dao.model` 拥有；错误码、权限码和枚举保留 common 的稳定共享契约。
- 菜单注册在 data-ops-boot 的 yak-security 迁移 `V2033__register_data_metadata_menu.sql`。
- 调度 namespace `YakScheduleNamespaces.DATA_METADATA = "yak-ops-metadata"`。

## ⚠️ 共表列 steward 契约（B 案的核心，lineage 与本文件的 ARCHITECTURE.md **必须同时记载**）

> 一句话：**共管的是列，不是语义。** 两侧各自只写自己 own 的列；读侧永不使用对方的属性袋。

| 列 | 写入方 | 读取方 | 约定 |
| --- | --- | --- | --- |
| `asset_key` `name` `parent_asset_id` `source_type` `source_id` `data_source_id`…`column_name` | lineage | 双方 | 本模块 INSERT 时按 provider/collector 交出的值写一次，**UPDATE 子句里没有它们** |
| `asset_type` | lineage | 双方 | 本模块 INSERT 时按 `type_def.lineage_asset_type` 映射写一次，保存时用 `LineageAssetType.values()` 校验；之后不刷。**只回答"图里长什么形状"，不是目录判别列** |
| `properties` | **lineage 独占** | **仅 lineage** | 本模块**永不读写**。lineage 的两种 upsert 都是 `properties = VALUES(properties)` 整包覆写，共用即静默丢键 |
| `project_id` / `project_scope_id`(生成列) / `create_time` / `update_time` | **双方都写 → 最高风险** | 双方 | 本模块登记出的行 `project_id` **必须非空**（入库前断言 49xxx）；lineage 的 upsert 含 `project_id = VALUES(project_id)`，全局路径把行刷成 NULL 会让它搬进 `project_scope_id=0` 的全局桶，与原行**并存且无键可拦** → 靠约定 + 看门狗。`create_time`/`update_time` NOT NULL **无默认值**，本模块 upsert 必须显式带值 |
| `type_id` `display_name` `fully_qualified_name` `fqn_hash` `summary` `owner_user` `domain_ids` `tier_label` `layer_code` `entity_status` `provider_type` `collect_job_id` `content_hash` `source_hash` `source_updated_at` `first_seen_at` `last_collect_at` `last_change_at` `gone_at` `catalog_version` `updated_by` `md_attributes` `s_str_1..3` `s_num_1..2` `s_bool_1` `s_date_1` | **metadata 独占** | 双方只读 | lineage 的列清单不含它们 ⇒ 加列对既有写入路径是**加法不是改写**（ticket 133 回归必须证明这一点） |

**为什么不能靠 DDL 拦**：全表唯一的键是 lineage 的 `uk (project_scope_id, asset_key)`；本模块**不新增唯一键**
（`fqn_hash` 是 `asset_key` 的派生值，给它再立键既冗余、又在现网 234 行上直接 1062 建不起来）。
所以三条不变式全部落在**代码层 + CI**：① 键由源域交出；② `fqn_hash`/`fully_qualified_name` 只在 `MetadataKeyCodec` 一处生成（grep 守护）；
③ 每个已登记 `type_def.lineage_asset_type` 必须是存活的 `LineageAssetType` 常量名（否则 lineage `valueOf` 读行时炸别人的血缘查询）。

**看门狗（唯一能发现归属被改的手段）**：按 `asset_key` 分组统计 `gone_at IS NULL` 的行数，断言 ≤ 1。

## 提槽与生成列

7 个共享槽位（`s_str_1..3` / `s_num_1..2` / `s_bool_1` / `s_date_1`）由 `field_def.storage_slot` 指向，
把热点扩展字段固化为 STORED 生成列。**为什么是共享槽位而不是 per-field 生成列**：单表混装所有类型，
per-field 会让表宽无界增长。**为什么必须一次建齐**：实测 `ADD COLUMN … GENERATED … STORED` 只支持
`ALGORITHM=COPY`（`INSTANT`/`INPLACE` 均报 1845）= 整表复制、期间不能并发 DML。
代价：两个类型不能同时用同一个槽做不同语义索引 → 冲突由 `MetadataSlotRegistry` 报错，不静默复用。
命名债如实记录：主表叫 `yak_metadata_asset`（lineage 前缀），侧表叫 `yak_md_asset_extension`（本方案前缀）且指向别人的主键——
**前缀一致 vs 归属清晰，这里选归属清晰**。

## 搜索

`MetadataSearchBackend` 一期唯一实现 `MysqlMetadataSearchBackend`（ngram FULLTEXT `(name, display_name, summary)`，V1 就建）。
`q` 走 `MATCH … AGAINST(? IN BOOLEAN MODE)`（特殊字符集中转义），单字查询降级 `LIKE` 并在 `explain` 标注；
`index` 多值 = 类型名 → 按 `type_id` 过滤；`queryFilter` 参与聚合计数、`postFilter` 不参与；
`search_after` 游标替代 offset 深翻；`trackTotalHits` 默认 false（无界禁止）。
**跨类型必须是真的一次查询**（SQL 条数不随类型数增长），类型只是 `GROUP BY type_id` 一个 facet。
ES 升级阈值：目录在场行 > 10 万且 P95 > 1.5s / 需要相关性打分 / 需要向量检索——三条任一成立前引入都是纯负债。

已落地（ticket 117，契约细节见 REQUIREMENTS.md 同名段）：`query` 包 = 接缝 + `SearchConditionBuilder`（SQL 形状唯一出处）
+ `MysqlMetadataSearchBackend`（恒 3~4 条语句）；`SearchPlan` 用字段分界承载 queryFilter/postFilter 语义，
IDENT 只出自白名单映射，用户输入全部绑参；`explain` 回占位 SQL 不回字面值。
> 该后端里凡"逐行累加进 Map"的 `jdbc.query` 必须显式 `(RowCallbackHandler)`：一参 lambda 只要带 `return`，
> 就静默改绑 `ResultSetExtractor`——整段只对停在首行之前的游标跑一次，取值即抛 SQLException。

## 详情聚合（ticket 118，契约见 REQUIREMENTS.md 同名段）

`detail` 包只做**装配**：一块一个取数动作，各自 try/catch 成 `SectionState`（OK/EMPTY/UNAVAILABLE），
不写 SQL、不解释元模型。目录行的读路径只有**一条**——`query/CatalogRowRead`（`SELECT_COLUMNS` + 槽位列 +
行映射），搜索后端与 `MetadataQueryApi` 的四个便捷方法都从它出，"禁止出现独立表"由此结构保证而非靠自觉。

三块跨域事实的**方向**各不相同，都为了守住"metadata 是下层事实供给方"：

| 块 | 走法 | 为什么不选另一种 |
| --- | --- | --- |
| 血缘 | 只回 `assetKey` + 入口路径，前端跳 lineage 既有端点 | 给 metadata 加 lineage 边就去查图，聚合接口替消费方决定了他要哪一跳 |
| 存储量 | lifecycle 自己出按表只读端点（按 `database_name` 过滤），前端当独立块取 | metadata 若依赖 lifecycle 读快照，与 modeling→metadata 那条 SPI 边**成环**（lifecycle 已依赖 modeling）；本模块也不建 mapper 查 `yak_lc_*` |
| 源域实时 | SPI `api/EntityProvider` 定义在**本模块**（plan §3.2b 定名），源域实现并依赖本模块（与 asset 的 `AssetProvider` 同构） | 反过来让 metadata 调源域就要 import 别人的内部包，正是 §5.2 禁止的反向依赖 |

## 调度（三段式，形态不同，不要混用）

| 通道 | 机制 | 理由 |
| --- | --- | --- |
| 物理采集 + 投影对账 | `YakScheduleEngineBridge` + `MetadataCollectScheduleHandler`（cron，走 `YakScheduleGateway`），共用 `collect_job`/`collect_run` | 与 lifecycle 同构；不新开第三张运行历史表 |
| 登记失败重试 | `@Scheduled(fixedDelayString = "${yak.metadata.register-retry.poll-delay-ms:1000}")`，**不经平台调度器** | cron 给不了秒级；修复机制不能依赖被修复的对象 |
| 手工触发 | `POST /api/v1/metadata/collect-jobs/{id}/run` | **开发/演示主路径**（Quartz 内存存储重启不补跑） |

两类调度**都必须**先 `projectScope.run/call(new ProjectContext(projectId, null), …)` 恢复项目上下文（调度线程无 HTTP 头，plan §9 T10）。
调度器未装配时 `gateway.available()` 为 false → 静默跳过登记，不阻断启动。

## 验证边界

后端由用户 IntelliJ 启动（:8080）：任何新 Java 类与新迁移在用户重启前**不可端到端验证**，
只能以"编译 + 单测 + DDL 在真库副本预演"为准，**不得写成已验证**（plan §9 T11）。
