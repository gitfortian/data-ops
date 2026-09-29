-- 发布快照扩为全量：版本行增加模型元数据快照(名称/描述/分层/业务域/方言)。
-- structure_json 仍只承载表结构；消费方读快照时无需回读活主表即可还原发布当时的模型信息。

ALTER TABLE yak_modeling_model_version
    ADD COLUMN meta_json LONGTEXT NULL COMMENT '发布时模型元数据快照(JSON)';
