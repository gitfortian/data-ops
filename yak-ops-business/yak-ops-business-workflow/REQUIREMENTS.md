# Workflow Requirements

本文件定义 Workflow **当前必须保持的行为 contract**。它描述业务能力和兼容要求，不记录历史迁移过程。

领域不变量看 `DOMAIN.md`，代码职责看 `ARCHITECTURE.md`，依赖方向看 `DEPENDENCIES.md`，统一工程规范看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。

## 1. Definition / Draft

WorkflowDefinition 是长期可编辑配置。

必须保持：

- 支持创建、查询、更新、删除草稿；
- 节点、连线、输入、编辑器元数据、工作流超时和失败策略属于当前定义；
- 发布或测试前必须校验节点 identity、连线引用和 DAG 无环；
- 节点绑定 Task 时必须保留当前 TaskAsset/TaskRevision 语义；
- 显式升级 TaskRevision 只修改当前草稿，不反向修改历史 WorkflowVersion；
- ONLINE 且存在活动 WorkflowExecution 的安全约束不得被删除/编辑流程绕过；
- 删除 Definition 不得顺手删除或改写已经存在的历史 Execution evidence。

## 2. Publish / WorkflowVersion

发布产生不可变 WorkflowVersion。

```text
mutable WorkflowDefinition
        |
        | publish
        v
immutable WorkflowVersion
```

Version 必须固定：

- version id / version number；
- source draft revision；
- executable WorkflowRunSpec；
- editor metadata snapshot；
- 每个节点对应的 TaskVersionSnapshot。

已发布版本不能因为后续草稿修改、TaskAsset current revision 变化或再次发布而改变。

## 3. Online / Offline

ONLINE 表示允许新的正式运行；OFFLINE 阻止新的普通正式运行。

- 首次上线或草稿发生变化时发布新的不可变版本；
- 再次上线未变化草稿不得无意义创建新版本；
- 下线不取消已经启动的 WorkflowExecution；
- 工作流上下线与已配置 Schedule 的启停保持现有联动；
- 历史运维恢复使用来源实例固定版本时，不通过 current draft 改写历史语义。

## 4. Launch Modes

所有会创建新 WorkflowExecution 的入口必须经过明确 Launch corridor。

当前支持：

- 手工/API 正式运行当前 active published version；
- 当前 draft test-run，不改变 active published version；
- ad-hoc DAG 运行；
- Schedule 正常触发，按触发时 active version 执行；
- Backfill，执行批次创建时固定的 WorkflowVersion；
- businessDate 运维补跑，执行来源实例固定的 WorkflowVersion；
- restart / rerun-from-node 创建新的 WorkflowExecution。

Schedule / Backfill 不允许直接调用 Yak Workflow Engine 绕过 `WorkflowLauncher`。

## 5. Runtime Ownership

WorkflowExecution、NodeExecution、NodeAttempt 的运行状态由 Yak Workflow Engine 唯一拥有。

Yak Ops Runtime 负责：

- 将 WorkflowRunSpec 和 TaskVersionSnapshot 转换为 engine definition；
- start / activate / pause / resume / cancel；
- retry / continue / restart / rerun 命令桥接；
- NodeDispatch 到 TaskExecutionGateway；
- external task execution id 绑定；
- task status polling；
- timeout 检查；
- restart recovery；
- 将 engine snapshot 投影为现有 WorkflowInstanceVO。

Yak Ops 不创建第二套独立运行状态机去覆盖 engine 状态。

## 6. Retry / Continue / Restart

必须长期区分：

```text
retry failed node / continue after failure
    = re-activate the same WorkflowExecution

restart / rerun from node
    = create a new WorkflowExecution
```

对带 Trigger Ledger 的终态实例做原地 retry/continue 前，必须先经过 durable reactivation reservation，避免破坏 SERIAL_WAIT / SERIAL_DISCARD 串行槽位语义。

## 7. Task Execution Boundary

Workflow 不实现 Offline Sync、Realtime Sync、SQL 等任务自己的运行生命周期。

节点实际执行统一通过 Business Job 的 `TaskExecutionGateway`：

- start 返回外部 task execution identity；
- status 提供外部执行证据；
- cancel/pause/resume 通过已有 gateway capability 执行；
- Workflow Attempt 保存外部 execution binding 用于恢复；
- task 类型不支持时必须在启动前明确拒绝，不能伪造成功。

