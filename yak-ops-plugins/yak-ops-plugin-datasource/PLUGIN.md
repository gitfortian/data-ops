# Datasource Plugin Contract

> 本文定义 Datasource Plugin 的**当前开发标准**。业务领域规则看 `yak-ops-business/yak-ops-business-datasource/DOMAIN.md`；历史演进看 PR / Git。

## 1. 模块职责

```text
yak-ops-plugin-datasource-api
  -> 稳定扩展协议，不依赖 Business DTO / VO

yak-ops-plugin-datasource-jdbc
  -> 通用 JDBC 基座 + MySQL/PostgreSQL/Oracle/达梦/Kingbase

yak-ops-plugin-datasource-doris
  -> Doris 专用扩展

yak-ops-plugin-datasource-all
  -> 聚合打包
```

Plugin 负责“如何连接和访问某类数据源”，Business Datasource 负责“数据源是什么、生命周期是什么、如何提供业务能力”。

## 2. 必须实现的稳定入口

每个插件实现 `DataSourcePlugin`：

```java
DataSourceDbType dbType();
DataSourcePluginDescriptor descriptor();
DataSourceConnection parseConnection(String connectionJson);
void testConnection(DataSourceConnection connection, int timeoutSeconds);
DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds);
```

支持 SQL 时覆盖：

```java
DataSourceSqlExecutor createSqlExecutor(
    DataSourceConnection connection,
    int connectionTimeoutSeconds);
```

插件必须通过：

```text
META-INF/services/io.yak.ops.spi.datasource.DataSourcePlugin
```

注册实现类。Business 通过 `ServiceLoader` 发现，不允许在业务代码硬编码具体插件类。

## 3. Descriptor 是唯一插件元数据协议

`DataSourcePluginDescriptor` 描述：

```text
dbType
apiVersion
capabilities
connectionForm
installRequired / installHint
```

硬规则：

- Plugin API 不返回 `DataSourcePluginConfigVO` 或其他 HTTP VO。
- Descriptor 必须 immutable。
- `descriptor.dbType == plugin.dbType()`。
- 当前 `apiVersion = 1`。
- 表单字段类型使用 `FieldType`，不靠特殊 key 猜组件类型。
- Secret 字段使用 `FieldType.PASSWORD` 声明，Business 由 Descriptor 推导脱敏字段。
- 前端 VO 由 Business `DataSourcePluginViewMapper` 投影，Plugin 不认识 Controller / VO。

## 4. Capability

当前标准能力：

```text
CONNECTION_TEST
CATALOG_METADATA
CATALOG_READ
SQL_EXECUTION
TRANSACTIONS
SSH_TUNNEL
```

依赖规则：

```text
TRANSACTIONS -> requires SQL_EXECUTION
CATALOG_READ -> requires CATALOG_METADATA
```

调用方必须先看 Capability，不以“调用后抛 UnsupportedOperationException”作为正常能力发现机制。

新增能力时：

1. 先确认 Business Requirement / Domain；
2. 扩 `DataSourceCapability`；
3. 明确依赖关系与降级行为；
4. 更新 Registry 校验、PLUGIN.md、contract tests；
5. 不通过插件私有 boolean 偷渡平台级能力。

## 5. Connection Contract

`parseConnection` 必须：

- 校验输入属于当前 `dbType`；
- 输出规范化 `DataSourceConnection`；
- 规范化 JSON 可持久化、可再次解析；
- 不在 `toString()` / 日志输出 Secret；
- 参数错误使用 `DataSourcePluginException(Operation.PARAMETER, ...)`。

连接测试错误使用：

```text
Operation.CONNECTIVITY
```

SQL 执行错误使用：

```text
Operation.EXECUTION
```

Catalog 错误使用：

```text
Operation.CATALOG
```

异常消息不得包含密码、Token、Private Key 等 Secret。

## 6. Secret 与表单

插件通过 Descriptor 表单 Schema 声明 PASSWORD 字段。Business 的 Secret merge / mask 只读取 Descriptor，不读取 HTTP VO。

通用规则：

