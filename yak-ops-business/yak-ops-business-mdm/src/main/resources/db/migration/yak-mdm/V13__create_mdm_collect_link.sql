-- 采集落地链路绑定(R1,review P0-1.7):一条已确认来源(yak_mdm_source)对应
-- 一个数据集成的离线落地任务;MDM 侧只存反查锚点 job_definition_id 与落地表名,
-- 采集状态/最近运行由 sync 模块按 jobDefinitionId 反查,不在 MDM 冗余执行态。

CREATE TABLE IF NOT EXISTS yak_mdm_collect_link (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体(冗余,便于按实体列表)',
    source_id BIGINT NOT NULL COMMENT '主数据来源绑定 yak_mdm_source.id',
    datasource_id BIGINT NOT NULL COMMENT '源数据源(松散 ID,冗余自来源)',
    landing_table VARCHAR(128) NOT NULL COMMENT '平台库落地表名',
    job_definition_id BIGINT NOT NULL COMMENT '数据集成离线任务定义 ID(反查锚点)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_collect_link_source (project_id, source_id),
    KEY idx_yak_mdm_collect_link_entity (project_id, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据采集落地链路绑定';
