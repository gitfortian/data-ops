# Realtime Sync Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

按顺序读取：

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
ARCHITECTURE.md  -> 子系统、truth ownership 与角色
DEPENDENCIES.md  -> package graph 与跨子系统 corridor
CODE_STYLE.md    -> 类、方法与 stereotype 的工程规范
REVIEW.md        -> 按什么标准判卷
PR diff / tests  -> 实际改了什么
```

“只是重构”不能成为绕过运行安全、领域规则或 dependency guard 的理由。

## 1. Requirement Alignment

检查代码是否符合 `REQUIREMENTS.md`：是否改变已有业务行为、引入未定义能力或越过模块边界。

出现未定义的新能力或行为变化时报告：

```text
Requirement Gap
```

不要替产品或开发者自行补需求。

## 2. Domain Compliance

重点检查：

- Task / DefinitionVersion / SyncExecution 是否混淆；
- Execution 是否读取 current Draft；
- Published Version 是否可能被原地修改；
- Restart 是否隐式升级，Apply target 是否执行中漂移；
- `UNKNOWN / CONFLICT` 是否被误判为 FAILED 或允许创建第二实例；
- Runtime state 是否重新回到 Task compatibility 字段；
- Current Environment 是否覆盖历史 Execution snapshot；
- 外部协议或 adapter 参数是否泄漏进 Core Domain；
- 是否重新引入第二套 Definition truth。

违反现有规则：`Domain Violation`。现有模型无法表达真实需求：`Domain Gap`。

## 3. Architecture Alignment

production Realtime Sync 已完成子系统收敛，`service/` 大桶不再是迁移期白名单。

重点检查：

- 新类是否明确归属 Definition / Execution / Reconcile / Query / Observability / Environment / Engine / Persistence；
- 是否重新创建 `service / common / helper / utils / base` 业务桶；
- Controller 是否只依赖稳定 Application Facade；
- `@Service` 是否仅用于 5 个稳定 Facade；
- Query / Observability 是否保持纯 read side；
- Reconcile 是否仍依据 runtime identity 与 runtime evidence 收敛，而不是猜外部 Job；
- Environment lifecycle 与 Runtime snapshot resolution 是否保持分离；
- Repository / DAO / Engine 是否反向依赖 Application；
- Core Domain 是否保持 framework / persistence / engine free；
- 是否为了减少重复提前抽 realtime/offline Shared Sync Kernel。

明确破坏已声明架构：`Architecture Violation`。真实需求无法由当前架构表达：`Architecture Gap`。

## 4. Dependency Alignment

任何新增 realtime 内部 import 都检查 `DEPENDENCIES.md` 与 `RealtimeSyncDependencyBoundaryTest`。

尤其关注窄 corridor：

```text
definition -> execution.RealtimeJobExecutionService
execution  -> reconcile.RealtimeReconcileCoordinator / RealtimeDeleteSafetyChecker
definition/execution/reconcile/observability -> environment.RealtimeRuntimeResolver
engine -> repository.RealtimeRuntimeIdentityStore
controller -> declared stable facades
```

不接受以下修复方式：

- 为了让测试通过直接扩大 dependency whitelist；
- 引入反向依赖后声称“现在只有一个调用点”；
- 用 reflection / service locator 绕过 import guard；
- 把兼容 mapper 塞回 Core Domain 规避循环。

Dependency graph 必须保持无环。

## 5. Code Style / Role Alignment

按 `CODE_STYLE.md` 检查：

- Service / Coordinator / Manager / Resolver / Query / Reader / Reconciler / Gateway / Repository 是否名副其实；
- 一个类是否承担多个 truth owner；
- 高风险流程是否把关键顺序写清楚；
- transaction 是否只覆盖需要线性化的工作；
- 是否出现泛化 `execute/handle/process` 吞掉关键状态语义；
- 注释是否解释 invariant / why，而非复述代码。

纯格式和个人偏好不要当成阻塞问题。

## 6. Correctness

检查真实错误：

- 状态迁移；
- 空值和边界值；
- 事务边界；
- 并发 / CAS / 锁；
- 幂等；
- 外部调用超时和部分失败；
- Start / Stop / Reconcile / Restart / Apply 竞态；
- Snapshot / Version / JobId 是否错配；
- Definition / Execution target 是否可能在命令中途漂移。

## 7. Compatibility

检查是否破坏：

- REST API；
- DB / Flyway；
- Yak YAML；
- 历史数据；
- 前端调用；
- 已存在 DefinitionVersion / SyncExecution。

破坏性变化必须有明确迁移方案，禁止借架构重构做 Big-Bang contract change。

## 8. Safety

重点检查：

- 重复启动 / 双实例；
- stop-during-start；
- `UNKNOWN / CONFLICT`；
- runtime identity 恢复；
- RuntimeEnvironmentSnapshot；
- replacement-stop reservation；
- prepared version re-check；
- 敏感配置是否扩大持久化或输出范围；
- 提交临时 artifact 是否按既有边界清理。

## 9. Engine / Persistence Boundary

Engine 通常不持久化业务状态。唯一显式例外是：

```text
RecoverableRealtimeEngineGateway
  -> RealtimeRuntimeIdentityStore
