# Semantic Domain

## 核心概念

### 数据标准(Standard)

平台级的字段级"规矩",分六类(**kind**),统一存储于 `yak_semantic_standard`,以 `kind` 判别,类别专有列可空、由服务层按类别校验:

| kind | 语义 | 专有列 | 专有必填 |
| --- | --- | --- | --- |
| `NAMING` | 命名标准 | scope(表/字段/库)、layer、rule_expr、example | rule_expr |
| `TYPE` | 类型标准 | type_code、std_type、source_mapping(JSON,源库类型映射) | type_code、std_type |
| `CODE` | 码值标准 | code_set_code、code_value、code_label | code_set_code、code_value |
| `UNIT` | 单位标准 | unit_code、unit_type | unit_code |
| `CALIBER` | 口径标准 | caliber_code、cal_rule、business_desc | cal_rule |
| `SECURITY` | 安全标准 | level_code、mask_rule | level_code、mask_rule |

公共列:std_code(编码)、std_name(名称)、status(ENABLED/DISABLED)、version(乐观版本,修改自增)、sort_order、is_preset、description。

### 不变量

1. **编码唯一**:`(project_id, kind, std_code)` 唯一,DB 唯一键兜底;编码创建后不可改(稳定键,同 modeling 模型编码)。
2. **项目空间归属**:全部业务行带 `project_id`,只取服务端可信上下文(`CurrentProject`),不建物理外键。
3. **删除 = 物理删除**:标准是字典项,删除前必须通过引用校验(本模块内过程字段引用 + modeling 侧引用统计,后者随 42 上报并入);历史靠版本快照(32)与审计留痕,不建回收站。
4. **停用是软路径**:被引用的标准应先停用;停用被引用标准时给出阻断提示。
5. **预置标识**:`is_preset=1` 的行来自预置初始化(31),与手工创建行同权管理(可停用可修改)。预置**内容**存于平台级模板表 `yak_semantic_preset_template`(无 project_id,Flyway 种子,ARCHITECTURE 迁移所有权);"初始化预置标准"端点把模板复制为当前项目标准行,幂等键为 `(kind, std_code)`——已存在的编码跳过,保证重复执行不产生重复行。
6. **类型标准的 `source_mapping`** 是源库类型→标准类型的映射 JSON,38 的自动映射唯一规则来源;本模块不解析其内部结构(消费方自读)。

### 业务域 / 业务过程 / 标准字段集(33~35,后续 ticket 落库)

- 业务域:树形(父域/子域),编码项目内唯一。
- 业务过程:挂在业务域下,编码项目内唯一;删除前校验字段集/源表关联引用。
- 标准字段:**全局字段库**,业务过程按引用关联(process_field),不随过程复制;字段的类型/单位/口径/码值/安全**必须引用本模块标准**(std_*_id),不允许自由填写。

### 数仓分层(37,后续 ticket 落库)

层配置(ODS/DWD/DWS/ADS 等):库、数据源引用、命名标准引用(**只存 std_naming_id,不复制 rule_expr**)、默认分区、存储格式、生命周期;分层编码项目内唯一。

### 对外引用契约(消费方视角)

- 消费方(modeling)只存本模块行的**松散 ID**;展示名经 SPI 批量解析(§ARCHITECTURE SPI 面)。
- 消费方**不解析**语义内容、不 join 本模块表。


## F-023 场景 Skill 标准匹配

F-023：StandardSuggestionQueryApi 是 Agent gateway 的授权只读入口。每次检查 semantic 读取权限与当前项目；只查启用 TYPE，SQL 最多 21 行、交付 20 行及截断标识。按 ID/版本复核时拒绝跨项目、停用、错类别、陈旧版本；故障不能折算成无候选。不接收 Agent 写命令。

依赖：Agent runtime → toolset → gateway → semantic.api / modeling.api；源域不依赖 Agent，不新增状态机或第二业务真相。合同见 [F-023](../../docs/product/features/F-023-skill-standard-match.md)。

## 跨域稳定字段引用（P0-B03）

Semantic 的标准字段删除是物理删除，受本域业务过程引用和 Modeling `std_field_id` 持久化引用共同保护：只要任一项目内持久引用存在，就必须阻断删除，不能在 Semantic 自动修改建模模型。通过 Semantic 声明、Modeling 实现的只读引用 SPI 读取，引用查询异常不当作无引用。回收站模型的引用仍可能恢复，解除后才可删除；项目身份始终从可信上下文获取。


## 跨域业务过程 / 业务域稳定引用（P0-B03）

Modeling 可以通过项目内 `process_id` / `domain_id` 引用 Semantic 的业务过程 / 业务域。对象删除除了本域子域、过程、字段和源表条件外，必须检查 Modeling 真实持久化引用（含回收站模型）；任何引用都阻断物理删除，不能绕过项目空间或自动清空模型端字段。消费者拥有引用事实并通过只读 SPI 提供当前 Project 的计数；不可用时禁止危险删除。


## P0-B03：分层删除与回收站模型引用保护

数仓分层是 Semantic 的稳定配置对象，Modeling 模型以 `layer_code` 消费。原列表被引用数仅统计当前存活模型，**不能当作物理删除的安全判定**：回收站模型仍可恢复，必须将已持久化的存活和回收站模型引用同时计入删除阻断。Semantic 通过 Modeling 所实现的只读 `LayerUsageReader.countPersistedLayerReferences` 判断；未装配提供者或查询失败时拒绝危险删除，不再按零引用处理。列表的当前有效模型数继续保持原定义，不混淆“活跃模型数”与“删前持久引用数”。


## 分层的 DataSource 引用真实性（P0-B03）

数仓分层记录对当前 Project 中 DataSource 身份的稳定引用。Create/Update 使用 DataSource 查询契约确认 ID 属于当前 Project（不拷贝凭证、不直读表），而不把正整数/前端下拉选项当作事实。数据源仅处于离线或 UNKNOWN 不等于身份不存在，支持配置离线采集任务。默认分层模板尚未绑定 datasourceId 时保持“待配置”，不得被当成已完成配置。
