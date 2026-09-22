# Ticket 119：lineage 物理节点登记打通 + 共表看门狗

**对应需求：** 消费方 #4 | **阶段：** P3 | **模块：** metadata + lineage（只走 api）

**What to build：** 采集到物理表即登记为血缘图节点（`LineageRegistrationApi`），血缘图第一次有物理层，边能接上真实表。**B 案下目录行就是图节点**，所以本票的真正产出是"重复目录行可被自动发现"这件事。

**Blocked by：** 113、114、115、133

**验收清单**
- [ ] `MetadataQueryApi` 对外可查；lineage 消费只经 `lineage/api`，不 import 内部包
- [ ] **共表看门狗断言**：按 `asset_key` 分组时 `gone_at IS NULL` 的行数 **≤ 1**。这是唯一能发现 plan §9 T19 的手段——`project_id` 被 lineage 的 upsert 覆写成 NULL 会把行搬进 `project_scope_id=0` 全局桶，与真实项目行并存，**没有任何唯一键会拦**
- [ ] GONE 时经 api 撤销图节点（`SUSPECT` 一律不撤销）
- [ ] 幂等与身份：同一 `asset_key` 二次登记**不产生新行**。`fqn_hash` 只是 `asset_key` 的派生值、**不单独立键**，所以"按 `fqn_hash` 查不到第二行"是上一条的**推论**，不得在测试里当独立断言写
- [ ] **现网 234 行认领不增殖**（有真实数据可比，不必等新采集）：`asset_type='TABLE' AND source_type='MODELING'` 仍 11 行、`semantic:field:%` 仍 8 行、`metric:%` 仍 4 行，只是 `type_id` 从 NULL 变非空；**任一数字翻倍即本条失败**（plan §2.3 后果 6、§10 测试 5④）
- [ ] `claimLegacyAssetProject`（`LineageWriteMapper.xml:5-14`）在 B 案后可能撞 lineage 自己的 `uk_yak_metadata_asset_project_key` → **1062 要以可识别错误冒出**，不让 lineage 吐未知 500
- [ ] 契约测试（§10 测试 5）：元数据产出的 `asset_key` **逐字等于** lineage 键生成器输出；lineage 的 upsert 跑过后 `md_attributes`/`entity_status`/`fqn_hash`/`project_id` 不变
