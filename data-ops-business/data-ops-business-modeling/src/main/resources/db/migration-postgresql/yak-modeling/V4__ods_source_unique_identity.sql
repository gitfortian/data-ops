-- R10 PR-A: forbid concurrent active ODS binding of the same exact source.
-- NULL schema (legacy/default) and named schemas are distinct coordinates.
-- We distinguish NULL from empty instead of using a collision-prone sentinel.
-- This migration intentionally refuses existing duplicate active bindings.
CREATE UNIQUE INDEX uk_yak_modeling_ods_source_identity
  ON yak_modeling_model (
    project_id, source_datasource_id, source_database,
    (source_schema IS NULL), COALESCE(source_schema, ''), source_table
  )
  WHERE deleted = 0
    AND UPPER(layer_code) = 'ODS'
    AND source_datasource_id IS NOT NULL
    AND source_database IS NOT NULL
    AND source_table IS NOT NULL;
