# Workflow Architecture

本文件描述 Workflow **当前长期架构**。它记录现在有效的 package、角色、truth ownership 和调用 corridor，不记录迁移过程。

需求看 `REQUIREMENTS.md`，领域规则看 `DOMAIN.md`，依赖矩阵看 `DEPENDENCIES.md`，统一工程规范看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。

## 1. Principles

1. **Package is architecture.** Definition / Execution / Runtime / Schedule / Trigger / Backfill 按业务子系统组织，而不是统一放进 Service/Impl。
2. **Role name is contract.** Manager / Launcher / Runtime / Query / Coordinator / Reconciler / Adapter 各自表达职责。
3. **Version 与 Runtime 分离。** mutable Definition、immutable Version、engine Execution 不能合并。
4. **运行真相单一 owner。** WorkflowExecution / Node / Attempt 由 Yak Workflow Engine 拥有。
5. **计划触发先入 Ledger。** Schedule / Backfill 先完成 durable admission，再进入 Launcher。
6. **历史执行冻结。** Published Version / Task snapshot / Trigger strategy 不能被 current config 隐式改写。
7. **跨子系统使用窄 corridor。** 需要反向协作时由能力使用方定义窄 Port，避免 package cycle。
8. **规则可执行。** dependency / layering / role / code-style tests 与文档一起维护。

## 2. Package Map

```text
io.yak.ops.business.workflow
├── controller
│   └── v1
├── definition              # draft / version / graph / task binding
├── execution               # launcher / operations / reactivation ports
├── runtime                 # engine runtime adapter / recovery
├── schedule                # schedule definition lifecycle / reconcile / audit orchestration
│   ├── engine              # Yak Schedule bridge
│   └── trigger             # durable trigger ledger / admission / queue
├── backfill                # backfill batch / rerun / trigger adapter / audit orchestration
├── observability           # SSE projection
├── repository              # durable business/engine persistence adapters
│   └── support
├── dao                     # MyBatis/ledger persistence primitives
└── domain                  # workflow values / identities / immutable semantics
```

production 不创建 `service / common / helper / utils / base / persistence` 业务大桶。

## 3. Application Entry

Workflow 保留少量真正稳定的 Application/Runtime facades：

```text
WorkflowDefinitionManager
WorkflowLauncher
WorkflowExecutionManager
WorkflowExecutionReactivator
WorkflowRuntime
WorkflowBackfillManager
```

它们可以使用 `@Service`，因为它们是稳定 use-case boundary，而不是因为“所有 Bean 都叫 Service”。Audit Coordinator / Planner / Query / Coordinator / Reconciler / Adapter / Guard / EventStream 等内部专业角色使用 `@Component`。

Audit Coordinator 只负责围绕已有业务入口记录业务事实，不成为 Definition / Execution / Schedule / Backfill 的第二 truth owner。

## 4. Definition Subsystem

```text
Controller
    -> WorkflowDefinitionAuditCoordinator
         -> WorkflowDefinitionManager
              -> WorkflowTaskBindingResolver
              -> WorkflowStartGraphCompiler
              -> WorkflowDefinitionRepository
              -> WorkflowRuntime
```

DefinitionManager 拥有 current draft、publish、active version、Task revision binding 与 definition-level safety。

`WorkflowStartGraphCompiler` 只把编辑态图转换为运行图；`WorkflowTaskBindingResolver` 负责 TaskAsset/TaskRevision snapshot；它们不拥有发布状态。

Schedule 激活/停用联动属于 `schedule.WorkflowDefinitionScheduleGuard`，避免 Definition 反向依赖 Schedule 生命周期。

## 5. Execution / Launch Subsystem

`WorkflowLauncher` 是创建新 WorkflowExecution 的统一 corridor：

```text
Manual/API
Draft Test
Schedule Trigger
Backfill Trigger
Restart / Rerun
        |
        v
  WorkflowLauncher
        |
        +-> WorkflowExecutionAuditBridge
        +-> resolve active/pinned version
        +-> record TriggerContext
        v
  WorkflowRuntime
```

