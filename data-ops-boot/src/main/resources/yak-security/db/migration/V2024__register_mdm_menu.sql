-- Register the business-mdm menu group and its master-data-overview page, plus
-- the mdm permission entries that back the page-level RBAC requirement.
--
-- Master data management (docs/mdm/menu.md, decision D-M8):
--   mdm          主数据管理  business-mdm entry (menu group)
--   mdm-overview 主数据总览  master data overview page (entry point)
--
-- Menu convergence: only the 5 MDM-specific menus are registered (总览/建模/识别/
-- 清洗/审批); collection/quality/lineage/service/standard/analysis reuse the
-- platform menus via jumps. Later MDM pages register their own menus in their
-- owning tickets (建模: 51, 识别: 53, 清洗: 56, 审批: 60). The overview page
-- requires 'mdm:read'.

-- 1. MDM permission root.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('mdm', '主数据管理', 0, 0, 1, 'Yak Ops 主数据管理权限',
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

-- 2. MDM page permission bound to its owning menu.
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
           '查看主数据总览页面及接口' description,
           'mdm-overview' menu_code
    UNION ALL SELECT 'mdm', 'mdm:create', '新建主数据',
                     '创建主数据实体/记录', 'mdm-overview'
    UNION ALL SELECT 'mdm', 'mdm:update', '编辑主数据',
                     '编辑/启停主数据配置', 'mdm-overview'
    UNION ALL SELECT 'mdm', 'mdm:delete', '删除主数据',
                     '删除主数据', 'mdm-overview'
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

-- 3. Menu catalog: mdm group follows 语义中心 (sort_order 7).
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('mdm', '主数据管理', NULL, NULL, 'project', 1,
 7, 1, 1, NULL, '主数据管理入口：总览、建模、识别、清洗、审批', '${appName}'),
('mdm-overview', '主数据总览', 'mdm', '/mdm/overview', 'database', 2,
 10, 1, 1, 'mdm:read', '主数据总览：实体分布、采集/质量/分发状态、变更与血缘概览', '${appName}')
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
 AND menu_row.menu_code IN ('mdm', 'mdm-overview')
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
 AND child_menu.parent_code = 'mdm'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
