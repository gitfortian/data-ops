# PD-004 — Enterprise Data Integration Contract

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-09-24  
Owner: Product  
Related Feature: F-006  
Related Issues: #126, #127, #128, #129, #130

## Context

DataOps 当前的数据接入能力已经明显超过“同步任务 CRUD”阶段：

- DataSource 已具备连接、Catalog、Capability、SQL 等基础；
- Metadata 已具备采集任务、运行、变更、重试、Reconciliation；
- Offline Sync 已具备 Draft、发布快照、Execution / Attempt、Schedule、Cursor、Backfill、Retry、Reconcile、Metrics、Logs；
- Realtime Sync 已具备 Draft / DefinitionVersion / SyncExecution、Validate、Start / Stop、Restart、Apply Published Version、Checkpoint、Metrics、Events、Logs、Reconcile；
- Workflow 已具备 immutable version、Schedule、Trigger Ledger、Backfill、rerun、recovery；
- Job Runtime 已存在稳定的 `TaskProvider / TaskVersionSnapshot / TaskExecutor` 边界；
- Lineage / Asset / Metadata 已经有明确 owning contract。

当前主要问题因此不是“缺少一个同步引擎”，而是这些能力仍以内部模块形态暴露给用户：

```text
数据源
元数据
离线同步
实时同步
工作流
实例
运维
```

用户需要自己理解这些模块之间的边界，才能拼出一条完整的“把外部数据稳定接入平台”的路径。

如果 Phase 6 继续按内部模块堆功能，最容易产生五类长期问题：

1. 为了统一体验新建第二份 `IntegrationTask / IntegrationExecution` Truth；
2. 把 Offline 与 Realtime 强行抽成一个共享 Sync Core，破坏两种完全不同的运行生命周期；
3. 把 Draft、Published、Scheduled、Running、Failed 等不同状态塞进一个 overloaded `status`；
4. Direct Schedule 与 Workflow 同时触发同一个 Batch Integration，产生重复业务运行；
5. 同步成功后只留下 engine metrics，而 Metadata、Lineage、Asset、Operations 仍然断裂。

本 Decision 冻结 Phase 6 的产品模型。后续 F-006-B/C/D 只能实现或扩展本契约，不得在局部页面、DTO、Provider 或 runtime adapter 中重新发明产品语义。

## Current Behavior

当前 Capability Map 已定义“数据接入与集成”的用户目标为“把外部数据可靠带进平台”，包含 DataSource、文件资源、离线同步、实时同步和 Metadata Harvest。

当前 J1 主路径是：

```text
DataSource
 -> Sync / Metadata Harvest
 -> Semantic / Model
 -> Development
 -> Workflow
 -> Quality / Security
 -> Catalog / Governance view
```

但这条 Journey 尚未冻结以下关键规则：

- DataSource / Metadata Discovery 如何成为 Integration 的默认入口；
- Batch 与 Realtime 哪些体验统一、哪些 Truth 必须分离；
- Offline Revision 是否正式作为 immutable Published Version 进入产品模型；
- Direct Schedule 与 Workflow Managed 如何避免双重自动化；
- Realtime Published Version 与 Running Version 如何并列解释；
- Retry / Rerun / Backfill / Reconcile / Restart / Apply Version 的统一词义；
- Integration 如何向 Metadata / Lineage / Asset 提供 evidence；
- 用户如何从一个运维入口定位问题，而不产生第二份 runtime truth。

因此 Phase 6 的缺口是 **Enterprise Data Integration Contract**，不是新的底层执行引擎。

## Decision

### D1. Data Integration 是用户可见产品概念，但 Integration View 只是 derived projection

DataOps 正式使用 `Integration` 作为“把一个来源稳定移动/复制/持续同步到一个目标”的用户概念。

但 Phase 6 **不创建新的 Integration source-of-truth entity**。

统一产品 identity 采用：

```text
IntegrationKey = <integrationType>:<sourceIdentity>

examples:
BATCH:<offlineDefinitionId>
REALTIME:<realtimeTaskId>
```

