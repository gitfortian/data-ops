# Stage 6.1 — Dataset Project Physical Contract

Stage 6 completed the Dataset runtime Project Space boundary and historical compatibility backfill. Stage 6.1 is the physical database Contract step: project ownership that is already required by runtime is now also required by MySQL.

## Scope

Only Dataset-owned Project columns are contracted:

```text
yak_dataset.project_id                    PROJECT_ROOT        -> NOT NULL
yak_dataset_query_performance.project_id  project runtime fact -> NOT NULL
```

`yak_dataset_version` and `yak_dataset_field` remain `INHERITED` through their parent Dataset and do not receive duplicate `project_id` columns.

## Migration sequence

```text
V3  Dataset project expand (nullable)
        ↓
V4  Query performance project column (nullable)
        ↓
Stage 6 ApplicationReady compatibility backfill
        ↓
Project A/B runtime verification
        ↓
V5  Stage 6.1 physical contract (NOT NULL)
```

`V5__contract_project_scope.sql` performs no ownership inference and no compatibility update. If NULL ownership remains, the Flyway migration is expected to fail fast.

## Deployment prerequisite

Flyway runs before `ApplicationReadyEvent`. Therefore an historical database must have successfully started with the Stage 6 runtime/backfill version before this Contract is deployed.

A database that jumps directly from a pre-Stage-6 schema to a release containing V5 is not a supported one-start upgrade path: V5 can run before `DatasetProjectCompatibilityBackfill`, so remaining legacy NULL rows will make the migration fail. Do not work around this by adding a magic Project ID or an UPDATE to the Contract migration.

Before deploying Stage 6.1, both checks must return `0`:

```sql
SELECT COUNT(*)
FROM yak_dataset
WHERE project_id IS NULL;

SELECT COUNT(*)
FROM yak_dataset_query_performance
WHERE project_id IS NULL;
```

The Stage 6 source-truth invariants should also remain clean:

```sql
-- Dataset Node ownership
SELECT COUNT(*)
FROM yak_dataset d
JOIN yak_dev_node n ON n.id = d.development_node_id
WHERE n.project_id IS NOT NULL
  AND d.project_id <> n.project_id;

-- TaskAsset ownership
SELECT COUNT(*)
FROM yak_dataset d
JOIN yak_dataset_version v
  ON v.dataset_id = d.id
 AND v.source_task_asset_id > 0
JOIN yak_task_asset a ON a.id = v.source_task_asset_id
WHERE a.project_id IS NOT NULL
  AND d.project_id <> a.project_id;

-- DataSource ownership for numeric SQL_QUERY datasource identities
SELECT COUNT(*)
FROM yak_dataset d
JOIN yak_dataset_version v
  ON v.dataset_id = d.id
 AND v.data_source_id REGEXP '^[0-9]+$'
JOIN yak_ops_data_source s
  ON s.id = CAST(v.data_source_id AS UNSIGNED)
WHERE s.project_id IS NOT NULL
  AND d.project_id <> s.project_id;

-- Query diagnostics inherit Dataset ownership
SELECT COUNT(*)
FROM yak_dataset_query_performance q
JOIN yak_dataset d ON d.id = q.dataset_id
WHERE q.project_id <> d.project_id;
```

All four invariant checks should return `0`.

## Contract rules

The Stage 6.1 migration must remain a pure Contract migration:

- no `UPDATE yak_dataset`;
- no `UPDATE yak_dataset_query_performance`;
- no hard-coded `project_id = 1`;
- no `project_id = 0` fallback;
- no Project inference from TaskAsset, DevelopmentNode, or DataSource;
- no duplicate Project column on DatasetVersion or DatasetField.

Historical ownership inference belongs to the Stage 6 compatibility backfill, not to Flyway Contract SQL.

## Compatibility code

This stage does not remove the existing Dataset compatibility backfill. The current rollout still has historical deployment concerns across adjacent Project Space stages, and removing compatibility infrastructure piecemeal would make that upgrade path harder to reason about.

The backfill remains idempotent, but it is **not** a substitute for the deployment prerequisite above because it executes after Flyway. Compatibility cleanup should happen only after the supported Project Space upgrade path is explicitly hardened and verified.

## Verification

Recommended module test:

```bash
mvn -pl yak-ops-business/yak-ops-business-dataset -am test
```

Recommended database verification:

1. start a representative historical database on the Stage 6 runtime/backfill release;
2. confirm the NULL and ownership-mismatch preflight queries all return `0`;
3. deploy Stage 6.1 and confirm V5 succeeds;
4. verify `information_schema.columns` reports both contracted `project_id` columns as `IS_NULLABLE = 'NO'`;
5. rerun Project A/B Dataset isolation smoke tests.

## Non-goals

- Stage 7 Offline Sync Project Space;
- Stage 7 Realtime Sync Project Space;
- changing Dataset runtime APIs or repository behavior;
- changing Task Catalog / Lineage Project propagation;
- adding Project columns to DatasetVersion / DatasetField;
- solving the global multi-stage direct-upgrade problem in this PR;
- removing all Project Space compatibility corridors.
