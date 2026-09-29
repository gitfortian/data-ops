-- Business processes (ticket 34): business actions anchored under a domain.
-- grain = analysis granularity (e.g. 单据/明细); biz_type FACT|DIMENSION.

CREATE TABLE IF NOT EXISTS yak_semantic_process (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    process_code VARCHAR(64) NOT NULL COMMENT '业务过程编码,项目内唯一,创建后不可改',
    process_name VARCHAR(128) NOT NULL COMMENT '业务过程名称',
    domain_id BIGINT NOT NULL COMMENT '所属业务域',
    grain VARCHAR(64) NULL COMMENT '粒度(如 单据/明细/天)',
    biz_type VARCHAR(16) NOT NULL DEFAULT 'FACT' COMMENT '类型:FACT/DIMENSION',
    owner VARCHAR(64) NULL COMMENT '负责人',
    description VARCHAR(512) NULL COMMENT '描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_process (project_id, process_code),
    KEY idx_yak_semantic_process_domain (project_id, domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务语义业务过程';
