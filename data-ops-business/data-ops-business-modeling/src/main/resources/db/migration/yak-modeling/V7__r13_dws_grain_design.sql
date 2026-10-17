-- R13-B: Modeling-owned DWS grain semantics, independent of physical DDL attributes.
-- Existing V1-V6 histories remain byte-for-byte unchanged.
CREATE TABLE yak_modeling_dws_grain_design (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  structure_fingerprint CHAR(64) NOT NULL,
  design_sha256 CHAR(64) NOT NULL,
  design_json LONGTEXT NOT NULL,
  revision BIGINT NOT NULL,
  updated_by VARCHAR(128) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_r13_dws_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
