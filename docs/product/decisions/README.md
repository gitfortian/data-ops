# Product Decisions

Product Decision 用于记录跨模块、会长期影响产品行为的正式决定。

## Status

- PROPOSED：讨论中，不是 Product Truth。
- ACCEPTED：已拍板，是 Product Truth。
- SUPERSEDED：已被后续 Decision 替代。
- REJECTED：明确不采用，保留原因供追溯。

只有 ACCEPTED Decision 可以作为后续 Product Spec、Domain Contract 和实现的上位依据。

## When to create a Product Decision

以下情况优先创建：

- 改变 Product Capability 边界；
- 新增/合并一级产品域；
- 改变 Truth Owner；
- 统一跨模块概念；
- 确立默认用户路径；
- 引入新的跨域治理规则；
- 决定一个工程模块是否应该成为用户产品面。

普通局部 Feature 不需要 Product Decision。

## File naming

建议：

PD-001-short-title.md

编号只用于稳定引用，不表达优先级。

## Rule

Review / gap report / AI 建议不能自动成为 Product Decision。

路径必须是：

~~~text
Evidence / Review
 -> PROPOSED Decision
 -> human/product review
 -> ACCEPTED Decision
 -> update Product Truth if needed
 -> Feature Specs / implementation
~~~
