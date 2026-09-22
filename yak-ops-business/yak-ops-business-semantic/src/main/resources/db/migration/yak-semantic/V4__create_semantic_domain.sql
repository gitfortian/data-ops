-- Business domain tree (ticket 33). parent_id=0 means root; the tree is
-- shallow-validated in the service (cycle prevention on move). Business
-- process reference checks for deletion arrive with ticket 34.

CREATE TABLE IF NOT EXISTS yak_semantic_domain (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    domain_code VARCHAR(64) NOT NULL COMMENT '业务域编码,项目内唯一,创建后不可改',
    domain_name VARCHAR(128) NOT NULL COMMENT '业务域名称',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父域 ID,0=根',
    owner VARCHAR(64) NULL COMMENT '负责人',
    description VARCHAR(512) NULL COMMENT '描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_domain (project_id, domain_code),
    KEY idx_yak_semantic_domain_parent (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务语义业务域树';
