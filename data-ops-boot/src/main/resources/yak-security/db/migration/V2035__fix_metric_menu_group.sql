-- Fix the metric menu group row registered by V2029 (pre-existing contract violation).
--
-- V2029 inserted the 'metric' GROUP with parent_code='', route_path='/metric',
-- menu_type=0. The navigation-menu contract (and every group since V2030) requires
-- a group row to be root (NULL parent), own no route path, and use menu_type=1.
-- The upsert keeps the same (menu_code, app_name) row, so role_menu grants —
-- which reference the primary key — are preserved.

INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('metric', '指标中心', NULL, NULL, 'report', 1,
 80, 1, 1, NULL, '指标中心菜单组', '${appName}')
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
