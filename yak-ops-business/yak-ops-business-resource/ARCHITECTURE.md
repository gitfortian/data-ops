# Yak Ops Resource Architecture

## 1. Architecture Goal

`yak-ops-business-resource` 按 Resource 领域子系统组织代码，而不是按通用 `Controller -> Service -> ServiceImpl -> DAO` 技术分层组织。

目标是让 package 本身回答：

- 谁管理 Namespace？
- 谁读写 Content？
- 谁拥有 Storage 路由？
- 谁把 Resource 解析给 Runtime？
- 谁传播 commit 后变更？
- 谁负责 Metadata persistence？

这不是为了删除 `Service` 这个单词，而是为了让角色名称和依赖方向表达真实职责。

## 2. Package Map

```text
io.yak.ops.business.resource
|
+-- controller
|   `-- v1
|       |-- ResourcesController
|       `-- mapper
|           |-- ResourceRequestMapper
|           `-- ResourceViewMapper
|
+-- namespace
|   |-- ResourceNamespaceManager
|   |-- ResourceNamespaceReader
|   |-- ResourceTreeReader
|   |-- ResourceParentResolver
|   |-- ResourceNamePolicy
|   `-- ResourceNamespaceCommand
|
+-- content
|   |-- ResourceContentManager
|   |-- ResourceContentReader
|   |-- ResourceContentPolicy
|   |-- ResourceChecksum
|   |-- ResourceBinarySource
|   `-- ResourceContentCommand
|
+-- storage
|   |-- ResourceStorageGateway
|   |-- StorageOperatorGatewayAdapter
|   |-- ResourceStorageRegistry
|   |-- ResourceStorageLifecycle
|   `-- ResourceStorageReader
|
+-- resolution
|   |-- ResourceDownloadProviderAdapter
|   `-- ResourceResolverAdapter
|
+-- sync
|   `-- ResourceChangeDispatcher
|
+-- repository
|   |-- ResourceRepository
|   `-- ResourceRepositoryAdapter
|
+-- dao
|   |-- ResourceDao
|   |-- impl
|   `-- mapper
|
+-- domain
|   |-- ResourceNode
|   |-- ResourceNodeFactory
|   |-- ResourcePath
|   |-- ResourceRevision
|   |-- ResourceContent
|   |-- ResourceDownload
|   |-- ResourceQuery
|   |-- ResourceTreeNode
|   `-- ResourceStoragePlugin
|
+-- config
`-- exception
```

## 3. Primary Corridors

### 3.1 HTTP command/read corridor

```text
HTTP Request
    -> ResourcesController
    -> ResourceRequestMapper
    -> Namespace / Content command or read role
    -> Domain result
    -> ResourceViewMapper
    -> HTTP VO
```

Controller 不允许直接进入 Repository、DAO、Mapper 或 `StorageOperator`。

### 3.2 Namespace command corridor

```text
ResourceNamespaceManager
    -> ResourceParentResolver / ResourceNamePolicy
    -> ResourceRepository
    -> ResourceStorageGateway
    -> ResourceStorageLifecycle
    -> ResourceChangeDispatcher
```

Namespace Manager 不读写文件字节内容；它只编排目录/路径/身份和与这些变更相关的 Storage object lifecycle。

### 3.3 Content command corridor

```text
ResourceContentManager
    -> ResourceNamespaceReader / ParentResolver / NamePolicy
    -> ResourceContentPolicy / ResourceChecksum
    -> ResourceStorageGateway
    -> ResourceRepository
    -> ResourceStorageLifecycle
    -> ResourceChangeDispatcher
```

Content Manager 不直接依赖 HTTP `MultipartFile`。Controller 使用 `ResourceBinarySource` 完成 transport adaptation。

### 3.4 Read corridor

```text
ResourceNamespaceReader -> ResourceRepository
ResourceTreeReader      -> ResourceRepository
ResourceContentReader   -> NamespaceReader + ResourceStorageGateway
ResourceStorageReader   -> ResourceStorageRegistry
```

Read role 不持有 mutation transaction。

### 3.5 Storage corridor

```text
Namespace / Content
        -> ResourceStorageGateway
        -> StorageOperatorGatewayAdapter
        -> ResourceStorageRegistry
        -> StorageOperator SPI
        -> Local / MinIO / HDFS / ...
```

`ResourceStorageGateway` 是 Resource business -> Storage SPI 的唯一业务能力入口。

Registry 负责：

- installed operator discovery；
- type -> operator binding；
- duplicate plugin detection；
- default storage selection；
- plugin domain view。

Adapter 负责：

- 把 Storage Plugin exception 转换为 Resource business exception；
- 隐藏 `StorageOperator` 实例；
- 暴露 Resource 需要的最小 create/write/open/move/delete contract。

### 3.6 Resolution corridor

