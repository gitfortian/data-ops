# Offline Sync Review

> 本文件定义**如何 Review**。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

按顺序读取：

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
ARCHITECTURE.md  -> 代码应该放在哪里、角色如何协作
DEPENDENCIES.md  -> 允许的依赖方向和 corridor
CODE_STYLE.md    -> 工程与代码风格
REVIEW.md        -> 按什么标准判卷
PR diff / tests  -> 实际改了什么
```

历史迁移方案只用于理解背景，不得覆盖当前这些 contract。

## Review 顺序

### 1. Requirement Alignment

检查代码是否符合 `REQUIREMENTS.md`：

- 是否实现已有离线同步能力？
- 是否改变手动、Schedule、Workflow、Backfill 的既有业务行为？
- 是否改变 Retry、UNKNOWN、Cancel、Cursor 或运行并发语义？
- 是否引入需求文档中没有的新 Task 类型、执行模式或自动推断行为？
- 是否越过离线同步控制面的模块边界？

出现未定义的新能力或行为变化时，报告：

```text
Requirement Gap
```

不要替产品或开发者自行补需求。

### 2. Domain Compliance

检查 `DOMAIN.md`，重点关注：

- Task / Batch / Attempt 是否混淆；
- Retry 是否错误地创建新 Batch，或被实现成一次普通 execute；
- Retry / 延迟 Backfill dispatch 是否回读 current Task 或 current Schedule 改变冻结 Snapshot；
- Batch 终态后是否还能追加 Attempt；
- BatchKey 是否稳定，Schedule 是否错误使用 actual callback time 作为身份；
- `UNKNOWN` / persisted `LOST` 是否被直接当成 FAILED 自动 Retry；
- 同 Task 是否可能同时出现多个 `RUNNING / WAITING_RETRY / UNKNOWN` Batch；
- Batch 状态是否由 latest Attempt 推导，旧 Attempt 晚到是否可能回退 runtime truth；
- Task `last-*` 是否重新参与启动、停止、编辑、下线、删除或 Retry 判断；
- Attempt 上的 definition/config/submittedConfig compatibility copy 是否重新被当成 Snapshot truth；
- Backfill 是否被建模成新的 Task 类型，或重新引入 `sceneType / syncType`；
- Backfill 同组 Batch 是否可能使用不同 Snapshot；
- Cursor 是否在 Attempt SUCCEEDED 或非 SUCCEEDED Batch 上推进；
- Cursor 是否可能回退、跨区间推进，或把 cursorId 直接当 source column；
- `batch_id = NULL` history 是否重新进入 Retry / Cancel / Reconcile / Task projection；
- Link-Up Job、Worker、Quartz、HTTP DTO、Credential 等基础设施类型是否泄漏进 Core Domain；
- 是否把离线/实时同步提前抽成 Shared Sync Kernel；
- Secret 是否进入 Task / Batch Snapshot / Attempt compatibility copy / 日志。

违反现有领域规则时，报告：

```text
Domain Violation
```

如果现有领域模型无法表达需求，报告：

```text
Domain Gap
```

不要用新增枚举、特殊状态或兼容分支偷偷绕过 Domain Gap。

### 3. Architecture / Dependencies

检查 `ARCHITECTURE.md + DEPENDENCIES.md`：

- 新代码是否放在正确 subsystem，而不是重新堆进 common/service/helper；
- `@Service` 是否只用于稳定 Application Service；
- 类名是否表达 Coordinator / Manager / Runtime / Planner / Gateway / Adapter 等真实角色；
- Controller / Dispatcher / Reconciler / Schedule 是否从声明过的 corridor 进入；
- 是否直接 import 其他 subsystem 的 internal implementation；
- Domain / DAO / Repository / Engine 是否保持底层边界；
- top-level package graph 是否出现新环。

单纯个人命名偏好不阻塞；但角色错误导致边界泄漏时属于架构问题。

### 4. Correctness

只检查有可触发场景的真实错误，重点包括：

- Batch / Attempt 状态迁移；
- latest Attempt 选择；
- Retry attemptNo / retryFrom 关系；
- Batch terminal guard；
- 空值、边界值和历史数据；
- 事务边界；
- CAS / 行锁 / reservation；
- BatchKey / request replay 幂等；
- Schedule 重复回调；
- Retry / Cancel 并发；
- Backfill 多节点 dispatcher 并发；
- PENDING -> RUNNING reservation；
- UNKNOWN / Reconcile / Worker restart；
- 外部提交超时、响应丢失和部分失败；
- Scope 投影是否使用冻结配置；
- Cursor position / version / range 顺序；
- Batch 成功与 Cursor 推进之间的事务和重复执行；
- Task projection 是否可能被旧 Attempt 晚到事件覆盖；
- Engine JobId、BatchId、AttemptId 是否可能错配。

不要因为实现看起来复杂就报告问题；必须说明哪条输入、状态或并发顺序会产生错误结果。

### 5. Compatibility

检查是否破坏：

- REST API；
- DB / Flyway migration；
- Task definition JSON / logical JobSpec；
- Schedule 配置；
- 历史 Batch / Attempt / Event / Cursor 数据；
- `batch_id = NULL` 历史查询；
- 前端列表、详情和 `last-*` 查询投影；
- 已存在的 PENDING / RUNNING / WAITING_RETRY / UNKNOWN Batch；
- persisted `LOST` 的读取兼容。

破坏性变更必须有明确迁移方案，禁止 Big-Bang 删除旧表、旧列或历史兼容字段。

特别注意：Attempt 上重复的 snapshot/config 字段仍可能承担 schema / API / 审计兼容职责。它们不能作为 runtime truth，但也不能因为“领域上重复”就无迁移直接删除。

### 6. Safety

重点检查：

- 同一触发产生重复 Batch；
- 同一 Batch 产生重复 attemptNo；
- terminal Batch 被 Retry；
- `UNKNOWN` 被盲目重试或被新 Batch 覆盖；
- Retry 与 Cancel 竞态；
- Schedule callback 重放；
- 多节点 Backfill dispatcher 双重提交；
- Batch Snapshot 被 current Task 漂移；
- Cursor 在失败/不确定状态推进；
- Cursor 回退或跳过未确认区间；
- batchless history 被重新激活；
- Secret 落库、进入快照或日志；
- 外部调用失败后遗留无法追踪的 Engine Job。

运行安全问题优先于代码整洁度。

### 7. Tests / Guardrails

每个 P0 / P1 问题都回答：

```text
现有哪个测试应该挡住？
```

如果没有，指出缺失测试。优先补能锁住领域行为的回归测试，而不是为了覆盖率堆普通 getter / mapper 测试。

高价值 guardrail 至少考虑：

- Retry 复用同 Batch / Scope / Snapshot；
- Retry 不读取 current Task；
- UNKNOWN 不自动 Retry；
- terminal Batch 禁止新 Attempt；
- Schedule BatchKey 重放幂等；
- 同 Task occupying Batch 并发保护；
- old Attempt late event 不回退 Batch / Task projection；
- Backfill group Snapshot 一致；
- PENDING reservation 防双 dispatch；
- Cursor 只在 Batch SUCCEEDED 后推进；
- stale Cursor advance 被拒绝；
- `batch_id = NULL` history 不进入运行链；
- persisted `LOST` 只归一为 UNKNOWN；
- Snapshot 缺失不 fallback Attempt compatibility copy；
- Secret 不进入持久化 Snapshot / 日志；
- package dependency / corridor architecture tests。

## 严重级别

```text
P0 Blocker
- 数据错写 / 丢失 / Cursor 错误推进造成不可恢复的数据范围错误
- 重复运行导致严重重复同步风险
- Secret 泄漏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- 违反 REQUIREMENTS.md / DOMAIN.md
- 明确架构边界、并发、幂等、事务、状态机或兼容性缺陷
- 高概率导致 Batch / Attempt / Cursor 运行故障

P2 Suggestion
- 有明确收益的可维护性、性能、可观测性或测试改进
- 不阻塞合并
```

纯格式或个人风格偏好不要作为问题提交，除非违反 `CODE_STYLE.md` 后会造成真实歧义、边界泄漏或风险。

## 每个问题必须有证据

一个有效 Review 问题至少包含：

```text
位置：文件 / 行或方法
级别：P0 / P1 / P2
依据：Requirement / Domain / Architecture / Dependency / correctness fact
场景：什么输入、状态或并发顺序会触发
风险：会造成什么结果
建议：修复方向，不必替作者重写整段代码
测试：应补或应命中的测试
```

没有可说明的触发场景和风险，就不要凑问题。

## 固定输出格式

```text
# Review Result

Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 问题列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Missing Tests
无 / 说明
```

规则：

- 有 P0 / P1 -> `CHANGES_REQUIRED`。
- 只有 P2 -> 可以 `PASS`，P2 不阻塞。
- 没发现真实问题 -> 直接 `PASS`，不要为了显得有价值硬凑问题。
- Review 结论只基于当前 contract、代码事实和可复现风险，不猜未来需求。
