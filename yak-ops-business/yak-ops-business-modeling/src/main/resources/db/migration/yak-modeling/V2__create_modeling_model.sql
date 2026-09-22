-- Warehouse modeling physical model table (ticket 02: model CRUD vertical).
--
-- One model maps to exactly one physical table (docs/model/issues/02-model-crud.md).
-- project_id follows docs/architecture/PROJECT_SCOPE.md: PROJECT_ROOT ownership,
-- no physical foreign keys, uniqueness of the business key is per project space.

CREATE TABLE IF NOT EXISTS yak_modeling_model (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_code VARCHAR(128) NOT NULL COMMENT '模型编码,项目空间内唯一',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    dialect VARCHAR(64) NOT NULL COMMENT '目标数据库方言',
    description VARCHAR(512) NULL COMMENT '模型描述',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '模型状态:DRAFT',
    created_by VARCHAR(128) NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_project_code (project_id, model_code),
    KEY idx_yak_modeling_model_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模物理模型';
