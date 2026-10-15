-- R11: immutable observation of target Catalog for one exact published ODS version.
-- NOT a deployment execution receipt, synchronization status or user attestation.
CREATE TABLE yak_modeling_ods_target_observation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  model_version_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  target_datasource_id BIGINT NOT NULL,
  target_database VARCHAR(128) NOT NULL,
  target_schema VARCHAR(128) NULL,
  target_table VARCHAR(128) NOT NULL,
  catalog_sha256 CHAR(64) NOT NULL,
  frozen_checksum VARCHAR(64) NOT NULL,
  observed_column_count INT NOT NULL,
  observed_by VARCHAR(128) NOT NULL,
  observed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_ods_observation_model (project_id, model_id, id),
  KEY idx_ods_observation_target (project_id, target_datasource_id, id)
);
