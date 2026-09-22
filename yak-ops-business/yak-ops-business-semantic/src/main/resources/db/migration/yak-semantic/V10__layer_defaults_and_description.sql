-- Warehouse layer usability (ticket 37, 2026-09-16): layer description +
-- default-layer initialization values (db/naming/partition/storage/lifecycle).
-- Templates carry the naming standard CODE (platform-level); initialization
-- resolves it to the project's NAMING standard id (skip when absent).

ALTER TABLE yak_semantic_layer
    ADD COLUMN description VARCHAR(512) NULL COMMENT '描述' AFTER lifecycle_days;

ALTER TABLE yak_semantic_layer_template
    ADD COLUMN database_name VARCHAR(128) NULL COMMENT '默认库名',
    ADD COLUMN std_naming_code VARCHAR(64) NULL COMMENT '命名标准编码(初始化时解析为项目内 ID)',
    ADD COLUMN default_partition VARCHAR(256) NULL COMMENT '默认分区表达式',
    ADD COLUMN storage_format VARCHAR(64) NULL COMMENT '存储格式',
    ADD COLUMN lifecycle_days INT NULL COMMENT '生命周期(天),空=永久';

UPDATE yak_semantic_layer_template SET
    database_name = 'ods_db',
    std_naming_code = 'naming_table_ods',
    default_partition = 'dt=yyyyMMdd',
    storage_format = 'Parquet',
    lifecycle_days = NULL
WHERE layer_code = 'ODS';

UPDATE yak_semantic_layer_template SET
    database_name = 'dwd_db',
    std_naming_code = 'naming_table_dwd',
    default_partition = 'dt=yyyyMMdd',
    storage_format = 'Parquet',
    lifecycle_days = 365
WHERE layer_code = 'DWD';

UPDATE yak_semantic_layer_template SET
    database_name = 'dws_db',
    std_naming_code = 'naming_table_dws',
    default_partition = 'dt=yyyyMMdd',
    storage_format = 'Parquet',
    lifecycle_days = 365
WHERE layer_code = 'DWS';

UPDATE yak_semantic_layer_template SET
    database_name = 'ads_db',
    std_naming_code = 'naming_table_ads',
    default_partition = NULL,
    storage_format = 'Parquet',
    lifecycle_days = 180
WHERE layer_code = 'ADS';
