-- 本文件由 scripts/db/consolidate-flyway-migrations.py 生成,请勿手改;要改结构请改脚本后重新生成。
-- 合并了 27 个版本化迁移(SQL 原文按版本号升序,未做逻辑改写)。
-- 被合并的文件:
--   V1__ [.] V1__baseline_modeling.sql
--   V2__ [.] V2__create_modeling_model.sql
--   V3__ [.] V3__create_modeling_directory_and_tag.sql
--   V4__ [.] V4__modeling_model_soft_delete.sql
--   V5__ [.] V5__modeling_model_structure.sql
--   V6__ [.] V6__modeling_model_code_unique_backstop.sql
--   V7__ [.] V7__modeling_model_pk_index_partition.sql
--   V8__ [.] V8__model_column_std_refs.sql
--   V9__ [.] V9__modeling_column_mapping.sql
--   V10__ [.] V10__modeling_layer_field_mapping.sql
--   V11__ [.] V11__model_derivation_refs.sql
--   V12__ [.] V12__model_source_binding_and_std_field.sql
--   V13__ [.] V13__aggregate_and_application_definitions.sql
--   V14__ [.] V14__layer_mapping_ungoverned_allowed.sql
--   V15__ [.] V15__model_import_lineage.sql
--   V16__ [.] V16__model_version_management.sql
--   V17__ [.] V17__model_column_aggregate_fields.sql
--   V18__ [.] V18__model_domain_ref.sql
--   V19__ [.] V19__directory_domain_binding.sql
--   V20__ [.] V20__model_updated_by.sql
--   V21__ [.] V21__model_directory_follow_domain_backfill.sql
--   V23__ [.] V23__model_lifecycle_governance.sql
--   V24__ [impact] V24__model_impact_analysis.sql
--   V25__ [impact-snapshot] V25__model_version_meta_snapshot.sql
--   V26__ [impact] V26__logical_modeling_foundation.sql
--   V27__ [impact] V27__logical_physical_mapping.sql
--   V28__ [impact-foundation] V28__model_version_foundation.sql

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V1__baseline_modeling.sql
-- Modeling module baseline.
--
-- Ticket 01 establishes the self-owned Flyway boundary only:
--   location: classpath:db/migration/yak-modeling
--   history:  flyway_schema_history_modeling
-- Domain tables are introduced by their owning tickets (model CRUD: ticket 02)
-- and must never be edited after merge. See ARCHITECTURE.md for the planned
-- table ownership.
SELECT 1;

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V2__create_modeling_model.sql
-- Warehouse modeling physical model table (ticket 02: model CRUD vertical).
--
-- One model maps to exactly one physical table (docs/model/issues/02-model-crud.md).
-- project_id follows docs/architecture/PROJECT_SCOPE.md: PROJECT_ROOT ownership,
-- no physical foreign keys, uniqueness of the business key is per project space.

