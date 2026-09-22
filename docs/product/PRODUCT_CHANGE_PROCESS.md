# Product Change Process

This is the default lifecycle for user-facing behavior changes.

## 1. Intake

Start with a user problem, not a requested implementation.

A Feature issue should identify:

- user
- problem
- expected outcome
- capability
- journey

## 2. Product Shape

For non-trivial changes, create a Feature Product Spec from `FEATURE_SPEC_TEMPLATE.md`.

Decide:

- truth ownership
- producer / consumer
- reuse
- navigation / entry point
- governance impact
- E2E acceptance

## 3. Product Review

The review question is:

> Does this improve a core journey without creating a second product truth or unnecessary product surface?

Possible outcomes:

- Accept
- Merge into existing capability
- Reframe
- Reject / defer

## 4. Domain / Architecture Review

Only after the product shape is clear:

- update DOMAIN / REQUIREMENTS if business rules change;
- update ARCHITECTURE / DEPENDENCIES if boundaries change;
- prepare implementation plan.

## 5. Implementation

Code follows the accepted contracts.

Implementation must not silently expand product scope.

If implementation discovers a product contradiction, stop and update the owning decision / spec instead of inventing behavior locally.

## 6. Product Acceptance

Completion requires an E2E user outcome, not only unit/API completion.

Evidence may include:

- UI behavior
- API response
- persisted fact
- event / audit record
- lineage / usage update
- runtime evidence

## 7. Closeout

After shipping:

- durable decisions remain in current contracts;
- temporary plans become historical evidence;
- stale contradictory plans are marked or archived;
- journey acceptance is updated when appropriate.

## Change Size

### Small

Examples: wording, validation, narrow behavior fix.

A concise Feature issue + PR Product Impact may be enough.

### Medium

Cross-page or cross-domain behavior change.

Requires Feature Product Spec.

### Large

New capability, new lifecycle, new top-level navigation, new truth owner, or new cross-domain contract.

Requires Product Spec + explicit Product Decision + Domain/Architecture updates before coding.
