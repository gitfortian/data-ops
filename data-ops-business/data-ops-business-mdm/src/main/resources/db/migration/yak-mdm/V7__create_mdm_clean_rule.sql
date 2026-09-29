-- Master data cleansing rule (ticket 56). Rule configuration is entity-level:
-- rule_type is DEDUP for now (57 adds STANDARDIZE/COMPLETE); rule_expr is a JSON
-- document describing the match fields and how they combine:
--   {"fields":[{"attrCode":"mobile","matchType":"EXACT"}],"condition":"AND"}
-- matchType: EXACT (raw equality) / FUZZY (trim + lowercase normalized equality).
-- enabled toggles participation in dedup discovery; sort_order keeps UI order.

CREATE TABLE IF NOT EXISTS yak_mdm_clean_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    rule_type VARCHAR(16) NOT NULL DEFAULT 'DEDUP' COMMENT '规则类型:DEDUP/STANDARDIZE/COMPLETE',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称(实体内唯一)',
    rule_expr JSON NOT NULL COMMENT '规则表达式(JSON):匹配字段与组合条件',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_clean_rule_name (project_id, entity_id, rule_name),
    KEY idx_yak_mdm_clean_rule_entity (project_id, entity_id),
    KEY idx_yak_mdm_clean_rule_type (project_id, rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据清洗规则';
