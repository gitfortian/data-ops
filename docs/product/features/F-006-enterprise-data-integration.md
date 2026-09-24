# F-006 — Enterprise Data Integration & Orchestration Closed Loop

Status: DRAFT  
状态说明：Phase 6 产品模型处于 Product Review，只有 `PD-004` 被 ACCEPTED 后，本 Feature 才升级为 APPROVED 并指导主体实现  
Feature ID: F-006  
负责人：Product  
目标版本：Phase 6  
创建日期：2026-09-24  
关联产品决策：`PD-004-enterprise-data-integration-contract.md`  
关联 Epic / Issues：#126、#127、#128、#129、#130

> 本 Feature 的目标不是重写 Offline / Realtime / Workflow，而是把 DataSource、Metadata Discovery、Batch、Realtime、Schedule、Workflow、Recovery、Governance Evidence 组织成一条企业用户可以理解和验收的数据集成闭环。

## 1. 目标与价值

**功能：** Enterprise Data Integration & Orchestration Closed Loop

**主要用户：**

- 负责把业务库、OLTP、OLAP、搜索/文档存储等外部数据接入平台的数据工程师；
- 负责 Batch Integration、Realtime CDC Pipeline 配置与发布的数据开发人员；
- 负责 Schedule / Workflow 编排与失败恢复的调度运维人员。

**次要用户：**

- 需要查看接入结果、血缘、元数据与资产上下文的数据治理人员；
- 需要诊断 Integration runtime、Backfill、Checkpoint、Unknown/Conflict 的平台运维人员；
- 作为下游消费者的数据开发、Semantic / Modeling、Dataset / Data Service 等产品能力。

**用户问题：**

当前平台已有强大的工程能力，但用户仍需要自行理解内部模块才能回答：

- “这个数据源能不能做离线同步 / CDC？”
- “我发现的这张表怎么直接开始接入，而不是手工再选一遍？”
- “当前改的是 Draft、线上 Published Version，还是正在运行的版本？”
- “这个 Batch 是由任务自己的 Cron 还是 Workflow 在调度？”
- “失败后该 Retry、Rerun、Backfill 还是 Reconcile？”
- “Realtime 发布了 v5，线上为什么还跑 v4？”
- “同步成功以后，Metadata / Lineage / Asset 为什么没有自动形成可追溯上下文？”
- “运维时应该去 Offline、Realtime、Workflow、Metadata 哪个页面找问题？”

**期望结果：**

用户能够：

1. 从 DataSource / Metadata Discovery 自然进入 Integration 创建；
2. 根据真实 capability 选择 Batch Integration 或 Realtime Pipeline；
3. 在统一产品上下文中理解 Source / Target、Draft、Published Version、Operating Mode 和 Runtime；
4. 明确 Save、Validate、Publish、Run、Schedule、Workflow 的不同含义；
5. 让 Batch 只由一个 automation owner 管理，避免 Direct Schedule 与 Workflow 重复触发；
6. 让 Realtime 保持 continuous deployment 语义，并明确 Published Version 与 Running Version；
7. 对失败进行 Retry / Rerun / Backfill / Reconcile / Restart / Apply Version 等正确动作；
8. 查看 Batch Execution / Attempt 或 Realtime Deployment / Checkpoint / Metrics / Logs；
9. 在成功接入后获得 Metadata / Lineage / Asset evidence 与稳定回链；
10. 从统一 Operations 入口找到问题，再进入对应专业 runtime detail；
11. 在 provider / runtime 不可用时看到真实不确定性，而不是假 FAILED / EMPTY；
12. 为后续 Semantic / Development / Governance / Consumption 提供稳定、可追溯的接入基础。

## 2. 产品模型

F-006 的产品模型以 `PD-004` 为唯一决策来源。在 PD-004 尚未 ACCEPTED 前，本节是待评审契约，不是已生效 Product Truth。

### 2.1 Integration 是产品投影，不是新的 owning entity

稳定 identity：

```text
IntegrationKey = <integrationType>:<sourceIdentity>

BATCH:<offlineDefinitionId>
REALTIME:<realtimeTaskId>
```

统一 Integration View 只组合 owning facts：

```text
Integration View
├── identity / type / project / owner
├── sourceRef / targetRef
├── definition state
├── published version
├── operating mode
├── runtime summary
├── governance evidence summary
└── specialist backlinks
```

