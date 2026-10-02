-- 本文件由 scripts/db/consolidate-flyway-migrations.py 生成,请勿手改;要改结构请改脚本后重新生成。
-- 合并了 3 个版本化迁移(SQL 原文按版本号升序,未做逻辑改写)。
-- 被合并的文件:
--   V1__ [.] V1__subscription_truth.sql
--   V2__ [.] V2__usage_evidence_truth.sql
--   V3__ [.] V3__subscription_lifecycle.sql

-- Source: data-ops-business/data-ops-business-consumption/src/main/resources/db/migration/yak-consumption/V1__subscription_truth.sql
CREATE TABLE IF NOT EXISTS yak_ops_consumption_subscription (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Security Project Space ID',
    product_key VARCHAR(255) NOT NULL COMMENT 'Canonical Phase 4 ProductKey',
    consumer_type VARCHAR(32) NOT NULL COMMENT 'USER/TEAM/DASHBOARD/DATA_SERVICE/JOB',
    source_domain VARCHAR(64) NOT NULL COMMENT 'Consumer owning domain',
    source_identity VARCHAR(255) NOT NULL COMMENT 'Stable consumer identity in owning domain',
    display_hint VARCHAR(255) DEFAULT NULL COMMENT 'Presentation-only consumer label',
    consumption_mode VARCHAR(32) NOT NULL COMMENT 'QUERY/PREVIEW/EXPORT/API_INVOKE/DOWNSTREAM',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELLED',
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

-- Source: data-ops-business/data-ops-business-consumption/src/main/resources/db/migration/yak-consumption/V2__usage_evidence_truth.sql
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

-- Source: data-ops-business/data-ops-business-consumption/src/main/resources/db/migration/yak-consumption/V3__subscription_lifecycle.sql
UPDATE yak_ops_consumption_subscription
SET status = 'REVOKED'
WHERE status = 'CANCELLED';

ALTER TABLE yak_ops_consumption_subscription
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/SUSPENDED/REVOKED';
