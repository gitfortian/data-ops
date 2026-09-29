# Ticket 04：登录审计——成功/失败/登出留痕（P1）

**对应需求：** 全量审计方案 M2 | **优先级：** P1 | **阻塞于：** 01 | **模块：** data-ops-boot

**What to build：** 补齐当前完全空白的登录留痕（调研证实：全仓无 login audit，`yak_security_oplog` 亦无登录写入），回答"谁在何时从哪个 IP 登录成功/失败了几次"。

**机制：**
- 登录实现来自外部安全框架 jar（`io.yak.framework.security`，仓库仅有反编译产物 `output/decompiled-remote/`），**不可改其代码**。两条路按可行性择一，开工先探测：
  1. 优先：boot 内 `@Aspect` 环绕框架 `LoginService`（或认证入口 bean）的登录成功/失败方法——全仓首个切面，需在 boot 引入 spring-boot-starter-aop；
  2. 备选：`OncePerRequestFilter` 挂登录/登出端点路径，读 response 判定成败（外部端点路径需实测确认）。
- 写统一审计：`operation_type=AUTH_LOGIN_SUCCESS/AUTH_LOGIN_FAILED/AUTH_LOGOUT`（01 常量），actor=提交的用户名（**失败时不能走 `AuditActorResolver`，其未登录会降级 SYSTEM**——从登录请求参数直取），`source='WEB'`，metadata 记 IP + User-Agent 摘要，**绝不含口令字段**。
- 与 02 的默认白名单协同：登录路径排除在通用写兜底之外，避免双记。

**验收清单**
- [ ] 错误口令登录 → 审计中心可见 AUTH_LOGIN_FAILED 且 actor=尝试用户名
- [ ] 正确登录/登出 → 成对 SUCCESS/LOGOUT 记录，含 IP
- [ ] 登录路径不产生第二条 WEB 兜底记录
- [ ] payload/metadata 无口令明文（测试断言）
- **验证边界：** 需真机登录流程验证（用户 IntelliJ 重启后，见项目验证惯例）。
