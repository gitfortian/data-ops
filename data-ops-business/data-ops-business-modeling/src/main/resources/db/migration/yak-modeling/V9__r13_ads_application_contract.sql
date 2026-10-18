-- R13-C1: Modeling-owned ADS application output contract; no runtime or consumer authorization.
CREATE TABLE yak_modeling_ads_application_design (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  contract_sha256 CHAR(64) NOT NULL,
  contract_json LONGTEXT NOT NULL,
  updated_by VARCHAR(128) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_r13_ads_application_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yak_modeling_ads_application_revision (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  contract_sha256 CHAR(64) NOT NULL,
  contract_json LONGTEXT NOT NULL,
  updated_by VARCHAR(128) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_r13_ads_application_revision (project_id, model_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
