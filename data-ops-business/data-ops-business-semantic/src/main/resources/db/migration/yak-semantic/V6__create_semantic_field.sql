-- Standard field library + process references (ticket 35, optimized 2026-09-15).
-- Fields are project-global; processes reference them via
-- yak_semantic_process_field (no copying). std_* loose references into
-- yak_semantic_standard; std_code_set_code references the CODE value DOMAIN
-- (code_set_code string) instead of a single code row. data_type is the
-- effective type: snapshot of the referenced standard's std_type (server
-- synced), or hand-filled when no type standard is referenced.

CREATE TABLE IF NOT EXISTS yak_semantic_field (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    field_code VARCHAR(64) NOT NULL COMMENT '字段编码,项目内唯一,创建后不可改',
    field_name VARCHAR(128) NOT NULL COMMENT '字段名称',
    role VARCHAR(16) NOT NULL COMMENT '角色:PROCESS(过程标识)/DIMENSION(维度)/METRIC(度量)',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED(停用不出现在字段集/绑定/派生)',
    data_type VARCHAR(64) NULL COMMENT '生效类型:引用类型标准时为 std_type 快照(服务端同步),未引用可手填',
    std_type_id BIGINT NULL COMMENT '类型标准引用(yak_semantic_standard,kind=TYPE)',
    std_unit_id BIGINT NULL COMMENT '单位标准引用(kind=UNIT)',
    std_caliber_id BIGINT NULL COMMENT '口径标准引用(kind=CALIBER)',
    std_code_set_code VARCHAR(64) NULL COMMENT '码集编码引用(CODE 类标准的 code_set_code,约束整个值域)',
    std_security_id BIGINT NULL COMMENT '安全标准引用(kind=SECURITY)',
    business_desc VARCHAR(512) NULL COMMENT '业务描述',
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源:PRESET(预置复制)/MANUAL(手工创建)/CAPTURE(沉淀)',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观版本,每次修改自增(后续版本快照基线)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_field (project_id, field_code),
    KEY idx_yak_semantic_field_role (project_id, role, status),
    -- 预留:码集详情页展示"哪些字段引用本码集" + 未来码集变更影响分析
    KEY idx_yak_semantic_field_code_set (project_id, std_code_set_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务语义标准字段库';

-- Process-to-field references (ordered). One field may be referenced by many
-- processes; uniqueness is per process. is_required drives derivation
-- default selection (ticket 44).

CREATE TABLE IF NOT EXISTS yak_semantic_process_field (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    process_id BIGINT NOT NULL COMMENT '业务过程',
    field_id BIGINT NOT NULL COMMENT '标准字段',
    is_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否过程必需字段(44 派生默认勾选)',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '过程内字段顺序,0 起',
    created_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '绑定人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_process_field (project_id, process_id, field_id),
    KEY idx_yak_semantic_process_field_field (project_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务过程-标准字段引用';
