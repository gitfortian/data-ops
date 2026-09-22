-- R1 收尾:清理已回退工单 54 遗留在 yak_mdm_source 的采集配置列
-- (collect_mode/collect_freq/config_status;field_mapping 已被 R0 以新口径正式启用,保留)。
-- 开发库才有这些残留列,全新库不存在,故逐列 information_schema 幂等守卫。

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='yak_mdm_source' AND COLUMN_NAME='collect_mode');
SET @ddl = IF(@has_col > 0,
    'ALTER TABLE yak_mdm_source DROP COLUMN collect_mode',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='yak_mdm_source' AND COLUMN_NAME='collect_freq');
SET @ddl = IF(@has_col > 0,
    'ALTER TABLE yak_mdm_source DROP COLUMN collect_freq',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='yak_mdm_source' AND COLUMN_NAME='config_status');
SET @ddl = IF(@has_col > 0,
    'ALTER TABLE yak_mdm_source DROP COLUMN config_status',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
