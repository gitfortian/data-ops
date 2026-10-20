-- Register the explorer alongside the existing source menu in deployed installations.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type, sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-source', '数据源管理', 'integration', '/data-source', 'database', 2, 10, 1, 1, 'resource:data-source:read', '连接配置与管理', '${appName}'),
('data-source-explore', '数据探查', 'integration', '/data-source/explore', 'monitor', 2, 11, 1, 1, 'resource:data-source:read', '实时库表结构探查', '${appName}'),
('batch-link-up', '任务管理', 'integration', '/sync/batch-link-up', 'sync', 2, 20, 1, 1, 'task:batch:read', '离线同步任务管理', '${appName}')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name), parent_code = VALUES(parent_code),
route_path = VALUES(route_path), icon_key = VALUES(icon_key),
menu_type = VALUES(menu_type), sort_order = VALUES(sort_order),
visible = VALUES(visible), active = VALUES(active),
required_permission_code = VALUES(required_permission_code), description = VALUES(description), is_delete = 0;

INSERT INTO yak_security_role_menu (role_id, menu_id, app_name)
SELECT old.role_id, explorer.id, old.app_name
FROM yak_security_role_menu old
JOIN yak_security_menu source_menu ON source_menu.id = old.menu_id
 AND source_menu.app_name = old.app_name AND source_menu.menu_code = 'data-source'
 AND source_menu.is_delete = 0
JOIN yak_security_menu explorer ON explorer.app_name = old.app_name
 AND explorer.menu_code = 'data-source-explore' AND explorer.active = 1 AND explorer.is_delete = 0
WHERE old.app_name = '${appName}' AND old.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
