-- Warehouse modeling review fixes: hard DB backstop for the live-row code
-- uniqueness race. Soft delete rewrites the stored code (original kept in
-- original_code) so a single (project_id, model_code) unique key can stay,
-- while soft-deleted rows no longer block re-creating the same code.

ALTER TABLE yak_modeling_model
    ADD COLUMN original_code VARCHAR(128) NULL COMMENT '软删除前的原始编码,存活行为 NULL',
    ADD UNIQUE KEY uk_yak_modeling_model_project_code (project_id, model_code);
