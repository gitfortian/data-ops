CREATE TABLE IF NOT EXISTS yak_ops_consumption_subscription (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Security Project Space ID',
    product_key VARCHAR(255) NOT NULL COMMENT 'Canonical Phase 4 ProductKey',
    consumer_type VARCHAR(32) NOT NULL COMMENT 'USER/TEAM/DASHBOARD/DATA_SERVICE/JOB',
    source_domain VARCHAR(64) NOT NULL COMMENT 'Consumer owning domain',
    source_identity VARCHAR(255) NOT NULL COMMENT 'Stable consumer identity in owning domain',
    display_hint VARCHAR(255) DEFAULT NULL COMMENT 'Presentation-only consumer label',
    consumption_mode VARCHAR(32) NOT NULL COMMENT 'QUERY/PREVIEW/EXPORT/API_INVOKE/DOWNSTREAM',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/REVOKED',
    created_by VARCHAR(128) NOT NULL COMMENT 'Subscription creator principal',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(128) NOT NULL COMMENT 'Last mutation principal',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_consumption_subscription_identity
        (project_id, product_key, consumer_type, source_domain, source_identity, consumption_mode),
    KEY idx_yak_consumption_subscription_product
        (project_id, product_key, status, updated_at, id),
    KEY idx_yak_consumption_subscription_consumer
        (project_id, consumer_type, source_domain, source_identity, status, updated_at, id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops governed data-product declared consumer dependencies';
