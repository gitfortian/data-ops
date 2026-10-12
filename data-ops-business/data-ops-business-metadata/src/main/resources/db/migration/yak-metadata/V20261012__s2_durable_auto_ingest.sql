-- S2: durable, project-scoped committed table evidence; never full-project GONE evidence.
CREATE TABLE yak_md_auto_ingest_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  entity_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL,
  lease_until DATETIME(6) NULL,
  claim_token VARCHAR(64) NULL,
  error_message VARCHAR(1024) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_md_auto_ingest_fact UNIQUE (project_id, run_id, entity_id),
  INDEX idx_md_auto_ingest_ready (status, next_attempt_at, id),
  INDEX idx_md_auto_ingest_source (project_id, entity_id, id)
);
