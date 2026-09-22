-- R7(reuse-plan,补齐 review 1.6): 去重「忽略组」账本。
-- 口径 = 组键 + 规则 + 原因:同一重复键在同一规则下只忽略一次,换规则不共享(键语义随规则变)。
-- 忽略不落 record 状态(ACTIVE/MERGED/DELETED 是数据生命周期,不能表达「已知非重复」),
-- 只在去重发现 SQL 里把命中的组键排除,并提供撤销。
--
-- match_key 必须用 utf8mb4_bin:去重键表达式来自 JSON_UNQUOTE(JSON_EXTRACT(...)),其结果集
-- 排序规则即 utf8mb4_bin;若本列用 *_ci,`keyExpr NOT IN (SELECT match_key ...)` 会因
-- 列与列之间排序规则不同直接报 1267,且大小写不敏感会把不同键误判为同一组。
CREATE TABLE IF NOT EXISTS yak_mdm_dedup_ignore (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    rule_id BIGINT NOT NULL COMMENT '忽略所属的去重规则(键语义随规则变,不跨规则共享)',
    match_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '重复组匹配键(与去重发现 GROUP BY 结果一致)',
    match_basis VARCHAR(512) NULL COMMENT '忽略时的匹配依据快照(仅用于「已忽略组」展示/审计)',
    reason VARCHAR(255) NULL COMMENT '忽略原因(已知非重复的说明)',
    created_by VARCHAR(64) NOT NULL COMMENT '忽略人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '忽略时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_dedup_ignore (project_id, rule_id, match_key),
    KEY idx_yak_mdm_dedup_ignore_entity (project_id, entity_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据去重忽略组(R7)';
