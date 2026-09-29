# Offline Sync Domain

> 本文件定义离线同步**当前有效领域约束**。历史迁移过程以 Git / PR 为准，不进入当前 contract。

## Mission

离线同步负责：定义一项离线同步任务，并把手动、调度、工作流或补数触发转换成可追踪、可重试、可取消的业务批次。

```text
OfflineSyncTask
      │ trigger / backfill request
      ▼
BatchExecution
      ├── BatchKey
      ├── BatchScope
      ├── ExecutionSnapshot
      └── ExecutionAttempt 1..N
                │
                ▼
          Engine Adapter

CursorRange Batch SUCCEEDED
      │
      ▼
Task Cursor CAS
```

核心关系：`Task != Batch != Attempt`。

## Hard Rules

1. `OfflineSyncTask` 是长期配置；`BatchExecution` 是一次业务批次；`ExecutionAttempt` 是一次实际提交。
2. Retry 创建新 Attempt，不创建新 Batch。
3. Batch 创建时冻结 `ExecutionSnapshot`；Retry、延迟 Backfill dispatch 都不得回读 current Task 改变 Snapshot。
4. Retry 必须保持同一个 Batch、BatchScope、ExecutionSnapshot 和 RetryPolicy Snapshot。
5. `UNKNOWN != FAILED`；结果不确定时必须先 reconcile，禁止盲目 Retry。
6. `LOST` 不是新的领域状态，只允许作为旧持久化值读取，并统一解释为 `UNKNOWN`。
7. Batch 终态后不再追加 Attempt；再次运行同一数据范围应创建新 Batch。
8. 当前同一个 Task 最多一个 `RUNNING / WAITING_RETRY / UNKNOWN` Batch；PENDING Backfill 只排队，不占执行槽位。
9. 每个 Batch 必须有稳定 `BatchKey`；Schedule 使用 `scheduleId + plannedFireTime`，不能使用实际回调时间。
10. `BatchScope` 描述数据范围；不得用 `sceneType/syncType` 代替 Scope / Route / Policy。
11. Backfill 创建一组 Batch，不创建新的 Task 类型；同一次 Backfill 共享同一 Snapshot。
12. Cursor route (`cursorId -> sourceColumn`) 与 BatchScope 分离；不得把 cursorId 当成数据库字段名。
13. Cursor 只允许在 Batch `SUCCEEDED` 后推进；`FAILED / CANCELED / UNKNOWN` 不得推进。
14. Cursor 推进必须用当前位置 + stateVersion CAS；旧 Batch 晚到成功不得回退或跨越 Cursor。
15. Task `last-*` 只能作为查询投影；运行真相只能来自 Batch / Attempt。
16. Batch 状态必须由同 Batch 的 latest Attempt 推导；旧 Attempt 的晚到事件不得回退 Batch 真相。
17. `DefinitionRevision` 只是修订标识，不等于 immutable `DefinitionVersion`。
18. Link-Up Job / Worker / Quartz / HTTP DTO / DataSource credential 都不是 Core Domain 对象。
19. SyncDefinition / Snapshot 不长期保存密码；凭据只在 Attempt 提交边界解析。
20. Batch `ExecutionSnapshot.logicalJobSpec` 是冻结 JobSpec 的唯一运行真相；不得从 Attempt `submittedConfig` 回退重建 Snapshot。
21. `OfflineJobExecution / yak_offline_job_execution` 仅是 ExecutionAttempt persistence compatibility view；其重复 definition/config/JobSpec 字段只能作为历史审计副本。
22. `batch_id = NULL` 仅表示历史 batchless execution：允许查询，不得 Retry、Cancel、Reconcile、参与 Task runtime projection 或反向绑定到猜测出的 Batch。
23. Attempt repository/DAO 不得提供 Task 级 `hasActiveExecution` 或 retroactive `bindBatch`；Task 占用判断只读 Batch runtime truth。
24. 实时同步和离线同步保持独立 Core；不得因为名字相似提前抽 Shared Sync Kernel。

## Domain Impact Analysis

修改离线同步代码前，必须先回答：

