# Ticket 134：共键改造——键生成器下沉 + `LineageAssetType` 加值

> ⚠️ **跨模块 Java 改动，唯一一张不能独自开工的票。** 涉及 `data-ops-common`、`data-ops-business-data-development`、`data-ops-business-lineage` 三处，**须与 lineage / data-development 一起排期，不由元数据单方面提交**（plan §2.3 后果 7）。

**对应需求：** 元数据中心（存储） | **阶段：** P0 | **模块：** common + data-development + lineage

**What to build：** 目录与血缘**共用一把 `asset_key`** 的物理前提。这是 B 案"不建第二套真相"能不能真正成立的卡点：键不同源 → 同一实体裂成两个节点，而**没有任何唯一键会报错**（plan §2.4.3）。

**Blocked by：** 无（但 114/130 全部依赖它）

**验收清单**
- [ ] 把 `TableIdentityResolver.PhysicalTableIdentity#assetKey()` 的**纯字符串逻辑下沉到 `data-ops-common`**，data-development 改为引用同一份，**行为逐字不变**并由其现有测试守护
- [ ] 元数据侧**不得 import 别模块内部包**（plan §0.3；`ModelTtlQueryApi`/`ModelTtlQueryApiImpl` 是正例，`StandardFieldMatcher:3` 是反例）
- [ ] `LineageAssetType` 增加 `DATABASE_SERVICE` / `DATABASE` / `DOMAIN` 三个常量（`asset_type` NOT NULL 且被 `valueOf` 解析，后果 1）
- [ ] `./mvnw -q -o -pl data-ops-common install -DskipTests` + data-development 与 lineage 单模块测试全绿
- [ ] 契约测试锁定：同一入参下，下沉前后 `assetKey()` 输出**逐字节相同**（防"顺手规范化"）

**为什么不让元数据自己拼键**：现网的键就是各源域自己生成的（`modeling:model:{id}` 出自 `ModelingLineageRegistrationService.java:275`，`semantic:field:{id}` 出自同文件 `:184`；物理表键出自 data-development 的 `TableIdentityResolver`）。元数据按"更整齐"的格式另起一套时两行各自满足唯一键，数据库不报错，只会静默产生第二份真相（plan §3.2b 硬约束 4）。
