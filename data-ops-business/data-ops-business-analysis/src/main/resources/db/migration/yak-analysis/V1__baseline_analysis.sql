-- Consolidated Analysis schema baseline.
-- Cross-domain references are logical only; physical FK constraints are intentionally omitted.
CREATE TABLE IF NOT EXISTS yak_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    dataset_id BIGINT NOT NULL,
    chart_type VARCHAR(32) NOT NULL,
    query_spec_json JSON NOT NULL,
    visual_config_json JSON NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_analysis_dataset_update (dataset_id, update_time),
    KEY idx_yak_analysis_chart_type (chart_type),
    KEY idx_yak_analysis_project_update (project_id, update_time, id),
    KEY idx_yak_analysis_project_dataset (project_id, dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
