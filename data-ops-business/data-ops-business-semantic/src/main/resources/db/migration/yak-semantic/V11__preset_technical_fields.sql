-- 预置技术字段为标准字段(process_time/event_time),用于「标准发现」匹配。
-- 2026-09-17:取代 v1 的 etl_time,改为 process_time(数据处理时间) + event_time(业务事件时间)。
-- 注:yak_project 在 yak_security 库,此处从同库的 yak_semantic_standard 取去重 project_id。

-- process_time:数据处理时间
INSERT INTO yak_semantic_field 
    (project_id, field_code, field_name, role, status, data_type, 
     std_type_id, business_desc, source, version, created_by)
SELECT 
    pid AS project_id,
    'process_time' AS field_code,
    '数据处理时间' AS field_name,
    'PROCESS' AS role,
    'ENABLED' AS status,
    'DATETIME' AS data_type,
    (SELECT s.id FROM yak_semantic_standard s WHERE s.kind = 'TYPE' AND s.std_code = 'datetime' AND s.project_id = pid LIMIT 1) AS std_type_id,
    'ETL处理时间,记录数据写入时间' AS business_desc,
    'PRESET' AS source,
    1 AS version,
    'system' AS created_by
FROM (SELECT DISTINCT project_id AS pid FROM yak_semantic_standard) projects
WHERE NOT EXISTS (
    SELECT 1 FROM yak_semantic_field f 
    WHERE f.project_id = projects.pid AND f.field_code = 'process_time'
);

-- event_time:业务事件时间
INSERT INTO yak_semantic_field 
    (project_id, field_code, field_name, role, status, data_type, 
     std_type_id, business_desc, source, version, created_by)
SELECT 
    pid AS project_id,
    'event_time' AS field_code,
    '业务事件时间' AS field_name,
    'PROCESS' AS role,
    'ENABLED' AS status,
    'DATETIME' AS data_type,
    (SELECT s.id FROM yak_semantic_standard s WHERE s.kind = 'TYPE' AND s.std_code = 'datetime' AND s.project_id = pid LIMIT 1) AS std_type_id,
    '业务事件发生时间,分区表即分区时间' AS business_desc,
    'PRESET' AS source,
    1 AS version,
    'system' AS created_by
FROM (SELECT DISTINCT project_id AS pid FROM yak_semantic_standard) projects
WHERE NOT EXISTS (
    SELECT 1 FROM yak_semantic_field f 
    WHERE f.project_id = projects.pid AND f.field_code = 'event_time'
);
