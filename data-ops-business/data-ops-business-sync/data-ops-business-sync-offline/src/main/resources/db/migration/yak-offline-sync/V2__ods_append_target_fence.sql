-- R11 PR-B: permanent first-write fence. No automatic release on failure/UNKNOWN.
-- UNIQUE constraint is the cross-worker, cross-Job atomic admission boundary.
CREATE TABLE yak_offline_ods_append_fence (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  target_datasource_id BIGINT NOT NULL,
  target_table_key VARCHAR(128) NOT NULL,
  job_definition_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  target_observation_id BIGINT NOT NULL,
  scope_sha256 CHAR(64) NOT NULL,
  create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_offline_ods_append_target (project_id, target_datasource_id, target_table_key),
  KEY idx_offline_ods_append_job (project_id, job_definition_id)
);
