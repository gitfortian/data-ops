-- Rename the metric "对外供给" mis-named page menu (metric gap ticket 11 / R-13).
--
-- /metric/service actually hosts 标签管理 + 指标统计 + 指标目录, not metadata
-- serving (T54 metadata API has no page). Verdict R-13: rename the display
-- only — menu_code, route_path and permission bindings stay untouched, so
-- role_menu grants (PK-based) are preserved and no bookmark breaks.
-- Guarded UPDATE (row is guaranteed by V2029); naturally idempotent and it
-- cannot clobber any later re-parenting.

UPDATE yak_security_menu
SET menu_name   = '标签与目录',
    icon_key    = 'tags',
    description = '标签管理、指标统计与指标目录'
WHERE menu_code = 'metric-service'
  AND app_name  = '${appName}'
  AND is_delete = 0;
