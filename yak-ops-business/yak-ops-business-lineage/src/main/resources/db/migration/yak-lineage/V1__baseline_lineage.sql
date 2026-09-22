-- Consolidated Lineage schema baseline.
-- Parent/edge integrity is enforced by lineage services; no physical FK constraints are created.
CREATE TABLE IF NOT EXISTS yak_metadata_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NULL COMMENT 'Yak Security Project ID from source truth',
    project_scope_id BIGINT
        GENERATED ALWAYS AS (COALESCE(project_id, 0)) STORED
        COMMENT 'Transitional project identity key for nullable Project migration',
    asset_key VARCHAR(512) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    source_type VARCHAR(64) NOT NULL DEFAULT '',
    source_id VARCHAR(200) NOT NULL DEFAULT '',
    parent_asset_id BIGINT NULL,
    data_source_id VARCHAR(64) NULL,
    database_name VARCHAR(256) NULL,
    schema_name VARCHAR(256) NULL,
    table_name VARCHAR(256) NULL,
    column_name VARCHAR(256) NULL,
    properties JSON NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_metadata_asset_project_key (project_scope_id, asset_key),
    KEY idx_yak_metadata_asset_type (asset_type),
    KEY idx_yak_metadata_asset_source (source_type, source_id),
    KEY idx_yak_metadata_asset_parent (parent_asset_id),
    KEY idx_yak_metadata_asset_datasource (data_source_id),
    KEY idx_yak_metadata_asset_project_type (project_id, asset_type, update_time),
    KEY idx_yak_metadata_asset_project_source (project_id, source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_metadata_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NULL COMMENT 'Project ID shared by source/target assets',
    source_asset_id BIGINT NOT NULL,
    target_asset_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(64) NOT NULL DEFAULT '',
    source_id VARCHAR(200) NOT NULL DEFAULT '',
    expression TEXT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    version VARCHAR(128) NOT NULL DEFAULT '',
    observed_at DATETIME(6) NOT NULL,
    properties JSON NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_metadata_relation_identity
        (source_asset_id, target_asset_id, relation_type, source_type, source_id, version),
    KEY idx_yak_metadata_relation_source (source_asset_id, relation_type),
    KEY idx_yak_metadata_relation_target (target_asset_id, relation_type),
    KEY idx_yak_metadata_relation_evidence (source_type, source_id),
    KEY idx_yak_metadata_relation_project_source (project_id, source_asset_id, relation_type),
    KEY idx_yak_metadata_relation_project_target (project_id, target_asset_id, relation_type),
    KEY idx_yak_metadata_relation_project_evidence (project_id, source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