其中：

- `sourceIdentity` 必须来自 Offline / Realtime owning domain；
- 展示名、路径、datasource name、table name 不能作为 Integration identity；
- Integration projection 可以缓存，但必须可从 owning contracts 重建；
- 删除 projection 不得导致 Offline / Realtime definition 或 runtime truth 丢失；
- Integration View 不拥有第二份 Draft、Version、Execution、Schedule、Checkpoint 或 Lineage Truth。

统一外壳至少可以表达：

```text
integrationKey
integrationType
project
owner
sourceRef
targetRef
definitionState
publishedVersion
operatingMode
runtimeSummary
lastActivity
governanceEvidenceSummary
specialistBacklinks
```

### D2. Unified Experience != Unified Runtime Truth

Batch 与 Realtime 共享产品入口、公共信息结构、状态语言和回链，但它们继续拥有不同领域生命周期。

Batch：

```text
Offline Definition
   -> Published Version
   -> BatchExecution
   -> Attempt(s)
   -> terminal
```

Realtime：

```text
Realtime Task
   -> DefinitionVersion
   -> SyncExecution / Deployment
   -> long-running runtime
   -> Stop / Restart / Apply Version / Reconcile
```

因此明确禁止：

- 创建一个通用 `IntegrationExecution` 替代 Offline / Realtime execution；
- 为 UI 统一把 Batch terminal execution 与 Realtime long-running deployment 做成同一种状态机；
- 把 Realtime 默认转换成普通 Workflow terminal Task；
- 为了共享代码而迁移 owning Truth。

### D3. Integration 生命周期必须使用四个正交状态维度

所有 Integration Surface 必须分别表达：

```text
1. Definition State
2. Release / Published Version State
3. Operating Mode
4. Runtime State
```

#### Definition State

建议产品语义：

```text
DRAFT
CLEAN
CHANGES_PENDING
INVALID
```

具体内部枚举可以不同，但必须能回答“当前 Draft 是否已保存、是否相对已发布版本有变化、是否通过定义校验”。

#### Release State

至少表达：

```text
NEVER_PUBLISHED
PUBLISHED(vN)
PUBLISHED(vN) + DRAFT_CHANGED
```

Release 不等于 Runtime Running。

#### Operating Mode

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

Realtime 后续如果出现事件编排等能力，必须另行扩展，不得直接复用 Batch operating mode 枚举制造假同构。

#### Runtime State

Runtime state 来自 owning runtime。公共 UI 可以映射为用户语言，但不得抹平以下关键差异：

```text
RUNNING
SUCCESS / SUCCEEDED
FAILED
CANCELLED / STOPPED
WAITING / QUEUED when applicable
UNKNOWN
CONFLICT
UNAVAILABLE (provider state, not runtime business terminal)
```

特别规则：

- `UNKNOWN != FAILED`；
- `CONFLICT != FAILED`；
- provider `UNAVAILABLE` 不允许映射成 STOPPED / EMPTY；
- Draft Changed 不改变正在运行的 Published Version。

### D4. Offline Published Revision 正式提升为 immutable Published Version 产品语义

Offline 当前已经使用 append-only published revision，并通过 `publishedRevisionId` 固定执行输入。

Phase 6 正式冻结产品语义：

```text
Offline Draft
   -> Publish
Offline Sync Version vN (immutable)
   -> Execute / Schedule / Workflow / Backfill
```

规则：

- Save Draft 不等于 Publish；
- Publish 不等于 Execute；
- Published Version 后续不可变；
- Manual / Direct Schedule / Workflow / Backfill 必须执行 immutable published snapshot；
- 后续 Draft 修改不得改变已创建 Execution / Backfill / Workflow snapshot；
- 现有 `OfflineJobRevision` 表和服务可以继续作为兼容存储，不要求仅为命名进行破坏性 migration。

历史恢复语义统一为：

```text
Select v1
 -> Restore v1 content as current Draft
 -> explicit Publish
 -> create/reuse a new current Published Version according to content contract
```

