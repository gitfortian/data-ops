# Ticket 114：采集核心——SPI 直采 + 双指纹 + 服务端判增量 upsert

**对应需求：** 物理元数据采集 | **阶段：** P1 | **模块：** metadata

**What to build：** 一轮采集把某个作用域（数据源/库）的物理表与列落成 `table` / `tableColumn` 两类实体行，并产出 NEW/CHANGED/UNCHANGED/GONE 四个计数。**重跑幂等**：一轮"什么都没变"的采集除 `last_collect_at` 外零写入、零变更流水、零通知。

**Blocked by：** 128、129（类型/槽位解释规则）、134（共键）

**进程内，不引新组件**（plan §3.1）：`DataSourceCatalog` SPI 已在进程内，调用即得，所以**跳过 OM 那 90+ 个方言目录**（这是本模块能在十余张票内落地、而不是三十张的原因）。跨进程批量 HTTP 提交、HTTP 重试、连接器水位续传**三类机制整体不采纳**。

**验收清单**
- [ ] `MetadataHarvestService`：`listDatabases() → listSchemas(db) → listTables(query) → listColumns(tablePath)`，游标**分批 ≤500**（plan §0.11，禁止一次 `listTables` 全载入内存）
- [ ] 表级/列级属性按 `DataSourceTable` 5 字段 + `DataSourceColumn` 9 字段落 `md_attributes`（plan §3.2）
- [ ] `content_hash` 规范化：`表级 = join('|', type, comment, 列指纹…)`；`列指纹 = join(':', lower(name), lower(typeName), jdbcType, size??-1, scale??-1, nullable?1:0, ordinal, pk?1:0, normalize(comment))`；列**按 `ordinalPosition` 升序**，ordinal 缺失/全 0 时退化为 name 字典序（plan §3.3）
- [ ] `normalize(comment)` = 折叠连续空白 + 去首尾；`name`/`typeName` 转小写，**注释不转小写**
- [ ] 指纹字段清单**写成常量集合、落在一个类里、由单测锁定**。易变项绝不进指纹：`href`、`deleted`、`inherited`、**认证的 `appliedDate/expiryDate`**（否则"认证过期"每天伪装成"表结构变更"刷屏）
- [ ] 服务端判增量：upsert 用 `ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)` + `<=>` 比较 hash；**`HARVESTED` 只比 `content_hash`，`REGISTERED` 只比 `source_hash`**（plan §3.3 分岔）
- [ ] **UPDATE 子句里没有 `asset_type` / `parent_asset_id` / `properties`**（steward 分界，plan §2.3 后果 1/2）：INSERT 时按 `type_def.lineage_asset_type` 写一次图形状，之后不刷
- [ ] `create_time`/`update_time` 显式带值（被复用表的这两列 NOT NULL 无默认，strict 模式直接报错）
- [ ] `last_collect_at` 单独刷、**不触发 CHANGED**（与 asset 的"对账只刷在场时间不复活"同口径）
- [ ] 统计层 `MetadataStatsProvider`（**只补 SPI 缺的**：行数/分区/最后 DDL 时间），Registry 按 `supports()` 路由（照 `AssetProviderRegistry`）；SQL 沿用 `StorageSnapshotService.statementsFor()` 的"候选语句按序尝试"+ 库名白名单校验；**一期只写 MySQL 实现且行数标"近似"**，Doris 路径不得写成已验证（plan §9 T12）
- [ ] **`size_bytes` 不在这里**：存储量读 lifecycle 快照，**本模块不建 mapper 查 `yak_lc_*`**；读时**必须同时按 `database_name` 过滤**（plan §9 T16：快照唯一键不含库名，同数据源多库会互相覆盖）
- [ ] 非 JDBC 插件 `listColumns` 空/异常 → 记该表 `PARTIAL` 失败，**绝不静默空表**（静默空表会让下一轮误判"全部列被删除"，plan §3.2 边界说明）
