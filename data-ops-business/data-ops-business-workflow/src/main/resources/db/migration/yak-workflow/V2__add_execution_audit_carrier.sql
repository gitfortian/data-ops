-- Durable cross-thread correlation for Workflow business audit.
-- Historical executions are intentionally not backfilled with fabricated audit operations.
ALTER TABLE yak_workflow_execution
    ADD COLUMN audit_carrier_json LONGTEXT NULL AFTER runtime_metadata_json;
