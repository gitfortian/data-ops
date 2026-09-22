# Ticket 86：失败重试定时任务 + 漂移检测

**对应需求：** 3.6 / D5 / D7 | **阶段：** P1 | **模块：** lifecycle

**What to build：** 平台每 30 分钟自动重试失败下发（上限 5 次→EXHAUSTED 进异常区）；策略内容或模型绑定变更后，已应用模型自动标"漂移 DRIFT"，监控可见并支持重新下发；用户也可在流水行点【立即重试】。

**Blocked by：** 85

**硬性约束：** 业务表是重试事实源，Quartz 只是闹钟（D7）；handler 内必须 `ProjectContextScope.call(new ProjectContext(payload.projectId))`。

**验收清单**
- [ ] common `YakScheduleNamespaces` 新增 `DATA_LIFECYCLE = "yak-ops-lifecycle"`
- [ ] `schedule/LifecycleScheduleEngineBridge`（cron `0 0/30 * * * ?`，payload projectId）+ `LifecycleRetryScheduleHandler`（bean 名常量）+ `LifecycleScheduleLifecycle`
- [ ] 重试逻辑：扫 `FAILED/RETRYING 且 next_retry_time<=now 且 attempts<5` → 重新执行 → 更新 attempts/status/finish_time；退避 30m×attempts
- [ ] 漂移：`TtlMonitorService` 按 `policy.updated_at > dispatch_record.policy_updated_at` 推导 DRIFT（无独立字段，避免双写）；策略编辑/绑定变更后无需回填
- [ ] `POST /dispatch-records/{id}/retry`（手动重试，立即执行一次）
- [ ] 单测：重试推进/耗尽、漂移推导（时间比较各分支）
