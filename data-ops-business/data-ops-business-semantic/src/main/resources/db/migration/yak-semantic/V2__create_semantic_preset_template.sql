-- Platform-level preset template table (ticket 31). No project_id: templates
-- are org-wide seeds; "initialize preset standards" copies them into the
-- project-scoped yak_semantic_standard rows (idempotent by (kind, std_code)).
-- Column set mirrors yak_semantic_standard minus project/audit/state columns.

CREATE TABLE IF NOT EXISTS yak_semantic_preset_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    kind VARCHAR(16) NOT NULL COMMENT '标准类别:NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY',
    std_code VARCHAR(64) NOT NULL COMMENT '标准编码(与标准表同规则)',
    std_name VARCHAR(128) NOT NULL COMMENT '标准名称',
    description VARCHAR(512) NULL COMMENT '描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序,小在前',
    scope VARCHAR(16) NULL COMMENT '命名标准:适用范围',
    layer VARCHAR(64) NULL COMMENT '命名标准:适用分层',
    rule_expr VARCHAR(1024) NULL COMMENT '命名标准:规则表达式',
    example VARCHAR(256) NULL COMMENT '命名标准:示例',
    type_code VARCHAR(64) NULL COMMENT '类型标准:类型编码',
    std_type VARCHAR(64) NULL COMMENT '类型标准:标准类型',
    source_mapping TEXT NULL COMMENT '类型标准:源库类型映射 JSON',
    code_set_code VARCHAR(64) NULL COMMENT '码值标准:码集编码',
    code_value VARCHAR(256) NULL COMMENT '码值标准:码值',
    code_label VARCHAR(256) NULL COMMENT '码值标准:码值标签',
    unit_code VARCHAR(64) NULL COMMENT '单位标准:单位编码',
    unit_type VARCHAR(64) NULL COMMENT '单位标准:单位类型',
    caliber_code VARCHAR(64) NULL COMMENT '口径标准:口径编码',
    cal_rule VARCHAR(1024) NULL COMMENT '口径标准:口径规则',
    business_desc VARCHAR(512) NULL COMMENT '口径标准:业务说明',
    level_code VARCHAR(64) NULL COMMENT '安全标准:等级编码',
    mask_rule VARCHAR(512) NULL COMMENT '安全标准:脱敏规则',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_semantic_preset_template (kind, std_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务语义预置标准模板(平台级)';

-- ============================================================================
-- 平台级预置标准模板（扩展版）—— 第一段
-- 命名 15 + 类型 60 + 单位 40 + 安全 25 = 140 条
-- ============================================================================

-- ============================================================
-- 一、命名标准（NAMING）—— 15 条
-- ============================================================
INSERT INTO yak_semantic_preset_template
(kind, std_code, std_name, description, sort_order, scope, layer, rule_expr, example)
VALUES
    ('NAMING', 'naming_table_ods', 'ODS表命名', 'ODS层表命名规范', 10, 'TABLE', 'ODS', '^ods_[a-z][a-z0-9_]*$', 'ods_trade_order'),
    ('NAMING', 'naming_table_dwd', 'DWD表命名', 'DWD层表命名规范', 11, 'TABLE', 'DWD', '^dwd_[a-z][a-z0-9_]*$', 'dwd_trade_order_detail'),
    ('NAMING', 'naming_table_dws', 'DWS表命名', 'DWS层表命名规范', 12, 'TABLE', 'DWS', '^dws_[a-z][a-z0-9_]*$', 'dws_trade_order_summary'),
    ('NAMING', 'naming_table_ads', 'ADS表命名', 'ADS层表命名规范', 13, 'TABLE', 'ADS', '^ads_[a-z][a-z0-9_]*$', 'ads_trade_order_report'),
    ('NAMING', 'naming_table_dim', 'DIM表命名', 'DIM层表命名规范', 14, 'TABLE', 'DIM', '^dim_[a-z][a-z0-9_]*$', 'dim_user'),
    ('NAMING', 'naming_table_tmp', '临时表命名', '临时表命名规范', 15, 'TABLE', 'TMP', '^tmp_[a-z][a-z0-9_]*$', 'tmp_order_20260915'),
    ('NAMING', 'naming_field', '字段命名', '字段统一命名规范', 20, 'FIELD', NULL, '^[a-z][a-z0-9_]*$', 'order_amount'),
    ('NAMING', 'naming_field_id', 'ID字段命名', 'ID字段命名规范', 21, 'FIELD', NULL, '^[a-z][a-z0-9_]*_id$', 'order_id'),
    ('NAMING', 'naming_field_time', '时间字段命名', '时间字段命名规范', 22, 'FIELD', NULL, '^[a-z][a-z0-9_]*_time$', 'order_time'),
    ('NAMING', 'naming_field_date', '日期字段命名', '日期字段命名规范', 23, 'FIELD', NULL, '^[a-z][a-z0-9_]*_date$', 'order_date'),
    ('NAMING', 'naming_field_flag', '标志字段命名', '标志字段命名规范', 24, 'FIELD', NULL, '^is_[a-z][a-z0-9_]*$', 'is_deleted'),
    ('NAMING', 'naming_field_amount', '金额字段命名', '金额字段命名规范', 25, 'FIELD', NULL, '^[a-z][a-z0-9_]*_amount$', 'order_amount'),
    ('NAMING', 'naming_field_cnt', '计数字段命名', '计数字段命名规范', 26, 'FIELD', NULL, '^[a-z][a-z0-9_]*_cnt$', 'order_cnt'),
    ('NAMING', 'naming_db', '库命名', '数据库命名规范', 30, 'DATABASE', NULL, '^[a-z][a-z0-9_]*$', 'dwd_trade'),
    ('NAMING', 'naming_partition', '分区字段命名', '分区字段命名规范', 31, 'FIELD', NULL, '^(dt|hour|month)$', 'dt');

-- ============================================================
-- 二、类型标准（TYPE）—— 60 条
-- ============================================================
INSERT INTO yak_semantic_preset_template
(kind, std_code, std_name, description, sort_order, type_code, std_type, source_mapping)
VALUES
-- 标识类（8）
('TYPE', 'id', '标识', '各类ID统一为字符串，避免精度丢失、join类型不一致', 10, 'id', 'string', '{"mysql":["bigint","varchar","char"],"hive":["string","bigint"],"clickhouse":["String","UInt64"],"oracle":["NUMBER","VARCHAR2"],"postgresql":["bigint","varchar"]}'),
('TYPE', 'code', '编码', '业务编码，如订单号、流水号', 11, 'code', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'uuid', '唯一标识', 'UUID类标识', 12, 'uuid', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","uuid"]}'),
('TYPE', 'serial_no', '流水号', '业务流水号', 13, 'serial_no', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'batch_no', '批次号', '批次号', 14, 'batch_no', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'version_no', '版本号', '版本号', 15, 'version_no', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'parent_id', '父级标识', '树形父节点', 16, 'parent_id', 'string', '{"mysql":["bigint","varchar"],"hive":["string","bigint"],"clickhouse":["String"],"oracle":["NUMBER","VARCHAR2"],"postgresql":["bigint","varchar"]}'),
('TYPE', 'root_id', '根标识', '树形根节点', 17, 'root_id', 'string', '{"mysql":["bigint","varchar"],"hive":["string","bigint"],"clickhouse":["String"],"oracle":["NUMBER","VARCHAR2"],"postgresql":["bigint","varchar"]}'),

-- 文本类（16）
('TYPE', 'name', '名称', '各类名称', 20, 'name', 'string', '{"mysql":["varchar","char","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'short_name', '简称', '简称', 21, 'short_name', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'full_name', '全称', '全称', 22, 'full_name', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'desc', '描述', '长文本描述', 23, 'desc', 'string', '{"mysql":["text","varchar"],"hive":["string"],"clickhouse":["String"],"oracle":["CLOB","VARCHAR2"],"postgresql":["text"]}'),
('TYPE', 'remark', '备注', '备注', 24, 'remark', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'status', '状态', '状态枚举，关联码值标准', 25, 'status', 'string', '{"mysql":["tinyint","varchar"],"hive":["string","tinyint"],"clickhouse":["String","Int8"],"oracle":["NUMBER","VARCHAR2"],"postgresql":["smallint","varchar"]}'),
('TYPE', 'type', '类型', '分类枚举，关联码值标准', 26, 'type', 'string', '{"mysql":["tinyint","varchar"],"hive":["string","tinyint"],"clickhouse":["String","Int8"],"oracle":["NUMBER","VARCHAR2"],"postgresql":["smallint","varchar"]}'),
('TYPE', 'level', '级别', '级别枚举', 27, 'level', 'string', '{"mysql":["tinyint","varchar"],"hive":["string","tinyint"],"clickhouse":["String","Int8"],"oracle":["NUMBER","VARCHAR2"],"postgresql":["smallint","varchar"]}'),
('TYPE', 'category', '分类', '分类', 28, 'category', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'tag', '标签', '标签', 29, 'tag', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'keyword', '关键词', '关键词', 30, 'keyword', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'title', '标题', '标题', 31, 'title', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'content', '内容', '内容', 32, 'content', 'string', '{"mysql":["text","longtext"],"hive":["string"],"clickhouse":["String"],"oracle":["CLOB"],"postgresql":["text"]}'),
('TYPE', 'url', '链接', 'URL', 33, 'url', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'path', '路径', '路径', 34, 'path', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'flag', '标志', '是否类标志，0/1', 35, 'flag', 'tinyint', '{"mysql":["tinyint","bit"],"hive":["tinyint","boolean"],"clickhouse":["Int8","Bool"],"oracle":["NUMBER"],"postgresql":["smallint","boolean"]}'),

-- 数值类（18）
('TYPE', 'amount', '金额', '金额类统一精度', 40, 'amount', 'decimal(18,2)', '{"mysql":["decimal","numeric","double"],"hive":["decimal","double"],"clickhouse":["Decimal","Float64"],"oracle":["NUMBER"],"postgresql":["numeric","decimal"]}'),
('TYPE', 'amount_tax', '含税金额', '含税金额', 41, 'amount_tax', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'price', '单价', '单价', 42, 'price', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'cost', '成本', '成本', 43, 'cost', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'profit', '利润', '利润', 44, 'profit', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'balance', '余额', '余额', 45, 'balance', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'discount', '折扣额', '折扣额', 46, 'discount', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'tax', '税额', '税额', 47, 'tax', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'freight', '运费', '运费', 48, 'freight', 'decimal(18,2)', '{"mysql":["decimal","numeric"],"hive":["decimal"],"clickhouse":["Decimal"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'quantity', '数量', '整数数量', 49, 'quantity', 'int', '{"mysql":["int","smallint"],"hive":["int","bigint"],"clickhouse":["Int32"],"oracle":["NUMBER"],"postgresql":["integer"]}'),
('TYPE', 'count', '计数', '大数计数', 50, 'count', 'bigint', '{"mysql":["bigint","int"],"hive":["bigint"],"clickhouse":["UInt64"],"oracle":["NUMBER"],"postgresql":["bigint"]}'),
('TYPE', 'stock', '库存', '库存', 51, 'stock', 'int', '{"mysql":["int"],"hive":["int"],"clickhouse":["Int32"],"oracle":["NUMBER"],"postgresql":["integer"]}'),
('TYPE', 'rate', '比率', '比率类统一精度', 52, 'rate', 'decimal(9,4)', '{"mysql":["decimal","float","double"],"hive":["decimal","double"],"clickhouse":["Decimal","Float64"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'percent', '百分比', '百分比', 53, 'percent', 'decimal(9,4)', '{"mysql":["decimal","float"],"hive":["decimal","double"],"clickhouse":["Decimal","Float64"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'score', '分值', '评分、积分', 54, 'score', 'decimal(9,2)', '{"mysql":["decimal","int"],"hive":["decimal","int"],"clickhouse":["Decimal","Int32"],"oracle":["NUMBER"],"postgresql":["numeric","integer"]}'),
('TYPE', 'point', '积分', '积分', 55, 'point', 'bigint', '{"mysql":["bigint","int"],"hive":["bigint"],"clickhouse":["Int64"],"oracle":["NUMBER"],"postgresql":["bigint"]}'),
('TYPE', 'weight', '重量', '重量，单位kg', 56, 'weight', 'decimal(12,3)', '{"mysql":["decimal","double"],"hive":["decimal","double"],"clickhouse":["Decimal","Float64"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),
('TYPE', 'volume', '体积', '体积，单位m³', 57, 'volume', 'decimal(12,3)', '{"mysql":["decimal","double"],"hive":["decimal","double"],"clickhouse":["Decimal","Float64"],"oracle":["NUMBER"],"postgresql":["numeric"]}'),

-- 时间类（8）
('TYPE', 'time', '时间', '事件时间，统一格式 yyyy-MM-dd HH:mm:ss', 60, 'time', 'string', '{"mysql":["datetime","timestamp"],"hive":["string","timestamp"],"clickhouse":["DateTime"],"oracle":["DATE","TIMESTAMP"],"postgresql":["timestamp","datetime"]}'),
('TYPE', 'date', '日期', '日期，统一格式 yyyy-MM-dd', 61, 'date', 'string', '{"mysql":["date"],"hive":["string","date"],"clickhouse":["Date"],"oracle":["DATE"],"postgresql":["date"]}'),
('TYPE', 'month', '月份', '月份，统一格式 yyyy-MM', 62, 'month', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'year', '年份', '年份，格式 yyyy', 63, 'year', 'string', '{"mysql":["varchar","char","int"],"hive":["string","int"],"clickhouse":["String"],"oracle":["VARCHAR2","NUMBER"],"postgresql":["varchar","integer"]}'),
('TYPE', 'datetime', '日期时间', '日期时间，yyyy-MM-dd HH:mm:ss', 64, 'datetime', 'string', '{"mysql":["datetime","timestamp"],"hive":["string","timestamp"],"clickhouse":["DateTime"],"oracle":["DATE","TIMESTAMP"],"postgresql":["timestamp","datetime"]}'),
('TYPE', 'timestamp', '时间戳', '毫秒时间戳（仅在必须时用）', 65, 'timestamp', 'bigint', '{"mysql":["bigint"],"hive":["bigint"],"clickhouse":["Int64"],"oracle":["NUMBER"],"postgresql":["bigint"]}'),
('TYPE', 'duration', '时长', '毫秒时长', 66, 'duration', 'bigint', '{"mysql":["bigint","int"],"hive":["bigint"],"clickhouse":["Int64"],"oracle":["NUMBER"],"postgresql":["bigint"]}'),
('TYPE', 'interval', '间隔', '间隔秒数', 67, 'interval', 'int', '{"mysql":["int"],"hive":["int"],"clickhouse":["Int32"],"oracle":["NUMBER"],"postgresql":["integer"]}'),

-- 其他类（10）
('TYPE', 'json', 'JSON', 'JSON 字符串', 70, 'json', 'string', '{"mysql":["json","text"],"hive":["string"],"clickhouse":["String"],"oracle":["CLOB"],"postgresql":["jsonb","json"]}'),
('TYPE', 'array', '数组', '数组（逗号分隔或JSON）', 71, 'array', 'string', '{"mysql":["varchar","text"],"hive":["array","string"],"clickhouse":["Array","String"],"oracle":["VARCHAR2"],"postgresql":["array","text"]}'),
('TYPE', 'map', '映射', 'K-V 映射', 72, 'map', 'string', '{"mysql":["text","json"],"hive":["map","string"],"clickhouse":["Map","String"],"oracle":["CLOB"],"postgresql":["jsonb","text"]}'),
('TYPE', 'mobile', '手机号', '手机号，关联安全标准', 73, 'mobile', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'email', '邮箱', '邮箱，关联安全标准', 74, 'email', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'idcard', '身份证', '身份证，关联安全标准', 75, 'idcard', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'bankcard', '银行卡', '银行卡，关联安全标准', 76, 'bankcard', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'address', '地址', '地址', 77, 'address', 'string', '{"mysql":["varchar","text"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar","text"]}'),
('TYPE', 'ip', 'IP地址', 'IP 地址', 78, 'ip', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}'),
('TYPE', 'currency', '币种', '币种编码，如 CNY、USD', 79, 'currency', 'string', '{"mysql":["varchar","char"],"hive":["string"],"clickhouse":["String"],"oracle":["VARCHAR2"],"postgresql":["varchar"]}');

-- ============================================================
-- 三、单位标准（UNIT）—— 40 条
-- ============================================================
INSERT INTO yak_semantic_preset_template
(kind, std_code, std_name, description, sort_order, unit_code, unit_type)
VALUES
-- 金额类（6）
('UNIT', 'yuan', '元', '金额单位', 10, 'yuan', '金额'),
('UNIT', 'wan_yuan', '万元', '金额单位', 11, 'wan_yuan', '金额'),
('UNIT', 'yi_yuan', '亿元', '金额单位', 12, 'yi_yuan', '金额'),
('UNIT', 'cent', '分', '金额单位', 13, 'cent', '金额'),
('UNIT', 'dollar', '美元', '金额单位', 14, 'dollar', '金额'),
('UNIT', 'euro', '欧元', '金额单位', 15, 'euro', '金额'),
-- 数量类（8）
('UNIT', 'piece', '件', '数量单位', 20, 'piece', '数量'),
('UNIT', 'ge', '个', '数量单位', 21, 'ge', '数量'),
('UNIT', 'tai', '台', '数量单位', 22, 'tai', '数量'),
('UNIT', 'tao', '套', '数量单位', 23, 'tao', '数量'),
('UNIT', 'zhang', '张', '数量单位', 24, 'zhang', '数量'),
('UNIT', 'ben', '本', '数量单位', 25, 'ben', '数量'),
('UNIT', 'xiang', '箱', '数量单位', 26, 'xiang', '数量'),
('UNIT', 'bao', '包', '数量单位', 27, 'bao', '数量'),
-- 重量类（5）
('UNIT', 'ton', '吨', '重量单位', 30, 'ton', '重量'),
('UNIT', 'kg', '千克', '重量单位', 31, 'kg', '重量'),
('UNIT', 'g', '克', '重量单位', 32, 'g', '重量'),
('UNIT', 'mg', '毫克', '重量单位', 33, 'mg', '重量'),
('UNIT', 'lb', '磅', '重量单位', 34, 'lb', '重量'),
-- 体积类（5）
('UNIT', 'm3', '立方米', '体积单位', 40, 'm3', '体积'),
('UNIT', 'l', '升', '体积单位', 41, 'l', '体积'),
('UNIT', 'ml', '毫升', '体积单位', 42, 'ml', '体积'),
('UNIT', 'gal', '加仑', '体积单位', 43, 'gal', '体积'),
('UNIT', 'ft3', '立方英尺', '体积单位', 44, 'ft3', '体积'),
-- 长度类（5）
('UNIT', 'm', '米', '长度单位', 50, 'm', '长度'),
('UNIT', 'cm', '厘米', '长度单位', 51, 'cm', '长度'),
('UNIT', 'mm', '毫米', '长度单位', 52, 'mm', '长度'),
('UNIT', 'km', '千米', '长度单位', 53, 'km', '长度'),
('UNIT', 'inch', '英寸', '长度单位', 54, 'inch', '长度'),
-- 时间类（6）
('UNIT', 'ms', '毫秒', '时间单位', 60, 'ms', '时间'),
('UNIT', 'second', '秒', '时间单位', 61, 'second', '时间'),
('UNIT', 'minute', '分钟', '时间单位', 62, 'minute', '时间'),
('UNIT', 'hour', '小时', '时间单位', 63, 'hour', '时间'),
('UNIT', 'day', '天', '时间单位', 64, 'day', '时间'),
('UNIT', 'month', '月', '时间单位', 65, 'month', '时间'),
-- 比率类（3）
('UNIT', 'percent', '百分比', '比率单位', 70, 'percent', '比率'),
('UNIT', 'permille', '千分比', '比率单位', 71, 'permille', '比率'),
('UNIT', 'ratio', '比率', '比率单位', 72, 'ratio', '比率'),
-- 其他（2）
('UNIT', 'kwh', '千瓦时', '能量单位', 80, 'kwh', '能量'),
('UNIT', 'cal', '卡路里', '能量单位', 81, 'cal', '能量');

-- ============================================================
-- 四、安全标准（SECURITY）—— 25 条
-- ============================================================
INSERT INTO yak_semantic_preset_template
(kind, std_code, std_name, description, sort_order, level_code, mask_rule)
VALUES
-- 个人身份信息（8）
('SECURITY', 'mobile', '手机号', '手机号敏感分级与脱敏', 10, 'L2', '保留前3后4，中间掩码：138****8888'),
('SECURITY', 'idcard', '身份证', '身份证敏感分级与脱敏', 11, 'L3', '保留前6后4：110101********1234'),
('SECURITY', 'bankcard', '银行卡', '银行卡敏感分级与脱敏', 12, 'L3', '保留后4：**** **** **** 1234'),
('SECURITY', 'email', '邮箱', '邮箱敏感分级与脱敏', 13, 'L2', '保留首字符与域名：u***@example.com'),
('SECURITY', 'name', '姓名', '姓名敏感分级与脱敏', 14, 'L2', '保留姓氏：张**'),
('SECURITY', 'address', '地址', '地址敏感分级与脱敏', 15, 'L2', '保留省市：浙江省杭州市****'),
('SECURITY', 'passport', '护照', '护照敏感分级与脱敏', 16, 'L3', '保留前2后2：AB****CD'),
('SECURITY', 'social_security', '社保号', '社保号敏感分级与脱敏', 17, 'L3', '保留后4：****1234'),
-- 账号凭证（4）
('SECURITY', 'password', '密码', '密码敏感分级与脱敏', 20, 'L4', '完全掩码：******'),
('SECURITY', 'token', '令牌', '令牌敏感分级与脱敏', 21, 'L4', '完全掩码：******'),
('SECURITY', 'secret_key', '密钥', '密钥敏感分级与脱敏', 22, 'L4', '完全掩码：******'),
('SECURITY', 'private_key', '私钥', '私钥敏感分级与脱敏', 23, 'L4', '完全掩码：******'),
-- 财务信息（5）
('SECURITY', 'salary', '薪资', '薪资敏感分级与脱敏', 30, 'L3', '区间化：10k-20k'),
('SECURITY', 'income', '收入', '收入敏感分级与脱敏', 31, 'L3', '区间化'),
('SECURITY', 'tax_no', '税号', '税号敏感分级与脱敏', 32, 'L2', '保留后4：****1234'),
('SECURITY', 'invoice_no', '发票号', '发票号敏感分级与脱敏', 33, 'L2', '保留后4：****1234'),
('SECURITY', 'account_balance', '账户余额', '账户余额敏感分级与脱敏', 34, 'L3', '区间化'),
-- 设备与网络（4）
('SECURITY', 'ip', 'IP地址', 'IP地址敏感分级与脱敏', 40, 'L2', '保留前两段：192.168.*.*'),
('SECURITY', 'mac', 'MAC地址', 'MAC地址敏感分级与脱敏', 41, 'L2', '保留前3段：00:11:22:**:**:**'),
('SECURITY', 'imei', '设备IMEI', '设备IMEI敏感分级与脱敏', 42, 'L3', '保留后4：****1234'),
('SECURITY', 'device_id', '设备ID', '设备ID敏感分级与脱敏', 43, 'L2', '哈希处理'),
-- 其他敏感（4）
('SECURITY', 'location', '位置', '位置敏感分级与脱敏', 50, 'L2', '精度降低到城市级'),
('SECURITY', 'birthday', '生日', '生日敏感分级与脱敏', 51, 'L2', '只保留年份：1990-**-**'),
('SECURITY', 'medical', '医疗信息', '医疗信息敏感分级与脱敏', 52, 'L4', '完全掩码'),
('SECURITY', 'biometric', '生物特征', '生物特征敏感分级与脱敏', 53, 'L4', '完全掩码');


-- ============================================================================
-- 平台级预置标准模板（扩展版）—— 第二段
-- 码值 100+ + 口径 40 = 140+ 条
-- ============================================================================

-- ============================================================
-- 五、码值标准（CODE）—— 100+ 条
-- ============================================================
INSERT INTO yak_semantic_preset_template
(kind, std_code, std_name, description, sort_order, code_set_code, code_value, code_label)
VALUES
-- 通用枚举（14）
('CODE', 'yes_no_0', '是否-否', '通用是否枚举', 10, 'yes_no', '0', '否'),
('CODE', 'yes_no_1', '是否-是', '通用是否枚举', 11, 'yes_no', '1', '是'),
('CODE', 'gender_0', '性别-未知', '性别枚举', 20, 'gender', '0', '未知'),
('CODE', 'gender_1', '性别-男', '性别枚举', 21, 'gender', '1', '男'),
('CODE', 'gender_2', '性别-女', '性别枚举', 22, 'gender', '2', '女'),
('CODE', 'enable_status_0', '启用状态-禁用', '启用状态', 30, 'enable_status', '0', '禁用'),
('CODE', 'enable_status_1', '启用状态-启用', '启用状态', 31, 'enable_status', '1', '启用'),
('CODE', 'delete_flag_0', '删除标志-未删除', '删除标志', 40, 'delete_flag', '0', '未删除'),
('CODE', 'delete_flag_1', '删除标志-已删除', '删除标志', 41, 'delete_flag', '1', '已删除'),
('CODE', 'valid_flag_0', '有效标志-无效', '有效标志', 50, 'valid_flag', '0', '无效'),
('CODE', 'valid_flag_1', '有效标志-有效', '有效标志', 51, 'valid_flag', '1', '有效'),
('CODE', 'audit_status_0', '审核状态-待审核', '审核状态', 60, 'audit_status', '0', '待审核'),
('CODE', 'audit_status_1', '审核状态-通过', '审核状态', 61, 'audit_status', '1', '审核通过'),
('CODE', 'audit_status_2', '审核状态-拒绝', '审核状态', 62, 'audit_status', '2', '审核拒绝'),

-- 交易域（20）
('CODE', 'order_status_1', '订单状态-待支付', '订单状态', 100, 'order_status', '1', '待支付'),
('CODE', 'order_status_2', '订单状态-已支付', '订单状态', 101, 'order_status', '2', '已支付'),
('CODE', 'order_status_3', '订单状态-已发货', '订单状态', 102, 'order_status', '3', '已发货'),
('CODE', 'order_status_4', '订单状态-已完成', '订单状态', 103, 'order_status', '4', '已完成'),
('CODE', 'order_status_5', '订单状态-已取消', '订单状态', 104, 'order_status', '5', '已取消'),
('CODE', 'order_status_6', '订单状态-已退款', '订单状态', 105, 'order_status', '6', '已退款'),
('CODE', 'order_status_7', '订单状态-已关闭', '订单状态', 106, 'order_status', '7', '已关闭'),
('CODE', 'pay_status_0', '支付状态-未支付', '支付状态', 110, 'pay_status', '0', '未支付'),
('CODE', 'pay_status_1', '支付状态-已支付', '支付状态', 111, 'pay_status', '1', '已支付'),
('CODE', 'pay_status_2', '支付状态-已退款', '支付状态', 112, 'pay_status', '2', '已退款'),
('CODE', 'pay_status_3', '支付状态-部分退款', '支付状态', 113, 'pay_status', '3', '部分退款'),
('CODE', 'pay_channel_1', '支付渠道-支付宝', '支付渠道', 120, 'pay_channel', '1', '支付宝'),
('CODE', 'pay_channel_2', '支付渠道-微信', '支付渠道', 121, 'pay_channel', '2', '微信'),
('CODE', 'pay_channel_3', '支付渠道-银行卡', '支付渠道', 122, 'pay_channel', '3', '银行卡'),
('CODE', 'pay_channel_4', '支付渠道-余额', '支付渠道', 123, 'pay_channel', '4', '余额'),
('CODE', 'pay_channel_5', '支付渠道-货到付款', '支付渠道', 124, 'pay_channel', '5', '货到付款'),
('CODE', 'refund_status_0', '退款状态-未退款', '退款状态', 130, 'refund_status', '0', '未退款'),
('CODE', 'refund_status_1', '退款状态-退款中', '退款状态', 131, 'refund_status', '1', '退款中'),
('CODE', 'refund_status_2', '退款状态-已退款', '退款状态', 132, 'refund_status', '2', '已退款'),
('CODE', 'refund_status_3', '退款状态-退款失败', '退款状态', 133, 'refund_status', '3', '退款失败'),

-- 用户域（16）
('CODE', 'user_type_1', '用户类型-普通', '用户类型', 200, 'user_type', '1', '普通用户'),
('CODE', 'user_type_2', '用户类型-VIP', '用户类型', 201, 'user_type', '2', 'VIP用户'),
('CODE', 'user_type_3', '用户类型-企业', '用户类型', 202, 'user_type', '3', '企业用户'),
('CODE', 'user_type_4', '用户类型-内部', '用户类型', 203, 'user_type', '4', '内部用户'),
('CODE', 'member_level_1', '会员等级-青铜', '会员等级', 210, 'member_level', '1', '青铜'),
('CODE', 'member_level_2', '会员等级-白银', '会员等级', 211, 'member_level', '2', '白银'),
('CODE', 'member_level_3', '会员等级-黄金', '会员等级', 212, 'member_level', '3', '黄金'),
('CODE', 'member_level_4', '会员等级-铂金', '会员等级', 213, 'member_level', '4', '铂金'),
('CODE', 'member_level_5', '会员等级-钻石', '会员等级', 214, 'member_level', '5', '钻石'),
('CODE', 'user_status_0', '用户状态-禁用', '用户状态', 220, 'user_status', '0', '禁用'),
('CODE', 'user_status_1', '用户状态-正常', '用户状态', 221, 'user_status', '1', '正常'),
('CODE', 'user_status_2', '用户状态-冻结', '用户状态', 222, 'user_status', '2', '冻结'),
('CODE', 'register_source_1', '注册来源-APP', '注册来源', 230, 'register_source', '1', 'APP'),
('CODE', 'register_source_2', '注册来源-小程序', '注册来源', 231, 'register_source', '2', '小程序'),
('CODE', 'register_source_3', '注册来源-Web', '注册来源', 232, 'register_source', '3', 'Web'),
('CODE', 'register_source_4', '注册来源-线下', '注册来源', 233, 'register_source', '4', '线下'),

-- 商品域（15）
('CODE', 'item_status_0', '商品状态-下架', '商品状态', 300, 'item_status', '0', '下架'),
('CODE', 'item_status_1', '商品状态-上架', '商品状态', 301, 'item_status', '1', '上架'),
('CODE', 'item_status_2', '商品状态-售罄', '商品状态', 302, 'item_status', '2', '售罄'),
('CODE', 'item_status_3', '商品状态-删除', '商品状态', 303, 'item_status', '3', '删除'),
('CODE', 'item_type_1', '商品类型-实物', '商品类型', 310, 'item_type', '1', '实物'),
('CODE', 'item_type_2', '商品类型-虚拟', '商品类型', 311, 'item_type', '2', '虚拟'),
('CODE', 'item_type_3', '商品类型-服务', '商品类型', 312, 'item_type', '3', '服务'),
('CODE', 'shelf_status_0', '上架状态-未上架', '上架状态', 320, 'shelf_status', '0', '未上架'),
('CODE', 'shelf_status_1', '上架状态-已上架', '上架状态', 321, 'shelf_status', '1', '已上架'),
('CODE', 'audit_status_item_0', '商品审核-待审核', '商品审核', 330, 'audit_status_item', '0', '待审核'),
('CODE', 'audit_status_item_1', '商品审核-通过', '商品审核', 331, 'audit_status_item', '1', '审核通过'),
('CODE', 'audit_status_item_2', '商品审核-拒绝', '商品审核', 332, 'audit_status_item', '2', '审核拒绝'),
('CODE', 'stock_status_0', '库存状态-无库存', '库存状态', 340, 'stock_status', '0', '无库存'),
('CODE', 'stock_status_1', '库存状态-有库存', '库存状态', 341, 'stock_status', '1', '有库存'),
('CODE', 'stock_status_2', '库存状态-紧张', '库存状态', 342, 'stock_status', '2', '库存紧张'),

-- 物流域（10）
('CODE', 'delivery_status_0', '配送状态-待发货', '配送状态', 400, 'delivery_status', '0', '待发货'),
('CODE', 'delivery_status_1', '配送状态-已发货', '配送状态', 401, 'delivery_status', '1', '已发货'),
('CODE', 'delivery_status_2', '配送状态-运输中', '配送状态', 402, 'delivery_status', '2', '运输中'),
('CODE', 'delivery_status_3', '配送状态-已签收', '配送状态', 403, 'delivery_status', '3', '已签收'),
('CODE', 'delivery_status_4', '配送状态-已退回', '配送状态', 404, 'delivery_status', '4', '已退回'),
('CODE', 'logistics_company_1', '物流公司-顺丰', '物流公司', 410, 'logistics_company', '1', '顺丰'),
('CODE', 'logistics_company_2', '物流公司-圆通', '物流公司', 411, 'logistics_company', '2', '圆通'),
('CODE', 'logistics_company_3', '物流公司-中通', '物流公司', 412, 'logistics_company', '3', '中通'),
('CODE', 'logistics_company_4', '物流公司-韵达', '物流公司', 413, 'logistics_company', '4', '韵达'),
('CODE', 'logistics_company_5', '物流公司-京东', '物流公司', 414, 'logistics_company', '5', '京东物流'),

-- 营销域（15）
('CODE', 'promotion_type_1', '促销类型-满减', '促销类型', 500, 'promotion_type', '1', '满减'),
('CODE', 'promotion_type_2', '促销类型-折扣', '促销类型', 501, 'promotion_type', '2', '折扣'),
('CODE', 'promotion_type_3', '促销类型-秒杀', '促销类型', 502, 'promotion_type', '3', '秒杀'),
('CODE', 'promotion_type_4', '促销类型-拼团', '促销类型', 503, 'promotion_type', '4', '拼团'),
('CODE', 'promotion_type_5', '促销类型-优惠券', '促销类型', 504, 'promotion_type', '5', '优惠券'),
('CODE', 'coupon_status_0', '优惠券状态-未使用', '优惠券状态', 510, 'coupon_status', '0', '未使用'),
('CODE', 'coupon_status_1', '优惠券状态-已使用', '优惠券状态', 511, 'coupon_status', '1', '已使用'),
('CODE', 'coupon_status_2', '优惠券状态-已过期', '优惠券状态', 512, 'coupon_status', '2', '已过期'),
('CODE', 'coupon_type_1', '优惠券类型-满减券', '优惠券类型', 520, 'coupon_type', '1', '满减券'),
('CODE', 'coupon_type_2', '优惠券类型-折扣券', '优惠券类型', 521, 'coupon_type', '2', '折扣券'),
('CODE', 'coupon_type_3', '优惠券类型-现金券', '优惠券类型', 522, 'coupon_type', '3', '现金券'),
('CODE', 'activity_status_0', '活动状态-未开始', '活动状态', 530, 'activity_status', '0', '未开始'),
('CODE', 'activity_status_1', '活动状态-进行中', '活动状态', 531, 'activity_status', '1', '进行中'),
('CODE', 'activity_status_2', '活动状态-已结束', '活动状态', 532, 'activity_status', '2', '已结束'),
('CODE', 'activity_status_3', '活动状态-已取消', '活动状态', 533, 'activity_status', '3', '已取消'),

-- 财务域（13）
('CODE', 'settle_status_0', '结算状态-未结算', '结算状态', 600, 'settle_status', '0', '未结算'),
('CODE', 'settle_status_1', '结算状态-结算中', '结算状态', 601, 'settle_status', '1', '结算中'),
('CODE', 'settle_status_2', '结算状态-已结算', '结算状态', 602, 'settle_status', '2', '已结算'),
('CODE', 'invoice_type_1', '发票类型-增值税普通', '发票类型', 610, 'invoice_type', '1', '增值税普通发票'),
('CODE', 'invoice_type_2', '发票类型-增值税专用', '发票类型', 611, 'invoice_type', '2', '增值税专用发票'),
('CODE', 'invoice_type_3', '发票类型-电子', '发票类型', 612, 'invoice_type', '3', '电子发票'),
('CODE', 'invoice_status_0', '发票状态-未开票', '发票状态', 620, 'invoice_status', '0', '未开票'),
('CODE', 'invoice_status_1', '发票状态-已开票', '发票状态', 621, 'invoice_status', '1', '已开票'),
('CODE', 'invoice_status_2', '发票状态-已红冲', '发票状态', 622, 'invoice_status', '2', '已红冲'),
('CODE', 'currency_code_CNY', '币种-人民币', '币种', 630, 'currency_code', 'CNY', '人民币'),
('CODE', 'currency_code_USD', '币种-美元', '币种', 631, 'currency_code', 'USD', '美元'),
('CODE', 'currency_code_EUR', '币种-欧元', '币种', 632, 'currency_code', 'EUR', '欧元'),
('CODE', 'currency_code_HKD', '币种-港币', '币种', 633, 'currency_code', 'HKD', '港币'),

-- 系统域（13）
('CODE', 'task_status_0', '任务状态-待运行', '任务状态', 700, 'task_status', '0', '待运行'),
('CODE', 'task_status_1', '任务状态-运行中', '任务状态', 701, 'task_status', '1', '运行中'),
('CODE', 'task_status_2', '任务状态-成功', '任务状态', 702, 'task_status', '2', '成功'),
('CODE', 'task_status_3', '任务状态-失败', '任务状态', 703, 'task_status', '3', '失败'),
('CODE', 'task_status_4', '任务状态-已取消', '任务状态', 704, 'task_status', '4', '已取消'),
('CODE', 'priority_1', '优先级-低', '优先级', 710, 'priority', '1', '低'),
('CODE', 'priority_2', '优先级-中', '优先级', 711, 'priority', '2', '中'),
('CODE', 'priority_3', '优先级-高', '优先级', 712, 'priority', '3', '高'),
('CODE', 'priority_4', '优先级-紧急', '优先级', 713, 'priority', '4', '紧急'),
('CODE', 'log_level_1', '日志级别-DEBUG', '日志级别', 720, 'log_level', '1', 'DEBUG'),
('CODE', 'log_level_2', '日志级别-INFO', '日志级别', 721, 'log_level', '2', 'INFO'),
('CODE', 'log_level_3', '日志级别-WARN', '日志级别', 722, 'log_level', '3', 'WARN'),
('CODE', 'log_level_4', '日志级别-ERROR', '日志级别', 723, 'log_level', '4', 'ERROR');

-- ============================================================
-- 六、口径标准（CALIBER）—— 40 条
-- ============================================================
INSERT INTO yak_semantic_preset_template
(kind, std_code, std_name, description, sort_order, caliber_code, cal_rule, business_desc)
VALUES
-- 交易域（12）
('CALIBER', 'gmv', 'GMV', '成交总额口径', 10, 'gmv', 'SUM(order_amount)', '不含取消订单的订单金额总和'),
('CALIBER', 'gmv_tax', '含税GMV', '含税成交总额口径', 11, 'gmv_tax', 'SUM(order_amount_tax)', '含税订单金额总和'),
('CALIBER', 'order_cnt', '订单量', '订单数量口径', 20, 'order_cnt', 'COUNT(DISTINCT order_id)', '支付成功订单去重计数'),
('CALIBER', 'pay_amount', '支付金额', '支付金额口径', 21, 'pay_amount', 'SUM(pay_amount)', '实际支付金额'),
('CALIBER', 'refund_amount', '退款金额', '退款金额口径', 22, 'refund_amount', 'SUM(refund_amount)', '退款金额'),
('CALIBER', 'net_gmv', '净GMV', '净成交总额口径', 23, 'net_gmv', 'SUM(order_amount) - SUM(refund_amount)', 'GMV 减退款'),
('CALIBER', 'avg_order_amount', '客单价', '客单价口径', 24, 'avg_order_amount', 'SUM(order_amount) / COUNT(DISTINCT order_id)', 'GMV / 订单量'),
('CALIBER', 'refund_rate', '退款率', '退款率口径', 25, 'refund_rate', 'refund_cnt / order_cnt', '退款订单数 / 订单总数'),
('CALIBER', 'cancel_rate', '取消率', '取消率口径', 26, 'cancel_rate', 'cancel_cnt / order_cnt', '取消订单数 / 订单总数'),
('CALIBER', 'pay_rate', '支付率', '支付率口径', 27, 'pay_rate', 'pay_cnt / order_cnt', '支付订单数 / 订单总数'),
('CALIBER', 'order_user_cnt', '下单用户数', '下单用户数口径', 28, 'order_user_cnt', 'COUNT(DISTINCT user_id)', '去重下单用户'),
('CALIBER', 'pay_user_cnt', '支付用户数', '支付用户数口径', 29, 'pay_user_cnt', 'COUNT(DISTINCT user_id)', '去重支付用户'),

-- 用户域（8）
('CALIBER', 'uv', 'UV', '独立访客口径', 100, 'uv', 'COUNT(DISTINCT user_id)', '去重访客数'),
('CALIBER', 'pv', 'PV', '页面浏览量口径', 101, 'pv', 'COUNT(1)', '页面浏览次数'),
('CALIBER', 'new_user_cnt', '新增用户数', '新增用户数口径', 102, 'new_user_cnt', 'COUNT(DISTINCT user_id)', '首次注册用户'),
('CALIBER', 'active_user_cnt', '活跃用户数', '活跃用户数口径', 103, 'active_user_cnt', 'COUNT(DISTINCT user_id)', '有行为用户'),
('CALIBER', 'retention_rate', '留存率', '留存率口径', 104, 'retention_rate', 'active_user_cnt / new_user_cnt', '留存用户 / 新增用户'),
('CALIBER', 'churn_rate', '流失率', '流失率口径', 105, 'churn_rate', 'churn_cnt / total_user_cnt', '流失用户 / 总用户'),
('CALIBER', 'arpu', 'ARPU', '人均消费口径', 106, 'arpu', 'SUM(order_amount) / COUNT(DISTINCT user_id)', '人均消费'),
('CALIBER', 'user_avg_order_cnt', '人均订单数', '人均订单数口径', 107, 'user_avg_order_cnt', 'COUNT(order_id) / COUNT(DISTINCT user_id)', '订单数 / 用户数'),

-- 商品域（8）
('CALIBER', 'item_sale_cnt', '商品销量', '商品销量口径', 200, 'item_sale_cnt', 'SUM(quantity)', '商品销售件数'),
('CALIBER', 'item_sale_amount', '商品销售额', '商品销售额口径', 201, 'item_sale_amount', 'SUM(order_amount)', '商品销售金额'),
('CALIBER', 'item_uv', '商品UV', '商品访客口径', 202, 'item_uv', 'COUNT(DISTINCT user_id)', '商品访客数'),
('CALIBER', 'item_pv', '商品PV', '商品浏览口径', 203, 'item_pv', 'COUNT(1)', '商品浏览量'),
('CALIBER', 'item_conversion_rate', '商品转化率', '商品转化率口径', 204, 'item_conversion_rate', 'item_sale_cnt / item_uv', '销量 / 访客'),
('CALIBER', 'stock_turnover_rate', '库存周转率', '库存周转率口径', 205, 'stock_turnover_rate', 'sale_cnt / avg_stock', '销量 / 平均库存'),
('CALIBER', 'item_return_rate', '商品退货率', '商品退货率口径', 206, 'item_return_rate', 'return_cnt / sale_cnt', '退货数 / 销量'),
('CALIBER', 'item_avg_price', '商品均价', '商品均价口径', 207, 'item_avg_price', 'SUM(order_amount) / SUM(quantity)', '金额 / 数量'),

-- 营销域（6）
('CALIBER', 'coupon_use_rate', '优惠券使用率', '优惠券使用率口径', 300, 'coupon_use_rate', 'used_cnt / issued_cnt', '使用 / 发放'),
('CALIBER', 'promotion_gmv', '活动GMV', '活动GMV口径', 301, 'promotion_gmv', 'SUM(order_amount)', '活动期间 GMV'),
('CALIBER', 'promotion_order_cnt', '活动订单量', '活动订单量口径', 302, 'promotion_order_cnt', 'COUNT(DISTINCT order_id)', '活动期间订单数'),
('CALIBER', 'roi', 'ROI', '投入产出比口径', 303, 'roi', 'promotion_gmv / promotion_cost', 'GMV / 投放成本'),
('CALIBER', 'conversion_rate', '转化率', '转化率口径', 304, 'conversion_rate', 'order_cnt / uv', '订单量 / 访客数'),
('CALIBER', 'click_rate', '点击率', '点击率口径', 305, 'click_rate', 'click_cnt / pv', '点击数 / 浏览量'),

-- 通用（6）
('CALIBER', 'count', '计数', '通用计数口径', 400, 'count', 'COUNT(1)', '总数'),
('CALIBER', 'sum_amount', '金额合计', '金额合计口径', 401, 'sum_amount', 'SUM(amount)', '金额汇总'),
('CALIBER', 'avg_amount', '金额均值', '金额均值口径', 402, 'avg_amount', 'AVG(amount)', '金额平均'),
('CALIBER', 'max_amount', '金额最大', '金额最大口径', 403, 'max_amount', 'MAX(amount)', '金额最大'),
('CALIBER', 'min_amount', '金额最小', '金额最小口径', 404, 'min_amount', 'MIN(amount)', '金额最小'),
('CALIBER', 'distinct_cnt', '去重计数', '去重计数口径', 405, 'distinct_cnt', 'COUNT(DISTINCT field)', '去重计数');