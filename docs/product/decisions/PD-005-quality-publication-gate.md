# PD-005 — 质量发布门禁

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-10-04  
Owner: Product

## Context

R2 希望治理结果影响发布。PD-001 的可解释质量证据与 Quality Task 失败映射已存在，
但没有 ACCEPTED 的通用发布门禁。不能把台账健康度、最近任意执行或上游表通过当作发布许可。

## Current Behavior

Quality Section MVP 仅物理表；Dataset/Data Service 来源发布和版本由 owning domain 控制。
质量检查不通过与运行技术失败独立；Asset 上架预检不等于源域发布。

## Decision

候选规则，接受后才实现：

- 首期只治理明确关联 Quality Target 的发布入口，未接入对象显示 NOT_APPLICABLE。
- Gate Policy 由 Quality 拥有，明确 target、规则集版本、允许结果、证据有效期和适用动作。
- Publication Owner 在冻结发布 target/version 后请求判定；保留 policy/evidence/version identity。
- 最新结果必须属于 exact target 和适用规则版本；过期、缺失、provider 故障不能当成通过。
- 采用 WARN / BLOCK 两种策略；BLOCK 缺少有效证据时拒绝发布，WARN 可继续但记录风险。
- 例外需要现有审批/审计能力与有效期，不创建第二个审批状态机。
- 不将 gate failure 写成 Quality execution 技术失败，不追溯改写已发布版本。

## Product Outcome

Producer 在发布前知道具体缺口，Owner 可定位为何发布被阻断及如何修复。J2/J4。

## Alternatives Considered

自动将所有上游表质量继承到产品：拒绝，无法证明覆盖、版本和时间范围。
只有提示：适合初始 WARN rollout，但不能满足需要阻断的发布策略。

## Consequences

明确 gate 与评分/执行不同；需要 target mapping、有效期和例外审计。治理服务不可用时
BLOCK 会降低发布可用性，因此先灰度 WARN，统计真实缺口后启用 BLOCK。

## Truth / Ownership Impact

- Truth Owner: Quality policy/evidence；来源域 publication/version；Approval 例外审批。
- Producers: Quality execution、来源 Publisher。
- Consumers: Dataset/Data Service Producer 和治理 Owner。

## Navigation / UX Impact

沿用发布面板、Quality execution 和待办入口，展示具体 target、结果、时间、处置链接。

## Migration Plan

不迁移既有发布状态。新增 opt-in gate policy，先 WARN；接受本 Decision 后补
APPROVED Feature、来源 Domain/Requirements 与 exact-version 测试再实施。

## Acceptance Evidence

通过、检查不通过、技术失败、无记录、过期、版本不匹配、provider 故障、重复发布、
审批例外及跨 Project 的真实 E2E。未接入产品不得显示已过门禁。

## Supersedes

None
