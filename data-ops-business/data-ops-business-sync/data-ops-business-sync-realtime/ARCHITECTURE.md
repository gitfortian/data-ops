# Realtime Sync Architecture

本文件定义 Realtime Sync 的**长期架构 contract**。它描述稳定边界、角色、运行真相与依赖方向，不记录 Stage / Wave 过程；历史演进以 Git / PR 为准。

需求语义看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，包依赖看 `DEPENDENCIES.md`，代码风格看 `CODE_STYLE.md`，Review 标准看 `REVIEW.md`。

## 设计原则

1. **业务子系统优先。** package 本身表达架构。
2. **稳定入口，隐藏内部角色。** Controller 只进入 Application Facade；跨子系统只走声明过的 corridor。
3. **名字表达角色。** Service / Coordinator / Manager / Resolver / Query / Reader / Reconciler / Gateway / Repository 不互相冒充。
4. **Truth 只有一个 owner。** Task、DefinitionVersion、SyncExecution、RuntimeEnvironmentSnapshot、Flink evidence 各自边界清晰。
5. **外部系统停在边界。** Flink / CLI / SSH / HTTP / MyBatis 等实现细节不进入 Core Domain。
6. **Query 与 Command 分离。** Observability / Query 不修改 Execution state。
7. **Environment 是邻接上下文。** Execution 冻结运行环境快照，不随后续 Environment 修改漂移。
8. **结构重构不偷改行为。** package move、class split 与 REST / DB / Domain semantic change 分开。
9. **架构规则必须可执行。** 文档 contract 由 architecture tests 与 dependency scan 守住。

## Package Map

```text
io.yak.ops.business.sync.realtime
├── controller          # HTTP inbound + transport mapper
├── definition          # Draft / Publish / immutable DefinitionVersion
├── execution           # Start / Stop / Restart / Apply lifecycle
│   └── query           # task/execution read model
├── reconcile           # runtime identity recovery + external state convergence
├── observability       # event/log/checkpoint/metrics read side
├── environment         # Compute Environment + RuntimeEnvironmentSnapshot resolution
├── engine              # Flink / Flink CDC / SSH outbound boundary
├── repository          # persistence contracts + adapters
│   └── support         # persistence-only compatibility conversion
├── dao                 # MyBatis persistence primitives
├── domain              # framework-free core domain / value objects
└── config              # module configuration
```

production 不允许重新创建 `service / common / helper / utils` 业务大桶。完整 top-level dependency matrix 和 corridor 见 `DEPENDENCIES.md`。

## Core Domain Model

```text
RealtimeSyncTask
      │ publish
      ▼
DefinitionVersion (immutable)
      │ start / restart / apply
      ▼
SyncExecution
```

核心关系：

```text
Task != DefinitionVersion != SyncExecution
```

`SyncDefinition` 是唯一逻辑配置事实；Wizard、Yak YAML、HTTP DTO、DB compatibility JSON、Flink YAML 都只是边界表示。

## Truth Ownership

```text
RealtimeSyncTask            = long-lived identity + current draft/published references
DefinitionVersion           = immutable published definition truth
SyncExecution               = desired/observed lifecycle + runtime execution truth
RuntimeEnvironmentSnapshot  = execution-time environment truth
Runtime Identity            = external job recovery identity
Task last/latest-*          = projection / compatibility only
Flink Job                   = external runtime evidence
```

Task compatibility 字段和 Deployment compatibility storage 不能重新成为运行真相。

## Stable Application Entries

仅以下类型是稳定 `@Service`：

```text
RealtimeJobDefinitionService
RealtimeJobExecutionService
RealtimeJobQueryService
RealtimeObservabilityService
ComputeEnvironmentService
```

入口关系：

```text
RealtimeJobController
   ├── RealtimeJobDefinitionService
   ├── RealtimeJobExecutionService
   ├── RealtimeJobQueryService
   └── RealtimeObservabilityService

ComputeEnvironmentController
   └── ComputeEnvironmentService
```

内部专业角色使用 `@Component` 或普通对象。

## Definition Subsystem

```text
RealtimeJobDefinitionService
        ├── RealtimeDefinitionManager
        ├── RealtimeDefinitionPublisher
        ├── RealtimeDefinitionValidator
        ├── RealtimeSourceConfigDigestCalculator
        └── RealtimeYamlCodec
```

职责：

- Manager：Task shell、Draft save、metadata delete；
- Publisher：完整 preflight、runtime validate、Draft re-check、immutable DefinitionVersion publish；
- Validator：未保存 Definition preflight；
- DigestCalculator：source-config compatibility digest；
- YamlCodec：Yak YAML 与同一 logical definition 的转换。

必须保持：Published Version 不可变；运行中的 Execution 不读取 current Draft；Publish 校验后重新检查 Draft revision/digest/environment binding；`DefinitionDigest != sourceConfigDigest != artifactDigest`。

Definition 删除任务前只通过 `RealtimeJobExecutionService.assertSafeToDelete` 进入 Execution/Reconcile 安全边界，不直接依赖 Reconcile 内部实现。

## Execution Core

```text
RealtimeJobExecutionService
        ├── RealtimeExecutionCoordinator
        │       ├── RealtimeExecutionStarter
        │       ├── RealtimeExecutionStateManager
        │       └── RealtimeExecutionReplacementManager
        ├── RealtimeExecutionPreparation
        ├── RealtimeReconcileCoordinator
        └── RealtimeDeleteSafetyChecker

RealtimeExecutionStarter
        ├── RealtimeExecutionPreparation
        ├── RealtimeExecutionReservationManager
        └── RealtimeExecutionStateManager
```

