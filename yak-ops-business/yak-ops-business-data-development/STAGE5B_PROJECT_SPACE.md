# Stage 5B — Data Development × Dataset Project Contract

Stage 5A completed the Data Development core ownership/runtime boundary. Stage 6 then made Dataset a fail-closed `PROJECT_ROOT`. Stage 5B closes the integration seam between the two modules and contracts the Data Development project columns physically.

## Ownership model

```text
yak_dev_directory        PROJECT_ROOT
yak_dev_node             PROJECT_ROOT
yak_dev_task_draft       INHERITED(node_id)
yak_dev_task_revision    INHERITED(node_id)
yak_dev_task_execution   PROJECT_RUNTIME
yak_dev_lineage_outbox   PROJECT_RUNTIME

Dataset                   PROJECT_ROOT
DatasetVersion            INHERITED(dataset_id)
DatasetField              INHERITED(version_id -> dataset_id)
```

Draft and Revision deliberately do not receive duplicate `project_id` columns.

## Data Development × Dataset boundary

The owning identities are independent source truths:

```text
DevelopmentNode.project_id
          │
          │ must equal
          ▼
Dataset.project_id
```

Data Development does not pass a caller-selected Project ID into Dataset. Instead:

1. Data Development resolves the Dataset Node inside the current Project.
2. Dataset executes through its Stage 6 fail-closed repository boundary.
3. `DevelopmentDatasetFacade` exposes the persisted Dataset Project as part of the internal snapshot.
4. Data Development compares that Project against `DevelopmentNode.requireProjectId()`.
5. A mismatch fails closed before the node can be marked configured.

Standalone Dataset SQL preview/query continues to rely on the trusted CurrentProject plus the already project-scoped Dataset/DataSource boundaries. Legacy TaskAsset flows retain the existing TaskAsset and source SQL node same-Project checks.

## Physical contract

`V2__contract_project_scope.sql` changes only the Project Root / Runtime columns to `NOT NULL`:

```text
yak_dev_directory.project_id
yak_dev_node.project_id
yak_dev_task_execution.project_id
yak_dev_lineage_outbox.project_id
```

The migration performs no ownership inference, no default-project update, and no `0`/hard-coded Project fallback.

## Deployment prerequisite

Flyway runs before `ApplicationReadyEvent`, while the existing `DataDevelopmentProjectCompatibilityBackfill` runs at ApplicationReady. Therefore an historical database must have successfully run a pre-contract application version first.

Before deploying this contract, all four checks must return `0`:

```sql
SELECT COUNT(*) FROM yak_dev_directory WHERE project_id IS NULL;
SELECT COUNT(*) FROM yak_dev_node WHERE project_id IS NULL;
SELECT COUNT(*) FROM yak_dev_task_execution WHERE project_id IS NULL;
SELECT COUNT(*) FROM yak_dev_lineage_outbox WHERE project_id IS NULL;
```

The Data Development / Dataset relationship should also be clean:

```sql
SELECT COUNT(*)
FROM yak_dataset d
JOIN yak_dev_node n ON n.id = d.development_node_id
WHERE d.project_id <> n.project_id;
```

If legacy NULL ownership remains, Flyway V2 is expected to fail. That is intentional fail-fast behavior.

## Non-goals

- Dataset `project_id NOT NULL` contract (Stage 6.1)
- adding `project_id` to Development Draft/Revision
- adding `project_id` to DatasetVersion/DatasetField
- Stage 7 Offline/Realtime Sync
- removing the compatibility backfills that Stage 6 still composes during historical cutover
