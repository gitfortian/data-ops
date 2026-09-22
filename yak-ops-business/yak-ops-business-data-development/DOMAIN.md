# Data Development Domain

> `REQUIREMENTS.md` 定义“需要什么”；本文件定义“哪些领域事实不能被实现细节破坏”；`ARCHITECTURE.md` 定义这些事实由哪些代码角色负责；`DEPENDENCIES.md` 定义允许依赖谁。

## 核心模型

```text
DevelopmentNode
├── executable node
│     │
│     ▼
│  DevelopmentTaskDraft (mutable)
│     │ publish
│     ▼
│  DevelopmentTaskRevision (immutable)
│     │ project
│     └──────────────> Task Catalog
│
│  current editor definition
│     │ run
│     ▼
│  DevelopmentTaskExecution
│
└── output node
      ├── DATASET
      └── DATA_SERVICE
             │
             ├── DevelopmentDataServiceDraft
             └── DevelopmentDataServiceRevision
```

核心关系：

```text
Node != Draft != Revision != Execution
```

命令侧基础约束由值对象持有：

```text
DevelopmentNodeName       = Node 名称规范化与合法性
DevelopmentDirectoryName  = Directory path segment 合法性
DevelopmentNodeType       = Node 类型词汇、分类与 capability gate
```

Application Service 不再复制这些规则。

## Truth Ownership

```text
DevelopmentNode             = 工作区长期身份与位置
DevelopmentTaskDraft        = 当前可编辑定义
DevelopmentTaskRevision     = 不可变发布版本
DevelopmentTaskExecution    = 编辑器运行历史
Task Catalog                = 发布状态 / 跨模块任务资产投影
Dataset Version             = Dataset 输出定义快照
Data Service Revision       = Data Service 不可变发布事实
Lineage subsystem           = Lineage Asset / Relation 最终事实
Task Runtime                = 外部执行事实
```

Task Catalog 不能反向成为 Data Development Revision 的存储真相；Lineage 也不能成为 Task 发布状态的真相。

## Domain Model Placement

`domain` 不是“所有 POJO 的默认目录”。它只保留**拥有领域事实、领域不变量或核心值语义**的类型。

纯读侧 / 展示侧模型跟随所属业务子系统：

```text
release.model
├── DevelopmentReleaseSummary
├── DevelopmentReleasePage
└── DevelopmentReleaseDetail

execution.model
├── DevelopmentTaskExecutionSummary
├── DevelopmentTaskExecutionPage
├── DevelopmentTaskExecutionDetail
└── DevelopmentTaskRunResult
```

这些对象可以组合多个 truth owner 的数据用于 API 展示，但自身不是新的 truth owner。不能因为它们是 record / DTO 就放进 `domain`。

当前仍保留在 `domain` 的历史 SQL lineage preview/result 等类型属于后续 legacy lineage 拆分范围，不在本次结构重构中顺手迁移。

## 13 条硬规则

1. **Node 是身份，不是版本。** 编辑定义和发布内容不能继续堆进 `DevelopmentNode`。
2. **Node Type 能力必须显式声明。** 是否支持 Task Lifecycle 只由 `DevelopmentNodeType.supportsTaskLifecycle()` 决定。
3. **Draft 可变但有版本。** 每次保存通过 `draftRevision` 做 optimistic concurrency check。
4. **Published Revision 不可变。** 发布后只能新增 Revision 或切换 Catalog 指针，不能修改历史 Revision。
5. **Draft、Revision、Execution 必须分离。** 运行或发布不能偷读错误的生命周期对象。
6. **Editor Run 不等于 Publish。** 编辑器运行使用临时 `TaskVersionSnapshot(version=0)`，不能隐式发布。
7. **TaskDefinition 只有一套规范化规则。** Node Type 对齐、schemaVersion、content 和 configJson normalization 不能在多个 Service 各复制一份。
8. **Publish 校验由 Task Plugin 提供能力事实。** Data Development 负责协调，不复制插件内部 SQL/SHELL/HTTP 规则。
9. **相同 Draft + 相同 Digest 重复发布必须幂等。** 可以重复修复 Task Catalog projection，但不能重复插入等价 Revision。
10. **DATASET / DATA_SERVICE 不是 executable Task。** 输出节点不得为了复用 Service 而进入 Task Draft / Revision 生命周期。
11. **Lineage 是派生事实。** SQL Revision 是证据来源；Asset / Relation 的最终 owner 是 Lineage 模块。
12. **结构重构不能偷改行为。** package move、角色拆分、领域语义变更、REST/DB 变更必须分开处理。
13. **Domain 不是 Read Model 大桶。** Page / Summary / Detail / response projection 必须跟随所属子系统，除非它本身真的拥有领域事实或不变量。

