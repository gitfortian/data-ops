# Offline Sync Requirements

> 本文件只描述**模块需要什么**，不描述怎么实现。历史需求和迁移讨论看 Issue / PR / Git；领域硬规则看 `DOMAIN.md`。

## 目标

Offline Sync 提供批式数据同步的控制面：定义离线同步任务，把手动、调度、工作流和补数触发转换成可追踪的业务批次，并提供执行、重试、取消、对账、补数和增量进度管理能力。

## 核心能力

- 创建、编辑、校验和保存离线同步任务。
- 描述 Source、Sink、表映射、字段映射、过滤条件和执行配置。
- 支持单表、多表等配置方式，但配置方式不是新的业务任务类型。
- 配置任务级调度策略和重试策略。
- 支持手动、Schedule、Workflow 和 Backfill 触发。
- 每次触发形成可独立追踪的业务批次，并保留当次运行所需的冻结配置。
- 一个业务批次允许发生多次实际执行尝试，用于失败后的重试。
- 支持停止当前运行批次，并查看批次、执行尝试、日志、异常和基础 Metrics。
- 外部执行结果不确定时支持持续 Reconcile，直到查明结果。
- 支持按数据范围补数；一次 Backfill Request 可以生成多个待执行批次。
- 支持增量 Cursor，记录任务已确认成功的数据进度。

## 关键业务行为

任务配置和已经创建的批次必须相互隔离。例如：

```text
Batch B100(snapshot=R3) RUNNING
+
Task current revision = R4
```

此时必须满足：

```text
Save Task R4       -> 不影响 B100
Retry B100         -> 仍执行 B100 冻结的范围和配置
New Manual Trigger -> 创建新的 Batch，并使用触发时的当前配置
Batch Terminal     -> 再次运行应创建新 Batch，不在旧 Batch 上追加普通执行
```

Retry 的业务含义是“重新尝试同一个批次”，不是“重新执行当前 Task”。因此 Task 后续修改、Schedule 后续修改都不能改变已经存在批次的 Retry 语义。

## 触发与批次要求

- 手动、Schedule、Workflow、Backfill 最终都必须形成明确的 Batch 身份。
- 同一逻辑触发重复到达时不能因为请求重试、回调重放或超时造成重复业务批次。
- Schedule 的同一次计划触发必须保持稳定身份，实际回调早晚不能改变它属于哪一次计划执行。
- 当前同一个 Task 最多一个 `RUNNING / WAITING_RETRY / UNKNOWN` Batch。
- Backfill 的 PENDING Batch 可以排队，但不能绕过同 Task 的运行占用限制并发提交。

## Retry / UNKNOWN / Cancel 要求

- Retry 必须属于原 Batch，保持原数据范围和原运行快照。
- Retry 创建新的执行尝试，必须保留 Attempt 次序和 Retry 来源，便于审计。
- Batch 已进入终态后不得继续追加 Retry Attempt。
- `UNKNOWN` 表示结果尚未查明，不等于 `FAILED`。
- `UNKNOWN` 时必须优先 Reconcile，禁止直接按失败处理并自动 Retry。
- Retry 创建与用户 Cancel 并发时不能同时成功，不能留下额外的失控执行实例。
- 外部 Worker 重启、网络超时或提交响应丢失时，应尽可能通过已有运行身份恢复事实，而不是猜测失败。

## Backfill / Cursor 要求

Backfill 是现有 Task 的一次补数请求，不是新的 Task 类型：

```text
Backfill Request
  -> Scope A -> Batch A
  -> Scope B -> Batch B
  -> Scope C -> Batch C
```

必须满足：

- 同一次 Backfill Request 创建的一组 Batch 使用同一份任务运行快照。
- 每个 Batch 明确表达自己的数据范围。
- 相同 Backfill 请求重放不能重复制造相同范围的 Batch。
- 当前无法明确映射到执行引擎的数据范围，不允许靠新增 `sceneType / syncType` 或猜测 SQL 语义解决。

Cursor 表示已经由成功批次确认的数据进度：

```text
Cursor = 100
Batch(100 -> 200) SUCCEEDED -> Cursor = 200
Batch(200 -> 300) FAILED    -> Cursor 仍为 200
```

同时要求：

- Cursor route 与数据范围概念分离，cursorId 不能被默认解释成数据库字段名。
- Cursor 只能由对应 Batch `SUCCEEDED` 推进；Attempt 单独成功不足以推进。
- `FAILED / CANCELED / UNKNOWN` Batch 不得推进 Cursor。
- 旧 Batch 晚到成功不得让 Cursor 回退，也不得跨过当前未确认的数据区间。
- 同一 Cursor 的连续补数范围必须保持可验证的顺序关系。

## 运行真相与查询要求

- Task 是长期配置，不承担单次运行状态真相。
- Batch 是一次业务执行的身份和生命周期真相。
- Attempt 是一次实际提交及其运行证据。
- Task `last-*` 只用于列表/详情等查询投影，不能决定启动、停止、编辑、下线、删除或 Retry。
- Batch 当前状态必须反映最新一次有效 Attempt，旧 Attempt 的晚到事件不能覆盖较新的运行事实。
- 历史 `batch_id = NULL` execution 允许查询和展示，但不得重新进入 Retry、Cancel、Reconcile 或当前 Task 状态计算。

## 安全与可恢复要求

- 数据源凭据只在实际提交边界短暂解析，不长期写入 Task 定义、Batch 快照、Attempt 审计副本或日志。
- 已创建 Batch 使用自己的冻结配置，不跟随 Task 后续修改漂移。
- 启动、Retry、Backfill dispatch 都必须具备幂等/并发保护，不能产生重复 Batch 或重复 Attempt。
- 外部调用超时、部分失败和状态不确定不能破坏已知业务身份。
- 历史兼容数据可以保留，但不能反向成为新运行链的业务真相。

## 模块边界

本模块负责离线同步控制面，不负责：

- 启动、扩缩容或管理 Link-Up / Worker / Flink Cluster 生命周期；
- 托管数据源密码、SSH 密码或其他长期 Secret；
- Connector 工程的构建、发布和部署管理；
- 通用工作流编排；
- 通用 ETL / 任意复杂转换引擎；
- 数据血缘计算；
- 为了和 Realtime Sync 名字相似而共享 Task / Execution Core Domain；
- 在语义不明确时自动推断多表补数、自定义 Query Scope 或 Cursor 字段。

## 当前明确未解决

以下能力需要单独设计，不能作为普通功能修改顺手加入：

```text
多表 / custom query 的 scoped Backfill 执行语义
当前单 Task 单 occupying Batch 之外的 ConcurrencyPolicy
真正 immutable DefinitionVersion / 发布版本模型
legacy execution 表列和类名的物理 contract migration
```

## 需求变更规则

如果 PR 引入本文件没有描述的新业务能力，或改变已有触发、Retry、UNKNOWN、Backfill、Cursor、并发和兼容行为：

```text
Requirement Gap
```

先确认需求并更新本文件，再实现代码。Reviewer / AI 不得自行补需求。

本文件只维护**当前有效需求**，不要追加迁移历史。
