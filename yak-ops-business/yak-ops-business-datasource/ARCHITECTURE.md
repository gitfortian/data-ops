# Datasource Architecture

> 本文是 `yak-ops-business-datasource` 的长期架构契约，描述当前代码应如何组织、职责归谁以及依赖如何流动。历史迁移过程看 Git / PR，不写回生产架构。跨模块统一工程风格以仓库根目录 [`CODE_STYLE.md`](../../CODE_STYLE.md) 为准。

## 1. 模块定位

Datasource 是 Yak Ops 的统一数据源控制面，负责：

- 数据源定义的创建、编辑、查询、删除；
- ConnectionProfile 解析、Secret 合并与连接探测；
- typed Catalog 元数据、预览、统计和 SQL 模板；
- Datasource Plugin 发现、Descriptor/Capability 适配；
- SQL Execution runtime、事务、取消、超时与审计；
- 数据源持久化和对外稳定引用。

不负责：实时/离线同步任务调度、Flink 生命周期、任意 ETL 编排、血缘计算、Driver 部署。

## 2. 设计原则

1. **Package 表达能力，Class 表达角色。** 代码按 management/query/connection/catalog/plugin/execution 等问题域组织，不回到 `controller -> service -> serviceImpl -> dao` 的默认三层模板。
2. **Domain 是业务事实，DTO/VO/PO/SPI 都是边界表示。** `DataSourceDefinition`、`ConnectionProfile`、Catalog Domain、`SqlExecutionAggregate` 不能被传输或持久化模型替代。
3. **外部能力走 Port/Adapter。** Application/Runtime 依赖 `Gateway` / `Repository`，Plugin SPI、MyBatis 和物理 SQL 实现在边界后面。
4. **读写角色分开。** `Manager` 处理生命周期命令，`Reader` 处理 read-side；连接、Catalog、Plugin、Execution 各自拥有专门角色。
5. **依赖图必须无环。** 不用 package 白名单掩盖反向引用；发现循环时重新归属职责。
6. **兼容协议只停在边界。** HTTP 的 DTO/VO/历史 `Map<String,Object>` alias 不能进入 Domain/Gateway。
7. **结构重构默认不改行为。** API、数据库、Plugin SPI、状态语义如需变化，必须按 Requirements/Domain 先定义。
8. **统一工程风格不在模块重复定义。** 通用 Role Vocabulary、Spring stereotype、method/test/comment 规则遵循根目录 `CODE_STYLE.md`；本文件只补充 Datasource 特有架构约束。

## 3. 最终包结构

```text
datasource
├── controller/v1
│   └── mapper                 # HTTP DTO/VO 与 typed input/domain projection
├── management                # DataSource aggregate command lifecycle
├── query                     # DataSource / Plugin read-side roles
├── connection                # connection resolve / probe
├── catalog                   # catalog reader / policy / matcher
├── plugin                    # raw Plugin discovery/registry boundary
├── execution
│   ├── domain                # SQL execution aggregate
│   ├── audit                 # observer/store/read-side
│   └── adapter               # outward execution SPI adapter
├── gateway
│   └── adapter               # Business Port <-> Datasource Plugin SPI
├── repository                # Domain Repository + persistence adapter
├── dao                       # MyBatis persistence implementation
├── domain
│   ├── catalog
│   └── plugin
├── security                  # reusable secret/text safety role
├── config
└── exception
```

生产代码不创建 `service / common / helper / utils / util / base` 这类模糊业务桶。

## 4. Datasource 角色语义

仓库级角色定义以根目录 `CODE_STYLE.md` 为准。Datasource 当前使用：

| Role | 本模块中的责任 |
| --- | --- |
| `Manager` | 数据源聚合生命周期、Command 编排和事务边界 |
| `Reader` | DataSource / Plugin / Audit read-side，不承担 command transition |
| `Resolver` | 将原始连接配置解析成可使用的业务对象 |
| `Tester` | 数据源连接探测并收敛 saved/unsaved test 语义 |
| `Policy` | Catalog/SQL 安全与业务决策 |
| `Matcher` | Catalog 表名匹配算法 |
| `Registry` | Datasource Plugin 发现、注册和按类型查找 |
| `Runtime` | SQL 执行生命周期、并发、事务、取消、超时编排 |
| `Observer` | SQL 执行旁路观测与审计写入 |
| `Gateway` | Business-owned 外部能力 Port |
| `Repository` | Domain 持久化 Port |
| `Adapter` | SPI / Persistence / outward contract 翻译 |
| `Mapper` | HTTP DTO/VO 边界转换 |
| `Codec/Masker` | 窄的 Secret/文本安全转换职责 |

当前 Datasource **没有** Service facade；Controller 直接进入明确的角色组件，这是当前架构的有意选择。若未来需要稳定 Application Service，必须同时更新 `DEPENDENCIES.md` 和 Architecture Guard，而不是默认增加 `XxxServiceImpl`。

## 5. 业务事实所有权

```text
DataSourceDefinition (Aggregate Root)
└── ConnectionProfile

Catalog Domain
├── CatalogReadRequest
├── CatalogTableQuery / CatalogTablePath
├── CatalogTable / CatalogColumn
└── CatalogQueryResult

Plugin Business Projection
└── DataSourcePluginDescriptor

SQL Execution
└── SqlExecutionAggregate
```

- `DataSourceDefinition`：已保存数据源配置的唯一业务事实。
- `ConnectionProfile`：规范化连接配置值对象。
- `connStatus`：当前已保存配置最近一次有效连接测试证据。
- Catalog Domain：Business-owned typed metadata/read contract。
- `DataSourcePluginDescriptor`：Plugin SPI Descriptor 的 Business 反腐投影。
- `SqlExecutionAggregate`：Execution / Statement 生命周期真相。
- DTO/VO、PO、SPI model、HTTP Map：只属于边界。

