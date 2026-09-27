-- Phase 5 / #124 — Metric Productization additive schema upgrade
-- Target: MySQL 8.x (compose.yaml uses MySQL 8.x).
--
-- This repository does not currently carry an application migration framework. Keep this
-- upgrade explicit and additive: it creates only the Phase 5 evidence/publication tables.
-- Existing Metric/Semantic tables are not altered here.
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
  result VARCHAR(16) NOT NULL,
  issues_json LONGTEXT NOT NULL,
  provider VARCHAR(128) NOT NULL,
  snapshot_digest CHAR(64) NOT NULL,
  checked_by VARCHAR(128) NOT NULL,
  checked_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_yak_metric_validation_subject
      (project_id, metric_id, metric_version, checked_at, id),
  KEY idx_yak_metric_validation_ready
      (project_id, metric_id, metric_version, result, checked_at, id),
  KEY idx_yak_metric_validation_version_id
      (project_id, metric_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only MetricVersion definition validation evidence';

CREATE TABLE IF NOT EXISTS yak_metric_publication_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  metric_id BIGINT NOT NULL,
  metric_version_id BIGINT NOT NULL,
  metric_version INT NOT NULL,
  snapshot_digest CHAR(64) NOT NULL,
  event_type VARCHAR(16) NOT NULL,
  subject_publication_id BIGINT NULL,
  readiness_json LONGTEXT NULL,
  acted_by VARCHAR(128) NOT NULL,
  acted_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_yak_metric_publication_metric
      (project_id, metric_id, acted_at, id),
  KEY idx_yak_metric_publication_version
      (project_id, metric_id, metric_version, id),
  KEY idx_yak_metric_publication_version_id
      (project_id, metric_version_id),
  KEY idx_yak_metric_publication_subject
      (project_id, subject_publication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only Published Metric Contract lifecycle ledger';

CREATE TABLE IF NOT EXISTS yak_metric_active_publication (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  metric_id BIGINT NOT NULL,
  publication_event_id BIGINT NOT NULL,
  metric_version_id BIGINT NOT NULL,
  metric_version INT NOT NULL,
  snapshot_digest CHAR(64) NOT NULL,
  published_by VARCHAR(128) NOT NULL,
  published_at DATETIME(6) NOT NULL,
  update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_yak_metric_active_project_metric (project_id, metric_id),
  KEY idx_yak_metric_active_event (project_id, publication_event_id),
  KEY idx_yak_metric_active_version (project_id, metric_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Current active pointer to immutable Published Metric Contract';
