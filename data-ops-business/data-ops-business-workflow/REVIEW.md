# Workflow Review

> 本文件定义 Workflow PR **如何 Review**。Reviewer / AI 是裁判，不在 Review 中自行补需求或创造新的运行语义。

## Review 前必读

```text
REQUIREMENTS.md     -> 模块必须提供什么
DOMAIN.md           -> 哪些事实、版本和运行 truth 不能违反
ARCHITECTURE.md     -> 角色与调用 corridor
DEPENDENCIES.md     -> package graph / narrow ports
../../CODE_STYLE.md -> Yak Ops 统一工程规则
PR diff / tests     -> 实际改了什么
```

## 1. Requirement Alignment

先检查是否改变当前行为 contract：

- Definition create/update/publish/online/offline/delete 是否变化？
- WorkflowVersion immutable / Task revision snapshot 是否变化？
- manual / API / draft test / Schedule / Backfill launch 是否变化？
- retry/continue 是否仍复用同一 Execution？
- restart/rerun 是否仍创建新 Execution？
- Schedule strategy / misfire / effective window 是否变化？
- Trigger Ledger dedupe / SERIAL_WAIT / SERIAL_DISCARD / PARALLEL 是否变化？
- Backfill pinned version / businessDate rerun 是否变化？
- recovery / timeout / external TaskExecution binding 是否变化？
- REST / DTO/VO / DB schema 是否变化？

需求文档未定义的新能力报告 `Requirement Gap`，不要通过“顺手重构”加入。

## 2. Domain Compliance

重点检查：

- Definition / Version / ExecutionMetadata / EngineExecution 是否混淆；
- 历史 Version 是否读取 current Task revision；
- 运行中的 Execution 是否切换到新的 active version；
- Yak Ops 是否创建第二套 Node/Attempt runtime truth；
- retry/continue 与 restart/rerun identity 是否混淆；
- Schedule callback 是否绕过 Trigger Ledger；
- duplicate planned fire 是否可能创建第二个 Execution；
- Backfill 是否在创建后跟随新的 active version；
- businessDate rerun 是否丢失来源版本/调度血缘；
- Yak Schedule snapshot 是否反向成为业务配置 owner；
- SSE/log/latest-* 是否反向成为 command truth。

违反当前规则报告 `Domain Violation`；当前模型无法表达真实需求报告 `Domain Gap`。

## 3. Architecture Alignment

检查：

- package 是否按 Definition / Execution / Runtime / Schedule / Trigger / Backfill 等 subsystem 表达；
- 是否重新创建 `service/common/helper/utils/base/persistence` 大桶；
- `@Service` 是否只用于稳定 facade；
- Planner/Query/Reconciler/Adapter 是否偷变成全能 Service；
- Schedule 是否直接调用 Runtime/Engine；
- Execution 是否直接 import Backfill/Schedule implementation；
- Backfill-specific 逻辑是否越过 `WorkflowBackfillTriggerGateway` 泄漏进 Trigger Coordinator；
- Repository/DAO/Domain 是否保持向下边界；
- 是否用 `@Lazy` / Context lookup 掩盖 package cycle。

## 4. Dependency Alignment

任何新增内部 import 都检查 `DEPENDENCIES.md` 和 `WorkflowDependencyBoundaryTest`。

重点 corridor：

```text
definition -> WorkflowRuntime
execution  -> WorkflowRuntime
schedule   -> WorkflowLauncher / WorkflowExecutionReactivationGuard
backfill   -> Schedule roles + WorkflowLauncher / rerun port
runtime    -> WorkflowEventStream
```

新的反向调用优先由 capability user 定义窄 Port，不第一时间扩大双向依赖。

## 5. Correctness

必须给出可触发场景，不因为“代码复杂”就报告问题。

高风险区域：

- publish 与 activeVersion 原子性；
- TaskVersionSnapshot 冻结；
- active execution admission；
- engine start 后 metadata/trigger binding；
- NodeDispatch 重复/延迟/timeout；
- external task execution start/status/cancel；
- terminal execution -> Trigger Ledger 完成；
- REACTIVATING durable reservation；
- SERIAL_WAIT queue advance；
- schedule misfire/reconcile；
- Backfill occurrence dedupe；
- process crash 后 recovery；
- SSE client disconnect。

## 6. Compatibility

检查是否破坏：

- `/api/v1/workflows/**` URL；
- DTO / VO JSON shape；
- `yak_workflow_*` tables / Flyway history；
- persisted status / strategy / trigger-source strings；
- MyBatis Mapper XML；
- Yak Workflow Engine SPI；
- Yak Schedule handler/key contract；
- TaskExecutionGateway contract；
- existing published versions / executions / schedules / triggers / backfills。

架构治理默认不做 DB、REST、状态机 semantic breaking change。

## 7. Safety

优先检查：

- duplicate WorkflowExecution；
- retry 破坏 SERIAL_WAIT 串行槽位；
- schedule callback 绕过 durable admission；
- Backfill 使用 current active version 而非 pinned version；
- current Definition/Task revision 漂移到历史运行；
- crash 后丢失 external execution identity；
- timeout/recovery 猜测外部结果；
- 删除/下线误取消已经运行的 Execution；
- 日志输出未来可能引入的 credential/secret payload。

## 8. Tests / Guardrails

每个 P0/P1 问题都回答：

```text
现有哪个 behavior test 或 architecture test 应该挡住？
```

重点 guard：

- `WorkflowDependencyBoundaryTest`
- `WorkflowLayeringConventionTest`
- `WorkflowCodeStyleConventionTest`
- `WorkflowRoleConventionTest`
- Definition / Runtime / Trigger / Backfill / Schedule 的现有行为测试

修改 architecture corridor 时，文档与 executable test 必须在同一 PR 更新。

## 严重级别

```text
P0 Blocker
- 明确重复/错任务执行导致不可恢复数据或严重运行风险
- 凭据/敏感数据泄漏
- durable evidence 被不可恢复破坏

P1 Must Fix
- Requirement / Domain violation
- Version / Execution / Trigger truth ownership 错误
- snapshot / concurrency / schedule / ledger / recovery 错误
- 明确 API / DB compatibility break
- package cycle 或边界绕过造成真实耦合风险

P2 Suggestion
- 有明确收益但不影响正确性的可维护性、性能、可观测性建议
```

纯个人格式偏好不作为阻塞项。

## 固定输出

```text
# Review Result
Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 建议列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Missing Tests
无 / 说明
```

有 P0/P1 -> `CHANGES_REQUIRED`；只有 P2 可以 `PASS`。
