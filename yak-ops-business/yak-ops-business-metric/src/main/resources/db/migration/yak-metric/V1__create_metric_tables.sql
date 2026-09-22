-- Metric module V1 (ticket 45): all 7 tables created in a single migration.
-- Tables: yak_metric, yak_metric_tag, yak_metric_tag_rel, yak_metric_version,
--         yak_metric_dependency, yak_metric_composition, yak_metric_usage.

CREATE TABLE IF NOT EXISTS yak_metric (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_code VARCHAR(64) NOT NULL COMMENT '指标编码,项目内唯一,创建后不可改',
    metric_name VARCHAR(128) NOT NULL COMMENT '指标名称',
    domain_id BIGINT NULL COMMENT '业务域(引用 semantic,松散 ID)',
    metric_type VARCHAR(16) NOT NULL COMMENT '类型:ATOMIC/DERIVED/COMPOSITE',
    caliber_id BIGINT NULL COMMENT '口径标准引用(引用 semantic,松散 ID,可空)',
    cal_rule VARCHAR(1024) NULL COMMENT '计算规则(从口径标准带出或手填)',
    model_id BIGINT NULL COMMENT '依赖模型(引用 modeling,松散 ID)',
    stat_dimensions JSON NULL COMMENT '统计维度(JSON 数组,如 ["order_date","order_city"])',
    stat_period VARCHAR(16) NOT NULL DEFAULT 'DAY' COMMENT '统计周期:DAY/WEEK/MONTH',
    unit_id BIGINT NULL COMMENT '单位标准引用(引用 semantic,松散 ID)',
    business_desc VARCHAR(512) NULL COMMENT '业务口径描述',
    owner VARCHAR(64) NULL COMMENT '负责人',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_metric_code (project_id, metric_code),
    KEY idx_yak_metric_domain (project_id, domain_id),
    KEY idx_yak_metric_type (project_id, metric_type),
    KEY idx_yak_metric_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标主表';

CREATE TABLE IF NOT EXISTS yak_metric_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    tag_code VARCHAR(64) NOT NULL COMMENT '标签编码,项目内唯一',
    tag_name VARCHAR(128) NOT NULL COMMENT '标签名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_metric_tag_code (project_id, tag_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标标签';

CREATE TABLE IF NOT EXISTS yak_metric_tag_rel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标',
    tag_id BIGINT NOT NULL COMMENT '标签',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_metric_tag_rel (project_id, metric_id, tag_id),
    KEY idx_yak_metric_tag_rel_tag (project_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标标签关联';

CREATE TABLE IF NOT EXISTS yak_metric_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标',
    version INT NOT NULL COMMENT '版本号',
    snapshot JSON NOT NULL COMMENT '版本快照',
    change_desc VARCHAR(512) NULL COMMENT '变更说明',
    changed_by VARCHAR(64) NOT NULL COMMENT '变更人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '变更时间',
    PRIMARY KEY (id),
    KEY idx_yak_metric_version_metric (project_id, metric_id),
    UNIQUE KEY uk_yak_metric_version (metric_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标版本历史';

CREATE TABLE IF NOT EXISTS yak_metric_dependency (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标',
    dependency_type VARCHAR(16) NOT NULL COMMENT '类型:MODEL/FIELD/CALIBER/UNIT/COMPOSITION',
    dependency_id BIGINT NOT NULL COMMENT '依赖对象 ID(松散引用)',
    dependency_code VARCHAR(64) NULL COMMENT '依赖对象编码(冗余快照,供展示)',
    dependency_version INT NULL COMMENT '依赖对象版本号(用于影响分析比对)',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_yak_metric_dep_metric (project_id, metric_id),
    KEY idx_yak_metric_dep_target (dependency_type, dependency_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标血缘登记(服务层自动写入)';

CREATE TABLE IF NOT EXISTS yak_metric_composition (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '复合指标 ID',
    sub_metric_id BIGINT NOT NULL COMMENT '子指标 ID',
    operator VARCHAR(8) NOT NULL COMMENT '运算方式:ADD/SUB/MUL/DIV',
    expression VARCHAR(512) NULL COMMENT '完整表达式(可选)',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '操作数顺序',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_yak_metric_comp_metric (project_id, metric_id),
    KEY idx_yak_metric_comp_sub (project_id, sub_metric_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复合指标组成';

CREATE TABLE IF NOT EXISTS yak_metric_usage (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标',
    usage_type VARCHAR(16) NOT NULL COMMENT '类型:REPORT/DASHBOARD/API/SCREEN',
    usage_id BIGINT NOT NULL COMMENT '使用方 ID',
    usage_name VARCHAR(128) NULL COMMENT '使用方名称(冗余快照)',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_yak_metric_usage_metric (project_id, metric_id),
    KEY idx_yak_metric_usage_consumer (usage_type, usage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标使用记录';
