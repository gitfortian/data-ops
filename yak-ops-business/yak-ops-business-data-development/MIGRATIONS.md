# Data Development Migration Contract

Data Development owns one Flyway namespace:

```text
location = classpath:db/migration/yak-data-development
history  = yak_data_development_schema_history
```

## First-release baseline

Yak Ops is still before its first formal product release, and the current development database is disposable. The former development-time chain:

```text
V1   initial authoring schema
V2   editor settings
V3   task execution history
V8   node updater
V9   temporary development graph
V10  remove development graph
V11  Data Service node lifecycle
V12  system env variables
V13  SQL lineage outbox
V14  Project scope
V15  execution control plane
```

is intentionally squashed into:

```text
V1__baseline_data_development.sql
```

The baseline directly creates the current final physical schema. It does not replay intermediate `ALTER TABLE` statements and does not create `yak_dev_graph`, because graph ownership was removed before the first release.

Current baseline tables:

```text
yak_dev_directory
yak_dev_node
yak_dev_task_draft
yak_dev_task_revision
yak_dev_editor_setting
yak_dev_task_execution
yak_dev_data_service_draft
yak_dev_data_service_revision
yak_dev_lineage_outbox
yak_system_env_var
```

The baseline includes the final Project/execution-control fields and indexes directly, including `project_id`, `updated_by`, `schema_version`, and `retry_of_execution_id` where applicable.

## Development database reset

This squash intentionally does not preserve pre-release Flyway checksums/history. Before starting the application after adopting this baseline, reset the disposable Data Development development tables/history once:

```sql
DROP TABLE IF EXISTS yak_dev_graph;
DROP TABLE IF EXISTS yak_dev_data_service_revision;
DROP TABLE IF EXISTS yak_dev_data_service_draft;
DROP TABLE IF EXISTS yak_dev_lineage_outbox;
DROP TABLE IF EXISTS yak_dev_task_execution;
DROP TABLE IF EXISTS yak_dev_editor_setting;
DROP TABLE IF EXISTS yak_dev_task_revision;
DROP TABLE IF EXISTS yak_dev_task_draft;
DROP TABLE IF EXISTS yak_dev_node;
DROP TABLE IF EXISTS yak_dev_directory;
DROP TABLE IF EXISTS yak_system_env_var;
DROP TABLE IF EXISTS yak_data_development_schema_history;
```

Do not use this reset on a database containing valuable or production data.

## After the first formal release

Once V1 is released, it becomes immutable. Future schema changes must be incremental:

```text
V1__baseline_data_development.sql
V2__...
V3__...
```

Rules:

1. Data Development migrations stay only in `db/migration/yak-data-development`.
2. Do not edit, rename, delete, or squash an applied migration after the first formal release.
3. A first-release baseline describes final schema state; it should not contain historical create-then-drop or add-then-remove steps.
4. Tables owned by another module must not be introduced into this namespace without an explicit ownership decision.
5. Migration ownership changes require documentation and architecture guards in the same PR.
