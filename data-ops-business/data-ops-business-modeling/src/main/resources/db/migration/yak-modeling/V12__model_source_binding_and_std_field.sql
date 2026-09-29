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
