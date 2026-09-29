-- Phase 4 action access: register authenticated Data Service invocation in Yak Security's canonical RBAC catalog.
-- This is a runtime action permission, not a console page-entry permission. Anonymous/API Key/Consumer/IP
-- invocation remains governed by the existing external runtime authentication and access-control chain.

INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'data-service:invoke',
       '调用数据服务',
       parent.id,
       1,
       2,
       '以已认证平台身份调用 Data Service Runtime',
       1,
       0,
       NULL,
       parent.app_name
FROM yak_security_permission parent
WHERE parent.permission_code = 'data-service'
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
                invoke_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission invoke_permission
  ON invoke_permission.permission_code = 'data-service:invoke'
 AND invoke_permission.app_name = root_permission.app_name
 AND invoke_permission.is_delete = 0
 AND invoke_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
