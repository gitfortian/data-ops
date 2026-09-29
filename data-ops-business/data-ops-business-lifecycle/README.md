# data-ops-business-lifecycle

数据生命周期模块：**平台管策略，存储管执行**。定义 TTL 策略、生成 Doris/Paimon TTL 语句、预览确认后下发；实际分区清理由存储引擎自治，平台不自建执行引擎。

## 模块边界

- **拥有**：TTL 策略（层默认/自定义）、模型绑定关系、语句生成规则、下发记录与重试、TTL 监控视图、存储快照与统计设置。
- **不拥有**：模型/分层定义（归 modeling）；数据源连接执行 SQL（归 datasource）；定时调度（归 job/调度引擎）；分层旧字段 `lifecycle_days`（归 semantic，本模块只兜底合成只读虚拟策略）。

## 依赖方向（强制）

```
lifecycle ──(SPI: ModelTtlQueryApi)──► modeling
lifecycle ──(SPI: DataSourceExecutionProvider)──► datasource
lifecycle ──(门面)──► audit / 调度引擎(YakScheduleGateway)
```

- 跨模块数据引用 = 松散 ID，无物理外键；展示名由 SPI 批量解析。
- 模型清单只经 `ModelTtlQueryApi` 读取，禁止 import modeling 内部实现。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、实体、不变量、D1~D8 决策 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块内结构与分层 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与原因 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求（按 ticket 追加） |
| [REVIEW.md](./REVIEW.md) | 评审要点 |

## Ticket 对应

80 模块骨架 · 81 策略 CRUD + 分层默认 · 82 模型绑定 · 83 语句生成 · 84 预览+确认令牌 · 85 下发+记录 · 86 失败重试 · 87 监控+存储统计 · 88 前端策略/监控/统计页 · 89 模型 Tab + 下发向导。设计文档见 [docs/data-lifecycle/](../../docs/data-lifecycle/)。
