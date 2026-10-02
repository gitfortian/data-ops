-- 本文件由 scripts/db/consolidate-flyway-migrations.py 生成,请勿手改;要改结构请改脚本后重新生成。
-- 合并了 16 个版本化迁移(SQL 原文按版本号升序,未做逻辑改写)。
-- 被合并的文件:
--   V1__ [.] V1__baseline_mdm.sql
--   V2__ [.] V2__create_mdm_entity.sql
--   V3__ [.] V3__create_mdm_attribute.sql
--   V4__ [.] V4__create_mdm_source.sql
--   V6__ [.] V6__create_mdm_record.sql
--   V7__ [.] V7__create_mdm_clean_rule.sql
--   V8__ [.] V8__create_mdm_merge_log.sql
--   V9__ [.] V9__create_mdm_distribution.sql
--   V10__ [.] V10__create_mdm_subscription.sql
--   V11__ [.] V11__create_mdm_change.sql
--   V12__ [.] V12__add_mdm_source_field_mapping.sql
--   V13__ [.] V13__create_mdm_collect_link.sql
--   V14__ [.] V14__drop_mdm_source_legacy_collect_cols.sql
--   V15__ [.] V15__mdm_change_instance_and_version.sql
--   V16__ [.] V16__create_mdm_dedup_ignore.sql
--   V17__ [.] V17__add_mdm_record_attribute_overrides.sql

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V1__baseline_mdm.sql
-- MDM module baseline.
--
-- Ticket 50 establishes the self-owned Flyway boundary only:
--   location: classpath:db/migration/yak-mdm
--   history:  flyway_schema_history_mdm
-- Domain tables are introduced by their owning tickets (entity: 51, attribute:
-- 52, source: 53/54, record: 55, clean rule: 56/57, distribution: 58, change:
-- 60) and must never be edited after merge. See ARCHITECTURE.md for the
-- planned table ownership.
SELECT 1;

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V2__create_mdm_entity.sql
-- Master data entity (ticket 51). entity_code is the project-scoped stable
-- key, immutable after creation. Attribute (52) / source (53) reference
-- checks for deletion arrive with their owning tickets.

CREATE TABLE IF NOT EXISTS yak_mdm_entity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_code VARCHAR(64) NOT NULL COMMENT '实体编码,项目内唯一,创建后不可改',
    entity_name VARCHAR(128) NOT NULL COMMENT '实体名称',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用',
    owner VARCHAR(64) NULL COMMENT '负责人',
    description VARCHAR(512) NULL COMMENT '描述',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_entity (project_id, entity_code),
    KEY idx_yak_mdm_entity_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据实体';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V3__create_mdm_attribute.sql
-- Master data attribute (ticket 52). attr_code is unique within an entity,
-- immutable after creation. Type/unit/security reference semantic standards by
-- id (loose id, no FK); code-set reference stores the code-set code. Collection
-- mapping (54) / clean rule (56) reference checks for deletion arrive with
-- their owning tickets.

