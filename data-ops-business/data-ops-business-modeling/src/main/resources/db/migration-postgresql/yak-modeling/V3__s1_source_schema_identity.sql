-- S1: exact source provenance, PostgreSQL vendor counterpart to MySQL V3.
-- Historical rows remain unverified (NULL source_schema).
ALTER TABLE yak_modeling_model
  ADD COLUMN source_schema VARCHAR(128);
CREATE INDEX idx_yak_modeling_model_exact_source
  ON yak_modeling_model (project_id, source_datasource_id, source_database, source_schema, source_table);
