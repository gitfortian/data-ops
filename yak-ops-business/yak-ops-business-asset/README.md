# yak-ops-business-asset

数据资产中心：**平台管目录，不管内容（D1）**。跨域资产台账、自动对账盘点、上架状态机、统一负责人、目录与编目规则、业务标签、健康度评分、统一搜索发现、360° 详情聚合与治理驾驶舱；业务事实（模型结构、指标口径、血缘、质量、定级、TTL）一律实时读源域，不造第二事实源。

## 模块边界

- **拥有**：资产台账（yak_asset_item）、目录树、标签字典、编目规则、盘点变更流水、浏览流水、健康度派生缓存与设置。
- **不拥有**：业务对象本体与内容（归各源域）；血缘存储/渲染（归 lineage）；安全等级字典与裁决（归 security）；分层/域字典（归 semantic）；质量规则与执行（归 quality）；TTL 策略与下发（归 lifecycle）；任务编目本体（归 task-catalog）。

## 依赖方向（强制）

```
modeling/metric/dataset/dashboard/task-catalog ──实现(只读)──► AssetProvider SPI(asset.api)
asset ──消费(只读)──► semantic / security / quality / lifecycle / lineage SPI
asset ──(门面)──► audit / 调度引擎(YakScheduleGateway) / yak-security(RBAC 注解)
```

- 源域 → asset 只经 `api/` 包 SPI；禁止 asset import 源域内部包。
- 跨模块数据引用 = 松散 ID/编码，无物理外键；asset_key 与 lineage 键同源（D6）。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、状态机、不变量、D1~D12 决策 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块内结构与分层 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与原因 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求（按 ticket 追加） |
| [REVIEW.md](./REVIEW.md) | 评审要点 |

## Ticket 对应

90 模块骨架+菜单 · 91 台账+手工登记 · 92 目录树+模板 · 93 标签 · 94 AssetProvider SPI+MODEL/METRIC · 95 对账引擎+编目规则 · 96 状态机+precheck · 97 搜索+360° 详情 · 98 健康度+驾驶舱 · 99/100 前端。设计文档见 [docs/data-asset/](../../docs/data-asset/)。
