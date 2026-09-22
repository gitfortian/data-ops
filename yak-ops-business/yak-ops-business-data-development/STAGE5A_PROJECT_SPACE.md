# Data Development Stage 5A — Project Space Core

Stage 5A hardens the existing Data Development Project Space support after the Stage 4 Task Catalog / Lineage projection substrate.

## Ownership model

| Object | Ownership | Stage 5A behavior |
| --- | --- | --- |
| `yak_dev_directory` | `PROJECT_ROOT` | CRUD requires trusted `CurrentProject`; no empty-context global fallback |
| `yak_dev_node` | `PROJECT_ROOT` | creation and CRUD require trusted `CurrentProject`; request bodies cannot choose `projectId` |
| `yak_dev_task_draft` | `INHERITED` | inherits Project through `node_id`; no duplicate `project_id` column |
| `yak_dev_task_revision` | `INHERITED` | inherits Project through `node_id`; Task Catalog revision projection obtains Project from the owning node |
| `yak_dev_task_execution` | `PROJECT_RUNTIME` | persists Project at submission; normal reads/writes fail closed; only reconciliation dispatcher may scan projects |
| `yak_dev_lineage_outbox` | `PROJECT_RUNTIME` | persists Project when enqueued; worker restores durable ProjectContext before Lineage IO |

## Source Truth rules

- HTTP node creation never accepts `projectId`; selected Project is resolved by the shared Project request context.
- Data Development Node is the Source Truth for task Project ownership.
- `DevelopmentTaskPublisher` publishes `node.projectId` to Task Catalog.
- `DataDevelopmentTaskRevisionProvider` returns the same `sourceProjectId`, allowing Task Catalog to reject Project drift.
- Lineage Outbox persists the Node Project and restores that Project before registering Lineage projections.
- A Lineage task is rejected when its durable Project, Node Project, or revision-to-node ownership does not match.

## Explicit cross-project dispatcher corridors

Two background scans remain intentionally cross-project:

- execution reconciliation (`listActiveForReconciliation`)
- lineage outbox polling (`due`)

They may only return rows with a persisted non-null Project ID. The worker/control plane must enter `ProjectContextScope` for each row before performing project-scoped business IO.

## Deferred work

Stage 5A intentionally does **not**:

- make Data Development `project_id` columns `NOT NULL`;
- remove the historical compatibility backfill;
- projectize Dataset binding or close the Data Development ↔ Dataset integration corridor;
- introduce Project columns on Draft/Revision child tables;
- perform the final Data Development Contract migration.

Dataset ownership is handled in Stage 6. Data Development × Dataset integration and final Contract cleanup remain Stage 5B / later global Contract work.
