# yak-ops-business-metadata

元数据中心：**一个统一的可扩展实体目录**。外部物理元数据（库/表/列）与内部逻辑元数据（模型/标准字段/业务域/指标）
**全部归类为可扩展实体**，统一登记、统一存储、跨类型统一检索，并向下游建模供给"物理事实"。
设计判据与来龙去脉见 [docs/data-metadata/plan.md](../../docs/data-metadata/plan.md)（下称 plan §x），
参考系统蒸馏见 [openmetadata-distill.md](../../docs/data-metadata/openmetadata-distill.md)（下称蒸馏 §x）。

## 模块边界

- **拥有**：元模型（`yak_md_type_def` / `yak_md_field_def`）、目录扩展侧表与采集/注册/治理 7 张自持表、
  物理 `table`/`tableColumn` 实体的全部属性。
- **不拥有**：`dataModel` / `standardField` / `domain` / `metric` 的业务内容（归各源域，本模块只存**目录投影**，plan §1.3）；
  图节点表 `yak_metadata_asset` 的**基线与既有列**（归 lineage，本模块是**目录列的 steward**，plan §2.5）；
  关系/边（归 `yak_metadata_relation`，lineage）；存储字节量（归 lifecycle `yak_lc_storage_snapshot`，只读复用）；
  标签字典与上架状态机（归 asset）；符合性判定规则（归 modeling，plan §5.2）。
- **禁止**：新增第二张资产表或边表；自建存储量采集；把源域业务内容（列定义、指标公式、标准字典项）抄进目录。

## 两条入口（同一个落库机制）

| 入口 | 适用 | 触发 | `provider_type` |
| --- | --- | --- | --- |
| **采集 HARVESTED** | 外部物理元数据 | 本模块主动调 `DataSourceCatalog` SPI（进程内，无连接器） | `HARVESTED` |
| **注册 REGISTERED** | 内部逻辑元数据 | **主：源域写成功后 push 调 `MetadataRegistrationApi`**；副：定时 `EntityProvider` 对账补漏/刷指纹/判 GONE | `REGISTERED` |

两条入口共用同一套 upsert、双指纹增量（`content_hash` / `source_hash`）、GONE 判定与质量熔断；
区别只在触发者与比哪把指纹。push 的三个必须（post-commit / 可重放 outbox / 保序）见 plan §3.2c。

## 依赖方向（强制）

```
modeling / semantic / metric ──实现 EntityProvider + 调用 MetadataRegistrationApi──► metadata.api
metadata ──消费(只读)──► lineage.api / datasource / lifecycle.api / semantic.api / asset.api
metadata ──(门面)──► 调度引擎 YakScheduleGateway / yak-security RBAC
```

- 源域 → metadata 只经 `api/` 包；**metadata 不得 import 任何源域内部包**（`MetadataLayeringConventionTest` 守，plan §9 T6）。
- **`asset_key` 由源域交出，元数据绝不替它拼键**（plan §2.3 后果 6）；现网键格式是既成事实，新造键不会报错只会分裂节点。
- 跨模块数据引用 = 松散 ID/编码，无物理外键。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、投影/归属判别式、不变量、命名规则 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 包结构、存储层、**共表列 steward 契约（必读）**、元模型与槽位 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与禁止项 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求（按 ticket 追加） |
| [REVIEW.md](./REVIEW.md) | 评审清单 |
| [docs/data-metadata/plan.md](../../docs/data-metadata/plan.md) | 完整方案（判据、实测记录、A/B 决策、陷阱 T1~T20） |
| [docs/data-metadata/issues/](../../docs/data-metadata/issues/) | 26 张 ticket 的验收清单（110~135） |

## Ticket 对应

110 骨架+契约集 · 111 菜单权限 V2033 · 112 自持 7 张表 · 133 共表目录列(跨模块) · 113 抽 `LineageRegistrationApi` ·
134 键生成器下沉+枚举加值(跨模块) · 128~129 元模型与自省 · 114~116 采集/GONE/调度 ·
130~131+135 写时登记与对账 · 117~118 统一检索与详情 · 119~121 三方消费 · 122/123/132 前端 · 124~127 治理与守卫
