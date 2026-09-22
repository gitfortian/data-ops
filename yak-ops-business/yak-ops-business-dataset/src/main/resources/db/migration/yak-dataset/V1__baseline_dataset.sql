-- Consolidated Dataset schema baseline.
-- Dataset-to-development/task references are logical references maintained by application services.
CREATE TABLE IF NOT EXISTS yak_dataset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    development_node_id BIGINT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(32) NOT NULL,
    current_version_id BIGINT NULL,
    draft_data_source_id VARCHAR(128) NULL COMMENT 'Draft data source selected in the editor',
    draft_sql LONGTEXT NULL COMMENT 'Draft SQL content in the editor',
    draft_schema_snapshot JSON NULL COMMENT 'Draft field contract snapshot (mirrors yak_dataset_version.schema_snapshot layout)',
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dataset_development_node (development_node_id),
    KEY idx_yak_dataset_status_update (status, update_time),
    KEY idx_yak_dataset_current_version (current_version_id),
    KEY idx_yak_dataset_update_id (update_time, id),
    KEY idx_yak_dataset_create_time (create_time),
    KEY idx_yak_dataset_project_status_update (project_id, status, update_time),
    KEY idx_yak_dataset_project_development_node (project_id, development_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dataset_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_task_asset_id BIGINT NOT NULL,
    source_task_revision_id BIGINT NOT NULL,
    source_task_revision_no INT NOT NULL,
    data_source_id VARCHAR(128) NULL,
    sql_content LONGTEXT NULL,
    schema_snapshot JSON NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dataset_version_no (dataset_id, version_no),
    KEY idx_yak_dataset_version_source (source_task_asset_id, source_task_revision_id),
    KEY idx_yak_dataset_version_datasource (data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dataset_field (
    field_id VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL,
    physical_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    nullable TINYINT(1) NOT NULL DEFAULT 1,
    description VARCHAR(1000) NULL,
    default_role VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (version_id, field_id),
    UNIQUE KEY uk_yak_dataset_field_physical_name (version_id, physical_name),
    KEY idx_yak_dataset_field_role (version_id, default_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Draft field table: per-field overrides before version commit.
CREATE TABLE IF NOT EXISTS yak_dataset_draft_field (
    field_id VARCHAR(64) NOT NULL COMMENT 'Stable field identity (preserved across save/publish cycles)',
    dataset_id BIGINT NOT NULL COMMENT 'FK to yak_dataset.id',
    physical_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    nullable TINYINT(1) NOT NULL DEFAULT 1,
    description VARCHAR(1000) NULL,
    default_role VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (dataset_id, field_id),
    UNIQUE KEY uk_yak_dataset_draft_field_physical_name (dataset_id, physical_name),
    KEY idx_yak_dataset_draft_field_role (dataset_id, default_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Persist Dataset Query Runtime diagnostics so evidence survives restarts and is shared across instances.
-- SQL text is stored only after application-side literal redaction.
CREATE TABLE IF NOT EXISTS yak_dataset_query_performance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    query_id CHAR(32) NOT NULL,
    dataset_id BIGINT NOT NULL,
    dataset_name VARCHAR(200) NULL,
    dataset_version_id BIGINT NULL,
    dataset_version_no INT NULL,
    source_type VARCHAR(32) NULL,
    data_source_id VARCHAR(128) NULL,
    sql_preview VARCHAR(4000) NULL,
    sql_hash CHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    failure_stage VARCHAR(64) NULL,
    error_type VARCHAR(200) NULL,
    error_message VARCHAR(2000) NULL,
    wait_millis BIGINT NOT NULL DEFAULT 0,
    prepare_millis BIGINT NOT NULL DEFAULT 0,
    execute_millis BIGINT NOT NULL DEFAULT 0,
    transfer_millis BIGINT NOT NULL DEFAULT 0,
    total_millis BIGINT NOT NULL DEFAULT 0,
    returned_rows INT NOT NULL DEFAULT 0,
    truncated TINYINT(1) NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dataset_query_performance_query (query_id),
    KEY idx_yak_dataset_query_performance_project_time (project_id, started_at, id),
    KEY idx_yak_dataset_query_performance_dataset_time (project_id, dataset_id, started_at, id),
    KEY idx_yak_dataset_query_performance_status_time (project_id, status, started_at, id),
    KEY idx_yak_dataset_query_performance_sql_hash (project_id, sql_hash, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
