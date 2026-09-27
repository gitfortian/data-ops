-- Phase 5 / #124 — post-upgrade schema verification
-- Expected result: every check returns expected_count = actual_count.
-- Any mismatch blocks Phase 5 rollout; do not treat a missing index/column as a warning.

SELECT 'phase5_tables' AS check_name, 3 AS expected_count, COUNT(*) AS actual_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'yak_metric_validation_evidence',
    'yak_metric_publication_event',
    'yak_metric_active_publication'
  );

SELECT 'validation_columns' AS check_name, 11 AS expected_count, COUNT(*) AS actual_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'yak_metric_validation_evidence'
  AND column_name IN (
    'id', 'project_id', 'metric_id', 'metric_version_id', 'metric_version',
    'result', 'issues_json', 'provider', 'snapshot_digest', 'checked_by', 'checked_at'
  );

SELECT 'publication_event_columns' AS check_name, 11 AS expected_count, COUNT(*) AS actual_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'yak_metric_publication_event'
  AND column_name IN (
    'id', 'project_id', 'metric_id', 'metric_version_id', 'metric_version',
    'snapshot_digest', 'event_type', 'subject_publication_id', 'readiness_json',
    'acted_by', 'acted_at'
  );

SELECT 'active_publication_columns' AS check_name, 10 AS expected_count, COUNT(*) AS actual_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'yak_metric_active_publication'
  AND column_name IN (
    'id', 'project_id', 'metric_id', 'publication_event_id', 'metric_version_id',
    'metric_version', 'snapshot_digest', 'published_by', 'published_at', 'update_time'
  );

-- This unique key is a runtime correctness requirement: MetricActivePublicationMapper.upsert()
-- uses ON DUPLICATE KEY UPDATE and relies on one active pointer per Project + Metric.
SELECT 'active_publication_unique_project_metric' AS check_name, 1 AS expected_count, COUNT(*) AS actual_count
FROM (
  SELECT index_name
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'yak_metric_active_publication'
    AND non_unique = 0
  GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'project_id,metric_id'
) AS required_unique;

SELECT 'validation_ready_index' AS check_name, 1 AS expected_count, COUNT(*) AS actual_count
FROM (
  SELECT index_name
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'yak_metric_validation_evidence'
  GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      = 'project_id,metric_id,metric_version,result,checked_at,id'
) AS required_index;

SELECT 'publication_metric_history_index' AS check_name, 1 AS expected_count, COUNT(*) AS actual_count
FROM (
  SELECT index_name
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'yak_metric_publication_event'
  GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      = 'project_id,metric_id,acted_at,id'
) AS required_index;
