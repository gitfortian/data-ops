-- Lifecycle module V1 (ticket 80): TTL policy, model binding, dispatch record,
-- storage snapshot, settings. See docs/data-lifecycle/design.md §1.

CREATE TABLE IF NOT EXISTS yak_lc_policy (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    policy_code VARCHAR(64) NOT NULL COMMENT '策略编码,项目内唯一,自动生成',
    policy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
    scope_type VARCHAR(16) NOT NULL COMMENT 'LAYER_DEFAULT/CUSTOM',
    layer_code VARCHAR(32) NULL COMMENT '分层默认策略持有的层编码',
    partition_granularity VARCHAR(8) NOT NULL DEFAULT 'DAY' COMMENT '分区粒度:DAY/MONTH/YEAR',
    hot_days INT NULL COMMENT '热窗口天数,NULL=无热段',
    cold_days INT NULL COMMENT '冷边界天数(≥热)',
    destroy_days INT NULL COMMENT '删除边界天数,NULL=永久保留',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '1=模板初始化产生(不可删可改)',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    remark VARCHAR(512) NULL COMMENT '备注',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_lc_policy_code (project_id, policy_code),
    KEY idx_yak_lc_policy_layer (project_id, layer_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTL 策略';

CREATE TABLE IF NOT EXISTS yak_lc_model_binding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '模型(引用 modeling,松散 ID)',
    policy_id BIGINT NOT NULL COMMENT '生效策略(覆盖分层默认)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删=回到继承',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_lc_binding_model (project_id, model_id, deleted),
    KEY idx_yak_lc_binding_policy (project_id, policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型 TTL 绑定(行存在=覆盖)';

CREATE TABLE IF NOT EXISTS yak_lc_dispatch_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '模型',
    policy_id BIGINT NOT NULL COMMENT '下发时生效策略',
    policy_updated_at DATETIME(6) NULL COMMENT '下发时策略 update_time 快照,漂移判定基准',
    trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH/RETRY',
    datasource_id BIGINT NULL COMMENT '目标数据源',
    database_name VARCHAR(128) NULL COMMENT '目标库',
    table_name VARCHAR(128) NULL COMMENT '目标表',
    storage_type VARCHAR(16) NULL COMMENT 'DORIS/PAIMON(解析结果)',
    statement TEXT NULL COMMENT '实际执行的 TTL 语句',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED/RETRYING/EXHAUSTED',
    attempts INT NOT NULL DEFAULT 1 COMMENT '执行次数(含首发),上限5',
    next_retry_time DATETIME(6) NULL COMMENT '重试闹钟扫描时间',
    error_message TEXT NULL COMMENT '最近失败原因',
    partition_hot INT NULL COMMENT '下发时热分区数快照',
    partition_cold INT NULL COMMENT '下发时冷分区数快照',
    partition_deleted INT NULL COMMENT '下发时将被删除分区数快照',
    operator VARCHAR(64) NOT NULL COMMENT '操作人(系统重试=SYSTEM)',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    finish_time DATETIME(6) NULL COMMENT '执行完成时间',
    PRIMARY KEY (id),
    KEY idx_yak_lc_dispatch_model (project_id, model_id, id),
    KEY idx_yak_lc_dispatch_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTL 下发流水(重试事实源)';

CREATE TABLE IF NOT EXISTS yak_lc_storage_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    snapshot_date DATE NOT NULL COMMENT '快照日期',
    layer_code VARCHAR(32) NOT NULL COMMENT '分层',
    datasource_id BIGINT NOT NULL COMMENT '来源数据源',
    database_name VARCHAR(128) NOT NULL COMMENT '库',
    table_name VARCHAR(128) NOT NULL COMMENT '表',
    size_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '存储字节(SHOW DATA)',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_lc_snapshot (project_id, snapshot_date, datasource_id, table_name),
    KEY idx_yak_lc_snapshot_trend (project_id, snapshot_date, layer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日存储快照';

CREATE TABLE IF NOT EXISTS yak_lc_setting (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    setting_key VARCHAR(64) NOT NULL COMMENT '键:cost_price_per_gb_month 等',
    setting_value VARCHAR(256) NULL COMMENT '值',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_lc_setting (project_id, setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生命周期模块设置';
