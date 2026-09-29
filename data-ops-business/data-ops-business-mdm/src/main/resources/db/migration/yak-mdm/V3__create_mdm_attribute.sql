-- Master data attribute (ticket 52). attr_code is unique within an entity,
-- immutable after creation. Type/unit/security reference semantic standards by
-- id (loose id, no FK); code-set reference stores the code-set code. Collection
-- mapping (54) / clean rule (56) reference checks for deletion arrive with
-- their owning tickets.

CREATE TABLE IF NOT EXISTS yak_mdm_attribute (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    attr_code VARCHAR(64) NOT NULL COMMENT '属性编码,实体内唯一,创建后不可改',
    attr_name VARCHAR(128) NOT NULL COMMENT '属性名称',
    attr_type VARCHAR(16) NOT NULL COMMENT '属性角色:PK/ATTR/RELATION',
    data_type VARCHAR(64) NULL COMMENT '数据类型(快照)',
    std_type_id BIGINT NULL COMMENT '类型标准引用(松散 ID)',
    std_unit_id BIGINT NULL COMMENT '单位标准引用(松散 ID)',
    std_code_set_code VARCHAR(64) NULL COMMENT '码值标准引用(码集编码)',
    std_security_id BIGINT NULL COMMENT '安全标准引用(松散 ID)',
    is_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填',
    business_desc VARCHAR(512) NULL COMMENT '业务描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_attribute (project_id, entity_id, attr_code),
    KEY idx_yak_mdm_attribute_entity (project_id, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据属性';
