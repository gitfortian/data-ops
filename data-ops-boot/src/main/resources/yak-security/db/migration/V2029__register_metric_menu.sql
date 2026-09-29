-- Register the metric module menus and permissions (ticket 45).
--
-- Metric management (docs/semantic/metrics/issues/45): metric group with 4 child pages:
-- metric_manage, metric_lineage, metric_impact, metric_service.
-- Permissions: metric:read, metric:create, metric:update, metric:delete.

-- 1. Parent permission group: metric.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'metric', '指标管理', 0, 0, 1,
       '指标管理模块:指标定义、血缘、影响分析、元数据服务',
       1, 0, 'metric', '${appName}'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM yak_security_permission
    WHERE permission_code = 'metric' AND app_name = '${appName}' AND is_delete = 0
);

-- 2. Child permissions bound to the metric group.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT item.permission_code,
       item.permission_name,
       parent.id,
       1,
       2,
       item.description,
       1,
       0,
       item.menu_code,
       parent.app_name
FROM yak_security_permission parent
JOIN (
    SELECT 'metric' parent_code, 'metric:read' permission_code,
           '查看指标' permission_name, '查看指标列表、详情、血缘等' description,
           'metric-manage' menu_code
    UNION ALL
    SELECT 'metric', 'metric:create', '创建指标', '创建指标定义', 'metric-manage'
    UNION ALL
    SELECT 'metric', 'metric:update', '编辑指标', '编辑指标、打标签、变更状态', 'metric-manage'
    UNION ALL
    SELECT 'metric', 'metric:delete', '删除指标', '删除指标定义', 'metric-manage'
) item ON item.parent_code = parent.permission_code
WHERE parent.app_name = '${appName}'
  AND parent.is_delete = 0
ON DUPLICATE KEY UPDATE
permission_name = VALUES(permission_name),
parent_id = VALUES(parent_id),
leaf = VALUES(leaf),
level = VALUES(level),
description = VALUES(description),
active = VALUES(active),
declared = VALUES(declared),
menu_code = VALUES(menu_code),
is_delete = 0;

-- 3. Menu group: metric center.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('metric', '指标中心', '', '/metric', 'metric', 0,
 80, 1, 1, 'metric:read', '指标管理模块入口', '${appName}')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name),
route_path = VALUES(route_path),
icon_key = VALUES(icon_key),
sort_order = VALUES(sort_order),
visible = VALUES(visible),
active = VALUES(active),
required_permission_code = VALUES(required_permission_code),
description = VALUES(description),
is_delete = 0;

-- 4. Child menus: manage, lineage, impact, service.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('metric-manage', '指标管理', 'metric', '/metric/manage', 'list', 2,
 10, 1, 1, 'metric:read', '指标定义、CRUD、列表、详情', '${appName}'),
('metric-lineage', '指标血缘', 'metric', '/metric/lineage', 'link', 2,
 20, 1, 1, 'metric:read', '指标血缘可视化', '${appName}'),
('metric-impact', '影响分析', 'metric', '/metric/impact', 'alert', 2,
 30, 1, 1, 'metric:read', '指标影响分析', '${appName}'),
('metric-service', '指标服务', 'metric', '/metric/service', 'api', 2,
 40, 1, 1, 'metric:read', '指标元数据API', '${appName}')
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

-- 5. Root administrators retain the full metric menu catalog.
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
 AND menu_row.parent_code = 'metric'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. Any granted child menu implies the metric parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'metric'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