产品上不得制造“回滚会重写/覆盖历史 v1/v2/v3”的错觉。

### D5. Batch Automation 同一时刻只能有一个主 Owner

Batch Integration 的自动化模式固定为：

```text
MANUAL_ONLY
DIRECT_SCHEDULE
WORKFLOW_MANAGED
```

语义：

#### MANUAL_ONLY

- 无自动触发 Owner；
- 允许显式 operator Run（受权限与状态约束）。

#### DIRECT_SCHEDULE

- Offline owning domain 持有 schedule lifecycle；
- shared scheduler 只是 timing engine；
- 由 Offline Schedule 触发 Batch Execution；
- 不能同时作为 Workflow 自动调度 Task。

#### WORKFLOW_MANAGED

- Offline Direct Schedule 必须 disabled / not owning automation；
- Published Offline Integration 通过现有 Task contract 暴露给 Workflow；
- Workflow 拥有 Workflow Definition / Version / Schedule / Trigger / Node progression；
- Offline 继续拥有实际 BatchExecution / Attempt Truth。

规则：

> **同一个 Batch Integration 不允许 Direct Schedule 与 Workflow 同时作为自动化 Owner。**

Manual Run 可以在产品允许时作为显式 operator override，但必须记录 trigger source / operator / audit，并且不得被误认为 automation owner。

### D6. Workflow 只编排 Published Offline Integration，不接管 Offline Runtime Truth

Workflow 对 Offline Integration 的稳定边界继续是：

```text
Workflow
 -> TaskVersionSnapshot
 -> TaskExecution / TaskExecutor contract
 -> Offline Sync owner
 -> BatchExecution
```

冻结以下规则：

- Workflow Task Catalog 只发现符合 `WORKFLOW_MANAGED` 条件且已发布的 Offline Integration；
- Workflow 使用 immutable snapshot，不回读当前 Draft；
- Workflow 保存 external execution identity / projected status，不复制 Offline Attempt / engine job truth；
- Workflow retry/recovery 是 Workflow Node 语义；Offline retry/attempt 是 Offline runtime 语义，两者不得使用一个“重跑”概念混淆；
- Workflow businessDate rerun/backfill 不等于 Offline retry；
- Workflow Schedule 与 Offline Direct Schedule 不重复拥有自动触发责任。

### D7. Realtime 是 Continuous Pipeline，不是普通 Workflow terminal Task

Realtime 产品身份固定为 **Continuous Data Pipeline / Deployment**。

核心不变量：

```text
RealtimeTask != DefinitionVersion != SyncExecution
```

并正式冻结两种操作：

```text
Restart Current Execution
    = 继续使用当前 running DefinitionVersion

Apply Published Version
    = 将 runtime 切换到最新 Published DefinitionVersion
```

因此允许出现合法状态：

```text
Draft = v6 changes
Published = v5
Running = v4
Runtime = RUNNING
```

UI 必须能够明确解释这种 Version Drift。

Realtime 默认不作为普通 Workflow Node，因为普通 Workflow Node 的基本语义是：

```text
start -> running -> terminal -> downstream
```

而 Realtime Deployment 是长期运行状态。若未来需要 Workflow 对 Realtime 执行 Start/Stop/Deploy command orchestration，必须以 command / deployment semantics 单独设计，不得把 long-running runtime 伪装成 terminal task。

### D8. DataSource -> Capability -> Metadata Discovery 是默认 Source Onboarding 路径

Phase 6 默认入口冻结为：

```text
Create / Select DataSource
 -> Test Connection
 -> Capability Detection
 -> Metadata Discovery / Harvest
 -> Select Source / Target
 -> Create Batch Integration or Realtime Pipeline
```

DataSource Detail 应至少可以回答：

```text
Connection state
Catalog capability
Offline source/sink capability
Realtime / CDC capability when available
Last metadata harvest
Discovered object count/change state
Existing integrations
Available next actions
```

但这些字段继续来自 owning domains：

- Connection / capability = DataSource；
- discovered metadata = Metadata；
- integrations = Offline / Realtime projections。

