# Product Decisions

Product Decision 用于记录跨模块、长期影响产品行为的正式决定。

## Status

- PROPOSED：讨论中，不是 Product Truth
- ACCEPTED：已拍板，属于 Product Truth
- SUPERSEDED：已被后续 Decision 替代
- REJECTED：明确不采用

## Implementation

Decision 的“是否接受”和“是否已经实现”是两个维度：

- NOT_STARTED
- PARTIAL
- DONE

例如：

~~~text
Status: ACCEPTED
Implementation: PARTIAL
~~~

表示产品方向已经生效，但当前代码尚未完全迁移。AI 不得把 PARTIAL 理解为可以在无关 PR 中顺手补完全部迁移。

## Requires a Product Decision

- 新增/合并一级 Product Capability
- 新增顶级导航域
- 改变跨域 Truth Owner
- 统一跨模块长期概念
- 改变默认跨域用户路径
- 新增长期跨域治理规则

## Rule

~~~text
Evidence / Review
 -> PROPOSED Decision
 -> Product Review
 -> ACCEPTED Decision
 -> update owning Product Contract
 -> Feature Spec
 -> implementation
~~~

Review / gap / AI 建议不能自动成为 Decision。
