ALTER TABLE yak_ops_data_service_call_log
    ADD COLUMN consumer_id BIGINT NULL AFTER api_key_id,
    ADD COLUMN source_revision_id BIGINT NULL AFTER api_key_prefix,
    ADD COLUMN source_revision_no INT NULL AFTER source_revision_id;

CREATE INDEX idx_data_service_call_log_consumer_time
    ON yak_ops_data_service_call_log(project_id, consumer_id, create_time);

CREATE INDEX idx_data_service_call_log_api_revision_time
    ON yak_ops_data_service_call_log(project_id, api_id, source_revision_id, create_time);
