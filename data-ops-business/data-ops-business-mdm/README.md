# data-ops-business-mdm

主数据管理模块(2026-09-16):主数据实体/属性/关系、识别、采集配置、清洗(去重/合并/标准化/补全)、审批、分发、服务、治理、分析。核心原则(design.md/menu.md):**能复用就复用,只新建主数据特有的;通用能力复用 + 跳转**。

## 模块边界

- **拥有**:主数据实体、属性、关系;来源绑定与采集配置;主数据记录(master_id/source_ids);去重/合并/标准化/补全规则;变更申请与审批流;分发配置;订阅;总览聚合视图。
- **不拥有**(复用 + 跳转,menu.md 七):数据源接入/元数据(归 datasource)、数据采集执行/调度(归 sync/数据集成)、质量规则引擎(归 quality/数据质量)、API 管理与缓存(归 data-service/数据服务)、数据标准(归 semantic/语义中心)、血缘(归 lineage/数据血缘)、权限(归 security)、资产统计(归 dataset)。

## 依赖方向(强制)

```
modeling ──► mdm ──► datasource / sync / quality / data-service / semantic / lineage / security / dataset
```

- 本模块**禁止 import** `io.yak.ops.business.modeling.*` 及其他被依赖模块的内部实现类型(compile-time 纪律,DEPENDENCIES.md 声明)。
- 跨模块数据引用 = 松散 ID(无物理外键);展示名经 SPI/公共契约批量解析。
- 菜单收敛(menu.md):只建 5 个特有菜单(总览/建模/识别/清洗/审批),采集→数据集成、质量→数据质量、血缘→数据血缘、服务→数据服务、标准→语义中心、分析→仪表盘,全部跳转。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、实体、不变量 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块内结构与分层 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与原因 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求(按 ticket 追加) |
| [REVIEW.md](./REVIEW.md) | 评审要点 |

## Ticket 对应

50 模块骨架+菜单(总览) · 51 实体建模 · 52 属性建模(引用标准) · 53 识别 · 54 采集配置 · 55 采集执行(对接 sync) · 56 清洗·去重合并 · 57 清洗·标准化补全 · 58 分发 · 59 服务(API/订阅/缓存) · 60 审批 · 61 治理 · 62 总览与分析。设计背景见 [docs/mdm/design.md](../../docs/mdm/design.md) 与 [docs/mdm/menu.md](../../docs/mdm/menu.md)。
