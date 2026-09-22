# Data Development Architecture

本文件定义 Data Development 的长期结构 contract。需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，依赖方向看 `DEPENDENCIES.md`，Review 看 `REVIEW.md`，工程硬化看 `ENGINEERING_HARDENING.md`。

## 设计原则

1. **业务子系统优先。** package 要能表达 Node、Task、Execution、Dataset、Data Service Publication、Release、Lineage 等业务角色。
2. **Service 是入口角色，不是分层目录。** 允许稳定 Application Service，禁止通用 Service 大桶。
3. **名字表达职责。** Manager / Publisher / Validator / Normalizer / Coordinator / Resolver / Parser / Analyzer / Reader / Gateway / Worker 各自只承担对应角色。
4. **Truth 只有一个 owner。** Node、Draft、Revision、Execution、Task Catalog projection、Data Service Runtime projection、Lineage evidence 不互相冒充。
5. **Domain 只放领域事实。** 纯 API / query read model 跟随所属业务子系统，不因为是 record 就进入 `domain`。
6. **结构重构不偷改行为。** package move 与 REST / DB / Domain semantic change 分开。
7. **外部系统停在边界。** Task Runtime、Task Catalog、Dataset、Data Service Runtime、Lineage、DataSource 都是邻接上下文。
8. **架构必须可执行。** 文档由 architecture tests、Repository boundary 和固定 legacy allowlist 守住。

## Package Map

```text
io.yak.ops.business.development
├── controller          # HTTP inbound only
├── node                # Node identity / metadata lifecycle
├── directory           # workspace directory lifecycle
├── task                # Draft / validation / immutable Revision / Task Catalog projection
├── execution           # editor manual run + execution history application boundary
│   └── model           # execution query / response projections
├── dataset             # Dataset output-node application boundary
├── dataservice         # Data Service Node -> Runtime publication owner boundary
├── release             # published Task Catalog read/activation boundary
│   └── model           # release-center query projections
├── editor              # editor preference application boundary
├── lineage             # outbox / worker / write transaction / analysis adapter
│   └── analysis        # shared Lineage analysis-contract implementation
├── domain              # truth-bearing facts / value objects / invariants only
├── repository          # persistence contracts + JDBC/MyBatis adapters
├── dao                 # MyBatis primitives
├── config              # module configuration
└── service             # frozen legacy SQL lineage + Data Service compatibility island
```

禁止新增 `common / helper / helpers / util / utils / base` 业务大桶。

## Core Truth

```text
DevelopmentNode
      │ author
      ▼
DevelopmentTaskDraft (mutable)
      │ publish
      ▼
DevelopmentTaskRevision (immutable)
      │ project
      └────────> Task Catalog

current editor definition
      │ run
      ▼
DevelopmentTaskExecution
```

固定关系：

```text
Node != Draft != Revision != Execution
```

DATASET / DATA_SERVICE 是 Output Node，不进入 executable Task Draft/Revision lifecycle。

Node / Directory 的命令侧名称规则由 `DevelopmentNodeName`、`DevelopmentDirectoryName` 值对象持有；Node Type 的合法性和 capability gate 由 `DevelopmentNodeType` 持有。Application Service 只负责仓储、事务和邻接上下文协调。

## Stable Application Entries

稳定入口按业务包放置：

```text
node.DevelopmentNodeService
directory.DevelopmentDirectoryService
task.DevelopmentTaskService
execution.DevelopmentTaskRunService
execution.DevelopmentTaskExecutionService
execution.DevelopmentTaskExecutionControlService
dataset.DevelopmentDatasetNodeService
dataservice.DevelopmentDataServicePublicationService
release.DevelopmentReleaseService
editor.DevelopmentEditorSettingsService
```

Controller 只依赖这些入口以及明确保留的 legacy lineage preview corridor，不直接进入 Repository / DAO。

## Task Subsystem

```text
DevelopmentTaskService
├── DevelopmentTaskNodeResolver
├── DevelopmentTaskDefinitionNormalizer
├── DevelopmentTaskDraftManager
├── DevelopmentTaskValidator
├── TaskDefinitionDigestCalculator
├── DevelopmentTaskPublisher
└── DevelopmentTaskRevisionReader
```

Task publish 成功后只向 `lineage.DevelopmentLineageOutbox` 投递 SQL evidence 工作，不直接同步写 Lineage 图。

## Execution Subsystem

```text
DevelopmentTaskRunService
    -> TaskExecutionGateway
    -> DevelopmentTaskExecutionService
        -> DevelopmentTaskExecutionRepository
        -> DevelopmentTaskExecutionRepositoryAdapter

DevelopmentTaskExecutionControlService
    -> persisted ReconciliationCandidate(project_id)
    -> ProjectContextScope
    -> TaskExecutionGateway
```

