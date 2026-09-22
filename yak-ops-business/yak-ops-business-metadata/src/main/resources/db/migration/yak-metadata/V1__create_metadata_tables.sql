-- Metadata center V1 (ticket 112): the 7 tables this module owns outright.
-- Metamodel tables (yak_md_type_def / yak_md_field_def) are V2 (ticket 128);
-- the shared catalog columns on yak_metadata_asset are ALTERed by the LINEAGE chain (ticket 133).
-- See docs/data-metadata/plan.md §2/§3/§6 and module ARCHITECTURE.md (steward contract).

-- §2.3 稀有大字段侧表：主键指向共表的主键，无物理外键（§0.9）
CREATE TABLE IF NOT EXISTS yak_md_asset_extension (
    asset_id BIGINT NOT NULL COMMENT 'yak_metadata_asset.id；无物理外键，跨模块松散引用',
    extension VARCHAR(128) NOT NULL COMMENT '点分名 <typeName>.<fieldName>',
    json_schema VARCHAR(256) NULL COMMENT '产该值的类型版本，便于排查漂移',
    `json` JSON NOT NULL COMMENT '稀有大字段值（主表保持窄）',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (asset_id, extension),
    KEY idx_yak_md_ext_name (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='稀有大字段侧表（主表保持窄）';

-- §3.1/§3.7：物理采集与投影对账共用这一张任务表，区别只在 provider_type
CREATE TABLE IF NOT EXISTS yak_md_collect_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    job_code VARCHAR(64) NOT NULL COMMENT '任务编码，自动生成可改，项目内唯一',
    job_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(16) NOT NULL COMMENT 'HARVESTED=物理采集 / REGISTERED=投影对账（§3.1）',
    type_name VARCHAR(64) NULL COMMENT 'REGISTERED 必填：对账的实体类型；HARVESTED 为 NULL',
    data_source_id BIGINT NULL COMMENT 'HARVESTED 必填：数据源 id（松散引用，无外键）',
    database_name VARCHAR(128) NULL COMMENT 'HARVESTED 作用域：库名，NULL=该源全部可采集库',
    schema_name VARCHAR(128) NULL COMMENT 'HARVESTED 作用域：schema',
    table_pattern VARCHAR(255) NULL COMMENT '表名匹配式（% 通配），NULL=全部',
    collect_columns TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=采到列级（一期默认开，§11.1.1）',
    cron_expression VARCHAR(64) NOT NULL COMMENT '调度 cron（系统默认时区）',
    enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=启用；新建默认关（§0.13）',
    dry_run_passed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=最近一次 dry-run 通过；未通过不可启用',
    collapse_threshold_pct INT NOT NULL DEFAULT 30 COMMENT '坍塌熔断阈值：单轮 GONE 占比超此值整轮判 SUSPECT（§3.4）',
    missing_rounds INT NOT NULL DEFAULT 2 COMMENT '连续缺失几轮才判 GONE（§3.4，与 asset 7 天窗口同口径）',
    last_run_id BIGINT NULL COMMENT '最近一次 yak_md_collect_run.id',
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_job_code (project_id, job_code),
    KEY idx_yak_md_job_enabled (project_id, enabled, provider_type, deleted),
    KEY idx_yak_md_job_source (data_source_id, database_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集/对账任务（两条入口共用）';

-- §3.3：比对结果四类计数 + §3.4 的 SUSPECT/FAILED；dry-run 与游标水位同表
CREATE TABLE IF NOT EXISTS yak_md_collect_run (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL COMMENT 'yak_md_collect_job.id',
    provider_type VARCHAR(16) NOT NULL COMMENT '冗余自任务，便于按通道统计',
    trigger_type VARCHAR(16) NOT NULL COMMENT 'SCHEDULE|MANUAL|DRY_RUN',
    dry_run TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=只算不写（启用前置条件，§0.13）',
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING|SUCCESS|FAILED|SUSPECT',
    cnt_total INT NOT NULL DEFAULT 0 COMMENT '本轮 seen 集大小（空集是熔断信号，不是 0 删除的理由）',
    cnt_new INT NOT NULL DEFAULT 0,
    cnt_changed INT NOT NULL DEFAULT 0,
    cnt_unchanged INT NOT NULL DEFAULT 0,
    cnt_gone INT NOT NULL DEFAULT 0 COMMENT 'SUSPECT 轮恒为 0（不落任何 GONE）',
    cnt_partial_failed INT NOT NULL DEFAULT 0 COMMENT '单表 listColumns 失败/为空记 PARTIAL，不静默当空表（§3.2 边界）',
    cursor_watermark VARCHAR(255) NULL COMMENT '中断续跑水位（游标分页的上一页末键）',
    scope_snapshot JSON NULL COMMENT '本轮作用域快照（防任务改配置后无法复盘）',
    error_message VARCHAR(2000) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    duration_ms BIGINT NULL,
    created_by VARCHAR(64) NOT NULL COMMENT '触发人；调度触发为 system',
    PRIMARY KEY (id),
    KEY idx_yak_md_run_job (project_id, job_id, started_at),
    KEY idx_yak_md_run_status (project_id, status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集/对账运行历史（不新开第三张历史表）';

-- §3.2c 写时登记 outbox：唯一键建在"变更"上而不是"状态"上
-- （键含 status 会让一条实体 DONE 之后再也无法重新登记；NULL 在 MySQL 唯一索引里彼此相异，故所有键列 NOT NULL）
CREATE TABLE IF NOT EXISTS yak_md_register_retry (
    task_id CHAR(36) NOT NULL COMMENT 'UUID；主键即幂等令牌',
    project_id BIGINT NOT NULL COMMENT '源域上下文；worker 执行前用它恢复 ProjectContext',
    type_name VARCHAR(64) NOT NULL COMMENT '= yak_md_type_def.type_name',
    asset_key VARCHAR(512) NOT NULL COMMENT '源域交出的键（§3.2b 硬约束 4）',
    source_id VARCHAR(200) NOT NULL COMMENT '源域内主键，重放时回查用',
    operation VARCHAR(16) NOT NULL COMMENT 'REGISTER|UNREGISTER',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING|IN_PROGRESS|DONE|DEAD',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_time DATETIME(6) NOT NULL COMMENT '退避后的下次执行时间',
    last_error VARCHAR(2000) NULL,
    payload JSON NULL COMMENT '整份 RegisterCommand；重放时不再回查源域，避免时序依赖',
    source_updated_at DATETIME(6) NOT NULL COMMENT '本次变更的源侧时间，兼作去重令牌',
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_yak_md_retry_change (project_id, type_name, asset_key, source_updated_at) COMMENT '同一次变更只排一条：并发重复登记天然合并',
    KEY idx_yak_md_retry_due (status, next_attempt_time),
    KEY idx_yak_md_retry_project_due (project_id, status, next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='写时登记的失败重试队列（outbox）';

-- §2.6 实体级变更流水：append-only，代码层不给 UPDATE/DELETE 路径（清理走 changed_at 冷数据归档）
CREATE TABLE IF NOT EXISTS yak_md_change (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL COMMENT 'yak_metadata_asset.id',
    asset_key VARCHAR(512) NOT NULL COMMENT '冗余键：行被撤销后仍可追溯',
    type_name VARCHAR(64) NOT NULL COMMENT '实体类型名（API 层同名）',
    provider_type VARCHAR(16) NOT NULL COMMENT 'HARVESTED|REGISTERED',
    change_type VARCHAR(24) NOT NULL COMMENT 'NEW|CHANGED|GONE|REVIVED|ATTR_CHANGED|STATUS_CHANGED|LABEL_CHANGED',
    field_name VARCHAR(64) NULL COMMENT '列级变更时的属性名；实体级为 NULL',
    before_value VARCHAR(1024) NULL,
    after_value VARCHAR(1024) NULL,
    detail JSON NULL COMMENT '差异明细（列增删等结构化留痕）',
    source_updated_at DATETIME(6) NULL COMMENT '源侧变更时间（不是本行写入时间）',
    collect_run_id BIGINT NULL COMMENT '由哪一轮采集/对账产生；人工变更为 NULL',
    changed_by VARCHAR(64) NOT NULL COMMENT '人写用户名，机器写 system',
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_yak_md_change_asset (project_id, asset_id, changed_at),
    KEY idx_yak_md_change_time (changed_at),
    KEY idx_yak_md_change_run (collect_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据变更流水（append-only）';

-- §6.1 标签溯源：label_type × state 双维度；认证 = 特定 label_code + expires_at 非空
CREATE TABLE IF NOT EXISTS yak_md_label (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL COMMENT 'yak_metadata_asset.id；表/列/模型/标准字段一律同构引用',
    label_code VARCHAR(64) NOT NULL COMMENT '标签字典码，复用 asset 标签体系',
    label_type VARCHAR(16) NOT NULL COMMENT 'MANUAL|AUTOMATED|PROPAGATED|DERIVED',
    state VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'SUGGESTED|CONFIRMED：机器/继承默认 SUGGESTED，等人工确认',
    applied_by VARCHAR(64) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    reason VARCHAR(512) NULL COMMENT '为什么打这个标（回答"凭什么是 PII"）',
    derived_from BIGINT NULL COMMENT 'PROPAGATED 时指向父标签行；不建通用传导框架（§4.3）',
    expires_at DATETIME(6) NULL COMMENT '仅 CERTIFICATION 用；不进 content_hash（§3.3）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_label (project_id, asset_id, label_code),
    KEY idx_yak_md_label_asset (asset_id),
    KEY idx_yak_md_label_state (project_id, state, label_type),
    KEY idx_yak_md_label_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据标签（含溯源与认证）';

-- §6.2 治理待办：open_marker 是"只允许一条开放行"的唯一可行形状
-- （UNIQUE(…, resolved_at) 语义相反：NULL 彼此相异 → 开放行可无限重复、反倒挤掉历史行；
--   生成列方案被 MySQL 拒：3109 Generated column cannot refer to auto-increment column）
CREATE TABLE IF NOT EXISTS yak_md_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL COMMENT 'FILL_COMMENT|CONFIRM_LABEL|FIX_CONFORMANCE|REVIEW_GONE',
    asset_id BIGINT NOT NULL COMMENT 'yak_metadata_asset.id；对所有实体类型同构',
    entity_status VARCHAR(24) NOT NULL DEFAULT 'Unprocessed' COMMENT '7 值蒸馏 §1.1；默认 Unprocessed 区分"没人看过"',
    assignee VARCHAR(64) NULL,
    created_by VARCHAR(64) NOT NULL,
    due_date DATE NULL,
    resolved_at DATETIME(6) NULL,
    resolve_note VARCHAR(512) NULL,
    open_marker BIGINT NOT NULL DEFAULT 0 COMMENT '去重位：未办结恒 0（互斥），办结时由服务写入自身 id（彼此相异）',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_task_open (project_id, task_type, asset_id, open_marker),
    CHECK ((open_marker = 0) = (resolved_at IS NULL)),
    KEY idx_yak_md_task_assignee (assignee, entity_status, due_date),
    KEY idx_yak_md_task_project (project_id, entity_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据治理待办';
