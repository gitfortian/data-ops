-- Standard version snapshots (ticket 32). One row per accepted update, holding
-- the FULL pre-change state of the standard (version = the version being
-- replaced). Rollback is view-only this phase; history lives here plus audit.

CREATE TABLE IF NOT EXISTS yak_semantic_standard_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    standard_id BIGINT NOT NULL COMMENT '所属标准',
    version INT NOT NULL COMMENT '快照对应的版本号(修改前的版本)',
    payload_json TEXT NOT NULL COMMENT '修改前完整状态 JSON(公共列+类别专有列)',
    operated_by VARCHAR(64) NOT NULL COMMENT '执行修改的用户',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '快照时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_std_version (project_id, standard_id, version),
    KEY idx_yak_semantic_std_version_std (project_id, standard_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务语义数据标准版本快照';
