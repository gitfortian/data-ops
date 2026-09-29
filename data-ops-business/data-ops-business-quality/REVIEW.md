# Data Quality Review

> 本文件定义 Data Quality PR **如何 Review**。Reviewer / AI 是裁判，不在 Review 中自行补需求或创造新领域语义。

## Review 前必读

```text
REQUIREMENTS.md  -> 模块必须提供什么
DOMAIN.md        -> 哪些业务事实不能违反
ARCHITECTURE.md  -> 角色和 truth ownership
DEPENDENCIES.md  -> package graph / corridor
../../CODE_STYLE.md -> Yak Ops 统一工程规则
PR diff / tests  -> 实际改了什么
```

## 1. Requirement Alignment

先检查是否改变当前需求 contract：

- Table Asset 注册是否仍以 Datasource Catalog 事实为准？
- Monitor / Rule / Settings save/delete 行为是否变化？
- manual / schedule execution 行为是否变化？
- single active execution 是否变化？
- after-commit dispatch 是否变化？
- STOP/CONTINUE、NOT_RUN、queue rejection 是否变化？
- Template / Folder / Workspace / Alert 行为是否变化？
- REST / permission / paging contract 是否变化？

需求文档未定义的新能力报告：

```text
Requirement Gap
```

不要通过“顺手重构”偷偷加入。

## 2. Domain Compliance

重点检查：

- TableAsset / Monitor / QualityExecutionPlan / Execution / RuleExecution 是否混淆；
- 已入队 Execution 是否回读 current Monitor/Rule/Settings 改写 frozen plan；
- 同 Monitor 是否可能产生多个 active Execution；
- disabled / no-enabled-rule Monitor 是否仍能执行；
- WAITING 记录是否可能在事务失败后仍被 Worker 执行；
- ERROR / NOT_PASSED / NOT_RUN 是否被错误折叠；
- STOP 后剩余 Rule 是否静默消失而没有 NOT_RUN evidence；
- AlertEvent 是否反向成为 Execution result owner；
- Yak Schedule 是否反向成为 MonitorSettings 配置 owner；
- Datasource metadata 是否被客户端字段覆盖；
- Workspace / Projector 是否开始修改 command truth。

违反当前规则：

```text
Domain Violation
```

当前模型无法表达真实需求：

```text
Domain Gap
```

## 3. Architecture Alignment

检查代码是否仍符合 `ARCHITECTURE.md`：

- Controller 是否进入明确 Manager / Reader / Projector；
- 是否重新创建 `service/common/helper/utils/base`；
- Manager 是否把 Reader 当 Helper；
- Reader 是否拥有 create/update/delete/run；
- Policy 是否只做 deterministic validate/normalize；
- Dispatcher 是否只负责 dispatch，不拥有最终执行状态；
- Worker 是否只消费 frozen plan 和执行证据；
- Gateway 是否停住 Datasource 外部类型；
- Repository/DAO 是否保持 persistence boundary；
- config 是否重新反向装配业务角色形成 cycle。

当前 Quality 不需要一个通用 `@Service` facade。新增 `@Service` 必须有明确新的稳定 Application API 设计，而不是为了“符合三层架构”。

## 4. Dependency Alignment

任何新增内部 import 都检查 `DEPENDENCIES.md` 和 `QualityDependencyBoundaryTest`。

重点 corridor：

```text
monitor -> QualityScheduleLifecycle / QualityScheduleCalculator
schedule -> QualityExecutionManager
workspace -> QualityMonitorReader
execution -> QualityAlertRecorder
asset/execution -> QualityDataCatalogGateway
```

Datasource 外部依赖只允许：

```text
QualityConfiguration -> BusinessDatabaseConfiguration
DataSourceQualityCatalogAdapter -> typed Datasource Catalog API
```

不要第一反应扩大白名单；先判断类是否放错 package 或缺少 Quality-owned port。

## 5. Correctness

必须给出可触发场景，不因为“代码看起来复杂”就报告问题。

高风险区域：

- Monitor lock 与 active-execution admission；
- Execution insert 和 afterCommit dispatch 顺序；
- ThreadPool queue rejection；
- Worker 重复执行 / mark-running guard；
- RuleFailureAction.STOP；
- final CheckResult 汇总；
- SQL compiler 的 read-only / identifier / WHERE 安全；
- Datasource plugin 返回元数据变化；
- Table Asset register/unregister 引用关系；
- Schedule save/pause/delete/reconcile；
- current Monitor edit 与 queued plan snapshot；
- Alert 记录异常；
- pagination / workspace report 边界值。

## 6. Compatibility

检查是否破坏：

- `/api/v1/data-quality/**` URL；
- DTO / VO JSON shape；
- permission codes；
- `yak_quality_*` tables / Flyway history；
- MyBatis Mapper XML；
- persisted enum/status values；
- shared `PageData` Repository paging；
- existing Monitor/Rule/Execution/Alert data；
- Datasource Plugin/Catalog typed contract；
- Yak Schedule key/handler contract。

架构整理默认不做 DB、REST、Domain semantic breaking change。

## 7. Safety

优先于整洁度检查：

- 重复 Execution；
- transaction rollback 后仍 dispatch；
- queue full 永久 WAITING；
- Worker 使用 current Monitor 漂移；
- SQL 失去只读限制；
- Datasource identity 错配；
- 删除仍被 Monitor 引用的 TableAsset；
- schedule callback 绕过 admission；
- Alert 失败覆盖执行真相；
- 日志/异常输出不应泄漏未来可能引入的敏感连接配置。

## 8. Tests / Guardrails

每个 P0/P1 问题都回答：

```text
现有哪个 behavior test 或 architecture test 应该挡住？
```

重点 guard：

- `QualityLayeringConventionTest`
- `QualityDependencyBoundaryTest`
- `QualityCodeStyleConventionTest`
- Asset / Monitor / Execution / Schedule / Template 的行为测试
- controller contract tests

修改 architecture corridor 时，文档与 executable test 必须在同一 PR 更新。

## 严重级别

```text
P0 Blocker
- 数据质量 SQL 产生破坏性写入
- 重复执行导致严重运行风险
- 明确敏感数据泄漏或不可恢复的数据破坏

P1 Must Fix
- Requirement / Domain violation
- Monitor/Execution truth ownership 错误
- transaction / queue / concurrency / schedule / snapshot 错误
- 明确 API / DB compatibility break
- architecture boundary 被绕过并造成真实耦合风险

P2 Suggestion
- 有明确收益但不影响正确性的可维护性、性能、可观测性建议
```

纯个人格式偏好不作为阻塞项；违反根 `CODE_STYLE.md` 且会造成角色/边界歧义时可以报告。

## 固定输出

```text
# Review Result
Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 建议列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Missing Tests
无 / 说明
```

有 P0/P1 -> `CHANGES_REQUIRED`；只有 P2 可以 `PASS`。