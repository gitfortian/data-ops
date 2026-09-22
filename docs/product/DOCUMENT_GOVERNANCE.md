# Document Governance

## 1. Purpose

DataOps 已积累大量设计、盘点、测试和阶段计划。治理目标不是减少文档数量，而是明确“谁拥有哪类真相”。

## 2. Document Classes

### A. Product Truth

只包含当前生效的跨域产品事实：

- `PRODUCT_STYLE.md` 中的仓库级产品研发规则
- `docs/product/PRODUCT_VISION.md`
- `docs/product/PRODUCT_PRINCIPLES.md`
- `docs/product/CAPABILITY_MAP.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/product/PRODUCT_GLOSSARY.md`
- `docs/product/decisions/**` 中 **Status: ACCEPTED** 的 Product Decision

注意：`docs/product/**` 这个目录本身并不自动等于 Product Truth。

### B. Active Change Contract

描述一个正在形成或实施中的产品变更：

- Feature Spec：仅 **APPROVED / IMPLEMENTING** 状态具有当前变更约束力
- PROPOSED Product Decision：仅用于讨论，不能指导实现
- PR Product Impact：用于当前变更的最小契约

Feature 上线后，长期有效的规则必须提升到 Product / Domain / Architecture Contract；Feature Spec 本身转为历史交付证据。

### C. Domain Contract

模块内稳定业务规则：

- `DOMAIN.md`
- `REQUIREMENTS.md`

### D. Architecture / Engineering Contract

- `ARCHITECTURE.md`
- `DEPENDENCIES.md`
- `CODE_STYLE.md`
- 被当前 Contract 明确引用的稳定架构文档

### E. Operational Contract

构建、发布、迁移、运行、恢复等操作规则，例如 `docs/release/**`。

### F. Evidence / Review

- `docs/v1/**`
- `docs/reviews/**`
- `docs/test/**`
- 模块 `REVIEW.md`
- gap backlog
- prototype
- current-state analysis

Evidence 可以促成 Decision，但不能自动成为 Requirement。

### G. Delivery Plan / Work Item

- `dev-plan.md`
- `issues/**`
- wave / stage / refactor plan

完成后默认降级为 Historical Evidence。

### H. Archive

已明确被替代、仅保留追溯价值的材料。

## 3. Authority Model

不同文档拥有不同范围，不做简单“目录越高权越大”的机械覆盖。

当同一事实发生冲突时：

~~~text
Accepted Product Truth
  > Active Feature Contract（仅本次变更范围）
  > Domain Contract
  > Architecture / Engineering Contract
  > Operational Contract
  > Evidence / Review
  > Delivery Plan
  > Archive
~~~

代码和测试是“当前实现事实”，但不自动拥有产品意图。若代码与已接受 Contract 不一致，应记录为 implementation gap，而不是偷偷把 Contract 改回代码现状。

## 4. Promotion Rule

~~~text
Observation
 -> Evidence
 -> Product Decision（需要时）
 -> Accepted Contract
 -> Feature Spec
 -> Implementation
 -> E2E Evidence
 -> Closeout
~~~

AI Review、Gap 文档、会议结论都不能跳过 Decision / Contract promotion。

## 5. Feature Spec Lifecycle

Feature Spec 固定放在：

`docs/product/features/`

状态：

- DRAFT：讨论中，不指导实现
- APPROVED：已批准，可进入实现
- IMPLEMENTING：实现中
- SHIPPED：已交付；不再作为未来实现的最高指令
- SUPERSEDED：已被替代

SHIPPED 后必须把长期规则提升到正确的 Product / Domain / Architecture Owner。

## 6. AI Context Budget

实现任务默认只加载：

1. Product baseline
2. ACCEPTED Decisions
3. 当前 APPROVED / IMPLEMENTING Feature Spec
4. 目标 Domain Contract
5. 目标 Architecture Contract
6. 必要 Evidence

禁止默认递归读取全部 `docs/**`。
