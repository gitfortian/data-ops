-- Layer-field mapping (ticket 43): where each standard field lands per layer
-- of a model. Decision D: rows are mostly auto-written by derivation (44);
-- manual edits are corrections. layer_id references yak_semantic_layer and
-- process_field_id references yak_semantic_field (loose IDs, no FK).

CREATE TABLE IF NOT EXISTS yak_modeling_layer_field_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    process_field_id BIGINT NOT NULL COMMENT '标准字段(semantic 松散 ID)',
    layer_id BIGINT NOT NULL COMMENT '分层(semantic 松散 ID,37 为源)',
    layer_field_name VARCHAR(128) NOT NULL COMMENT '该层落地字段名',
    layer_data_type VARCHAR(64) NULL COMMENT '该层落地类型',
    source_field VARCHAR(256) NULL COMMENT '来源字段(模型内字段名或上游字段)',
    transform_expr VARCHAR(1024) NULL COMMENT '转换表达式(语法守卫)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_lfm (project_id, model_id, process_field_id, layer_id, layer_field_name),
    KEY idx_yak_modeling_lfm_field (project_id, process_field_id),
    KEY idx_yak_modeling_lfm_layer (project_id, layer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段分层映射(标准字段 x 层落地)';
