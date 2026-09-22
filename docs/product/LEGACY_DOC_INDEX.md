# Existing Documentation Classification Index

本索引用于解释旧文档，不移动、不删除文件。

| Path / Family | Class | Current Use |
|---|---|---|
| `PRODUCT_STYLE.md` | Product Process / Product Truth | 仓库级产品研发规则 |
| Product Vision / Principles / Capability / Journeys / Glossary | Product Truth | 当前跨域产品基线 |
| ACCEPTED `docs/product/decisions/**` | Product Truth | 已拍板跨域决定 |
| PROPOSED / REJECTED / SUPERSEDED Decisions | Decision Record | 讨论/历史，不是当前产品真相 |
| `docs/product/features/**` APPROVED / IMPLEMENTING | Active Change Contract | 当前 Feature 约束 |
| SHIPPED / SUPERSEDED Feature Specs | Historical Delivery Evidence | 追溯，不覆盖当前 Contract |
| module `DOMAIN.md` | Domain Contract | 稳定领域不变量 |
| module `REQUIREMENTS.md` | Domain Contract | 稳定行为要求 |
| module `ARCHITECTURE.md` | Architecture Contract | 架构边界 |
| module `DEPENDENCIES.md` | Architecture Contract | 依赖走廊 |
| `CODE_STYLE.md` | Engineering Contract | 跨仓库工程规则 |
| `docs/architecture/**` | Architecture Contract or Evidence | 只有被当前 Contract 明确引用的部分视为权威 |
| `docs/release/**` | Operational Contract | 发布/运行规则 |
| `docs/PLATFORM_CORE_FLOW.md` | Evidence / predecessor baseline | 旧产品思路来源 |
| `docs/INTERACTION_PRINCIPLES.md` | UX Evidence | 设计输入 |
| `docs/MENU_REDESIGN.md` | UX / Delivery Evidence | 历史菜单方案 |
| `docs/home-overview-contract.md` | Domain / UX Evidence | 需与当前 Product baseline 对齐 |
| `docs/v1/**` | Evidence / Review | 重要盘点材料，但不再是 Product Truth |
| `docs/test/**` | Evidence | 验收/缺陷证据 |
| `docs/prototypes/**` | Design Evidence | 交互参考 |
| `docs/*/dev-plan.md` | Delivery Plan | 阶段实现计划 |
| `docs/*/issues/**` | Work Item | 实现票据 |
| `docs/*/gap-backlog*.md` | Evidence / Backlog | 需要 promotion 才能成为需求 |
| module `README.md` | Orientation | 低于 Contract |
| module `REVIEW.md` | Review / Evidence | 不拥有产品需求 |

## Cleanup rule

不做一次性大搬家。

每一族旧文档按以下顺序治理：

1. 提取 durable fact；
2. 找到真正 Owner；
3. promotion 到当前 Contract；
4. 更新引用；
5. 最后再归档/移动旧材料。