`WorkflowExecutionReactivator` 处理同一个 Execution 的 retry/continue。它只依赖 execution-owned `WorkflowExecutionReactivationGuard`；Schedule Trigger Coordinator 实现该 Port，从而能施加 Ledger 串行槽位规则而不制造 execution -> schedule package cycle。

`WorkflowExecutionManager` 负责实例运维 read/use-case。businessDate rerun 通过 execution-owned `WorkflowBusinessDateRerunGateway` 进入 `WorkflowBackfillAuditCoordinator`，而不是直接认识 Backfill business implementation。

## 6. Runtime Subsystem

```text
WorkflowRuntime
    -> Yak Workflow Engine
    -> TaskExecutionGateway
    -> WorkflowRuntimeRepository
    -> WorkflowEventStream
```

Runtime 负责 engine adapter、NodeDispatch、TaskExecution polling、timeout、pause/resume/cancel、retry/restart/rerun 和 VO projection。

`WorkflowRuntimeRecovery` 只负责应用启动后的 durable recovery orchestration，不成为第二 runtime owner。Runtime 内存中的 dispatch queue、control map、active set 都是可恢复辅助状态。

Business Audit 不进入 Runtime 状态机；Execution 终态通过 durable `WorkflowExecutionTerminalEvent` AFTER_COMMIT 投影到 Audit。

## 7. Schedule Subsystem

```text
HTTP direct schedule mutation
Yak Schedule automatic expire/disable
    -> WorkflowScheduleAuditCoordinator
    -> create / revision / lifecycle
    -> durable schedule DAO
    -> WorkflowScheduleEngineBridge
         -> Yak Schedule

Workflow online/offline linkage
    -> WorkflowDefinitionScheduleGuard
    -> WorkflowScheduleAuditCoordinator
    -> lifecycle only (no second AuditOperation)

Yak Schedule callback
    -> WorkflowScheduleTriggerHandler
    -> WorkflowScheduleTriggerCoordinator
```

`WorkflowScheduleAuditCoordinator` 记录直接 Schedule mutation 和无父业务操作的 Scheduler 自动 lifecycle。Cron、timezone、日期区间和策略可作为审计业务事实，Schedule `input` 原值不能复制到 Audit。

Workflow online/offline 的 Schedule 联动属于父 Workflow 操作的派生副作用，不再生成第二条 Schedule AuditOperation；这样既控制 Audit Center 噪音，也保证 Project authorization decision 仍归属父 `WORKFLOW_ENABLE/DISABLE`。

`WorkflowScheduleEngineBridge` 是 framework boundary；Schedule 业务表仍是配置 truth。

`WorkflowScheduleReconciler` 恢复 framework projection；`WorkflowScheduleRuntimeState` 只维护 last/next fire projection；`WorkflowScheduleMisfireRecovery` 生成 durable recovery Trigger。

## 8. Trigger Ledger Subsystem

```text
callback / Backfill occurrence
        -> WorkflowScheduleTriggerCoordinator
        -> WorkflowScheduleTriggerAdmission
        -> durable Trigger Ledger
        -> WorkflowLauncher
        -> WORKFLOW_EXECUTE audit
```

Admission 拥有 claim/dedupe、SERIAL_WAIT/SERIAL_DISCARD/PARALLEL、LAUNCHING/REACTIVATING reservation、execution binding 与队首推进。

Coordinator 负责跨步骤编排，不复制 Admission 的事务规则。

Trigger Ledger 不为每个 RECEIVED/WAITING/SKIPPED 状态额外创建 Audit Center 行。真正启动的业务执行由 `WorkflowLauncher` 的 `WORKFLOW_EXECUTE` 记录，并通过 triggerId/scheduleId/backfillId 保留来源血缘。

