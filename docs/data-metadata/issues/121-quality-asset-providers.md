# Ticket 121：quality 选择器切数据源 + asset 的 TABLE provider（消费方 #2/#3）

**对应需求：** 元数据被三方消费 | **阶段：** P3 | **模块：** quality + asset（被消费方改造）+ metadata（provider 实现）

**What to build：** 两个既有挂载点改读元数据：质量规则表白/列选择器（原来实时打 `DataSourceCatalog`）、资产台账的物理表来源（`AssetSourceType` 原来没有 `TABLE`）。

**Blocked by：** 118、133、134

**验收清单**
- [ ] `AssetSourceType` 加 `TABLE` 一枚；实现类放**元数据模块** `metadata/asset/MetadataTableAssetProvider.java`（`AssetProvider` SPI 已是正确形状：`sourceType()/cursorList()/refresh()`）
- [ ] **`assetKey` 必须复用本域血缘登记键生成器，与 `yak_metadata_asset` 同源**（SPI javadoc 原文）。这与 plan §2.3 后果 6、§3.2b 硬约束 4 是**同一条约束的三次表述**——asset SPI / metadata provider / lineage 生成器三方都只复用、不另起键，这才是"同源"的真正保障，**不靠任何一把唯一键拦**
- [ ] `QualityTableAssetRepository` 改造：选择器读元数据查询 API，**首屏不打数据源**
- [ ] **保留实时 catalog 作为元数据缺失时的回落**，并在 UI 标注来源（已采集 / 实时）。理由：采集默认每日一次，切干净会让"刚建的表选不到"变成新问题
- [ ] 可筛"有注释 / 无注释"（这是元数据比实时 catalog 多给的维度）
- [ ] 消费方 #5（低成本附带）：按 `layer_code` 统计表/列/注释覆盖率，供 semantic 分层覆盖度使用
- [ ] 消费方 #6（metric 源表存在性校验）**明确留二期**，不在本票
