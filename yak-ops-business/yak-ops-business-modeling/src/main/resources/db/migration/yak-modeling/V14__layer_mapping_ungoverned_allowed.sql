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
