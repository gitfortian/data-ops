CREATE TABLE yak_modeling_logical_entity_mapping (
    id BIGINT PRIMARY KEY,
    logical_entity_id BIGINT NOT NULL,
    physical_table_id BIGINT NOT NULL,
    mapping_type VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_entity_mapping_entity
    ON yak_modeling_logical_entity_mapping(logical_entity_id);

CREATE TABLE yak_modeling_logical_attribute_mapping (
    id BIGINT PRIMARY KEY,
    logical_attribute_id BIGINT NOT NULL,
    physical_column_id BIGINT NOT NULL,
    mapping_expression VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_attribute_mapping_attribute
    ON yak_modeling_logical_attribute_mapping(logical_attribute_id);
