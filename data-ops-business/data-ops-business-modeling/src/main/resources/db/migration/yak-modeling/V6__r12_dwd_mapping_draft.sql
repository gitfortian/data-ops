-- R12: Modeling-owned author mapping root and immutable revisions.
-- Depends on ODS migration V5 (PR #575) for ordered rollout.
CREATE TABLE yak_modeling_mapping_set (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL,
 physical_model_id BIGINT NOT NULL,
 logical_model_id BIGINT NOT NULL,
 logical_version_no INT NOT NULL,
 logical_snapshot_sha CHAR(64) NOT NULL,
 physical_structure_sha CHAR(64) NOT NULL,
 draft_revision BIGINT NOT NULL DEFAULT 1,
 mapping_json LONGTEXT NOT NULL,
 updated_by VARCHAR(128) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_r12_mapping_physical (project_id,physical_model_id),
 KEY idx_r12_mapping_logical (project_id,logical_model_id,logical_version_no)
);
CREATE TABLE yak_modeling_mapping_revision (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL,
 mapping_set_id BIGINT NOT NULL,
 revision_no INT NOT NULL,
 draft_revision BIGINT NOT NULL,
 logical_snapshot_sha CHAR(64) NOT NULL,
 physical_structure_sha CHAR(64) NOT NULL,
 content_sha CHAR(64) NOT NULL,
 frozen_json LONGTEXT NOT NULL,
 frozen_by VARCHAR(128) NOT NULL,
 frozen_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_r12_mapping_revision (project_id,mapping_set_id,revision_no)
);
