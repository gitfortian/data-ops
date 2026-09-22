# yak-ops-business-job

`yak-ops-business-job` 是 Yak Ops 的通用任务运行中枢：负责 **Task Discovery、不可变 Snapshot、Execution Routing 和 Runtime Context**。

它不拥有业务任务定义，也不负责 Cron / Schedule 生命周期。

```text
Business TaskProvider
        ↓
TaskRegistry
        ↓
TaskVersionSnapshot
        ↓
TaskExecutionGateway
        ↓
TaskExecutor
```

核心约束：

- `TaskDefinition != TaskVersionSnapshot != TaskExecution`；
- 业务域通过 `TaskProvider / TaskExecutor` 接入，Job Core 不直接依赖具体业务模块；
- SQL / Python / Java / Shell 复用统一 Plugin Runtime，只贡献各自 Capability；
- Offline Sync 自己提供 `SYNC` Provider / Executor，执行事实仍归 Offline Sync；
- `TaskExecutionGateway` 只路由，不实现具体引擎；
- Runtime 只通过 `TaskEnvironmentResolver` 读取环境能力；
- 调度生命周期归各业务域，不归 Job。

代码结构：

```text
task         稳定跨模块契约 + Execution Gateway
discovery    Provider 聚合
runtime      Plugin 执行生命周期 / Context
adapter      Task Type 能力适配
environment  环境变量管理与 Runtime Resolver
controller   HTTP 入口
dao          持久化
config       配置
```

架构文档：

- [REQUIREMENTS.md](REQUIREMENTS.md)
- [DOMAIN.md](DOMAIN.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [DEPENDENCIES.md](DEPENDENCIES.md)
- [REVIEW.md](REVIEW.md)

仓库级写法统一遵循 `../../CODE_STYLE.md`。
