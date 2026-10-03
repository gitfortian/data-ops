CREATE TABLE IF NOT EXISTS yak_job_execution_result (
  execution_id VARCHAR(128) NOT NULL,
  project_id BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  idempotency_hash CHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  error_message TEXT NULL,
  output_json LONGTEXT NOT NULL,
  completed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (execution_id),
  UNIQUE KEY uk_job_result_project_type_key (project_id, task_type, idempotency_hash),
  KEY idx_job_result_project_time (project_id, completed_at)
);
