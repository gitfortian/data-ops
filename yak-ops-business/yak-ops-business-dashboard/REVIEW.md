# Dashboard Review Checklist

Use this checklist for Dashboard changes.

## Truth ownership

- Does Dashboard remain the sole owner of Dashboard identity/current/published pointers?
- Does an immutable DashboardVersion remain the owner of its composition snapshot?
- Is Analysis still the owner of reusable Analysis definitions?
- Is Lineage still derived projection rather than command truth?

## Version lifecycle

- Does save append a new version instead of mutating history?
- Does restore copy-forward into a new version rather than rewind the current pointer to an old row?
- Can current and published pointers still differ safely?
- Does save avoid moving the published pointer?
- Does publish only move the published pointer?
- Is `activateVersion` kept as compatibility vocabulary only?

## Composition

- Does each widget still choose exactly one of `analysisId` / `inlineAnalysis`?
- Are layout/filter/interaction rules owned by explicit policies?
- Is dynamic JSON handling contained at the JSON policy/persistence/adapter boundaries?
- Did the change avoid inventing Dataset lifecycle/schema ownership for `activeDatasetId`?

## Cross-module boundaries

- Does Analysis access go through `DashboardAnalysisGateway`?
- Is Analysis implementation knowledge confined to `AnalysisDashboardAdapter`?
- Does Lineage access go through `DashboardLineageGraphGateway`?
- Is Lineage implementation knowledge confined to `LineageDashboardAdapter`?
- Does the Analysis deletion guard query `DashboardReferenceRepository` rather than DAO?

## Persistence

- Do application roles depend on repository ports instead of DAO/PO/Mapper?
- Do repository contracts expose only Dashboard domain/JDK values?
- Are JSON columns translated by the persistence codec/adapter rather than leaked upward?
- Are schema/Flyway changes intentionally scoped rather than mixed into architecture cleanup?

## Projection

- Is Dashboard lineage triggered after commit?
- Does it use `DashboardEffectiveSnapshotReader` instead of duplicating effective-snapshot logic?
- Does projection run independently from the already committed Dashboard transaction?
- Does projection failure remain isolated and observable through logs?
- Does inline parse uncertainty remain projection evidence rather than Dashboard state?

## Compatibility

- Are `/api/v1/dashboards/**` paths unchanged unless the PR explicitly declares an API change?
- Are request/response JSON and long-ID behavior preserved?
- Is the deprecated activate route preserved when compatibility is required?

## Concurrency

- Did the PR accidentally claim the `nextVersionNo -> appendVersion` concurrency gap is solved?
- If changing version allocation, is that change isolated, documented as domain behavior and protected by concurrency tests?

## Architecture

- Does the package name communicate capability and role?
- Is the package graph acyclic?
- Did a generic `service/common/helper/utils/base/support` bucket reappear?
- Are `@Service`, `@Component`, `@Repository` and `@Configuration` used according to repository `CODE_STYLE.md`?
- Which behavior test and architecture test protect the change?
