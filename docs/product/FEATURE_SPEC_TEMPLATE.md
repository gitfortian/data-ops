# Feature Product Spec

Status: DRAFT  
Feature ID: F-XXX  
Owner:  
Target Release: TBD  
Created: YYYY-MM-DD  
Related Decisions: None  
Related Issues / Designs: None

> 固定存放于 `docs/product/features/`。只有 APPROVED / IMPLEMENTING 状态可以指导当前实现。

## 1. Objective — Required

**Feature：**

**User：**

**Problem：**

**Expected Outcome：**

**Why Now：**

一句话说明为什么值得现在做，而不是“因为模块还缺这个功能”。

## 2. Success Metrics

> Acceptance 证明“做对了”；Success Metrics 证明“做这件事有价值”。两者不能互相替代。

中型/大型 Feature 必须至少定义一个可观察成功信号。

| Metric / Signal | Baseline | Target | Measurement / Evidence |
|---|---|---|---|
|  |  |  |  |

如果当前无法量化，可以使用明确的行为/运营信号，但必须说明如何观察。

## 3. Product Context — Required

**Capability：**

**User Journey：**

**Entry Point：**

**Previous Step：**

**Next Step：**

**User Story（optional）：**

> As a ..., I want ..., so that ...

## 4. Assumptions & Validation

把“事实”和“我们暂时相信的事情”分开。

| Assumption | Why we believe it | How to validate | Result |
|---|---|---|---|
|  |  |  |  |

AI 不得把未验证 Assumption 写成 Product Truth。

## 5. Truth & Ownership — Required

**Truth Owner：**

**Producer(s)：**

**Consumer(s)：**

**Source of Truth：**

是否新增第二份业务真相：否 / 是（必须说明）

## 6. Options & Scope

对于存在明显方案选择的 Feature，列出考虑过的选项；跨产品域长期选择应提升为 Product Decision。

### Chosen approach

### Alternatives considered

### In scope

### Out of scope — Required

## 7. Reuse — Required

复用现有能力：

- Project Space / RBAC
- Dataset
- Semantic
- Metric
- Asset
- Lineage
- Approval
- Audit
- Alert / Notification
- Task / Workflow
- Other

需要重复建设时说明原因。

## 8. User Experience — Required

Happy Path：

Empty State：

Error / Blocking State：

Permission Denied：

Loading / Long-running：

Cross-domain backlink：

## 9. Governance Impact

- Asset / governed object state
- Lineage
- Quality
- Security / Masking
- Approval
- Audit
- Lifecycle
- Usage / Impact

## 10. Open Questions

> 未解决问题要显式留在这里，不能由 AI 静默脑补。

| Question | Blocking? | Owner | Decision / Answer | Date |
|---|---|---|---|---|
|  |  |  |  |  |

Status 升为 APPROVED 前，不允许仍存在未处理的 blocking question。

## 11. Supporting Evidence

按需链接：

- User / customer evidence:
- Current-state data:
- Mockup / prototype:
- Architecture diagram:
- Related review:
- Related issue:

## 12. Architecture Impact

本节只记录已决定产品形态对实现的影响：

- API:
- DB:
- Domain:
- Events:
- Compatibility:

## 13. Acceptance — Required

### E2E Scenario

Given:

When:

Then:

### Evidence

- UI / API:
- Persisted fact / event:
- Audit:
- Observability:

## 14. Closeout

当状态更新为 SHIPPED 时：

- Success Metrics 当前结果：
- 哪些长期规则已提升到 Product Truth：
- 哪些规则已提升到 Domain Contract：
- 哪些边界已提升到 Architecture Contract：
- 当前实现证据：
