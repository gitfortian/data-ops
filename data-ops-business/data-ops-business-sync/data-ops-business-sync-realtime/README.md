# Realtime Sync

Realtime Sync 是 Yak Ops 的持续数据同步控制面，负责定义同步任务、发布不可变版本、控制运行实例，并查看运行状态和基础可观测信息。

## Read First

本目录只维护**当前有效 contract**，历史设计与迁移过程以 Git / PR 为准。

建议按顺序阅读：

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块需要什么 |
| [`DOMAIN.md`](./DOMAIN.md) | 哪些领域规则不能违反 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 子系统、truth ownership 与角色如何协作 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 可以依赖谁、跨子系统走哪条 corridor |
| [`CODE_STYLE.md`](../../../CODE_STYLE.md) | Yak Ops 仓库统一工程与代码规范 |
| [`REVIEW.md`](./REVIEW.md) | PR 按什么标准判卷 |

当前 production 已完成 Definition / Execution / Reconcile / Query / Observability / Environment 子系统收敛，旧 `service/` 业务大桶已退休。`RealtimeArchitectureTest` 保护角色边界，`RealtimeSyncDependencyBoundaryTest` 扫描真实源码 import，保护无环依赖图、窄 corridor 与稳定 Application Facade。

结构调整不得借机改变现有 REST / DB / Domain runtime behavior；真正的领域或接口变化必须单独评审。

## Core Model

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

`SyncDefinition` 是唯一配置事实；Wizard、Yak YAML、HTTP DTO、DB JSON、Flink YAML 都只是 Adapter / Projection。

## Runtime Truth

```text
RealtimeSyncTask            = long-lived task identity + draft/published references
DefinitionVersion           = immutable published definition truth
SyncExecution               = execution lifecycle truth
RuntimeEnvironmentSnapshot  = execution-time environment truth
Runtime Identity            = external recovery identity
Task latest/last-*          = projection / compatibility only
Flink Job                   = external runtime evidence
```

运行状态不能重新回到 Task compatibility 字段；`UNKNOWN / CONFLICT` 不能当成 FAILED 猜测处理，必须先 Reconcile。

## Compute Environment

实时同步控制面以 **Compute Environment** 作为唯一运行时配置来源。任务在创建时必须绑定一个已启用的运行环境；每次部署都会保存不可变的运行环境快照，后续修改默认环境或环境配置不会把历史部署重定向到其他 Flink 集群。

在 **设置 → 计算引擎** 中维护 Flink CDC 运行环境。当前支持两种提交方式：

- `LOCAL`：Yak Ops 与 Flink CDC CLI 位于同一执行环境，通过本地 `ProcessBuilder` 调用 `flink-cdc.sh`。
- `SSH`：Yak Ops 可以运行在 Windows 或其他机器，通过本机 OpenSSH 客户端连接 Linux 执行节点，在远端调用 `flink-cdc.sh`。

运行环境保存：

- Flink REST URL；
- Flink Home / Flink CDC Home / Java Home；
- Flink / Flink CDC 版本；
- `LOCAL` 或 `SSH` 提交方式；
- SSH 提交节点配置（SSH 模式）。

应用级 `yak.sync.realtime` 配置只负责控制面自身行为，例如工作目录、HTTP/提交超时、日志上限和 reconcile 参数。任务生命周期不再通过 application.yml 猜测运行环境。

## Baseline

实时同步当前只维护一个 Flyway 基线：

```text
db/migration/yak-realtime-sync/V1__create_realtime_sync.sql
```

这个基线描述当前最终表结构，不承担旧开发版本的原地升级。已经执行过旧 V1-V8 的开发数据库需要删除实时同步相关表以及 `yak_realtime_schema_history` 后重新初始化；不要对已有旧 history 直接执行新的 V1。

新基线不再包含：

- 历史 `ALTER TABLE` / 数据回填；
- `LEGACY` runtime identity 状态；
- 未绑定运行环境的实时任务；
- 长期持久化的 Pipeline YAML。

