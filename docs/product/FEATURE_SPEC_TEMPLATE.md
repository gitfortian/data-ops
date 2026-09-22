# Feature Product Spec Template

> Feature 实现前复制本模板。小需求可以精简，但标记为 Required 的字段必须存在。

## 1. Summary — Required

**Feature：**

**User：**

**Problem：**

**Expected Outcome：**

## 2. Product Context — Required

**Capability：**  
从 `CAPABILITY_MAP.md` 选择。

**User Journey：**  
从 `USER_JOURNEYS.md` 选择。

**Entry Point：**

**Previous Step：**

**Next Step：**

## 3. Truth & Ownership — Required

**Truth Owner：**

**Producer(s)：**

**Consumer(s)：**

**Source of Truth：**

是否新增第二份业务真相：否 / 是（必须说明原因）

## 4. Reuse — Required

本功能复用：

- [ ] Project Space
- [ ] RBAC / Resource Authorization
- [ ] Dataset
- [ ] Semantic
- [ ] Metric
- [ ] Asset
- [ ] Lineage
- [ ] Approval
- [ ] Audit
- [ ] Alert / Notification
- [ ] Task / Workflow
- [ ] 其它：

需要自建已有类似能力时，原因：

## 5. User Experience — Required

Happy Path：

Empty State：

Error / Blocking State：

Permission Denied：

Loading / Long-running：

跨域回链：

## 6. Governance Impact

是否影响：

- Asset 状态
- Lineage
- Quality
- Security / Masking
- Approval
- Audit
- Lifecycle
- Usage / Impact

如果“不影响”，是否符合产品逻辑？

## 7. API / Data / Architecture

本节只记录产品已经决定后需要的实现影响，不在这里反向发明产品需求。

- API:
- DB:
- Domain:
- Events:
- Compatibility:

## 8. Acceptance — Required

### E2E Scenario

Given:
When:
Then:

### Evidence

- UI:
- API:
- DB / Event:
- Audit:
- Observability:

## 9. Non-goals — Required

明确本次不做：

## 10. Product Decision

需要拍板的问题：

Decision:
Date:
Reason:
