# Ticket 01：@Auditable 注解 + 写接口操作类型推导工具（P0）

**对应需求：** 全量审计方案 M1（docs/audit/issues/tickets.md） | **优先级：** P0 | **模块：** yak-ops-common + yak-ops-business-audit

**What to build：** 提供声明式审计语义的"词汇表"，本票**纯新增、不改任何行为**：注解被 02 的拦截器消费，也被 06–08 的语义补齐引用。

**机制：**
- 新注解 `io.yak.ops.common.audit.Auditable`（放 **common** 而非 audit 模块：全业务模块已直接/传递依赖 common，零 pom 改动；仅 yak-ops-business-job 无直依赖，届时补一行）：
  - `String name()`（可读操作名，如"发布模型"）
  - `String type() default ""`（操作类型码，空则由 02 从路径推导）
  - `String resourceType() default ""` / `String resourceIdParam() default ""`（SpEL 或参数名，取资源标识）
  - `boolean recordPayload() default false`（03 消费）
  - `boolean ignore() default false`（02 跳过：内部心跳/批量导入等）
- 新工具 `io.yak.ops.business.audit.AuditOperationTypes`（常量类）：`MODULE_ACTION` 命名规范的常量池（如 `MODELING_MODEL_PUBLISH`），并集中登录事件常量 `AUTH_LOGIN_SUCCESS / AUTH_LOGIN_FAILED / AUTH_LOGOUT`（04 消费）。
- 新推导器 `AuditWebOperationInfer`：由 `HandlerMethod`（HTTP method + mapping 路径）推导 `(operationType, operationName, resourceType)`；规则表：POST→CREATE（路径含 /page|/query|/test|/execute 等动词例外映射）、PUT→UPDATE、DELETE→DELETE、PATCH→UPDATE；模块段取 `/api/v1/{module}` 首段。
- 推导规则**先建契约测试锁死**（表驱动，输入 mapping → 期望三元组），覆盖现有 131 个 Controller 的代表性路径。

**验收清单**（2026-09-22 全部落地，audit 模块 66 例绿）
- [x] `@Auditable` 注解五属性齐备（`common/annotation/Auditable.java`），javadoc 一行说明默认值语义
- [x] `AuditOperationTypes` 常量 + `AuditWebOperationInfer` 落在 audit 模块，仅依赖 JDK/Jackson
- [x] 契约测试 44 例表驱动（含 /page、/test-connection、多级资源、无版本段、非法输入边界）
- [x] 推导器为纯字符串函数（httpMethod + 路径模板入参），audit 模块零新增依赖
- [x] `mvn -o -pl yak-ops-business/yak-ops-business-audit test` 绿
- **验证边界：** 本票无运行时行为，无需重启验证。