Backfill-specific trigger 创建/加载/launch 被隔离在 `WorkflowBackfillTriggerGateway` 后面；其实现位于 Backfill 子系统，Schedule 不 import Backfill implementation。

## 9. Backfill Subsystem

```text
HTTP create/cancel
Execution businessDate rerun port
    -> WorkflowBackfillAuditCoordinator
    -> WorkflowBackfillManager
         -> WorkflowBackfillPlanner
         -> durable Backfill DAO
         -> WorkflowScheduleTriggerCoordinator

WorkflowBackfillTriggerAdapter
    -> WorkflowBackfillQuery
    -> WorkflowScheduleParameterResolver
    -> WorkflowLauncher
```

Manager 拥有批次 create/cancel/businessDate rerun 的业务语义；Planner 只生成 occurrence；Reconciler 只根据 durable batch 补齐 Trigger。

`WorkflowBackfillAuditCoordinator` 是 execution-owned `WorkflowBusinessDateRerunGateway` 的唯一 Backfill 实现，并把 create/cancel/rerun 转成管理 AuditOperation。每个真正启动的子 WorkflowExecution 继续由 Launcher 独立审计。

## 10. Observability

`WorkflowEventStream` 只维护 SSE client 与 heartbeat，并消费 Runtime 投影。它不读取/修改 Definition、Schedule、Trigger Ledger 或 Engine persistence，SSE 断开也不影响执行。

Business Audit 是独立业务解释投影，不取代 SSE、Runtime log 或 Trigger Ledger。

## 11. Persistence Boundary

Workflow 当前存在两类 durable boundary：

```text
Definition / Runtime
    -> Workflow*Repository
    -> Repository Adapter
    -> DAO / Engine SPI

Schedule / Trigger / Backfill coordination
    -> narrow Workflow*Dao ledger primitives
```

Schedule/Trigger/Backfill 的 DAO 直接使用是当前短事务/ledger coordination contract；不得扩散到 Controller 或 Domain。若未来进一步收口 Repository，应作为独立 persistence refactor，不借治理白名单伪装。

Repository contract 不暴露 Workflow HTTP DTO/VO/PO/MyBatis。

## 12. Cross-module Boundaries

### Yak Workflow Engine

Runtime 与 engine persistence adapter 可以依赖 `yak-workflow-engine`。业务层不重新实现 engine state machine。

### Business Job

```text
Definition -> TaskRegistry / TaskAsset revision resolution
Runtime    -> TaskExecutionGateway
```

Workflow 不直接执行具体任务实现。

### Yak Schedule

只有 Schedule framework boundary 负责保存/暂停/删除 timer projection；业务 Trigger 仍由 Workflow Trigger Ledger 管理。

## 13. Truth Ownership

```text
Definition       = current mutable config
Version          = immutable publish snapshot
Runtime metadata = Yak Ops launch/version/trigger context
Engine Execution = runtime state truth
Schedule         = durable timer config
Trigger Ledger   = dedupe/admission/lineage truth
Backfill         = pinned-version batch truth
Business Audit   = historical business-action explanation projection
SSE/log          = projection only
```

出现两个角色同时决定同一个 truth 时，先修 ownership，不要通过静态 Context、Helper 或新的状态字段掩盖冲突。

## 14. Change Rule

新增/移动代码前回答：

1. 属于哪个 Workflow subsystem？
2. 它是 Manager / Launcher / Runtime / Query / Coordinator / Reconciler / Adapter 中哪个角色？
3. 它拥有哪个 truth，还是只读取/投影？
4. 新 dependency 是否符合 `DEPENDENCIES.md`？
5. 是否让 current Definition/Task revision 漂移进历史 Version/Execution？
6. 是否绕过 Trigger Ledger 或 WorkflowLauncher？
7. 是否制造第二个 engine runtime owner？
8. 哪个 behavior test 和 architecture test 会保护这次修改？

答不清楚时不要创建新的 ServiceImpl/Common/Helper。
