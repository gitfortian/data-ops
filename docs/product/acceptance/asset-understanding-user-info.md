# Asset Understanding 验收案例：用户信息表

- **文档类型：** 验收证据（Golden Asset 候选）
- **状态：** DRAFT — 样本事实已核实；产品行为验收尚未执行
- **事实快照：** 2026-09-23，只读核对默认项目空间 `project_id=1`
- **关联契约：** [PD-001 — Asset Governance Hub](../decisions/PD-001-asset-governance-hub.md)、[F-001-A — Asset Section Contract](../features/F-001-A-asset-section-contract.md)

本案例记录一个真实资产及待验证的用户问题，作为产品验收证据，不自动成为 Product Truth、Feature 实施指令或新的 API / Contract 设计。文中的字段比较是候选验收问题；执行前必须遵守下方记录的现行契约适用性。

## 样本身份

| 项目 | 已核实事实 | 来源 |
| --- | --- | --- |
| 项目空间 | 默认空间，`project_id=1` | 只读项目数据查询 |
| 治理资产 | ID `8`，`用户信息表`，`asset_key=modeling:model:49`，类型 `TABLE`，来源类型 `MODEL`，状态 `PENDING` | Asset Registry `yak_asset_item` |
| Asset owner | `root` | Asset Registry `yak_asset_item.owner` |
| 模型资产 | Model ID `49`，模型名 `用户信息表`，模型表名 `ods_cust_info` | Modeling `yak_modeling_model` |
| 来源映射 | 数据源 ID `3`，`crm_db.crm_customer` | Model `source_datasource_id` / `source_database` / `source_table` |
| 模型字段 | 14 个 | Modeling `yak_modeling_model_column` |
| 采集元数据 | 14 条：1 条表记录及 13 个不同字段记录；最近采集时间为 `2026-09-21` | Metadata `yak_metadata_asset`，项目空间、数据源、库和表均与上述来源映射匹配 |
| Metadata owner | 未提供（表及字段记录的 `owner_user` 为空） | Metadata `yak_metadata_asset.owner_user` |

未查询物理来源表的业务行；本案例只使用 Asset、模型结构和已采集的元数据事实。

## 现行契约适用性

该治理资产的 `source_type` 是 `MODEL`。F-001-A §14「场景 B：Model」规定 Model Asset 的 Technical Metadata 和 Quality 为 `NOT_APPLICABLE`。因此：

- 下列模型结构与采集字段的比较是待验证的跨域用户问题，不能据此要求当前 Technical Metadata Section 在 Model Asset 上返回物理元数据。
- Q1 的身份链可用于检查 Asset、Model 与模型来源映射是否能被用户理解；各事实仍由各自拥有域负责。
- 在产品契约没有明确调整前，当前 Model Asset 的 Technical Metadata 预期仍为 `NOT_APPLICABLE`。
- 本案例不新增 Section 状态。F-001-A 当前定义 `OK`、`EMPTY`、`NOT_APPLICABLE`、`UNAVAILABLE`、`PERMISSION_DENIED`，没有 `MISSING`。单个 owner 未提供应作为字段级未知事实呈现并保留来源，不把它扩展成新的 Section 状态。

## 用户问题与预期证据

### Q1：这个资产是什么？

**预期可核验的回答：**

```text
治理资产：用户信息表（Asset 8）
模型资产：用户信息表（Model 49），模型表名 ods_cust_info
来源映射：数据源 3 / crm_db.crm_customer
```

**证据来源：** Asset Registry identity、Model 元数据、Model source mapping。回答应区分模型表名与来源表名，不把映射写成同一张物理表。

### Q2：模型字段与采集字段是否对应？

**观测结果：**

```yaml
model_fields: 14
metadata_fields: 13
matched_fields: 12
model_fields_without_metadata:
  - process_time
  - event_time
metadata_fields_without_model_match:
  - etl_time
```

这是字段名对应关系的比较，不是数据完整性、质量或可信度评分。验收必须能指出比较双方及差异，不能把结果改写成“字段完整率”而隐藏额外字段或口径。

**证据来源：** Model Column 列表、Metadata 已采集 Column 列表、两份列表的确定性比较结果。

**契约状态：** 候选跨域问题；受上方 Model Asset 的 Technical Metadata 适用性规则约束，当前尚未验收为 Section 行为。

### Q3：谁负责？

**预期可核验的回答：**

```yaml
asset_owner:
  value: root
  source: Asset Registry
metadata_owner:
  value: unknown
  source: Metadata collector record
```

`root` 是 Asset Registry 的治理联系人事实；它不能覆盖 Metadata 记录中 owner 为空的事实，也不能被描述成 Metadata owner。

### Q4：有哪些已知风险线索？

模型字段注释包含手机号、身份证待脱敏，以及性别、注册来源、会员等级、状态等枚举不统一的描述；表注释指出数据存在脏数据、逻辑外键无物理约束。

验收回答必须把这些内容归因于模型/元数据注释，并与检测结论分开。没有运行安全策略检查或质量规则时，不得声称“未检测到脱敏策略”“存在已确认质量问题”或给出置信度分数。

## 缺失、空结果与依赖失败

- Metadata owner 为空时，owner 值应明确为未知，并保留 Asset owner 与 Metadata owner 的不同来源；这不是 Section 级 `MISSING` 状态。
- 对适用的 Section，只有在查询成功并确认没有记录时才使用 `EMPTY`；查询依赖失败时使用 `UNAVAILABLE`，说明原因，并让其它 Section 继续可用。
- 对本案例当前的 Model Asset，Technical Metadata 遵守 F-001-A 的 `NOT_APPLICABLE` 约定。若将来要在此资产展示来源物理元数据，先完成产品契约的适用性决策，再把该行为纳入验收。

## 验收证据与未完成项

1. 静态事实已核对：Asset、Model、source mapping、字段列表及 Metadata 采集记录均限定在 `project_id=1`。
2. 需要通过已授权的产品路径验证 Q1、Q3、Q4 如何展示，并确认每项结论能回溯到对应事实来源。
3. Q2 当前作为 Contract Gap Analysis 输入；在 F-001-A 的 Model 适用性规则未解决前，不将其判作 Technical Metadata Section 的通过条件。
4. 需分别验证适用 Section 的确认空结果与依赖不可用行为；不能用同一个空响应代表两者。
5. 端到端产品验收尚未执行，因此本案例当前不宣称通过，也不作为关闭 Issue #11 或其关联 Issue 的证据。

## 明确不在本案例范围内

- 新增通用 Contract 字段、Section 状态或 Provider；
- 定义 Asset Understanding 聚合 API 或 Reasoning API；
- 生成 confidence 数值、质量结论或安全策略结论；
- 读取来源表业务行或修改默认项目空间数据。
