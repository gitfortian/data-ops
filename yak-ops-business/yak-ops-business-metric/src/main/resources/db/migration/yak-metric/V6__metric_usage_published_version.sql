ALTER TABLE yak_metric_usage
    ADD COLUMN metric_version INT NULL COMMENT '引用的 Published MetricVersion；NULL 表示历史引用版本未知'
    AFTER metric_id;

-- Earlier Dataset bindings used a development node id. Normalize them to the owning Dataset id
-- so Phase 4 evidence lookup uses the canonical ProductKey identity.
UPDATE yak_metric_usage usage_row
JOIN yak_dataset dataset_row
  ON dataset_row.project_id = usage_row.project_id
 AND dataset_row.development_node_id = usage_row.usage_id
SET usage_row.usage_id = dataset_row.id
WHERE usage_row.usage_type = 'DATASET'
  AND usage_row.metric_version IS NULL;
