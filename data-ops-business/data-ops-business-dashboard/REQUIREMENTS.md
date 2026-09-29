# Dashboard Requirements

## Purpose

Dashboard provides persistent, versioned BI composition inside a Project Space. It composes reusable Analysis references and optional inline analysis payloads into layouts, filters and interactions while keeping editing and published state independent.

## Supported use cases

The module supports listing, reading, creating, versioning, publishing, restoring and deleting Dashboards; reading immutable history; blocking deletion of historically referenced Analysis assets; and projecting the effective snapshot into Lineage after commit.

The deprecated `activateVersion` API remains a compatibility alias for restore and never reactivates an old row.

## Project Space requirements

- `yak_dashboard` is a `PROJECT_ROOT` and stores trusted `CurrentProject.projectId`.
- Controllers are `PROJECT_REQUIRED`.
- List, detail, update, publish, restore, delete, overview and reference-check paths fail closed outside the current Project.
- DashboardVersion, Widget, Filter, Binding and Interaction inherit ownership from the parent Dashboard; every independent child read/write proves that parent.
- Request DTOs cannot choose or override `projectId`.
- `DashboardChangedEvent` freezes `projectId`; after-commit projection restores it with `ProjectContextScope`.

## Composition contract

A Dashboard version contains a name/description snapshot, optional `activeDatasetId`, optional theme object, widgets, global filters/bindings and interactions.

Every widget chooses exactly one mode:

```text
analysisId XOR inlineAnalysis
```

A reusable `analysisId` must resolve through `DashboardAnalysisGateway` in the current Project. `activeDatasetId` and an explicit `inlineAnalysis.datasetId` must resolve through `DashboardDatasetGateway` in the current Project.

These are identity and ownership checks only. Dashboard does not take ownership of Dataset ONLINE state, current version, schema compatibility or query execution.

Existing limits remain: at most 200 widgets, a 24-column grid, at most 20 global filters, 200 bindings and 100 interactions, plus the existing JSON shape and serialized-size limits.

## Version and publication requirements

DashboardVersion is append-only. Save normalizes a candidate, allocates the next number using the current repository strategy, appends an immutable snapshot and moves the current pointer.

Restoring V2 while current is V7 produces V8. It does not move current back to the old V2 row.

`currentVersionId` and `publishedVersionId` may intentionally differ. Publishing moves only the published pointer to the current immutable version and is a business no-op when both already match.

## Reference and lineage requirements

Reusable Analysis definitions remain owned by Analysis. Any historical DashboardVersion reference continues to block deletion, scoped to the current Project.

After commit, Lineage uses the published snapshot when present and current otherwise, runs in an independent transaction, and isolates projection failure from committed Dashboard truth. Inline parsing remains best effort.

## Persistence and migration requirements

The existing six Dashboard tables remain authoritative. Only `yak_dashboard` stores `project_id`; descendants inherit ownership.

Historical Project ownership may be backfilled only from complete, resolvable Dataset/Analysis evidence that agrees on one Project. Empty, orphaned or mixed-Project history must remain unresolved and block the `NOT NULL` contract. No default Project, Project `0`, first Project or request-supplied fallback is allowed.

## HTTP compatibility

`/api/v1/dashboards/**`, request/response fields, long-ID serialization and the deprecated activate route remain stable. The only transport change is the required trusted Project context already defined by the shared Project Space contract.

## Explicit non-goals

This stage does not add a Dashboard execution engine, Dataset query execution, Dataset ONLINE/schema validation, an Analysis definition copy inside Dashboard, in-place history editing, publish-time copying, a new status machine, stronger version-number CAS/retry semantics or synchronous Lineage truth.
