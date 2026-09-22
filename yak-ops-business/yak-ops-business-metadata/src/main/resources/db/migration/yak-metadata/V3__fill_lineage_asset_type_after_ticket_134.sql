-- Metadata center V3 (ticket 134): 补齐三行 lineage_asset_type。
--
-- V2 把 databaseService / database / domain 三行的 lineage_asset_type 留成 NULL，当时的口径是
-- "LineageAssetType 还没有这三个常量"（后果 1：asset_type NOT NULL 且 lineage 读行逐行 valueOf，
-- 写进一个枚举里没有的名字，行存得下，炸的是别人的血缘查询）。134 已给该枚举补上
-- DATABASE_SERVICE / DATABASE / DOMAIN，这三行随之落地，NULL 的含义到此结束。
--
-- description 里"待 ticket 134"一并清掉：留着会让下一个人以为还欠一张票。
-- 幂等：按唯一键 uk_yak_md_type_name 定位，重复执行结果一致；库里没有这三行时影响 0 行、不报错。

UPDATE yak_md_type_def
   SET lineage_asset_type = 'DATABASE_SERVICE',
       description = '一个被采集的数据源；键 datasource:{dataSourceId}'
 WHERE type_name = 'databaseService';

UPDATE yak_md_type_def
   SET lineage_asset_type = 'DATABASE',
       description = '键 database:{dataSourceId}:{db}'
 WHERE type_name = 'database';

UPDATE yak_md_type_def
   SET lineage_asset_type = 'DOMAIN',
       description = 'semantic own；键 semantic:domain:{domainId}'
 WHERE type_name = 'domain';
