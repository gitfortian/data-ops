# Job Domain

> `REQUIREMENTS.md` 定义能力；本文件定义不能被实现细节破坏的事实。

## 核心模型

```text
TaskDefinition (discovery descriptor)
        │ freeze
        ▼
TaskVersionSnapshot (immutable execution input)
        │ run
        ▼
TaskExecution (one runtime attempt view)
```

硬规则：

```text
TaskDefinition != TaskVersionSnapshot != TaskExecution
```

## Truth Ownership

```text
Business Domain        = Task Definition / Release / business execution truth
Job TaskRegistry       = discoverable-task projection
TaskVersionSnapshot    = one execution's frozen input
TaskExecutionGateway   = type router
TaskExecutor           = task-type execution capability
TaskExecution          = common runtime view
Scheduler              = business-owned timing lifecycle
```

## Hard Rules

1. Registry 不是 Task Source Truth，只聚合 `TaskProvider`。
2. `TaskRegistration` 的 descriptor / snapshot 必须属于同一 Task ID 和 Type。
3. 有版本任务必须执行固定 Snapshot，禁止运行时回读当前配置。
4. `version=0` 表示未发布/无版本能力的兼容运行，不等于 Published Revision。
5. Gateway 只做路由；具体引擎和业务生命周期属于 `TaskExecutor` owner。
6. 同一种 Task Type 在一个应用中只能有一个 Executor。
7. Plugin Task 的幂等、状态、取消、异步生命周期只实现一套。
8. SQL / Python / Java / Shell Adapter 只贡献 Capability，不复制 Runtime。
9. SYNC 的定义和执行事实归 Offline Sync；Offline 通过 Job contract 反向注册能力。
10. Runtime Context 只依赖 `TaskEnvironmentResolver`，不依赖设置页面 CRUD。
11. Job 不注册 Cron，不维护业务 Schedule 状态。
12. 结构重构不得顺手改变 API、快照、状态或取消语义。

## Plugin Runtime

```text
TaskVersionSnapshot
  -> definition decode
  -> TaskPlugin validation
  -> TaskExecutionContext
  -> capability contribution
  -> plugin executor
  -> TaskExecution
```

`AbstractTaskExecutorAdapter` 拥有公共生命周期；Task Type Adapter 不重新实现 execution map、idempotency index、线程生命周期和状态转换。

## External Business Runtime

```text
Job contract
   ↑ implements
Offline Sync
   ├── OfflineSyncTaskProvider
   └── OfflineSyncTaskExecutor
```

依赖方向表达的是“业务域向通用 Runtime 注册能力”，不是 Job 获得 Offline 领域所有权。

## Compatibility Corridor

`SyncTaskRunner / SyncTaskExecution / SyncTaskExecutorAdapter` 仅保留给旧 Workflow 测试/构造器，必须无 Spring Bean 注册，并标记为待删除兼容类型。生产 SYNC 运行不得经过该 corridor。
