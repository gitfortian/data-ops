# data-ops-business-metric

指标管理模块：统一指标定义、口径、依赖、使用，形成指标资产。指标是**面向业务的度量**，回答"业务看什么"（GMV、订单量、转化率等）。

## 模块边界

- **拥有**：指标定义（原子/派生/复合）、指标标签、指标版本快照、定义校验证据、显式发布账本、指标血缘登记、复合指标组成、Reference Usage。
- **不拥有**：数据标准/业务域/字段库（归 semantic）；模型/字段/血缘（归 modeling）；Observed Usage / Consumption Evidence（归 consumption）；血缘可视化（归 lineage）；API 鉴权/调用记录（归 data-service）。

显式发布绑定一个 immutable `MetricVersion`；后续 Draft 不移动 active publication。下游 Reference Usage 可记录精确版本，历史仅有 Metric ID 的记录保留为版本未知。Reference Usage 不代表真实运行消费。

## 依赖方向（强制）

```
dataset/dashboard ──(SPI)──► metric ──(SPI)──► semantic
data-service ──(复用鉴权)──► metric ──(SPI)──► modeling
                              metric ──(调用)──► lineage
```

- 本模块**禁止 import `io.yak.ops.business.modeling.*` 的内部实现**，仅经 SPI 调用。
- 跨模块数据引用 = 松散 ID（无物理外键）；展示名由 SPI 批量解析。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、实体、不变量 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块内结构与分层 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与原因 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求（按 ticket 追加） |
| [REVIEW.md](./REVIEW.md) | 评审要点 |

## Ticket 对应

45 模块骨架 · 46 指标 CRUD · 47 列表+搜索 · 48 详情页 · 49 标签 · 50 版本 · 51 血缘 · 52 使用 · 53 影响分析 · 54 服务 · 55 市场 · 56 AI 推荐。设计文档见 [docs/semantic/metrics/](../../docs/semantic/metrics/)。
