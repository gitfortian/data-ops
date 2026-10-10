-- Logical models predate Project Spaces. Legacy rows remain unowned and must not
-- be exposed by the new scoped draft API until explicitly reviewed/migrated.
ALTER TABLE yak_modeling_logical_model ADD COLUMN project_id BIGINT NULL;
ALTER TABLE yak_modeling_logical_model ADD COLUMN process_id BIGINT NULL;
ALTER TABLE yak_modeling_logical_attribute ADD COLUMN std_field_id BIGINT NULL;

CREATE UNIQUE INDEX uq_logical_model_project_code
  ON yak_modeling_logical_model(project_id, code);
CREATE INDEX idx_logical_model_project_process
  ON yak_modeling_logical_model(project_id, process_id);
CREATE INDEX idx_logical_attribute_std_field
  ON yak_modeling_logical_attribute(std_field_id);
