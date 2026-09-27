-- F-005 / S2: append-only Definition Validation evidence bound to immutable MetricVersion.

CREATE TABLE IF NOT EXISTS yak_metric_validation_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '证据主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标 ID',
    metric_version_id BIGINT NOT NULL COMMENT 'yak_metric_version.id,不可变版本身份',
    metric_version INT NOT NULL COMMENT '指标版本号(审计展示冗余)',
    result VARCHAR(16) NOT NULL COMMENT '校验结果:READY/BLOCKED',
    issues_json JSON NOT NULL COMMENT '结构化校验问题数组',
    provider VARCHAR(128) NOT NULL COMMENT '校验 Provider 身份',
    snapshot_digest VARCHAR(64) NOT NULL COMMENT '被校验版本 snapshot 的 SHA-256',
    checked_by VARCHAR(64) NOT NULL COMMENT '校验执行人',
    checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '校验时间',
    PRIMARY KEY (id),
    KEY idx_metric_validation_subject (project_id, metric_id, metric_version, checked_at),
    KEY idx_metric_validation_version_id (project_id, metric_version_id),
    KEY idx_metric_validation_ready (project_id, metric_id, metric_version, result, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标定义校验证据';
