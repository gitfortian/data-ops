# Ticket 04：SQL 执行审计观测页面（P1）

**对应需求：** 数据源缺失能力盘点 §6 第 4 行 | **优先级：** P1 | **模块：** data-ops-ui +（仅菜单迁移）data-ops-boot

**What to build：** 后端三接口已就绪且数据持续写入（`SqlExecutionAuditController`：`POST /api/v1/sql-executions/page`、`GET /{executionId}`、`POST /summary`，权限 `resource:sql-execution:read`，`/api/v1/sql-executions` 已注册进 `PROJECT_REQUEST_RULES`），前端零消费。补一个只读观测页。

**设计：**
- 菜单位置：数据接入组下、与「数据源管理」并列的新页面「SQL 执行审计」（menuCode `sql-execution-audit`，route `/sql-execution-audit`，required_permission_code `resource:sql-execution:read`）。
- 页面结构模板：`pages/data-security/audit/index.tsx`（单文件：summary 卡 + 过滤 + antd Table + 行点开详情抽屉）。详情展示语句明细（`SqlExecutionAuditDetailVO` 的 statement 列表：SQL 预览/耗时/行数/截断标记）。
- 服务层：新建 `services/sql-execution/{api.ts,types.ts}`，VO/DTO 字段照抄 `data-ops-common .../bean/vo/observability/` 与 `bean/dto/observability/`。
- 过滤条件以 `SqlExecutionAuditQueryDTO` 实际字段为准（caller、dataSourceId、时间范围等，实现时先读 DTO）。
- 数据源页联动：`DataSourceCard` 增加「执行记录」跳转 `/sql-execution-audit?dataSourceId=…`（可选加分项）。

**配套迁移：** `V2039__register_sql_execution_audit_menu.sql`（模板 V2034：权限 upsert + menu upsert + root 角色绑定），并把它 append 进 `navigationMenuContract.test.ts` 的 `CATALOG_EXTENSION_MIGRATIONS`；`navigation.ts` 加路由条目后核对 `navigation.test.ts` 中对 integration/resources 组路由清单的硬断言。若 V2039 已被并发会话占用则顺延取号。

**验收清单**
- [x] 页面 + 服务 + 类型（分页映射 `{bizData,pagination}` → Table `current/pageNo` 规则见项目记忆）
- [x] 详情抽屉渲染语句明细
- [x] V2039 菜单迁移 + CATALOG_EXTENSION_MIGRATIONS + navigation 测试同步
- [x] `npx tsc --noEmit` 不新增错误（按文件分组对比基线）——182 处均非本单文件；`DataSourceCard(56)` framer-motion Variants 与 `navigationMenuContract.test(263)` 均为存量（前者与 SummaryCards/Toolbar/index 同模式同错，后者所在行本单未触碰）
- [x] jest（临时 CJS 配置配方）跑 navigation 两套件：navigation.test.ts 全绿；navigationMenuContract.test.ts 19/20 绿，唯一红为并发会话未提交的 `navigation.ts` metric `parentGroupId: 'modeling'` 漂移（后端 V2035/V2036 定值 data-analysis），与本单无关，本单新增条目全部通过
- [x] DataSourceCard「执行记录」加分项已做：`ScrollText` 图标按钮 → `/sql-execution-audit?dataSourceId=…`，按 `resource:sql-execution:read` 权限显隐（`permissions.canReadSqlExecutions`）
- [ ] 真机 UI 验证待后端重启后由 Agent 走 browser-use 探页面

**验证边界：** 无新后端代码，仅新迁移；重启后 V2039 落库即生效。
