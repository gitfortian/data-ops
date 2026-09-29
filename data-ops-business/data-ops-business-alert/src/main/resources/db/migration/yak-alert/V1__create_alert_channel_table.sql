-- Yak Ops 告警渠道配置表。
CREATE TABLE IF NOT EXISTS yak_ops_alert_channel (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel_type VARCHAR(64) NOT NULL COMMENT '渠道类型标识（如 DINGTALK）',
    config_json LONGTEXT NOT NULL COMMENT '渠道配置 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    conn_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连通状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_channel_type (channel_type)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 告警渠道配置';
