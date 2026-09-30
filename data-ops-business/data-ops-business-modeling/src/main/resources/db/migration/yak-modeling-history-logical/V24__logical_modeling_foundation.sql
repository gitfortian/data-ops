CREATE TABLE IF NOT EXISTS yak_modeling_logical_model (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(1024),
    domain_id BIGINT,
    owner VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_model_domain_id ON yak_modeling_logical_model(domain_id);

CREATE TABLE IF NOT EXISTS yak_modeling_logical_entity (
    id BIGINT PRIMARY KEY,
    logical_model_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    business_name VARCHAR(256),
    description VARCHAR(1024),
    owner VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_entity_model_id ON yak_modeling_logical_entity(logical_model_id);

CREATE TABLE IF NOT EXISTS yak_modeling_logical_attribute (
    id BIGINT PRIMARY KEY,
    entity_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    logical_type VARCHAR(128),
    description VARCHAR(1024),
    primary_flag BOOLEAN DEFAULT FALSE,
    nullable BOOLEAN DEFAULT TRUE,
    sort INT DEFAULT 0
);

CREATE INDEX idx_logical_attribute_entity_id ON yak_modeling_logical_attribute(entity_id);

CREATE TABLE IF NOT EXISTS yak_modeling_entity_relation (
    id BIGINT PRIMARY KEY,
    source_entity_id BIGINT NOT NULL,
    target_entity_id BIGINT NOT NULL,
    relation_type VARCHAR(64),
    cardinality VARCHAR(64),
    description VARCHAR(1024)
);

CREATE INDEX idx_entity_relation_source ON yak_modeling_entity_relation(source_entity_id);
CREATE INDEX idx_entity_relation_target ON yak_modeling_entity_relation(target_entity_id);
