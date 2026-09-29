-- Lineage tracking: record how the model's fields were imported.
-- import_mode: MANUAL / SOURCE_TABLE / MODEL / BUSINESS_PROCESS
-- source_model_id: when import_mode = MODEL, the model fields were copied from.

ALTER TABLE yak_modeling_model
    ADD COLUMN import_mode VARCHAR(32) NULL COMMENT '字段导入方式(血缘追溯):MANUAL/SOURCE_TABLE/MODEL/BUSINESS_PROCESS',
    ADD COLUMN source_model_id BIGINT NULL COMMENT '来源模型ID(import_mode=MODEL时记录,血缘追溯)';

CREATE INDEX idx_yak_modeling_model_source_model ON yak_modeling_model (source_model_id);
