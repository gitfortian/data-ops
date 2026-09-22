-- Register the data asset (Asset Center) module menus and permissions (ticket 90).
--
-- Group data-asset with 4 child pages: overview, catalog, inventory, taxonomy.
-- Permissions: data-asset:read/create/update/delete. Template = V2031 (lifecycle).

-- 1. Parent permission group: data-asset.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'data-asset', '数据资产', 0, 0, 1,
       '数据资产中心:台账、盘点上架、目录标签、健康度与治理驾驶舱',
       1, 0, 'data-asset', '${appName}'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM yak_security_permission
    WHERE permission_code = 'data-asset' AND app_name = '${appName}' AND is_delete = 0
);

-- 2. Child permissions bound to the data-asset group.
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
    SELECT 'data-asset' parent_code, 'data-asset:read' permission_code,
           '查看数据资产' permission_name, '查看资产台账、目录、详情与概览' description,
           'data-asset-catalog' menu_code
    UNION ALL
    SELECT 'data-asset', 'data-asset:create', '登记资产', '手工登记资产/新增目录标签规则', 'data-asset-taxonomy'
    UNION ALL
    SELECT 'data-asset', 'data-asset:update', '资产治理操作', '上下架/改负责人/打标/移目录/规则启停/变更确认/触发对账', 'data-asset-inventory'
    UNION ALL
    SELECT 'data-asset', 'data-asset:delete', '删除资产台账对象', '删除台账/目录/标签/规则', 'data-asset-taxonomy'
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

-- 3. Menu group: data asset.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-asset', '数据资产', NULL, NULL, 'database', 1,
 100, 1, 1, NULL, '数据资产中心入口(治理门面)', '${appName}')
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

-- 4. Child menus: overview, catalog, inventory, taxonomy.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-asset-overview', '资产概览', 'data-asset', '/data-asset/overview', 'dashboard', 2,
 10, 1, 1, 'data-asset:read', '治理驾驶舱:KPI、分布、待办与动态', '${appName}'),
('data-asset-catalog', '资产目录', 'data-asset', '/data-asset/catalog', 'search', 2,
 20, 1, 1, 'data-asset:read', '统一搜索发现与目录浏览', '${appName}'),
('data-asset-inventory', '盘点上架', 'data-asset', '/data-asset/inventory', 'check-circle', 2,
 30, 1, 1, 'data-asset:read', '待上架池、变更确认与源接入', '${appName}'),
('data-asset-taxonomy', '目录与标签', 'data-asset', '/data-asset/taxonomy', 'tags', 2,
 40, 1, 1, 'data-asset:read', '目录树、编目规则与标签字典', '${appName}')
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

-- 5. Root administrators retain the full data-asset menu catalog.
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
 AND menu_row.parent_code = 'data-asset'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. Any granted child menu implies the data-asset parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'data-asset'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
