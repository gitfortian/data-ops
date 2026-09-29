-- Reconcile datasource permission codes into the resource:data-source:* family.
--
-- V1000/V2006 registered READ as resource:data-source:read while the write actions kept the
-- legacy short codes, and datasource:view duplicated READ — three naming styles for one module.
-- The four write codes are renamed **in place** here: permission_id never changes, so
-- yak_security_role_permission grants survive without re-granting anything. The duplicated view
-- code and the now-empty compatibility group are then retired the way V1000 retires rows.
--
-- declared is never touched: these rows belong to the SQL catalog, not the declarative registry.
-- Idempotent: the rename only matches legacy codes, and retirement only touches live legacy rows.

-- 1. Rename the write codes and re-parent them under the resource group.
--    Kept free of read-after-write ordering hazards: only parent_id and permission_code are
--    assigned here, and parent_id never reads the column being rewritten.
UPDATE yak_security_permission leaf
JOIN yak_security_permission parent
  ON parent.permission_code = 'resource'
 AND parent.app_name = leaf.app_name
 AND parent.level = 1
 AND parent.is_delete = 0
SET leaf.parent_id = parent.id,
    leaf.permission_code = CASE leaf.permission_code
      WHEN 'datasource:create' THEN 'resource:data-source:create'
      WHEN 'datasource:update' THEN 'resource:data-source:update'
      WHEN 'datasource:delete' THEN 'resource:data-source:delete'
      WHEN 'datasource:test' THEN 'resource:data-source:test'
      ELSE leaf.permission_code
    END
WHERE leaf.app_name = '${appName}'
  AND leaf.is_delete = 0
  AND leaf.permission_code IN (
      'datasource:create', 'datasource:update', 'datasource:delete', 'datasource:test');

-- 2. The renamed rows no longer describe a legacy code, so refresh their wording.
UPDATE yak_security_permission
SET description = CASE permission_code
      WHEN 'resource:data-source:create' THEN '创建数据源'
      WHEN 'resource:data-source:update' THEN '编辑数据源配置'
      WHEN 'resource:data-source:delete' THEN '删除数据源'
      WHEN 'resource:data-source:test' THEN '测试数据源连接'
      ELSE description
    END
WHERE app_name = '${appName}'
  AND is_delete = 0
  AND permission_code IN (
      'resource:data-source:create', 'resource:data-source:update',
      'resource:data-source:delete', 'resource:data-source:test');

-- 3. Holders of the retiring datasource:view already see the page; carry that grant forward to
--    the READ code that actually protects it, so nobody loses access to 数据源管理.
INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT view_grant.role_id,
                read_row.id,
                view_grant.app_name
FROM yak_security_role_permission view_grant
JOIN yak_security_permission view_row
  ON view_row.id = view_grant.permission_id
 AND view_row.app_name = view_grant.app_name
 AND view_row.permission_code = 'datasource:view'
 AND view_row.is_delete = 0
 AND view_row.active = 1
JOIN yak_security_permission read_row
  ON read_row.permission_code = 'resource:data-source:read'
 AND read_row.app_name = view_grant.app_name
 AND read_row.is_delete = 0
 AND read_row.active = 1
WHERE view_grant.app_name = '${appName}'
  AND view_grant.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 4. The copied READ grant must keep implying the page menu, same rule as V2006 step 4.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                menu_row.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.menu_code = 'data-source'
 AND permission_row.is_delete = 0
 AND permission_row.active = 1
JOIN yak_security_menu menu_row
  ON menu_row.menu_code = 'data-source'
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 5. Retire the duplicated view code and the empty compatibility group: drop their grants first,
--    then the rows themselves. menu bindings of the renamed leaves stay 'data-source'.
UPDATE yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
SET role_permission.is_delete = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
  AND permission_row.permission_code IN ('datasource:view', 'datasource');

UPDATE yak_security_permission
SET active = 0,
    is_delete = 1
WHERE app_name = '${appName}'
  AND permission_code IN ('datasource:view', 'datasource');
