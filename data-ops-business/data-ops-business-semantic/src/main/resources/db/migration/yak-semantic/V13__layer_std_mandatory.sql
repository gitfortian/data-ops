-- M2-5 定标强制名单入分层配置(docs/PLATFORM_CORE_FLOW.md 决策 3):
-- 强制口径不再由代码前缀判定,由「数仓分层」每条配置携带;默认强制,存量 ODS 贴源层批量置免强制。
ALTER TABLE yak_semantic_layer
    ADD COLUMN std_mandatory TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否强制字段落标(M2-5 定标闸门口径)' AFTER status;

UPDATE yak_semantic_layer
   SET std_mandatory = 0
 WHERE UPPER(layer_code) LIKE 'ODS%';

ALTER TABLE yak_semantic_layer_template
    ADD COLUMN std_mandatory TINYINT(1) NOT NULL DEFAULT 1 COMMENT '初始化默认值:是否强制字段落标' AFTER sort_order;

UPDATE yak_semantic_layer_template
   SET std_mandatory = 0
 WHERE UPPER(layer_code) LIKE 'ODS%';