Source Onboarding 不建立第二份 Catalog。

### D9. Authoring Mode 是编辑体验，不是产品 identity

Batch 当前的 single / multi / script 不再作为三个独立产品身份。

统一产品语言：

```text
Batch Integration
   -> Guided Mapping
   -> Bulk Mapping
   -> Advanced Definition
```

Realtime 当前 Wizard / YAML 同理：

```text
Realtime Pipeline
   -> Guided Editor
   -> YAML / Advanced Editor
```

不同 authoring mode 必须读取和保存同一 owning definition truth。

### D10. Recovery Vocabulary 一词一义

Phase 6 冻结以下跨产品术语：

#### Retry

对一次失败执行的“再次尝试”。具体是否在同一 logical execution 内创建新 Attempt，由 owning runtime 决定。

Retry 不意味着新的业务日期，也不自动改变 Published Version。

#### Rerun

重新发起一次业务运行，通常产生新的 business execution identity；Workflow rerun 可绑定原 WorkflowVersion / businessDate 语义。

#### Backfill

针对明确历史范围/业务日期集合的批量补数。Backfill 必须冻结提交时的 Version / Snapshot，不因排队期间 Draft 改变而漂移。

#### Reconcile

重新读取 external engine/runtime truth，用于把 UNKNOWN / CONFLICT / stale projection 收敛到可解释状态。Reconcile 不是 retry。

#### Restart Current Execution / Version

Realtime 使用当前运行中的 DefinitionVersion 重新启动 runtime。

#### Apply Published Version

Realtime 显式把 runtime 切换到最新已发布 DefinitionVersion。

所有 UI/API 文案不得用一个模糊“重跑”按钮覆盖这些不同动作。

### D11. UNKNOWN / CONFLICT / UNAVAILABLE 是一等产品状态

Phase 6 不允许为了页面简化把不确定状态压成 FAILED。

规则：

- engine submit 明确失败可以是 FAILED；
- 已提交后无法确认外部 engine 状态时使用 UNKNOWN / equivalent owning state；
- 本地与 external runtime evidence 冲突时使用 CONFLICT；
- provider/service 无法查询时是 `UNAVAILABLE` evidence state，而不是新的 runtime terminal state；
- `EMPTY` 只能表示 provider 正常且确认无数据；
- Reconcile 用于收敛 UNKNOWN / CONFLICT，不能被描述为“重试任务”。

### D12. Integration 只生产 Governance Evidence，不拥有 Metadata / Lineage / Asset Truth

同步成功或持续运行后，Integration 应产生可以被治理域消费的 evidence。

建议稳定 evidence shell 至少包含：

```text
integrationKey
integrationType
publishedVersion
executionOrDeploymentRef
sourceRef
targetRef
observedAt
provider / evidenceRef
status / delivery state
```

#### Metadata

Integration 可以触发/建议 output discovery、register sink evidence 或增量 harvest，但 Metadata domain 继续拥有 discovered entity truth。

#### Lineage

Integration 通过现有 Lineage registration contract 注册：

```text
Source Table/Asset
 -> DATA_MOVEMENT evidence
 -> Target Table/Asset
```

Lineage relation truth 继续属于 Lineage domain。

#### Asset

Integration Detail 可以引用 Source / Sink Asset；Asset 可以回链 Integration。但 Asset 不复制 Integration runtime truth，Integration 也不复制 Asset owner/quality/security truth。

#### Failure semantics

- 数据移动成功后，Metadata / Lineage evidence delivery 失败不得回滚数据移动；
- evidence delivery 必须可重试、幂等、可诊断；
- evidence gap 必须显式显示，不能伪造成“没有 metadata / lineage”；
- duplicate evidence delivery 不得制造重复 owning relation。

### D13. Integration Operations 是统一查找问题入口，不是新的 Runtime Owner

Phase 6 允许提供统一 Integration Operations projection，至少表达：

