-- Master data entity (ticket 51). entity_code is the project-scoped stable
-- key, immutable after creation. Attribute (52) / source (53) reference
-- checks for deletion arrive with their owning tickets.

CREATE TABLE IF NOT EXISTS yak_mdm_entity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_code VARCHAR(64) NOT NULL COMMENT '实体编码,项目内唯一,创建后不可改',
    entity_name VARCHAR(128) NOT NULL COMMENT '实体名称',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用',
    owner VARCHAR(64) NULL COMMENT '负责人',
    description VARCHAR(512) NULL COMMENT '描述',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_entity (project_id, entity_code),
    KEY idx_yak_mdm_entity_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据实体';
