# 数据资产（Asset Center）—— 菜单与权限契约

> 注册迁移：`data-ops-boot/src/main/resources/yak-security/db/migration/V2032__register_data_asset_menu.sql`（幂等，模板=V2031 lifecycle 单组式，5 步结构：权限根 → 页面权限 → 菜单目录 → 授予 root 角色 → 子菜单隐含父组）。

## 组与页

| menuCode | 名称 | 路由 | iconKey | sort | 需要的权限 |
|---|---|---|---|---|---|
| `data-asset` | 数据资产（组, section=management, order 25） | `/data-asset`（redirect→overview） | `database` | 100 | data-asset:read |
| `data-asset-overview` | 资产概览 | `/data-asset/overview` | `dashboard` | 10 | data-asset:read |
| `data-asset-catalog` | 资产目录 | `/data-asset/catalog` | `search` | 20 | data-asset:read |
| `data-asset-inventory` | 盘点上架 | `/data-asset/inventory` | `check-circle` | 30 | data-asset:read |
| `data-asset-taxonomy` | 目录与标签 | `/data-asset/taxonomy` | `tags` | 40 | data-asset:read |

隐藏路由：`/data-asset/detail/:id`（资产 360°，不在菜单出现，权限 `data-asset:read`）。

## 权限码（`AssetPermissionCode`，common `constant/asset/`）

| permission_code | 名称 | 绑定菜单 |
|---|---|---|
| `data-asset:read` | 查看数据资产 | data-asset-catalog |
| `data-asset:create` | 登记资产/新增目录标签规则 | data-asset-taxonomy |
| `data-asset:update` | 上下架/改负责人/打标/移目录/规则启停/变更确认 | data-asset-inventory |
| `data-asset:delete` | 删除台账/目录/标签/规则 | data-asset-taxonomy |

- 上架/下架（状态机流转）归 `update`；对账手动触发归 `update`；概览与目录页仅 `read`。
- 页面级 `@RequiresPermission` 按端点覆盖，模式同 lifecycle。

## 前端登记

- `src/constants/securityMenuCodes.ts`：`asset: 'data-asset'`, `assetOverview: 'data-asset-overview'`, `assetCatalog: 'data-asset-catalog'`, `assetInventory: 'data-asset-inventory'`, `assetTaxonomy: 'data-asset-taxonomy'`
- `src/config/navigation.ts`：组 `{ id:'data-asset', title:'数据资产', iconKey:'database', section:'management', order:25 }`（management 段现状：资源管理 20 → **数据资产 25** → 数据质量 30 → 数据安全 35 → 数据消费 40 → 数据服务 50，资产中心作为治理门面插在质量之前）；四条路由带 `mode:'one', permission:'data-asset:read'`；详情为隐藏路由。
- 契约测试：`navigationMenuContract.test.ts` 的 CATALOG_EXTENSION_MIGRATIONS 追加 V2032。

## 跳转（不建独立菜单）

- 详情血缘 Tab → 全屏血缘图谱（lineage 既有路由，带 assetKey 参数）。
- 详情"源对象"链接 → modeling 模型详情 / metric 指标详情 / dataset 详情等源域页面。
- 概览待办"未定级资产" → 数据安全-分级分类页（security 既有菜单）。
