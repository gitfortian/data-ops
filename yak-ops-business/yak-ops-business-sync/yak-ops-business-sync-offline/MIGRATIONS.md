# Offline Sync Migration Contract

Offline Sync owns an isolated Flyway namespace:

```text
classpath:db/migration/yak-offline-sync
yak_offline_sync_schema_history
```

Migrations from Offline Sync must not be placed in Datasource, Data Development, Data Service, or another module's namespace.

## Baseline

`V1__baseline_offline_sync.sql` is the consolidated first-release baseline. It directly creates the current core Offline Sync tables without replaying the earlier development-time expand/backfill chain:

```text
yak_offline_job_definition
yak_offline_batch_execution
yak_offline_job_execution
yak_offline_execution_event
yak_offline_sync_cursor
```

The baseline must not contain historical `ALTER TABLE` / `UPDATE` repair steps.

Once additive migrations exist after V1, V1 is treated as immutable: do not edit its checksum to add later fields.

## Additive migrations

Current ordered migration sequence:

```text
V1  baseline Offline Sync schema
V2  task notification policy
V3  durable Batch AuditCarrier correlation snapshot
```

`V2__add_offline_notification_config.sql` adds nullable task notification configuration without rewriting legacy rows.

`V3__add_batch_audit_carrier.sql` adds nullable `yak_offline_batch_execution.audit_carrier_json`. The field freezes the AuditCarrier for one business Batch so later Attempt retry/reconcile work can rejoin the same AuditOperation even after the original HTTP/thread/trace has ended. Existing historical batches remain nullable and queryable; the migration does not fabricate an operation for history that predates business audit correlation.

## Migration rules

1. Applied migration version, description, and checksum are immutable.
2. New schema changes use the next incremental version; do not fold them back into V1.
3. Do not invent Project IDs, audit operation IDs, or other historical ownership/correlation facts during SQL migration.
4. Additive compatibility columns should stay nullable when old rows cannot be truthfully backfilled.
5. Runtime code must remain safe when reading historical rows created before the new additive field existed semantically.

## Development database reset

Old pre-consolidation development histories may still require a one-time reset if they were created before the V1 baseline was established. Do not use such a reset on a database containing valuable data.

For databases already on the current V1/V2 migration stream, normal Flyway upgrade applies V3; do not reset them just to add AuditCarrier correlation.
