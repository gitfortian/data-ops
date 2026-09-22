# Ticket 128：元模型落地——两张定义表 + 两个 registry

**对应需求：** 统一可扩展实体（本次范围修正的地基） | **阶段：** P0 | **模块：** metadata + common

**What to build：** "**类型与字段是行、不是代码**"这一条落地——加一类元数据 = 插一行 `type_def`，加一个可搜字段 = 插一行 `field_def`，**都不改表结构**。这是"统一检索"能成立的前提，不是锦上添花。

**Blocked by：** 112。**必须排在 114 之前**：没有类型注册表与槽位登记，`type_id`/`md_attributes`/提槽列就没有解释规则，采集上来的是无法渲染也查不动的裸 JSON（plan §7）。

**采纳 OM 的哪一层 / 不采纳哪一层**（plan §11.1 第 4 条，判据必须逐层给）：
✅ 采纳"**类型即数据的元模型**"；❌ 仍不采纳 **JSON Schema → 代码生成**。两者是两件事，**不引运行时 schema 校验器**——"加字段免改表"的全部所需就是"类型与字段是行"。

**验收清单**
- [ ] `yak_md_type_def` DDL **逐字照抄 plan §2.2**，含 `search_default_weight` / `search_include_by_default` 两列 **随建表一起出，不留后补 ALTER**（plan §7 ticket 128）
- [ ] `yak_md_field_def` V1 基线，含 **8 类一期实体的 INSERT**（采集侧 `databaseService`/`database`/`table`/`tableColumn`，
      注册侧 `dataModel`/`standardField`/`domain`/`metric`，键/FQN pattern **必须复刻源域既有格式**，plan §2.3 后果 6 表；
      `databaseService`/`database`/`domain` 三者依赖 ticket 134 的 `LineageAssetType` 加值，未加值前保存即被后果 1 的校验拒）
- [ ] **`tableColumn` 默认不进检索面**（`search_include_by_default=0`）：列约为表 12.6 倍（实测 373 表 / 4684 列），一期不还这一条就是"搜索结果被列淹没"（plan §4.6）
- [ ] `MetadataTypeRegistry`：类型/字段解析 + **失效缓存**（新增 `field_def` 后不重启即生效，plan §8 P0b）
- [ ] `MetadataSlotRegistry`：7 个提槽集中登记，**两类型抢同一槽 → 第二次分配报错，绝不静默复用**（plan §2.4.1）；"槽位够不够"在第一天就被评审，而不是等到第 20 个扩展字段
- [ ] 层级用**类型对**声明 `(table, tableColumn, refersTo)` + 实体行 `parent_asset_id` 挂父，**不靠 `parent_types`**（这是对 OM 的主动偏离：它用 ES 嵌套文档解决列检索，我们没有 ES，plan §2.2 末）
- [ ] `base_type` 与 `field_type` 并存是**刻意的**：`field_type` 走 OM"字段类型也是一个 Type"，`base_type` 是唯一真正决定"怎么存、怎么筛"的判别（借思想不借实现）
