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
| `docs/20261001/**`, `docs/20261003/**`, `docs/20261004/**` | Dated Evidence / Historical | 按日期保留历史工作记录；不能替代当前 Contract |
| `docs/frontend-review/**`, `docs/architecture-review/**` | Review / Evidence | 静态检查、Review 结论需要 owner 核销后才能改变契约 |
| `docs/phase7-*.md`, `docs/phase8*.md`, `docs/phase9*.md` | Stage Evidence / Plan | 阶段快照，可能已过时；不自动成为当前执行计划 |
| `.zcode/plans/plan-sess_36a3068e-4352-4d63-8388-d516f3f18f85.md` | **Unapproved historical work plan** | 包含压缩 Flyway 迁移、删除 baseline 和放弃历史库升级等高风险建议；`docs/release/**`、当前 Flyway 装配和历史校验才是运行依据，**严禁据此执行 SQL 文件删改** |
| `.zcode/plans/plan-sess_a18ccd05-9ad6-47e1-9223-c72e865f530c.md` | Historical design request | 指向尚不存在的 `docs/two-layer-modeling-design.md`，不构成模块迁移决策 |
| `.zcode/plans/plan-sess_b1fff9ab-dc67-4cd5-822f-1e6dca9c139d.md` | Historical delivery-plan request | 后续是否落地须按 `docs/agent/**` 当前材料与已有架构合同核对，不能作为自动重构命令 |
| `data-ops-ui/tsc-output.txt` (retired) | Local generated diagnostic | 静态历史输出不用于 `tsc` 基线验收；以 `data-ops-ui/scripts/type-baseline.json` 与 `check-type-baseline.mjs` 为当前真相 |

## Cleanup rule

不做一次性大搬家。

每一族旧文档按以下顺序治理：

1. 提取 durable fact；
2. 找到真正 Owner；
3. promotion 到当前 Contract；
4. 更新引用；
5. 最后再归档/移动旧材料。

## Navigation and historical plan boundary

- 仓库文档导航见 [docs/README.md](../README.md)，工程治理收口证据见 [docs/engineering/code-cleanliness-p1-closeout.md](../engineering/code-cleanliness-p1-closeout.md)。
- **保留** `.zcode/plans/*.md` 原始内容作为历史输入证据，不迁移至活动执行合同、不标记已批准、也不从中复制 SQL 修改操作。
- `docs/product/**`、`docs/release/**`、Flyway SQL、模块 Contract 和真实环境证据，不因文件年龄或名称而自动归档/删除。
