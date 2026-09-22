# Analysis Architecture

## Purpose

Analysis is a Project-owned reusable analytical-definition control plane, not a query execution runtime. Definition, query semantics, visualization, Dataset binding, references, Lineage projection and persistence retain explicit roles.

## Package map

```text
io.yak.ops.business.analysis
├── AnalysisService
├── AnalysisReferenceService
├── AnalysisDeletionGuard
├── controller/v1
│   ├── dto
│   ├── vo
│   └── converter
├── definition
├── domain
├── query
├── visualization
├── reference
├── lineage
├── gateway
│   ├── dataset
│   └── lineage
├── repository
│   └── codec
├── dao
└── config
```

The root public types remain compatibility boundaries. New implementation roles belong to a named subsystem.

## Stable facades

`AnalysisService` delegates use cases to `AnalysisManager` and `AnalysisReader`. `AnalysisReferenceService` exposes only narrow existence validation for downstream modules. `AnalysisDeletionGuard` is the stable extension point used by owners such as Dashboard.

## Definition flow

```text
HTTP @ProjectScope
 -> AnalysisService
 -> AnalysisManager
 -> AnalysisDefinitionNormalizer
 -> AnalysisDatasetGateway
 -> persist with trusted CurrentProject.projectId
 -> publish AnalysisChangedEvent(projectId, analysisId)
```

Create/update normalize query and visual semantics and validate the Dataset through the owner-defined gateway. Delete runs every guard before removing metadata. No request field owns Project identity.

## Dataset boundary

```text
AnalysisDatasetGateway
 -> DatasetAnalysisAdapter
 -> DatasetBindingPolicy
```

Only the adapter knows Dataset implementation APIs. Because Dataset binding is Project-scoped, the reference must belong to the current Project in addition to satisfying Dataset's ONLINE/current-schema contract.

## Persistence

```text
Definition / Reader
 -> AnalysisRepository
 -> AnalysisRepositoryAdapter
 -> AnalysisDao
 -> AnalysisMapper / AnalysisPO
```

`yak_analysis` is a Project root. DAO insert stores `CurrentProject.requireProjectId()` and all reads/updates/deletes include the same trusted predicate. `AnalysisJsonCodec` alone owns JSON-column representation.

## Lineage projection

```text
AnalysisChangedEvent(projectId, analysisId)
 -> AFTER_COMMIT listener
 -> ProjectContextScope
 -> AnalysisLineageSynchronizer (REQUIRES_NEW)
 -> AnalysisLineageGraphGateway
 -> LineageAnalysisAdapter
```

The listener restores the frozen Project before reading Analysis state or updating derived graph evidence. Projection failure is logged and cannot roll back committed Analysis truth.

## Dependency and stereotype rules

Controller enters only through stable facades. Definition reaches Dataset and Lineage only through Analysis-owned ports. Domain/query/visualization values do not depend on Spring, HTTP, MyBatis or external business implementations.

`@Service` is reserved for stable facades; explicit internal roles use `@Component`; persistence adapters use `@Repository`.

## Compatibility

REST paths, request/response fields, Dataset binding semantics, Dashboard reference guards, Lineage evidence and query semantics remain stable. Analysis Version, execution, result cache and runtime remain explicit non-goals until separately designed.