不得：

- 新建第二份 Offline / Realtime definition；
- 新建第二份 execution/deployment truth；
- 用 Integration projection 表替代 owning domain；
- 使用展示名、datasource 名、table path 作为 stable identity；
- 为统一 API 强迫 Batch 与 Realtime 使用同一 runtime state machine。

### 2.2 Truth Owner

| 事实 | Owner |
|---|---|
| DataSource connection / credentials / capability | DataSource |
| Metadata discovery / collected entity / change | Metadata |
| Batch definition / published version / execution / attempt / cursor / direct schedule | Offline Sync |
| Realtime task / definition version / sync execution / runtime state | Realtime Sync |
| Workflow definition / version / schedule / trigger / workflow execution | Workflow |
| Generic task routing / immutable task snapshot contract | Job Runtime |
| Lineage relation | Lineage |
| Asset identity / governance context | Asset |
| Integration View / Operations View | derived projection only |

## 3. 生命周期状态模型

F-006 禁止把多个维度压缩成一个模糊 `status`。

### 3.1 Definition State

用于解释当前可编辑定义：

```text
DRAFT
CLEAN
CHANGES_PENDING
INVALID
```

具体 owning enum 可以不同，但产品必须能区分：

- 本地/当前编辑内容尚未保存；
- Draft 已保存；
- Draft 与当前 Published Version 一致；
- Draft 相对 Published Version 有变化；
- 定义校验失败；
- definition provider / dependency unavailable。

### 3.2 Release / Published Version

至少表达：

```text
NEVER_PUBLISHED
PUBLISHED vN
PUBLISHED vN + CHANGES_PENDING
```

Published 不等于 Running，也不等于 Schedule Enabled。

### 3.3 Operating Mode

Batch：

```text
MANUAL_ONLY
DIRECT_SCHEDULE
WORKFLOW_MANAGED
```

Realtime：

```text
CONTINUOUS
```

Batch 同一时刻只允许一个 automation owner。

### 3.4 Runtime State

Runtime state 继续由 owning domain 提供。公共产品层必须保留重要差异：

```text
QUEUED / WAITING when applicable
RUNNING
SUCCESS / SUCCEEDED
FAILED
CANCELLED / STOPPED
UNKNOWN
CONFLICT
```

Provider / evidence state 独立表达：

```text
READY
EMPTY
UNAVAILABLE
FORBIDDEN
NOT_APPLICABLE
UNSUPPORTED
```

其中：

- UNKNOWN 不等于 FAILED；
- CONFLICT 不等于 FAILED；
- UNAVAILABLE 不等于 STOPPED；
- EMPTY 只能表示 provider 成功且确认无数据；
- UNSUPPORTED 表示能力不支持，不等于 provider 故障。

## 4. Source Onboarding & Metadata Discovery

### 4.1 默认 Journey

```text
Create / Select DataSource
→ Test Connection
→ Capability Detection
→ Metadata Discovery / Harvest
→ Select Source / Target
→ New Integration
→ Batch Integration / Realtime Pipeline
```

DataSource 是连接入口，Metadata 是 discovered structure truth，Integration 只负责跨域 handoff。

### 4.2 DataSource Context

DataSource Detail 至少需要组合展示：

```text
Connection
Capability
Metadata Harvest
Discovered Objects
Existing Integrations
Next Actions
```

能力状态至少能解释：

- Catalog discovery supported / unsupported / unavailable；
- Offline source support；
- Offline sink support；
- CDC / Realtime support；
- SQL / preview 等现有能力（适用时）。

不得把 capability `UNSUPPORTED` 映射成 connection failure。

### 4.3 Metadata Handoff

用户从 discovered database/schema/table 创建 Integration 时：

- 使用稳定 metadata/source reference；
- 不要求用户手工复制 schema/table 文本；
- metadata provider failure 不显示成 `0 tables`；
- source removed / changed 进入 Integration risk / validation context；
- Integration 不修改 Metadata owning truth。

## 5. Integration Authoring

### 5.1 Unified Entry

产品入口统一为：

```text
New Integration
├── Batch Integration
└── Realtime Pipeline
```

### 5.2 Batch Authoring Modes

当前 single / multi / script 统一解释为编辑方式：

