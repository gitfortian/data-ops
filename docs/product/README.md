# DataOps Product Baseline

这里维护当前产品治理基线。

## Current Product Truth

1. [PRODUCT_VISION.md](./PRODUCT_VISION.md)
2. [PRODUCT_PRINCIPLES.md](./PRODUCT_PRINCIPLES.md)
3. [CAPABILITY_MAP.md](./CAPABILITY_MAP.md)
4. [USER_JOURNEYS.md](./USER_JOURNEYS.md)
5. [PRODUCT_GLOSSARY.md](./PRODUCT_GLOSSARY.md)
6. [decisions/](./decisions/) 中 **Status: ACCEPTED** 的 Product Decision

注意：不是所有 `docs/product/**` 文件都是 Product Truth。

## Change Contracts

- [PRODUCT_CHANGE_PROCESS.md](./PRODUCT_CHANGE_PROCESS.md)
- [FEATURE_SPEC_TEMPLATE.md](./FEATURE_SPEC_TEMPLATE.md)
- [features/](./features/)：只有 APPROVED / IMPLEMENTING Feature Spec 可以指导当前实现

## Governance

- [DOCUMENT_GOVERNANCE.md](./DOCUMENT_GOVERNANCE.md)
- [LEGACY_DOC_INDEX.md](./LEGACY_DOC_INDEX.md)
- [PRODUCT_GUARD.md](./PRODUCT_GUARD.md)

## Acceptance Evidence

- [Asset Understanding Acceptance Case：用户信息表](./acceptance/asset-understanding-user-info.md) — Golden Asset 候选样本；事实已核对，端到端验收尚未执行。该案例属于证据，不替代 ACCEPTED Decision 或 APPROVED Feature Spec。

仓库级产品研发规则见 [PRODUCT_STYLE.md](../../PRODUCT_STYLE.md)。

AI / Coding Agent 入口见 [AGENTS.md](../../AGENTS.md)。

## Authority summary

~~~text
Accepted Product Truth
  -> Active Feature Contract
  -> Domain Contract
  -> Architecture / Engineering Contract
  -> Code as current implementation fact
  -> Evidence / Review / Plans
~~~

## 当前阶段

当前阶段优先做：

- 从模块完成转向用户旅程闭环；
- 找出跨模块断点；
- 收敛产品概念和 Truth Owner；
- 让治理进入真实执行/消费；
- 在正式 Product Decision 之前，不把 Review 建议提前写成硬产品规则。
