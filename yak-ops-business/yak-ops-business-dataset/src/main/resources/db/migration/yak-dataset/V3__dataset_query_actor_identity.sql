ALTER TABLE yak_dataset_query_performance
    ADD COLUMN actor_type VARCHAR(32) NULL AFTER sql_hash,
    ADD COLUMN actor_id VARCHAR(128) NULL AFTER actor_type;

CREATE INDEX idx_yak_dataset_query_actor_time
    ON yak_dataset_query_performance(project_id, actor_type, actor_id, started_at, id);
