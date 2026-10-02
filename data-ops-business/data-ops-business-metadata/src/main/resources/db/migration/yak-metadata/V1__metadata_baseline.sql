-- 本文件由 scripts/db/consolidate-flyway-migrations.py 生成,请勿手改;要改结构请改脚本后重新生成。
-- 合并了 3 个版本化迁移(SQL 原文按版本号升序,未做逻辑改写)。
-- 被合并的文件:
--   V1__ [.] V1__create_metadata_tables.sql
--   V2__ [.] V2__create_metamodel_tables.sql
--   V3__ [.] V3__fill_lineage_asset_type_after_ticket_134.sql

-- Source: data-ops-business/data-ops-business-metadata/src/main/resources/db/migration/yak-metadata/V1__create_metadata_tables.sql
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

-- Source: data-ops-business/data-ops-business-metadata/src/main/resources/db/migration/yak-metadata/V2__create_metamodel_tables.sql
-- Metadata center V2 (ticket 128): the metamodel — "类型与字段是行，不是代码".
-- Adding an entity type = INSERT one yak_md_type_def row; adding a searchable custom field =
-- INSERT one yak_md_field_def row pointing at a free slot. Neither changes table shape.
-- DDL is verbatim plan §2.2. Slot columns themselves live on yak_metadata_asset
-- and are created by the LINEAGE chain (ticket 133, plan §2.3).

