-- Consolidated Realtime Sync schema baseline for the first formal release.
-- Compute Environment and Runtime Lease are GLOBAL; Definition is PROJECT_ROOT and Deployment is PROJECT_RUNTIME.
CREATE TABLE IF NOT EXISTS yak_compute_environment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    engine_type VARCHAR(32) NOT NULL DEFAULT 'FLINK_CDC',
    deployment_mode VARCHAR(32) NOT NULL DEFAULT 'REMOTE',
    submitter_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    config_json LONGTEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    last_check_status VARCHAR(16) NULL,
    last_check_message VARCHAR(500) NULL,
    last_check_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_environment_name (name),
    KEY idx_compute_environment_default (is_default, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_realtime_job_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    runtime_environment_id BIGINT NOT NULL,
    spec_json LONGTEXT NULL,
    release_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    desired_state VARCHAR(16) NOT NULL DEFAULT 'STOPPED',
    observed_state VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
    definition_version INT NOT NULL DEFAULT 1,
    published_version INT NULL,
    published_definition_version_id BIGINT NULL,
    config_digest CHAR(64) NULL,
    last_error TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_realtime_project_name (project_id, job_name),
    KEY idx_realtime_project_state (project_id, release_state, update_time),
    KEY idx_realtime_states (desired_state, observed_state),
    KEY idx_realtime_definition_environment (runtime_environment_id),
    KEY idx_realtime_published_definition_version (published_definition_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_realtime_definition_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_draft_revision INT NOT NULL,
    runtime_environment_id BIGINT NOT NULL,
    definition_json LONGTEXT NOT NULL,
    definition_digest CHAR(64) NULL,
    source_config_digest CHAR(64) NOT NULL,
    domain_mapping_state VARCHAR(32) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_realtime_definition_version_no (task_id, version_no),
    UNIQUE KEY uk_realtime_definition_source_digest (task_id, source_config_digest),
    KEY idx_realtime_definition_version_task_revision (task_id, source_draft_revision),
    KEY idx_realtime_definition_version_environment (runtime_environment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_realtime_job_deployment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version_id BIGINT NULL,
    definition_version INT NOT NULL,
    runtime_environment_id BIGINT NOT NULL,
    runtime_environment_version INT NOT NULL,
    runtime_environment_snapshot_json LONGTEXT NOT NULL,
    spec_snapshot_json LONGTEXT NOT NULL,
    spec_summary VARCHAR(1000) NULL,
    config_digest CHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    engine_type VARCHAR(32) NOT NULL DEFAULT 'FLINK_CDC',
    desired_state VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    observed_state VARCHAR(32) NOT NULL DEFAULT 'STARTING',
    runtime_job_name VARCHAR(128) NULL,
    runtime_identity_state VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    gateway_job_id VARCHAR(128) NULL,
    runtime_version VARCHAR(64) NULL,
    runtime_revision VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    result_uncertain TINYINT(1) NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    replacement_command_type VARCHAR(32) NULL,
    replacement_target_definition_version_id BIGINT NULL,
    replacement_idempotency_key VARCHAR(128) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_realtime_project_idempotency (project_id, idempotency_key),
    UNIQUE KEY uk_realtime_runtime_job_name (runtime_job_name),
    KEY idx_realtime_deployment_project_state (project_id, observed_state, update_time),
    KEY idx_realtime_deployment_project_definition (project_id, definition_id, id),
    KEY idx_realtime_deployment_definition (definition_id, id),
    KEY idx_realtime_deployment_definition_version (definition_version_id, id),
    KEY idx_realtime_execution_state (definition_id, observed_state, id),
    KEY idx_realtime_deployment_environment (runtime_environment_id),
    KEY idx_realtime_replacement_pending (definition_id, replacement_idempotency_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_realtime_job_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    definition_id BIGINT NOT NULL,
    deployment_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_state VARCHAR(32) NULL,
    to_state VARCHAR(32) NULL,
    message TEXT NULL,
    payload_json LONGTEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_realtime_event_definition (definition_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_realtime_runtime_lease (
    id TINYINT NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until DATETIME(3) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO yak_realtime_runtime_lease (id, lease_until)
VALUES (1, '1970-01-01 00:00:00.000')
ON DUPLICATE KEY UPDATE id = VALUES(id);
