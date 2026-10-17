-- R12-3: append-only execution attempts; do not mutate the applied V1/V2.
-- Runtime result is NOT evidence of target-row reconciliation.
CREATE TABLE yak_dev_dwd_execution_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    binding_digest CHAR(64) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    attempt_digest CHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    runtime_execution_id VARCHAR(256) NULL,
    runtime_status VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_dwd_attempt_project_key (project_id, request_key),
    KEY idx_dwd_attempt_binding (project_id, binding_id),
    KEY idx_dwd_attempt_runtime (project_id, runtime_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