```text
Guided Mapping
Bulk Mapping
Advanced Definition
```

它们必须读取/保存同一个 Offline Definition Truth。

Batch authoring 最低需要覆盖：

```text
Source
Target
Table / object selection
Mapping
Filter
Incremental strategy / cursor when applicable
Runtime / connector options
Validation
Draft Save
Publish
Operating Mode
```

### 5.3 Realtime Authoring Modes

Realtime 统一为一个 Pipeline 产品身份：

```text
Guided Editor
YAML / Advanced Editor
```

二者共享同一 `CdcPipelineSpec` / owning definition。

最低需要覆盖：

```text
Source
Sink
Route Mapping
Schema Evolution Policy
Runtime Environment
Capability / Validate
Draft Save
Publish
```

### 5.4 Validate / Save / Publish 分离

产品必须明确：

```text
Validate != Save Draft != Publish != Run/Start
```

- Validate 可以绑定当前待保存定义或精确 saved Draft，按 owning contract 明确；
- Save 只持久化 Draft；
- Publish 产生/激活 immutable Published Version；
- Run / Start 使用 Published Version；
- later Draft edits 不改变正在运行的 Published Version。

## 6. Batch Integration Productization

### 6.1 Immutable Published Version

Offline 产品生命周期正式表达：

```text
Editable Draft
    ↓ Publish
Immutable Offline Sync Version vN
    ↓
Manual / Direct Schedule / Workflow / Backfill
```

现有 physical `Revision` 存储可以保持兼容，但产品层统一称 Published Version。

历史恢复：

```text
View v1
→ Restore as current Draft
→ validate/edit
→ explicit Publish
→ new current Published Version
```

不得显示为“覆盖 v1/v2/v3 历史”。

### 6.2 Execution / Attempt

运行关系：

```text
Published Version
→ BatchExecution
  ├── Attempt #1
  ├── Attempt #2
  └── ...
→ terminal
```

用户 Detail 至少可解释：

- exact Published Version；
- trigger source；
- source / sink；
- start/end/duration；
- current/final state；
- Attempts；
- engine execution ref；
- record / byte / QPS / table metrics（现有能力范围内）；
- structured failure reason；
- logs；
- next valid recovery action。

### 6.3 Direct Schedule

复用 Offline Schedule lifecycle / shared schedule engine。

最低产品语义：

```text
Operating Mode = DIRECT_SCHEDULE
Cron / Timezone
Enabled
Next Runs
Last Fire / Next Fire
Retry Policy
Overlap / Misfire when supported
Schedule Health
```

Schedule provider unavailable 不得显示为 Disabled。

### 6.4 Workflow Managed

```text
Operating Mode = WORKFLOW_MANAGED
→ published Offline Integration exposed through TaskProvider
→ immutable TaskVersionSnapshot
→ Workflow Node
→ Offline execution owner
```

Workflow 只持有编排事实，不拥有 Offline Attempt / engine execution truth。

### 6.5 Backfill

Backfill 必须是一等产品能力：

```text
Select historical range/business scope
→ choose/freeze Published Version
→ plan batches
→ queue / run
→ inspect failures
→ retry/recover
```

至少回答：

- 补哪个范围；
- 使用哪个 version；
- 会拆多少 batch；
- 是否排队；
- cursor 会不会受到影响；
- partial failure 如何处理；
- 后续 Draft 修改为什么不会改变已提交 backfill。

## 7. Realtime Pipeline Productization

### 7.1 Realtime Lifecycle

```text
Realtime Task
→ Draft
→ Publish DefinitionVersion
→ Start
→ SyncExecution / Deployment
→ long-running runtime
```

Realtime Detail 应围绕 Deployment，而不是只围绕配置表单。

### 7.2 Published Version / Running Version

必须能够同时展示：

```text
Draft Changes
Published Version
Running Version
Runtime State
```

允许并明确解释：

```text
Draft has v6 changes
Published v5
Running v4
```

### 7.3 Runtime Detail

最低需要表达：

```text
Runtime Environment
Start time / uptime
Checkpoint health / last checkpoint
Throughput
Lag
Events
Submission logs
Runtime exceptions
Last reconcile
```

不得通过日志猜测核心 runtime state。

### 7.4 Lifecycle Actions

动作语义固定：

