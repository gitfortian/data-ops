-- F-008: organize existing capabilities by user task (#288 / #289).
-- Stable leaf codes, URLs, permissions and row IDs preserve existing grants.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('integration', '数据接入与集成', NULL, NULL, 'connector', 1, 10, 1, 1, NULL, '数据接入与集成', '${appName}'),
('modeling', '标准、指标与建模', NULL, NULL, 'project', 1, 20, 1, 1, NULL, '标准、指标与建模', '${appName}'),
('semantic', '业务语义', 'modeling', NULL, 'knowledge', 1, 10, 1, 1, NULL, '业务语义', '${appName}'),
('metric', '指标', 'modeling', NULL, 'report', 1, 30, 1, 1, NULL, '指标', '${appName}'),
('development', '开发与运行', NULL, NULL, 'workflow', 1, 30, 1, 1, NULL, '开发与运行', '${appName}'),
('workflow', '工作流', 'development', NULL, 'instance', 1, 40, 1, 1, NULL, '工作流', '${appName}'),
('data-asset', '数据资产与治理', NULL, NULL, 'quality', 1, 40, 1, 1, NULL, '数据资产与治理', '${appName}'),
('data-governance', '资产管理', 'data-asset', NULL, 'database', 1, 30, 1, 1, NULL, '资产管理', '${appName}'),
('data-metadata', '技术元数据', 'data-asset', NULL, 'sync', 1, 40, 1, 1, NULL, '技术元数据', '${appName}'),
('data-quality', '数据质量', 'data-asset', NULL, 'quality', 1, 60, 1, 1, NULL, '数据质量', '${appName}'),
('data-security', '数据安全', 'data-asset', NULL, 'monitor', 1, 70, 1, 1, NULL, '数据安全', '${appName}'),
('data-lifecycle', '数据生命周期', 'data-asset', NULL, 'realtime', 1, 80, 1, 1, NULL, '数据生命周期', '${appName}'),
('mdm', '专业方案：主数据管理', 'data-asset', NULL, 'database', 1, 90, 1, 1, NULL, '专业方案：主数据管理', '${appName}'),
('data-analysis', '数据消费与服务', NULL, NULL, 'insight', 1, 50, 1, 1, NULL, '数据消费与服务', '${appName}'),
('data-service', 'API 服务', 'data-analysis', NULL, 'api', 1, 30, 1, 1, NULL, 'API 服务', '${appName}'),
('analysis-presentation', '分析展示', 'data-analysis', NULL, 'insight', 1, 40, 1, 1, NULL, '分析展示', '${appName}'),
('home', '首页', NULL, '/home', 'home', 2, 0, 1, 1, NULL, '首页', '${appName}'),
('data-source', '数据源', 'integration', '/data-source', 'database', 2, 10, 1, 1, 'resource:data-source:read', '数据源', '${appName}'),
('sql-execution-audit', 'SQL 执行记录', 'integration', '/sql-execution-audit', 'monitor', 2, 50, 1, 1, 'resource:sql-execution:read', 'SQL 执行记录', '${appName}'),
('dashboard', '仪表盘', 'analysis-presentation', '/dashboard', 'insight', 2, 10, 1, 1, NULL, '仪表盘', '${appName}'),
('consumption-catalog', '数据产品目录', 'data-analysis', '/data-analysis/consumption', 'knowledge', 2, 10, 1, 1, 'data-asset:read', '数据产品目录', '${appName}'),
('dataset-management', '数据集管理', 'data-analysis', '/dataset', 'database', 2, 20, 1, 1, NULL, '数据集管理', '${appName}'),
('data-analysis-lineage', '数据血缘', 'data-asset', '/data-analysis/lineage', 'workflow', 2, 50, 1, 1, NULL, '数据血缘', '${appName}'),
('digital-screen', '数字大屏', 'analysis-presentation', '/digital-screen', 'insight', 2, 20, 1, 1, NULL, '数字大屏', '${appName}'),
('data-service-api', 'API 服务目录', 'data-service', '/data-service', 'api', 2, 10, 1, 1, 'data-service:read', 'API 服务目录', '${appName}'),
('data-service-access', '调用方与密钥', 'data-service', '/data-service/access', 'api', 2, 20, 1, 1, 'data-service:access', '调用方与密钥', '${appName}'),
('data-service-debug', 'API 调试', 'data-service', '/data-service/debug', 'api', 2, 30, 1, 1, 'data-service:runtime', 'API 调试', '${appName}'),
('data-service-overview', '运行概览', 'data-service', '/data-service/overview', 'monitor', 2, 40, 1, 1, 'data-service:observe', '运行概览', '${appName}'),
('data-service-logs', '调用记录', 'data-service', '/data-service/logs', 'report', 2, 50, 1, 1, 'data-service:observe', '调用记录', '${appName}'),
('data-development', '开发工作台', 'development', '/data-development', 'api', 2, 10, 1, 1, 'data-development:read', '开发工作台', '${appName}'),
('data-development-release', '发布中心', 'development', '/data-development/releases', 'report', 2, 20, 1, 1, 'data-development:read', '发布中心', '${appName}'),
('data-development-execution', '开发运行记录', 'development', '/data-development/executions', 'report', 2, 30, 1, 1, 'data-development:read', '开发运行记录', '${appName}'),
('workflow-definition', '工作流定义', 'workflow', '/workflow/definitions', 'workflow', 2, 10, 1, 1, NULL, '工作流定义', '${appName}'),
('workflow-instances', '工作流实例', 'workflow', '/workflow/instances', 'instance', 2, 30, 1, 1, NULL, '工作流实例', '${appName}'),
('modeling-workspace', '模型工作台', 'modeling', '/modeling', 'database', 2, 20, 1, 1, 'modeling:read', '模型工作台', '${appName}'),
('semantic-standard', '数据标准', 'semantic', '/semantic/standards', 'database', 2, 30, 1, 1, 'semantic:read', '数据标准', '${appName}'),
('semantic-domain', '业务域', 'semantic', '/semantic/domains', 'project', 2, 10, 1, 1, 'semantic:read', '业务域', '${appName}'),
('semantic-process', '业务过程', 'semantic', '/semantic/processes', 'workflow', 2, 20, 1, 1, 'semantic:read', '业务过程', '${appName}'),
('semantic-field', '标准字段', 'semantic', '/semantic/fields', 'knowledge', 2, 40, 1, 1, 'semantic:read', '标准字段', '${appName}'),
('semantic-layer', '数仓分层', 'semantic', '/semantic/layers', 'database', 2, 50, 1, 1, 'semantic:read', '数仓分层', '${appName}'),
('mdm-overview', '主数据总览', 'mdm', '/mdm/overview', 'database', 2, 10, 1, 1, 'mdm:read', '主数据总览', '${appName}'),
('mdm-modeling', '主数据建模', 'mdm', '/mdm/modeling', 'database', 2, 20, 1, 1, 'mdm:read', '主数据建模', '${appName}'),
('mdm-identification', '主数据识别', 'mdm', '/mdm/identification', 'instance', 2, 30, 1, 1, 'mdm:read', '主数据识别', '${appName}'),
('mdm-cleansing', '主数据清洗', 'mdm', '/mdm/cleansing', 'quality', 2, 40, 1, 1, 'mdm:read', '主数据清洗', '${appName}'),
('mdm-approval', '主数据变更', 'mdm', '/mdm/approval', 'workflow', 2, 50, 1, 1, 'mdm:read', '主数据变更', '${appName}'),
('metric-manage', '指标管理', 'metric', '/metric/manage', 'report', 2, 10, 1, 1, 'metric:read', '指标管理', '${appName}'),
('metric-lineage', '指标血缘', 'metric', '/metric/lineage', 'workflow', 2, 20, 1, 1, 'metric:read', '指标血缘', '${appName}'),
('metric-impact', '影响分析', 'metric', '/metric/impact', 'monitor', 2, 30, 1, 1, 'metric:read', '影响分析', '${appName}'),
('metric-service', '指标标签与统计', 'metric', '/metric/service', 'knowledge', 2, 40, 1, 1, 'metric:read', '指标标签与统计', '${appName}'),
('data-lifecycle-policy', '生命周期策略', 'data-lifecycle', '/data-lifecycle/policy', 'database', 2, 10, 1, 1, 'data-lifecycle:read', '生命周期策略', '${appName}'),
('data-lifecycle-monitor', 'TTL 运行监控', 'data-lifecycle', '/data-lifecycle/monitor', 'monitor', 2, 20, 1, 1, 'data-lifecycle:read', 'TTL 运行监控', '${appName}'),
('data-lifecycle-storage', '存储统计', 'data-lifecycle', '/data-lifecycle/storage', 'insight', 2, 30, 1, 1, 'data-lifecycle:read', '存储统计', '${appName}'),
('data-security-overview', '数据安全总览', 'data-security', '/data-security/overview', 'monitor', 2, 10, 1, 1, 'data-security:read', '数据安全总览', '${appName}'),
('data-security-classification', '分级分类', 'data-security', '/data-security/classification', 'database', 2, 20, 1, 1, 'data-security:read', '分级分类', '${appName}'),
('data-security-access', '数据访问策略', 'data-security', '/data-security/access', 'system', 2, 30, 1, 1, 'data-security:read', '数据访问策略', '${appName}'),
('data-security-masking', '数据脱敏', 'data-security', '/data-security/masking', 'quality', 2, 40, 1, 1, 'data-security:read', '数据脱敏', '${appName}'),
('data-security-audit', '访问审计', 'data-security', '/data-security/audit', 'report', 2, 50, 1, 1, 'data-security:read', '访问审计', '${appName}'),
('data-security-compliance', '合规管理', 'data-security', '/data-security/compliance', 'insight', 2, 60, 1, 1, 'data-security:read', '合规管理', '${appName}'),
('data-approval-todo', '我的待办', NULL, '/approval/todo', 'report', 2, 10, 1, 1, 'data-approval:read', '我的待办', '${appName}'),
('data-approval-flows', '审批流程配置', 'system', '/approval/flows', 'workflow', 2, 45, 1, 1, 'data-approval:manage', '审批流程配置', '${appName}'),
('data-metadata-overview', '元数据概览', 'data-metadata', '/data-metadata/overview', 'insight', 2, 10, 1, 1, 'data-metadata:read', '元数据概览', '${appName}'),
('data-metadata-collect', '采集与对账', 'data-metadata', '/data-metadata/collect', 'sync', 2, 40, 1, 1, 'data-metadata:read', '采集与对账', '${appName}'),
('data-asset-overview', '资产概览', 'data-asset', '/data-asset/overview', 'insight', 2, 20, 1, 1, 'data-asset:read', '资产概览', '${appName}'),
('data-asset-catalog', '资产目录', 'data-asset', '/data-asset/catalog', 'knowledge', 2, 10, 1, 1, 'data-asset:read', '资产目录', '${appName}'),
('data-asset-inventory', '盘点上架', 'data-governance', '/data-asset/inventory', 'quality', 2, 10, 1, 1, 'data-asset:read', '盘点上架', '${appName}'),
('data-asset-taxonomy', '目录与标签', 'data-governance', '/data-asset/taxonomy', 'database', 2, 20, 1, 1, 'data-asset:read', '目录与标签', '${appName}'),
('resource-management', '文件资源', 'integration', '/resource-management', 'database', 2, 40, 1, 1, 'resource:view', '文件资源', '${appName}'),
('data-quality-overview', '质量总览', 'data-quality', '/data-quality/overview', 'monitor', 2, 5, 1, 1, 'quality:execution:read', '质量总览', '${appName}'),
('data-quality-table-config', '数据表监控', 'data-quality', '/data-quality/table-config', 'quality', 2, 10, 1, 1, 'quality:monitor:read', '数据表监控', '${appName}'),
('data-quality-execution', '质量检查记录', 'data-quality', '/data-quality/execution', 'report', 2, 20, 1, 1, 'quality:execution:read', '质量检查记录', '${appName}'),
('data-quality-rule-template', '规则模板', 'data-quality', '/data-quality/rule-template', 'quality', 2, 30, 1, 1, 'quality:template:read', '规则模板', '${appName}'),
('batch-link-up', '离线同步', 'integration', '/sync/batch-link-up', 'sync', 2, 20, 1, 1, 'task:batch:read', '离线同步', '${appName}'),
('realtime-sync', '实时同步', 'integration', '/sync/realtime', 'realtime', 2, 30, 1, 1, 'task:realtime:read', '实时同步', '${appName}'),
('resources', '资源管理', NULL, NULL, 'database', 1, 40, 0, 0, NULL, '文件资源改为接入域直接入口', '${appName}'),
('data-approval', '审批中心', NULL, NULL, 'report', 1, 70, 0, 0, NULL, '待办与流程配置分别进入工作入口和平台设置', '${appName}')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name),
parent_code = VALUES(parent_code),
route_path = VALUES(route_path),
icon_key = VALUES(icon_key),
menu_type = VALUES(menu_type),
sort_order = VALUES(sort_order),
visible = VALUES(visible),
active = VALUES(active),
required_permission_code = VALUES(required_permission_code),
description = VALUES(description),
is_delete = 0;

