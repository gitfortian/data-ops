-- Yak Ops Offline Sync first-release baseline.
--
-- This file represents the final schema state for the first formal release. Development-time
-- expand/backfill/contract migrations are intentionally squashed because the pre-release database
-- is disposable. After the first formal release this V1 becomes immutable.
--
-- Definition / Batch / Attempt / Event / Cursor lifecycle is owned by application code; no
-- physical FK constraints are created here.

CREATE TABLE IF NOT EXISTS yak_offline_job_definition (
    id BIGINT NOT NULL COMMENT '任务定义 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    job_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    job_desc VARCHAR(1000) NULL COMMENT '任务描述',
    mode VARCHAR(32) NOT NULL COMMENT 'GUIDE_SINGLE/GUIDE_MULTI',
    definition_json LONGTEXT NOT NULL COMMENT '当前可编辑任务定义',
    job_spec_json LONGTEXT NULL COMMENT '当前可执行 Link-Up JobSpec',
    config_digest CHAR(64) NULL COMMENT 'JobSpec SHA-256 摘要',
    release_state VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    source_type VARCHAR(64) NULL,
    sink_type VARCHAR(64) NULL,
    source_datasource_id BIGINT NULL,
    sink_datasource_id BIGINT NULL,
    source_table TEXT NULL,
    sink_table TEXT NULL,
    schedule_json LONGTEXT NULL COMMENT '任务级调度原始配置',
    schedule_enabled TINYINT(1) NOT NULL DEFAULT 0,
    cron_expression VARCHAR(128) NULL,
    retry_max_attempts INT NOT NULL DEFAULT 1,
    retry_backoff_seconds INT NOT NULL DEFAULT 60,
    schedule_last_fire_time DATETIME(3) NULL,
    schedule_next_fire_time DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0 COMMENT '当前定义版本号',
    last_execution_id BIGINT NULL,
    last_engine_job_id VARCHAR(128) NULL,
    last_job_status VARCHAR(32) NULL,
    last_error_message TEXT NULL,
    last_duration_millis BIGINT NULL,
    last_read_row_count BIGINT NULL,
    last_qps DOUBLE NULL,
    last_sync_bytes BIGINT NULL,
    last_start_time DATETIME(3) NULL,
    last_end_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_project_job_name (project_id, job_name),
    KEY idx_yak_offline_release_state (release_state),
    KEY idx_yak_offline_job_status (last_job_status),
    KEY idx_yak_offline_schedule (schedule_enabled, cron_expression),
    KEY idx_yak_offline_schedule_next (schedule_enabled, schedule_next_fire_time),
    KEY idx_yak_offline_update_time (update_time),
    KEY idx_yak_offline_project_release (project_id, release_state, update_time),
    KEY idx_yak_offline_project_schedule (project_id, schedule_enabled, cron_expression),
    KEY idx_yak_offline_project_schedule_next
      (project_id, schedule_enabled, schedule_next_fire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步任务定义';

CREATE TABLE IF NOT EXISTS yak_offline_batch_execution (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务批次 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    job_definition_id BIGINT NOT NULL COMMENT '离线同步任务 ID',
    batch_key VARCHAR(512) NOT NULL COMMENT '任务内稳定业务批次身份',
    trigger_type VARCHAR(32) NOT NULL COMMENT 'MANUAL/SCHEDULE/WORKFLOW/BACKFILL',
    batch_scope_type VARCHAR(32) NOT NULL COMMENT 'FULL_SELECTION/DATA_WINDOW/PARTITION_SCOPE/CURSOR_RANGE',
    batch_scope_value LONGTEXT NOT NULL COMMENT 'BatchScope canonical value',
    batch_scope_fingerprint CHAR(64) NOT NULL COMMENT 'BatchScope SHA-256 fingerprint',
    definition_snapshot_json LONGTEXT NOT NULL COMMENT 'Batch 创建时冻结的 SyncDefinition 快照',
    definition_revision INT NOT NULL COMMENT 'Batch 创建时的定义修订号',
    retry_max_attempts INT NOT NULL COMMENT 'Batch 创建时冻结的最大 Attempt 数',
    retry_backoff_seconds INT NOT NULL COMMENT 'Batch 创建时冻结的 Retry backoff 秒数',
    config_digest CHAR(64) NOT NULL COMMENT '冻结配置摘要',
    logical_job_spec_json LONGTEXT NULL COMMENT 'Batch 创建时冻结的不含凭据逻辑 JobSpec',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/WAITING_RETRY/SUCCEEDED/FAILED/CANCELED/UNKNOWN',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_batch_task_key (job_definition_id, batch_key),
    KEY idx_yak_offline_batch_task_status (job_definition_id, status, id),
    KEY idx_yak_offline_batch_scope (batch_scope_fingerprint),
    KEY idx_yak_offline_batch_trigger_status (trigger_type, status, id),
    KEY idx_yak_offline_batch_project_status (project_id, status, update_time),
    KEY idx_yak_offline_batch_project_task (project_id, job_definition_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步业务批次';

CREATE TABLE IF NOT EXISTS yak_offline_job_execution (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行实例 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    job_definition_id BIGINT NOT NULL,
    batch_id BIGINT NULL COMMENT '所属业务批次 ID',
    definition_version INT NOT NULL DEFAULT 1,
    engine_base_url VARCHAR(500) NOT NULL COMMENT '本次执行使用的 YAML 地址快照',
    engine_job_id VARCHAR(128) NULL,
    external_execution_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    worker_instance_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    state_version BIGINT NOT NULL DEFAULT 1,
    attempt_no INT NOT NULL DEFAULT 1,
    trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    retry_from_execution_id BIGINT NULL,
    cancellation_requested TINYINT(1) NOT NULL DEFAULT 0,
    retry_created TINYINT(1) NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NULL,
    config_digest CHAR(64) NULL,
    definition_snapshot_json LONGTEXT NOT NULL COMMENT '本次执行的任务定义快照',
    submitted_config LONGTEXT NOT NULL COMMENT '本次执行的逻辑 JobSpec 快照',
    engine_snapshot_json LONGTEXT NULL,
    error_message TEXT NULL,
    source_record_count BIGINT NOT NULL DEFAULT 0,
    sink_attempted_record_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Sink 尝试写入记录数',
    sink_success_record_count BIGINT NOT NULL DEFAULT 0,
    sink_committed_record_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Sink 已提交记录数',
    source_read_bytes BIGINT NOT NULL DEFAULT 0,
    sink_written_bytes BIGINT NOT NULL DEFAULT 0,
    source_average_qps DOUBLE NOT NULL DEFAULT 0 COMMENT 'Source 平均读取 QPS',
    sink_average_qps DOUBLE NOT NULL DEFAULT 0 COMMENT 'Sink 平均写入 QPS',
    failed_record_count BIGINT NOT NULL DEFAULT 0 COMMENT '失败记录数',
    skipped_record_count BIGINT NOT NULL DEFAULT 0 COMMENT '跳过记录数',
    database_commit_millis BIGINT NOT NULL DEFAULT 0 COMMENT '数据库提交耗时，毫秒',
    sql_execution_millis BIGINT NOT NULL DEFAULT 0 COMMENT 'SQL 执行耗时，毫秒',
    qps DOUBLE NOT NULL DEFAULT 0,
    duration_millis BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    start_time DATETIME(3) NULL,
    end_time DATETIME(3) NULL,
    last_sync_time DATETIME(3) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_external_execution (external_execution_id),
    UNIQUE KEY uk_yak_offline_idempotency (idempotency_key),
    KEY idx_yak_offline_execution_definition (job_definition_id, id),
    KEY idx_yak_offline_execution_status (status, last_sync_time),
    KEY idx_yak_offline_execution_retry (retry_created, next_retry_time),
    KEY idx_yak_offline_execution_batch (batch_id, attempt_no, id),
    KEY idx_yak_offline_execution_project_status (project_id, status, last_sync_time),
    KEY idx_yak_offline_execution_project_task (project_id, job_definition_id, id),
    KEY idx_yak_offline_execution_created (create_time, id),
    KEY idx_yak_offline_execution_project_created (project_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步执行实例';

CREATE TABLE IF NOT EXISTS yak_offline_execution_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    execution_id BIGINT NOT NULL,
    state_version BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    event_type VARCHAR(64) NOT NULL,
    message TEXT NULL,
    payload_json LONGTEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_yak_offline_event_execution (execution_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步执行状态事件';

CREATE TABLE IF NOT EXISTS yak_offline_sync_cursor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Cursor persistence ID',
    job_definition_id BIGINT NOT NULL COMMENT 'OfflineSyncTask ID',
    cursor_id VARCHAR(128) NOT NULL COMMENT 'Task 内稳定 Cursor identity',
    source_column VARCHAR(256) NOT NULL COMMENT 'Cursor 对应 source column route',
    position_value VARCHAR(1024) NOT NULL COMMENT '当前已提交 Cursor 位置',
    last_succeeded_batch_id BIGINT NULL COMMENT '最近一次推进 Cursor 的 SUCCEEDED Batch ID',
    state_version BIGINT NOT NULL DEFAULT 1 COMMENT 'Cursor CAS version',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_cursor_task_id (job_definition_id, cursor_id),
    KEY idx_yak_offline_cursor_last_batch (last_succeeded_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步 Task Cursor';
