CREATE TABLE IF NOT EXISTS yak_ops_consumption_usage_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    product_key VARCHAR(255) NOT NULL,
    source_version_identity VARCHAR(255) NOT NULL,
    source_display_version VARCHAR(255) NULL,
    consumer_type VARCHAR(64) NOT NULL,
    source_domain VARCHAR(128) NOT NULL,
    source_identity VARCHAR(255) NOT NULL,
    display_hint VARCHAR(255) NULL,
    observed_at DATETIME(6) NOT NULL,
    consumption_mode VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    provider VARCHAR(128) NOT NULL,
    provider_evidence_ref VARCHAR(255) NOT NULL,
    deduplication_id VARCHAR(255) NOT NULL,
    normalized_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumption_usage_dedup (project_id, deduplication_id),
    KEY idx_consumption_usage_product (project_id, product_key, observed_at),
    KEY idx_consumption_usage_consumer (
        project_id, consumer_type, source_domain, source_identity, observed_at
    )
);
