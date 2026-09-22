# Workflow Domain

本文件定义 Workflow **当前领域事实、生命周期和 truth ownership**。历史迁移过程不属于 Domain contract。

## 1. Core Identity

Workflow 中必须长期区分：

```text
WorkflowDefinition
    != WorkflowVersion
    != WorkflowExecutionMetadata
    != Engine WorkflowExecution
    != NodeExecution
    != NodeAttempt
    != WorkflowSchedule
    != WorkflowScheduleTrigger
    != WorkflowBackfill
```

它们字段相似也不能合并成一个通用 Job/Task 状态对象。

## 2. Truth Ownership

| Truth | Owner |
| --- | --- |
| 当前可编辑 DAG / input / editor metadata | Yak Ops `WorkflowDefinition` |
| 不可变发布 DAG + Task revision snapshot | Yak Ops `WorkflowVersion` |
| version/testRun/trigger 等运行业务上下文 | Yak Ops runtime metadata |
| WorkflowExecution lifecycle | Yak Workflow Engine |
| NodeExecution lifecycle | Yak Workflow Engine |
| NodeAttempt lifecycle | Yak Workflow Engine |
| Schedule 配置 | Yak Ops `WorkflowSchedule` |
| Trigger dedupe / admission / lineage | Yak Ops Trigger Ledger |
| Backfill 批次与 pinned version | Yak Ops `WorkflowBackfill` |
| 时间触发计算/回调 | Yak Schedule projection |
| 节点对应的真实 TaskExecution | Business Job / TaskExecutionGateway |

`WorkflowTruth` 只命名这些 owner，不创建新的状态存储。

## 3. WorkflowDefinition

Definition 是 current mutable configuration。它可以反复编辑，但 current draft 不能改变已经发布的 WorkflowVersion，也不能改变历史 WorkflowExecution 的运行语义。

Definition 的 last execution 字段属于展示投影，不是新的运行状态 owner。

## 4. WorkflowVersion

WorkflowVersion 是 publish-time immutable snapshot。

```text
Draft revision N
    -> graph validation
    -> runtime graph selection
    -> TaskRevision snapshot
    -> WorkflowVersion N (immutable)
```

硬规则：

- version number 对同一 workflow 单调递增；
- version 绑定 source draft revision；
- run spec / task versions / editor metadata 发布后不可变；
- current TaskAsset revision 更新不能漂移到历史 version；
- Backfill / operational rerun 可以显式固定历史 published version。

## 5. Launch And Execution Metadata

一次 launch 需要同时区分：

```text
为什么启动          -> TriggerContext / launch metadata
用哪个业务版本      -> WorkflowVersion identity
engine 如何运行      -> WorkflowExecution runtime state
```

Yak Ops 可以保存 workflowVersionId/no、testRun、triggerId、scheduleId、plannedFireTime 等 metadata；这些字段不能反向覆盖 engine 状态。

## 6. Engine Runtime Truth

`WorkflowExecution / NodeExecution / NodeAttempt` 的状态机只存在于 Yak Workflow Engine。

Yak Ops Runtime 是适配层：负责注册 definition、调用 engine 命令、驱动 TaskExecution、恢复 durable metadata、投影 VO。

禁止：

- 在 Yak Ops 再建一套与 engine 并行的 Node/Attempt 状态机；
- 用 `latestExecutionStatus` 等 projection 覆盖 durable engine evidence；
- 因为 HTTP/Task gateway 一次调用失败就猜测 engine 已经失败；
- current Definition 变化后重写运行中/历史 execution 的 definition snapshot。

## 7. Retry Versus New Execution

```text
retry failed node / retry failed nodes / continue after failure
    -> same WorkflowExecution

restart / rerun from node
    -> new WorkflowExecution
```

原地恢复终态实例时，如果该实例来自 Trigger Ledger，必须先重新获得工作流并发槽位。否则可能与已经推进的 SERIAL_WAIT 队首形成并发。

## 8. TaskVersion And TaskExecution

`TaskVersionSnapshot` 与 `TaskExecution` 不是一回事。

- TaskVersionSnapshot 在 WorkflowVersion/运行准备时固定“要执行什么”；
- TaskExecutionGateway 返回“外部任务实际执行成什么”；
- NodeAttempt 绑定 external execution id 使进程重启后能继续查询；
- 一个旧 NodeAttempt 不能因为 current Task 定义变化而切换任务版本。

## 9. Schedule Truth

`WorkflowSchedule` 是 durable schedule definition；Yak Schedule 只是 runtime timer projection。

- schedule save/online/offline/delete 以业务表为准；
- framework schedule 丢失后可以 reconcile；
- framework nextFireTime 可以投影回业务表，但不能决定 schedule 配置；
- workflow offline 后不允许新的普通 scheduled execution；
- schedule callback 必须进入 Trigger Ledger，而不是直接 start engine。

## 10. Trigger Ledger

WorkflowScheduleTrigger 表示“一次计划触发”的 durable identity 和准入证据。

```text
Schedule / Backfill occurrence
        |
        v
    Trigger Ledger
        |
   admission/dedupe
        |
        v
   WorkflowLauncher
        |
        v
   WorkflowExecution
```

关键不变量：

- dedupe key 对同一逻辑计划唯一；
- plannedFireTime 是业务计划时间，actualFireTime 是实际到达时间，两者不能混淆；
- execution strategy 在 Trigger 创建时固定；
- SERIAL_WAIT 队列推进必须在 durable ledger 上完成；
- Trigger terminal 与 WorkflowExecution terminal 可以有不同枚举，但必须有确定映射；
- Trigger 只记录 execution identity/状态证据，不成为 engine runtime owner。

## 11. Backfill

WorkflowBackfill 是“批次”，不是一个 Execution。它固定 workflow/version、schedule、cron/timezone、business-date range、execution strategy、batch input，以及运维补跑时的 operationType/sourceExecutionId。

一个 Backfill 通过多个 Trigger Ledger 记录物化为多个 WorkflowExecution。Backfill cancel 不回滚已经运行的 Execution；它只阻止尚未启动的 occurrence。

## 12. Recovery Truth

Recovery 只能依据 durable evidence：engine execution snapshot、runtime metadata、external task execution binding、Trigger Ledger、Schedule/Backfill durable definitions。

内存中的 queue/control map 都是可重建 runtime state，不是持久业务 truth。

## 13. Observability

SSE、日志、列表/详情 VO 是 projection。客户端断开、日志失败或 SSE heartbeat 失败都不能修改 Execution 结果。

## 14. Domain Gap Rule

出现以下需求时先报告 **Domain Gap**，不要增加隐藏 flag：

- 运行中的 Execution 自动切换到最新 WorkflowVersion；
- retry 被要求创建新 Execution，但又继续复用原 execution identity；
- Trigger 不经过 durable admission 就直接启动；
- SERIAL_WAIT 允许同时占用多个槽位；
- Backfill 创建后自动跟随新的 active version；
- Yak Ops 与 Yak Workflow Engine 同时被要求拥有 Node/Attempt 状态；
- Schedule framework snapshot 被要求反向成为业务配置真相。

现有领域表达不了的新需求，应先更新本文件和行为测试，再修改实现。
