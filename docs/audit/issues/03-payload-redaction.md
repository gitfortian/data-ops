# Ticket 03：敏感脱敏 + 可选请求体留痕（P1）

**对应需求：** 全量审计方案 M1 | **优先级：** P1 | **阻塞于：** 02 | **模块：** yak-ops-business-audit

**What to build：** 对显式声明 `@Auditable(recordPayload=true)` 的接口，把（脱敏后的）请求体写入 `yak_audit_operation.metadata_json.payload`，让审计能回答"改成了什么"；未声明的接口维持 02 的 method+path 底账不记 body。

**机制：**
- 新 `AuditPayloadRedactor`（audit 模块）：JSON 树遍历，字段名命中黑名单（不区分大小写：password/pwd/secret/token/credential/accesskey/privatekey/cookie 等，及 `*secret*`/`*key*` 模式）→ 值替换 `"***"`；超长截断（如 8KB）；非 JSON/表单只留参数名摘要。
- 拦截器（02）在 `recordPayload=true` 且请求 Content-Type 为 JSON 时缓存并脱敏 body；**多租户密码类接口（datasource 保存连接、security 凭证）严禁误开**——白名单代码评审制。
- 黑名单字段清单进配置（`yak.audit.redact-fields`，追加式，默认含内置项）。
- 读端：`AuditQueryController` 详情接口透出 payload；审计中心详情抽屉展示 JSON（前端如已有 metadata 展示则复用）。

**验收清单**（2026-09-22 落地，audit 模块含 6 例脱敏契约测试全绿）
- [x] `AuditPayloadRedactor` 单测：嵌套对象/数组/黑名单命中/配置追加词/截断(8KB)/非法 JSON 摘要/空体 六类（内置词表 password/secret/token/credential/authorization/cookie/accesskey/privatekey/jdbcurl…）
- [x] `recordPayload=true` 接口：`AuditPayloadCaptureFilter`（/api/* 写方法，16KB 缓存上限）+ 拦截器读取脱敏写 `metadata_json.payload`；未声明/缺省：无 payload 字段（表单只落参数名，敏感参数名亦打星）
- [x] 密码字段全链路不出现在库中（`recordPayloadStoresRedactedBody` 断言 doesNotContain 原文）
- [x] 读端零改动：`AuditOperationDetail.metadata` 已透传，详情抽屉可见
- [x] 配置追加：`yak.audit.web.redact-fields`
- **票面偏差记录：** 脱敏器不依赖 common 配置类，构造注入 ObjectMapper；黑名单在 `AuditWebProperties.redactFields` 追加合并。