```text
Start
Stop
Restart Current Execution
Apply Published Version
Reconcile
```

- Restart 保持当前 DefinitionVersion；
- Apply 显式使用最新 Published Version；
- Reconcile 读取 external runtime truth，收敛 UNKNOWN / CONFLICT；
- runtime provider unavailable 不等于 stopped / failed。

## 8. Orchestration Boundary

### 8.1 Automation Owner Rule

Batch 自动化必须满足：

```text
Direct Schedule XOR Workflow Managed
```

不是 UI 单选框意义上的 XOR，而是服务端 owning lifecycle 必须真正阻止双重自动触发。

### 8.2 Workflow Boundary

Workflow 继续拥有：

- Workflow Definition / Version；
- Schedule / Trigger Ledger；
- WorkflowExecution / Node progression；
- Workflow retry / recovery / businessDate rerun；
- Workflow Backfill。

Offline 继续拥有：

- Offline Published Version；
- BatchExecution / Attempt；
- cursor；
- Direct Schedule（仅 DIRECT_SCHEDULE）；
- Offline Backfill execution truth。

### 8.3 Realtime Boundary

Realtime 默认不作为普通 terminal Workflow Task。

如果未来要编排 Start/Stop/Apply 等 lifecycle command，需要单独 Product Decision / Feature 扩展；不把 long-running deployment 硬塞进 `start -> terminal -> next node` 模型。

## 9. Recovery Vocabulary

### Retry

针对失败执行的再次尝试，按 owning runtime 决定是否是新 Attempt / execution。它不自动改变 Published Version。

### Rerun

重新创建一次业务运行，Workflow 场景通常绑定 businessDate / WorkflowVersion / original execution evidence。

### Backfill

对明确历史范围的批量补数；必须冻结 submit-time Version/Snapshot。

### Reconcile

读取 external engine/runtime truth，修复 UNKNOWN / CONFLICT / stale projection；不是 Retry。

### Restart Current Execution

Realtime 使用当前运行 DefinitionVersion 重启。

### Apply Published Version

Realtime 把 runtime 切换到最新 Published DefinitionVersion。

以上词汇必须进入 UI copy、API projection、runbook 和 Product Glossary（PD-004 ACCEPTED 后同步基线）。

## 10. Governance Evidence Closed Loop

### 10.1 Integration Evidence Shell

建议统一证据引用：

```text
integrationKey
integrationType
publishedVersion
executionOrDeploymentRef
sourceRef
targetRef
observedAt
provider
providerEvidenceRef
deliveryState
```

该 shell 用于跨域追踪，不成为 Metadata / Lineage / Asset owning record。

### 10.2 Metadata Evidence

Integration success / stable realtime running 可触发或生产：

- sink/output discovery evidence；
- metadata refresh recommendation / registration；
- source/sink schema observation evidence。

Metadata domain 继续决定实体是否创建、变更、消失与 reconciliation。

### 10.3 Lineage Evidence

通过现有 stable lineage registration contract 注册：

```text
Source Table / Asset
→ data movement relation + integration evidence
→ Target Table / Asset
```

必须幂等、可重试，并保留 version / execution / observed time。

### 10.4 Asset Backlinks

Integration Detail 可以进入 Source/Sink Asset；Asset / Metadata context 可以回到对应 Integration。

回链全部使用 stable ID，不依赖展示名匹配。

### 10.5 Evidence Failure

- 数据同步成功后 evidence delivery failure 不回滚数据移动；
- delivery failure 可诊断、可重试；
- evidence gap 明确显示 `UNAVAILABLE / PENDING / FAILED` 等真实状态；
- 不允许因为 provider failure 显示“无血缘/无元数据”。

## 11. Integration Operations

统一 Operations 用来回答“哪里出问题”，而不是取代 owning runtime。

### 11.1 List / Summary

至少包含：

```text
Integration Type
Source -> Target
Published Version
Executed / Running Version
Operating Mode
Runtime State
Trigger Source
Last Activity
Health / Metrics Summary
Evidence Health
Next Action
```

### 11.2 Specialist Drill-down

```text
Batch
→ Offline Execution / Attempt
→ Direct Schedule
→ Backfill / Cursor

Realtime
→ Deployment
→ Checkpoint / Metrics
→ Events / Logs / Reconcile

Workflow Managed Batch
→ Workflow Execution
↔ Offline Execution
```

