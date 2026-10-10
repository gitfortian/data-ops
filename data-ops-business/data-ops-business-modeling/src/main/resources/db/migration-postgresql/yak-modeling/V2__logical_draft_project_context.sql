-- Existing logical models have no trusted project membership. Do not invent it.
-- NULL project_id legacy rows are intentionally invisible to the new scoped API.
ALTER TABLE yak_modeling_logical_model ADD COLUMN project_id bigint;
ALTER TABLE yak_modeling_logical_model ADD COLUMN process_id bigint;
ALTER TABLE yak_modeling_logical_attribute ADD COLUMN std_field_id bigint;

CREATE UNIQUE INDEX uq_logical_model_project_code
  ON yak_modeling_logical_model(project_id, code);
CREATE INDEX idx_logical_model_project_process
  ON yak_modeling_logical_model(project_id, process_id);
CREATE INDEX idx_logical_attribute_std_field
  ON yak_modeling_logical_attribute(std_field_id);
