-- R13 UX-4: immutable saved revisions for DWS grain contracts.
-- Existing current design is backfilled as the first recoverable history entry.
CREATE TABLE yak_modeling_dws_grain_revision (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  structure_fingerprint CHAR(64) NOT NULL,
  design_sha256 CHAR(64) NOT NULL,
  design_json LONGTEXT NOT NULL,
  updated_by VARCHAR(128) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_r13_dws_revision (project_id, model_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO yak_modeling_dws_grain_revision
  (project_id, model_id, revision, structure_fingerprint, design_sha256,
   design_json, updated_by, updated_at)
SELECT project_id, model_id, revision, structure_fingerprint, design_sha256,
       design_json, updated_by, updated_at
FROM yak_modeling_dws_grain_design;
