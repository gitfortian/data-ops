-- 回收站要回答「入站前最后一次是谁改的」：created_by 只记到创建人，deleted_by 只记到删除动作，
-- 中间若干轮编辑无人可查。update_time 由建表 DDL 的 ON UPDATE 自动维护，
-- updated_by 只能由应用侧写入（见 ModelRepositoryAdapter 的 live 更新入口）。
ALTER TABLE yak_modeling_model
    ADD COLUMN updated_by VARCHAR(128) NULL COMMENT '最后更新人(应用侧写入;NULL=该列上线前的历史行)';
