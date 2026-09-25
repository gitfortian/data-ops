-- Nullable by design: historical query diagnostics predate stable execution-time subject capture.
ALTER TABLE yak_dataset_query_performance
    ADD COLUMN subject_type VARCHAR(32) NULL AFTER error_message,
    ADD COLUMN subject_source_domain VARCHAR(64) NULL AFTER subject_type,
    ADD COLUMN subject_source_identity VARCHAR(255) NULL AFTER subject_source_domain,
    ADD COLUMN subject_display_hint VARCHAR(255) NULL AFTER subject_source_identity;

CREATE INDEX idx_yak_dataset_query_performance_subject_time
    ON yak_dataset_query_performance (
        project_id,
        subject_type,
        subject_source_domain,
        subject_source_identity,
        started_at,
        id
    );