## Task Definition Contract

`TaskDefinition` 是 Data Development 与 Task Plugin / Task Runtime 共享的逻辑定义表示。

进入 Draft、Publish、Editor Run 前必须统一规范化：

```text
taskType       -> trim + upper-case + equals Node.type
schemaVersion  -> > 0
content        -> null becomes empty string
configJson     -> JSON Object + canonical serialization
```

同一规则不得在 `DevelopmentTaskService`、`DevelopmentTaskRunService`、Controller 等位置重复实现。

## Publish Contract

```text
require executable Node
    -> lock Draft
    -> verify expected draftRevision
    -> normalize TaskDefinition
    -> Task Plugin validation
    -> calculate Definition Digest
    -> reuse or append immutable Revision
    -> reconcile Task Catalog projection
    -> enqueue SQL lineage evidence work
```

`DevelopmentTaskRevision.represents(draftRevision, checksum)` 表达“现有不可变版本是否已经代表当前 Draft”的领域判断。

## Execution Contract

```text
current editor definition
    -> normalize
    -> TaskVersionSnapshot(version = 0)
    -> shared Task Runtime
    -> DevelopmentTaskExecution history
```

Editor Run 不读取或写入 Published Revision，不修改 Task Catalog projection。运行历史记录与共享 Runtime execution evidence 是相邻事实，不互相替代。

## Dataset / Data Service Contract

DATASET 和 DATA_SERVICE 都是 Output Node，但各自拥有独立输出定义生命周期。

```text
Dataset definition     -> Dataset Version / Dataset runtime boundary
Data Service definition -> Data Service Revision / Runtime boundary
```

它们与普通 Task 的关系是相邻能力，不是继承关系。不能为了复用 SQL、发布或 Service，把 Output Node 重新塞回 executable Task lifecycle。

Data Service Definition 可以自己持有发布不变量，例如 `validatePublishable()`；这些规则属于 Data Service definition truth，而不是通用 Task Plugin truth。

## Lineage Boundary

```text
SQL DevelopmentTaskRevision
    -> durable outbox
    -> parser / analyzer
    -> lineage evidence
    -> yak-ops-business-lineage
```

Data Development 可以持有 Parser、Analyzer、Outbox、Worker 等证据产生角色，但不得复制 Lineage Asset / Relation 的领域所有权。

发布事务与 lineage projection 解耦：Lineage 失败不能回滚已成功发布的 Revision。Worker 只对最新 Revision 执行 replacement，避免旧 Revision 覆盖新证据。

## Architecture Boundary

长期业务子系统固定为：

```text
Node
Directory
Task Authoring
Execution
Dataset
Release
Editor
Lineage evidence
Persistence boundaries
```

新业务角色进入对应 package。`domain` 只保留 truth-bearing model / value object；读侧 projection 跟随所属业务 package。`service` 目录仅是冻结 legacy island，不是业务层；其固定文件集合以 `ARCHITECTURE.md` 和架构测试为准。

任何结构调整必须满足：

- 不新增第二套 Draft / Revision / Execution truth；
- 不把 Task Catalog 变成 Revision owner；
- 不把 Output Node 重新变成 executable Task；
- 不把 Lineage graph truth 搬回 Data Development；
- 不把 API/query projection 堆进 `domain`；
- 不通过 `service/common/helper/utils` 隐藏职责；
- 不通过扩大 dependency whitelist 掩盖真实架构循环。

## 修改代码前后

涉及领域行为的修改，先写：

```text
Domain Impact Analysis
- Aggregate / truth owner:
- Draft / Revision / Execution impact:
- Invariant impact:
- Runtime / Catalog / Lineage boundary impact:
- Domain Gap: yes/no
```

结构调整完成后确认：

```text
Domain Compliance Report
- Rule preserved/implemented:
- Behavior compatibility:
- Tests:
- Known gaps:
```

## 自动护栏

长期 contract 由 architecture tests 固化：

```text
DataDevelopmentArchitectureDocumentationTest
DataDevelopmentDependencyBoundaryTest
DataDevelopmentRoleConventionTest
DataDevelopmentDomainModelPlacementTest
```

行为测试继续保护 resource-name invariants、Draft concurrency、Publish、Editor Run、Dataset、Data Service 和 Lineage parsing/replacement 等现有语义。

**不要因为功能或结构调整被护栏拦住就删护栏。** 如果规则真的变化，同一个 PR 中同步修改 Requirement/Domain/Architecture/Dependencies/Review contract 与对应测试。
