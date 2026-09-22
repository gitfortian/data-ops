# yak-ops-business-security

数据安全模块(2026-09-18):数据分级分类(基础)、权限管理(谁能访问)、数据脱敏(怎么脱敏)、访问审计(谁访问了)、合规管理(是否合规)。核心原则(docs/data-security/design.md、menu.md):**分级分类是所有下游判断的地基;脱敏/裁决/合规都读分级,不各自造标准**。

## 模块边界

- **拥有**:安全等级(字典)、数据分类(树)、资产分级标签(对象↔等级/分类)、敏感发现规则与扫描、访问策略(申请/审批/裁决)、脱敏算法与脱敏策略、访问审计日志、合规规则与体检发现、总览聚合。
- **不拥有**(复用 + 跳转):数据源接入/元数据(归 datasource)、字段级安全标准定义(归 semantic 的 SECURITY 标准,本模块引用其 ID)、RBAC 角色/菜单权限(归 framework security)、变更留痕(经 audit 门面)、血缘(归 lineage)。

## 依赖方向(强制)

```
modeling / metric / data-service ──► security ──► datasource / semantic / audit
```

- 本模块**禁止 import** 其他业务模块的内部实现类型(compile-time 纪律,见 DEPENDENCIES.md)。
- 下游经 `api` 包 SPI 消费分级/脱敏/裁决(`SecurityClassificationQueryApi`/`SecurityMaskingApi`/`SecurityAccessDecisionApi`),不直读本模块表。
- 跨模块引用 = 松散 ID(无物理外键);等级/分类展示名经 SPI 解析。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DOMAIN.md](./DOMAIN.md) | 领域概念、实体、不变量 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块内结构与分层 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖方向与原因 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 行为要求(按 ticket 追加) |
| [REVIEW.md](./REVIEW.md) | 评审要点 |

## Ticket 对应

70 模块骨架+菜单 · 71 安全等级 · 72 数据分类 · 73 资产分级标签 · 74 敏感发现 · 75 访问策略与裁决 · 76 脱敏算法与策略 · 77 访问审计 · 78 合规体检 · 79 总览聚合。设计背景见 [docs/data-security/requirement.md](../../docs/data-security/requirement.md)、[design.md](../../docs/data-security/design.md) 与 [menu.md](../../docs/data-security/menu.md)。
