-- Consumer-centric Data Service access model.
-- API keys belong to a caller (consumer) and one consumer may access many Data Service APIs.

CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Security Project Space ID',
    name VARCHAR(128) NOT NULL COMMENT '调用方名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '调用方说明',
    access_scope VARCHAR(16) NOT NULL DEFAULT 'SELECTED' COMMENT 'ALL/SELECTED',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许该调用方访问',
    default_rate_limit_per_minute INT NOT NULL DEFAULT 60 COMMENT '新建 Key 默认每分钟调用上限',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_consumer_name (project_id, name),
    KEY idx_yak_ops_data_service_consumer_project (project_id, update_time, id),
    KEY idx_yak_ops_data_service_consumer_scope (project_id, access_scope, enabled)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务调用方';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_api_grant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer_id BIGINT UNSIGNED NOT NULL COMMENT '调用方 ID',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_consumer_api (consumer_id, api_id),
    KEY idx_yak_ops_data_service_grant_api (api_id, consumer_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务调用方 API 授权';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_ip_access_policy (
    consumer_id BIGINT UNSIGNED NOT NULL COMMENT '调用方 ID',
    mode VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/ALLOWLIST/DENYLIST',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (consumer_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务调用方 IP 访问策略';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_ip_access_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer_id BIGINT UNSIGNED NOT NULL COMMENT '调用方 ID',
    rule_type VARCHAR(16) NOT NULL COMMENT 'ALLOWLIST/DENYLIST',
    network_cidr VARCHAR(80) NOT NULL COMMENT '规范化 IP 或 CIDR',
    description VARCHAR(255) DEFAULT NULL COMMENT '规则说明',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    expires_at DATETIME(3) DEFAULT NULL COMMENT '过期时间，NULL 表示永久',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_consumer_ip_rule
        (consumer_id, rule_type, network_cidr),
    KEY idx_yak_ops_data_service_consumer_ip_active
        (consumer_id, rule_type, enabled, expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务调用方 IP 黑白名单';

ALTER TABLE yak_ops_data_service_api_key
    ADD COLUMN consumer_id BIGINT UNSIGNED DEFAULT NULL COMMENT '调用方 ID' AFTER api_id,
    ADD KEY idx_yak_ops_data_service_key_consumer (consumer_id, enabled);

-- Existing API-scoped keys cannot be grouped into callers reliably, so preserve behavior by
-- creating one migrated consumer per key and granting only its original API. Raw keys stay valid.
INSERT INTO yak_ops_data_service_consumer
(id, project_id, name, description, access_scope, enabled,
 default_rate_limit_per_minute, create_time, update_time)
SELECT key_row.id,
       api_row.project_id,
       CONCAT(LEFT(COALESCE(NULLIF(TRIM(key_row.name), ''), '调用方'), 90),
              ' · legacy-', key_row.id),
       '由旧版单 API Key 自动迁移；可在 API 调用页面重新归并调用方。',
       'SELECTED',
       key_row.enabled,
       key_row.rate_limit_per_minute,
       key_row.create_time,
       key_row.update_time
FROM yak_ops_data_service_api_key key_row
JOIN yak_ops_data_service_api api_row ON api_row.id = key_row.api_id
WHERE key_row.api_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id),
    enabled = VALUES(enabled),
    default_rate_limit_per_minute = VALUES(default_rate_limit_per_minute),
    update_time = VALUES(update_time);

INSERT IGNORE INTO yak_ops_data_service_consumer_api_grant
(consumer_id, api_id, create_time)
SELECT key_row.id, key_row.api_id, key_row.create_time
FROM yak_ops_data_service_api_key key_row
WHERE key_row.api_id IS NOT NULL;

UPDATE yak_ops_data_service_api_key
SET consumer_id = id
WHERE consumer_id IS NULL
  AND api_id IS NOT NULL;

-- New consumer credentials are not owned by one API. Keep api_id only as a compatibility marker
-- for migrated keys and legacy management endpoints.
ALTER TABLE yak_ops_data_service_api_key
    MODIFY COLUMN api_id BIGINT UNSIGNED NULL COMMENT '旧版单 API 归属；Consumer Key 为 NULL';
