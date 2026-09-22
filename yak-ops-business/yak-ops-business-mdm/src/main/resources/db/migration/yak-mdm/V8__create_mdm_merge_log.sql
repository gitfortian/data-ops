-- Master data merge history (ticket 56). Every merge is an explicit user
-- operation: the master record absorbs attributes/source_ids and the merged
-- records are marked MERGED (kept for traceability). This table records the
-- audit trail: which rule produced the group, which record survived, which
-- records were merged away, and who performed the merge.

CREATE TABLE IF NOT EXISTS yak_mdm_merge_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    rule_id BIGINT NULL COMMENT '触发合并的去重规则(可空)',
    master_record_id BIGINT NOT NULL COMMENT '保留的主记录',
    merged_record_ids JSON NOT NULL COMMENT '被合并记录 ID 列表',
    result VARCHAR(512) NOT NULL COMMENT '合并结果摘要',
    created_by VARCHAR(64) NOT NULL COMMENT '操作人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_yak_mdm_merge_log_entity (project_id, entity_id),
    KEY idx_yak_mdm_merge_log_master (project_id, master_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据合并日志';
