-- Coordination rows serialize release publication for one source inside one Project.
CREATE TABLE IF NOT EXISTS yak_dataset_source_publication_lock (
    project_id BIGINT NOT NULL,
    source_task_asset_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, source_task_asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
