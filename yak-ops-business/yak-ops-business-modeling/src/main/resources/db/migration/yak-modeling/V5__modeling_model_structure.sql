-- Warehouse modeling table structure editing (ticket 05): table info + columns.
--
-- table_name is the physical table name; blank falls back to model_code at
-- save time. Columns are saved with full-replace semantics: the request
-- carries the complete ordered column list, persisted as sort_order 0..n-1.

ALTER TABLE yak_modeling_model
    ADD COLUMN table_name VARCHAR(128) NULL COMMENT '物理表名,空则按 model_code 兜底',
    ADD COLUMN table_comment VARCHAR(512) NULL COMMENT '表注释';

CREATE TABLE IF NOT EXISTS yak_modeling_model_column (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    column_name VARCHAR(128) NOT NULL COMMENT '字段名,模型内唯一(不区分大小写)',
    data_type VARCHAR(64) NOT NULL COMMENT '数据类型',
    length INT NULL COMMENT '长度或精度',
    scale INT NULL COMMENT '小数位数',
    nullable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可空',
    default_value VARCHAR(256) NULL COMMENT '默认值',
    column_comment VARCHAR(512) NULL COMMENT '字段注释',
    business_description VARCHAR(512) NULL COMMENT '业务描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '字段顺序,0 起',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_column (model_id, column_name),
    KEY idx_yak_modeling_model_column_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型字段';
