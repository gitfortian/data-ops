# Job Dependencies

本文件定义 Job 内部与跨模块的允许依赖方向。

## Internal Graph

```text
controller  -> task / environment
discovery   -> task
adapter     -> runtime / task
runtime     -> task / environment
environment -> dao
task        -> no Job implementation package
dao         -> persistence primitives only
```

依赖图必须保持无环。

## External Corridors

Job 可以依赖通用基础能力：

```text
core / spi / plugin-task-api / datasource capability
```

Job Core 不允许直接依赖具体业务域：

```text
business.sync.offline
business.workflow
business.development
```

业务域接入 Job 的正确方向是：

```text
Business Module
      -> job.task.TaskProvider
      -> job.task.TaskExecutor
```

当前 Offline Sync 就按这个方向提供 SYNC discovery / execution。

## Persistence

- Controller 不访问 DAO；
- Discovery / Runtime / Adapter 不访问 DAO；
- `environment` 可以通过 `SystemEnvVarDao` 持久化应用环境变量；
- Task contract 不依赖 MyBatis、PO、Repository 实现。

## Scheduler Boundary

Job 不依赖 Yak Schedule 业务生命周期，不允许出现 Job 自己的 Cron Registrar、Schedule Handler 或计划状态 owner。

## Compatibility

Deprecated `SyncTaskRunner / SyncTaskExecution / SyncTaskExecutorAdapter` 仅允许留在 `task`，不得注册 Spring Bean，也不得被新的生产代码引用。

依赖规则以 `JobDependencyBoundaryTest` 为可执行准绳。