```

它用于保证 runtime identity 在外部提交开始前已持久化。不要把这条安全 corridor 扩成 Engine -> RealtimeJobStore / DAO。

Persistence 侧检查：

- Repository contract 不暴露 DAO model / Mapper / Controller DTO；
- DAO 不调用 Application / Engine / Repository；
- persistence compatibility mapping 留在 `repository.support`；
- Repository 不反向依赖 Definition / Execution / Reconcile。

## 10. Tests / Guardrails

每个 P0 / P1 问题都回答：现有哪个测试应该挡住？没有就指出 Missing Test。

长期 guard：

```text
RealtimeArchitectureTest
  -> role / stereotype / core-domain / read-side guards

RealtimeSyncDependencyBoundaryTest
  -> top-level dependency matrix
  -> no-cycle
  -> cross-subsystem corridors
  -> @Service allowlist
  -> forbidden broad buckets
  -> persistence compatibility location
```

行为安全测试仍负责 Start/Stop/Restart/Apply/Reconcile/Environment Snapshot 等 runtime contract。

## Refactor PR Rules

```text
一个 PR 一个主要边界
package move / class split / behavior change 尽量分开
不顺手改 REST / DB / Flyway / Domain semantics
不长期保留 production 新旧双入口
行为测试与 architecture tests 都必须保留
```

涉及结构调整的 PR 建议包含：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Domain Gap: yes/no

Architecture Impact Analysis
- Target subsystem:
- Stable entry / gateway:
- Runtime truth owner:
- Dependency direction changed: yes/no
```

如果修改 package dependency，再增加：

```text
Dependency Impact Analysis
- New edge:
- Existing corridor or new corridor:
- Cycle impact:
- DEPENDENCIES.md updated: yes/no
- RealtimeSyncDependencyBoundaryTest updated: yes/no
```

## 严重级别

```text
P0 Blocker
- 数据丢失 / 不可恢复破坏
- 重复运行导致严重数据风险
- 敏感信息泄漏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS / DOMAIN
- 明确并发、幂等、事务、兼容性缺陷
- 高概率运行故障
- 打破稳定架构/依赖 corridor 或引入 cycle

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 非阻塞工程建议
```

## 每个问题必须有证据

有效 Review 问题至少包含：

```text
位置：文件 / 行或方法
级别：P0 / P1 / P2
依据：Requirement / Domain / Architecture / Dependencies / correctness fact
场景：什么输入、依赖关系或并发顺序会触发
风险：会造成什么结果
建议：修复方向
测试：应补或应命中的测试
```

没有可说明的触发场景和风险，就不要凑问题。

## 固定输出格式

```text
# Review Result

Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 问题列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Architecture Gap
无 / 说明

## Dependency Gap
无 / 说明

## Missing Tests
无 / 说明
```

有 P0/P1 -> `CHANGES_REQUIRED`；只有 P2 可以 `PASS`。没发现真实问题就直接 `PASS`，不要为了显得有价值硬凑问题。
