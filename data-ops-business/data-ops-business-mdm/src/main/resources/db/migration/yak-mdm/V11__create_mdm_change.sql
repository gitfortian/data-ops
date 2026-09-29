-- Ticket 60: 主数据变更审批(申请/审批流/版本)
CREATE TABLE IF NOT EXISTS `yak_mdm_change` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`      BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`       BIGINT       NOT NULL COMMENT '实体',
  `master_id`       VARCHAR(128) NOT NULL COMMENT '目标主数据记录',
  `change_type`     VARCHAR(16)  NOT NULL COMMENT '变更类型:CREATE/UPDATE/MERGE/DELETE',
  `change_content`  TEXT         NOT NULL COMMENT '变更内容(JSON,含属性值对比)',
  `approval_level`  INT          NOT NULL DEFAULT 1 COMMENT '审批级别:1/2',
  `approval_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '审批状态:PENDING/APPROVED/REJECTED/WITHDRAWN',
  `applicant`       VARCHAR(64)  NULL COMMENT '申请人',
  `approver`        VARCHAR(64)  NULL COMMENT '审批人(当前审批人)',
  `approval_comment` TEXT        NULL COMMENT '审批意见',
  `approval_time`   DATETIME     NULL COMMENT '审批时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mdm_change_entity` (`project_id`, `entity_id`),
  KEY `idx_mdm_change_applicant` (`project_id`, `applicant`),
  KEY `idx_mdm_change_master` (`project_id`, `entity_id`, `master_id`),
  KEY `idx_mdm_change_status` (`approval_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据变更审批';
