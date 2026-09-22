-- Field-level source mapping (ticket 19). One mapping per target column
-- (model column). std_process_field_id is the M4 reservation (43/44): a
-- loose reference into yak-ops-business-semantic standard fields.

CREATE TABLE IF NOT EXISTS yak_modeling_column_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    target_column VARCHAR(128) NOT NULL COMMENT '模型目标字段名',
    source_datasource_id BIGINT NULL COMMENT '源数据源(datasource 松散引用)',
    source_database VARCHAR(128) NULL COMMENT '源库',
    source_table VARCHAR(128) NULL COMMENT '源表',
    source_column VARCHAR(128) NULL COMMENT '源字段',
    transform_expr VARCHAR(1024) NULL COMMENT '转换表达式(语法校验后存储)',
    std_process_field_id BIGINT NULL COMMENT '标准字段引用(semantic 松散 ID,M4 预留)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_column_mapping (project_id, model_id, target_column),
    KEY idx_yak_modeling_column_mapping_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型字段来源映射';
