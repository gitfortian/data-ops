-- Model version management: publish snapshots + rollback support.
-- published_version_id: points to the currently-online version row.
-- latest_version_no: monotonically increasing counter per model (0 = never published).

ALTER TABLE yak_modeling_model
    ADD COLUMN published_version_id BIGINT NULL COMMENT '当前发布版本ID',
    ADD COLUMN latest_version_no INT NOT NULL DEFAULT 0 COMMENT '最新版本号(0=未发布)';

CREATE TABLE IF NOT EXISTS yak_modeling_model_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    version_no INT NOT NULL COMMENT '版本号(模型内递增)',
    structure_json LONGTEXT NOT NULL COMMENT '完整结构快照(JSON)',
    column_count INT NOT NULL DEFAULT 0 COMMENT '字段数量',
    checksum CHAR(64) NOT NULL COMMENT '结构 SHA-256 摘要(幂等判定)',
    published_by VARCHAR(128) NULL COMMENT '发布人',
    publish_time DATETIME(6) NOT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_modeling_version_model_no (model_id, version_no),
    KEY idx_modeling_version_model_time (model_id, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型版本快照(不可变)';