- 用户提交 `******`、空值或缺失 Secret 时，可沿用已保存值；
- 新的明确 Secret 值覆盖旧值；
- `password / token / secret / privateKey / passphrase` 等常见 key 仍有兜底识别；
- SSH 等嵌套对象同样必须脱敏；
- 测试夹具使用明显的 `TEST_ONLY_*` 值，不放真实凭据。

## 7. Catalog Contract

`DataSourceCatalog` 已完全类型化：

```java
List<String> listDatabases();
List<String> listSchemas(String database);
List<DataSourceTable> listTables(DataSourceCatalogQuery query);
List<DataSourceColumn> listColumns(DataSourceTablePath tablePath);
List<DataSourceColumn> describe(DataSourceCatalogReadRequest request);
DataSourceQueryResult preview(DataSourceCatalogReadRequest request, int limit);
long count(DataSourceCatalogReadRequest request);
String buildSqlTemplate(String tablePath);
String resolveSql(String sql, DataSourceCatalogReadRequest request);
```

禁止在稳定 SPI 新增 `Map<String,Object>` 协议。新增 Catalog 语义应扩 typed request/model。

`DataSourceCatalogReadRequest` 只有两种模式：

```text
TABLE -> tablePath required
SQL   -> query required
```

变量通过 `variables` 显式传递。Business 的旧 HTTP `paramsList` 兼容解析不能扩散回 Plugin SPI。

## 8. SQL Executor Contract

一个 `DataSourceSqlExecutor` 表示一次物理执行 Session：

- `execute` 执行 SQL；
- `cancel` 为 best-effort；
- `close` 必须安全释放资源；
- 声明 `TRANSACTIONS` 时必须支持 begin / commit / rollback；
- transaction 内多个 statement 必须使用同一物理连接；
- timeout / cancel / failure 不得吞异常。

Business SQL Runtime 通过 `SqlExecutionGateway` 访问该 SPI，插件模型不得进入 Runtime Domain。

## 9. Thread Safety / 生命周期

- `DataSourcePlugin` 可被 Registry 单例复用，应保持无请求级可变状态。
- `DataSourceCatalog` 可以按调用创建，不假设跨线程共享。
- `DataSourceSqlExecutor` 是一次 Session，不要求跨线程复用。
- JDBC Connection / Statement 必须在完成、失败、取消路径正确关闭。
- 不允许把正在执行的 Connection / Statement 缓存在 Plugin 单例字段。

## 10. 新增插件步骤

```text
1. 确认 DataSourceDbType
2. 实现 DataSourcePlugin
3. 定义 descriptor + capabilities + connection form
4. 实现 parseConnection / testConnection
5. 按能力实现 Catalog / SQL Executor
6. 注册 META-INF/services
7. 增加 descriptor contract test
8. 增加 connection normalize / invalid input test
9. 增加 Catalog typed request test（如支持）
10. 增加 SQL transaction / cancel test（如声明能力）
```

如果新插件需要平台当前没有的业务概念，先报告 `Requirement Gap / Domain Gap`，不要直接把私有字段扩成全局约定。

## 11. Phase 4 SPI 迁移

Phase 4 有意做一次 source-level SPI breaking change，仓库内置插件已同步迁移：

```text
pluginConfig() -> descriptor()
Catalog Map request -> DataSourceCatalogReadRequest
```

第三方插件升级步骤：

```text
1. 将 DataSourcePluginConfigVO 转为 DataSourcePluginDescriptor
2. 声明真实 Capability
3. 将 PASSWORD 字段保留为 FieldType.PASSWORD
4. 将 Catalog Map 解析改为 DataSourceCatalogReadRequest
5. 更新 apiVersion = CURRENT_API_VERSION
6. 运行 contract tests
```

REST API、Business DTO / VO、数据库表结构不因该 SPI 迁移改变。

## 12. Review Checklist

```text
[ ] Plugin API 无 Business DTO / VO 依赖
[ ] descriptor dbType / apiVersion 正确
[ ] Capability 与真实实现一致
[ ] Secret 字段可被 Descriptor 识别
[ ] Catalog 无 Map 协议
[ ] 异常 Operation 分类正确且不泄露 Secret
[ ] ServiceLoader 注册存在
[ ] transaction / cancel / close 与 Capability 一致
[ ] 内置/新增插件 contract tests 通过
```
