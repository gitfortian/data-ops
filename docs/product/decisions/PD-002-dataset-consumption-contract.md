# PD-002 — Dataset as Default Governed Consumption Contract

Status: PROPOSED  
Date: 2026-09-22  
Owner: Product

## Context

DataOps currently has multiple consumption products: Analysis, Dashboard, Digital Screen, Data Service and Agent.

If every consumer can independently bind raw DataSource, custom SQL, local aggregation logic, Metric definitions or task outputs, the product develops multiple consumption truths.

That creates recurring problems:

- the same business data is queried differently by each product;
- security and governance enforcement becomes inconsistent;
- usage and lineage are hard to reason about;
- Metric/Semantic cannot reliably connect to final consumption;
- Agent risks becoming a parallel data-access platform.

Dataset already has the right product characteristics to become the stable consumption boundary.

## Decision

Make **Dataset** the default governed consumption contract for DataOps.

Preferred path:

~~~text
Production / Published data
  -> Dataset
      -> Analysis / Dashboard
      -> Data Service
      -> Agent
      -> future governed consumers
~~~

This does not ban direct SQL or raw DataSource access.

Instead:

- governed product flows should default to Dataset;
- direct SQL / raw DataSource paths are classified as explicit advanced or exceptional modes;
- advanced paths must still pass applicable security/audit/governance controls;
- consumers should not re-create Dataset-owned schema/field semantics locally without a specific reason.

## Product Outcome

Improve J3 “Production -> Dataset -> Consumption”, J5 “Sensitive Data -> Safe Consumption” and J6 “Question -> Evidence-backed AI Answer”.

Users should know:

> If data is ready to be consumed in DataOps, Dataset is the normal contract.

This gives Dashboard/API/Agent a common source for:

- fields and schema;
- version/context;
- permissions;
- security decisions;
- usage;
- lineage;
- future semantic/metric bindings.

## Alternatives Considered

### Option A — Every consumer chooses any source equally

Pros:
- maximum flexibility;
- minimal migration.

Cons:
- fragmented semantics;
- governance enforcement duplicated per consumer;
- weak lineage and usage;
- high long-term product complexity.

### Option B — Force every query through Dataset immediately

Pros:
- strongest consistency.

Cons:
- migration risk;
- blocks legitimate operational/admin/debug scenarios;
- current Dataset capability may not cover all advanced needs.

### Option C — Dataset as governed default, explicit advanced bypass

This proposal.

It establishes one recommended product contract without pretending every advanced use case is already covered.

## Consequences

### Positive

- simplifies consumption mental model;
- gives Security one practical enforcement integration point;
- makes usage and lineage more coherent;
- gives Metric a clear route into consumption;
- makes Agent safer and easier to reason about;
- reduces duplicated query/binding semantics.

### Trade-offs

- Dataset must support the consumption features expected by downstream products;
- consumers may need migration adapters;
- some current direct-SQL flows need explicit classification;
- performance and low-latency paths may need specialized Dataset execution modes.

### Risks

- Dataset could become a “god module” if it starts owning every consumer concern;
- teams may wrap raw SQL as Dataset without improving governance;
- forcing migration too early could break working product flows.

## Truth / Ownership Impact

Dataset owns:

- Dataset identity and definition;
- Dataset version/contract;
- Dataset field/catalog semantics that belong to Dataset;
- Dataset query contract.

Dataset does not automatically own:

- Metric definitions;
- Semantic standards;
- physical DataSource connection truth;
- consumer visualization definitions;
- Data Service runtime policies;
- Agent conversation/evidence truth.

Consumers reference Dataset rather than copying its business contract.

## Navigation / UX Impact

If ACCEPTED:

- Dataset should be presented as “ready-to-consume data”.
- Dashboard/API/Agent create flows should prefer choosing Dataset.
- direct SQL / raw source options should be clearly labeled advanced/exceptional.
- users should be able to navigate from Dataset to consumers and from consumers back to Dataset.
- Dataset detail should increasingly expose usage and lineage.

No immediate removal of direct paths is required.

## Migration Impact

Phased:

1. inventory all consumer source types;
2. label each as governed default vs advanced/exceptional;
3. close missing Dataset capability gaps;
4. connect Dashboard to Dataset contract consistently;
5. connect Data Service governed path to Dataset;
6. keep direct SQL mode explicit where justified;
7. ensure Agent remains Dataset-first;
8. add usage/lineage feedback across consumers.

## Acceptance Evidence

A representative Dataset should be consumable by Dashboard, Data Service and Agent while preserving:

- consistent field semantics;
- project/permission boundary;
- applicable security decision;
- lineage back to production/source;
- usage back from consumer to Dataset;
- consumer-to-Dataset navigation.

A direct SQL/API advanced path, if retained, must be explicitly marked and governed rather than silently behaving as the normal product path.

## Non-goals

- Dataset does not replace Metric.
- Dataset does not replace Semantic.
- Dataset does not own Dashboard visualization definitions.
- Dataset does not prohibit all raw SQL/admin/debug access.
- This proposal does not require immediate migration of every existing consumer.

## Supersedes

None
