# Data Quality Requirements

本文件定义 Data Quality **当前必须保持的行为 contract**。它描述业务能力和兼容要求，不记录历史 Stage / Wave。

领域不变量看 `DOMAIN.md`，代码职责看 `ARCHITECTURE.md`，依赖方向看 `DEPENDENCIES.md`，统一工程规范看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。

## 1. Table Asset

Data Quality 必须以已注册的物理表作为质量监控目标。

- 注册前必须通过 Datasource 的 typed Catalog 能力确认物理表仍真实存在；
- Datasource 插件返回的 database / schema / table type / remarks 是注册时物理元数据的权威来源；
- 客户端不能通过注册请求伪造物理表类型或备注覆盖 Datasource 事实；
- 候选表列表必须排除已经注册的目标；
- 已被质量监控引用的 Table Asset 不能直接取消注册；
- Data Quality 不直接依赖 Datasource HTTP DTO/VO、DAO、Repository 或 Plugin Registry。

## 2. Monitor / Rule / Settings

质量监控由 Monitor、Rules 和 MonitorSettings 共同定义。

必须保持：

- 监控目标必须符合当前已注册表和目标唯一性约束；
- Monitor 名称、负责人、数据源和物理表身份必须经过现有 Policy 校验；
- Rule 的 scope、类型、阈值、区间、自定义 SQL 等继续由 Quality-owned Policy 标准化和验证；
- MonitorSettings 继续支持 MANUAL / SCHEDULE 两种运行方式；
- 调度周期、时间、weekday、cron、失败动作、通知渠道和通知目标继续由 Settings Policy 统一标准化；
- MANUAL 模式不保留无效调度参数；
- 非消息类通知在启用时必须具备可用通知目标；
- 删除 Monitor 前必须确认没有活动执行。

## 3. Execution Admission

手动执行和调度执行进入同一 Quality Execution 生命周期。

执行受理必须：

1. 锁定 Monitor；
2. 确认 Monitor 存在且 enabled；
3. 至少存在一条 enabled Rule；
4. 同一 Monitor 不存在另一个 active Execution；
5. 持久化 WAITING Execution；
6. 在受理时构造 immutable `QualityExecutionPlan`；
7. 只在事务提交成功后分发 Worker。

当前不允许同一 Monitor 并发创建多个活动执行。

## 4. Frozen Execution Plan

`QualityExecutionPlan` 是 enqueue-time immutable snapshot。

它必须冻结一次执行真正需要的：

- Monitor identity / datasource / physical target；
- Rule snapshot；
- Rule failure action；
- notification configuration；
- alert level。

Execution 一旦受理，后续修改当前 Monitor / Rule / Settings 不能改变已入队或运行中的执行计划。

Worker 只能消费收到的 `QualityExecutionPlan` 和外部执行证据，不得重新读取 current Monitor 作为规则/目标配置来源。

## 5. Rule Execution

Worker 必须逐条产生 RuleExecution evidence。

- SQL/metric 计算继续通过现有 `QualitySqlCompiler` / `QualityMetricEvaluator`；
- 数据读取只通过 `QualityDataCatalogGateway`；
- Quality SQL 保持现有只读、单查询约束；
- 每条规则记录执行结果、metric、expected value、SQL/error 和 duration 等当前审计字段；
- 单条规则异常记录为 ERROR evidence，不能丢失后继续伪装 PASSED；
- `RuleFailureAction.STOP` 下，前序规则出现非 PASSED 后，剩余规则必须记录为 `NOT_RUN`；
- `CONTINUE` 下按现有规则继续执行后续检查。

## 6. Execution Completion

Execution 的最终结果必须来自本次执行实际 RuleExecution 结果，而不是 UI 投影或 Alert 状态。

当前结果语义保持：

- 没有错误且没有未通过规则 -> PASSED；
- 存在未通过规则且没有 ERROR -> NOT_PASSED；
- 存在执行错误或 Worker 异常 -> ERROR；
- 执行中的运行态继续使用现有 WAITING / RUNNING 与 `CheckResult.RUNNING` 语义。

Worker 顶层异常必须将 Execution 收口为失败证据，并更新 Monitor 的 last-result projection。

## 7. Dispatch Safety

Execution dispatch 必须在数据库事务提交后进行。

- 未提交成功的 Execution 不得进入异步 Worker；
- ThreadPool 拒绝任务时，必须把已受理的 Execution 收口为 ERROR，而不是永久停留 WAITING；
- queue rejection 同时更新 Monitor last-result projection；
- dispatch/worker 不能变成 Monitor/Rule 当前定义的第二 truth owner。

## 8. Schedule

业务表中的 Monitor + MonitorSettings 是调度配置事实来源；Yak Schedule 是运行时触发投影。

必须保持：

- MANUAL 或 disabled Monitor 不保留活动调度；
- SCHEDULE 模式通过 QualityScheduleLifecycle 同步到 Yak Schedule；
- Schedule Handler 只负责验证当前是否允许触发并调用 `QualityExecutionManager`；
- Schedule callback 不直接写 Execution 表或绕过 execution admission；
- Monitor 删除时清理残留 schedule；
- nextRunTime 是运行投影，不取代 MonitorSettings 的配置事实。

## 9. Alert Evidence

Alert 不是 Execution 状态机。

- PASSED / RUNNING / NOT_RUN 不产生失败告警；
- 需要通知时记录 AlertEvent evidence；
- MESSAGE 渠道与其他通知渠道继续保持现有 delivery status 语义；
- 告警记录失败不得反向修改已经确认的 Execution 结果；
- 日志与告警不能成为 Monitor / Execution 的业务 truth。

## 10. Template / Folder

内置模板与自定义模板保持独立生命周期。

- built-in template 继续只读；
- custom template 名称/code/folder/SQL/参数 schema/Set Flag 继续遵守现有约束；
- 自定义 SQL 继续执行已有单 SELECT / normalization 规则；
- folder 名称、父子关系和循环约束继续保持；
- copy/create/update/delete 不改变现有 HTTP contract。

## 11. Workspace / Read Side

Workspace、Execution Workspace、structured log 都是 read-side projection。

- Reader / Projector 不拥有 Monitor 或 Execution command transition；
- 查询继续使用 Repository Domain contract；
- 分页 Repository 统一使用 `io.yak.framework.common.PageData<T>`；
- HTTP paging shape 保持现有 DTO/VO contract；
- structured log 从 persisted execution evidence 确定性投影，不成为新的持久化 truth。

## 12. Compatibility

Stage 2 治理不得顺手改变：

- `/api/v1/data-quality/**` 现有 endpoint；
- request DTO / response VO JSON shape；
- permission code；
- Quality database table / Flyway；
- shared `PageData` repository boundary；
- Datasource Plugin SPI；
- Yak Schedule framework contract；
- manual / scheduled execution semantics；
- single active execution per Monitor；
- after-commit dispatch；
- STOP -> remaining NOT_RUN；
- queue-full failure semantics；
- alert recording semantics。

真正需要改变以上 contract 时，应独立提出 Requirement / Domain / Migration 变更，不应混进纯架构治理 PR。