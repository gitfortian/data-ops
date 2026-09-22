# Yak Ops Workflow

Workflow 是 Yak Ops 的持久化编排控制面：负责可编辑工作流定义、不可变发布版本、统一启动入口、运行实例控制、调度 Trigger Ledger、Backfill/运维补跑、恢复与实时观测。

真正的节点调度状态机由 Yak Workflow Engine 提供；Workflow 模块不再复制一套 Execution / NodeExecution / NodeAttempt 状态机。

```text
WorkflowDefinition
    -> publish immutable WorkflowVersion
    -> WorkflowExecution
         -> NodeExecution
              -> NodeAttempt

WorkflowSchedule -> Trigger Ledger -> WorkflowExecution
WorkflowBackfill -> pinned WorkflowVersion -> Trigger Ledger
```

## Read First

本目录只维护**当前有效 contract**。历史演进通过 Git / PR 追溯。

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | Workflow 必须提供哪些行为 |
| [`DOMAIN.md`](./DOMAIN.md) | 哪些事实、版本和运行生命周期不能混淆 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | package、角色与运行 corridor 如何组织 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 可以依赖谁、跨边界走哪里 |
| [`../../CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 统一工程与角色规范 |
| [`REVIEW.md`](./REVIEW.md) | Workflow PR 按什么标准评审 |

## Package Shape

```text
workflow/
├── controller          # HTTP inbound
├── definition          # draft / publish / version / graph / task binding
├── execution           # launch / instance operations / reactivation ports
├── runtime             # Yak Workflow Engine runtime integration / recovery
├── schedule            # durable schedule lifecycle / reconcile
│   ├── engine          # Yak Schedule bridge
│   └── trigger         # trigger ledger / admission / dedupe / queue
├── backfill            # backfill batch / business-date rerun / trigger adapter
├── observability       # SSE event stream
├── repository          # durable definition/runtime/engine adapters
├── dao                 # schedule/backfill/ledger/MyBatis primitives
└── domain              # framework-free workflow values and trigger identities
```

production 不维护 `workflow/service/**` 或 `workflow/persistence/**` 这类通用业务桶。Spring Bean 使用 Manager / Launcher / Runtime / Coordinator / Query / Reconciler / Adapter 等真实角色名。

## Truth Ownership

```text
WorkflowDefinition                   = current editable definition truth
WorkflowVersion                      = immutable published graph + task revision snapshot
Workflow execution business metadata = Yak Ops durable launch/version/trigger context
WorkflowExecution / Node / Attempt   = Yak Workflow Engine runtime truth
WorkflowSchedule                     = durable schedule configuration truth
WorkflowScheduleTrigger              = trigger dedupe/admission/lineage truth
WorkflowBackfill                     = pinned-version batch truth
Yak Schedule                         = timer/trigger projection
TaskExecutionGateway                 = underlying task execution boundary
```

最重要的规则：**一个运行事实只能有一个 owner**。Yak Ops 可以保存运行上下文和投影，但不能反向重写 Yak Workflow Engine 已确认的运行状态。

## Main Corridors

```text
HTTP
  -> Definition / Execution / Runtime / Schedule / Backfill roles

Manual/API/Schedule/Backfill
  -> WorkflowLauncher
  -> WorkflowRuntime
  -> Yak Workflow Engine
  -> TaskExecutionGateway

Schedule callback
  -> Trigger Ledger admission
  -> WorkflowLauncher

Backfill
  -> pinned WorkflowVersion
  -> Trigger Ledger
  -> WorkflowLauncher
```

完整依赖矩阵与窄 corridor 见 `DEPENDENCIES.md`。
