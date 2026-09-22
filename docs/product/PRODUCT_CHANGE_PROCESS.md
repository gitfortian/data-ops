# Product Change Process

用户可感知行为变更的默认流程。

## 1. Change Classification

每个 PR 先判断：

- PRODUCT：改变用户行为、产品规则、业务生命周期或产品面
- TECHNICAL：不改变产品行为的重构、性能、依赖、工程治理
- DOCS：纯文档
- OPS：部署、运维、流水线等

修改业务代码不自动等于 PRODUCT；但声明 TECHNICAL 且改了业务路径时，必须解释为什么产品行为不变。

## 2. Intake

PRODUCT change 从用户问题开始：

- User
- Problem
- Expected Outcome
- Capability
- Journey

## 3. Product Shape

中型及以上变化创建 Feature Spec，放在 `docs/product/features/`。

决定：

- Truth Owner
- Producer / Consumer
- Reuse
- UX entry / next step
- Governance impact
- E2E acceptance

## 4. Product Decision

以下变化必须先有 ACCEPTED Product Decision：

- 新一级产品域
- 新顶级导航域
- 新跨域 Truth Owner
- 合并/拆分 Product Capability
- 改变默认跨域用户路径
- 新增长期跨域治理规则

## 5. Domain / Architecture Review

产品形态明确后：

- 业务规则变化 -> 更新 DOMAIN / REQUIREMENTS
- 边界变化 -> 更新 ARCHITECTURE / DEPENDENCIES
- 再准备 Dev Plan

## 6. Implementation

代码只能实现已批准范围。发现产品冲突时回到 Owner Contract，不在局部代码里发明规则。

## 7. Product Acceptance

PRODUCT change 完成必须有真实 E2E Scenario + Evidence。

## 8. Closeout

Feature 上线后：

- 长期产品规则提升到 Product Truth
- 稳定领域规则提升到 Domain Contract
- 实现边界提升到 Architecture Contract
- Feature Spec 标记 SHIPPED
- 临时计划降级为 Historical Evidence

## Change Size

### Small
窄行为修复；Issue + PR Product Impact 可足够。

### Medium
跨页面或跨模块行为；要求 Feature Spec。

### Large
新 Capability、Lifecycle、一级导航、Truth Owner 或默认跨域路径；要求 ACCEPTED Product Decision + Feature Spec。
