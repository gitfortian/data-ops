# Ticket 02：HTTP 写接口审计兜底拦截器 + 存量埋点合并（P0）

**对应需求：** 全量审计方案 M1 核心票 | **优先级：** P0 | **模块：** data-ops-boot + data-ops-business-audit

**What to build：** 让全部 437 个写接口"裸奔也有底账"：新增一个 MVC 拦截器，凡未手工埋点的非 GET 请求自动落一条 `yak_audit_operation`（SUCCEEDED/FAILED、actor、project、耗时、method+path），同时保证已手工埋点的 129 个操作点**不双记、零改动**。上线即达成写接口留痕 100%（粗粒度）。

**机制：**
- 新 `io.yak.ops.boot.audit.AuditWebInterceptor`（模板：`boot/project/ProjectScopeInterceptor.java` 的注册与排序，须在其之后以保证 project 上下文已就位；注册进现有 `WebMvcConfigurer`）。
- `preHandle`：GET/OPTIONS 直接放行；`@Auditable.ignore()=true` 放行；否则 request attribute 记开始时间。
- `afterCompletion`：
  1. **合并守卫**：若 `AuditContext`（`business/audit/AuditContext.java:10` ThreadLocal）本请求内已开账（operation_id 非空）→ 跳过。**开工先验证假设**：`JdbcBusinessAuditService.start()`（`:63`）确会写该 ThreadLocal、请求结束确会清理；若不成立则在 `start()` 落现成 carrier 处补标记（属本票最小改动）。
  2. 单段式直写终态（不走 RUNNING 两段）：`exception!=null || status>=400 → FAILED`，否则 SUCCEEDED；`operation_type/name/resource_type` 由 01 的 `AuditWebOperationInfer` 推导，`@Auditable` 存在时注解值**覆盖**推导值；`resource_id` 从路径变量/`resourceIdParam` 提取。
  3. `source='WEB'`（列已有，`V1__baseline_audit.sql`），actor/project 走现有 `AuditActorResolver` + `CurrentProject`；`metadata_json={method,path,httpStatus,durationMs,错误摘要}`。
  4. 复用 `JdbcBusinessAuditService` 的 fail-open 写路径（`safeWrite :443`），审计写失败绝不影响业务。
- 开关：`yak.audit.web.enabled`（默认 true），`AuditConfiguration.java:47` 处 `@ConditionalOnProperty`。
- `AuditQueryController` `/options`（`:65`）补 `source` 筛选项（WEB/其它），供审计中心区分兜底与业务埋点。

**已知边界（票尾记录，不阻塞）：** HTTP 200 + 业务错误码直返的失败，拦截器记 SUCCEEDED——语义级成败仍以手工埋点/06–08 注解为准；如需兜底感知，另立 ControllerAdvice 专项票。

**验收清单**（2026-09-22 代码与离线测试落地；boot 上下文红为存量，见下）
- [x] **假设修正：`start()` 并不写 `AuditContext` ThreadLocal**（后者仅异步恢复作用域，调研已证伪）。合并守卫改为新增 `business/audit/AuditWebLedger`：拦截器 preHandle `beginRequest()`，`JdbcBusinessAuditService.startInternal` 首行 `markSdkOperationOpened()`（仅在请求作用域内生效，非 HTTP 线程无副作用），afterCompletion `hasSdkOperation()` 判定跳过 + finally `endRequest()` 防线程复用脏值
- [x] 合并守卫单测：模拟已开账请求 → 拦截器不写第二行（`skipsWhenBusinessSdkAlreadyOpenedOperation` + `AuditWebLedgerTest` 2 例）
- [x] 兜底单测：POST 成功/HTTP 4xx/抛异常/@Auditable 覆盖/ignore 五类断言（含 duration、source=WEB），boot 侧 14 例全绿
- [x] 开关关闭后零写入（`yak.audit.web.enabled` 契约测试；关闭即不注册拦截器与 Filter）
- [x] GET 零写入；读语义 POST（含 `/api/v1/audit/operations/page`）不写；`exclude-paths` 默认排除 `/api/v1/audit/**`
- [x] `/options` source 筛选：读端天然具备（`JdbcAuditQueryService` distinct 派生 + `AuditOperationQuery.source` 谓词已存在），零改动
- [ ] 真机：重启后任一未埋点写接口（如 ModelingModelController 的 DdlService 链路）在审计中心可查到 WEB 记录、既有埋点记录不翻倍
- **验证边界：** `YakOpsApplicationTests` 上下文集成与 2 个架构测试为存量/环境红（boot 离线不可验，见项目记忆），与本票无关；新 Java 类验证交用户 IntelliJ 重启确认。
