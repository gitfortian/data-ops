-- Register the warehouse-modeling menu group and its workspace page, plus the
-- modeling permission entries that back the page-level RBAC requirement.
--
-- Warehouse modeling (docs/model/modeling-requirements.md):
--   modeling            数据建模   warehouse modeling entry (menu group)
--   modeling-workspace  模型工作台 warehouse modeling workspace page
--
-- The page requires 'modeling:read'. Existing roles receive the page through
-- role management; root administrators are granted below like V2017 does.

-- 1. Modeling permission root.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('modeling', '数据建模', 0, 0, 1, 'Yak Ops 数仓建模权限',
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

-- 2. Modeling page permission bound to its owning menu.
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
    SELECT 'modeling' parent_code,
           'modeling:read' permission_code,
           '查看数据建模' permission_name,
           '查看数据建模页面及接口' description,
           'modeling-workspace' menu_code
    UNION ALL SELECT 'modeling', 'modeling:create', '新建物理模型',
                     '创建数仓物理模型', 'modeling-workspace'
    UNION ALL SELECT 'modeling', 'modeling:update', '编辑物理模型',
                     '编辑数仓物理模型结构与属性', 'modeling-workspace'
    UNION ALL SELECT 'modeling', 'modeling:delete', '删除物理模型',
                     '删除数仓物理模型', 'modeling-workspace'
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

-- 3. Menu catalog: modeling group leads the data-production section, before
--    数据集成 (sort_order 10), mirroring the frontend task-section order.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('modeling', '数据建模', NULL, NULL, 'project', 1,
 5, 1, 1, NULL, '数仓建模入口', '${appName}'),
('modeling-workspace', '模型工作台', 'modeling', '/modeling', 'database', 2,
 10, 1, 1, 'modeling:read', '数仓建模工作台：模型目录、结构设计与模型资产视图', '${appName}')
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
 AND menu_row.menu_code IN ('modeling', 'modeling-workspace')
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
 AND child_menu.parent_code = 'modeling'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