### 11.3 Projection Rules

- projection 必须可重建；
- SSE / streaming 断开后从 durable owning truth 恢复；
- 读失败不能变成假 EMPTY；
- 不开放“手工改 runtime status”这种破坏 Truth 的操作；
- runtime / evidence failure 使用结构化状态，不解析日志文本作为核心事实。

## 12. UX / Navigation

F-006 不新增一级 Product Capability，继续归属“数据接入与集成”。

目标用户心智：

```text
数据接入与集成
├── 数据源
├── 数据集成
│   ├── Batch
│   └── Realtime
└── 运行 / Operations context（具体入口位置按现有导航统一）
```

专业页面仍然保留：

- Metadata；
- Workflow；
- Asset / Lineage；
- Offline / Realtime specialist detail。

旧 URL 可以兼容，但不能继续形成不同产品语义。

## 13. 权限 / Project / Failure 原则

### Permission

UI 只能根据权限改善交互；后端 owning domain 始终是最终权威。

至少需要分别保护：

- datasource read/create/update/test；
- metadata read/harvest；
- batch create/edit/publish/execute/schedule/backfill；
- realtime create/edit/publish/execute；
- workflow read/edit/publish/execute；
- evidence / governance read；
- Operations admin action（如存在）。

### Project

所有 source / target / integration / workflow / evidence 查询与 mutation 必须遵守 Project Space；跨项目 stable ID 不得通过 Detail / validation / backlink 泄漏。

### Failure

至少区分：

```text
Connection invalid / unavailable
Capability unsupported
Metadata empty / unavailable
Validation failed / provider unavailable
Draft conflict
Publish failed
Engine submit failed
Task failed
Runtime unknown
Runtime conflict
Schedule unavailable
Evidence delivery failed
Forbidden
Cross-project
```

## 14. Phase 6 交付结构

Phase 6 保持粗粒度，只有 4 张主 Issue：

```text
#127 F-006-A Product Contract / Decision
        ↓
#128 F-006-B Source Onboarding / Authoring
        ↓
#129 F-006-C Execution / Orchestration / Recovery
        ↓
#130 F-006-D Governance Evidence / Operations / Golden E2E
        ↓
Close #126
```

### 子 Issue 拆分规则

只有满足任一条件时再拆：

1. 独立 PR 链且生命周期明显不同；
2. 可独立并行；
3. 有独立产品验收；
4. 是主 Issue 的明确阻塞风险；
5. schema / API / migration compatibility 需要单独评审。

不要按 Controller / Service / Repository / Mapper / DTO / UI Component / Connector Adapter 拆票。

## 15. Golden E2E

### Golden A — Batch Direct Schedule

```text
Create / Select DataSource
→ Test / Capability
→ Metadata Discovery
→ select source/target
→ create Batch Integration
→ mapping/filter/incremental
→ Validate
→ Save Draft
→ Publish v1
→ DIRECT_SCHEDULE
→ scheduled fire
→ BatchExecution
→ Attempt
→ metrics/logs
→ SUCCESS
→ sink Metadata evidence
→ Lineage evidence
→ Asset / Operations
→ Integration Detail
```

必须验证：

- Draft edit 后已触发 v1 execution 不漂移；
- Publish v2 后新 trigger 使用 v2；
- Retry 与新 Rerun 可区分；
- Backfill 固定 Version/Snapshot；
- schedule unavailable 不显示成 disabled。

### Golden B — Workflow Managed Batch

```text
Published Offline Integration
→ switch WORKFLOW_MANAGED
→ Workflow Task Catalog
→ bind published snapshot
→ Workflow publish
→ Workflow schedule/trigger
→ WorkflowExecution
→ Offline BatchExecution
→ terminal
→ next node
→ Governance / Operations
```

必须验证：

- Direct Schedule 已失去 automation ownership；
- Workflow 不拥有 Offline Attempt Truth；
- external execution backlink 正确；
- Workflow rerun 与 Offline retry evidence 可区分；
- running execution 不受 Draft / later publish 漂移影响。

### Golden C — Realtime CDC

