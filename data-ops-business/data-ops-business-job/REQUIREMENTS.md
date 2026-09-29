# Job Requirements

本文件只回答“Job 模块需要提供什么能力”。领域硬规则看 `DOMAIN.md`，代码边界看 `ARCHITECTURE.md`。

## 模块定位

Job 是业务无关的 Task Runtime Hub。业务域拥有任务定义和业务执行事实，Job 提供统一发现、快照和执行路由。

## 必须提供

### Task Discovery

业务域通过 `TaskProvider` 提供可执行任务。Registry 聚合 Provider，拒绝重复 Task ID，但不读取具体业务表或业务 Service。

`TaskRegistration` 必须同时交付：

```text
TaskDefinition        # 发现描述
TaskVersionSnapshot   # 可执行快照
```

二者的 Task ID / Type 必须一致。

### Immutable Snapshot

有版本能力的任务运行时必须使用调用方固定的 `TaskVersionSnapshot`。不得在执行时回读当前 Draft / Latest Version。

没有版本能力的兼容任务可使用 `version=0`，但这不能伪装成 Published Revision。

### Execution Routing

`TaskExecutionGateway` 按 Task Type 路由到唯一 `TaskExecutor`，统一暴露：

```text
start / status / cancel
```

Gateway 不拥有具体执行引擎。

### Plugin Runtime

SQL / Python / Java / Shell 等 TaskPlugin 类型共享一套本地执行生命周期：

```text
snapshot validation
-> plugin validation
-> context/capability assembly
-> idempotency
-> async execution
-> status/cancel/result conversion
```

具体 Adapter 只贡献自己的 Capability。

### Business-owned Runtime

SYNC 等拥有独立业务运行时的类型，由对应业务模块实现 Job 的 `TaskProvider / TaskExecutor` 契约。Job 不反向依赖业务实现。

### Runtime Environment

任务运行上下文只依赖 `TaskEnvironmentResolver` 获取全局环境变量。环境变量 CRUD 不是 Runtime 的依赖面。

## 非职责

Job 不负责：

- Cron / Schedule 注册和生命周期；
- Offline Sync / Data Development / Workflow 的业务定义；
- 业务执行记录的最终事实；
- Task Plugin 内部业务规则。

## 兼容要求

重构不得偷偷改变现有 `/api/v1/tasks`、`/api/v1/system/env-vars`、Task Gateway、Snapshot、幂等、状态和取消语义。
