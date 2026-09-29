# Ticket 05：审计覆盖 CI 守卫——基线棘轮防回归（P1）

**对应需求：** 全量审计方案 M2（治本票：把"漏埋点"从静默变成显性） | **优先级：** P1 | **阻塞于：** 02 | **模块：** data-ops-boot(test)

**What to build：** 一个测试，扫描全部 `*Controller` 写 mapping（@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping），统计**无语义留痕**（既无 `@Auditable` 也无手工 `start()` 支撑）的接口数，与基线文件比对：**只降不升**，升了红。

**机制：**
- 扫描：boot 聚合全模块 classpath，反射/类扫描（对齐仓内既有架构矩阵测试的写法）枚举 handler method；判定链：路径在排除清单（登录、健康检查、内部心跳）→ 通过；有 `@Auditable`（含 ignore=true）→ 通过；其对应 Service 有手工 `BusinessAuditService.start`/`Audit.tx` → 通过（Service 关联可按 Controller→Service 注入字段近似判定，判定不了的入"人工确认"清单）。
- 基线文件：`docs/audit/coverage-baseline.json`，按模块记录 gap 数 + 明细（类#方法）；测试失败时输出 diff 明细。
- **02 落地即初始化基线**：此时全部写接口至少有兜底，基线记的是"语义缺口"而非"留痕缺口"；06–08 每批只改数字不改机制。
- 棘轮：新接口无 @Auditable → gap+1 → 红 → 开发者加注解（或 ignore）即绿。

**验收清单**
- [ ] 扫描测试可本地 + CI 运行，输出按模块汇总 + 明细 diff
- [ ] 初始化 baseline（当前估 ~308 个语义缺口），测试绿
- [ ] 人为在任一 Controller 加一个裸写接口 → 测试红且点名（自检例）
- [ ] 排除清单可配置并含 `/api/v1/audit/**`、登录、actuator
- **验证边界：** 纯测试票，无需重启。
