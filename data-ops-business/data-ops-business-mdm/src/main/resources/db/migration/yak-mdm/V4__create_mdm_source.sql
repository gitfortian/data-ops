-- Master data source binding (ticket 53). A source = datasource table bound
-- to a master data entity, role MAIN (primary) / AUXILIARY (supplementary,
-- registered now, participates in collection from 54). Collection config
-- columns (field_mapping/collect_mode/collect_freq) arrive with ticket 54.

CREATE TABLE IF NOT EXISTS yak_mdm_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    datasource_id BIGINT NOT NULL COMMENT '数据源(松散 ID)',
    source_database VARCHAR(128) NULL COMMENT '源库',
    source_schema VARCHAR(128) NULL COMMENT '源模式',
    source_table VARCHAR(128) NOT NULL COMMENT '源表',
    source_role VARCHAR(16) NOT NULL DEFAULT 'MAIN' COMMENT '角色:MAIN/AUXILIARY',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_source (project_id, entity_id, datasource_id, source_database, source_schema, source_table),
    KEY idx_yak_mdm_source_entity (project_id, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据来源';