## 6. 核心流程

### 6.1 数据源命令

```text
HTTP DTO
  -> DataSourceRequestMapper
  -> DataSourceConfigurationCommand
  -> DataSourceManager
       -> DataSourceConnectionResolver -> DataSourcePluginGateway
       -> DataSourceDefinition
       -> DataSourceRepository
```

`DataSourceManager` 负责 create/update/delete，不负责 HTTP 解析、连接探测、Catalog 查询。

### 6.2 数据源查询

```text
Controller
  -> DataSourceReader
  -> DataSourceRepository
  -> Domain
  -> DataSourceViewMapper
  -> HTTP VO
```

Reader 不返回 HTTP VO，不依赖 DAO/PO。

### 6.3 连接解析与测试

```text
HTTP DTO
  -> DataSourceRequestMapper
  -> DataSourceConnectionRequest
  -> DataSourceConnectionTester
       -> DataSourceReader
       -> DataSourceConnectionResolver
       -> DataSourcePluginGateway
       -> DataSourceRepository (saved status only)
```

已保存配置成功才标 `CONNECTED`；真实连通失败标 `DISCONNECTED`；参数错误不覆盖已有状态；未保存配置测试不持久化状态。

### 6.4 Catalog

```text
HTTP compatibility Map
  -> CatalogRequestMapper
  -> CatalogReadRequest
  -> CatalogReadPolicy
  -> DataSourceCatalogReader
  -> DataSourceCatalogGateway
  -> SpiDataSourceCatalogGateway
  -> Plugin Catalog SPI
```

Map 到 `controller` 为止。Reader/Gateway/SPI 均使用 typed contract；preview/count/describe 的 SQL 模式先通过只读 Policy。

### 6.5 Plugin 描述

```text
Controller
  -> DataSourcePluginReader (query)
  -> DataSourcePluginGateway
  -> SpiDataSourcePluginGateway
  -> DataSourcePluginRegistry
  -> Plugin SPI Descriptor
  -> Business DataSourcePluginDescriptor
  -> DataSourcePluginViewMapper
```

`plugin` package 只拥有 raw Plugin discovery/registry，不反向调用 Business Gateway。

### 6.6 SQL Execution

```text
SqlExecutionRequest / Plan (yak-ops-core)
  -> SqlExecutionPolicy
  -> SqlExecutionAggregate
  -> DefaultSqlExecutionRuntime
  -> SqlExecutionGateway
  -> SpiSqlExecutionGateway
  -> Datasource execution SPI
```

Runtime 负责线程、Future、事务 Session、取消和超时编排；Aggregate 拥有生命周期；Gateway 负责物理 SQL Port。

### 6.7 SQL Audit Read Side

```text
HTTP Audit DTO
  -> SqlExecutionAuditMapper
  -> SqlExecutionAuditCriteria
  -> SqlExecutionAuditReader
  -> SqlExecutionAuditDao
  -> typed audit projection
  -> SqlExecutionAuditMapper
  -> HTTP Audit VO
```

Audit Reader 是明确 observability read-side，可读取 DAO projection，但不接受/返回 HTTP DTO/VO。

## 7. 持久化边界

```text
Business Role
  -> DataSourceRepository
  -> DataSourceRepositoryAdapter
  -> DataSourceDao
  -> MyBatis Mapper / DataSourcePO
```

Repository contract 只暴露 Domain；PO/MyBatis 不进入 management/query/connection/catalog。

`yak_ops_data_source` 和现有 Flyway 历史保持兼容。`DataSourceDefinition.restore(...)` 是持久化重建聚合的显式入口，不重新开放 public setter。

## 8. 外向 Execution Adapter

`execution.adapter.BusinessDataSourceExecutionProvider` 是明确例外：它实现 Task Plugin 所需的 `DataSourceExecutionProvider`，因此可以直接接触 Datasource Plugin Registry/SPI。它是**外向 Adapter**，不是普通 Application 主链路；该例外不能扩散。

## 9. Secret 与错误边界

- `gateway.adapter.DataSourceSecretCodec` 只处理 Descriptor-owned connection JSON 的 Secret merge/mask。
- `security.SensitiveTextMasker` 处理 JDBC URL / 用户可见错误文本中的凭据。
- `DataSourceExceptionHandler` 不反向依赖 Controller class；Advice 通过 controller package 限定作用域。
- Secret 不进入普通日志、异常响应、`toString()` 或未脱敏 VO。

## 10. 依赖治理

完整矩阵见 `DEPENDENCIES.md`。核心方向：

```text
Controller
   ↓
Management / Query / Connection / Catalog / Execution
   ↓
Gateway / Repository
   ↓
Adapter / DAO / Plugin Registry
   ↓
External SPI / Persistence
```

Domain、Config、Security 等底层能力不向上反向依赖。`DataSourceDependencyBoundaryTest` 扫描真实 Java import 并校验矩阵和无环性。

## 11. 修改规则

提交前回答：

```text
1. 这个逻辑属于哪个业务能力 package？
2. 这个类的 role 是否符合根目录 CODE_STYLE.md？
3. 是否改变 Domain truth owner / lifecycle？
4. 是否引入新的 package edge？
5. 是否把 DTO/VO/PO/SPI/Map 泄漏进业务内部？
6. 是否需要同步更新 REQUIREMENTS / DOMAIN / ARCHITECTURE / DEPENDENCIES / tests？
```

无法在现有模型表达需求时报告 `Domain Gap`；需要新增依赖方向时先修改 `DEPENDENCIES.md`，不要先放宽测试。
