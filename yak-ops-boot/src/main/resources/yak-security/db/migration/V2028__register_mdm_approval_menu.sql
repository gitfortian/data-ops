-- Register the business-mdm "master data approval" page (ticket 60).
--
-- Master data management (docs/mdm/menu.md): the mdm group and overview page
-- were registered by V2024, modeling by V2025, identification by V2026,
-- cleansing by V2027. This migration adds the approval page only.
-- The approval page requires 'mdm:read'.

-- 1. Approval page permission bound to its owning menu.
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
    SELECT 'mdm' parent_code,
           'mdm:read' permission_code,
           '查看主数据' permission_name,
           '查看主数据审批页面及接口' description,
           'mdm-approval' menu_code
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

-- 2. Menu catalog: approval page under the mdm group.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('mdm-approval', '主数据审批', 'mdm', '/mdm/approval', 'audit', 2,
 50, 1, 1, 'mdm:read', '主数据审批：变更申请、审批流、版本管理', '${appName}')
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

-- 3. Root administrators retain the full reconciled business menu catalog.
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
 AND menu_row.menu_code = 'mdm-approval'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 4. Any granted child menu implies its parent group (keeps group rows in sync).
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'mdm'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
