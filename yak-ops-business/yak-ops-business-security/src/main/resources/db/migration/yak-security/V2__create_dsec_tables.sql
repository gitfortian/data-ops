-- Data-security business tables (tickets 71~78). Prefix yak_dsec_.
-- Every row carries project_id (trusted context), no physical foreign keys.

CREATE TABLE IF NOT EXISTS yak_dsec_security_level (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    level_code VARCHAR(32) NOT NULL COMMENT '等级编码,项目内唯一,创建后不可改',
    level_name VARCHAR(64) NOT NULL COMMENT '等级名称',
    rank_no INT NOT NULL DEFAULT 0 COMMENT '序位,越大越敏感',
    std_security_id BIGINT NULL COMMENT '对齐语义中心 SECURITY 标准ID(可空)',
    description VARCHAR(512) NULL COMMENT '描述',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DISABLED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsec_level (project_id, level_code),
    KEY idx_dsec_level_rank (project_id, rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全等级(分级)';

CREATE TABLE IF NOT EXISTS yak_dsec_data_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    category_code VARCHAR(64) NOT NULL COMMENT '分类编码,项目内唯一',
    category_name VARCHAR(128) NOT NULL COMMENT '分类名称',
    parent_code VARCHAR(64) NULL COMMENT '父分类编码,根为空',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    description VARCHAR(512) NULL COMMENT '描述',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsec_category (project_id, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据分类';

CREATE TABLE IF NOT EXISTS yak_dsec_classification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    object_type VARCHAR(16) NOT NULL COMMENT 'DATASOURCE/DATABASE/TABLE/COLUMN',
    object_key VARCHAR(512) NOT NULL COMMENT '规范化自然键 type:dsId:db.table.column',
    datasource_id BIGINT NULL COMMENT '数据源ID',
    db_name VARCHAR(128) NULL COMMENT '库名',
    table_name VARCHAR(128) NULL COMMENT '表名',
    column_name VARCHAR(128) NULL COMMENT '列名',
    object_name VARCHAR(256) NULL COMMENT '展示名',
    level_id BIGINT NOT NULL COMMENT '安全等级ID',
    category_id BIGINT NULL COMMENT '数据分类ID',
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/DISCOVERED/INHERITED',
    confidence INT NOT NULL DEFAULT 100 COMMENT '置信度0-100',
    discovery_rule_id BIGINT NULL COMMENT '命中的发现规则ID',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'CANDIDATE/ACTIVE/REJECTED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsec_classification (project_id, object_key),
    KEY idx_dsec_classification_level (project_id, level_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产分级标签';

CREATE TABLE IF NOT EXISTS yak_dsec_discovery_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码,项目内唯一',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    match_type VARCHAR(16) NOT NULL COMMENT 'NAME/COMMENT/CONTENT/REGEX',
    pattern VARCHAR(512) NOT NULL COMMENT '关键词或正则',
    level_id BIGINT NOT NULL COMMENT '命中定级(安全等级ID)',
    category_id BIGINT NULL COMMENT '命中分类(数据分类ID)',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    description VARCHAR(512) NULL COMMENT '描述',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsec_discovery_rule (project_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感数据发现规则';

CREATE TABLE IF NOT EXISTS yak_dsec_access_policy (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    policy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
    subject_type VARCHAR(16) NOT NULL COMMENT 'USER/ROLE',
    subject_key VARCHAR(128) NOT NULL COMMENT '用户名/角色码',
    scope_type VARCHAR(16) NOT NULL COMMENT 'DATASOURCE/DATABASE/TABLE/COLUMN/LEVEL/ALL',
    datasource_id BIGINT NULL COMMENT '数据源ID',
    db_name VARCHAR(128) NULL COMMENT '库名',
    table_name VARCHAR(128) NULL COMMENT '表名',
    column_name VARCHAR(128) NULL COMMENT '列名',
    level_id BIGINT NULL COMMENT 'scope=LEVEL 时的等级ID',
    access_type VARCHAR(16) NOT NULL DEFAULT 'READ' COMMENT 'READ/WRITE/EXPORT',
    effect VARCHAR(16) NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
    priority INT NOT NULL DEFAULT 0 COMMENT '命中优先级,大者优先',
    valid_from DATETIME(6) NULL COMMENT '生效时间',
    valid_to DATETIME(6) NULL COMMENT '失效时间',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/DISABLED',
    applicant VARCHAR(64) NULL COMMENT '申请人',
    approver VARCHAR(64) NULL COMMENT '审批人',
    reason VARCHAR(512) NULL COMMENT '申请/审批理由',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_dsec_policy_subject (project_id, subject_type, subject_key),
    KEY idx_dsec_policy_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据访问策略';

CREATE TABLE IF NOT EXISTS yak_dsec_masking_algorithm (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间(内置算法按项目种子)',
    algo_code VARCHAR(32) NOT NULL COMMENT '算法编码',
    algo_name VARCHAR(64) NOT NULL COMMENT '算法名称',
    params VARCHAR(1024) NULL COMMENT '算法参数(JSON文本)',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '内置不可删',
    description VARCHAR(512) NULL COMMENT '描述',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsec_algo (project_id, algo_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脱敏算法字典';

CREATE TABLE IF NOT EXISTS yak_dsec_masking_policy (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    policy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
    level_id BIGINT NULL COMMENT '按等级触发',
    category_id BIGINT NULL COMMENT '按分类触发',
    column_pattern VARCHAR(128) NULL COMMENT '列名匹配(可空,支持 * 通配)',
    algo_id BIGINT NOT NULL COMMENT '施加算法ID',
    priority INT NOT NULL DEFAULT 0 COMMENT '多命中取高',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    description VARCHAR(512) NULL COMMENT '描述',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_dsec_masking_policy (project_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脱敏策略';

CREATE TABLE IF NOT EXISTS yak_dsec_access_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    access_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '访问时间',
    actor VARCHAR(64) NOT NULL COMMENT '访问人',
    resource_type VARCHAR(16) NOT NULL COMMENT 'DATASOURCE/TABLE/COLUMN',
    resource_key VARCHAR(512) NOT NULL COMMENT '对象自然键',
    resource_name VARCHAR(256) NULL COMMENT '展示名',
    action VARCHAR(16) NOT NULL COMMENT 'READ/WRITE/EXPORT',
    level_code VARCHAR(32) NULL COMMENT '命中等级编码',
    decision VARCHAR(16) NOT NULL COMMENT 'ALLOW/DENY/NEED_APPROVAL',
    masked TINYINT NOT NULL DEFAULT 0 COMMENT '是否脱敏',
    algo_code VARCHAR(32) NULL COMMENT '脱敏算法编码',
    source VARCHAR(32) NULL COMMENT '调用来源模块',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_dsec_log_time (project_id, access_time),
    KEY idx_dsec_log_actor (project_id, actor),
    KEY idx_dsec_log_decision (project_id, decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据访问流水';

CREATE TABLE IF NOT EXISTS yak_dsec_compliance_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码,项目内唯一',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(32) NOT NULL COMMENT '合规规则类型',
    params VARCHAR(1024) NULL COMMENT '参数(JSON文本)',
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    description VARCHAR(512) NULL COMMENT '描述',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsec_compliance_rule (project_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规规则';

CREATE TABLE IF NOT EXISTS yak_dsec_compliance_finding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    batch_id VARCHAR(64) NOT NULL COMMENT '体检批次',
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    rule_type VARCHAR(32) NOT NULL COMMENT '规则类型快照',
    target_key VARCHAR(512) NULL COMMENT '被检对象',
    target_name VARCHAR(256) NULL COMMENT '对象展示名',
    passed TINYINT NOT NULL DEFAULT 1 COMMENT '是否通过',
    finding VARCHAR(512) NULL COMMENT '缺口描述',
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
    checked_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '检查时间',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_dsec_finding_batch (project_id, batch_id),
    KEY idx_dsec_finding_passed (project_id, passed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规检查结果';
