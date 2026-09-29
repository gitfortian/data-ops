# Dataset Stage 6 Project Space

Stage 6 turns Dataset from a nullable, optionally filtered migration surface into a project-scoped BI asset boundary.

## Ownership

| Data | Ownership |
| --- | --- |
| `yak_dataset` | `PROJECT_ROOT` |
| `yak_dataset_version` | `INHERITED` from `yak_dataset` |
| `yak_dataset_field` | `INHERITED` from `yak_dataset_version -> yak_dataset` |
| `yak_dataset_query_performance` | project-scoped observability/runtime fact |

Version and Field deliberately do **not** duplicate `project_id`. Reads are qualified through joins to the owning Dataset root.

## Runtime contract

Dataset HTTP APIs are `PROJECT_REQUIRED`.

Normal Dataset repository operations require `CurrentProject.requireProjectId()` and do not fall back to global reads when project context is missing. This covers root CRUD and inherited Version / Field reads.

Dataset overview and query-performance reads are also fail-closed by Project. The query-performance recorder remains best-effort: an observability persistence failure may fall back to an in-memory buffer, but that fallback is tagged with the current Project when available and cannot be read through an unscoped Dataset API.

The frontend request policy marks `/api/v1/datasets/**` as `PROJECT_REQUIRED`; Project identity is carried by the trusted request context rather than Dataset payloads.

## Source binding

Dataset sources must resolve inside the same Project Space:

- Task Catalog binding requires `TaskAsset.projectId == CurrentProject.projectId`.
- Immutable `TaskSourceRevision.sourceProjectId` must also match CurrentProject.
- DataSource-backed SQL/schema access continues through the already project-scoped DataSource execution/catalog boundary.
- Data Development Dataset Nodes first resolve their owning DevelopmentNode through the Stage 5A fail-closed repository and retain their existing same-project TaskAsset / SQL node checks.

Cross-project sharing remains unsupported in Project Space V1.

## Lineage projection

Dataset lineage is a projection and therefore receives Project identity from Dataset source truth rather than from Lineage itself.

```text
Dataset transaction in Project A
    -> DatasetLineageRefreshRequested(project=A, dataset=id)
    -> AFTER_COMMIT
    -> ProjectContextScope(Project A)
    -> Dataset snapshot
    -> Lineage RegisterAsset/RegisterRelation(sourceProjectId=A)
```

The AFTER_COMMIT event carries durable Project identity explicitly so a future async executor cannot accidentally depend on the originating HTTP ThreadLocal.

## Historical compatibility backfill

`DatasetProjectCompatibilityBackfill` runs after Dataset Flyway and explicitly completes DataSource / Data Development backfills first.

Stage 4 intentionally allowed Task Catalog projection rows to remain `project_id IS NULL` until their real producer became Project-aware. A historical Dataset may already reference one of those legacy TaskAssets. Before Dataset ownership is inferred, the backfill may claim only this narrow case:

```text
DatasetVersion.source_task_asset_id
    -> legacy TaskAsset(source=DATA_DEVELOPMENT, project=NULL)
    -> TaskAsset.source_ref
    -> owning DevelopmentNode.project_id
```

This is not Dataset choosing a Project. The Project comes from the Data Development producer Source Truth. If a scoped TaskAsset with the same source/sourceRef/project already exists under another identity, startup fails instead of silently merging immutable references. If any Dataset-referenced TaskAsset still has no Project after this producer-owned claim, startup also fails; Dataset is not allowed to move itself to a default Project while retaining an unscoped source reference.

Before mutating Dataset ownership it then checks whether trusted sources disagree. Candidate ownership comes from:

1. existing `yak_dataset.project_id`;
2. owning `yak_dev_node.project_id` for Development Dataset Nodes;
3. referenced `yak_task_asset.project_id` for QUERY_REVISION versions;
4. referenced `yak_ops_data_source.project_id` for numeric SQL_QUERY datasource identities.

If one Dataset has multiple distinct candidate Projects, startup fails instead of choosing one.

After conflict validation, ownership is inferred from those sources in that order. Only rows with no usable source truth are moved to the compatibility default Project. Query-performance rows inherit their Dataset Project when possible and otherwise use the same compatibility fallback.

Startup then asserts:

- every Dataset-referenced TaskAsset has Project ownership;
- no Dataset remains with `project_id IS NULL`;
- no query-performance row remains with `project_id IS NULL`;
- Dataset / DevelopmentNode ownership agrees;
- Dataset / TaskAsset ownership agrees where TaskAsset has Project identity;
- Dataset / DataSource ownership agrees where the stored datasource id resolves to a scoped DataSource;
- Dataset query diagnostics agree with their Dataset owner.

## Physical Contract

This Stage 6 PR intentionally keeps the existing V3 nullable database column during rollout.

`project_id NOT NULL` must be a follow-up **Stage 6.1 Contract** after a representative historical database has started successfully with this backfill and Project A/B isolation has been verified. Adding the NOT NULL migration in the same deployment would run Flyway before the ApplicationReady backfill and could prevent an existing database from starting.

Stage 6.1 should be small and limited to physical constraints / removal of any now-proven compatibility code. Data Development × Dataset final integration remains Stage 5B after Stage 6.
