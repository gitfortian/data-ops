-- R10 PR-A: the current project's active ODS models may bind a real source only once.
-- Existing unbound DRAFTs and deleted rows have NULL keys and are unaffected.
-- Deliberately fails when legacy active duplicate bindings exist: clean up/verify
-- duplicates before rollout rather than silently choosing a model to overwrite.
-- JSON_ARRAY distinguishes a missing schema from an explicitly named schema,
-- and cannot collide by joining user-controlled table or schema strings.
ALTER TABLE yak_modeling_model
  ADD COLUMN ods_source_identity_hash CHAR(64)
    GENERATED ALWAYS AS (
      CASE
        WHEN deleted = 0
          AND UPPER(layer_code) = 'ODS'
          AND source_datasource_id IS NOT NULL
          AND source_database IS NOT NULL
          AND source_table IS NOT NULL
        THEN SHA2(CAST(JSON_ARRAY(source_datasource_id, source_database,
          source_schema, source_table) AS CHAR), 256)
        ELSE NULL
      END
    ) STORED;
CREATE UNIQUE INDEX uk_yak_modeling_ods_source_identity
  ON yak_modeling_model (project_id, ods_source_identity_hash);
