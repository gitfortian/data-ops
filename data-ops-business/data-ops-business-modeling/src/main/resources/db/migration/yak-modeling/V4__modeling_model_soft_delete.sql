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
