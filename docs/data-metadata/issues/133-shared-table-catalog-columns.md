# Ticket 133：共表改造——给 `yak_metadata_asset` 追加目录列（B 案专属）

> ⚠️ **跨模块票：不得由元数据单方面提交。** 迁移落在 **lineage 的** `db/migration/yak-lineage`，须与 lineage owner 一起评审。

**对应需求：** 元数据中心（存储） | **阶段：** P0 | **模块：** yak-ops-business-lineage（被改）+ metadata（steward）

**What to build：** 统一实体表 = **就地扩展 lineage 既有的 `yak_metadata_asset`**（plan §11.1 第 5 条，A 案作废）。买到的核心一条：§0.2"不建第二套真相"自此**由表结构本身保证**，平台不出现第三张目录形状的表，也不用再写第四个 `*LineageSynchronizer`。

**Blocked by：** 110（错误码/PO 就位）；与 112 并行

**验收清单**
- [ ] `V2__add_metadata_catalog_columns.sql` 落 `db/migration/yak-lineage`（版本号续 lineage 现状），**`V1__baseline_lineage.sql` 在 `git diff` 里零改动**（plan §9 T5）
- [ ] 新增列**逐字照抄 plan §2.3 的 ALTER**：`type_id` / `display_name` / `fully_qualified_name` / `fqn_hash` / `summary` / `owner_user` / `domain_ids` / `tier_label` / `layer_code` / `entity_status` / `provider_type` / `collect_job_id` / `content_hash` / `source_hash` / `source_updated_at` / `first_seen_at` / `last_collect_at` / `last_change_at` / `gone_at` / `catalog_version` / `updated_by` / `md_attributes`
- [ ] **7 个提槽生成列一次建齐**（`s_str_1..3`/`s_num_1..2`/`s_bool_1`/`s_date_1`）。实测约束：同一条 ALTER 里 `ADD COLUMN … STORED` 只能 `ALGORITHM=COPY`（plan §2.4.1，1845 已复现）→ 迁移注释里写明锁表窗口与运维要求
- [ ] **`type_id` 与 `lineage_asset_type` 成对出现**：目录判别走 `type_id`，`asset_type` 只图表形状（后果 1：`LineageAssetType` 是闭集 Java 枚举 + 读侧 `valueOf`）
- [ ] **不新增任何唯一键**：`fqn_hash` 只加普通索引。身份就是 lineage 那把 `uk (project_scope_id, asset_key)`（后果 4、plan §2.4.3）。`KEY` 与 `FULLTEXT … WITH PARSER ngram` 按 §2.3 清单建
- [ ] **"四个只有目录知道的列"一律可空**（`fully_qualified_name`/`fqn_hash`/`entity_status`/`content_hash`），遗留行用 NULL 承载"未纳入目录语义"，**不填假值**（后果 3）。已在 234 行真表副本上预演：可空版本通过、`NOT NULL DEFAULT ''` + UNIQUE 版本直接 1062
- [ ] **steward 列契约写进两份 `ARCHITECTURE.md`**（lineage 拥有 `asset_key/asset_type/parent_asset_id/properties` 的既有语义；metadata 拥有 `md_attributes`、`s_*` 与目录治理列；两侧都不得写对方列；目录读侧**永不使用 `properties`**）——只写在方案里等于没写（plan §2.5 三条硬要求）
- [ ] **lineage 写入路径回归**（专防后果 5，`LineageWriteMapper.xml`）：`upsertAsset:16-32`（`:26` 覆写 `project_id`、`:31` 整包覆写 `properties`）与 `upsertAssets:47-65`（`:59`/`:64`）跑过后，目录列 `md_attributes`/`entity_status`/`fqn_hash`/`project_id` **不被清空或覆写**；`selectAssetForUpdate:82-94` 在 `projectId==null` 时不加 project 谓词、`claimLegacyAssetProject:5-14` 的搬行行为**以可识别错误冒出**（撞既有 `uk_yak_metadata_asset_project_key` → 1062），不是未知 500
- [ ] lineage 既有测试全绿；三个 `*LineageSynchronizer`（analysis/dashboard/dataset）回归通过

**残余风险（必须写进契约，不许藏）**：`project_id` 被刷成 NULL → 行落进 `project_scope_id=0` 全局桶 → 与真实项目行并存，**没有任何键会拦**，只能靠 ticket 119 的看门狗查询发现（plan §9 T19）。
