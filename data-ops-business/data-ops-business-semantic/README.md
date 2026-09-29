# data-ops-business-semantic

业务语义模块(决策 E,2026-09-14):数据标准、业务域/业务过程/标准字段集、业务过程↔源表关联、数仓分层配置的归属模块。是被依赖的**全局上游**:建模(modeling)按 SPI 消费,未来数据集成/开发/质量/服务/资产亦可消费。

## 模块边界

- **拥有**:六类数据标准(命名/类型/码值/单位/口径/安全)、标准版本快照、业务域、业务过程、标准字段集(全局字段库+过程引用)、业务过程↔源表关联、数仓分层配置、标准引用/绕过统计。
- **不拥有**:模型/字段/映射/血缘/版本(归 modeling);数据源连接与驱动(归 datasource)。

## 依赖方向(强制)

```
modeling ──(仅 SPI 调用)──► semantic ──(公共契约)──► datasource
```

- 本模块**禁止 import `io.yak.ops.business.modeling.*`**(compile-time 纪律,DEPENDENCIES.md 声明)。
- 跨模块数据引用 = 松散 ID(无物理外键);展示名由本模块 SPI 批量解析。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、实体、不变量 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块内结构与分层 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与原因 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求(按 ticket 追加) |
| [REVIEW.md](./REVIEW.md) | 评审要点 |

## Ticket 对应

30 模块骨架+六类标准表 · 31 预置 · 32 管理界面 · 33 业务域 · 34 业务过程 · 35 标准字段集 · 36 源表关联 · 37 分层配置 · 41/42 推荐与统计(SPI) · 40 沉淀(Capture API)。设计背景见 [docs/semantic/module-design.md](../../docs/semantic/module-design.md)。
