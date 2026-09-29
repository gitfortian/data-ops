-- Warehouse layer config (ticket 37). std_naming_id references a NAMING-kind
-- standard (loose ID); rule_expr is never copied (DOMAIN.md boundary).

CREATE TABLE IF NOT EXISTS yak_semantic_layer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    layer_code VARCHAR(32) NOT NULL COMMENT '分层编码,项目内唯一,创建后不可改',
    layer_name VARCHAR(64) NOT NULL COMMENT '分层名称',
    database_name VARCHAR(128) NULL COMMENT '该层对应库名',
    datasource_id BIGINT NULL COMMENT '该层对应数据源(datasource 松散引用)',
    std_naming_id BIGINT NULL COMMENT '命名标准引用(NAMING 类标准 ID)',
    default_partition VARCHAR(256) NULL COMMENT '默认分区表达式',
    storage_format VARCHAR(64) NULL COMMENT '存储格式',
    lifecycle_days INT NULL COMMENT '生命周期(天),空=永久',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    is_preset TINYINT(1) NOT NULL DEFAULT 0 COMMENT '预置标识',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_layer (project_id, layer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓分层配置';

-- Platform-level layer templates (no project_id): the four default layers.
CREATE TABLE IF NOT EXISTS yak_semantic_layer_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    layer_code VARCHAR(32) NOT NULL COMMENT '分层编码',
    layer_name VARCHAR(64) NOT NULL COMMENT '分层名称',
    description VARCHAR(512) NULL COMMENT '说明',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_layer_template (layer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓分层默认模板(平台级)';

INSERT INTO yak_semantic_layer_template (layer_code, layer_name, description, sort_order) VALUES
    ('ODS', '贴源层', '源表原样落地 + 技术字段', 10),
    ('DWD', '明细层', '清洗/规范化/退化维后的明细', 20),
    ('DWS', '汇总层', '按维度组合的轻度汇总', 30),
    ('ADS', '应用层', '面向应用的结果层', 40);
