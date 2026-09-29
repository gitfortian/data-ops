-- Yak Ops Data Quality first-release baseline.
-- This file represents the complete schema owned by the Data Quality module.

CREATE TABLE IF NOT EXISTS yak_quality_template_folder (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自定义规则模板目录 ID',
    parent_id BIGINT NULL COMMENT '上级目录 ID，NULL 表示根目录',
    folder_name VARCHAR(100) NOT NULL COMMENT '目录名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system' COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_yak_quality_template_folder_parent (parent_id, deleted, sort_order),
    KEY idx_yak_quality_template_folder_name (folder_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量自定义规则模板目录';

CREATE TABLE IF NOT EXISTS yak_quality_rule_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则模板 ID',
    template_code VARCHAR(64) NOT NULL COMMENT '稳定模板编码',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) NULL COMMENT '模板描述',
    rule_type VARCHAR(40) NOT NULL COMMENT '规则类型',
    rule_scope VARCHAR(20) NOT NULL COMMENT 'TABLE/COLUMN',
    quality_dimension VARCHAR(40) NOT NULL COMMENT '质量维度',
    parameter_schema_json TEXT NOT NULL COMMENT '前端参数定义',
    builtin TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否系统模板',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    folder_id BIGINT NULL COMMENT '自定义模板目录 ID',
    template_sql MEDIUMTEXT NULL COMMENT '自定义模板 SQL',
    set_flag VARCHAR(1000) NULL COMMENT 'SQL 前置 Set Flag，英文逗号分隔',
    check_type VARCHAR(32) NOT NULL DEFAULT 'NUMERIC' COMMENT '自定义模板校验类型',
    check_method VARCHAR(64) NOT NULL DEFAULT 'FIXED_VALUE' COMMENT '自定义模板校验方式',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system' COMMENT '模板创建人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_quality_template_code (template_code),
    KEY idx_yak_quality_template_dimension (quality_dimension, enabled),
    KEY idx_yak_quality_template_folder (folder_id, builtin, deleted, enabled),
    KEY idx_yak_quality_template_source (builtin, deleted, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量规则模板';

CREATE TABLE IF NOT EXISTS yak_quality_table_asset (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据质量表资产 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    data_source_id BIGINT NOT NULL COMMENT '数据源 ID',
    data_source_name VARCHAR(128) NOT NULL COMMENT '数据源名称快照',
    database_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '数据库名称',
    schema_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Schema 名称',
    table_name VARCHAR(256) NOT NULL COMMENT '数据表名称',
    table_type VARCHAR(40) NULL COMMENT 'TABLE/VIEW 等插件类型',
    remarks VARCHAR(1000) NULL COMMENT '数据表描述快照',
    registered_by VARCHAR(128) NOT NULL DEFAULT 'system' COMMENT '注册人',
    registered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_quality_table_asset_target
      (project_id, data_source_id, database_name, schema_name, table_name),
    KEY idx_yak_quality_table_asset_query
      (project_id, data_source_id, database_name, deleted, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量已注册数据表';

CREATE TABLE IF NOT EXISTS yak_quality_monitor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '质量监控 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    monitor_name VARCHAR(100) NOT NULL COMMENT '监控名称',
    description VARCHAR(500) NULL COMMENT '监控描述',
    data_source_id BIGINT NOT NULL COMMENT '数据源 ID',
    data_source_name VARCHAR(128) NOT NULL COMMENT '数据源名称快照',
    database_name VARCHAR(128) NULL COMMENT '数据库名称',
    schema_name VARCHAR(128) NULL COMMENT 'Schema 名称',
    table_name VARCHAR(256) NOT NULL COMMENT '数据表名称',
    where_clause TEXT NULL COMMENT '数据范围 WHERE 条件片段',
    owner VARCHAR(128) NOT NULL COMMENT '负责人',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_result VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN' COMMENT '最近检查结果',
    last_execution_no VARCHAR(64) NULL COMMENT '最近执行编号',
    last_run_time DATETIME(3) NULL COMMENT '最近运行时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_yak_quality_monitor_target
      (project_id, data_source_id, database_name, schema_name, table_name, deleted),
    KEY idx_yak_quality_monitor_result (project_id, last_result, deleted),
    KEY idx_yak_quality_monitor_updated (project_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量监控';

CREATE TABLE IF NOT EXISTS yak_quality_monitor_setting (
    monitor_id BIGINT NOT NULL COMMENT '质量监控 ID',
    run_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULE',
    schedule_frequency VARCHAR(20) NULL COMMENT 'DAILY/WEEKLY/CRON',
    schedule_time TIME NULL COMMENT '每日或每周执行时间',
    schedule_weekday VARCHAR(3) NULL COMMENT 'MON/TUE/WED/THU/FRI/SAT/SUN',
    cron_expression VARCHAR(128) NULL COMMENT 'Spring Cron 表达式',
    next_run_time DATETIME(3) NULL COMMENT '下一次计划运行时间',
    rule_failure_action VARCHAR(20) NOT NULL DEFAULT 'CONTINUE' COMMENT 'CONTINUE/STOP',
    notify_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否生成告警事件',
    notify_channel VARCHAR(20) NULL DEFAULT 'MESSAGE' COMMENT 'MESSAGE/EMAIL/WEBHOOK',
    notify_target VARCHAR(1000) NULL COMMENT '接收人、邮箱或 Webhook 地址',
    alert_level VARCHAR(20) NOT NULL DEFAULT 'WARNING' COMMENT 'WARNING/CRITICAL',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (monitor_id),
    KEY idx_yak_quality_setting_due (run_mode, next_run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量监控运行与问题处理设置';

CREATE TABLE IF NOT EXISTS yak_quality_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '质量规则 ID',
    monitor_id BIGINT NOT NULL COMMENT '所属质量监控 ID',
    template_id BIGINT NOT NULL COMMENT '来源模板 ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码快照',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(40) NOT NULL COMMENT '规则类型快照',
    rule_scope VARCHAR(20) NOT NULL COMMENT 'TABLE/COLUMN',
    quality_dimension VARCHAR(40) NOT NULL COMMENT '质量维度快照',
    column_name VARCHAR(256) NULL COMMENT '检查字段',
    comparison_operator VARCHAR(20) NOT NULL COMMENT '比较方式',
    threshold_value DECIMAL(30, 10) NULL COMMENT '阈值或范围最小值',
    threshold_end DECIMAL(30, 10) NULL COMMENT '范围最大值',
    enum_values_json TEXT NULL COMMENT '枚举允许值',
    custom_sql MEDIUMTEXT NULL COMMENT '自定义检查 SQL',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_yak_quality_rule_monitor (monitor_id, deleted, sort_order),
    KEY idx_yak_quality_rule_template (template_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量监控规则';

CREATE TABLE IF NOT EXISTS yak_quality_execution (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行内部 ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    execution_no VARCHAR(64) NOT NULL COMMENT '执行编号',
    monitor_id BIGINT NOT NULL COMMENT '质量监控 ID',
    monitor_name VARCHAR(100) NOT NULL COMMENT '监控名称快照',
    data_source_id BIGINT NOT NULL COMMENT '数据源 ID 快照',
    data_source_name VARCHAR(128) NOT NULL COMMENT '数据源名称快照',
    database_name VARCHAR(128) NULL COMMENT '数据库名称快照',
    schema_name VARCHAR(128) NULL COMMENT 'Schema 名称快照',
    table_name VARCHAR(256) NOT NULL COMMENT '数据表快照',
    object_name VARCHAR(768) NOT NULL COMMENT '监控对象展示名称',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式',
    execution_status VARCHAR(20) NOT NULL DEFAULT 'WAITING'
        COMMENT 'WAITING/RUNNING/SUCCESS/FAILED',
    check_result VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
        COMMENT 'PASSED/NOT_PASSED/ERROR/RUNNING',
    total_rules INT NOT NULL DEFAULT 0 COMMENT '规则总数',
    passed_rules INT NOT NULL DEFAULT 0 COMMENT '通过规则数',
    failed_rules INT NOT NULL DEFAULT 0 COMMENT '未通过规则数',
    error_rules INT NOT NULL DEFAULT 0 COMMENT '执行异常规则数',
    operator_name VARCHAR(128) NOT NULL COMMENT '触发人',
    queued_at DATETIME(3) NOT NULL COMMENT '入队时间',
    started_at DATETIME(3) NULL COMMENT '开始时间',
    finished_at DATETIME(3) NULL COMMENT '结束时间',
    duration_ms BIGINT NULL COMMENT '执行耗时',
    error_message VARCHAR(1000) NULL COMMENT '整体错误',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_quality_execution_no (execution_no),
    KEY idx_yak_quality_execution_monitor (project_id, monitor_id, created_at),
    KEY idx_yak_quality_execution_status (project_id, execution_status, created_at),
    KEY idx_yak_quality_execution_result (project_id, check_result, created_at),
    KEY idx_yak_quality_execution_queued (project_id, queued_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量监控执行';

CREATE TABLE IF NOT EXISTS yak_quality_rule_execution (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则执行 ID',
    execution_id BIGINT NOT NULL COMMENT '所属监控执行 ID',
    rule_id BIGINT NOT NULL COMMENT '规则 ID',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称快照',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码快照',
    rule_type VARCHAR(40) NOT NULL COMMENT '规则类型快照',
    column_name VARCHAR(256) NULL COMMENT '字段快照',
    check_result VARCHAR(20) NOT NULL COMMENT 'PASSED/NOT_PASSED/ERROR',
    metric_value VARCHAR(256) NULL COMMENT '实际指标展示',
    expected_value VARCHAR(500) NULL COMMENT '期望值展示',
    executed_sql MEDIUMTEXT NULL COMMENT '最终执行 SQL',
    error_message VARCHAR(1000) NULL COMMENT '规则执行错误',
    duration_ms BIGINT NULL COMMENT '规则执行耗时',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_yak_quality_rule_execution_parent (execution_id, id),
    KEY idx_yak_quality_rule_execution_rule (rule_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量规则执行结果';

CREATE TABLE IF NOT EXISTS yak_quality_alert_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '告警事件 ID',
    monitor_id BIGINT NOT NULL COMMENT '质量监控 ID',
    execution_no VARCHAR(64) NOT NULL COMMENT '执行编号',
    check_result VARCHAR(20) NOT NULL COMMENT 'NOT_PASSED/ERROR',
    alert_level VARCHAR(20) NOT NULL COMMENT 'WARNING/CRITICAL',
    notify_channel VARCHAR(20) NOT NULL COMMENT 'MESSAGE/EMAIL/WEBHOOK',
    notify_target VARCHAR(1000) NULL COMMENT '接收目标快照',
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'RECORDED' COMMENT 'RECORDED/PENDING/FAILED',
    alert_message VARCHAR(1000) NOT NULL COMMENT '告警摘要',
    error_message VARCHAR(1000) NULL COMMENT '告警处理错误',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    delivered_at DATETIME(3) NULL COMMENT '实际送达时间',
    PRIMARY KEY (id),
    KEY idx_yak_quality_alert_monitor (monitor_id, created_at),
    KEY idx_yak_quality_alert_execution (execution_no),
    KEY idx_yak_quality_alert_status (delivery_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量告警事件';

INSERT INTO yak_quality_rule_template
(template_code, template_name, description, rule_type, rule_scope,
 quality_dimension, parameter_schema_json, builtin, enabled, sort_order)
VALUES
('TABLE_ROW_COUNT', '表行数', '统计目标数据范围的总行数，并与阈值进行比较。',
 'TABLE_ROW_COUNT', 'TABLE', '完整性',
 '{"fields":["operator","threshold"],"defaultOperator":"GT","defaultThreshold":0}',
 1, 1, 10),
('COLUMN_NOT_NULL', '字段非空', '统计字段非空率，空表按 100% 处理。',
 'COLUMN_NOT_NULL', 'COLUMN', '完整性',
 '{"fields":["columnName","operator","threshold"],"defaultOperator":"GTE","defaultThreshold":100}',
 1, 1, 20),
('COLUMN_UNIQUE', '字段唯一', '统计字段非空值中的唯一率。',
 'COLUMN_UNIQUE', 'COLUMN', '唯一性',
 '{"fields":["columnName","operator","threshold"],"defaultOperator":"GTE","defaultThreshold":100}',
 1, 1, 30),
('COLUMN_RANGE', '字段数值范围', '检查字段值是否都位于指定的最小值和最大值之间。',
 'COLUMN_RANGE', 'COLUMN', '有效性',
 '{"fields":["columnName","threshold","thresholdEnd"],"thresholdLabel":"最小值","thresholdEndLabel":"最大值"}',
 1, 1, 40),
('COLUMN_ENUM', '字段枚举值', '检查字段非空值是否都包含在允许的枚举集合内。',
 'COLUMN_ENUM', 'COLUMN', '准确性',
 '{"fields":["columnName","enumValues"]}',
 1, 1, 50),
('CUSTOM_SQL', '自定义 SQL', '执行只读 SELECT，并使用首行首列作为质量指标。支持 ${table}、${column} 和 ${where}。',
 'CUSTOM_SQL', 'TABLE', '自定义',
 '{"fields":["customSql","operator","threshold"],"defaultOperator":"EQ","defaultThreshold":0}',
 1, 1, 60)
ON DUPLICATE KEY UPDATE
template_name = VALUES(template_name),
description = VALUES(description),
rule_type = VALUES(rule_type),
rule_scope = VALUES(rule_scope),
quality_dimension = VALUES(quality_dimension),
parameter_schema_json = VALUES(parameter_schema_json),
builtin = VALUES(builtin),
enabled = VALUES(enabled),
sort_order = VALUES(sort_order);
