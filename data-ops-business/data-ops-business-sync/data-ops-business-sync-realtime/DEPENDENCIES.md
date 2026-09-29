# Realtime Sync Dependency Rules

本文件定义 Realtime Sync 的长期包依赖 contract。`ARCHITECTURE.md` 解释为什么这样分层，本文件回答：**一个 package 可以依赖谁、跨子系统必须经过哪条走廊、哪些反向依赖绝对禁止。**

这些规则由 `RealtimeSyncDependencyBoundaryTest` 扫描 `src/main/java` 的真实 import 并执行，文档与测试必须同步修改。

## 1. 原则

Realtime Sync 使用**显式、窄、无环**依赖图：

- package 是架构，不把职责藏进 `service / common / helper / utils`；
- 上层可以依赖下层，下层不反向调用 Application；
- 跨业务子系统优先通过稳定 Facade / Resolver / Gateway / Repository contract；
- 允许的 dependency 不等于鼓励依赖，能留在本子系统就不要跨包；
- 新增一条跨包 import 前，先确认它是否需要成为长期 corridor；
- 所有 Realtime Sync top-level package 必须保持无环。

## 2. Top-level dependency matrix

`A -> B` 表示 A 可以 import B。未列出的依赖默认禁止。

| Source | Allowed Realtime Sync targets |
| --- | --- |
| `controller` | `definition`, `domain`, `environment`, `execution`, `observability` |
| `definition` | `domain`, `engine`, `environment`, `execution`, `repository` |
| `execution` | `domain`, `engine`, `environment`, `reconcile`, `repository` |
| `reconcile` | `config`, `domain`, `engine`, `environment`, `repository` |
| `observability` | `domain`, `engine`, `environment`, `repository` |
| `environment` | `domain`, `engine`, `repository` |
| `engine` | `config`, `domain`, `repository` |
| `repository` | `config`, `dao`, `domain` |
| `dao` | `config` |
| `config` | `domain` |
| `domain` | none |

同一 top-level package 内的 subpackage import 不视为跨子系统，例如 `execution -> execution.query` 属于同一 Execution area。

## 3. Stable inbound corridors

Controller 不得自行选择内部角色。HTTP inbound 固定进入以下 Application Facade：

```text
RealtimeJobController
   ├── definition.RealtimeJobDefinitionService
   ├── execution.RealtimeJobExecutionService
   ├── execution.query.RealtimeJobQueryService
   └── observability.RealtimeObservabilityService

ComputeEnvironmentController
   └── environment.ComputeEnvironmentService
```

Controller 可以使用 `domain` 类型完成 transport mapping，但禁止直接依赖 Repository、DAO、Engine、Reconcile 内部角色。

## 4. Definition -> Execution corridor

Definition 正常职责不应依赖 Execution 内部实现。

唯一允许的跨边界入口是：

```text
definition
   -> execution.RealtimeJobExecutionService
```

当前用途是删除任务前执行 `assertSafeToDelete`。Definition 不得直接 import：

- `RealtimeExecutionCoordinator`
- `RealtimeExecutionStateManager`
- `RealtimeExecutionReservationManager`
- `RealtimeExecutionReplacementManager`
- `reconcile.*`
- `engine` 的运行状态写逻辑

这样 Definition 不会成为第二个 Execution owner。

## 5. Execution -> Reconcile corridor

Execution Application Facade 需要提供手工 reconcile 与删除安全能力，但不能重新吸收 Reconcile 实现。

允许：

```text
execution
   ├── reconcile.RealtimeReconcileCoordinator
   └── reconcile.RealtimeDeleteSafetyChecker
```

不允许 Execution 直接依赖：

```text
RealtimeRuntimeIdentityRecovery
RealtimeRuntimeStateReconciler
RealtimeReconciler (@Scheduled)
```

Runtime identity recovery 和状态收敛仍由 Reconcile 子系统拥有。

## 6. Runtime Environment corridor

Compute Environment 是邻接上下文。跨子系统解析运行环境时只允许使用：

