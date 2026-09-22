# Yak Ops Data Quality

Data Quality 是 Yak Ops 的数据质量控制面，负责数据表资产注册、质量监控定义、规则/模板管理、手动与调度执行、告警证据和质量工作台查询。

当前核心执行关系：

```text
Project-scoped Table Asset
    -> Project-scoped Monitor + Rules + Settings
    -> enqueue immutable QualityExecutionPlan(projectId)
    -> Project-scoped Execution + RuleExecution evidence
```

## Read First

本目录只维护**当前有效 contract**。历史 Stage / Wave / 重构过程通过 Git / PR 追溯。

建议按顺序阅读：

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块必须提供哪些行为 |
| [`DOMAIN.md`](./DOMAIN.md) | 哪些业务事实和生命周期不能违反 |
| [`PROJECT_SPACE.md`](./PROJECT_SPACE.md) | 哪些事实属于 Project、后台如何恢复上下文 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 代码放哪里、各角色如何协作 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 可以依赖谁、跨边界走哪里 |
| [`../../CODE_STYLE.md`](../../CODE_STYLE.md) | Yak Ops 统一工程与角色规范 |
| [`REVIEW.md`](./REVIEW.md) | Quality PR 按什么标准评审 |

## Package Shape

```text
quality/
├── controller       # HTTP inbound + transport mapper
├── asset            # registered quality table lifecycle / candidate read
├── monitor          # monitor/rule/settings lifecycle and policy
├── execution        # execution admission / immutable plan / dispatch / worker
├── alert            # alert evidence recording
├── schedule         # Yak Schedule projection and callback boundary
├── template         # built-in/custom template and folder lifecycle
├── workspace        # workspace/report/log read-side projection
├── gateway          # Quality-owned external capability ports + adapters
├── repository       # business persistence ports + adapters
├── dao              # MyBatis persistence primitives
├── domain           # framework-free business values / execution snapshot
└── config           # module wiring and properties
```

production 不再维护 `quality/service/**`。Quality 当前也不引入一个新的通用 `@Service` facade；Controller 直接进入声明过的 Manager / Reader / Projector 角色。

## Truth Ownership

```text
CurrentProject               = 当前请求可信的工作空间边界
TableAsset                   = 当前 Project 已注册的数据质量物理表事实
Monitor + Rules + Settings   = 当前 Project 的质量监控定义事实
QualityExecutionPlan         = enqueue 时冻结的 Project + 规则执行快照
Execution                    = 当前 Project 的一次质量检查持久化事实
RuleExecution                = 单条规则的执行证据，通过 Execution 继承 Project
Yak Schedule                 = 携带 Project 的时间触发投影，不是 Monitor 配置真相
AlertEvent                   = 告警/通知证据，通过 Monitor/Execution 继承 Project
Rule Template / Folder       = 平台级全局模板能力
```

已入队或运行中的执行不得通过回读当前 Monitor / Rule / Settings 改写自己的执行快照，也不得在异步线程中依赖原 HTTP 上下文。

## Main Boundaries

Project Space：

```text
HTTP @ProjectScope
    -> trusted CurrentProject
    -> project-scoped Repository / DAO

Background payload.projectId
    -> ProjectContextScope
    -> normal Quality application roles
```

Datasource：

```text
Quality business role
    -> QualityDataCatalogGateway
    -> DataSourceQualityCatalogAdapter
    -> DataSourceCatalogReader
```

Persistence：

```text
Manager / Reader / Worker
    -> narrow Repository port
    -> RepositoryAdapter
    -> DAO / PO / MyBatis
```

Execution：

```text
QualityExecutionManager
    -> QualityExecutionPlanFactory
    -> immutable QualityExecutionPlan
    -> QualityExecutionDispatcher (afterCommit + ProjectContextScope)
    -> QualityExecutionWorker
    -> QualityAlertRecorder
```

完整 Project 归属见 `PROJECT_SPACE.md`，依赖矩阵和窄 corridor 见 `DEPENDENCIES.md`。
