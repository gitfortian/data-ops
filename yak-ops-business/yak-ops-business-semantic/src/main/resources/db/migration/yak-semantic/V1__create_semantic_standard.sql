-- Business semantic module: data standards catalog (ticket 30).
--
-- Six standard kinds share one table with a kind discriminator; kind-specific
-- columns are nullable and validated by the service layer (DOMAIN.md). The
-- unique key (project_id, kind, std_code) is the DB-level backstop for the
-- per-project per-kind code uniqueness invariant.

CREATE TABLE IF NOT EXISTS yak_semantic_standard (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    kind VARCHAR(16) NOT NULL COMMENT '标准类别:NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY',
    std_code VARCHAR(64) NOT NULL COMMENT '标准编码,类别内唯一,创建后不可改',
    std_name VARCHAR(128) NOT NULL COMMENT '标准名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观版本,每次修改自增',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    is_preset TINYINT(1) NOT NULL DEFAULT 0 COMMENT '预置标识:1=来自预置初始化',
    description VARCHAR(512) NULL COMMENT '描述',
    scope VARCHAR(16) NULL COMMENT '命名标准:适用范围 TABLE/FIELD/DATABASE',
    layer VARCHAR(64) NULL COMMENT '命名标准:适用分层引用',
    rule_expr VARCHAR(1024) NULL COMMENT '命名标准:规则表达式',
    example VARCHAR(256) NULL COMMENT '命名标准:示例',
    type_code VARCHAR(64) NULL COMMENT '类型标准:类型编码',
    std_type VARCHAR(64) NULL COMMENT '类型标准:标准类型',
    source_mapping TEXT NULL COMMENT '类型标准:源库类型映射 JSON',
    code_set_code VARCHAR(64) NULL COMMENT '码值标准:码集编码',
    code_value VARCHAR(256) NULL COMMENT '码值标准:码值',
    code_label VARCHAR(256) NULL COMMENT '码值标准:码值标签',
    unit_code VARCHAR(64) NULL COMMENT '单位标准:单位编码',
    unit_type VARCHAR(64) NULL COMMENT '单位标准:单位类型',
    caliber_code VARCHAR(64) NULL COMMENT '口径标准:口径编码',
    cal_rule VARCHAR(1024) NULL COMMENT '口径标准:口径规则',
    business_desc VARCHAR(512) NULL COMMENT '口径标准:业务说明',
    level_code VARCHAR(64) NULL COMMENT '安全标准:等级编码',
    mask_rule VARCHAR(512) NULL COMMENT '安全标准:脱敏规则',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_standard (project_id, kind, std_code),
    KEY idx_yak_semantic_standard_kind (project_id, kind, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务语义数据标准(六类统一表)';
