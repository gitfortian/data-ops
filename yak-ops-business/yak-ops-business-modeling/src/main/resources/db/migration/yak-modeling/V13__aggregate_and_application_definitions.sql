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