CREATE TABLE IF NOT EXISTS yak_md_type_def (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type_name VARCHAR(64) NOT NULL COMMENT '实体类型名或字段类型名，全局唯一，如 table/tableColumn/dataModel',
    category VARCHAR(16) NOT NULL COMMENT 'ENTITY|FIELD（对齐 OM type.json category）',
    name_space VARCHAR(64) NOT NULL DEFAULT 'custom' COMMENT 'OM: nameSpace；区分 platform/custom',
    display_name VARCHAR(128) NOT NULL,
    parent_types VARCHAR(256) NULL COMMENT '多父以逗号分隔；物理层级用 refersTo 类型对（plan §2.2 末）',
    fqn_pattern VARCHAR(256) NULL COMMENT 'ENTITY 必填：展示用 FQN 生成式，占位符由 MetadataKeyCodec 用属性上下文渲染',
    key_separator VARCHAR(8) NOT NULL DEFAULT '.' COMMENT '**只用于展示用 FQN 的拼接**；asset_key 的分隔符沿用既有 ":"，不由本列决定（§2.3 后果 6）',
    schema_def JSON NULL COMMENT '属性 schema（字段清单/型/必填/校验）。OM 存字符串，我们存 JSON 以可查',
    provider_bean VARCHAR(128) NULL COMMENT '对账副通道用的 EntityProvider bean 名（§3.2b）；采集型为 NULL',
    lineage_asset_type VARCHAR(32) NULL COMMENT 'ENTITY 必填：映射到 LineageAssetType 常量名，落库写入 asset_type；保存时用 values() 校验（§2.3 后果 1）。NULL=待 ticket 134 给该枚举加值，登记时报 49005',
    key_prefix VARCHAR(64) NULL COMMENT 'ENTITY 必填：asset_key 的固定前缀（如 modeling:model:、table:），§3.2b 硬约束 4 用它校验 provider 交出的键',
    collectible TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=由元数据模块主动采集(物理)；0=由源域写时登记 + 定时对账(投影，§3.2b)',
    search_default_weight FLOAT NOT NULL DEFAULT 1.0 COMMENT '统一检索的乘性权重（§4.6，表>列）',
    search_include_by_default TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=不进默认检索面（列实体设 0，§4.6）',
    icon_url VARCHAR(256) NULL,
    color VARCHAR(24) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|DEPRECATED：永不物理删，历史实体还要能解析',
    version INT NOT NULL DEFAULT 1,
    description VARCHAR(512) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_type_name (type_name),
    KEY idx_yak_md_type_category (category, status),
    KEY idx_yak_md_type_collect (collectible, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元模型：类型定义（实体类型 + 字段类型）';

CREATE TABLE IF NOT EXISTS yak_md_field_def (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type_id BIGINT NOT NULL COMMENT '所属实体类型 yak_md_type_def.id（category=ENTITY）',
    field_name VARCHAR(64) NOT NULL COMMENT '属性键，进 md_attributes 的 key；camelCase（OM entityName pattern 约定）',
    field_type VARCHAR(64) NOT NULL COMMENT '字段类型名，须解析到 category=FIELD 的 type_def',
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    required TINYINT(1) NOT NULL DEFAULT 0,
    is_null TINYINT(1) NOT NULL DEFAULT 1,
    base_type VARCHAR(24) NOT NULL COMMENT 'STRING|INTEGER|NUMBER|BOOLEAN|DATE|DATETIME|ENTITY_REFERENCE|JSON|ARRAY',
    entity_type_ref VARCHAR(64) NULL COMMENT 'base_type=ENTITY_REFERENCE 时允许的类型名，逗号分隔（OM customPropertyConfig.entityTypes）',
    constraint_def JSON NULL COMMENT '枚举集/正则/min-max/format（OM enumConfig/format/tableConfig）',
    default_value VARCHAR(256) NULL,
    searchable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=参与 q 全文检索；默认 0 防目录被低价值字段污染',
    match_type VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT 'text|exact|like|range',
    boost FLOAT NOT NULL DEFAULT 1.0 COMMENT '搜索权重（OM FieldBoost）',
    facetable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=进筛选聚合',
    storage_slot VARCHAR(32) NULL COMMENT '提槽到哪个生成列；NULL=只在 md_attributes 里不可查',
    ordinal INT NOT NULL DEFAULT 0,
    show_in_list TINYINT(1) NOT NULL DEFAULT 0,
    deprecated TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_md_field (type_id, field_name),
    KEY idx_yak_md_field_search (searchable, type_id),
    KEY idx_yak_md_field_slot (storage_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元模型：扩展字段定义';

-- ---------------------------------------------------------------------------
-- category=FIELD：字段类型也是数据（OM 的 propertyType 层）。
-- base_type 才是"怎么存、怎么筛"的唯一判别（借思想不借实现，plan §2.2 点 1）。
-- ---------------------------------------------------------------------------
INSERT INTO yak_md_type_def (type_name, category, name_space, display_name, description) VALUES
  ('STRING', 'FIELD', 'platform', '字符串', NULL),
  ('INTEGER', 'FIELD', 'platform', '整数', NULL),
  ('NUMBER', 'FIELD', 'platform', '数值', NULL),
  ('BOOLEAN', 'FIELD', 'platform', '布尔', NULL),
  ('DATE', 'FIELD', 'platform', '日期', NULL),
  ('DATETIME', 'FIELD', 'platform', '日期时间', NULL),
  ('ENTITY_REFERENCE', 'FIELD', 'platform', '实体引用', '指向目录内另一实体，按类型名校验'),
  ('JSON', 'FIELD', 'platform', '结构化', '稀有大字段应落 yak_md_asset_extension，不进主表属性袋'),
  ('ARRAY', 'FIELD', 'platform', '数组', NULL)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

-- ---------------------------------------------------------------------------
-- category=ENTITY：一期 8 类实体。
-- key_prefix / fqn_pattern **不是设计选择而是既成事实**——有存量的行必须逐字复用现网键（plan §2.3 后果 6）。
-- lineage_asset_type 三个 (新) 值（DATABASE_SERVICE/DATABASE/DOMAIN）依赖 ticket 134 给
-- LineageAssetType 加常量；在 134 落地前**先留 NULL**，登记该类型时报 49005 而不是
-- 写出一个让 lineage 读行 valueOf 抛异常的脏值（后果 1）。
-- ---------------------------------------------------------------------------
INSERT INTO yak_md_type_def
  (type_name, category, name_space, display_name, parent_types, fqn_pattern, key_separator,
   provider_bean, lineage_asset_type, key_prefix, collectible,
   search_default_weight, search_include_by_default, icon_url, color, status, description) VALUES
  ('databaseService', 'ENTITY', 'platform', '数据源服务', NULL,
   '{dataSourceName}', '.', NULL, NULL, 'datasource:', 1, 1.0, 1, NULL, '#5B8FF9', 'ACTIVE',
   '一个被采集的数据源；键 datasource:{dataSourceId}；lineage_asset_type 待 ticket 134'),
  ('database', 'ENTITY', 'platform', '数据库', 'databaseService',
   '{dataSourceName}.{databaseName}', '.', NULL, NULL, 'database:', 1, 1.0, 1, NULL, '#5AD8A6', 'ACTIVE',
   '键 database:{dataSourceId}:{db}；lineage_asset_type 待 ticket 134'),
  ('table', 'ENTITY', 'platform', '物理表', 'database',
   '{databaseName}.{schemaName}.{tableName}', '.', NULL, 'TABLE', 'table:', 1, 2.0, 1, NULL, '#5B8FF9', 'ACTIVE',
   '键 table:[unresolved:]{dsId}:{db}.{schema}.{tbl}，与 TableIdentityResolver 逐字同源（ticket 134）'),
  ('tableColumn', 'ENTITY', 'platform', '物理列', 'table',
   '{databaseName}.{schemaName}.{tableName}.{columnName}', '.', NULL, 'COLUMN', 'column:', 1, 0.2, 0, NULL, '#9270CA', 'ACTIVE',
   '列约为表 12.6 倍，默认不进检索面，以"命中 N 列"聚合露出（plan §4.6）'),
  ('dataModel', 'ENTITY', 'platform', '数据模型', NULL,
   '{layerCode}.{modelCode}', '.', 'modelEntityProvider', 'TABLE', 'modeling:model:', 0, 1.5, 1, NULL, '#F6BD16', 'ACTIVE',
   'modeling own，目录只存投影；键 modeling:model:{modelId} 由源域交出（现网 11 行认领）'),
  ('standardField', 'ENTITY', 'platform', '标准字段', 'domain',
   '{fieldCode}', '.', 'standardFieldEntityProvider', 'COLUMN', 'semantic:field:', 0, 1.0, 1, NULL, '#5AD8A6', 'ACTIVE',
   'semantic own；键 semantic:field:{fieldId}（现网 8 行认领，另有 148 条未进过图）'),
  ('domain', 'ENTITY', 'platform', '业务域', NULL,
   '{domainCode}', '.', 'domainEntityProvider', NULL, 'semantic:domain:', 0, 1.0, 1, NULL, '#5B8FF9', 'ACTIVE',
   'semantic own；键 semantic:domain:{domainId}；lineage_asset_type 待 ticket 134'),
  ('metric', 'ENTITY', 'platform', '指标', 'domain',
   '{metricCode}', '.', 'metricEntityProvider', 'METRIC', 'metric:', 0, 1.2, 1, NULL, '#F6BD16', 'ACTIVE',
   'metric own；键 metric:{metricId}（现网 4 行认领）')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), update_time = CURRENT_TIMESTAMP(6);

-- ---------------------------------------------------------------------------
-- 一期实体的目录投影/采集属性（plan §1.3 投影清单 + §3.2 SPI 字段）。
-- 提槽只为"要按它筛/搜"的字段；其余留在 md_attributes 里只展示。
-- 列清单里**不含**源域业务内容（模型列定义、指标公式、标准字典项一律实时读源域）。
--
-- 落点三态（plan §4.5：`searchable`/`facetable` 为 1 的字段必须有落点，否则保存即 49xxx）：
--   ① 生成列槽（storage_slot）  ② 目录固有列（MetadataNativeFilterColumns）  ③ 无落点 = 只展示
-- q 的**默认面**不依赖本表：目录固有列 name / display_name / summary 上的 ngram FULLTEXT 对每类实体
-- 都生效，所以"表名/列名/编码"这类**本身就是 name 列**的字段 searchable=0——标 1 只会重复计分，
-- 且它没有、也不需要槽（这正是"每类实体都占一个槽"会把 7 个槽第一天耗尽的原因）。
--
-- V1 基线槽位占用（7 个建一次、日后加第 8 个 = 一次 ALGORITHM=COPY 运维窗口，plan §2.4.1）：
--   s_str_1 = dataType      （tableColumn / standardField 共用：**同一属性语义**才允许共槽）
--   s_str_2 = databaseName  （table 的所属库；database 自己的库名走 name 列，不占槽）
--   s_str_3 = **空闲**      （留给"插一行 field_def 立刻可搜"的扩展位，plan §8 P0b）
--   s_num_1 = columnCount   s_num_2 = rowCountApprox   s_bool_1 = partitioned   s_date_1 = lastDdlTime
-- ---------------------------------------------------------------------------
INSERT INTO yak_md_field_def
  (type_id, field_name, field_type, display_name, required, is_null, base_type,
   constraint_def, searchable, match_type, boost, facetable, storage_slot, ordinal, show_in_list)
SELECT t.id, x.field_name, x.field_type, x.display_name, x.required, 1, x.base_type,
       NULL, x.searchable, x.match_type, x.boost, x.facetable, x.storage_slot, x.ordinal, x.show_in_list
FROM yak_md_type_def t
JOIN (
  SELECT 'databaseService' AS type_name, 'dataSourceName' AS field_name, 'STRING' AS field_type, '数据源名称' AS display_name,
         1 AS required, 'STRING' AS base_type, 0 AS searchable, 'text' AS match_type, 1.0 AS boost, 0 AS facetable, NULL AS storage_slot, 10 AS ordinal, 1 AS show_in_list
  UNION ALL SELECT 'database', 'databaseName', 'STRING', '库名', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 10, 1
  UNION ALL SELECT 'database', 'characterSet', 'STRING', '字符集', 0, 'STRING', 0, 'exact', 1.0, 0, NULL, 20, 0
  UNION ALL SELECT 'table', 'databaseName', 'STRING', '所属库', 1, 'STRING', 1, 'text', 1.0, 1, 's_str_2', 10, 1
  UNION ALL SELECT 'table', 'schemaName', 'STRING', '所属 schema', 0, 'STRING', 0, 'exact', 1.0, 0, NULL, 20, 0
  UNION ALL SELECT 'table', 'tableName', 'STRING', '表名', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 30, 1
  UNION ALL SELECT 'table', 'tableType', 'STRING', '表类型', 0, 'STRING', 0, 'exact', 1.0, 0, NULL, 40, 0
  UNION ALL SELECT 'table', 'tableComment', 'STRING', '表注释', 0, 'STRING', 0, 'text', 0.8, 0, NULL, 50, 1
  UNION ALL SELECT 'table', 'columnCount', 'INTEGER', '列数', 0, 'INTEGER', 0, 'range', 1.0, 0, 's_num_1', 60, 1
  UNION ALL SELECT 'table', 'rowCountApprox', 'INTEGER', '行数(近似)', 0, 'INTEGER', 0, 'range', 1.0, 0, 's_num_2', 70, 1
  UNION ALL SELECT 'table', 'partitioned', 'BOOLEAN', '是否分区表', 0, 'BOOLEAN', 0, 'exact', 1.0, 1, 's_bool_1', 80, 0
  UNION ALL SELECT 'table', 'lastDdlTime', 'DATETIME', '最后 DDL 时间', 0, 'DATETIME', 0, 'range', 1.0, 0, 's_date_1', 90, 0
  UNION ALL SELECT 'tableColumn', 'columnName', 'STRING', '列名', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 10, 1
  UNION ALL SELECT 'tableColumn', 'dataType', 'STRING', '数据类型', 1, 'STRING', 1, 'exact', 1.0, 1, 's_str_1', 20, 1
  UNION ALL SELECT 'tableColumn', 'columnSize', 'INTEGER', '长度', 0, 'INTEGER', 0, 'range', 1.0, 0, NULL, 30, 0
  UNION ALL SELECT 'tableColumn', 'decimalDigits', 'INTEGER', '小数位', 0, 'INTEGER', 0, 'range', 1.0, 0, NULL, 40, 0
  UNION ALL SELECT 'tableColumn', 'nullable', 'BOOLEAN', '可空', 0, 'BOOLEAN', 0, 'exact', 1.0, 0, NULL, 50, 1
  UNION ALL SELECT 'tableColumn', 'ordinalPosition', 'INTEGER', '列序', 0, 'INTEGER', 0, 'range', 1.0, 0, NULL, 60, 0
  UNION ALL SELECT 'tableColumn', 'primaryKey', 'BOOLEAN', '主键', 0, 'BOOLEAN', 0, 'exact', 1.0, 0, NULL, 70, 1
  UNION ALL SELECT 'tableColumn', 'columnComment', 'STRING', '列注释', 0, 'STRING', 0, 'text', 0.8, 0, NULL, 80, 1
  UNION ALL SELECT 'dataModel', 'modelCode', 'STRING', '模型编码', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 10, 1
  UNION ALL SELECT 'dataModel', 'layerCode', 'STRING', '分层', 0, 'STRING', 0, 'exact', 1.0, 1, NULL, 20, 1
  UNION ALL SELECT 'dataModel', 'publishState', 'STRING', '发布状态(源域)', 0, 'STRING', 0, 'exact', 1.0, 0, NULL, 30, 0
  UNION ALL SELECT 'standardField', 'fieldCode', 'STRING', '标准编码', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 10, 1
  UNION ALL SELECT 'standardField', 'dataType', 'STRING', '标准类型', 0, 'STRING', 1, 'exact', 1.0, 1, 's_str_1', 20, 1
  UNION ALL SELECT 'domain', 'domainCode', 'STRING', '域编码', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 10, 1
  UNION ALL SELECT 'metric', 'metricCode', 'STRING', '指标编码', 1, 'STRING', 0, 'text', 1.0, 0, NULL, 10, 1
  UNION ALL SELECT 'metric', 'unit', 'STRING', '单位', 0, 'STRING', 0, 'exact', 1.0, 0, NULL, 20, 0
) x ON x.type_name = t.type_name
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), update_time = CURRENT_TIMESTAMP(6);

-- Source: data-ops-business/data-ops-business-metadata/src/main/resources/db/migration/yak-metadata/V3__fill_lineage_asset_type_after_ticket_134.sql
-- Metadata center V3 (ticket 134): 补齐三行 lineage_asset_type。
--
-- V2 把 databaseService / database / domain 三行的 lineage_asset_type 留成 NULL，当时的口径是
-- "LineageAssetType 还没有这三个常量"（后果 1：asset_type NOT NULL 且 lineage 读行逐行 valueOf，
-- 写进一个枚举里没有的名字，行存得下，炸的是别人的血缘查询）。134 已给该枚举补上
-- DATABASE_SERVICE / DATABASE / DOMAIN，这三行随之落地，NULL 的含义到此结束。
--
-- description 里"待 ticket 134"一并清掉：留着会让下一个人以为还欠一张票。
-- 幂等：按唯一键 uk_yak_md_type_name 定位，重复执行结果一致；库里没有这三行时影响 0 行、不报错。

UPDATE yak_md_type_def
   SET lineage_asset_type = 'DATABASE_SERVICE',
       description = '一个被采集的数据源；键 datasource:{dataSourceId}'
 WHERE type_name = 'databaseService';

UPDATE yak_md_type_def
   SET lineage_asset_type = 'DATABASE',
       description = '键 database:{dataSourceId}:{db}'
 WHERE type_name = 'database';

UPDATE yak_md_type_def
   SET lineage_asset_type = 'DOMAIN',
       description = 'semantic own；键 semantic:domain:{domainId}'
 WHERE type_name = 'domain';
