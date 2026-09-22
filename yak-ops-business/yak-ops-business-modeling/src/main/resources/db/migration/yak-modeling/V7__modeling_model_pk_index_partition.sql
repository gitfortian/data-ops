-- Warehouse modeling primary key / index / partition editing (ticket 06).
--
-- Primary key is a column-name list stored on the model row (pk_columns as a
-- JSON array); indexes live in their own table with full-replace semantics;
-- partition config and table properties (JSON) ride on the model row. All
-- column references are validated against the saved column list in service.

ALTER TABLE yak_modeling_model
    ADD COLUMN pk_columns VARCHAR(1024) NULL COMMENT '主键列名 JSON 数组,空=无主键',
    ADD COLUMN partition_type VARCHAR(64) NULL COMMENT '分区类型(按方言)',
    ADD COLUMN partition_columns VARCHAR(1024) NULL COMMENT '分区列名 JSON 数组',
    ADD COLUMN partition_expr VARCHAR(512) NULL COMMENT '分区表达式',
    ADD COLUMN table_properties VARCHAR(2048) NULL COMMENT '表属性 JSON 对象';

CREATE TABLE IF NOT EXISTS yak_modeling_model_index (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    index_name VARCHAR(128) NOT NULL COMMENT '索引名,模型内唯一(不区分大小写)',
    unique_index TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否唯一索引',
    index_type VARCHAR(64) NULL COMMENT '索引类型(按方言,可空)',
    column_names VARCHAR(1024) NOT NULL COMMENT '索引列名 JSON 数组',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_index (model_id, index_name),
    KEY idx_yak_modeling_model_index_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型索引';