Pipeline YAML 只在发布/启动时根据任务 Spec 编译，并在提交边界短暂存在。数据库保存结构化 Spec、摘要、部署环境快照和运行标识，不保存带敏感连接信息的提交 YAML。

## SSH Mode

推荐使用 OpenSSH key 或 ssh-agent。SSH 用户不需要 root 权限，但至少需要：

- 能执行 `${flink-cdc-home}/bin/flink-cdc.sh`；
- 能读取 Flink CDC connector 和 Flink 安装目录；
- 能在远端 `${TMPDIR:-/tmp}` 创建临时文件；
- 能从 Linux 执行节点访问运行环境中配置的 Flink REST 地址（或 `remoteRestAddress/remoteRestPort`）。

Yak Ops 使用 `BatchMode=yes`，因此需要交互输入的 SSH 登录流程不属于当前支持范围。

典型链路：

```text
Windows / Yak Ops
  ├─ HTTP ───────────────> Flink REST
  └─ OpenSSH ────────────> Linux execution node
                              └─ flink-cdc.sh
                                     └─ Flink cluster
```

## Pipeline YAML Security Boundary

SSH 模式不会在 Yak Ops 本地长期保存提交期 Pipeline YAML：

```text
Yak Ops memory Pipeline YAML
  -> OpenSSH stdin
  -> Linux mktemp temporary file (umask 077)
  -> flink-cdc.sh
  -> trap cleanup
```

LOCAL 模式只在应用工作目录创建提交期临时 YAML，提交结束后立即删除。两种模式都只长期保留脱敏后的提交 stdout/stderr。

敏感连接配置只允许在提交边界短暂解析和使用；不得长期进入 SyncDefinition、DefinitionVersion、RuntimeEnvironmentSnapshot 或日志。

## Runtime Identity And Recovery

每次部署在进入 Flink CDC CLI 之前都会持久化确定性的 runtime job name：

```text
REQUIRED -> BOUND -> CLI submit
```

如果提交结果不确定或 Yak Ops 在 JobId 落库前退出，Reconcile 使用该 runtime identity 精确查找 Flink Job。新基线不存在旧部署的 `LEGACY` 分支，也不会按用户可见任务名猜测 JobId。

多实例 reconcile 通过 `yak_realtime_runtime_lease` 抢占租约，确保同一时刻只有一个实例执行全局对账。

## Observability

当前页面统一使用：

- `GET /api/v1/realtime-sync/{id}/observability`：运行概览、Checkpoint、Metrics；
- `GET /api/v1/realtime-sync/{id}/logs/submission`：Flink CDC CLI 提交日志；
- `GET /api/v1/realtime-sync/{id}/logs/runtime`：Flink Exception History。

旧的 `/logs`、`/checkpoints`、`/metrics` 兼容接口不再作为当前 API 使用。

Observability 属于 read side：可以组合本地持久化与 Flink REST 事实，但不能拥有 Start / Stop / Restart / Apply / Reconcile 的状态迁移。

## Out Of Scope

当前模块不负责：

- 启动、扩缩容或管理 Flink Cluster 生命周期；
- SSH 隧道代理 Flink REST；
- Runtime Agent / 常驻远端进程；
- 交互式远端凭据托管；
- JobManager/TaskManager 日志文件通过 SSH 下载；
- 通用工作流编排；
- 通用 ETL / 任意复杂转换引擎；
- 数据血缘计算；
- Connector 工程的构建和部署管理。

如果 Yak Ops 无法直接访问 Flink REST，应通过内网网络、反向代理或安全网关解决 REST 可达性。

## Engineering Rule

新增或移动代码前至少回答：

1. 属于 Definition、Execution、Reconcile、Observability、Environment、Engine 还是 Persistence？
2. 是什么 role，是否符合根目录 `CODE_STYLE.md`？
3. 谁拥有它读写的 runtime truth？
4. 新 import 是否符合 `DEPENDENCIES.md`，跨子系统是否走声明过的 corridor？
5. 哪个 behavior test 与 architecture test 证明改动没有破坏现有 contract？

答不清楚时，不要创建新的 `service / common / helper / utils` 业务大桶，也不要通过扩大 architecture-test 白名单绕过边界设计。