## 8. Schedule

`yak_workflow_schedule` 是 Workflow 调度配置事实来源；Yak Schedule 是时间触发投影。

Schedule 当前必须支持：

- Cron + timezone；
- start/end 生效区间；
- `PARALLEL / SERIAL_WAIT / SERIAL_DISCARD` 执行策略；
- `FIRE_ONCE / SKIP` misfire 策略；
- 本次运行 input；
- ONLINE/OFFLINE 生命周期；
- last/next fire time 运行投影。

工作流未发布/未上线时不能启用 Schedule。Schedule 删除或下线必须停止新的时间触发，并处理尚未启动的等待 Trigger。

## 9. Trigger Ledger

`yak_workflow_schedule_trigger` 是一次计划触发的 durable ledger。

Ledger 必须保存并保护：

- trigger identity / dedupe key；
- workflow / schedule / backfill lineage；
- planned fire time / actual fire time / business date；
- execution / misfire strategy snapshot；
- RECEIVED / WAITING / LAUNCHING / REACTIVATING / RUNNING / terminal evidence；
- 绑定的 WorkflowExecution identity。

同一计划触发重复到达时不得创建第二个 WorkflowExecution。

`SERIAL_WAIT` 必须排队；`SERIAL_DISCARD` 在槽位忙时跳过；`PARALLEL` 允许并发。准入、启动绑定、终态完成与队首推进必须继续以 Ledger 的 durable evidence 为准。

## 10. Backfill / businessDate Rerun

WorkflowBackfill 表示一批历史业务日期计划，不等于单个 WorkflowExecution。

- 创建 Backfill 时固定当前 active WorkflowVersion id/no；
- 后续发布新版本不能改变已创建 Backfill；
- Backfill 根据保存的 Cron/timezone 物化 occurrence，并逐个进入 Trigger Ledger；
- 单批当前最多物化 1000 个 occurrence，日期跨度最多 3660 天；
- Backfill cancel 只阻止尚未启动的 Trigger，已经 RUNNING 的 WorkflowExecution 继续完成；
- businessDate rerun 必须使用来源实例的 immutable version 与创建时调度血缘；
- 来源实例的旧系统调度参数不能被当作新 rerun 的 planned time。

## 11. Recovery / Reconcile

应用重启后必须能从 durable evidence 恢复，而不是依赖内存对象仍然存在。

当前恢复职责包括：

- 非终态 WorkflowExecution runtime recovery；
- external task execution binding recovery；
- Schedule projection reconcile；
- misfire recovery；
- Trigger Ledger 中间态恢复与 SERIAL_WAIT 队首推进；
- RUNNING Backfill occurrence 再物化，依赖 dedupe key 补齐缺失 Trigger。

无法证明外部运行结果时，不得为了 UI 好看伪造终态。

## 12. Observability

WorkflowInstanceVO 与 SSE 是运行状态投影，不是新的 truth owner。

- 查询必须来自 engine/durable runtime evidence；
- SSE 断连不能影响 WorkflowExecution；
- heartbeat 只用于连接保活；
- 日志必须带 execution/node/attempt/task 等可定位 identity，且不能成为状态来源。

## 13. Compatibility

架构治理不得顺手改变：

- `/api/v1/workflows/**` 现有 endpoint；
- request DTO / response VO JSON shape；
- `yak_workflow_*` 表与 Flyway baseline；
- persisted status / trigger source / strategy 字符串；
- WorkflowVersion publish 与 Task revision snapshot 语义；
- manual / API / test / schedule / backfill / rerun 行为；
- Trigger Ledger dedupe / queue / lineage；
- Yak Workflow Engine contract；
- Business Job TaskExecutionGateway contract。

真正需要改变以上 contract 时，应独立提出 Requirement / Domain / Migration 变更，不混入纯架构整理。

## 14. Non-goals

Workflow 当前不负责：

- 重新实现 Yak Workflow Engine 的 DAG 状态机；
- 实现具体 Task 的业务执行逻辑；
- 成为 Datasource 连接/凭据 owner；
- 把 Yak Schedule 当成 Schedule 配置数据库；
- 通过新的 Common/Helper/Context 隐藏跨子系统依赖。