CREATE TABLE IF NOT EXISTS yak_mdm_attribute (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    attr_code VARCHAR(64) NOT NULL COMMENT '属性编码,实体内唯一,创建后不可改',
    attr_name VARCHAR(128) NOT NULL COMMENT '属性名称',
    attr_type VARCHAR(16) NOT NULL COMMENT '属性角色:PK/ATTR/RELATION',
    data_type VARCHAR(64) NULL COMMENT '数据类型(快照)',
    std_type_id BIGINT NULL COMMENT '类型标准引用(松散 ID)',
    std_unit_id BIGINT NULL COMMENT '单位标准引用(松散 ID)',
    std_code_set_code VARCHAR(64) NULL COMMENT '码值标准引用(码集编码)',
    std_security_id BIGINT NULL COMMENT '安全标准引用(松散 ID)',
    is_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填',
    business_desc VARCHAR(512) NULL COMMENT '业务描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_attribute (project_id, entity_id, attr_code),
    KEY idx_yak_mdm_attribute_entity (project_id, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据属性';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V4__create_mdm_source.sql
-- Master data source binding (ticket 53). A source = datasource table bound
-- to a master data entity, role MAIN (primary) / AUXILIARY (supplementary,
-- registered now, participates in collection from 54). Collection config
-- columns (field_mapping/collect_mode/collect_freq) arrive with ticket 54.

CREATE TABLE IF NOT EXISTS yak_mdm_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    datasource_id BIGINT NOT NULL COMMENT '数据源(松散 ID)',
    source_database VARCHAR(128) NULL COMMENT '源库',
    source_schema VARCHAR(128) NULL COMMENT '源模式',
    source_table VARCHAR(128) NOT NULL COMMENT '源表',
    source_role VARCHAR(16) NOT NULL DEFAULT 'MAIN' COMMENT '角色:MAIN/AUXILIARY',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态:ENABLED/DISABLED',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_source (project_id, entity_id, datasource_id, source_database, source_schema, source_table),
    KEY idx_yak_mdm_source_entity (project_id, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据来源';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V6__create_mdm_record.sql
-- Master data record (ticket 55a). The unified master table: master_id is the
-- cross-system stable id (D3), source_ids records each source system's raw id
-- (D4). Rows are written by master-data processing tasks executed in
-- data-development (plan A, D-M11); MDM reads them for display/governance.
--
-- status: ACTIVE visible / MERGED merged-away (kept for traceability) /
-- DELETED soft-deleted. version increments on each upsert.

CREATE TABLE IF NOT EXISTS yak_mdm_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    master_id VARCHAR(64) NOT NULL COMMENT '主数据唯一 ID(跨系统统一)',
    attributes JSON NOT NULL COMMENT '属性值(键=属性编码)',
    source_ids JSON NOT NULL COMMENT '各系统原始 ID:{datasourceId: 原始ID}',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE/MERGED/DELETED',
    version INT NOT NULL DEFAULT 1 COMMENT '版本,每次更新自增',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_record (project_id, entity_id, master_id),
    KEY idx_yak_mdm_record_entity (project_id, entity_id),
    KEY idx_yak_mdm_record_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V7__create_mdm_clean_rule.sql
-- Master data cleansing rule (ticket 56). Rule configuration is entity-level:
-- rule_type is DEDUP for now (57 adds STANDARDIZE/COMPLETE); rule_expr is a JSON
-- document describing the match fields and how they combine:
--   {"fields":[{"attrCode":"mobile","matchType":"EXACT"}],"condition":"AND"}
-- matchType: EXACT (raw equality) / FUZZY (trim + lowercase normalized equality).
-- enabled toggles participation in dedup discovery; sort_order keeps UI order.

CREATE TABLE IF NOT EXISTS yak_mdm_clean_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    rule_type VARCHAR(16) NOT NULL DEFAULT 'DEDUP' COMMENT '规则类型:DEDUP/STANDARDIZE/COMPLETE',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称(实体内唯一)',
    rule_expr JSON NOT NULL COMMENT '规则表达式(JSON):匹配字段与组合条件',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_clean_rule_name (project_id, entity_id, rule_name),
    KEY idx_yak_mdm_clean_rule_entity (project_id, entity_id),
    KEY idx_yak_mdm_clean_rule_type (project_id, rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据清洗规则';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V8__create_mdm_merge_log.sql
-- Master data merge history (ticket 56). Every merge is an explicit user
-- operation: the master record absorbs attributes/source_ids and the merged
-- records are marked MERGED (kept for traceability). This table records the
-- audit trail: which rule produced the group, which record survived, which
-- records were merged away, and who performed the merge.

CREATE TABLE IF NOT EXISTS yak_mdm_merge_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    rule_id BIGINT NULL COMMENT '触发合并的去重规则(可空)',
    master_record_id BIGINT NOT NULL COMMENT '保留的主记录',
    merged_record_ids JSON NOT NULL COMMENT '被合并记录 ID 列表',
    result VARCHAR(512) NOT NULL COMMENT '合并结果摘要',
    created_by VARCHAR(64) NOT NULL COMMENT '操作人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_yak_mdm_merge_log_entity (project_id, entity_id),
    KEY idx_yak_mdm_merge_log_master (project_id, master_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据合并日志';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V9__create_mdm_distribution.sql
-- Ticket 58: 主数据分发配置(实体级,目标系统+方式+频率)
CREATE TABLE IF NOT EXISTS `yak_mdm_distribution` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`           BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`            BIGINT       NOT NULL COMMENT '主数据实体',
  `target_system`        VARCHAR(64)  NOT NULL COMMENT '目标系统编码',
  `target_name`          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '目标系统名称',
  `distribute_mode`      VARCHAR(16)  NOT NULL COMMENT '分发方式:API/MESSAGE/FILE',
  `distribute_freq`      VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '分发频率:MANUAL/DAILY/HOURLY',
  `distribute_scope`     VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT '分发范围:FULL/INCREMENTAL',
  `status`               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/ACTIVE/DISABLED',
  `last_distribute_time` DATETIME     NULL COMMENT '最近分发时间',
  `last_distribute_count` INT         NULL DEFAULT 0 COMMENT '最近分发条数',
  `last_distribute_fail`  INT         NULL DEFAULT 0 COMMENT '最近分发失败条数',
  `created_by`           VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mdm_dist_entity` (`project_id`, `entity_id`),
  UNIQUE KEY `uk_mdm_dist_target` (`project_id`, `entity_id`, `target_system`, `distribute_mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据分发配置';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V10__create_mdm_subscription.sql
-- Ticket 59: 主数据订阅管理(系统订阅实体变更,变更时通知)
CREATE TABLE IF NOT EXISTS `yak_mdm_subscription` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`      BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`       BIGINT       NOT NULL COMMENT '订阅实体',
  `subscriber_code` VARCHAR(64)  NOT NULL COMMENT '订阅方系统编码',
  `subscriber_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '订阅方名称',
  `notify_mode`     VARCHAR(16)  NOT NULL DEFAULT 'EVENT' COMMENT '通知方式:EVENT/WEBHOOK',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE/DISABLED',
  `created_by`      VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mdm_sub_entity` (`project_id`, `entity_id`),
  UNIQUE KEY `uk_mdm_sub_subscriber` (`project_id`, `entity_id`, `subscriber_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据订阅管理';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V11__create_mdm_change.sql
-- Ticket 60: 主数据变更审批(申请/审批流/版本)
CREATE TABLE IF NOT EXISTS `yak_mdm_change` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`      BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`       BIGINT       NOT NULL COMMENT '实体',
  `master_id`       VARCHAR(128) NOT NULL COMMENT '目标主数据记录',
  `change_type`     VARCHAR(16)  NOT NULL COMMENT '变更类型:CREATE/UPDATE/MERGE/DELETE',
  `change_content`  TEXT         NOT NULL COMMENT '变更内容(JSON,含属性值对比)',
  `approval_level`  INT          NOT NULL DEFAULT 1 COMMENT '审批级别:1/2',
  `approval_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '审批状态:PENDING/APPROVED/REJECTED/WITHDRAWN',
  `applicant`       VARCHAR(64)  NULL COMMENT '申请人',
  `approver`        VARCHAR(64)  NULL COMMENT '审批人(当前审批人)',
  `approval_comment` TEXT        NULL COMMENT '审批意见',
  `approval_time`   DATETIME     NULL COMMENT '审批时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mdm_change_entity` (`project_id`, `entity_id`),
  KEY `idx_mdm_change_applicant` (`project_id`, `applicant`),
  KEY `idx_mdm_change_master` (`project_id`, `entity_id`, `master_id`),
  KEY `idx_mdm_change_status` (`approval_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据变更审批';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V12__add_mdm_source_field_mapping.sql
-- R0(reuse-plan): 来源绑定增加字段映射(JSON),口径 = 属性编码 -> 源列名。
-- NULL/空 = 约定回退"属性编码与源列同名";加工 SQL 生成器按此映射产出 SELECT 列。
-- 幂等守卫:老开发库执行过已回退的旧 V5(工单 54),同名列已存在时跳过。
SET @has_col = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'yak_mdm_source'
      AND COLUMN_NAME = 'field_mapping');
SET @ddl = IF(
    @has_col = 0,
    'ALTER TABLE yak_mdm_source ADD COLUMN field_mapping JSON NULL COMMENT ''属性编码→源列名映射,NULL=同名回退'' AFTER source_table',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V13__create_mdm_collect_link.sql
-- 采集落地链路绑定(R1,review P0-1.7):一条已确认来源(yak_mdm_source)对应
-- 一个数据集成的离线落地任务;MDM 侧只存反查锚点 job_definition_id 与落地表名,
-- 采集状态/最近运行由 sync 模块按 jobDefinitionId 反查,不在 MDM 冗余执行态。

CREATE TABLE IF NOT EXISTS yak_mdm_collect_link (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体(冗余,便于按实体列表)',
    source_id BIGINT NOT NULL COMMENT '主数据来源绑定 yak_mdm_source.id',
    datasource_id BIGINT NOT NULL COMMENT '源数据源(松散 ID,冗余自来源)',
    landing_table VARCHAR(128) NOT NULL COMMENT '平台库落地表名',
    job_definition_id BIGINT NOT NULL COMMENT '数据集成离线任务定义 ID(反查锚点)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_collect_link_source (project_id, source_id),
    KEY idx_yak_mdm_collect_link_entity (project_id, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据采集落地链路绑定';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V14__drop_mdm_source_legacy_collect_cols.sql
-- R1 收尾:清理已回退工单 54 遗留在 yak_mdm_source 的采集配置列
-- (collect_mode/collect_freq/config_status;field_mapping 已被 R0 以新口径正式启用,保留)。
-- 开发库才有这些残留列,全新库不存在,故逐列 information_schema 幂等守卫。

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='yak_mdm_source' AND COLUMN_NAME='collect_mode');
SET @ddl = IF(@has_col > 0,
    'ALTER TABLE yak_mdm_source DROP COLUMN collect_mode',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='yak_mdm_source' AND COLUMN_NAME='collect_freq');
SET @ddl = IF(@has_col > 0,
    'ALTER TABLE yak_mdm_source DROP COLUMN collect_freq',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='yak_mdm_source' AND COLUMN_NAME='config_status');
SET @ddl = IF(@has_col > 0,
    'ALTER TABLE yak_mdm_source DROP COLUMN config_status',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V15__mdm_change_instance_and_version.sql
-- R4(reuse-plan,接审批中心): 变更记录关联审批单 + 记录版本快照表。
-- 快照口径 = ModelVersionService 发布模式的 MDM 版:全量 attributes JSON,uk 幂等,线性追加。
CREATE TABLE IF NOT EXISTS `yak_mdm_record_version` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`  BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`   BIGINT       NOT NULL COMMENT '实体',
  `master_id`   VARCHAR(128) NOT NULL COMMENT '主数据标识',
  `version`     INT          NOT NULL COMMENT '记录版本号(与 yak_mdm_record.version 对齐)',
  `attributes`  TEXT         NOT NULL COMMENT '该版本属性全量快照(JSON)',
  `status`      VARCHAR(16)  NOT NULL COMMENT '该版本记录状态',
  `change_id`   BIGINT       NULL COMMENT '产生该版本的变更申请(基线快照为 NULL)',
  `operator`    VARCHAR(64)  NULL COMMENT '操作人(审批人/基线归属申请人)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mdm_version` (`project_id`, `entity_id`, `master_id`, `version`),
  KEY `idx_mdm_version_master` (`project_id`, `entity_id`, `master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录版本快照(R4)';

-- 变更单挂审批中心实例 id(在途唯一/两级推进由中心引擎负责,MDM 不自建)
SET @has_col = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'yak_mdm_change'
      AND COLUMN_NAME = 'instance_id');
SET @ddl = IF(
    @has_col = 0,
    'ALTER TABLE yak_mdm_change ADD COLUMN instance_id BIGINT NULL COMMENT ''审批中心单据 id(yak_approval_instance.id)'' AFTER approval_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V16__create_mdm_dedup_ignore.sql
-- R7(reuse-plan,补齐 review 1.6): 去重「忽略组」账本。
-- 口径 = 组键 + 规则 + 原因:同一重复键在同一规则下只忽略一次,换规则不共享(键语义随规则变)。
-- 忽略不落 record 状态(ACTIVE/MERGED/DELETED 是数据生命周期,不能表达「已知非重复」),
-- 只在去重发现 SQL 里把命中的组键排除,并提供撤销。
--
-- match_key 必须用 utf8mb4_bin:去重键表达式来自 JSON_UNQUOTE(JSON_EXTRACT(...)),其结果集
-- 排序规则即 utf8mb4_bin;若本列用 *_ci,`keyExpr NOT IN (SELECT match_key ...)` 会因
-- 列与列之间排序规则不同直接报 1267,且大小写不敏感会把不同键误判为同一组。
CREATE TABLE IF NOT EXISTS yak_mdm_dedup_ignore (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    entity_id BIGINT NOT NULL COMMENT '主数据实体',
    rule_id BIGINT NOT NULL COMMENT '忽略所属的去重规则(键语义随规则变,不跨规则共享)',
    match_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '重复组匹配键(与去重发现 GROUP BY 结果一致)',
    match_basis VARCHAR(512) NULL COMMENT '忽略时的匹配依据快照(仅用于「已忽略组」展示/审计)',
    reason VARCHAR(255) NULL COMMENT '忽略原因(已知非重复的说明)',
    created_by VARCHAR(64) NOT NULL COMMENT '忽略人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '忽略时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_mdm_dedup_ignore (project_id, rule_id, match_key),
    KEY idx_yak_mdm_dedup_ignore_entity (project_id, entity_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据去重忽略组(R7)';

-- Source: data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/V17__add_mdm_record_attribute_overrides.sql
-- Persist explicit, reviewed field values so the next source processing run
-- can refresh source-managed attributes without erasing governance decisions.
ALTER TABLE yak_mdm_record
    ADD COLUMN attribute_overrides JSON NULL COMMENT '经审批或清洗锁定的属性值';
