-- 目录跟随业务域：模型保存时按所选业务域自动落/复用目录，目录树与业务域树一一对应。
-- 名称可变（域改名要能跟上），所以用 domain_id 显式绑定，而不是按名称匹配。

ALTER TABLE yak_modeling_directory
    ADD COLUMN domain_id BIGINT NULL COMMENT '绑定的业务域(semantic 松散引用;目录随域自动生成)';

CREATE INDEX idx_yak_modeling_directory_domain ON yak_modeling_directory (project_id, domain_id);
