-- Ticket 58: 主数据分发配置(实体级,目标系统+方式+频率)
CREATE TABLE IF NOT EXISTS `yak_mdm_distribution` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`           BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`            BIGINT       NOT NULL COMMENT '主数据实体',
  `target_system`        VARCHAR(64)  NOT NULL COMMENT '目标系统编码',
  `target_name`          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '目标系统名称',
  `distribute_mode`      VARCHAR(16)  NOT NULL COMMENT '分发方式:API/MESSAGE/FILE',
  `distribute_freq`      VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '分发频率:MANUAL/DAILY/HOURLY',
  `distribute_scope`     VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT '分发范围:FULL/INCREMENTAL',
  `status`               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/ACTIVE/DISABLED',
  `last_distribute_time` DATETIME     NULL COMMENT '最近分发时间',
  `last_distribute_count` INT         NULL DEFAULT 0 COMMENT '最近分发条数',
  `last_distribute_fail`  INT         NULL DEFAULT 0 COMMENT '最近分发失败条数',
  `created_by`           VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mdm_dist_entity` (`project_id`, `entity_id`),
  UNIQUE KEY `uk_mdm_dist_target` (`project_id`, `entity_id`, `target_system`, `distribute_mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据分发配置';