固定安全 contract：

- 同一 Task 最多一个 Active / Uncertain Execution；
- Start 先 DB reservation，再 external submit；
- same-key race 可恢复；
- prepared DefinitionVersion 提交前 re-check；
- stop-during-start 必须绑定并取消返回的精确 JobId；
- stop 结果不确定进入 UNKNOWN；
- RestartExecution 固定原 DefinitionVersion；
- ApplyPublishedVersion 固定 command-time Published Version；
- replacement intent 持久化 command type / target / Idempotency-Key；
- UNKNOWN / CONFLICT 先 reconcile，不猜失败、不创建第二实例。

## Reconcile Subsystem

```text
RealtimeReconcileCoordinator
        ├── RealtimeRuntimeIdentityRecovery
        └── RealtimeRuntimeStateReconciler

RealtimeDeleteSafetyChecker
RealtimeReconciler (@Scheduled + lease)
```

必须保持：

- deterministic runtime identity 唯一匹配才绑定 JobId；
- 多匹配不猜：运行意图保持 CONFLICT/UNKNOWN；
- orphan recovery grace window 内不提前终结；
- RuntimeStatus.UNKNOWN 只能收敛 UNKNOWN；
- expected RUNNING 且外部任务确认终止/不存在才收敛 FAILED；
- desired STOPPED 但外部仍 RUNNING 时停止精确 Job；
- 连续 Engine failure 达 threshold 才标 UNKNOWN，成功后清零；
- 删除前外部 RUNNING / UNKNOWN 都拒绝；
- 后台 reconcile 先取得 multi-instance lease。

## Query / Observability

```text
RealtimeJobQueryService
        └── RealtimeJobReadModelQuery

RealtimeObservabilityService
        ├── RealtimeObservabilityReader
        ├── RealtimeEventQuery
        └── RealtimeEventStream
```

read side 只组合 Repository projection、RuntimeEnvironmentSnapshot 和 Flink read evidence；不得依赖 Execution state machine、Execution command roles 或 Reconcile command roles，也不得因读取失败反向写 UNKNOWN / FAILED / STOPPED。

## Environment / Runtime Boundary

```text
ComputeEnvironmentService
        ├── ComputeEnvironmentManager
        │       └── ComputeEnvironmentConfigNormalizer
        └── ComputeEnvironmentDiagnoser

Definition / Execution / Reconcile / Observability
        └── RealtimeRuntimeResolver
```

固定 snapshot contract：

```text
new work
  -> current RuntimeEnvironmentRef
  -> current enabled ComputeEnvironment
  -> RuntimeEnvironmentSnapshot

existing SyncExecution
  -> persisted RuntimeEnvironmentSnapshot
  -> never fallback to current Environment
```

Environment 后续修改、停用、默认值切换不能改变已有 Execution 的运行上下文。

## Engine Boundary

```text
RuntimeEnvironmentSnapshot
        ↓
RealtimeEngineGateway / Flink clients
        ↓
Flink REST / Flink CDC CLI / LOCAL / SSH adapter
```

Engine 通常只依赖 config/domain。存在一个刻意保留的安全 corridor：

```text
RecoverableRealtimeEngineGateway
   -> RealtimeRuntimeIdentityStore
```

原因是 runtime identity 必须在 CLI 可能启动前持久化。该例外不能扩展成 Engine -> RealtimeJobStore / DAO。

## Persistence Boundary

```text
Application / internal roles
        ↓
Repository contracts
        ↓
Repository adapters
        ↓
DAO
```

规则：

- Repository contract 不暴露 DAO model / Mapper / Controller DTO；
- DAO 不依赖 Application / Engine / Repository；
- Core Domain 不依赖 Repository / DAO / Engine / Spring；
- Repository 不反向依赖 Definition / Execution / Reconcile；
- immutable DefinitionVersion 的 legacy `CdcPipelineSpec` compatibility mapping 位于 `repository.support.CdcPipelineSpecCompatibilityMapper`；
- compatibility mapper 不是 Core Domain，也不是第二套 editable Definition truth。

## Dependency Governance

`DEPENDENCIES.md` 是 package dependency contract，`RealtimeSyncDependencyBoundaryTest` 直接扫描 production Java import 并保护：

```text
top-level dependency matrix
acyclic graph
Definition -> Execution corridor
Execution -> Reconcile corridor
RuntimeResolver corridor
Engine -> RuntimeIdentityStore corridor
Controller -> stable facade corridor
@Service allowlist
no service/common/helper/utils buckets
persistence compatibility mapper location
```

`RealtimeArchitectureTest` 继续保护角色、Spring stereotype、read-side、Core Domain purity、Repository contract 等结构语义。

修改依赖白名单前必须先证明真实架构需求，不能为了让测试通过直接扩大 corridor。

## Change Rules

1. 一个 PR 一个主要边界或行为关注点；
2. behavior change 与 package move 尽量分开；
3. 新入口稳定后不保留 production 双入口；
4. 不借重构改 REST / DB / Flyway / Domain semantics；
5. 不提前抽 realtime/offline Shared Sync Kernel；
6. 新 dependency 必须同时符合 `ARCHITECTURE.md + DEPENDENCIES.md`；
7. 代码风格与角色命名遵守 `CODE_STYLE.md`；
8. behavior tests 与 architecture tests 都是长期 contract，不因“迁移完成”删除。
