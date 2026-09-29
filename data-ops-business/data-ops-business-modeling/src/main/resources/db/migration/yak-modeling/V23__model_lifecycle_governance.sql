CREATE TABLE yak_modeling_lifecycle_record (
  id BIGINT PRIMARY KEY,
  object_type VARCHAR(64) NOT NULL,
  object_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  operator VARCHAR(64),
  reason VARCHAR(512),
  create_time TIMESTAMP
);

CREATE INDEX idx_lifecycle_object
  ON yak_modeling_lifecycle_record(object_type, object_id);
