# Job Architecture

本文件定义 `yak-ops-business-job` 的长期代码边界。

## Package Map

```text
io.yak.ops.business.job
├── controller       # HTTP inbound
├── task             # stable cross-module contracts + TaskExecutionGateway
├── discovery        # TaskProvider aggregation
├── runtime          # shared Plugin execution lifecycle/context
├── adapter
│   └── plugin       # SQL / Python / Java / Shell capability adapters
├── environment      # env CRUD facade + runtime resolver
├── dao              # persistence primitives
└── config           # module configuration
```

禁止重新创建 `service / common / helper / utils` 作为业务大桶。

## Stable Contract Package

`task` 是跨模块稳定入口，只允许承载：

```text
TaskDefinition
TaskVersionSnapshot
TaskRegistration
TaskExecution
TaskProvider
TaskRegistry
TaskExecutor
TaskExecutionGateway
```

以及暂时保留的 deprecated SYNC compatibility corridor。

`task` 不允许 import Offline Sync、Workflow、Data Development 等具体业务实现。

## Discovery

```text
Business TaskProvider(s)
        ↓
InMemoryTaskRegistry
        ↓
TaskRegistry
```

`discovery` 只做聚合、去重和快照索引，不知道任务来自哪个业务域。

## Execution

```text
Caller
  -> TaskExecutionGateway
  -> TaskExecutor
```

`TaskExecutionGateway` 是稳定 Application Service。它按 Type 路由，不管理 Task Plugin 生命周期。

Plugin 类型走：

```text
adapter.plugin
     ↓
runtime.AbstractTaskExecutorAdapter
     ↓
TaskPlugin
```

`runtime` 拥有幂等、ExecutionHandle、虚拟线程、状态/取消和结果转换；Adapter 只贡献 Capability。

## Environment

`SystemEnvVarService` 是设置页面稳定 Facade；`TaskEnvironmentResolver` 是 Runtime 读取边界。`TaskExecutionContextFactory` 只能依赖 Resolver。

## Business Runtime Extension

Job Core 不依赖 Offline Sync。Offline Sync 自己实现 `TaskProvider / TaskExecutor` 并由应用组合层自动发现。

允许方向：

```text
Offline Sync -> job.task contract
```

禁止方向：

```text
job.* -> business.sync.offline implementation
```

## Spring Roles

生产代码中的 `@Service` 只允许：

```text
task/TaskExecutionGateway.java
environment/SystemEnvVarService.java
```

Registry、Factory、Adapter 等专业角色使用 `@Component` 或 plain object。

## Persistence

Controller 不直接访问 DAO。Runtime / Adapter / Discovery 不访问 Job DAO。只有 `environment` 当前通过 DAO 持久化应用环境变量。

结构规则由 architecture tests 持续执行。
