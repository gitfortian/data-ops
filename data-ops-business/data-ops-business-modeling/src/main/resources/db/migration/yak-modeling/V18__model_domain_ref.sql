-- 业务域引用(semantic 松散引用):新建模型向导选择的业务域需要持久化,
-- 此前 domainId 在 CreateRequest 被丢弃,列表/树按域过滤只能经业务过程间接推导。
-- 历史行按已关联的业务过程回填一次。

ALTER TABLE yak_modeling_model
    ADD COLUMN domain_id BIGINT NULL COMMENT '业务域(semantic 松散引用,新建模型向导写入)';

CREATE INDEX idx_yak_modeling_model_domain ON yak_modeling_model (domain_id);

UPDATE yak_modeling_model m
    JOIN yak_semantic_process p ON p.id = m.process_id
   SET m.domain_id = p.domain_id
 WHERE m.domain_id IS NULL;
