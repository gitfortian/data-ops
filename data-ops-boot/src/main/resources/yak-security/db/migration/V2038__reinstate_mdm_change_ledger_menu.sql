-- Reinstate the MDM change ledger menu, retired by V2036 on an inaccurate premise.
--
-- V2036 set mdm-approval visible=0/active=0 claiming the page "merged into the
-- approval center". Only the decision step moved: 变更单台账（按状态查全部申请、
-- 跳转审批单）与「撤回在途变更」两项能力审批中心各页都没有，而 /mdm/approval 被
-- 降级成 redirect 后整页无入口 ⇒ MDM 变更撤回零 UI 入口。页面本身（R4/ticket 60
-- 交付）一直在仓库里，这里只把菜单接回 /mdm 组并给出准确名称。
-- menu_code 是 RBAC 稳定契约，沿用 V2028 的 'mdm-approval' 不改；菜单名改为
-- 「主数据变更」，与同组 主数据X 命名一致，且不再暗示本页可通过/拒绝变更。
-- Idempotent: every statement is an upsert.

-- 1. Menu page: 主数据变更 under the mdm group, visible again.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('mdm-approval', '主数据变更', 'mdm', '/mdm/approval', 'workflow', 2,
 50, 1, 1, 'mdm:read', '主数据变更台账：变更申请进度、审批单跳转、撤回在途申请', '${appName}')
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

-- 2. Root administrators keep the page (V2028 grant survives V2036, this
-- re-asserts it for any environment whose role bindings were re-synced).
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
 AND menu_row.menu_code = 'mdm-approval'
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
