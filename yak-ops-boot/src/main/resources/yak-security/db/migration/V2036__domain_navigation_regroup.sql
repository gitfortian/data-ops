-- Domain navigation regroup per docs/MENU_REDESIGN.md (M0).
--
-- 17 flat groups collapse into 7 business domains (数据接入 / 数据开发与编排 /
-- 标准与建模 / 数据治理 / 数据资产 / 消费与服务 / 审批中心) plus 系统管理.
-- Menu codes are the stable RBAC contract: no page menu code changes here.
-- This migration only (a) registers the new 数据治理 domain group,
-- (b) re-parents existing GROUP rows under their domain (menu_type stays 1,
-- route_path stays NULL), (c) moves two PAGE rows to their new owning group
-- (data-source -> integration, data-analysis-lineage -> data-asset), and
-- (d) retires 主数据审批, whose page merged into the approval center
-- (/mdm/approval now redirects to /approval/todo).
-- Idempotent: every statement is an upsert or a guarded insert (V2032/V2035 template).

-- 1. New top-level domain group: 数据治理.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-governance', '数据治理', NULL, NULL, 'quality', 1,
 40, 1, 1, NULL, '数据治理域:主数据/质量/安全/生命周期', '${appName}')
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

-- 2. Domain group renames (they stay root containers).
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('integration', '数据接入', NULL, NULL, 'connector', 1,
 10, 1, 1, NULL, '数据源/同步任务/资源', '${appName}'),
('development', '数据开发与编排', NULL, NULL, 'workflow', 1,
 20, 1, 1, NULL, '数据开发工作台与工作流编排入口', '${appName}'),
('modeling', '标准与建模', NULL, NULL, 'project', 1,
 30, 1, 1, NULL, '数仓标准体系与模型设计工作台', '${appName}'),
('data-analysis', '消费与服务', NULL, NULL, 'insight', 1,
 60, 1, 1, NULL, 'BI 消费、指标与 API 服务入口', '${appName}')
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

-- 3. Existing groups demote to sub-groups of their domain.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('resources', '资源管理', 'integration', NULL, 'database', 1,
 40, 1, 1, NULL, '文件资源入口', '${appName}'),
('workflow', '工作流编排', 'development', NULL, 'workflow', 1,
 60, 1, 1, NULL, '工作流定义与实例入口', '${appName}'),
('semantic', '数仓标准体系', 'modeling', NULL, 'knowledge', 1,
 50, 1, 1, NULL, '数据标准/业务域/业务过程/标准字段/数仓分层', '${appName}'),
('mdm', '主数据', 'data-governance', NULL, 'database', 1,
 10, 1, 1, NULL, '主数据管理入口', '${appName}'),
('data-quality', '数据质量', 'data-governance', NULL, 'quality', 1,
 20, 1, 1, NULL, '数据质量页面入口', '${appName}'),
('data-security', '数据安全', 'data-governance', NULL, 'monitor', 1,
 30, 1, 1, NULL, '数据安全页面入口', '${appName}'),
('data-lifecycle', '生命周期', 'data-governance', NULL, 'realtime', 1,
 40, 1, 1, NULL, '数据生命周期入口', '${appName}'),
('data-metadata', '元数据管理', 'data-asset', NULL, 'sync', 1,
 60, 1, 1, NULL, '元数据采集/目录/搜索(归入数据资产域)', '${appName}'),
('metric', '指标中心', 'data-analysis', NULL, 'report', 1,
 40, 1, 1, NULL, '指标中心菜单组', '${appName}'),
('data-service', 'API 服务', 'data-analysis', NULL, 'api', 1,
 60, 1, 1, NULL, '数据服务管理入口', '${appName}')
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

-- 4. Page rows moving to a new owning group (path/permission unchanged).
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-source', '数据源管理', 'integration', '/data-source', 'database', 2,
 10, 1, 1, 'resource:data-source:read', '数据源管理', '${appName}'),
('data-analysis-lineage', '数据血缘', 'data-asset', '/data-analysis/lineage', 'workflow', 2,
 50, 1, 1, NULL, '全局数据血缘(归入数据资产域)', '${appName}')
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

-- 5. Retire 主数据审批: page merged into the approval center; the frontend
-- keeps /mdm/approval addressable via a redirect for one release.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('mdm-approval', '主数据审批', 'mdm', '/mdm/approval', 'audit', 2,
 50, 0, 0, 'mdm:read', '已下线:并入审批中心待办', '${appName}')
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

-- 6. Root administrators gain the new domain group row.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT root_permission.role_id,
                menu_row.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.app_name = root_permission.app_name
 AND root_row.permission_code = 'security:root'
 AND root_row.is_delete = 0
 AND root_row.active = 1
JOIN yak_security_menu menu_row
  ON menu_row.app_name = root_permission.app_name
 AND menu_row.menu_code = 'data-governance'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 7. Every granted child menu implies its (new) parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code IS NOT NULL
 AND child_menu.parent_code <> ''
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 8. And its grandparent domain (grants reference the parent chain, depth 2).
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                grand_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code IS NOT NULL
 AND child_menu.parent_code <> ''
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.parent_code IS NOT NULL
 AND parent_menu.parent_code <> ''
JOIN yak_security_menu grand_menu
  ON grand_menu.menu_code = parent_menu.parent_code
 AND grand_menu.app_name = role_menu.app_name
 AND grand_menu.is_delete = 0
 AND grand_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
