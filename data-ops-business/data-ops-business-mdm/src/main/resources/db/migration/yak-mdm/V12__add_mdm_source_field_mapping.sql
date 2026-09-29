-- R0(reuse-plan): 来源绑定增加字段映射(JSON),口径 = 属性编码 -> 源列名。
-- NULL/空 = 约定回退"属性编码与源列同名";加工 SQL 生成器按此映射产出 SELECT 列。
-- 幂等守卫:老开发库执行过已回退的旧 V5(工单 54),同名列已存在时跳过。
SET @has_col = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'yak_mdm_source'
      AND COLUMN_NAME = 'field_mapping');
SET @ddl = IF(
    @has_col = 0,
    'ALTER TABLE yak_mdm_source ADD COLUMN field_mapping JSON NULL COMMENT ''属性编码→源列名映射,NULL=同名回退'' AFTER source_table',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
