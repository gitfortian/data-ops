-- Standard usage events (ticket 42, push-based): modeling reports APPLY /
-- BYPASS at its action points; semantic aggregates server-side.

CREATE TABLE IF NOT EXISTS yak_semantic_standard_usage (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    standard_id BIGINT NOT NULL COMMENT '标准',
    usage_type VARCHAR(16) NOT NULL COMMENT '事件:APPLY/BYPASS',
    scene VARCHAR(32) NULL COMMENT '场景:EDITOR/REVERSE_IMPORT/RECOMMEND',
    model_ref VARCHAR(64) NULL COMMENT '来源模型(松散引用)',
    operated_by VARCHAR(64) NULL COMMENT '触发用户',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '时间',
    PRIMARY KEY (id),
    KEY idx_yak_semantic_std_usage (project_id, standard_id, usage_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标准引用/绕过事件流水';
