-- Phase 5 / #124 — Metric Productization additive schema upgrade
-- Target: MySQL 8.x (compose.yaml uses MySQL 8.x).
--
-- This script is the manual-install equivalent of the yak-metric Flyway migrations V4-V7.
-- Run it only for installations that do not apply the module's Flyway migrations; do not run
-- both paths against the same database. Keep the final schema identical to those migrations.
--
-- Safe application rollback policy:
--   Roll back application binaries WITHOUT dropping these tables. Validation evidence and the
--   publication ledger are durable governance/audit history and are backward-compatible orphans
--   to an older binary.
--
-- Run the companion phase5-metric-productization-verify.sql after this script.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS yak_metric_validation_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  metric_id BIGINT NOT NULL,
  metric_version_id BIGINT NOT NULL,
  metric_version INT NOT NULL,
  result VARCHAR(16) NOT NULL COMMENT 'PASSED/FAILED/NOT_APPLICABLE',
  provider_state VARCHAR(16) NOT NULL DEFAULT 'READY'
      COMMENT 'READY/UNAVAILABLE/FORBIDDEN',
  issues_json JSON NOT NULL,
  provider VARCHAR(128) NOT NULL,
  snapshot_digest VARCHAR(64) NOT NULL,
  checked_by VARCHAR(64) NOT NULL,
  checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_metric_validation_subject (project_id, metric_id, metric_version, checked_at),
  KEY idx_metric_validation_ready
      (project_id, metric_id, metric_version, result, checked_at),
  KEY idx_metric_validation_version_id
      (project_id, metric_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only MetricVersion definition validation evidence';

CREATE TABLE IF NOT EXISTS yak_metric_publication_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  metric_id BIGINT NOT NULL,
  metric_version_id BIGINT NOT NULL,
  metric_version INT NOT NULL,
  snapshot_digest VARCHAR(64) NOT NULL,
  event_type VARCHAR(16) NOT NULL,
  subject_publication_id BIGINT NULL,
  readiness_json JSON NULL,
  acted_by VARCHAR(64) NOT NULL,
  acted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_metric_publication_metric (project_id, metric_id, acted_at),
  KEY idx_metric_publication_version (project_id, metric_version_id),
  KEY idx_metric_publication_subject
      (project_id, subject_publication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only Published Metric Contract lifecycle ledger';

CREATE TABLE IF NOT EXISTS yak_metric_active_publication (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  metric_id BIGINT NOT NULL,
  publication_event_id BIGINT NOT NULL,
  metric_version_id BIGINT NOT NULL,
  metric_version INT NOT NULL,
  snapshot_digest VARCHAR(64) NOT NULL,
  published_by VARCHAR(64) NOT NULL,
  published_at DATETIME(6) NOT NULL,
  update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_metric_active_publication (project_id, metric_id),
  KEY idx_metric_active_publication_event (project_id, publication_event_id),
  KEY idx_metric_active_publication_version (project_id, metric_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Current active pointer to immutable Published Metric Contract';

ALTER TABLE yak_metric_usage
  ADD COLUMN metric_version INT NULL
      COMMENT '引用的 Published MetricVersion；NULL 表示历史引用版本未知'
      AFTER metric_id;

-- Normalize legacy Dataset references to the canonical Dataset identity. Rows that cannot be
-- matched stay legacy/unknown; do not invent a Dataset or MetricVersion identity.
UPDATE yak_metric_usage usage_row
JOIN yak_dataset dataset_row
  ON dataset_row.project_id = usage_row.project_id
 AND dataset_row.development_node_id = usage_row.usage_id
SET usage_row.usage_id = dataset_row.id
WHERE usage_row.usage_type = 'DATASET'
  AND usage_row.metric_version IS NULL;

-- Upgrade legacy validation outcomes to the accepted result/provider-state vocabulary.
UPDATE yak_metric_validation_evidence
SET result = CASE result
      WHEN 'READY' THEN 'PASSED'
      WHEN 'BLOCKED' THEN 'FAILED'
      ELSE 'NOT_APPLICABLE'
    END,
    provider_state = 'READY';
