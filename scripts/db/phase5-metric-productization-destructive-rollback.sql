-- Phase 5 / #124 — DESTRUCTIVE schema rollback
--
-- DO NOT run this for a normal application rollback.
-- Normal rollback: deploy the previous application version and LEAVE these additive tables intact.
-- They contain durable validation/publication governance evidence and do not block an older binary.
--
-- Run this file only when all of the following are true:
--   1. a database backup/export has been captured and verified;
--   2. no Phase 5 binary is running;
--   3. governance/audit owners approved deletion of Metric validation/publication history;
--   4. the operator intentionally accepts irreversible loss after the last backup.
--
-- Drop the mutable active pointer before the append-only ledgers/evidence.

DROP TABLE IF EXISTS yak_metric_active_publication;
DROP TABLE IF EXISTS yak_metric_publication_event;
DROP TABLE IF EXISTS yak_metric_validation_evidence;