CREATE TABLE IF NOT EXISTS yak_modeling_model (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_code VARCHAR(128) NOT NULL COMMENT '模型编码,项目空间内唯一',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    dialect VARCHAR(64) NOT NULL COMMENT '目标数据库方言',
    description VARCHAR(512) NULL COMMENT '模型描述',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '模型状态:DRAFT',
    created_by VARCHAR(128) NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_project_code (project_id, model_code),
    KEY idx_yak_modeling_model_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模物理模型';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V3__create_modeling_directory_and_tag.sql
-- Warehouse modeling catalog organization (ticket 03): directory tree + tags.
--
-- Directories/tags are PROJECT_ROOT-owned like the model itself. parent_id=0
-- marks a root directory (same convention as yak_dev_directory). A model
-- belongs to at most one directory (directory_id=0 means uncategorized) and
-- can carry multiple tags. Filters combine: directory exact match, tags OR.

CREATE TABLE IF NOT EXISTS yak_modeling_directory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父目录,0 表示根',
    name VARCHAR(128) NOT NULL COMMENT '目录名称',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_directory_sibling (project_id, parent_id, name),
    KEY idx_yak_modeling_directory_parent (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型目录';

CREATE TABLE IF NOT EXISTS yak_modeling_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    name VARCHAR(128) NOT NULL COMMENT '标签名称',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_tag_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型标签';

CREATE TABLE IF NOT EXISTS yak_modeling_model_tag_rel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '模型 id',
    tag_id BIGINT NOT NULL COMMENT '标签 id',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_tag (model_id, tag_id),
    KEY idx_yak_modeling_model_tag_tag (project_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型-标签关联';

ALTER TABLE yak_modeling_model
    ADD COLUMN directory_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属目录,0 表示未分类' AFTER status,
    ADD KEY idx_yak_modeling_model_directory (project_id, directory_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V4__modeling_model_soft_delete.sql
-- Warehouse modeling recycle bin (ticket 04): model soft delete.
--
-- Live-row semantics: deleted=0 rows are the only visible/quotable models.
-- The (project_id, model_code) uniqueness moves from a DB unique key to a
-- service-level check among live rows, so soft-deleted rows no longer block
-- re-creating a model with the same code (see DOMAIN.md status semantics).

ALTER TABLE yak_modeling_model
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记:1=回收站',
    ADD COLUMN deleted_by VARCHAR(128) NULL COMMENT '执行软删除的用户',
    ADD COLUMN deleted_time DATETIME(6) NULL COMMENT '软删除时间',
    DROP INDEX uk_yak_modeling_model_project_code,
    ADD KEY idx_yak_modeling_model_code (project_id, model_code),
    ADD KEY idx_yak_modeling_model_deleted (project_id, deleted);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V5__modeling_model_structure.sql
-- Warehouse modeling table structure editing (ticket 05): table info + columns.
--
-- table_name is the physical table name; blank falls back to model_code at
-- save time. Columns are saved with full-replace semantics: the request
-- carries the complete ordered column list, persisted as sort_order 0..n-1.

ALTER TABLE yak_modeling_model
    ADD COLUMN table_name VARCHAR(128) NULL COMMENT '物理表名,空则按 model_code 兜底',
    ADD COLUMN table_comment VARCHAR(512) NULL COMMENT '表注释';

CREATE TABLE IF NOT EXISTS yak_modeling_model_column (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    column_name VARCHAR(128) NOT NULL COMMENT '字段名,模型内唯一(不区分大小写)',
    data_type VARCHAR(64) NOT NULL COMMENT '数据类型',
    length INT NULL COMMENT '长度或精度',
    scale INT NULL COMMENT '小数位数',
    nullable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可空',
    default_value VARCHAR(256) NULL COMMENT '默认值',
    column_comment VARCHAR(512) NULL COMMENT '字段注释',
    business_description VARCHAR(512) NULL COMMENT '业务描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '字段顺序,0 起',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_column (model_id, column_name),
    KEY idx_yak_modeling_model_column_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型字段';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V6__modeling_model_code_unique_backstop.sql
-- Warehouse modeling review fixes: hard DB backstop for the live-row code
-- uniqueness race. Soft delete rewrites the stored code (original kept in
-- original_code) so a single (project_id, model_code) unique key can stay,
-- while soft-deleted rows no longer block re-creating the same code.

ALTER TABLE yak_modeling_model
    ADD COLUMN original_code VARCHAR(128) NULL COMMENT '软删除前的原始编码,存活行为 NULL',
    ADD UNIQUE KEY uk_yak_modeling_model_project_code (project_id, model_code);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V7__modeling_model_pk_index_partition.sql
-- Warehouse modeling primary key / index / partition editing (ticket 06).
--
-- Primary key is a column-name list stored on the model row (pk_columns as a
-- JSON array); indexes live in their own table with full-replace semantics;
-- partition config and table properties (JSON) ride on the model row. All
-- column references are validated against the saved column list in service.

ALTER TABLE yak_modeling_model
    ADD COLUMN pk_columns VARCHAR(1024) NULL COMMENT '主键列名 JSON 数组,空=无主键',
    ADD COLUMN partition_type VARCHAR(64) NULL COMMENT '分区类型(按方言)',
    ADD COLUMN partition_columns VARCHAR(1024) NULL COMMENT '分区列名 JSON 数组',
    ADD COLUMN partition_expr VARCHAR(512) NULL COMMENT '分区表达式',
    ADD COLUMN table_properties VARCHAR(2048) NULL COMMENT '表属性 JSON 对象';

CREATE TABLE IF NOT EXISTS yak_modeling_model_index (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    index_name VARCHAR(128) NOT NULL COMMENT '索引名,模型内唯一(不区分大小写)',
    unique_index TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否唯一索引',
    index_type VARCHAR(64) NULL COMMENT '索引类型(按方言,可空)',
    column_names VARCHAR(1024) NOT NULL COMMENT '索引列名 JSON 数组',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_index (model_id, index_name),
    KEY idx_yak_modeling_model_index_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型索引';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V8__model_column_std_refs.sql
-- Data-standard reference reservation (ticket 30, M4 cross-module contract).
--
-- Loose-ID references into yak-ops-business-semantic (no physical FK, per
-- PROJECT_SCOPE convention). Written by tickets 38/39/44; read-path passes
-- them through until then (always NULL in this migration's scope).

ALTER TABLE yak_modeling_model_column
    ADD COLUMN std_type_id BIGINT NULL COMMENT '类型标准引用(semantic 松散 ID)',
    ADD COLUMN std_naming_id BIGINT NULL COMMENT '命名标准引用(semantic 松散 ID)',
    ADD COLUMN std_code_set_code VARCHAR(64) NULL COMMENT '码集编码引用(CODE 类标准的 code_set_code,松散引用)',
    ADD COLUMN std_unit_id BIGINT NULL COMMENT '单位标准引用(semantic 松散 ID)',
    ADD COLUMN std_caliber_id BIGINT NULL COMMENT '口径标准引用(semantic 松散 ID)',
    ADD COLUMN std_security_id BIGINT NULL COMMENT '安全标准引用(semantic 松散 ID)';

CREATE INDEX idx_yak_modeling_model_column_std_type
    ON yak_modeling_model_column (std_type_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V9__modeling_column_mapping.sql
-- Field-level source mapping (ticket 19). One mapping per target column
-- (model column). std_process_field_id is the M4 reservation (43/44): a
-- loose reference into yak-ops-business-semantic standard fields.

CREATE TABLE IF NOT EXISTS yak_modeling_column_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    target_column VARCHAR(128) NOT NULL COMMENT '模型目标字段名',
    source_datasource_id BIGINT NULL COMMENT '源数据源(datasource 松散引用)',
    source_database VARCHAR(128) NULL COMMENT '源库',
    source_table VARCHAR(128) NULL COMMENT '源表',
    source_column VARCHAR(128) NULL COMMENT '源字段',
    transform_expr VARCHAR(1024) NULL COMMENT '转换表达式(语法校验后存储)',
    std_process_field_id BIGINT NULL COMMENT '标准字段引用(semantic 松散 ID,M4 预留)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_column_mapping (project_id, model_id, target_column),
    KEY idx_yak_modeling_column_mapping_model (project_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型字段来源映射';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V10__modeling_layer_field_mapping.sql
-- Layer-field mapping (ticket 43): where each standard field lands per layer
-- of a model. Decision D: rows are mostly auto-written by derivation (44);
-- manual edits are corrections. layer_id references yak_semantic_layer and
-- process_field_id references yak_semantic_field (loose IDs, no FK).

CREATE TABLE IF NOT EXISTS yak_modeling_layer_field_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '所属模型',
    process_field_id BIGINT NOT NULL COMMENT '标准字段(semantic 松散 ID)',
    layer_id BIGINT NOT NULL COMMENT '分层(semantic 松散 ID,37 为源)',
    layer_field_name VARCHAR(128) NOT NULL COMMENT '该层落地字段名',
    layer_data_type VARCHAR(64) NULL COMMENT '该层落地类型',
    source_field VARCHAR(256) NULL COMMENT '来源字段(模型内字段名或上游字段)',
    transform_expr VARCHAR(1024) NULL COMMENT '转换表达式(语法守卫)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_lfm (project_id, model_id, process_field_id, layer_id, layer_field_name),
    KEY idx_yak_modeling_lfm_field (project_id, process_field_id),
    KEY idx_yak_modeling_lfm_layer (project_id, layer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段分层映射(标准字段 x 层落地)';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V11__model_derivation_refs.sql
-- Derivation support (ticket 44): model row carries the owning business
-- process and target layer (loose references, no physical FK).

ALTER TABLE yak_modeling_model
    ADD COLUMN process_id BIGINT NULL COMMENT '业务过程(semantic 松散引用,44 派生写入)',
    ADD COLUMN layer_code VARCHAR(32) NULL COMMENT '目标分层编码(37 分层,44 派生写入)';

CREATE INDEX idx_yak_modeling_model_process ON yak_modeling_model (process_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V12__model_source_binding_and_std_field.sql
-- 44 字段继承与治理(2026-09-17):两处落点。
--
-- 1) 模型来源绑定:逆向导入写入数据源/库/表,补齐 08 票"模型持久化来源标记"的缺口,
--    并让 44 派生能精确反查"该源表对应的 ODS 模型"(不再靠表名约定)。
-- 2) 模型字段的标准字段关联:ODS 轻治理(38)与派生继承(44)都把命中结果写在这里,
--    作为"字段 ↔ 标准字段"的唯一权威(43 分层映射 process_field_id 与 19 来源映射
--    std_process_field_id 均由此派生)。
--
-- 均为可空列 + 普通索引,无物理外键;回滚只需 DROP COLUMN / DROP INDEX。

ALTER TABLE yak_modeling_model
    ADD COLUMN source_datasource_id BIGINT NULL COMMENT '来源数据源(datasource 松散ID,逆向导入写入)',
    ADD COLUMN source_database VARCHAR(128) NULL COMMENT '来源库',
    ADD COLUMN source_table VARCHAR(128) NULL COMMENT '来源表';

ALTER TABLE yak_modeling_model_column
    ADD COLUMN std_field_id BIGINT NULL COMMENT '标准字段(semantic 松散ID;38/44 匹配或继承写入)';

CREATE INDEX idx_yak_modeling_model_source
    ON yak_modeling_model (project_id, source_datasource_id, source_table);

CREATE INDEX idx_yak_modeling_model_column_std
    ON yak_modeling_model_column (project_id, std_field_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V13__aggregate_and_application_definitions.sql
-- 51 DWS 结构化聚合 / 52 ADS 应用绑定:聚合定义与应用绑定的落点。
--
-- 1) 模型行:统计周期(DWS 表级周期约定)与应用/报表绑定(ADS 松散引用)。
-- 2) 43 分层映射:字段角色(维度/度量)与聚合函数,使聚合定义可校验、可生成加工 SQL,
--    而不是塞进 transform_expr 自由文本。
--
-- 均为可空列,无物理外键;回滚只需 DROP COLUMN。

ALTER TABLE yak_modeling_model
    ADD COLUMN stat_period VARCHAR(8) NULL COMMENT '统计周期(DWS/ADS,如 1h/1d/1w/1m/ALL)',
    ADD COLUMN app_code VARCHAR(64) NULL COMMENT '应用/报表编码(ADS,松散引用)',
    ADD COLUMN app_name VARCHAR(128) NULL COMMENT '应用/报表名称(ADS,展示用)';

ALTER TABLE yak_modeling_layer_field_mapping
    ADD COLUMN field_role VARCHAR(16) NULL COMMENT '聚合层字段角色:DIMENSION 分组键 / MEASURE 度量(51)',
    ADD COLUMN aggregate_func VARCHAR(16) NULL COMMENT '聚合函数(MEASURE:SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V14__layer_mapping_ungoverned_allowed.sql
-- 51:聚合/应用层的字段角色与聚合函数属于**建模语义**,未治理字段也必须落 43 映射。
--
-- 44 的规则是"未关联标准字段的字段不写 43"(43 原名"标准字段 × 层落地");但 DWS/ADS 的
-- 维度/度量定义就是表的粒度与聚合语义,丢了定义就无法生成加工 SQL。因此:
-- - INHERIT(DWD/DIM)行为不变:未治理字段不写 43;
-- - AGGREGATE/APPLICATION(DWS/ADS):所有业务字段都写 43,未治理字段的 process_field_id 为空。
--
-- MySQL 唯一键允许多个 NULL,故未治理行仍受 (project_id, model_id, layer_id, layer_field_name) 约束。

ALTER TABLE yak_modeling_layer_field_mapping
    MODIFY COLUMN process_field_id BIGINT NULL COMMENT '标准字段(semantic 松散ID);未治理字段为空(聚合层定义仍需落库)';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V15__model_import_lineage.sql
-- Lineage tracking: record how the model's fields were imported.
-- import_mode: MANUAL / SOURCE_TABLE / MODEL / BUSINESS_PROCESS
-- source_model_id: when import_mode = MODEL, the model fields were copied from.

ALTER TABLE yak_modeling_model
    ADD COLUMN import_mode VARCHAR(32) NULL COMMENT '字段导入方式(血缘追溯):MANUAL/SOURCE_TABLE/MODEL/BUSINESS_PROCESS',
    ADD COLUMN source_model_id BIGINT NULL COMMENT '来源模型ID(import_mode=MODEL时记录,血缘追溯)';

CREATE INDEX idx_yak_modeling_model_source_model ON yak_modeling_model (source_model_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V16__model_version_management.sql
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

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V17__model_column_aggregate_fields.sql
-- 表结构列支持 DWS/ADS 聚合元数据(统一抽屉 + 详情页表结构页签内反推)。
--
-- 角色/聚合函数/口径落在物理列上,使字段编辑器可直接表达聚合语义、
-- saveStructure 可持久化、加工 SQL 可校验,而不是塞进自由文本。
--
-- 均为可空列,无物理外键;回滚只需 DROP COLUMN。
-- 注意:与 V13 的 yak_modeling_layer_field_mapping.field_role/aggregate_func 是
-- 不同落点 —— 前者是分层映射口径,此处是模型物理列口径(详情页表结构页签直读直写)。

ALTER TABLE yak_modeling_model_column
    ADD COLUMN field_role VARCHAR(16) NULL COMMENT '聚合层字段角色:DIMENSION 分组键 / MEASURE 度量(DWS/ADS)',
    ADD COLUMN aggregate_func VARCHAR(16) NULL COMMENT '聚合函数(MEASURE:SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)',
    ADD COLUMN transform_expr VARCHAR(1024) NULL COMMENT '口径/转换表达式';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V18__model_domain_ref.sql
-- 业务域引用(semantic 松散引用):新建模型向导选择的业务域需要持久化,
-- 此前 domainId 在 CreateRequest 被丢弃,列表/树按域过滤只能经业务过程间接推导。
-- 历史行按已关联的业务过程回填一次。

ALTER TABLE yak_modeling_model
    ADD COLUMN domain_id BIGINT NULL COMMENT '业务域(semantic 松散引用,新建模型向导写入)';

CREATE INDEX idx_yak_modeling_model_domain ON yak_modeling_model (domain_id);

UPDATE yak_modeling_model m
    JOIN yak_semantic_process p ON p.id = m.process_id
   SET m.domain_id = p.domain_id
 WHERE m.domain_id IS NULL;

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V19__directory_domain_binding.sql
-- 目录跟随业务域：模型保存时按所选业务域自动落/复用目录，目录树与业务域树一一对应。
-- 名称可变（域改名要能跟上），所以用 domain_id 显式绑定，而不是按名称匹配。

ALTER TABLE yak_modeling_directory
    ADD COLUMN domain_id BIGINT NULL COMMENT '绑定的业务域(semantic 松散引用;目录随域自动生成)';

CREATE INDEX idx_yak_modeling_directory_domain ON yak_modeling_directory (project_id, domain_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V20__model_updated_by.sql
-- 回收站要回答「入站前最后一次是谁改的」：created_by 只记到创建人，deleted_by 只记到删除动作，
-- 中间若干轮编辑无人可查。update_time 由建表 DDL 的 ON UPDATE 自动维护，
-- updated_by 只能由应用侧写入（见 ModelRepositoryAdapter 的 live 更新入口）。
ALTER TABLE yak_modeling_model
    ADD COLUMN updated_by VARCHAR(128) NULL COMMENT '最后更新人(应用侧写入;NULL=该列上线前的历史行)';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V21__model_directory_follow_domain_backfill.sql
-- 目录跟随业务域(V19)之后新建/编辑的模型才会自动落目录，此前建的存量模型即使已有
-- domain_id 也仍停在未分类(0)，左侧目录树按域看不到它们。按域已绑定的目录回填一次归属：
-- 同域多条目录取最小 ID(与 findByDomainId 的 orderByAsc(id) LIMIT 1 一致)；域还没有绑定
-- 目录的保持未分类，等下一次保存该模型时由 ensureDirectoryForDomain 补目录。
-- 命中条件带 directory_id = 0，重复执行只会扫到仍未归位的行，天然幂等。

UPDATE yak_modeling_model m
    JOIN (
        SELECT d.project_id, d.domain_id, MIN(d.id) AS directory_id
        FROM yak_modeling_directory d
        WHERE d.domain_id IS NOT NULL
        GROUP BY d.project_id, d.domain_id
    ) bound ON bound.project_id = m.project_id AND bound.domain_id = m.domain_id
   SET m.directory_id = bound.directory_id
 WHERE m.deleted = 0
   AND m.directory_id = 0;

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling/V23__model_lifecycle_governance.sql
CREATE TABLE IF NOT EXISTS yak_modeling_lifecycle_record (
  id BIGINT PRIMARY KEY,
  object_type VARCHAR(64) NOT NULL,
  object_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  operator VARCHAR(64),
  reason VARCHAR(512),
  create_time TIMESTAMP
);

CREATE INDEX idx_lifecycle_object
  ON yak_modeling_lifecycle_record(object_type, object_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact/V24__model_impact_analysis.sql
CREATE TABLE IF NOT EXISTS yak_modeling_impact_record (
    id BIGINT PRIMARY KEY,
    source_object_type VARCHAR(64) NOT NULL,
    source_object_id BIGINT NOT NULL,
    target_object_type VARCHAR(64) NOT NULL,
    target_object_id BIGINT NOT NULL,
    impact_type VARCHAR(32) NOT NULL,
    description VARCHAR(512),
    create_time DATETIME NOT NULL
);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact-snapshot/V25__model_version_meta_snapshot.sql
-- 发布快照扩为全量：版本行增加模型元数据快照(名称/描述/分层/业务域/方言)。
-- structure_json 仍只承载表结构；消费方读快照时无需回读活主表即可还原发布当时的模型信息。

ALTER TABLE yak_modeling_model_version
    ADD COLUMN meta_json LONGTEXT NULL COMMENT '发布时模型元数据快照(JSON)';

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact/V26__logical_modeling_foundation.sql
CREATE TABLE IF NOT EXISTS yak_modeling_logical_model (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(1024),
    domain_id BIGINT,
    owner VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_model_domain_id ON yak_modeling_logical_model(domain_id);

CREATE TABLE IF NOT EXISTS yak_modeling_logical_entity (
    id BIGINT PRIMARY KEY,
    logical_model_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    business_name VARCHAR(256),
    description VARCHAR(1024),
    owner VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_entity_model_id ON yak_modeling_logical_entity(logical_model_id);

CREATE TABLE IF NOT EXISTS yak_modeling_logical_attribute (
    id BIGINT PRIMARY KEY,
    entity_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    logical_type VARCHAR(128),
    description VARCHAR(1024),
    primary_flag BOOLEAN DEFAULT FALSE,
    nullable BOOLEAN DEFAULT TRUE,
    sort INT DEFAULT 0
);

CREATE INDEX idx_logical_attribute_entity_id ON yak_modeling_logical_attribute(entity_id);

CREATE TABLE IF NOT EXISTS yak_modeling_entity_relation (
    id BIGINT PRIMARY KEY,
    source_entity_id BIGINT NOT NULL,
    target_entity_id BIGINT NOT NULL,
    relation_type VARCHAR(64),
    cardinality VARCHAR(64),
    description VARCHAR(1024)
);

CREATE INDEX idx_entity_relation_source ON yak_modeling_entity_relation(source_entity_id);
CREATE INDEX idx_entity_relation_target ON yak_modeling_entity_relation(target_entity_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact/V27__logical_physical_mapping.sql
CREATE TABLE IF NOT EXISTS yak_modeling_logical_entity_mapping (
    id BIGINT PRIMARY KEY,
    logical_entity_id BIGINT NOT NULL,
    physical_table_id BIGINT NOT NULL,
    mapping_type VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_entity_mapping_entity
    ON yak_modeling_logical_entity_mapping(logical_entity_id);

CREATE TABLE IF NOT EXISTS yak_modeling_logical_attribute_mapping (
    id BIGINT PRIMARY KEY,
    logical_attribute_id BIGINT NOT NULL,
    physical_column_id BIGINT NOT NULL,
    mapping_expression VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX idx_logical_attribute_mapping_attribute
    ON yak_modeling_logical_attribute_mapping(logical_attribute_id);

-- Source: data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact-foundation/V28__model_version_foundation.sql
CREATE TABLE IF NOT EXISTS yak_modeling_logical_model_version (
  id BIGINT PRIMARY KEY,
  model_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  snapshot TEXT,
  created_by VARCHAR(64),
  create_time TIMESTAMP,
  update_time TIMESTAMP
);

CREATE INDEX idx_model_version_model_id
  ON yak_modeling_logical_model_version(model_id);
