# Yak Ops Business Dashboard

`yak-ops-business-dashboard` owns versioned Dashboard composition in Yak Ops.

Read these contracts before changing the module:

- [REQUIREMENTS.md](REQUIREMENTS.md) — supported behavior and non-goals.
- [DOMAIN.md](DOMAIN.md) — truth ownership and lifecycle invariants.
- [ARCHITECTURE.md](ARCHITECTURE.md) — package roles and execution flows.
- [DEPENDENCIES.md](DEPENDENCIES.md) — allowed dependency corridors.
- [REVIEW.md](REVIEW.md) — review checklist for future changes.
- [`../../CODE_STYLE.md`](../../CODE_STYLE.md) — repository-wide engineering style.

## Core model

```text
Dashboard identity
    ├── currentVersionId   -> current editable snapshot
    └── publishedVersionId -> externally effective snapshot

DashboardVersion
    ├── theme snapshot
    ├── widget snapshots
    ├── global-filter snapshots
    └── interaction snapshots
```

A Dashboard is not a mutable layout row. Saving composition appends a new immutable `DashboardVersion` and moves the current pointer. Publishing moves only the published pointer. Restoring an old version copies that historical snapshot into a **new** version.

## Truth ownership

```text
Dashboard
    owns identity + current/published pointers

DashboardVersion
    owns immutable composition snapshot

Analysis
    owns reusable Analysis definitions

Lineage
    owns lineage graph truth

Dashboard lineage subsystem
    owns best-effort projection of committed Dashboard state
```

A linked widget freezes an `analysisId` reference, not a copy of the Analysis definition. An inline widget freezes its inline payload inside the Dashboard version.

## Package map

```text
io.yak.ops.business.dashboard
├── DashboardService          stable compatibility facade
├── definition                identity mutation
├── read                      shared Dashboard identity/current read side
├── change                    committed Dashboard change fact
├── version                   append/read/restore immutable versions
├── publication               publish pointer + effective snapshot selection
├── composition               widget/layout/filter/interaction/json policies
├── gateway/analysis          Dashboard-owned Analysis reference corridor
├── gateway/lineage           Dashboard-owned Lineage graph corridor
├── reference                 historical Analysis deletion restriction
├── lineage                   derived lineage projection
├── repository                business persistence ports/adapters
├── repository/codec          persistence JSON conversion
├── dao                       MyBatis persistence implementation
├── domain                    framework-free Dashboard model
├── controller/v1             HTTP boundary
└── config                    infrastructure wiring
```

`read` and `change` are deliberately neutral packages shared by Definition, Version, Publication and Lineage. They prevent application capability packages from depending back on each other and keep the package graph acyclic.

## Key lifecycle

```text
create
  -> create Dashboard identity
  -> append V1
  -> current = V1

save
  -> normalize composition
  -> append V(n+1)
  -> current = V(n+1)

publish
  -> published = current

restore V2 while current is V7
  -> copy V2 snapshot
  -> append V8
  -> current = V8
  -> published remains unchanged
```

`currentVersion` and `publishedVersion` are intentionally independent. Editing a newer draft must not change the published snapshot.

## Cross-module boundaries

Dashboard business roles do not call Analysis or Lineage implementation APIs directly.

```text
Composition
    -> DashboardAnalysisGateway
    -> AnalysisDashboardAdapter
    -> AnalysisReferenceService

Lineage projection
    -> DashboardLineageGraphGateway
    -> LineageDashboardAdapter
    -> Lineage module
```

The direct Maven datasource dependency remains an infrastructure dependency for DataSource/Flyway/MyBatis enablement. It is not a Dashboard domain dependency.

## Validation

Authoritative module verification in a normal project environment:

```bash
mvn -pl yak-ops-business/yak-ops-business-dashboard -am test
```

Architecture tests under `src/test/java/io/yak/ops/business/dashboard/architecture` protect the package graph, gateway corridors, role stereotypes, documentation, persistence boundary and low-ambiguity code-style rules.
