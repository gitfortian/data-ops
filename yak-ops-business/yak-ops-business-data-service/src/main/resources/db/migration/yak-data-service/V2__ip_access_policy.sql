-- Data Service IP/CIDR allowlist and denylist policy.

CREATE TABLE IF NOT EXISTS yak_ops_data_service_ip_access_policy (
    api_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API ID',
    mode VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/ALLOWLIST/DENYLIST',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (api_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops Data Service IP access policy';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_ip_access_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    api_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API ID',
    rule_type VARCHAR(16) NOT NULL COMMENT 'ALLOWLIST/DENYLIST',
    network_cidr VARCHAR(80) NOT NULL COMMENT '规范化 IP 或 CIDR',
    description VARCHAR(255) DEFAULT NULL COMMENT '规则说明',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    expires_at DATETIME(3) DEFAULT NULL COMMENT '过期时间，NULL 表示永久',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_ip_rule (api_id, rule_type, network_cidr),
    KEY idx_yak_ops_data_service_ip_rule_active (api_id, rule_type, enabled, expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops Data Service IP allowlist/denylist rules';
