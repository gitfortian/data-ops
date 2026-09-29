-- Yak Ops Data Service first-release baseline.
-- This file represents the complete schema owned by the Data Service module.

CREATE TABLE IF NOT EXISTS yak_ops_data_service_api (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Security Project Space ID',
    name VARCHAR(128) NOT NULL COMMENT '服务名称',
    path VARCHAR(255) NOT NULL COMMENT '服务相对路径',
    data_source_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Ops 数据源 ID',
    sql_text LONGTEXT NOT NULL COMMENT '只读 SELECT SQL，支持 :name 命名参数',
    max_rows INT NOT NULL DEFAULT 1000 COMMENT '最大返回行数',
    timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '查询超时秒数',
    pagination_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用返回结果分页',
    enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
    auth_mode VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '访问控制：NONE/API_KEY',
    cache_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用本机结果缓存',
    cache_ttl_seconds INT NOT NULL DEFAULT 60 COMMENT '缓存 TTL（秒）',
    cache_max_entries INT NOT NULL DEFAULT 200 COMMENT '单服务最大缓存条目数',
    circuit_breaker_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用熔断保护',
    circuit_failure_threshold INT NOT NULL DEFAULT 5 COMMENT '连续失败触发熔断阈值',
    circuit_recovery_seconds INT NOT NULL DEFAULT 30 COMMENT '熔断恢复等待秒数',
    runtime_generation BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Runtime 缓存命名空间单调代际',
    description VARCHAR(500) DEFAULT NULL COMMENT '说明',
    source_type VARCHAR(64) DEFAULT NULL COMMENT '发布来源类型',
    source_ref VARCHAR(128) DEFAULT NULL COMMENT '发布来源引用',
    source_revision_id BIGINT DEFAULT NULL COMMENT '来源版本 ID',
    source_revision_no INT DEFAULT NULL COMMENT '来源版本号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_path (path),
    UNIQUE KEY uk_yak_ops_data_service_source (source_type, source_ref),
    KEY idx_yak_ops_data_service_ds (data_source_id),
    KEY idx_yak_ops_data_service_enabled (enabled),
    KEY idx_yak_ops_data_service_update_time (update_time),
    KEY idx_yak_ops_data_service_auth_mode (auth_mode),
    KEY idx_yak_ops_data_service_source_revision (source_revision_id),
    KEY idx_yak_ops_data_service_project_update (project_id, update_time, id),
    KEY idx_yak_ops_data_service_project_enabled (project_id, enabled)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务 API 定义';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_api_key (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    name VARCHAR(128) NOT NULL COMMENT '调用方/Key 名称',
    key_prefix VARCHAR(24) NOT NULL COMMENT '仅用于识别的 Key 前缀',
    key_hash CHAR(64) NOT NULL COMMENT 'API Key SHA-256 摘要',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    rate_limit_per_minute INT NOT NULL DEFAULT 60 COMMENT '每分钟最大调用次数',
    expires_at DATETIME(3) DEFAULT NULL COMMENT '过期时间，NULL 表示不过期',
    last_used_at DATETIME(3) DEFAULT NULL COMMENT '最后调用时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_key_hash (key_hash),
    KEY idx_yak_ops_data_service_key_api (api_id),
    KEY idx_yak_ops_data_service_key_enabled (api_id, enabled),
    KEY idx_yak_ops_data_service_key_expire (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务 API Key';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_documentation (
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    sql_hash CHAR(64) DEFAULT NULL COMMENT '保存文档时的 SQL SHA-256，用于识别 Schema 漂移',
    parameter_schema_json MEDIUMTEXT DEFAULT NULL COMMENT '请求参数文档 JSON',
    response_schema_json MEDIUMTEXT DEFAULT NULL COMMENT '响应行字段文档 JSON',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (api_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务 API 文档元数据';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_call_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '调用发生时的数据服务 Project Space ID',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    service_name VARCHAR(128) NOT NULL COMMENT '调用时服务名称快照',
    service_path VARCHAR(255) NOT NULL COMMENT '调用时路径快照',
    caller_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY' COMMENT '调用方类型：LEGACY/PUBLIC/API_KEY/CONSOLE',
    api_key_id BIGINT UNSIGNED DEFAULT NULL COMMENT '调用 API Key ID',
    api_key_name VARCHAR(128) DEFAULT NULL COMMENT '调用方名称快照',
    api_key_prefix VARCHAR(24) DEFAULT NULL COMMENT 'API Key 前缀快照',
    params_json TEXT DEFAULT NULL COMMENT '请求参数 JSON',
    success TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否成功',
    duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '执行耗时毫秒',
    row_count INT NOT NULL DEFAULT 0 COMMENT '返回行数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_yak_ops_data_service_log_api (api_id),
    KEY idx_yak_ops_data_service_log_time (create_time),
    KEY idx_yak_ops_data_service_log_success (success),
    KEY idx_yak_ops_data_service_log_key (api_key_id),
    KEY idx_yak_ops_data_service_log_time_api (create_time, api_id),
    KEY idx_yak_ops_data_service_log_success_time_id (success, create_time, id),
    KEY idx_yak_ops_data_service_log_api_time_id (api_id, create_time, id),
    KEY idx_yak_ops_data_service_log_project_time (project_id, create_time, id),
    KEY idx_yak_ops_data_service_log_project_api_time (project_id, api_id, create_time, id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务调用日志';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_rate_window (
    api_key_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API Key ID',
    window_minute BIGINT NOT NULL COMMENT 'Epoch minute shared across instances',
    request_count INT NOT NULL DEFAULT 0 COMMENT 'Cluster-wide calls admitted in this window',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Last update time',
    PRIMARY KEY (api_key_id, window_minute),
    KEY idx_yak_ops_data_service_rate_window_minute (window_minute)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops Data Service cluster rate-limit windows';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_call_log_hourly (
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Project Space ID',
    api_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API ID',
    bucket_hour DATETIME NOT NULL COMMENT 'Application/database local hour bucket',
    service_name VARCHAR(256) NOT NULL COMMENT 'Service name snapshot',
    service_path VARCHAR(512) NOT NULL COMMENT 'Service path snapshot',
    total_calls BIGINT NOT NULL DEFAULT 0,
    success_calls BIGINT NOT NULL DEFAULT 0,
    failure_calls BIGINT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    total_rows BIGINT NOT NULL DEFAULT 0,
    first_call_at DATETIME(3) DEFAULT NULL,
    last_call_at DATETIME(3) DEFAULT NULL,
    last_success_at DATETIME(3) DEFAULT NULL,
    last_failure_at DATETIME(3) DEFAULT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id, api_id, bucket_hour),
    KEY idx_yak_ops_data_service_rollup_project_hour (project_id, bucket_hour),
    KEY idx_yak_ops_data_service_rollup_api_hour (project_id, api_id, bucket_hour)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops Data Service hourly invocation rollup';
