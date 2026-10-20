-- Forward-only PostgreSQL catalog update; no source or task records are changed.
UPDATE "yak_security_menu" SET "menu_name" = '数据源管理', "description" = '连接配置与管理'
WHERE "app_name" = '${appName}' AND "menu_code" = 'data-source' AND "is_delete" = 0;
UPDATE "yak_security_menu" SET "menu_name" = '任务管理', "description" = '离线同步任务管理'
WHERE "app_name" = '${appName}' AND "menu_code" = 'batch-link-up' AND "is_delete" = 0;

INSERT INTO "yak_security_menu"
("menu_code", "menu_name", "parent_code", "route_path", "icon_key", "menu_type", "sort_order",
 "visible", "active", "required_permission_code", "description", "is_delete", "app_name")
SELECT 'data-source-explore', '数据探查', 'integration', '/data-source/explore', 'monitor', 2, 11,
  1, 1, 'resource:data-source:read', '实时库表结构探查', 0, '${appName}'
WHERE NOT EXISTS (SELECT 1 FROM "yak_security_menu"
 WHERE "menu_code" = 'data-source-explore' AND "app_name" = '${appName}');
UPDATE "yak_security_menu" SET "menu_name" = '数据探查', "parent_code" = 'integration',
 "route_path" = '/data-source/explore', "icon_key" = 'monitor', "menu_type" = 2,
 "sort_order" = 11, "visible" = 1, "active" = 1,
 "required_permission_code" = 'resource:data-source:read', "is_delete" = 0
WHERE "menu_code" = 'data-source-explore' AND "app_name" = '${appName}';

INSERT INTO "yak_security_role_menu" ("role_id", "menu_id", "app_name")
SELECT old."role_id", explorer."id", old."app_name"
FROM "yak_security_role_menu" old
JOIN "yak_security_menu" source_menu
  ON source_menu."id" = old."menu_id" AND source_menu."app_name" = old."app_name"
 AND source_menu."menu_code" = 'data-source' AND source_menu."is_delete" = 0
JOIN "yak_security_menu" explorer
  ON explorer."app_name" = old."app_name" AND explorer."menu_code" = 'data-source-explore'
 AND explorer."active" = 1 AND explorer."is_delete" = 0
WHERE old."app_name" = '${appName}' AND old."is_delete" = 0
 AND NOT EXISTS (SELECT 1 FROM "yak_security_role_menu" previous
 WHERE previous."role_id" = old."role_id" AND previous."menu_id" = explorer."id"
 AND previous."app_name" = old."app_name" AND previous."is_delete" = 0);
