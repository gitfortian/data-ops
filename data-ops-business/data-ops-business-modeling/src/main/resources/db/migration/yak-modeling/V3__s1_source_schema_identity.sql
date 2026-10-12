-- S1 project-scoped exact source provenance. Old rows remain NULL (unverified for schema-qualified sources).
ALTER TABLE yak_modeling_model
  ADD COLUMN source_schema VARCHAR(128) NULL COMMENT '来源 schema，空为默认/旧来源' AFTER source_database;
CREATE INDEX idx_yak_modeling_model_exact_source
  ON yak_modeling_model (project_id, source_datasource_id, source_database, source_schema, source_table);

ALTER TABLE yak_modeling_column_mapping
  ADD COLUMN source_schema VARCHAR(128) NULL COMMENT '逐列来源 Schema; NULL 表示默认/历史不明' AFTER source_database;
