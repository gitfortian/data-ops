# Yak Ops Datasource

Datasource 是 Yak Ops 的统一数据源控制面：管理数据源定义与连接，提供 typed Catalog、Plugin 能力适配和 SQL Execution，并向同步、数据开发、任务等模块提供稳定数据源引用。

## 开发前必读

本模块遵循仓库根目录统一 [`CODE_STYLE.md`](../../CODE_STYLE.md)，模块只维护自身需求、领域、架构、依赖与 Review contract。

```text
REQUIREMENTS.md      -> 模块需要什么
ARCHITECTURE.md      -> 代码如何组织、职责归谁
DEPENDENCIES.md      -> package 允许怎么依赖
DOMAIN.md            -> 业务事实和不变量
MIGRATIONS.md        -> Datasource 独立 Flyway namespace 与一期 V1 baseline
../../CODE_STYLE.md  -> Yak Ops 仓库统一工程与代码规范
REVIEW.md            -> 按什么标准 Review
PLUGIN.md            -> Datasource Plugin 开发标准（插件模块）
```

Plugin 文档：`yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN.md`。

## 当前架构

```text
Controller / Mapper
       ↓
┌──────────────────────────────────────────┐
│ Management │ Query │ Connection │ Catalog│
└──────────────────────────────────────────┘
       ↓                     ↓
 Repository               Gateway
       ↓                     ↓
 DAO / PO          Adapter -> Plugin Registry / SPI

SQL Execution
  -> Policy -> Aggregate -> Runtime -> SqlExecutionGateway
```

Datasource 当前没有泛化 Service 层；Controller 直接调用 `Manager / Reader / Tester / CatalogReader` 等明确角色。`Service` 只在未来确实需要稳定 Application Facade 时引入，不作为默认模板。

## 核心角色

```text
management/DataSourceManager
  -> create / update / delete

query/DataSourceReader
  -> detail / page / summary / options

query/DataSourcePluginReader
  -> Business plugin descriptor read-side

connection/DataSourceConnectionResolver
connection/DataSourceConnectionTester

catalog/DataSourceCatalogReader
catalog/CatalogReadPolicy
catalog/CatalogTableMatcher

plugin/DataSourcePluginRegistry
  -> raw plugin discovery only

execution/DefaultSqlExecutionRuntime
execution/audit/*

gateway/DataSourcePluginGateway
gateway/DataSourceCatalogGateway
gateway/SqlExecutionGateway
```

## 核心模型

```text
DataSourceDefinition (Aggregate Root)
└── ConnectionProfile

Catalog Domain
├── CatalogReadRequest
├── CatalogTable / CatalogColumn
└── CatalogQueryResult

Plugin Business Projection
└── DataSourcePluginDescriptor

SQL Execution
└── SqlExecutionAggregate
```

DTO/VO、PO、Plugin SPI model、HTTP Map 都不是 Domain。

## 数据源生命周期

```text
Create / connection config changed
             ↓
           UNKNOWN
          /       \
saved test ok   connectivity failure
       ↓              ↓
  CONNECTED      DISCONNECTED
```

参数错误不覆盖已有连接状态；未保存配置测试不持久化状态。

## Catalog

```text
HTTP Map
  -> CatalogRequestMapper
  -> CatalogReadRequest
  -> CatalogReadPolicy
  -> DataSourceCatalogReader
  -> DataSourceCatalogGateway
  -> Plugin Catalog SPI
```

历史 Map/alias 仅用于 REST 兼容，到 Controller Mapper 为止。Business Gateway 和 Plugin Catalog 使用 typed model。SQL preview/count/describe 先通过单条 SELECT 只读校验。

## Plugin

```text
DataSourcePluginReader
  -> DataSourcePluginGateway
  -> SpiDataSourcePluginGateway
  -> DataSourcePluginRegistry
  -> DataSourcePlugin.descriptor()
```

Registry 加载并校验 `apiVersion / dbType / capabilities`；Business Gateway 将 SPI Descriptor 转成 Business-owned `DataSourcePluginDescriptor`，HTTP VO 再由 `DataSourcePluginViewMapper` 投影。

## SQL Execution

```text
SqlExecutionRequest / Plan (yak-ops-core)
  -> SqlExecutionPolicy
  -> SqlExecutionAggregate
  -> DefaultSqlExecutionRuntime
  -> SqlExecutionGateway
  -> Datasource execution SPI
```

Aggregate 管生命周期，Runtime 管并发/事务/取消/超时，Gateway 管物理 Session。

## Secret

- Descriptor-aware connection JSON merge/mask：`gateway.adapter.DataSourceSecretCodec`。
- JDBC URL / 用户可见错误文本：`security.SensitiveTextMasker`。
- Secret 不进入普通日志、`toString()` 或未脱敏响应。

## 持久化

```text
DataSourceRepository
  -> DataSourceRepositoryAdapter
  -> DataSourceDao
  -> MyBatis Mapper / DataSourcePO
```

`DataSourceDefinition.restore(...)` 负责持久化重建；Aggregate 不开放 public setter。

Flyway 只扫描 Datasource 自己的 `db/migration/yak-datasource`，并使用 `yak_datasource_schema_history`；Data Service 使用自己的 `yak-data-service` namespace，两个模块不共享 migration version sequence。详细规则见 `MIGRATIONS.md`。

## 架构护栏

测试会检查：

```text
Domain boundary
Gateway contract
Top-level dependency matrix + acyclic graph
raw SPI / HTTP Map / persistence corridor
Flyway namespace / first-release baseline
no broad service/common/helper/utils/util/base package
no default @Service / ServiceImpl
repository-level code-style regressions
```

仓库统一风格以根目录 `CODE_STYLE.md` 为准；Datasource 专属依赖与角色规则由 `ARCHITECTURE.md / DEPENDENCIES.md` 和 Architecture Guard 保护。规则变化时文档和测试同一 PR 修改，不通过删除/放宽测试绕过。

## 兼容边界

保持：

```text
REST API 路径和主要 JSON shape
yak_ops_data_source 当前表语义
yak-ops-core SQL Execution contract
Datasource Plugin SPI v1 contract
内置数据库插件运行行为
Task Plugin SQL execution provider
```

当前仍处于第一期，Flyway 开发期增量已 squash 为 `V1__baseline_datasource.sql`；正式发布后 migration 才进入不可变兼容期。

未来 Plugin SPI breaking change 必须通过新 API version + migration plan，不连带修改 REST/DB。

## 已知独立 Gap

```text
DataSourceDefinition 物理命名清理
```
