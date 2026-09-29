# Dashboard Architecture

## Architecture principle

Dashboard is a Project-owned, versioned composition control plane. Package names describe business capability and role; cross-module access uses owner-defined gateways instead of a generic Service/Support layer.

```text
HTTP @ProjectScope
  -> DashboardService
      -> Definition / Version / Publication
          -> shared Read side
          -> Composition
              -> Analysis gateway
              -> Dataset gateway
          -> Repositories
              -> DAO + trusted CurrentProject

Committed Dashboard change(projectId, dashboardId)
  -> AFTER_COMMIT listener
  -> ProjectContextScope
  -> effective snapshot reader
  -> Lineage gateway
```

## Package roles

### Root facade

`DashboardService` is the stable application and HTTP compatibility facade. It delegates to explicit internal roles and does not absorb their implementation logic.

### `definition`

`DashboardManager` owns stable Dashboard identity create/delete. The root identity is persisted with `project_id` from `CurrentProject`; callers never choose ownership through request data.

### `read`

`DashboardReader` is the shared read-side entry for Dashboard identity and current detail. Repository reads fail closed when the requested Dashboard is outside the current Project.

### `change`

`DashboardChangedEvent` is a framework-free committed fact containing `projectId`, `dashboardId` and deletion semantics. The frozen Project is required because after-commit projection cannot depend on the original request ThreadLocal.

### `version`

`DashboardVersionAppender`, `DashboardVersionManager` and `DashboardVersionReader` own append-only version history. Every child operation first proves its owning Dashboard in the current Project. Version code does not publish.

### `publication`

`DashboardPublisher` moves the published pointer to the current immutable version. `DashboardEffectiveSnapshotReader` selects published when present and current otherwise. Publication does not append or rewrite historical versions.

### `composition`

`DashboardCompositionNormalizer` coordinates candidate validation. Widget, layout, filter, interaction and JSON policies keep their focused rules.

Composition enters external truth only through:

```text
DashboardAnalysisGateway
DashboardDatasetGateway
```

A reusable Analysis, `activeDatasetId`, and an explicit inline `datasetId` must resolve inside the current Project. This is an ownership check, not Dataset ONLINE/schema/query validation.

### `gateway/analysis`

```text
DashboardAnalysisGateway
  -> AnalysisDashboardAdapter
  -> AnalysisReferenceService
```

### `gateway/dataset`

```text
DashboardDatasetGateway
  -> DatasetDashboardAdapter
  -> DatasetReader
```

### `reference`

Dashboard implements Analysis deletion safety through `DashboardReferenceRepository`. Historical widget references remain relevant and the SQL join is constrained by Dashboard Project ownership.

### `lineage`

`DashboardLineageRefreshListener` restores the event's Project with `ProjectContextScope`, then reads the effective snapshot. `DashboardLineageSynchronizer` owns deterministic projection and `DashboardInlineLineageExtractor` owns best-effort inline evidence parsing. Shared graph access goes only through `DashboardLineageGraphGateway`.

### `repository` and `dao`

Repository ports expose domain/JDK values. Adapters translate persistence representations. DAO owns MyBatis details and applies `project_id` to root CRUD, overview aggregation and cross-domain reference joins. Child tables inherit through their parent and are never returned by a globally trusted child ID alone.

### `domain`

Domain values are framework-free and do not import Analysis, Dataset or Lineage implementation APIs.

### `controller/v1`

Controllers are `PROJECT_REQUIRED`, enter through `DashboardService`, and preserve existing REST/JSON compatibility.

## Create and save flow

```text
request
 -> trusted Project context
 -> normalize candidate
 -> prove Analysis/Dataset references
 -> create or require Project-owned Dashboard
 -> append immutable version
 -> move current pointer
 -> publish DashboardChangedEvent(projectId, dashboardId)
 -> commit
 -> restore Project for lineage projection
```

## Restore flow

```text
read Project-owned historical Vn
 -> copy snapshot to candidate
 -> normalize and prove current references
 -> append next Vm
 -> current = Vm
 -> published unchanged
```

Restore is copy-forward; no old version row is reactivated or mutated.

## Publish flow

Publishing proves the Dashboard and target version share the current Project, then moves only the published pointer. Re-publishing the same current version is a business no-op.

## Persistence and migration

`yak_dashboard` is `PROJECT_ROOT`. `yak_dashboard_version` and all component rows are inherited facts.

Historical ownership is backfilled only when all explicit Dataset/Analysis evidence resolves and agrees on one Project. Empty, orphaned or mixed-Project Dashboards remain unresolved and the subsequent `NOT NULL` contract blocks deployment rather than guessing.

## Compatibility and known gap

The REST paths, request/response fields, long-ID serialization, append-only history, current/published divergence and deprecated activate alias remain stable.

The known version allocation concurrency boundary remains: the unique `(dashboard_id, version_no)` key protects durable uniqueness, while richer retry/CAS allocation is future work.
