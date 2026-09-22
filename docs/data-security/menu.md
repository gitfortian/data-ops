# 数据安全（Data Security）—— 最终菜单设计

> 模块：`yak-ops-business-security`　菜单组：`data-security`　权限前缀：`data-security:`
> 原则：**只建数据安全特有的菜单；数据资产浏览/模型查看通过跳转进入；对外能力走 SPI 不设菜单。**

## 一、菜单结构（收敛为 1 组 + 6 页）

```
数据安全 (data-security)
├── 数据安全总览   data-security-overview   入口，安全态势
├── 分级分类       data-security-classification   等级/分类字典 + 资产标签 + 发现规则
├── 权限管理       data-security-access     数据访问策略与裁决
├── 数据脱敏       data-security-masking    算法字典 + 脱敏策略
├── 访问审计       data-security-audit      访问流水与安全视角统计
└── 合规管理       data-security-compliance 合规规则与体检报告
```

`sort_order = 8`（排在主数据管理 `mdm`=7 之后）。

## 二、页面权限映射

| 页面 menuCode | required_permission_code |
|---------------|--------------------------|
| data-security-overview | `data-security:read` |
| data-security-classification | `data-security:read` |
| data-security-access | `data-security:read` |
| data-security-masking | `data-security:read` |
| data-security-audit | `data-security:read` |
| data-security-compliance | `data-security:read` |

操作权限：`data-security:create` / `data-security:update` / `data-security:delete`（页面级 `@RequiresPermission` 按端点覆盖）。

## 三、注册迁移

`yak-ops-boot/src/main/resources/yak-security/db/migration/V2030__register_data_security_menu.sql`（当前最大 V2029 之后），5 步幂等结构（权限根 → 页面权限 → 菜单目录 → 授予 root 角色 → 子菜单隐含父组），对齐 mdm V2024 范式。

## 四、跳转（不建独立菜单）

- 从"分级分类/资产标签"跳转到数据源目录（datasource）/模型（modeling）浏览上下文。
- 从"访问审计"跳转到统一操作审计（audit 既有菜单）看配置生命周期留痕。
