-- Register the business-security (data-security) menu group and its pages, plus the
-- data-security permission entries backing page-level RBAC (docs/data-security/menu.md).
--
--   data-security             数据安全        group (sort_order 8, after mdm=7)
--   data-security-overview    数据安全总览     overview
--   data-security-classification 分级分类
--   data-security-access      权限管理
--   data-security-masking     数据脱敏
--   data-security-audit       访问审计
--   data-security-compliance  合规管理
-- All pages require 'data-security:read'.

-- 1. Data-security permission root.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('data-security', '数据安全', 0, 0, 1, 'Yak Ops 数据安全权限',
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

-- 2. Page/action permissions bound to their owning menu.
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
    SELECT 'data-security' parent_code, 'data-security:read' permission_code,
           '查看数据安全' permission_name, '查看数据安全各页面及接口' description,
           'data-security-overview' menu_code
    UNION ALL SELECT 'data-security', 'data-security:create', '新建数据安全配置',
                     '创建等级/分类/标签/策略/规则', 'data-security-classification'
    UNION ALL SELECT 'data-security', 'data-security:update', '编辑数据安全配置',
                     '编辑/启停数据安全配置', 'data-security-classification'
    UNION ALL SELECT 'data-security', 'data-security:delete', '删除数据安全配置',
                     '删除数据安全配置', 'data-security-classification'
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

-- 3. Menu catalog.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-security', '数据安全', NULL, NULL, 'lock', 1,
 8, 1, 1, NULL, '数据安全：分级分类、权限、脱敏、审计、合规', '${appName}'),
('data-security-overview', '数据安全总览', 'data-security', '/data-security/overview', 'dashboard', 2,
 10, 1, 1, 'data-security:read', '数据安全总览：定级覆盖、策略、脱敏、合规缺口', '${appName}'),
('data-security-classification', '分级分类', 'data-security', '/data-security/classification', 'database', 2,
 20, 1, 1, 'data-security:read', '安全等级/数据分类字典、资产分级标签与敏感发现', '${appName}'),
('data-security-access', '权限管理', 'data-security', '/data-security/access', 'team', 2,
 30, 1, 1, 'data-security:read', '数据级访问策略与访问裁决', '${appName}'),
('data-security-masking', '数据脱敏', 'data-security', '/data-security/masking', 'safety', 2,
 40, 1, 1, 'data-security:read', '脱敏算法字典与脱敏策略', '${appName}'),
('data-security-audit', '访问审计', 'data-security', '/data-security/audit', 'file-search', 2,
 50, 1, 1, 'data-security:read', '数据访问留痕与安全视角统计', '${appName}'),
('data-security-compliance', '合规管理', 'data-security', '/data-security/compliance', 'audit', 2,
 60, 1, 1, 'data-security:read', '合规规则与体检报告', '${appName}')
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
 AND menu_row.menu_code IN
     ('data-security','data-security-overview','data-security-classification',
      'data-security-access','data-security-masking','data-security-audit','data-security-compliance')
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 5. Any granted child menu implies its parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'data-security'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
