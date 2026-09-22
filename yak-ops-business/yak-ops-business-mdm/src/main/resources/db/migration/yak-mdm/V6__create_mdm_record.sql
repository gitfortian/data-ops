-- Master data record (ticket 55a). The unified master table: master_id is the
-- cross-system stable id (D3), source_ids records each source system's raw id
-- (D4). Rows are written by master-data processing tasks executed in
-- data-development (plan A, D-M11); MDM reads them for display/governance.
--
-- status: ACTIVE visible / MERGED merged-away (kept for traceability) /
-- DELETED soft-deleted. version increments on each upsert.

CREATE TABLE IF NOT EXISTS yak_mdm_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    master_id VARCHAR(64) NOT NULL COMMENT '主数据唯一 ID(跨系统统一)',
    attributes JSON NOT NULL COMMENT '属性值(键=属性编码)',
    source_ids JSON NOT NULL COMMENT '各系统原始 ID:{datasourceId: 原始ID}',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE/MERGED/DELETED',
    version INT NOT NULL DEFAULT 1 COMMENT '版本,每次更新自增',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_record (project_id, entity_id, master_id),
    KEY idx_yak_mdm_record_entity (project_id, entity_id),
    KEY idx_yak_mdm_record_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录';