Editor Run 使用临时 `TaskVersionSnapshot(version=0)`。它不是 Publish，也不能创建 immutable Revision。

Execution history 是 Data Development 的运行记录；真正的 executor runtime state 仍由共享 Task Runtime 提供。`execution.model` 只是读侧 / response projection，不拥有运行状态。

Reconciler 可以跨项目扫描 durable execution，但每条记录必须恢复自己的 `project_id` 后才能执行 get/update/runtime reconcile，不能在空 `CurrentProject` 下回写。

## Node / Directory / Dataset / Data Service Publication / Release

```text
node        -> Node identity / rename / delete / updater metadata
directory   -> hierarchy / path / empty-delete rule
dataset     -> Dataset-owned datasource + SQL + field contract
dataservice -> Data Service Node authoring/runtime source + publication owner boundary
release     -> Task Catalog release projection + online/offline/activate
editor      -> user editor settings
```

`DevelopmentDataServicePublicationService` 不拥有 Data Service Runtime truth。它只保证 source-managed Runtime 的创建、重发和启停必须从 Data Development authoring context 进入。

`DevelopmentDataServiceNodeSourceProvider` 已归 `dataservice`，不再位于 legacy `service`。`DevelopmentDataServiceSqlCompiler` 的核心实现也归 `dataservice`；旧包只保留无逻辑 compatibility shell，等待大体量 Node Service 独立迁移。

## Lineage Boundary

```text
Task publish
   -> DevelopmentLineageOutbox
   -> DevelopmentLineageOutboxRepository
   -> JDBC adapter
   -> DevelopmentLineageWorker
   -> ProjectContextScope(project_id)
   -> legacy SQL parser preparation
   -> lineage.analysis.DevelopmentSqlProjectionLineageAnalyzer
   -> DevelopmentLineageWriteTransaction
   -> yak-ops-business-lineage
```

后台 Outbox 不能在空 `CurrentProject` 下读取 Node/Revision。Worker 必须从持久化 `project_id` 恢复受信项目上下文，并校验 Outbox 与 Node 的项目身份一致后才能写 Lineage。

## Persistence Boundary

Repository 是持久化 contract；SQL/JDBC/MyBatis 是 adapter primitive：

```text
application role -> repository contract -> repository adapter / dao -> database
```

Stage 3 已关闭三个历史 direct-JDBC 债务：

```text
Execution history -> DevelopmentTaskExecutionRepositoryAdapter
Editor settings    -> DevelopmentEditorSettingRepositoryAdapter
Lineage Outbox     -> DevelopmentLineageOutboxRepositoryAdapter
```

`execution`、`editor`、`lineage` 等 application role 不得直接持有 `JdbcTemplate`。架构测试会阻止这种依赖重新出现。

## Frozen Legacy Service Island

`service` 已不是新代码落点，只允许固定剩余类型：

```text
DerivedAwareSqlColumnLineageParser
DevelopmentDataServiceNodeService
DevelopmentDataServiceSqlCompiler      # logic-free compatibility shell
DevelopmentDraftConflictException
DevelopmentSqlLineagePreviewService
DevelopmentSqlLineageService
DevelopmentTaskValidationException
SqlColumnLineageParser
SqlTableLineageParser
TableIdentityResolver
```

`DevelopmentDataServiceNodeSourceProvider` 已迁入 `dataservice`，不得回流。大体量 SQL parser / Data Service Node Service 的后续迁移必须独立 PR，不与算法或产品行为变化混做。

## Frontend Workbench Boundary

工作台主组件负责 orchestration，不再内嵌所有稳定视图与纯算法：

```text
DevelopmentWorkbench
├── StandaloneResourceEditors
├── UnsavedChangesModal
├── workbenchTabs
└── workbenchResponse
```

Tab close/focus 规则必须保持纯函数并由 `workbenchTabs.test.ts` 覆盖。Data Service Node Editor 仍是已知大组件债务，后续拆分时优先分离 publication state、contract panels 与 resize state，不在结构 PR 中改变交互语义。

## Architecture Guards

```text
DataDevelopmentArchitectureDocumentationTest
  -> module contract docs exist and README links them

DataDevelopmentDependencyBoundaryTest
  -> controller cannot enter repository/dao
  -> application roles cannot own JdbcTemplate
  -> package graph cycle check
  -> frozen service allowlist
  -> Data Service Runtime Provider owner package

DataDevelopmentRoleConventionTest
  -> stable @Service entries
  -> technical roles do not masquerade as Service
  -> moved roles cannot return as compatibility wrappers

DataDevelopmentGovernanceContractTest
  -> every Data Development controller is PROJECT_REQUIRED + READ
  -> EDIT / DELETE / EXECUTE / PUBLISH / RELEASE stay explicit
```

如果架构规则真的变化，同一个 PR 中同时修改文档、测试和代码；不要因为护栏报错就删除护栏。