```text
Integration type
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

点击后回到专业 detail：

```text
Batch -> Offline Execution / Schedule / Backfill
Realtime -> Deployment / Checkpoint / Events / Logs
Workflow-managed Batch -> Workflow + Offline execution backlinks
```

Operations projection：

- 必须可从 owning truth 重建；
- 不允许用户手工编辑 runtime state；
- 不允许以日志解析结果作为核心状态；
- provider unavailable 必须显示 unavailable，而不是假 `0 running` / `0 integrations`。

### D14. Phase 6 Golden E2E 必须覆盖三条真实 Journey

#### Golden A — Batch Direct Schedule

```text
DataSource
 -> Connection / Capability
 -> Metadata Discovery
 -> Batch Authoring
 -> Validate
 -> Save Draft
 -> Publish v1
 -> DIRECT_SCHEDULE
 -> BatchExecution / Attempt
 -> Metrics / Logs
 -> SUCCESS
 -> Metadata / Lineage Evidence
 -> Asset / Operations
```

#### Golden B — Workflow Managed Batch

```text
Published Offline Integration
 -> WORKFLOW_MANAGED
 -> Workflow Task Catalog
 -> bind immutable snapshot
 -> Workflow publish/schedule
 -> WorkflowExecution
 -> Offline BatchExecution
 -> terminal
 -> next node
 -> Governance / Operations
```

#### Golden C — Realtime CDC

```text
DataSource / Metadata Discovery
 -> Realtime Authoring
 -> Validate
 -> Publish v1
 -> Start v1
 -> RUNNING / checkpoint / lag
 -> edit Draft v2
 -> Publish v2
 -> Running v1 + Published v2
 -> Apply v2
 -> RUNNING v2
 -> Governance / Operations
```

所有 Journey 必须包含权限、provider unavailable、runtime failure / unknown、evidence failure 等负向验证。

## Product Outcome

Phase 6 完成后，用户不再需要理解“数据源、元数据、离线同步、实时同步、工作流、实例”为什么是不同模块，才能完成数据接入。

用户围绕一个稳定目标工作：

> **把外部数据以可版本化、可编排、可恢复、可观察、可治理的方式稳定带入 DataOps。**

同时平台保持清晰 Truth Ownership，不通过“统一”制造新的跨域一致性问题。

本 Decision 主要服务：

- J1 — 从外部数据到可信、可治理的数据对象；
- J4 — 从发现运行问题到定位影响；
- J5 — 从敏感数据到安全消费的上游接入证据。

## Alternatives Considered

### Option A — 新建统一 Integration Domain，接管 Offline / Realtime definition + execution

拒绝。

它能快速统一 API/UI，但会复制成熟的 Offline / Realtime Truth，并迫使 long-running Realtime 与 terminal Batch 使用同一生命周期，后续会产生大量双写、同步和状态冲突。

### Option B — 保持所有模块完全独立，不建立 Integration 产品概念

拒绝。

它能避免跨域 projection，但用户仍必须理解内部模块拼装，J1 不能形成商业标品级闭环。

### Option C — Unified Product Projection + Domain-owned Truth（采用）

统一用户入口、状态语言、回链和 Journey；Offline / Realtime / Workflow / Metadata / Lineage 继续持有自己的事实。

### Option D — 所有 Batch 一律用 Workflow 调度

拒绝作为默认规则。

单任务周期运行使用 Direct Schedule 更轻；多任务依赖、业务 DAG 使用 Workflow Managed 更合理。Phase 6 采用“唯一 automation owner”而不是“所有任务强制 Workflow”。

### Option E — 把 Realtime Pipeline 暴露为普通 Workflow Task

拒绝作为默认模型。

长期运行 Deployment 与 terminal Workflow Node 的完成语义冲突。未来若需要编排 Realtime lifecycle，应按 deployment command contract 单独设计。

## Consequences

### Positive

- 用户获得统一 Integration 心智，同时不牺牲成熟领域模型；
- Batch / Realtime Draft、Published、Runtime 状态可以清晰解释；
- Direct Schedule / Workflow 双重调度风险被产品契约消除；
- Offline immutable version 语义正式收口；
- Realtime version drift、Restart / Apply Version 成为可理解产品行为；
- Recovery vocabulary 统一，避免不同页面都叫“重跑”；
- Metadata / Lineage / Asset 真正接入 Integration Journey；
- Operations 可以统一找问题，而不引入第二份 runtime truth。

### Trade-offs

- Integration View 需要跨域 composition，API/UI 比简单 CRUD 更复杂；
- Batch / Realtime 不能完全共享 DTO / state machine；
- Evidence delivery 需要处理 eventual consistency、retry、gap 与 partial failure；
- Operating Mode 切换需要服务端真实约束，而不仅是导航变化。

### Risks

- 实现团队可能逐渐把 Integration projection 表写成 owning table；
- UI 为求统一可能再次抹掉 Batch / Realtime 专业差异；
- Direct Schedule 与 Workflow 的模式约束若只做前端，会留下重复触发风险；
- evidence provider 不完整时，UI 可能误显示“无 lineage / 无 metadata”；
- Offline Revision 物理命名若被误认为必须立即全量重构，可能制造不必要 migration 风险。

## Truth / Ownership Impact

本 Decision 不迁移以下 Truth Owner：

```text
DataSource          -> DataSource domain
Metadata            -> Metadata domain
Offline Sync        -> Offline domain
Realtime Sync       -> Realtime domain
Workflow            -> Workflow domain
Lineage             -> Lineage domain
Asset               -> Asset domain
```

新增的是跨域**产品契约**与 derived Integration / Operations projection，不新增第二份业务 Truth。

## Navigation / UX Impact

Phase 6 不新增新的一级 Product Capability；继续使用现有“数据接入与集成”。

推荐默认导航：

```text
数据接入与集成
  -> 数据源
  -> 数据集成（Batch / Realtime）
  -> Integration Operations / Runs（若现有运维域更合适，也可作为现有运维入口的 projection）
