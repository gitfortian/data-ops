-- W1-2 离线同步草稿/发布双表（多版本契约 C2）：
-- 主表 definition_json/job_spec_json 语义收窄为"可编辑草稿"；已发布内容只存在于
-- yak_offline_job_revision（append-only 全量快照），执行/调度/工作流经 published_revision_id 读快照。
CREATE TABLE IF NOT EXISTS yak_offline_job_revision (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '发布版本 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    job_definition_id BIGINT NOT NULL COMMENT '任务定义 ID',
    version_no INT NOT NULL COMMENT '发布版本号，任务内 MAX+1 递增',
    definition_json LONGTEXT NOT NULL COMMENT '发布时冻结的 SyncDefinition 全量快照',
    job_spec_json LONGTEXT NOT NULL COMMENT '发布时冻结的逻辑 JobSpec',
    config_digest CHAR(64) NOT NULL COMMENT '逻辑 JobSpec SHA-256',
    checksum CHAR(64) NULL COMMENT 'definition+spec 组合摘要(展示用,等值判断不依赖)',
    source_version INT NULL COMMENT '发布时主表 version 计数(追溯用)',
    created_by VARCHAR(64) NULL COMMENT '发布人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_revision_version (job_definition_id, version_no),
    KEY idx_yak_offline_revision_job (job_definition_id, id),
    KEY idx_yak_offline_revision_idem (job_definition_id, checksum),
    KEY idx_yak_offline_revision_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步任务发布版本(append-only)';

-- 发布态收敛（契约 C1）：release_state 历史值 ONLINE 归一为 PUBLISHED。
UPDATE yak_offline_job_definition SET release_state = 'PUBLISHED' WHERE release_state = 'ONLINE';

ALTER TABLE yak_offline_job_definition
    ADD COLUMN published_revision_id BIGINT NULL COMMENT '当前生效的发布版本 ID；NULL=从未发布',
    ADD COLUMN latest_version_no INT NOT NULL DEFAULT 0 COMMENT '最大发布版本号(列表展示)';

-- 存量回填：已有可执行配置(spec 非空)的任务生成 v1 并把指针指过去，保证重启后
-- 执行路径(读发布快照)对存量任务行为不变。checksum 用 0x00 分隔与 Java
-- VersionDigests.sha256Fields 字节口径一致。
INSERT INTO yak_offline_job_revision
    (project_id, job_definition_id, version_no, definition_json, job_spec_json,
     config_digest, checksum, source_version, created_by)
SELECT d.project_id, d.id, 1, d.definition_json, d.job_spec_json,
       COALESCE(d.config_digest, LOWER(SHA2(d.job_spec_json, 256))),
       LOWER(SHA2(CONCAT(d.definition_json, 0x00, d.job_spec_json), 256)),
       d.version, 'migration'
FROM yak_offline_job_definition d
WHERE d.job_spec_json IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM yak_offline_job_revision r WHERE r.job_definition_id = d.id);

UPDATE yak_offline_job_definition d
SET d.published_revision_id =
        (SELECT r.id FROM yak_offline_job_revision r
          WHERE r.job_definition_id = d.id ORDER BY r.version_no DESC LIMIT 1),
    d.latest_version_no =
        (SELECT r.version_no FROM yak_offline_job_revision r
          WHERE r.job_definition_id = d.id ORDER BY r.version_no DESC LIMIT 1)
WHERE d.published_revision_id IS NULL
  AND d.job_spec_json IS NOT NULL;
