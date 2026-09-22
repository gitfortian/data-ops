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
