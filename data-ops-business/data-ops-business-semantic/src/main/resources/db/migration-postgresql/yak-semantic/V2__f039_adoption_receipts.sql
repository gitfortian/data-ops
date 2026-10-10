-- F-039 #497: append-only successful per-item receipts, same transaction as business writes.
CREATE TABLE IF NOT EXISTS yak_semantic_adoption_receipt (
    receipt_id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(100) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    semantic_id BIGINT,
    semantic_version INTEGER,
    message VARCHAR(512),
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_f039_adoption_candidate UNIQUE (project_id, task_id, candidate_id)
);
CREATE INDEX IF NOT EXISTS idx_f039_adoption_task ON yak_semantic_adoption_receipt (project_id, task_id);
