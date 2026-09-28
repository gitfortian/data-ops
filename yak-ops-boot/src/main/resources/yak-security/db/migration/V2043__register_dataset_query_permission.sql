-- Phase 4 action access: register Dataset query execution in Yak Security's canonical RBAC catalog.
-- This is an action permission, not a page-entry permission; dataset navigation remains unchanged.

INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('dataset', '数据集', 0, 0, 1, 'Yak Ops 数据集权限',
 1, 0, NULL, '${appName}')
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

INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'dataset:query',
       '查询数据集',
       parent.id,
       1,
       2,
       '执行数据集查询',
       1,
       0,
       NULL,
       parent.app_name
FROM yak_security_permission parent
WHERE parent.permission_code = 'dataset'
  AND parent.app_name = '${appName}'
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

-- Preserve the existing cutover rule: root administrators receive newly introduced capabilities.
INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT root_permission.role_id,
                dataset_query_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission dataset_query_permission
  ON dataset_query_permission.permission_code = 'dataset:query'
 AND dataset_query_permission.app_name = root_permission.app_name
 AND dataset_query_permission.is_delete = 0
 AND dataset_query_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
