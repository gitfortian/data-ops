-- Stage 5: source-neutral published task asset catalog.
-- The catalog stores discovery metadata and a current revision pointer only;
-- immutable task content remains owned by the source domain.

CREATE TABLE IF NOT EXISTS yak_task_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    project_id BIGINT NULL,
    project_scope_id BIGINT
        GENERATED ALWAYS AS (COALESCE(project_id, 0)) STORED
        COMMENT 'Transitional project identity key for nullable Project migration',
    name VARCHAR(200) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_revision_id BIGINT NOT NULL,
    current_revision_no INT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_task_asset_project_source_ref
        (project_scope_id, source, source_ref),
    KEY idx_yak_task_asset_catalog (status, source, task_type),
    KEY idx_yak_task_asset_project (project_id, status),
    KEY idx_yak_task_asset_project_catalog (project_id, status, source, task_type, update_time),
    KEY idx_yak_task_asset_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