1. 变化属于 Task、Batch、Attempt、Scope、Snapshot、Cursor 还是 Policy？
2. 是否改变 Batch / Attempt 生命周期？
3. 是否改变 Retry、UNKNOWN、Cancel 或并发语义？
4. 是否改变 BatchKey / 幂等身份？
5. 是否让 Retry / Backfill dispatch 回读 current Task 或 current SchedulePolicy？
6. 是否把 Task `last-*`、Engine 状态、Attempt compatibility copy 或外部 JobId 当成领域真相？
7. Batch 状态是否仍由 latest Attempt 唯一推导？
8. Cursor 是否只由 SUCCEEDED Batch 推进，并保留 CAS 顺序保护？
9. 是否让 batchless history 重新进入运行链？
10. 是否引入新的 `sceneType/syncType`、技术类型或凭据泄漏？
11. 是否能映射现有模型？不能映射则记录 `Domain Gap`，先设计再编码。

## Stable Runtime Contract

当前命令侧运行真相固定为：

```text
Task command
    │
    ▼
BatchExecution status
    │
    ▼
latest ExecutionAttempt

Task last-*              = projection only
Attempt snapshot copies  = audit compatibility only
batch_id = NULL history  = query only
```

Batch 状态推导固定为：活动 Attempt -> `RUNNING`；FAILED 且存在 `nextRetryTime` -> `WAITING_RETRY`；FAILED 无 Retry 窗口 -> `FAILED`；UNKNOWN -> `UNKNOWN`；SUCCEEDED/CANCELED 对应同名 Batch 终态。

冻结执行证据固定为：

```text
BatchExecution.ExecutionSnapshot
├── definitionSnapshot
├── definitionRevision
├── RetryPolicySnapshot
├── configDigest
└── logicalJobSpec       <- sole frozen JobSpec truth
```

Attempt persistence 中的 `definition_snapshot_json / config_digest / submitted_config` 继续写入，仅为了兼容既有 schema、历史接口与审计；任何 Retry、幂等校验、Scope 投影或延迟 dispatch 都不得以这些副本作为运行真相。

## Backfill / Cursor Contract

```text
Backfill(requestId)
      ├── Scope A -> PENDING Batch
      ├── Scope B -> PENDING Batch
      └── Scope C -> PENDING Batch

PENDING -> RUNNING CAS -> Attempt 1
```

PENDING Backfill 不占当前 Task execution slot；dispatcher 只在没有 occupying Batch 时运行下一个。CursorRange Batch 只有在当前 Cursor position 等于 `afterExclusive` 时可启动。

Cursor 推进固定为：

```text
Batch.status == SUCCEEDED
AND cursor.position == range.afterExclusive
AND cursor.stateVersion == expectedVersion
        │
        ▼ CAS
cursor.position = range.throughInclusive
```

## Historical Compatibility Boundary

保留但不进入新运行链：

- `yak_offline_job_execution` 旧表名与 `OfflineJobExecution` 类名；
- Attempt 上重复的 definition/config/submittedConfig 列；
- 历史 `batch_id = NULL` execution；
- 读取旧状态 `LOST` 时归一为 `UNKNOWN`。

这些是物理/接口兼容债务，不再是 runtime semantic debt。若未来要删表列或重命名，必须单独走 contract migration，不得在功能改动中顺手破坏历史查询。

## Forbidden Shortcuts

禁止：

- 把 Retry 实现成一次新的普通 execute；
- 把 UNKNOWN / persisted LOST 直接当 FAILED 自动重试；
- 用 Attempt repository 的活动状态查询代替 Batch 级并发和 reservation；
- 用 Task `lastJobStatus / lastExecutionId` 决定运行、停止、编辑、下线或删除；
- 用非 latest Attempt 的状态覆盖 Batch runtime truth；
- 用 actual callback time 作为 Schedule Batch 身份；
- 让 Attempt 自己重新生成或重建 Snapshot；
- Batch logicalJobSpec 缺失时回退读取 Attempt submittedConfig；
- 把 batchless history 重新绑定到猜测出的 Batch；
- Attempt SUCCEEDED 就直接推进 Cursor，而不确认 Batch SUCCEEDED；
- 在 FAILED / CANCELED / UNKNOWN Batch 上推进 Cursor；
- 不做 position/version CAS 就覆盖 Cursor；
- 把 cursorId 当 source column，或把 Route/Policy 塞进 BatchScope；
- 把 Link-Up JobSpec、Worker、Connector、Quartz 类型放进 Core Domain；
- 为“全量/增量/单表/多表/补数”继续增加任务类型枚举；
- 为了和 realtime 一致而提前增加 immutable DefinitionVersion 或 Shared Sync Kernel。

如果需求无法在本模型内表达：`Domain Gap -> STOP -> 先补领域设计`。
