CREATE TABLE yak_modeling_logical_model_version (
  id BIGINT PRIMARY KEY,
  model_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  snapshot TEXT,
  created_by VARCHAR(64),
  create_time TIMESTAMP,
  update_time TIMESTAMP
);

CREATE INDEX idx_model_version_model_id
  ON yak_modeling_logical_model_version(model_id);
