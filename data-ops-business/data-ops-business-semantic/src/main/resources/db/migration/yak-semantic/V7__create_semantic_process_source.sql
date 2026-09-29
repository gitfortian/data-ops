-- Process-to-source-table bindings (ticket 36). datasource_id references the
-- datasource module (loose ID, no FK); connectivity is validated at bind time
-- via DataSourceConnectionTester.

CREATE TABLE IF NOT EXISTS yak_semantic_process_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    process_id BIGINT NOT NULL COMMENT '业务过程',
    datasource_id BIGINT NOT NULL COMMENT '数据源 ID(datasource 模块松散引用)',
    source_table VARCHAR(128) NOT NULL COMMENT '源表名',
    table_role VARCHAR(16) NOT NULL DEFAULT 'MAIN' COMMENT '表角色:MAIN/DETAIL/DIM',
    join_condition VARCHAR(512) NULL COMMENT '关联条件',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_process_source (project_id, process_id, datasource_id, source_table),
    KEY idx_yak_semantic_process_source_process (project_id, process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务过程-源表关联';
