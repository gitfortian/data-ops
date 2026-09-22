-- Consolidated Dashboard schema baseline.
-- Dashboard owns composition/version lifecycle in application code; cross-table references stay logical.
CREATE TABLE IF NOT EXISTS yak_dashboard (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    current_version_id BIGINT NULL,
    current_version_no INT NOT NULL DEFAULT 0,
    published_version_id BIGINT NULL,
    published_version_no INT NOT NULL DEFAULT 0,
    published_time DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_dashboard_update (update_time),
    KEY idx_yak_dashboard_current_version (current_version_id),
    KEY idx_yak_dashboard_published_version (published_version_id),
    KEY idx_yak_dashboard_project_update (project_id, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    description_snapshot VARCHAR(2000) NULL,
    active_dataset_id BIGINT NULL,
    theme_json JSON NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_version_no (dashboard_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_widget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_version_id BIGINT NOT NULL,
    widget_key VARCHAR(64) NOT NULL,
    analysis_id BIGINT NULL,
    title VARCHAR(200) NULL,
    inline_analysis_json JSON NULL,
    grid_x INT NOT NULL,
    grid_y INT NOT NULL,
    grid_w INT NOT NULL,
    grid_h INT NOT NULL,
    min_w INT NULL,
    min_h INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_widget_key (dashboard_version_id, widget_key),
    KEY idx_yak_dashboard_widget_analysis (analysis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_filter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_version_id BIGINT NOT NULL,
    filter_key VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    operator VARCHAR(32) NOT NULL,
    default_value_json JSON NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_filter_key (dashboard_version_id, filter_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_filter_binding (
    dashboard_version_id BIGINT NOT NULL,
    filter_key VARCHAR(64) NOT NULL,
    widget_key VARCHAR(64) NOT NULL,
    field_id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (dashboard_version_id, filter_key, widget_key),
    KEY idx_yak_dashboard_filter_binding_widget (dashboard_version_id, widget_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dashboard_interaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dashboard_version_id BIGINT NOT NULL,
    interaction_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    source_widget_key VARCHAR(64) NOT NULL,
    source_field_id VARCHAR(64) NOT NULL,
    target_filter_key VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dashboard_interaction_key (dashboard_version_id, interaction_key),
    KEY idx_yak_dashboard_interaction_source (dashboard_version_id, source_widget_key),
    KEY idx_yak_dashboard_interaction_filter (dashboard_version_id, target_filter_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
