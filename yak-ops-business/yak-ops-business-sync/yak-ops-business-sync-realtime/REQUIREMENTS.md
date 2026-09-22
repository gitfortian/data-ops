# Realtime Sync Requirements

> 本文件只描述**模块需要什么**，不描述怎么实现。历史需求和讨论看 Issue / PR / Git。

## 目标

Realtime Sync 提供持续数据同步的控制面：定义同步任务、发布可运行版本、控制运行实例，并查看运行状态和基础可观测信息。

## 核心能力

- 创建、编辑、校验和保存实时同步任务。
- 描述 Source、Sink、同步路由、启动策略、Schema 演进和执行策略。
- Wizard 与 Yak YAML 编辑同一份逻辑配置，不产生两套业务定义。
- 发布可追踪、不可变的已发布版本。
- 启动、停止任务，并查看当前运行状态。
- 支持“重启当前版本”和“应用最新已发布版本”两个明确操作。
- 查看提交日志、运行异常、Checkpoint 和基础 Metrics。
- 任务绑定运行环境；支持本地或远程执行 Flink CDC CLI。
- 保存每次运行所使用的定义版本和运行环境快照，便于追踪和恢复。

## 关键业务行为

允许同时存在：

```text
Running E100(V3)
+
Draft V4
+
Published V4
```

此时必须满足：

```text
Save Draft             -> 不影响 E100
Publish V4             -> 不热更新 E100
Start                   -> 使用当前 Published Version
RestartExecution(E100) -> 新建同版本 Execution
ApplyPublishedVersion  -> 新建使用目标 Published Version 的 Execution
```

## 运行安全要求

- 同一 Task 最多一个 Active / Uncertain Execution。
- `UNKNOWN / CONFLICT` 时不能盲目创建第二个运行实例。
- 启动请求必须具备幂等保护，不能因超时或重试造成重复运行。
- Stop 与 Start 并发时不能留下失控的外部 Job。
- 外部提交结果不确定时必须保留可恢复身份并通过 Reconcile 查明事实。
- 数据源凭据只在提交边界短暂使用，不长期写入定义、快照或日志。
- 已运行实例使用自己的运行环境快照，不跟随之后的环境修改漂移。

## 模块边界

本模块负责实时同步控制面，不负责：

- 启动、扩缩容或管理 Flink Cluster 生命周期；
- 托管数据源密码或 SSH 登录密码；
- 把 Flink Job、Pipeline YAML、SSH 命令当成业务 Task/Definition；
- 通用工作流编排；
- 通用 ETL / 任意复杂转换引擎；
- 数据血缘计算；
- Connector 工程的构建和部署管理。

## 当前明确未解决

以下能力不是普通需求变更可以顺手加入的内容，需要单独设计：

```text
Archive / Tombstone
ExecutionPolicy checkpoint/restart 完整运行时生效
Flink FINISHED / snapshot-only
legacy failure-rate mapping
Compute Environment 物理上下文拆分
API v2 / 物理 Schema 命名清理
```

## 需求变更规则

如果 PR 引入本文件没有描述的新业务能力或改变已有业务行为：

```text
Requirement Gap
```

先确认需求并更新本文件，再实现代码。Reviewer / AI 不得自行补需求。

本文件只维护**当前有效需求**，不要追加迭代历史。