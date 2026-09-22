# Ticket 116：触发通道——调度 Bridge + Handler 与手工 run

**对应需求：** 物理元数据采集 | **阶段：** P1 | **模块：** metadata

**What to build：** 采集任务能被定时触发，也能被手工触发（`POST /api/v1/metadata/collect-jobs/{id}/run`，**开发/演示主路径**——Quartz 内存存储重启后不补跑），并把每轮结果写进 `yak_md_collect_run`。

**Blocked by：** 114、115

**调度形态沿用 lifecycle，不参考 OM**（OM 的 `AppScheduler` 是 Quartz 在 JVM 内，plan §3.7）：
- [ ] `MetadataScheduleEngineBridge`：`YakScheduleGateway.save(new ScheduleDefinition(key, name, ScheduleTrigger.cron(cron, ZoneId.systemDefault()), new ScheduleTarget(HANDLER, payload), SchedulePolicy.defaults(), true, metadata))`；登记前 `gateway.snapshot(name).isPresent()` 短路保幂等
- [ ] `MetadataCollectScheduleHandler implements ScheduleHandler`，`@Component(HANDLER)`
- [ ] **必须先恢复项目上下文再动手**：`projectScope.call(new ProjectContext(projectId, null), () -> …)`。调度线程无 HTTP 头，不恢复则 `@ProjectScope` 全链路失效、`CurrentProject.requireProjectId()` 直接抛——**采集任务最容易写错的一处**（模板 `lifecycle/schedule/LifecycleTtlScheduleHandler.java`）
- [ ] 调度器未装配时 `gateway.available()` 为 false → **静默跳过登记、不阻断启动**（lifecycle 现例 `if (!gateway.available()) return;`）
- [ ] `collect_job` CRUD + 启停 + `collect_run` 读写（四计数、状态、耗时、游标水位）
- [ ] 调度 namespace `DATA_METADATA`；默认 cron 每日 03:00（plan §11.2 第 5 条：物理采集可慢、投影对账应更密）
- [ ] 单测：**无请求头的调度线程**能写入带正确 `project_id` 的行（plan §10 测试 4）

**新建任务默认关 + 必经 dry-run 预览**才允许启用（plan §0.13 交互原则；实现在 ticket 122 的表单，本票提供 dry-run 能力位）。
