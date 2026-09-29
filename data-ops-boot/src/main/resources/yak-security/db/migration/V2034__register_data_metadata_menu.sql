-- Register the metadata center menus and permissions (ticket 111).
--
-- Group data-metadata with 4 child pages: overview, catalog, search, collect.
-- Permissions: data-metadata:read/create/update/delete. Template = V2032 (data asset).
--
-- Why a standalone first-level menu (plan §11.1 第 2 条): collect jobs and run history are
-- an *ops* mental model, while the asset ledger is a *catalog* mental model. Folding them
-- together would make asset own collection scheduling, which contradicts its "管目录不管内容"
-- positioning.
--
-- Number note: this file is V2034, not V2033 — V2033 is already applied by ticket 101
-- (data-approval). Never edit an applied migration (plan §9 T5).

-- 1. Parent permission group: data-metadata.
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'data-metadata', '元数据', 0, 0, 1,
       '元数据中心:采集任务、统一目录、跨类型搜索与元模型扩展',
       1, 0, 'data-metadata', '${appName}'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM yak_security_permission
    WHERE permission_code = 'data-metadata' AND app_name = '${appName}' AND is_delete = 0
);

-- 2. Child permissions bound to the data-metadata group.
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
    SELECT 'data-metadata' parent_code, 'data-metadata:read' permission_code,
           '查看元数据' permission_name, '查看统一目录、跨类型搜索结果与概览' description,
           'data-metadata-catalog' menu_code
    UNION ALL
    SELECT 'data-metadata', 'data-metadata:create', '新建采集任务',
           '新建采集/对账任务、类型定义与扩展字段', 'data-metadata-collect'
    UNION ALL
    SELECT 'data-metadata', 'data-metadata:update', '元数据运维操作',
           '任务启停/dry-run/手动运行、修改类型与字段定义、确认标签与办结待办', 'data-metadata-collect'
    UNION ALL
    SELECT 'data-metadata', 'data-metadata:delete', '删除元数据配置',
           '删除采集任务、废弃类型定义与字段定义', 'data-metadata-collect'
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

-- 3. Menu group: metadata center.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-metadata', '元数据', NULL, NULL, 'database', 1,
 101, 1, 1, NULL, '元数据中心入口(采集/目录/搜索)', '${appName}')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name),
route_path = VALUES(route_path),
icon_key = VALUES(icon_key),
sort_order = VALUES(sort_order),
visible = VALUES(visible),
active = VALUES(active),
required_permission_code = VALUES(required_permission_code),
description = VALUES(description),
is_delete = 0;

-- 4. Child menus: overview, catalog, search, collect.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-metadata-overview', '元数据概览', 'data-metadata', '/data-metadata/overview', 'dashboard', 2,
 10, 1, 1, 'data-metadata:read', '覆盖率、采集健康度与治理待办总览', '${appName}'),
('data-metadata-catalog', '目录浏览', 'data-metadata', '/data-metadata/catalog', 'search', 2,
 20, 1, 1, 'data-metadata:read', '按实体类型下钻的层级目录(库→表→列)', '${appName}'),
('data-metadata-search', '统一搜索', 'data-metadata', '/data-metadata/search', 'insight', 2,
 30, 1, 1, 'data-metadata:read', '一次查询跨 8 类实体,含元模型新增的类型', '${appName}'),
('data-metadata-collect', '采集与对账', 'data-metadata', '/data-metadata/collect', 'check-circle', 2,
 40, 1, 1, 'data-metadata:read', '采集/对账任务、运行历史与 dry-run 熔断', '${appName}')
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

-- 5. Root administrators retain the full data-metadata menu catalog.
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
 AND menu_row.parent_code = 'data-metadata'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. Any granted child menu implies the data-metadata parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
 AND child_menu.parent_code = 'data-metadata'
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