```text
External Task Runtime
      -> ResourceResolver SPI
      -> ResourceResolverAdapter
      -> ResourceDownloadProvider SPI
      -> ResourceDownloadProviderAdapter
      -> NamespaceReader + ContentReader
      -> StorageGateway
```

Resolution 是跨模块 adapter，不允许直接读 DAO / Repository internal implementation。

### 3.7 Sync corridor

```text
Namespace / Content mutation
      -> DB commit
      -> ResourceChangeDispatcher
      -> ResourceFileSyncProvider SPI
```

Sync 是 after-commit side effect。Provider failure 不反向污染 Resource transaction。

## 4. Persistence Boundary

```text
Business Role
    -> ResourceRepository
    -> ResourceRepositoryAdapter
    -> ResourceDao
    -> ResourceMapper / MyBatis-Plus
    -> ResourcePO
    -> yak_ops_resource
```

约束：

- `ResourceRepository` 只暴露 Domain；
- Repository Adapter 是 PO/Domain translation owner；
- DAO 只处理 Persistence contract；
- `ResourcePO` 不得进入 Namespace / Content / Resolution / Controller；
- 当前单表查询继续使用 MyBatis-Plus；没有复杂 join 时不为“架构感”额外引入 XML SQL。

## 5. Truth Model

```text
Database / ResourceRepository
    owns identity + namespace + current revision metadata

Storage Plugin
    owns physical bytes

Resolution
    owns temporary materialization only

Sync Provider
    owns external projection only
```

任何新增功能都必须先回答“这份状态的 Owner 是谁”，再决定 package。

## 6. Consistency Workflows

### 6.1 Create directory / upload / online create

```text
validate
   -> Storage create/write
   -> Repository insert
       -> commit -> Sync
       -> insert failure -> Storage cleanup compensation
```

### 6.2 Rename / move

```text
validate
   -> Storage move
   -> update Resource + descendants metadata
       -> commit -> Sync
       -> metadata failure -> Storage rollback attempt
```

### 6.3 Delete

```text
Repository delete Resource + descendants
   -> commit
   -> Storage delete best-effort
   -> Sync best-effort
```

这些顺序不能由 Adapter 或 Controller 自行重排。

## 7. Role Vocabulary

| Role | Meaning |
|---|---|
| `Manager` | 一个业务子系统的 mutation lifecycle owner |
| `Reader` | 无副作用 read-side entry |
| `Resolver` | 把 identity/reference 解析为当前业务对象或运行时对象 |
| `Policy` | 可测试的业务约束/决策，不拥有持久状态 |
| `Gateway` | Resource-owned 外部能力 Port |
| `Adapter` | Resource contract 与外部 SPI/transport/persistence 模型之间翻译 |
| `Registry` | 已安装 plugin/capability 的运行时注册表 |
| `Lifecycle` | 补偿、after-commit 等外部生命周期动作 |
| `Dispatcher` | 把已完成业务变化分发给外部消费者 |
| `Repository` | Domain persistence Port |
| `Dao` | MyBatis/persistence data access |
| `Mapper` | HTTP 或 Persistence 边界转换，不承载业务决策 |
| `Command` | immutable mutation input，不是 HTTP DTO |

禁止把多个职责重新塞进 `XxxSupport`、`XxxHelper`、`XxxUtils` 或新的大 `XxxServiceImpl`。

## 8. Spring Stereotypes

Resource 当前使用：

- `@Component`：Manager / Reader / Policy / Resolver / Lifecycle / Dispatcher / SPI Adapter 等明确业务角色；
- `@Repository`：Repository Adapter / DAO persistence role；
- `@Configuration`：模块 wiring；
- `@RestController` / `@RestControllerAdvice`：HTTP boundary。

是否使用 `@Service` 不是架构目标。当前模块不需要用 `@Service` 来表达业务分层，角色名和 package 已经承担该语义。

## 9. Public vs Internal Contracts

### Stable external contracts

- `/api/v1/resources/**` HTTP contract；
- `StorageOperator` SPI；
- `ResourceResolver` SPI；
- `ResourceDownloadProvider` SPI；
- `ResourceFileSyncProvider` SPI。

### Resource-owned business contracts

- `ResourceRepository`；
- `ResourceStorageGateway`；
- Namespace / Content command/read roles；
- Domain value/read models。

外部模块优先依赖既有跨模块 SPI，不应直接绑定 Resource 内部 Manager 实现。

## 10. Architecture Governance

Stage 2 使用 executable tests 固化：

- top-level package dependency matrix；
- actual package graph acyclic；
- Controller/persistence/Storage SPI boundary；
- `namespace/content -> storage` 只走 ResourceStorageGateway/Lifecycle；
- `namespace/content -> sync` 只走 ResourceChangeDispatcher；
- `resolution -> Resource read-side` 的窄入口；
- Repository / DAO transport boundary；
- Domain framework isolation；
- broad bucket prohibition；
- role stereotype consistency；
- repository-wide CODE_STYLE single source。

架构文档与 executable guard 必须同步更新，不能只改其中一边。