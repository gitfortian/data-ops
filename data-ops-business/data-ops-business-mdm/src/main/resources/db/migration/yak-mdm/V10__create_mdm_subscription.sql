-- Ticket 59: 主数据订阅管理(系统订阅实体变更,变更时通知)
CREATE TABLE IF NOT EXISTS `yak_mdm_subscription` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`      BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`       BIGINT       NOT NULL COMMENT '订阅实体',
  `subscriber_code` VARCHAR(64)  NOT NULL COMMENT '订阅方系统编码',
  `subscriber_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '订阅方名称',
  `notify_mode`     VARCHAR(16)  NOT NULL DEFAULT 'EVENT' COMMENT '通知方式:EVENT/WEBHOOK',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE/DISABLED',
  `created_by`      VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mdm_sub_entity` (`project_id`, `entity_id`),
  UNIQUE KEY `uk_mdm_sub_subscriber` (`project_id`, `entity_id`, `subscriber_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据订阅管理';
