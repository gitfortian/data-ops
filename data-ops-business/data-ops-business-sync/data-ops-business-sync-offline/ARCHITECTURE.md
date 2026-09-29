# Offline Sync Architecture

本文件描述离线同步**当前有效架构**。不记录 Stage / Wave 迁移过程；历史设计以 Git / PR 为准。

领域语义看 `DOMAIN.md`，依赖规则看 `DEPENDENCIES.md`，编码约定看 `CODE_STYLE.md`。

## 设计原则

1. **业务子系统优先，技术分层第二。** package 本身就是架构图。
2. **稳定入口，隐藏内部。** Controller 和后台任务只进入明确的 Application Facade / Gateway。
3. **名字表达角色。** `Service / Coordinator / Manager / Runtime / Planner / Gateway / Adapter` 各有明确含义。
4. **运行真相只有一个主人。** Task 管配置，Batch 管业务批次，Attempt 管实际执行证据。
5. **外部系统停在边界。** Link-Up、Quartz、MyBatis、HTTP DTO、Credential 不进入 Core Domain。
6. **架构规则可执行。** 关键边界由 architecture tests 守护，而不是只写在文档里。

## Package Map

```text
io.yak.ops.business.sync.offline
├── controller          # HTTP inbound
├── definition          # Task definition application
├── execution           # Batch / Attempt execution subsystem
│   ├── query           # execution read model
│   └── adapter         # execution-boundary projection
├── backfill            # backfill planning / dispatch
├── cursor              # incremental cursor lifecycle
├── schedule            # schedule lifecycle / callback boundary
├── reconcile           # background state convergence
├── engine              # Link-Up outbound boundary
├── mapping             # transport / view mapping
├── repository          # domain persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── domain              # domain model / value objects
└── config              # module configuration
```

不要因为类多就新增 `service / common / helper / utils` 大桶目录。先判断它属于哪个业务子系统，再判断角色。

## Stable Application Entry

对外稳定入口只有三个 Application Service：

```text
OfflineJobDefinitionService
OfflineJobExecutionService
OfflineBackfillService
```

```text
HTTP Controller
   ├── OfflineJobDefinitionService
   ├── OfflineJobExecutionService
   └── OfflineBackfillService
```

`@Service` 只表达这种稳定 Application Facade。内部专业角色使用更准确的名字和 `@Component`。

后台入口也必须走声明过的 corridor：

```text
OfflineBackfillDispatcher   -> OfflineJobExecutionService
OfflineExecutionReconciler  -> OfflineJobExecutionService
OfflineScheduleHandler      -> OfflineScheduleExecutionGateway
                                  ^
                                  |
                           OfflineJobExecutionService
```

## Execution Core

```text
OfflineJobExecutionService
        |
        +-> OfflineExecutionCoordinator
        |       |
        |       +-> OfflineExecutionClaimManager
        |       |       +-> OfflineExecutionAttemptFactory
        |       |       `-> OfflineExistingBatchClaimManager
        |       +-> OfflineBatchScopeExecutionAdapter
        |       +-> LinkUpClient
        |       `-> OfflineExecutionStateManager
        |
        +-> OfflineExecutionQuery
        +-> OfflineExecutionLogQuery
        `-> OfflineBatchRuntime
```

核心角色：

| Role | Responsibility |
| --- | --- |
| `Coordinator` | 编排步骤，不拥有所有细节 |
| `ClaimManager` | 新 Batch admission |
| `ExistingBatchClaimManager` | Retry / pending Backfill 在已有 Batch 上创建 Attempt |
| `AttemptFactory` | 从冻结 Batch 创建 persistence Attempt |
| `StateManager` | Attempt 状态、事件、Retry window、Task projection |
| `BatchRuntime` | Batch runtime truth 与 latest Attempt 推导 |
| `Query` | read model，不承载 command 语义 |

## Main Flows

普通触发：

```text
Manual / Workflow / Schedule
 -> Application Entry
 -> Claim Batch + Attempt 1
 -> apply frozen scope / credentials
 -> Link-Up
 -> apply execution state
 -> refresh Batch truth
```

Retry：

```text
Failed Batch
 -> claimRetry
 -> same Batch / Scope / Snapshot
 -> new Attempt
```

Backfill / Cursor：

```text
Backfill Request
 -> OfflineBackfillPlanner
 -> PENDING Batch group
 -> Dispatcher
 -> Attempt
 -> Batch SUCCEEDED
 -> OfflineCursorGateway
 -> Cursor CAS advance
```

## Truth Ownership

```text
Task                 = long-lived configuration
BatchExecution       = business identity + frozen snapshot + runtime status
latest Attempt       = latest physical execution evidence
Task last-*          = query projection only
Cursor               = confirmed successful progress
Engine Job / Worker  = external runtime evidence
```

如果一个状态可以从两个地方“同时决定”，架构通常已经开始漂移。先明确 owner，再写代码。

## Change Rule

新增或移动代码前，依次回答：

1. 它属于哪个业务子系统？
2. 它是什么角色？名字是否能直接表达职责？
3. 谁是它的稳定调用入口？
4. 它读取和修改的 runtime truth 属于谁？
5. 是否跨子系统？如果是，已有 Gateway / Facade 是否足够？
6. 依赖方向是否符合 `DEPENDENCIES.md`？
7. 哪个测试能锁住这条架构规则？

答不清楚时，不要先创建 `Helper / Common / Utils / Base`；先把边界设计清楚。
