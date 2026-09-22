# Dashboard Dependency Contract

## Direction

Dependencies point from transport/application orchestration toward explicit business and persistence ports. Cross-module dependencies pass only through Dashboard-owned gateways.

## Package matrix

| Source | Allowed direct Dashboard dependencies |
| --- | --- |
| root `DashboardService` | `definition`, `read`, `version`, `publication`, `domain` |
| `controller` | root stable facade, controller DTO/VO/converter, domain values needed by transport conversion |
| `definition` | `read`, `change`, `domain`, `composition`, version appender, `repository` ports |
| `read` | `domain`, `repository` ports |
| `change` | JDK only |
| `version` | `read`, `change`, `domain`, `composition`, `repository` ports |
| `publication` | `read`, `change`, `domain`, `repository` ports |
| `composition` | `domain`, `gateway.analysis`, `gateway.dataset` ports |
| `reference` | `repository` reference port plus external Analysis deletion extension contract |
| `lineage` | `change`, `domain`, `publication`, `gateway.lineage` port |
| `gateway.analysis` | Dashboard-owned port; adapter may call the Analysis stable reference facade |
| `gateway.dataset` | Dashboard-owned port; adapter may call the Dataset stable scoped reader |
| `gateway.lineage` | Dashboard-owned port; adapter may call Lineage module APIs |
| `repository` | `domain`, `dao`, `repository.codec`, infrastructure annotations |
| `repository.codec` | Jackson persistence infrastructure only |
| `dao` | DAO/PO/Mapper/MyBatis, `CurrentProject`, datasource infrastructure |
| `domain` | JDK only |
| `config` | wiring/infrastructure only |

The matrix describes permitted direction, not permission for every class to depend on every package in its row. Keep dependencies narrower when possible, and keep the actual graph acyclic.

## Stable facade corridor

HTTP callers enter through:

```text
DashboardController / DashboardOverviewController
    -> DashboardService
```

Both controllers are `PROJECT_REQUIRED`. They must not inject repositories, DAOs, cross-module adapters or lineage internals.

## Project context corridor

`yak_dashboard` is a Project root. `DashboardVersion`, Widget, Filter, Binding and Interaction inherit ownership through their owning Dashboard.

```text
HTTP @ProjectScope
    -> trusted CurrentProject
    -> root DAO predicate: project_id = currentProject.requireProjectId()
```

Child reads and writes must prove the parent Dashboard before returning or mutating inherited rows. A request ID from another workspace is treated as absent in the current workspace.

After-commit work freezes `projectId` in `DashboardChangedEvent` and restores it through `ProjectContextScope`. It must not assume the request ThreadLocal survives commit callbacks.

## Analysis corridor

Composition cannot import the Analysis module directly.

```text
composition
 -> DashboardAnalysisGateway
 -> AnalysisDashboardAdapter
 -> AnalysisReferenceService
```

Only `gateway/analysis/AnalysisDashboardAdapter` may import Analysis application/reference types. Because Analysis reads are Project-scoped, this corridor also proves that reusable widget references belong to the current Project.

Dashboard's Analysis deletion guard is the inverse extension mechanism defined by Analysis. It queries historical references through `DashboardReferenceRepository`, whose implementation constrains the reference join by the current Project.

## Dataset corridor

`activeDatasetId` and an explicit `inlineAnalysis.datasetId` are Project references. Dashboard validates only identity and same-Project ownership here; Dataset ONLINE state, schema compatibility and query execution remain Dataset-owned concerns.

```text
composition
 -> DashboardDatasetGateway
 -> DatasetDashboardAdapter
 -> DatasetReader
```

Only `gateway/dataset/DatasetDashboardAdapter` may import Dataset implementation APIs. No controller, composition policy, repository or DAO may bypass this port.

## Lineage corridor

Dashboard lineage business code cannot import Lineage implementation types.

```text
lineage
 -> DashboardLineageGraphGateway
 -> LineageDashboardAdapter
 -> Lineage module
```

Only `gateway/lineage/LineageDashboardAdapter` may know the Lineage API and graph translation infrastructure.

## Repository corridor

Application packages use repository interfaces only.

```text
application role
 -> DashboardRepository / DashboardVersionRepository / DashboardReferenceRepository
 -> adapter
 -> DashboardDao
```

Repository interfaces must not expose PO/MyBatis types, controller DTO/VO or serialized JSON strings as business contracts.

## Domain purity

`domain` must not import Spring, Jackson, MyBatis, DAO/repository adapters, controller types, Analysis APIs, Dataset APIs or Lineage APIs. Domain values remain plain Java records, enums and value objects.

## Version and publication separation

`version` owns append/read/restore and must not update the published pointer. `publication` owns the published-pointer transition and must not append or mutate historical versions. This protects `current != published` as a first-class invariant.

## Infrastructure dependencies

Dashboard directly depends on:

- `yak-ops-core` for trusted Project context;
- `yak-ops-business-datasource` for business DataSource/Flyway/MyBatis infrastructure;
- `yak-ops-business-analysis` through the Analysis gateway adapter;
- `yak-ops-business-dataset` through the Dataset gateway adapter;
- `yak-ops-business-lineage` through the Lineage gateway adapter.

A Maven dependency does not grant arbitrary package access. Owner-defined gateways remain mandatory.

## Forbidden broad buckets

Do not reintroduce top-level production packages named `service`, `common`, `helper`, `helpers`, `utils`, `util`, `base`, `persistence` or `support`.

## Change rule

A dependency-graph change requires the truth owner, this contract and executable architecture tests to change together. Do not delete or weaken a guard merely to make a new import compile.
