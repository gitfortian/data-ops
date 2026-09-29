# Job Review

Review Job 改动时重点看边界，不以“有没有 Service”作为目标。

## Domain

确认：

- `TaskDefinition / TaskVersionSnapshot / TaskExecution` 没有重新混成一个对象；
- 有版本任务仍执行固定 Snapshot；
- Registry 没有成为业务 Task Source Truth；
- Job 没有获得具体业务执行事实或调度所有权。

## Roles

确认：

- `TaskExecutionGateway` 只路由；
- Registry 只聚合 Provider；
- Plugin Adapter 只贡献 Capability；
- 公共执行生命周期只在 `runtime` 实现一套；
- Runtime 通过 `TaskEnvironmentResolver` 获取环境。

出现新的 `*Service` 前先判断它是否真的是稳定 Application Facade。Parser / Adapter / Registry / Factory / Resolver 不应为了注入方便标成 Service。

## Dependencies

重点拒绝：

```text
job -> concrete business module
controller -> dao
runtime -> dao
adapter -> dao
new service/common/helper/utils bucket
```

业务域扩展 Job 应实现 `job.task` contract，而不是让 Job import 业务 Service。

## Compatibility

若涉及现有调用链，检查：

- REST contract；
- Task Type normalization；
- Snapshot semantics；
- idempotency key；
- status / cancel / result mapping；
- Plugin validation error semantics。

Deprecated SYNC corridor 只能缩小，不能新增消费者。

## Review Output

推荐在 PR 中简短说明：

```text
Domain Impact:
Architecture Impact:
Behavior Compatibility:
Tests:
Known Debt:
```

`REQUIREMENTS.md / DOMAIN.md / ARCHITECTURE.md / DEPENDENCIES.md` 与 architecture tests 是判定基准。
