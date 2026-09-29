-- Register the data lifecycle (TTL) module menus and permissions (ticket 80).
--
-- Group data-lifecycle with 3 child pages: policy, monitor, storage.
-- Permissions: data-lifecycle:read/create/update/delete. Template = V2029 (metric).

-- 1. Parent permission group: data-lifecycle.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'data-lifecycle', '数据生命周期', 0, 0, 1,
       '数据生命周期模块:TTL 策略、分区预览、下发监控与存储统计',
       1, 0, 'data-lifecycle', '${appName}'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM yak_security_permission
    WHERE permission_code = 'data-lifecycle' AND app_name = '${appName}' AND is_delete = 0
);

-- 2. Child permissions bound to the data-lifecycle group.
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
    SELECT 'data-lifecycle' parent_code, 'data-lifecycle:read' permission_code,
           '查看数据生命周期' permission_name, '查看 TTL 策略、监控与存储统计' description,
           'data-lifecycle-policy' menu_code
    UNION ALL
    SELECT 'data-lifecycle', 'data-lifecycle:create', '新建生命周期策略', '创建 TTL 策略与分层默认', 'data-lifecycle-policy'
    UNION ALL
    SELECT 'data-lifecycle', 'data-lifecycle:update', '编辑生命周期策略', '编辑策略/绑定模型/下发 TTL', 'data-lifecycle-policy'
    UNION ALL
    SELECT 'data-lifecycle', 'data-lifecycle:delete', '删除生命周期策略', '删除策略与解绑模型', 'data-lifecycle-policy'
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

-- 3. Menu group: data lifecycle.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-lifecycle', '数据生命周期', NULL, NULL, 'monitor', 1,
 90, 1, 1, NULL, '数据生命周期(TTL)模块入口', '${appName}')
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

-- 4. Child menus: policy, monitor, storage.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-lifecycle-policy', '策略管理', 'data-lifecycle', '/data-lifecycle/policy', 'database', 2,
 10, 1, 1, 'data-lifecycle:read', 'TTL 策略与分层默认策略', '${appName}'),
('data-lifecycle-monitor', 'TTL 监控', 'data-lifecycle', '/data-lifecycle/monitor', 'monitor', 2,
 20, 1, 1, 'data-lifecycle:read', '模型 TTL 状态、下发流水与异常', '${appName}'),
('data-lifecycle-storage', '存储统计', 'data-lifecycle', '/data-lifecycle/storage', 'insight', 2,
 30, 1, 1, 'data-lifecycle:read', '各层存储量、趋势与成本估算', '${appName}')
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

-- 5. Root administrators retain the full data-lifecycle menu catalog.
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
 AND menu_row.parent_code = 'data-lifecycle'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. Any granted child menu implies the data-lifecycle parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'data-lifecycle'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
