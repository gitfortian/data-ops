-- Yak Ops Data Development first-release baseline.
--
-- This module is still pre-release. Development-time V1/V2/V3/V8...V15 migrations are
-- intentionally squashed into one final schema definition. After the first formal release,
-- this V1 becomes immutable and future changes must use V2, V3, ...

CREATE TABLE IF NOT EXISTS yak_dev_directory (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_directory_project_sibling (project_id, parent_id, name),
    KEY idx_yak_dev_directory_parent (parent_id),
    KEY idx_yak_dev_directory_project_parent (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_node (
    id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    directory_id BIGINT NOT NULL DEFAULT 0,
    configured TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    updated_by VARCHAR(128) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_dev_node_directory (directory_id, name),
    KEY idx_yak_dev_node_type (type, update_time),
    KEY idx_yak_dev_node_project (project_id, update_time),
    KEY idx_yak_dev_node_project_directory (project_id, directory_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_task_draft (
    node_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    schema_version INT NOT NULL,
    content LONGTEXT NOT NULL,
    config_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (node_id),
    KEY idx_yak_dev_task_draft_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_task_revision (
    id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    schema_version INT NOT NULL,
    content LONGTEXT NOT NULL,
    config_json LONGTEXT NOT NULL,
    checksum CHAR(64) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_task_revision_node_no (node_id, revision_no),
    KEY idx_yak_dev_task_revision_node_time (node_id, create_time),
    KEY idx_yak_dev_task_revision_checksum (node_id, checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_editor_setting (
    user_key VARCHAR(128) NOT NULL,
    setting_json LONGTEXT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (user_key),
    KEY idx_yak_dev_editor_setting_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_task_execution (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    task_name VARCHAR(200) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    runtime_execution_id VARCHAR(128) NULL,
    retry_of_execution_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    operator_name VARCHAR(128) NULL,
    duration_ms BIGINT NULL,
    error_message VARCHAR(1000) NULL,
    content LONGTEXT NOT NULL,
    config_json LONGTEXT NOT NULL,
    output_json LONGTEXT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_task_execution_runtime (runtime_execution_id),
    KEY idx_yak_dev_task_execution_node_time (node_id, start_time),
    KEY idx_yak_dev_task_execution_status_time (status, start_time),
    KEY idx_yak_dev_task_execution_type_time (task_type, start_time),
    KEY idx_yak_dev_task_execution_operator_time (operator_name, start_time),
    KEY idx_yak_dev_task_execution_project_time (project_id, start_time),
    KEY idx_yak_dev_task_execution_project_status (project_id, status, start_time),
    KEY idx_yak_dev_task_execution_retry (retry_of_execution_id),
    KEY idx_yak_dev_task_execution_status_update (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_data_service_draft (
    node_id BIGINT NOT NULL,
    definition_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (node_id),
    KEY idx_yak_dev_data_service_draft_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_data_service_revision (
    id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    definition_json LONGTEXT NOT NULL,
    checksum CHAR(64) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_dev_data_service_revision_node_no (node_id, revision_no),
    KEY idx_yak_dev_data_service_revision_node_time (node_id, create_time),
    KEY idx_yak_dev_data_service_revision_checksum (node_id, checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_dev_lineage_outbox (
    task_id CHAR(36) NOT NULL,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_time DATETIME(6) NOT NULL,
    last_error VARCHAR(2000) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_yak_dev_lineage_outbox_revision (node_id, revision_id),
    KEY idx_yak_dev_lineage_outbox_due (status, next_attempt_time),
    KEY idx_yak_dev_lineage_outbox_project_due (project_id, status, next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_system_env_var (
    var_key VARCHAR(128) NOT NULL,
    var_value TEXT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (var_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
