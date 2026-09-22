-- R4(reuse-plan,接审批中心): 变更记录关联审批单 + 记录版本快照表。
-- 快照口径 = ModelVersionService 发布模式的 MDM 版:全量 attributes JSON,uk 幂等,线性追加。
CREATE TABLE IF NOT EXISTS `yak_mdm_record_version` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`  BIGINT       NOT NULL COMMENT '项目空间',
  `entity_id`   BIGINT       NOT NULL COMMENT '实体',
  `master_id`   VARCHAR(128) NOT NULL COMMENT '主数据标识',
  `version`     INT          NOT NULL COMMENT '记录版本号(与 yak_mdm_record.version 对齐)',
  `attributes`  TEXT         NOT NULL COMMENT '该版本属性全量快照(JSON)',
  `status`      VARCHAR(16)  NOT NULL COMMENT '该版本记录状态',
  `change_id`   BIGINT       NULL COMMENT '产生该版本的变更申请(基线快照为 NULL)',
  `operator`    VARCHAR(64)  NULL COMMENT '操作人(审批人/基线归属申请人)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mdm_version` (`project_id`, `entity_id`, `master_id`, `version`),
  KEY `idx_mdm_version_master` (`project_id`, `entity_id`, `master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录版本快照(R4)';

-- 变更单挂审批中心实例 id(在途唯一/两级推进由中心引擎负责,MDM 不自建)
SET @has_col = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'yak_mdm_change'
      AND COLUMN_NAME = 'instance_id');
SET @ddl = IF(
    @has_col = 0,
    'ALTER TABLE yak_mdm_change ADD COLUMN instance_id BIGINT NULL COMMENT ''审批中心单据 id(yak_approval_instance.id)'' AFTER approval_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