```

旧的“离线同步 / 实时同步”URL 可以兼容保留，但应逐步汇入统一 Integration context；`single / multi / script` 只作为 Batch authoring mode，不作为并列产品。

DataSource / Metadata / Workflow / Asset specialist pages 继续存在，但使用稳定 identity 与 Integration context 双向回链。

## Migration Plan

1. **#127 / F-006-A**：评审本 Decision 与 F-006；通过后升级 PD-004=ACCEPTED、F-006=APPROVED，并同步 Capability Map / User Journeys / Glossary。
2. **#128 / F-006-B**：实现 Source Onboarding、Metadata handoff、统一 Integration authoring / canonical detail；不改变 owning Truth。
3. **#129 / F-006-C**：产品化 Batch operating mode、Schedule / Workflow handoff、Execution / Attempt、Backfill / recovery、Realtime deployment / version drift。
4. **#130 / F-006-D**：完成 Metadata / Lineage / Asset evidence、Operations projection，以及三条 Golden E2E / failure injection。

`Status: ACCEPTED` 只表示产品规则正式生效；在 #128/#129/#130 完成前，`Implementation` 保持 `NOT_STARTED` 或按真实进度更新。

## Acceptance Evidence

把 Implementation 更新为 DONE 前至少需要：

- Batch Direct Schedule Golden Journey 通过；
- Workflow Managed Batch Golden Journey 通过；
- Realtime CDC Golden Journey 通过；
- Draft / Published / Operating Mode / Runtime 四维状态有自动化或可重复集成验证；
- Direct Schedule / Workflow duplicate automation 被服务端阻止；
- Offline execution / backfill 固定 immutable published snapshot；
- Realtime Published Version / Running Version drift、Restart / Apply Version 有验证；
- UNKNOWN / CONFLICT / UNAVAILABLE 不会被 UI/API 伪装成 FAILED / EMPTY；
- Metadata / Lineage evidence delivery failure 可重试且不回滚同步成功；
- Asset / Operations backlinks 使用稳定 identity；
- 代码/存储审查确认没有第二份 Offline / Realtime / Workflow / Metadata / Lineage / Asset owning Truth。

## Supersedes

None
