-- Register the SQL execution audit observation menu (datasource ticket 04).
--
-- Backend three endpoints (SqlExecutionAuditController) have been live and
-- auditing since the SQL execution engine shipped; this page is their only UI.
-- Permission follows the unified resource:* convention already used by
-- resource:data-source:read (parent group 'resource' from V1000).
-- Menu lives in the integration domain next to 数据源管理 (route order 15).
-- Idempotent: every statement is an upsert.

-- 1. Child permission bound to the existing resource group.
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
    SELECT 'resource' parent_code, 'resource:sql-execution:read' permission_code,
           '查看 SQL 执行审计' permission_name,
           '查看数据源上 SQL 执行/语句级留痕、耗时与失败原因' description,
           'sql-execution-audit' menu_code
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

-- 2. Menu page under the integration group.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('sql-execution-audit', 'SQL 执行审计', 'integration', '/sql-execution-audit', 'monitor', 2,
 15, 1, 1, 'resource:sql-execution:read', '数据源 SQL 执行观测：执行/语句两级台账', '${appName}')
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

-- 3. Root administrators keep the full catalog.
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
 AND menu_row.menu_code = 'sql-execution-audit'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