```text
DataSource / Metadata Discovery
→ Realtime Pipeline
→ Validate
→ Save Draft
→ Publish v1
→ Start v1
→ RUNNING
→ Checkpoint / Throughput / Lag
→ edit Draft v2
→ Publish v2
→ Published v2 / Running v1
→ Apply Published Version
→ RUNNING v2
→ Metadata / Lineage evidence
→ Operations
```

必须验证：

- Restart Current Execution 仍使用 v1/v2 当前运行版本；
- Apply Published Version 显式切换版本；
- UNKNOWN / CONFLICT 故障注入与 Reconcile；
- runtime provider unavailable 不伪装 STOPPED / FAILED。

## 16. Failure Injection Matrix

Phase 6 最终至少覆盖：

- invalid datasource credentials；
- connection unavailable；
- capability unsupported；
- metadata provider unavailable；
- confirmed metadata empty；
- source table removed / changed；
- sink schema incompatible；
- Draft changed but not published；
- publish stale / conflict；
- engine submit failure；
- task failure / retry exhausted；
- duplicate trigger / idempotency；
- backfill queued / partial failure；
- Direct Schedule / Workflow mode conflict；
- Workflow external execution recovery；
- Realtime UNKNOWN / CONFLICT / runtime unavailable；
- Metadata / Lineage evidence delivery failure；
- Asset provider unavailable / forbidden；
- cross-project / permission denied。

## 17. Phase 6 验收

只有全部满足才允许把 F-006 更新为 SHIPPED、关闭 #126：

- [ ] PD-004 已 ACCEPTED，F-006 已 APPROVED/IMPLEMENTING 后完成主体交付。
- [ ] #128 Source Onboarding / Authoring 用户结果完成。
- [ ] #129 Execution / Orchestration / Recovery 用户结果完成。
- [ ] #130 三条真实 Golden Journey 与 failure matrix 完成。
- [ ] Batch / Realtime 的 Definition / Published / Operating / Runtime 四维状态可解释。
- [ ] Offline Published Version immutable，历史 restore 不覆盖历史。
- [ ] Direct Schedule 与 Workflow automation owner 唯一且由服务端约束。
- [ ] Workflow 通过稳定 Task contract 使用 Offline Integration，不复制 execution truth。
- [ ] Realtime Published / Running version drift、Restart / Apply / Reconcile 可解释。
- [ ] Retry / Rerun / Backfill / Reconcile 一词一义。
- [ ] UNKNOWN / CONFLICT / UNAVAILABLE 不伪装 FAILED / EMPTY。
- [ ] Integration success 能进入 Metadata / Lineage / Asset evidence closure。
- [ ] Operations projection 可统一定位问题且不成为 runtime truth。
- [ ] Project / RBAC / Audit 关键动作按现有平台 contract 生效。
- [ ] evidence delivery failure 不回滚成功的数据运行，且可以重试/诊断。
- [ ] 代码/存储/架构评审确认没有第二份 Offline / Realtime / Workflow / Metadata / Lineage / Asset Truth。

## 18. Enterprise Gate

Phase 6 最终验收同时保存：

- stable datasource / source / target identities；
- exact Published Version；
- operating mode；
- execution / attempt / deployment identity；
- trigger source；
- structured runtime failure / recovery evidence；
- metrics / checkpoint / log evidence；
- metadata / lineage evidence refs；
- Asset / Workflow / Integration backlinks；
- Project / RBAC negative evidence；
- schema/API/migration compatibility review；
- provider partial failure / retry diagnostics；
- 三条 Golden E2E 可重复执行记录。

PR merge、单个 Controller/API、单一契约测试、只有成功同步记录都不能代替产品验收。

## 19. Non-goals

F-006 明确不做：

- 不重写 Offline / Realtime execution engine；
- 不创建新的 shared Sync Core owning domain；
- 不重新实现 Workflow Engine / Scheduler；
- 不把 Realtime 默认做成普通 Workflow terminal Task；
- 不创建新的通用 ETL Transform DSL；
- 不以新增大量 Connector 数量作为完成标准；
- 不重做 Metadata / Lineage / Asset；
- 不让 Integration / Operations projection 成为 owning Truth；
- 不提前实现完整 Semantic / Metric 产品化；
- 不扩张为完整 Incident / Reliability / APM 平台；
- 不在本期实现完整复杂 Security Governance；
- 不以 Agent-first 方式重构 Integration 产品。
