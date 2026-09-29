# Datasource Requirements

> 本文件只描述**模块需要什么**，不描述怎么实现。历史需求和讨论看 Issue / PR / Git。

## 目标

Datasource 提供 Yak Ops 的统一数据源控制面：维护数据源配置、验证连接、访问 Catalog 元数据、提供 SQL Execution，并通过标准 Plugin API 扩展不同数据源。

## 核心能力

- 创建、编辑、查询、删除数据源。
- 管理名称、类型、环境、备注和连接配置。
- 支持已保存数据源和未保存配置的连接测试。
- 记录已保存配置最近一次连接测试结果：`UNKNOWN / CONNECTED / DISCONNECTED`。
- 提供数据库、Schema、表、字段、预览、统计、SQL 模板和变量解析。
- Catalog Business 链路和 Plugin SPI 均使用 typed request / metadata / result，不以 Map key 扩展业务语义。
- 提供统一 SQL Execution，支持单语句、多语句、事务、取消、超时和终态观测。
- Plugin 通过 versioned `DataSourcePluginDescriptor` 声明表单、Secret 字段和 Capability。
- Plugin API 不依赖 Business DTO / VO；HTTP PluginConfig 由 Business 投影。
- 对连接 Secret 做合并、脱敏和安全输出。

## 数据源生命周期

```text
Create -> normalize -> DataSourceDefinition / ConnectionProfile -> UNKNOWN
Update -> dbType unchanged -> replace ConnectionProfile -> UNKNOWN
Test saved -> success CONNECTED / connectivity failure DISCONNECTED
Test invalid saved config -> keep previous status
Test unsaved -> validation only -> no persisted state
```

配置可解析不等于连接成功；配置变化后旧连接结果失效。

## 对外事件契约

Create / Update / Delete 三条命令在事务内发布 `api.DataSourceChangedEvent(dataSourceId, dbType, name, changeType)`，
`changeType` 为 `CREATED / UPDATED / DELETED`；事件只带标识与非敏感描述，连接参数与凭证不进事件。

- 下游订阅必须使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`，普通 `@EventListener` 会看到回滚前状态。
- 数据源模块自身的 Catalog 缓存监听器是模板实现：`CREATED` 不清缓存，`UPDATED / DELETED` 按 `dataSourceId` 失效。
- 契约本身只加字段、不改语义；下游按需订阅，数据源模块不强制任何模块消费。

## Catalog

```text
HTTP compatibility Map
  -> typed CatalogReadRequest
  -> read-only validation
  -> DataSourceCatalogGateway
  -> typed Plugin Catalog request
  -> Plugin Catalog
```

- TABLE 模式必须有 `table_path`；SQL 模式必须有 `query`。
- preview / count / describe 的 SQL 模式只允许单条只读 SELECT。
- HTTP 历史 alias 和 `paramsList` 继续兼容，但 Map 只能停在 HTTP compatibility boundary。
- Plugin Catalog SPI 不接受 `Map<String,Object>`。

## SQL Execution

```text
SqlExecutionRequest / SqlExecutionPlan (data-ops-core)
  -> policy
  -> SqlExecutionAggregate
  -> SqlExecutionGateway
  -> Datasource execution SPI
```

- `data-ops-core` 是 Request / Plan / Result / Snapshot 唯一公共 contract。
- 多 Statement 边界由调用方显式提供，不按分号自动拆分。
- `SINGLE_TRANSACTION` 必须在同一 Session 执行；不支持事务时快速失败。
- Cancel 为 best-effort 物理动作，但生命周期必须收敛终态。
- Statement timeout 必须提升为 execution `TIMED_OUT`。

## Plugin 标准

每个 `DataSourcePlugin` 必须提供：

```text
dbType
descriptor(apiVersion, capabilities, connectionForm)
parseConnection
testConnection
createCatalog
createSqlExecutor (声明 SQL_EXECUTION 时)
```

当前 Capability：

```text
CONNECTION_TEST
CATALOG_METADATA
CATALOG_READ
SQL_EXECUTION
TRANSACTIONS
SSH_TUNNEL
```

依赖关系：`TRANSACTIONS -> SQL_EXECUTION`，`CATALOG_READ -> CATALOG_METADATA`。详细规范见 `data-ops-plugins/data-ops-plugin-datasource/PLUGIN.md`。

当前 Plugin SPI v1 使用 Descriptor + typed Catalog contract。未来 source-level breaking change 必须提升 API version，并提供内置/第三方插件迁移计划；不得借 SPI 迁移连带改变 REST 或数据库结构。

## 安全要求

- Secret 不得通过异常、`toString()`、普通日志或未脱敏响应输出。
- HTTP 详情中的 JDBC 地址和连接 JSON 必须脱敏。
- 掩码、空值或缺失 Secret 可以沿用已保存值，掩码不得覆盖真实凭据。
- Secret 字段由 Plugin Descriptor Schema 声明，Business 不依赖 HTTP VO 判断 Secret。
- Dataset / Data Service / Analysis 等只读调用方不得绕过 SQL Policy。

## 模块边界

本模块负责 DataSource Domain、持久化、连接测试、typed Catalog、SQL Execution 生命周期、Plugin discovery/adapter；不负责实时/离线同步定义与运行时生命周期、任意 ETL 编排、血缘计算或第三方 Driver 部署。

## 兼容性要求

保持：

```text
REST API 路径和主要 JSON shape
yak_ops_data_source / Flyway
data-ops-core SQL Execution contract
Datasource Plugin SPI v1
MySQL / PostgreSQL / Oracle / Doris / 达梦 / Kingbase 内置插件行为
Task Plugin SQL execution provider
```

## 当前明确未解决

```text
DataSourceDefinition 物理命名清理
```

## 需求变更规则

本文件未描述的新能力或行为变化统一报告：

```text
Requirement Gap
```

先确认需求并更新本文件，再实现代码。Reviewer / AI 不得自行补需求。
