# Documentation navigation · 产品合同与保留证据

本页按照**当前仓库中实际存在的文件**维护入口。不要从目录名或历史计划判断需求已批准。仅 `ACCEPTED` Product Decision 与 `APPROVED / IMPLEMENTING` Feature Spec 能作为对应范围的实施依据；原始 Review、Issue、旧计划和代码快照只是证据。

## 当前权威入口

| 需要做什么 | 当前文件 | 使用边界 |
| --- | --- | --- |
| 产品定位、用户旅程和能力图 | [产品基线](product/README.md)、[Capability Map](product/CAPABILITY_MAP.md)、[User Journeys](product/USER_JOURNEYS.md) | 以当前获批合同为准，不能以代码存在推断旅程已验收 |
| 审查 Product Decision / Feature | [文档治理](product/DOCUMENT_GOVERNANCE.md)、[特性清单](product/features/README.md)、[变更流程](product/PRODUCT_CHANGE_PROCESS.md) | `ACCEPTED` / `APPROVED` / `IMPLEMENTING`，不得把 PROPOSED 当作事实 |
| 产品历史和讨论材料 | [历史文档分类](product/LEGACY_DOC_INDEX.md)、[保留的 Review 索引](reviews/README.md) | Evidence / Review，不是新产品合同 |
| 当前运行与升级要求 | [Release Guide](release/RELEASING.md)、[仓库代码规范](../CODE_STYLE.md) | 不改写已经执行的 Flyway，也不凭历史规划执行删除 |
| 前端现状与设计 | [前端工程约定](../data-ops-ui/FRONTEND_CODE_STYLE.md)、[IA V3.1](product/experience/IA-V3.1-navigation-page-composition-plan.md) | IA 文档仍按其审批状态执行；原型不是生产代码 |
| AI/Coding Agent 入口 | [AGENTS.md](../AGENTS.md)、[PRODUCT_STYLE.md](../PRODUCT_STYLE.md) | 按 Owner 和已批准变更最小范围读取 |

## 历史证据清理的可追溯边界

2026-10-13 的仓库提交 [`482017e`](https://github.com/gitfortian/data-ops/commit/482017e607c33d13ebcc4cdf64277aeb6d60114b) 清理了旧的 `docs/20261001/**`、`docs/20261003/**`、`docs/20261004/**`、`docs/engineering/**`、`docs/frontend-review/**`、`docs/architecture-review/**`、`docs/v1/**`、`docs/test/**` 和旧历史证据索引。**这些路径不再是 main 中有效的本地链接**。需要审计旧材料时，可查看[清理前的 Git 历史快照](https://github.com/gitfortian/data-ops/tree/140c82c6227ef39339ff19825c41786b4035c6fb/docs)，不得编造已删除的实时文档或将历史记录自动变成当前 Contract。

对于现存材料，优先阅读[保留的 Review 索引](reviews/README.md)、[产品规格](product/features/README.md)、[产品决策](product/decisions/)和各模块稳定合同。只有真实保存于当前库且具备合法访问路径的证据才能当作**当前可访问证据**；Git 历史快照属于历史来源，不表示今天验收通过。

## 高风险旧计划（历史输入，不执行）

- `.zcode/plans/plan-sess_36a3068e-4352-4d63-8388-d516f3f18f85.md` 曾提出删除大量 Flyway 历史版本、改写 baseline 与迁移顺序。**严禁**以该提案自行修改已经执行的迁移 SQL；当前升级约束以 Release / Flyway 合同为准。
- `.zcode/plans/plan-sess_a18ccd05-9ad6-47e1-9223-c72e865f530c.md` 属于历史双层建模设计请求，不是正式授权。
- `.zcode/plans/plan-sess_b1fff9ab-dc67-4cd5-822f-1e6dca9c139d.md` 属于 Agent 可观测性历史任务草案，不是可直接执行的开发计划。

## 实施与验收原则

面向具体变更：Product / Domain Contract → 工程与 API 事实 → 真实测试证据。当前源码、CI、部署版本、Owner 身份和角色权限必须分别核对；PR merged 不等于已部署/已验收。没有证据不宣称 SHIPPED。
