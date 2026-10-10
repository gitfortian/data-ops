-- F-039 #497: durable per-item receipt. Receipt and formal definition share a transaction.
-- Never modify the consolidated V1 history.
CREATE TABLE IF NOT EXISTS yak_semantic_adoption_receipt (
    receipt_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(100) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    semantic_id BIGINT NULL,
    semantic_version INT NULL,
    message VARCHAR(512) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (receipt_id),
    UNIQUE KEY uk_f039_adoption_candidate (project_id, task_id, candidate_id),
    KEY idx_f039_adoption_task (project_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
