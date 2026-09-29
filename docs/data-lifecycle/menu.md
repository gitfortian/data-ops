# 数据生命周期（TTL）—— 菜单与权限契约

> 注册迁移：`data-ops-boot/src/main/resources/yak-security/db/migration/V2031__register_data_lifecycle_menu.sql`（幂等，模板=V2029 metric 单组式）。

## 组与页

| menuCode | 名称 | 路由 | iconKey | sort | 需要的权限 |
|---|---|---|---|---|---|
| `data-lifecycle` | 数据生命周期（组, section=task, order 9） | `/data-lifecycle`（redirect→policy） | `monitor` | 90 | data-lifecycle:read |
| `data-lifecycle-policy` | 策略管理 | `/data-lifecycle/policy` | `database` | 10 | data-lifecycle:read |
| `data-lifecycle-monitor` | TTL 监控 | `/data-lifecycle/monitor` | `monitor` | 20 | data-lifecycle:read |
| `data-lifecycle-storage` | 存储统计 | `/data-lifecycle/storage` | `insight` | 30 | data-lifecycle:read |

隐藏路由：无独立详情路由（Tab 在 modeling 详情页内）。

## 权限码（`LifecyclePermissionCode`，constant/lifecycle/）

| permission_code | 名称 | 绑定菜单 |
|---|---|---|
| `data-lifecycle:read` | 查看数据生命周期 | data-lifecycle-policy |
| `data-lifecycle:create` | 新建生命周期策略 | data-lifecycle-policy |
| `data-lifecycle:update` | 编辑策略/绑定/下发 | data-lifecycle-policy |
| `data-lifecycle:delete` | 删除策略/解绑 | data-lifecycle-policy |

下发（执行 ALTER）归入 `update`；监控与存储页仅 `read`。

## 前端登记

- `src/constants/securityMenuCodes.ts`：`lifecycle: 'data-lifecycle'`, `lifecyclePolicy: 'data-lifecycle-policy'`, `lifecycleMonitor: 'data-lifecycle-monitor'`, `lifecycleStorage: 'data-lifecycle-storage'`
- `src/config/navigation.ts`：组 `{ id:'data-lifecycle', title:'数据生命周期', iconKey:'monitor', section:'task', order:9 }`；三条路由带 `mode:'one', permission:'data-lifecycle:read'`
- 契约测试：`navigationMenuContract.test.ts` 的 CATALOG_EXTENSION_MIGRATIONS 追加 V2031。