-- Update existing framework-owned metadata; never copy system-* rows.
UPDATE yak_security_menu SET menu_name = '平台设置'
WHERE menu_code = 'system' AND app_name = '${appName}' AND is_delete = 0;
UPDATE yak_security_menu SET sort_order = 5
WHERE menu_code = 'system-security-projects' AND app_name = '${appName}' AND is_delete = 0;

-- Existing effective Asset READ users already have discovery API access.
-- Grant its visible shortcut without adding any role-permission rows.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT eligible.role_id, catalog.id, eligible.app_name
FROM (
  SELECT role_permission.role_id, role_permission.app_name
  FROM yak_security_role_permission role_permission
  JOIN yak_security_permission permission_row
    ON permission_row.id = role_permission.permission_id
   AND permission_row.app_name = role_permission.app_name
   AND permission_row.permission_code IN ('data-asset:read', 'security:root')
   AND permission_row.active = 1 AND permission_row.is_delete = 0
  WHERE role_permission.app_name = '${appName}' AND role_permission.is_delete = 0
  UNION
  SELECT role_menu.role_id, role_menu.app_name
  FROM yak_security_role_menu role_menu
  JOIN yak_security_menu existing_menu
    ON existing_menu.id = role_menu.menu_id AND existing_menu.app_name = role_menu.app_name
   AND existing_menu.required_permission_code = 'data-asset:read'
   AND existing_menu.active = 1 AND existing_menu.is_delete = 0
  WHERE role_menu.app_name = '${appName}' AND role_menu.is_delete = 0
) eligible
JOIN yak_security_menu catalog
  ON catalog.app_name = eligible.app_name AND catalog.menu_code = 'consumption-catalog'
 AND catalog.active = 1 AND catalog.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- A granted leaf implies its parent. Two passes cover page -> subgroup ->
-- capability, without granting sibling leaves or changing old relationships.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id, parent_menu.id, role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id AND child_menu.app_name = role_menu.app_name
 AND child_menu.active = 1 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.active = 1 AND parent_menu.is_delete = 0
WHERE role_menu.app_name = '${appName}' AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id, parent_menu.id, role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id AND child_menu.app_name = role_menu.app_name
 AND child_menu.active = 1 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.active = 1 AND parent_menu.is_delete = 0
WHERE role_menu.app_name = '${appName}' AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
