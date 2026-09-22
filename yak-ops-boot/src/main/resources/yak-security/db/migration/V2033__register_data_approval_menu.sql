-- Register the general approval (Approval Center) menus and permissions (ticket 101).
--
-- Group data-approval with 2 child pages: todo center, flow definitions.
-- Permissions: data-approval:read/create/approve/manage. Template = V2032 (data asset).

-- 1. Parent permission group: data-approval.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'data-approval', '审批中心', 0, 0, 1,
       '通用审批流:流程配置、发起、待办、审批与记录',
       1, 0, 'data-approval', '${appName}'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM yak_security_permission
    WHERE permission_code = 'data-approval' AND app_name = '${appName}' AND is_delete = 0
);

-- 2. Child permissions bound to the data-approval group.
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
    SELECT 'data-approval' parent_code, 'data-approval:read' permission_code,
           '查看审批' permission_name, '查看待办、我发起的与审批单详情' description,
           'data-approval-todo' menu_code
    UNION ALL
    SELECT 'data-approval', 'data-approval:create', '配置审批流程', '新建审批流程定义', 'data-approval-flows'
    UNION ALL
    SELECT 'data-approval', 'data-approval:approve', '处理审批', '通过/拒绝当前级审批单', 'data-approval-todo'
    UNION ALL
    SELECT 'data-approval', 'data-approval:manage', '流程启停与删除', '启停/删除流程定义、查看全部审批单', 'data-approval-flows'
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

-- 3. Menu group: approval center.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-approval', '审批中心', NULL, NULL, 'audit', 1,
 110, 1, 1, NULL, '通用审批流入口(待办与流程配置)', '${appName}')
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

-- 4. Child menus: todo, flows.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-approval-todo', '待办中心', 'data-approval', '/approval/todo', 'check-circle', 2,
 10, 1, 1, 'data-approval:read', '我的待办、我发起的与我审批过的', '${appName}'),
('data-approval-flows', '流程配置', 'data-approval', '/approval/flows', 'control', 2,
 20, 1, 1, 'data-approval:manage', '审批流程定义与启停(管理员)', '${appName}')
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

-- 5. Root administrators retain the full data-approval menu catalog.
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
 AND menu_row.parent_code = 'data-approval'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. Any granted child menu implies the data-approval parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'data-approval'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
