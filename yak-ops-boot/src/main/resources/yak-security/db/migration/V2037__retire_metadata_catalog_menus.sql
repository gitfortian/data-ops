-- Retire 元数据目录浏览/统一搜索 menus per docs/PLATFORM_CORE_FLOW.md (M2-2).
--
-- 两页能力已并入资产目录「元数据实体」视图(?view=entity)，前端不再声明
-- data-metadata-catalog / data-metadata-search 两个 menuCode；旧地址各保留
-- 一跳 redirect(同 mdm-approval 退役先例)。Idempotent: upsert only.

INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-metadata-catalog', '目录浏览', 'data-metadata', '/data-metadata/catalog', 'database', 2,
 20, 0, 0, 'data-metadata:read', '已下线:并入资产目录元数据实体视图', '${appName}'),
('data-metadata-search', '统一搜索', 'data-metadata', '/data-metadata/search', 'knowledge', 2,
 30, 0, 0, 'data-metadata:read', '已下线:并入资产目录元数据实体视图', '${appName}')
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
