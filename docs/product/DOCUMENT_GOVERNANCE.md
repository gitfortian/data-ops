# Document Governance

## 1. Purpose

DataOps has accumulated a large amount of useful design material. The problem is not document volume by itself; it is ambiguity about which document is authoritative.

This policy defines document roles so humans and AI agents can distinguish current product truth from domain contracts, implementation design, analysis evidence, and historical plans.

## 2. Document Classes

### A. Product Truth

Owns product intent and cross-domain decisions.

Location:

`docs/product/**` and root `PRODUCT_STYLE.md`

Examples:

- Product Vision
- Product Principles
- Capability Map
- User Journeys
- Product Glossary
- accepted cross-domain Product Decisions

Product Truth answers:

- why the product exists
- which user problem matters
- product boundaries
- cross-domain ownership
- user journeys
- terminology

### B. Domain Contract

Owns stable business rules inside one domain.

Preferred location:

`yak-ops-business/<module>/DOMAIN.md`
`yak-ops-business/<module>/REQUIREMENTS.md`

Domain Contract may refine Product Truth but must not contradict it.

### C. Architecture Contract

Owns implementation boundaries and dependency rules.

Preferred location:

- module `ARCHITECTURE.md`
- module `DEPENDENCIES.md`
- repository `CODE_STYLE.md`
- stable cross-cutting architecture documents explicitly referenced by current Product / Domain contracts

Architecture answers “how”, not “why this product capability should exist”.

### D. Operational Contract

Owns build, release, migration, runtime and operating procedures.

Examples:

- `docs/release/**`
- migration runbooks
- production operational procedures

### E. Evidence / Review

Records observations, audits, tests, product reviews, gap analysis and prototypes.

Examples:

- `docs/v1/**`
- `docs/test/**`
- `REVIEW.md`
- gap backlogs
- prototype HTML
- current-state analysis

Evidence is valuable input for decisions, but does not automatically become a product requirement.

### F. Delivery Plan / Work Item

Temporary implementation planning.

Examples:

- `dev-plan.md`
- `issues/**`
- stage / wave plans
- refactor plans

After completion, these documents become historical evidence unless promoted into Product / Domain / Architecture truth.

### G. Archive

Superseded material kept for traceability.

Archive content must not be treated as current instruction without an explicit reference from a current contract.

## 3. Authority Order

When documents disagree:

```text
Product Truth
  > Domain Contract
  > Architecture Contract
  > Operational Contract
  > Evidence / Review
  > Delivery Plan
  > Archive
```

This is not a blanket rule for every technical detail. A higher-level document owns intent; a lower-level document owns details within its scope. Conflict means ownership must be clarified, not silently merged.

## 4. Promotion Rule

A finding does not become Product Truth because an AI wrote it in a review.

Promotion requires:

1. evidence
2. explicit decision
3. update to the owning current contract
4. removal or marking of contradictory current text

Example:

```text
gap report says Dashboard should consume Metric
  -> product decision accepted
  -> USER_JOURNEYS / CAPABILITY_MAP updated
  -> Dashboard Feature Spec created
  -> implementation begins
```

## 5. Historical Document Rule

Old documents remain useful for:

- discovering why a design exists
- finding known gaps
- locating test evidence
- reconstructing decisions

They must not be used as direct implementation instructions when they conflict with current Product / Domain contracts.

AI agents should say:

> historical evidence suggests X; current product contract says Y

instead of silently choosing one.

## 6. New Document Rule

Do not create a new Markdown file by default.

Before creating one, decide which class it belongs to.

Prefer updating an existing owner document when the information is durable.

Create a new document only when it owns a distinct durable contract or a bounded piece of evidence / delivery work.

## 7. Lifecycle

```text
Observation
 -> Evidence
 -> Decision
 -> Current Contract
 -> Implementation
 -> E2E Evidence
 -> Historical delivery material
```

Plans should not stay forever as competing specifications.

## 8. AI Context Rule

For implementation tasks, AI should not recursively load all `docs/**`.

Use this context budget:

1. product baseline
2. relevant feature spec
3. target domain contracts
4. target architecture contracts
5. selected evidence only when needed

This reduces context pollution and outdated-plan leakage.
