CREATE TABLE yak_modeling_impact_record (
    id BIGINT PRIMARY KEY,
    source_object_type VARCHAR(64) NOT NULL,
    source_object_id BIGINT NOT NULL,
    target_object_type VARCHAR(64) NOT NULL,
    target_object_id BIGINT NOT NULL,
    impact_type VARCHAR(32) NOT NULL,
    description VARCHAR(512),
    create_time DATETIME NOT NULL
);