```text
environment.RealtimeRuntimeResolver
```

允许调用方：

```text
definition
execution
reconcile
observability
```

规则：

- 新 Draft / Publish / Start 可以把当前 enabled Environment 解析为 Snapshot；
- 已存在 SyncExecution 必须优先使用自身持久化的 `RuntimeEnvironmentSnapshot`；
- row 未 hydrate 时只能从 execution/deployment 的持久化 snapshot 恢复；
- 不允许历史 Execution 回读当前 Compute Environment 作为 fallback；
- 其他子系统不得直接调用 `ComputeEnvironmentManager` 修改 Environment 生命周期。

Controller 管理 Environment 时只通过 `ComputeEnvironmentService`。

## 7. Engine -> Repository safety corridor

Engine 通常只依赖 `config` 和 `domain`。存在一个刻意保留的窄 persistence corridor：

```text
engine.RecoverableRealtimeEngineGateway
   -> repository.RealtimeRuntimeIdentityStore
```

原因不是便利，而是安全不变量：**deterministic runtime identity 必须在 Flink CDC CLI 开始提交前持久化。**

因此允许 Engine import `RealtimeRuntimeIdentityStore`，但不允许把这条例外扩展成：

```text
engine -> RealtimeJobStore
engine -> DAO
engine -> DefinitionVersionRepository
```

Engine 不能成为业务状态持久化层。

## 8. Persistence boundary

目标方向：

```text
Application / Internal roles
        ↓
Repository contracts
        ↓
Repository adapters
        ↓
DAO
```

规则：

- Repository contract 不暴露 MyBatis PO / Mapper / Controller DTO；
- Repository implementation 可以使用 DAO / domain / module config；
- DAO 只处理 persistence primitives，不调用 Application、Engine、Repository；
- Core Domain 不依赖 Repository / DAO / Engine / Controller / Spring；
- persistence compatibility mapping 属于 `repository.support`，不进入 Core Domain，也不反向依赖 Definition。

当前 legacy `CdcPipelineSpec` 与 Core `SyncDefinition` 的兼容映射位于：

```text
repository.support.CdcPipelineSpecCompatibilityMapper
```

它服务于 immutable DefinitionVersion persistence compatibility，不是第二套 editable Definition truth。

## 9. Query / Observability read-side rule

Query / Observability 可以组合：

```text
Repository projection + RuntimeEnvironmentSnapshot + Flink read evidence
```

但不得依赖：

```text
SyncExecutionStateMachine
Execution command roles
Reconcile command roles
DAO / Mapper PO / JdbcTemplate
```

read side 的失败可以作为读取失败返回，不能反向写 `UNKNOWN / FAILED / STOPPED`。

## 10. `@Service` reservation

`@Service` 只允许稳定 Application Facade：

```text
definition/RealtimeJobDefinitionService.java
execution/RealtimeJobExecutionService.java
execution/query/RealtimeJobQueryService.java
observability/RealtimeObservabilityService.java
environment/ComputeEnvironmentService.java
```

内部角色使用 `@Component` 或普通对象。新增第六个 `@Service` 必须先证明存在新的稳定 Application use-case，而不是因为类“有业务逻辑”。

## 11. Forbidden buckets

production Realtime Sync 不允许重新出现以下 top-level 业务桶：

```text
service/
common/
helper/
utils/
```

真正通用的技术能力应该有明确边界，例如 `engine`、`repository.support`、`controller.mapper`；不要用模糊目录逃避角色命名。

## 12. Change protocol

任何改变本文件依赖矩阵或 corridor 的 PR 必须同时：

1. 说明为什么现有边界无法表达该需求；
2. 更新 `ARCHITECTURE.md`（如果架构语义发生变化）；
3. 更新 `DEPENDENCIES.md`；
4. 更新 `RealtimeSyncDependencyBoundaryTest`；
5. 证明依赖图仍然无环；
6. 确认没有建立第二个 runtime/config truth owner。

只为了绕过 architecture test 而扩大白名单，不是可接受的修复。
