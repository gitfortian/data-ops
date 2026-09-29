# Data Quality Domain

本文件定义 Data Quality **当前领域事实、生命周期和 truth ownership**。历史重构过程不属于 Domain contract。

## 1. Core Identity

Quality 中需要长期区分的对象：

```text
TableAsset
    != Monitor
    != QualityExecutionPlan
    != Execution
    != RuleExecution
    != AlertEvent
```

它们不能因为字段相似而合并成一个通用 Job/Task 状态对象。

## 2. TableAsset

`TableAsset` 表示已经被 Data Quality 注册并认可的物理表身份。

它保存 Quality 需要的物理目标描述，但真实物理元数据在注册/候选读取时来自 Datasource typed Catalog boundary。

硬规则：

- 注册目标必须能由 Datasource Catalog 发现；
- database / schema / table 共同参与物理目标 identity；
- 已被 Monitor 使用的 TableAsset 不能直接取消注册；
- TableAsset 不是 Datasource aggregate 的副本，也不能成为新的 datasource truth owner。

## 3. Monitor Definition Truth

当前质量监控定义由以下事实共同组成：

```text
Monitor
+ Rules
+ MonitorSettings
```

Monitor 拥有：目标表、owner、enabled 等长期配置。

Rules 拥有：template、rule type/scope、column、operator、threshold、自定义 SQL 等规则配置。

MonitorSettings 拥有：MANUAL/SCHEDULE、调度表达、RuleFailureAction、通知配置和 alert level。

Monitor 的 last-result / last-execution 等字段是运行结果投影，不是新执行计划的配置来源。

## 4. Execution Admission

一次新执行只有在 admission 成功后成立。

当前安全顺序：

```text
lock Monitor
 -> require existing/enabled Monitor
 -> require enabled Rule
 -> reject active Execution collision
 -> insert WAITING Execution
 -> freeze QualityExecutionPlan
 -> afterCommit dispatch
```

同一 Monitor 最多一个 active Execution。

没有成功提交持久化事务，就不能把 Worker 当成已受理执行。

## 5. QualityExecutionPlan

`QualityExecutionPlan` 是 enqueue 时冻结的**不可变执行快照**。

```text
current Monitor / Rules / Settings
        |
        | enqueue
        v
QualityExecutionPlan (immutable)
        |
        v
Worker
```

它不是 current Monitor 的引用，也不是 DTO。

一旦生成：

- Monitor 后续改名、换 owner、修改目标或禁用，不能改写已入队计划；
- Rule 后续新增/删除/改阈值，不能改写已入队计划；
- Settings 后续修改 STOP/CONTINUE、通知配置，不能改写已入队计划；
- Worker 不回读 current Monitor 重新决定本次规则集合。

如果未来某个需求需要“执行中动态读取最新规则”，必须作为新的 Domain Gap 明确设计，不能用一次 repository query 偷偷绕过 snapshot 语义。

## 6. Execution Evidence

`Execution` 表示一次质量检查的持久化业务事实。

`RuleExecution` 表示本次 Execution 中一条规则的实际执行证据。

执行状态和检查结果由执行流程写入；Workspace/Controller/Alert 只能读取或投影，不能成为第二 owner。

硬规则：

- WAITING 记录必须先于异步 Worker；
- Worker 开始后通过 repository CAS/transition 更新为运行态；
- final CheckResult 由实际规则结果推导；
- ERROR evidence 不能被当成 PASSED；
- RuleFailureAction.STOP 下未执行规则必须留下 `NOT_RUN` evidence，而不是静默消失；
- queue rejection 必须留下可查询的 ERROR 结果；
- 一个旧 Worker/重复 dispatch 不能创建第二份同一 Execution truth。

## 7. Rule Result Semantics

当前规则结果保持互斥语义：

```text
PASSED       = 已执行且满足规则
NOT_PASSED   = 已执行但不满足规则
ERROR        = 规则本身执行异常
RUNNING      = 运行中投影
NOT_RUN      = 因执行策略未执行
```

`NOT_RUN != ERROR != NOT_PASSED`。

不要为了统计简单把这些状态折叠成一个 boolean success。

## 8. STOP / CONTINUE

`RuleFailureAction` 属于 enqueue-time plan。

- `CONTINUE`：按当前执行计划继续后续规则；
- `STOP`：发现前序非 PASSED 结果后停止真实执行，剩余规则记录为 NOT_RUN。

这条策略不能在 Worker 中回读 current MonitorSettings 重新决定。

## 9. Schedule Truth

Quality Schedule 的业务真相属于：

```text
Monitor.enabled
+ MonitorSettings.runMode / schedule fields
```

Yak Schedule 只是 runtime trigger projection。

因此：

- 保存/修改 MonitorSettings 后由 `QualityScheduleLifecycle` 同步外部调度；
- Schedule 丢失或应用重启可以从业务表恢复；
- framework snapshot 的 `nextFireTime` 可以投影回 MonitorSettings.nextRunTime；
- framework 不能反向成为 Monitor 是否启用调度的配置 owner；
- Schedule Handler 接受触发后仍必须走 `QualityExecutionManager` admission。

## 10. Alert Truth

`AlertEvent` 是通知/告警证据，不是 Execution 结果。

```text
Execution final result
      |
      v
QualityAlertRecorder
      |
      v
AlertEvent
```

Alert 记录失败不能把已经确认的 Execution 结果改成另一状态。

## 11. Template Domain

QualityTemplate / CustomTemplate / TemplateFolder 是规则定义支持域，不拥有 Monitor runtime truth。

- built-in Template 为内置可读定义；
- CustomTemplate 拥有自定义 SQL、check method/type、parameter schema、folder 等自定义定义；
- TemplateFolder 只拥有模板组织结构；
- Monitor Rule 在保存时经过 Policy 标准化，不能直接把 HTTP DTO 当作持久化/执行模型。

## 12. Datasource Boundary

Quality 不拥有 Datasource 连接、Plugin 或 Catalog truth。

跨模块只允许：

```text
Quality role
 -> QualityDataCatalogGateway
 -> DataSourceQualityCatalogAdapter
 -> Datasource typed Catalog API
```

Quality Core Domain 不知道 Datasource DTO/VO/PO、Repository、DAO 或 Plugin Registry。

`QualityQueryResult` 中的动态 row map 是外部 SQL preview 的边界表示；它不能扩展为 Quality 长期业务模型。

## 13. Persistence Boundary

Application 角色依赖窄 Repository port，而不是 DAO。

```text
Business role
 -> Quality*Repository
 -> RepositoryAdapter
 -> Quality*Dao / PO
```

Repository contract 使用 Domain / Query model 和 shared `PageData`；HTTP DTO/VO、PO、MyBatis 类型不允许泄漏到业务角色。

## 14. Domain Gap Rule

出现以下需求时先报告 Domain Gap，而不是增加隐藏 flag：

- 同 Monitor 多并发执行；
- queued Execution 自动切换到最新 Monitor 定义；
- 执行中动态修改规则集合；
- Alert delivery 反向决定 Execution result；
- Quality 自己成为 Datasource metadata owner；
- 新的规则状态被强塞进现有 PASSED/NOT_PASSED/ERROR/NOT_RUN。

现有领域表达不了的需求，应先更新本文件和行为测试，再修改实现。