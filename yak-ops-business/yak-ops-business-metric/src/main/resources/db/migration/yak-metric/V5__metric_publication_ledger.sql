-- F-005-C: durable Metric publication truth.
-- Publication events are append-only. The active pointer is mutable current-state only and never
-- replaces immutable MetricVersion or the publication ledger.

CREATE TABLE IF NOT EXISTS yak_metric_publication_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '发布事件主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标 ID',
    metric_version_id BIGINT NOT NULL COMMENT 'yak_metric_version.id,不可变版本身份',
    metric_version INT NOT NULL COMMENT '发布绑定的版本号',
    snapshot_digest VARCHAR(64) NOT NULL COMMENT '发布时 immutable snapshot 的 SHA-256',
    event_type VARCHAR(16) NOT NULL COMMENT '事件:PUBLISHED/WITHDRAWN',
    subject_publication_id BIGINT NULL COMMENT 'WITHDRAWN 所撤回的 PUBLISHED 事件 ID',
    readiness_json JSON NULL COMMENT 'PUBLISHED 时冻结的 publication gate evidence',
    acted_by VARCHAR(64) NOT NULL COMMENT '执行人',
    acted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '事件时间',
    PRIMARY KEY (id),
    KEY idx_metric_publication_metric (project_id, metric_id, acted_at),
    KEY idx_metric_publication_version (project_id, metric_version_id),
    KEY idx_metric_publication_subject (project_id, subject_publication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标发布事件账本';

CREATE TABLE IF NOT EXISTS yak_metric_active_publication (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '当前发布指针主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    metric_id BIGINT NOT NULL COMMENT '指标 ID',
    publication_event_id BIGINT NOT NULL COMMENT '当前生效的 PUBLISHED 事件 ID',
    metric_version_id BIGINT NOT NULL COMMENT '当前生效的 immutable MetricVersion ID',
    metric_version INT NOT NULL COMMENT '当前生效版本号',
    snapshot_digest VARCHAR(64) NOT NULL COMMENT '当前生效 snapshot SHA-256',
    published_by VARCHAR(64) NOT NULL COMMENT '最近显式发布人',
    published_at DATETIME(6) NOT NULL COMMENT '最近显式发布时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_active_publication (project_id, metric_id),
    KEY idx_metric_active_publication_event (project_id, publication_event_id),
    KEY idx_metric_active_publication_version (project_id, metric_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标当前生效发布指针';
