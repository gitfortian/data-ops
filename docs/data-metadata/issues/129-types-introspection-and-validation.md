# Ticket 129：类型自省接口 + 元模型级联保存校验

**对应需求：** 统一可扩展实体 | **阶段：** P0 | **模块：** metadata

**What to build：** `GET /api/v1/metadata/types` 返回全部类型与其字段定义，成为**前端渲染与检索配置的唯一来源**；同时把"错配置进不了库"的两处级联校验做在保存路径上。

**Blocked by：** 128

**验收清单**
- [ ] 接口返回 8 类一期实体 + 各自字段定义（含 `display_name`/`icon`/`color`/`searchable`/`storage_slot`/权重）
- [ ] `type_def` 侧校验：`kind=ENTITY` 必填 `key_prefix` / `fqn_pattern` / `lineage_asset_type`
- [ ] **`lineage_asset_type` 必须是 `LineageAssetType.values()` 里的常量名**，否则保存被拒（后果 1：lineage 读侧对每行做 `valueOf`，错值会炸**别人的**血缘查询）
- [ ] `key_prefix` 与该类型登记的键格式自洽（`fqn_pattern` 前缀比对）
- [ ] `field_def` 侧校验：`searchable=1` 而无 `storage_slot` → **49xxx 直接拒、不落库**；`base_type=ENTITY_REFERENCE` 校验 `entity_type_ref` 指向的类型存在
- [ ] 错误码走 `MetadataErrorCode`（49001~），**真机验证不被兜成 999**（plan §9 T3）
- [ ] 前端**零类型常量**：本接口即目录页/详情/筛选项的唯一驱动源（ticket 132 的前提）
- [ ] 单测：三处校验各有正反用例；`MetadataLayeringConventionTest` 不直连 DB 的部分为纯函数

**可扩展性的硬证明在 ticket 128+129 这一对**：插一行 `field_def`（`searchable=1` + 空闲槽位）后**不重启、不改代码**，该字段立刻能被 `queryFilter=attr.x=1` 过滤、能进 `q` 命中。做不到就是元模型白建了，评审时必须判本票不通过（plan §8 P0b）。
