ALTER TABLE yak_offline_batch_execution
    ADD COLUMN audit_carrier_json LONGTEXT NULL COMMENT 'Frozen cross-thread AuditCarrier snapshot for the Batch';
