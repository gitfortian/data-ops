# Ticket 03：连接健康定时巡检（P1）

**对应需求：** 数据源缺失能力盘点 §6 第 3 行 | **优先级：** P1 | **模块：** data-ops-business-datasource

**What to build：** `conn_status` 目前只有手动 `testSaved` 会写（`DataSourceConnectionTester.java:27-45`），summary 卡/首页数字长期失真。模块内新增低频探活 worker，按项目轮转回写状态。

**设计（内部 housekeeping，走仓内模式 B：Spring `@Scheduled`，不用平台调度引擎）：**
- 模板：`metadata/register/RegisterRetryWorker.java`（`@Scheduled(fixedDelayString = "${…}")` + `ProjectContextScope` 恢复项目上下文——DAO 层硬依赖 CurrentProject，见项目记忆 999 坑）+ `RegisterRetrySchedulingConfiguration` 的模块内 `@EnableScheduling` 开关类。
- 新 `DataSourceHealthProbeWorker`：查 `SELECT DISTINCT project_id FROM yak_ops_data_source`（DAO 补一个全项目扫描口，仅取 project_id，不泄露连接参数），逐项目 `projectScope.run(new ProjectContext(pid, null), …)` 内 `findAll` 后逐个 `tester.testSaved(id)`，吞掉异常（状态已由 Tester 回写为 DISCONNECTED）。
- 配置：`yak.datasource.health-probe.enabled`（默认 true）、`interval-ms`（默认 300000=5min，启动错峰用 initialDelay）；单实例守卫用 `AtomicBoolean running`（skip-if-ticking，对齐仓内无 ShedLock 的现实）。
- 不引入新 namespace（YakScheduleNamespaces 不加 DATASOURCE——本票是进程内巡检，不是用户可配作业）。

**验收清单**
- [x] `@EnableScheduling` 模块内开关类 + Worker，properties 挂 `DataSourceProperties`
- [x] DAO `selectDistinctProjectIds()`；探针循环不因单个源失败中断
- [x] 单测：mock Tester 抛异常仍继续、disabled 时不跑、重入跳过
- [x] `./mvnw -q -o -pl data-ops-business/data-ops-business-datasource -am test` 绿

> 2026-09-22 落地：`DataSourceSchedulingConfiguration` + `connection/DataSourceHealthProbeWorker`（`interval-ms`/`initial-delay-ms` 占位符直读 Environment，`enabled` 走 properties 绑定可动态关）；`DataSourceRepository#distinctProjectIds()` 为唯一跨项目读取口，只回 project_id。worker 单测 5/5、模块 131/131 绿。

**验证边界：** 巡检在启动后自动跑，需用户重启后观察 `conn_status`/`update_time` 变化（DB 直查即可，无 UI）。
