# Ticket 07：权限编码统一对账迁移（P2）

**对应需求：** 数据源缺失能力盘点 §6 第 7 行 + §3 E2 | **优先级：** P2 | **模块：** data-ops-common + data-ops-boot（迁移）+ data-ops-ui

**What to build：** 数据源模块三种命名风格并存：READ=`resource:data-source:read`（新式）、CREATE/UPDATE/DELETE/TEST=`datasource:*`（旧式）、SQL 执行观测=`resource:sql-execution:read`。统一到 `resource:data-source:*` 家族。

**设计：**
- `DataSourcePermissionCode`（data-ops-common constant 包）四个写操作常量值改为 `resource:data-source:create|update|delete|test`；Java 引用点不动（都走常量）。
- DB 侧对账迁移 `V20xx__reconcile_datasource_permission_codes.sql`（取号顺延，Ticket 04 会先占一个号）：**原地 UPDATE** `yak_security_permission.permission_code`（`datasource:create/update/delete/test` → 新值）——`permission_id` 不变，`yak_security_role_permission` 授权自然存活；旧 `datasource:view`、`datasource` 组行置 `active=0,is_delete=1` 退役（V1000 的退役姿势）。menu_code 绑定不变（`data-source`），页面 `required_permission_code` 不变。
- 注意 `declared` 位：SQL 管理行为 `declared=0`，UPDATE 不碰该位，防止声明式注册器把改码后的行下线。
- 前端 `yakOpsPermissions.ts` dataSource 块同步改字符串；grep 全仓（含测试/种子数据）确认无其它 `datasource:` 字面量残留。
- 老授权兼容：不做双码并存——同一 permission 行改码，已配角色即新码生效；未配置角色的用户行为不变。

**验收清单**
- [ ] 常量 + 前端常量同批改值
- [ ] reconcile 迁移幂等（UPDATE WHERE 旧码；退役旧行），仿 V2006 风格带注释头
- [ ] grep 无 `datasource:create` 等字面量残留（迁移/测试/V1000 历史文件除外——**已应用迁移不可改**）
- [ ] 后端模块编译 + 前端 `npx tsc --noEmit` 不新增错误
- [ ] navigationMenuContract 测试：`requiredPermissionCode ⊆ declared permissions` 断言仍绿

**验证边界：** 迁移与授权生效需用户重启后端后用 root 登录实测四类按钮权限。
