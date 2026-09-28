-- Publication changes the stable business contract and is separate from draft editing.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'metric:publish',
       '发布指标',
       parent.id,
       1,
       2,
       '发布或撤回精确 MetricVersion',
       1,
       0,
       NULL,
       parent.app_name
FROM yak_security_permission parent
WHERE parent.permission_code = 'metric'
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

-- Root administrators receive the new action permission by the existing cutover rule.
INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT root_permission.role_id,
                publish_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission publish_permission
  ON publish_permission.permission_code = 'metric:publish'
 AND publish_permission.app_name = root_permission.app_name
 AND publish_permission.is_delete = 0
 AND publish_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
