# Datasource Domain

> `REQUIREMENTS.md` 定义需要什么；`ARCHITECTURE.md` 定义职责归属；本文件定义**业务事实和不变量**。无法用当前模型表达需求时报告 `Domain Gap`。

## 核心模型

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
├── yak-ops-core Request / Plan / Result / Snapshot
└── SqlExecutionAggregate
```

## 12 条硬规则

1. **`DataSourceDefinition` 是已保存数据源配置唯一业务事实。** DTO、VO、PO、Plugin SPI Model 不得替代 Domain。
2. **`ConnectionProfile` 是连接配置值对象。** `dbType` 创建后不可变；连接配置变化后状态回到 `UNKNOWN`。
3. **Core Domain 不依赖 Spring / MyBatis / HTTP DTO/VO/PO / Datasource Plugin SPI。**
4. **Aggregate 状态只能通过领域行为变化。** 不恢复 public setter；持久化重建使用 `restore(...)`。
5. **主要业务链路只依赖 Business Port。** Raw Plugin Registry/SPI、DAO/PO 不进入 management/query/connection/catalog。
6. **Business Gateway Contract 不暴露 SPI、HTTP DTO/VO 或 PO。** 转换和异常映射在 Adapter。
7. **Catalog Business 与 Plugin Catalog 使用 typed model。** HTTP Map 只在 Controller compatibility boundary 解析一次。
8. **Catalog Metadata 是 Business-owned Domain。** SPI metadata 必须经 Adapter 转为 `CatalogTable / CatalogColumn / CatalogQueryResult`。
9. **Plugin Descriptor/Capability 是插件能力事实。** `TRANSACTIONS -> SQL_EXECUTION`；`CATALOG_READ -> CATALOG_METADATA`；不以异常探测正常能力。
10. **`SqlExecutionAggregate` 是 SQL execution lifecycle truth。** Runtime 不维护第二套状态机，Aggregate 不持有线程/Future/physical Session。
11. **物理 SQL 只经 `SqlExecutionGateway.Session`。** Transaction/cancel/close 保持同一物理 Session 语义。
12. **无法表达需求就是 `Domain Gap`。** 不通过隐藏 Map key、临时 boolean、VO 字段或 SPI 私有字段绕过模型。

## DataSource 不变量

- `name / dbType / environment / ConnectionProfile.normalizedJson` 必须存在。
- `dbType` 不可变。
- ConnectionProfile 变化 -> `UNKNOWN`。
- 已保存连接测试成功 -> `CONNECTED`。
- 已保存配置发生真实连通失败 -> `DISCONNECTED`。
- 参数/配置校验失败不能覆盖已有连接状态。
- 未保存配置测试不持久化状态。
- Secret、完整连接 JSON、可能携带凭据的 JDBC URL 不进入普通日志或 `toString()`。

## 数据源状态图

```text
create / connection changed
          |
          v
       UNKNOWN
       /     \
 test ok   connectivity failure
    |             |
    v             v
CONNECTED    DISCONNECTED

invalid request/config -> status unchanged
unsaved test           -> no persisted transition
```

## Catalog 不变量

```text
HTTP Map
  -> CatalogReadRequest
  -> CatalogReadPolicy
  -> DataSourceCatalogReader
  -> DataSourceCatalogGateway
  -> typed Plugin Catalog SPI
```

- TABLE 必须有 `tablePath`；SQL 必须有 `sql/query`。
- preview / count / describe 的 SQL 模式只允许单条 SELECT。
- HTTP `paramsList` 只在兼容入口解析为 typed variable。
- Plugin SPI 不接受 `Map<String,Object>`。
- 新 Catalog 语义扩 typed Domain/SPI，不新增隐藏 key。

## Plugin Contract

```text
DataSourcePlugin
├── dbType
├── descriptor
│   ├── apiVersion
│   ├── capabilities
│   └── connectionForm
├── parseConnection
├── testConnection
├── createCatalog
└── createSqlExecutor
```

- `descriptor.dbType == plugin.dbType()`。
- 当前 `apiVersion = 1`。
- `TRANSACTIONS` 只能和 `SQL_EXECUTION` 一起声明。
- `CATALOG_READ` 只能和 `CATALOG_METADATA` 一起声明。
- Secret 字段通过 `FieldType.PASSWORD` 声明。
- Plugin 单例不持有请求级 Connection/Statement 状态。
- `plugin.DataSourcePluginRegistry` 只拥有 raw SPI discovery；Business read-side 通过 `query.DataSourcePluginReader -> DataSourcePluginGateway` 获取 Business Descriptor。
- HTTP PluginConfig 由 `DataSourcePluginViewMapper` 投影，不是 SPI 模型。

## Secret 规则

```text
Descriptor-aware connection JSON
  -> gateway.adapter.DataSourceSecretCodec

JDBC URL / user-facing text
  -> security.SensitiveTextMasker
```

- 掩码、空值、缺失 Secret 编辑时可复用 stored secret。
- Secret schema 由 Plugin Descriptor 推导，不从 HTTP VO 猜测。
- Error Handler 必须对用户可见错误文本做最终脱敏。

## SQL Execution 生命周期

```text
PENDING -> RUNNING -> CANCELLING
                   -> SUCCEEDED | FAILED | CANCELLED | TIMED_OUT
```

- 终态不能重新打开。
- 失败/取消/超时后未执行 Statement -> `SKIPPED`。
- Statement timeout -> Execution `TIMED_OUT`。
- `SINGLE_TRANSACTION` begin/execute/commit/rollback 使用同一 Session。
- physical cancel best-effort，但领域终态必须收敛。

## 持久化兼容

`yak_ops_data_source` 及 `jdbc_url / connection_params / original_json / conn_status` 是持久化投影，由 Repository Adapter 显式映射。

```text
PO -> DataSourceDefinition.restore(...)
Aggregate -> PO setters
```

PO setter 不等于 Domain setter；Aggregate 不因 ORM 方便重新变成贫血模型。

## 修改协议

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Truth owner:
- Domain Gap: yes/no

Domain Compliance Report
- Rule changed/implemented:
- Safety/tests:
- Known gaps:
```

规则真实变化时，同一 PR 更新 Requirements/Domain/Tests；不要通过删除护栏绕过失败。

## 已知独立 Gap

```text
DataSourceDefinition 物理命名清理
```
