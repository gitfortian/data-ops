-- W1-1 TTL 策略版本化(契约 C2/C4):主表列=可编辑草稿,yak_lc_policy_version=已发布全量快照(append-only)。
-- 存量策略回填为 v1 已发布,消费方(绑定/监控/下发)自此只读发布快照。

ALTER TABLE yak_lc_policy
    ADD COLUMN publish_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT '发布态 DRAFT/PUBLISHED/OFFLINE(与可用性开关 status 正交)' AFTER status,
    ADD COLUMN draft_revision INT NOT NULL DEFAULT 0
        COMMENT '草稿修订号,每次编辑自增' AFTER publish_state,
    ADD COLUMN published_version_id BIGINT NULL
        COMMENT '当前发布快照 -> yak_lc_policy_version.id,消费方唯一入口' AFTER draft_revision,
    ADD COLUMN latest_version_no INT NOT NULL DEFAULT 0
        COMMENT '最大版本号(展示用冗余,分配以版本表 MAX+1 为准)' AFTER published_version_id;

CREATE TABLE IF NOT EXISTS yak_lc_policy_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    policy_id BIGINT NOT NULL COMMENT '所属策略',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    version_no INT NOT NULL COMMENT '版本号,自 1 追加',
    payload_json LONGTEXT NOT NULL COMMENT '发布内容全量快照(规范化 JSON)',
    checksum CHAR(64) NOT NULL COMMENT 'SHA-256(payload_json 口径见 VersionDigests;回填行为 LOWER(SHA2(...)) 可能异口径,比较一律走语义快照等值)',
    source_draft_revision INT NULL COMMENT '发布时的草稿修订号',
    created_by VARCHAR(64) NOT NULL COMMENT '发布人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发布时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_lc_policy_version (policy_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='TTL 策略发布版本(append-only,禁止物理删除/修改)';

INSERT INTO yak_lc_policy_version
    (policy_id, project_id, version_no, payload_json, checksum, source_draft_revision, created_by)
SELECT id, project_id, 1,
       JSON_OBJECT(
           'policyCode', policy_code,
           'policyName', policy_name,
           'scopeType', scope_type,
           'layerCode', layer_code,
           'partitionGranularity', partition_granularity,
           'hotDays', hot_days,
           'coldDays', cold_days,
           'destroyDays', destroy_days,
           'remark', remark),
       LOWER(SHA2(CONCAT(policy_code, '|', policy_name, '|', scope_type, '|',
           IFNULL(layer_code, ''), '|', partition_granularity, '|',
           IFNULL(hot_days, ''), '|', IFNULL(cold_days, ''), '|',
           IFNULL(destroy_days, ''), '|', IFNULL(remark, '')), 256)),
       0, created_by
FROM yak_lc_policy
WHERE deleted = 0;

UPDATE yak_lc_policy p
    JOIN yak_lc_policy_version v ON v.policy_id = p.id AND v.version_no = 1
SET p.publish_state = 'PUBLISHED',
    p.published_version_id = v.id,
    p.latest_version_no = 1
WHERE p.deleted = 0;
