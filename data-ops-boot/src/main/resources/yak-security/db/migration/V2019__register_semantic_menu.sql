-- Register the business-semantic menu group and its data-standards page, plus
-- the semantic permission entries that back the page-level RBAC requirement.
--
-- Business semantic (docs/semantic/module-design.md, decision E):
--   semantic            语义中心   business-semantic entry (menu group)
--   semantic-standard   数据标准   six-kind data standards catalog page
--
-- Later semantic pages (业务域/业务过程/数仓分层) register their own menus in
-- their owning tickets (33/34/37). The page requires 'semantic:read'.

-- 1. Semantic permission root.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('semantic', '语义中心', 0, 0, 1, 'Yak Ops 业务语义权限',
 1, 0, NULL, '${appName}')
ON DUPLICATE KEY UPDATE
permission_name = VALUES(permission_name),
parent_id = VALUES(parent_id),
leaf = VALUES(leaf),
level = VALUES(level),
description = VALUES(description),
active = VALUES(active),
declared = VALUES(declared),
is_delete = 0;

-- 2. Semantic page permission bound to its owning menu.
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
    SELECT 'semantic' parent_code,
           'semantic:read' permission_code,
           '查看数据标准' permission_name,
           '查看数据标准页面及接口' description,
           'semantic-standard' menu_code
    UNION ALL SELECT 'semantic', 'semantic:create', '新建数据标准',
                     '创建数据标准', 'semantic-standard'
    UNION ALL SELECT 'semantic', 'semantic:update', '编辑数据标准',
                     '编辑/启停数据标准', 'semantic-standard'
    UNION ALL SELECT 'semantic', 'semantic:delete', '删除数据标准',
                     '删除数据标准', 'semantic-standard'
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

-- 3. Menu catalog: semantic group follows 数据建模 (sort_order 5).
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('semantic', '语义中心', NULL, NULL, 'project', 1,
 6, 1, 1, NULL, '业务语义入口：数据标准、业务语义与数仓分层', '${appName}'),
('semantic-standard', '数据标准', 'semantic', '/semantic/standards', 'database', 2,
 10, 1, 1, 'semantic:read', '数据标准目录：命名/类型/码值/单位/口径/安全六类标准', '${appName}')
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

-- 4. Root administrators retain the full reconciled business menu catalog.
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
 AND menu_row.menu_code IN ('semantic', 'semantic-standard')
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 5. Any granted child menu implies its parent group (keeps group rows in sync).
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'semantic'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
