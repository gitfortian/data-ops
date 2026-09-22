# Offline Sync Dependencies

本文件定义离线同步的依赖方向。原则：**显式、窄、无环**。

## Internal Graph

| Source | Allowed |
| --- | --- |
| `controller` | `config`, `definition`, `execution`, `backfill` |
| `backfill` | `config`, `cursor`, `definition`, `domain`, `execution`, `repository` |
| `reconcile` | `config`, `domain`, `engine`, `execution`, `repository` |
| `execution` | `config`, `cursor`, `definition`, `domain`, `engine`, `mapping`, `repository`, `schedule` |
| `definition` | `config`, `domain`, `engine`, `mapping`, `repository`, `schedule` |
| `schedule` | `config`, `domain`, `repository` |
| `cursor` | `config`, `domain`, `repository` |
| `mapping` | `domain`, `engine` |
| `repository` | `config`, `dao`, `domain` |
| `dao` | `config` |
| `engine` | `config` |
| `domain` | none |
| `config` | none |

同一 top-level package 的子包属于同一子系统，但不会自动成为其他子系统的公共 API。

## Internal Corridors

```text
controller -> OfflineJobExecutionService
backfill   -> OfflineJobExecutionService / OfflineExecutionScopeValidator
reconcile  -> OfflineJobExecutionService

any        -> OfflineCursorGateway

definition -> OfflineScheduleLifecycle / OfflineScheduleSupport
execution  -> OfflineScheduleExecutionGateway
```

内部 Manager / Coordinator / query / adapter 不能因为 `public` 就成为跨子系统入口。

## Job Task Extension Corridor

Offline Sync 作为业务 owner，可以向通用 Job Runtime 注册能力：

```text
definition/OfflineSyncTaskProvider
    -> job.task.TaskProvider / TaskRegistration / TaskVersionSnapshot

execution/adapter/OfflineSyncTaskExecutor
    -> job.task.TaskExecutor / TaskExecution / TaskVersionSnapshot
```

这是**业务域实现通用扩展契约**，不是 Job 获得 Offline 领域所有权。

允许：

```text
offline -> io.yak.ops.business.job.task.* (declared contract only)
```

禁止：

```text
offline -> job.discovery / job.runtime / job.adapter / job.environment
```

该 corridor 由 `OfflineJobTaskExtensionBoundaryTest` 固化。

## Bottom Layers

```text
Domain     -> no upper-layer dependency
DAO        -> config only
Repository -> domain + dao + config
Engine     -> config only
```

Domain 不知道 Spring Controller、MyBatis、Job Runtime、Yak Schedule 或 Link-Up DTO；Credential 只在 outbound submit boundary 解析。

## No Cycles

Top-level dependency graph 必须无环。遇到循环先确认职责 owner，再设计窄 Facade / Gateway，不使用 lazy、反射或通用 Context 掩盖耦合。

## Dependency Injection

依赖通过构造器显式注入。构造器依赖过多通常表示职责过宽，应优先拆角色。

## Guardrails

```text
OfflineSyncLayeringConventionTest
OfflineSyncDependencyBoundaryTest
OfflineJobTaskExtensionBoundaryTest
```

新增依赖时先判断：类是否放错包、是否已有稳定入口、是否真的需要新 corridor。只有架构确实变化时，才同时修改本文件与测试。
