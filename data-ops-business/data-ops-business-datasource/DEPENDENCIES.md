# Datasource Dependency Contract

> 本文定义 `io.yak.ops.business.datasource` 生产源码的顶层 package 依赖契约。目标不是画一个理想分层图，而是把当前允许的真实依赖固定成**无环有向图**。

## 1. 顶层依赖矩阵

`A -> B` 表示 A 可以 import B。未列出的内部 datasource edge 默认禁止。

```text
controller -> api, catalog, config, connection, domain, exception, execution, gateway, management, query
management -> api, config, connection, domain, exception, query, repository
connection -> config, domain, exception, gateway, query, repository
catalog -> api, config, domain, exception, gateway, query
query -> config, domain, exception, gateway, repository
execution -> config, dao, domain, exception, gateway, plugin, repository

gateway -> config, domain, exception, plugin, security
repository -> config, dao, domain
plugin -> config, exception
dao -> config
exception -> config, security
security -> config

api ->
config ->
domain ->
```

该图必须无环。新增 edge 前先判断职责是否放错 package；只有确实是长期架构关系时才修改矩阵。

`api` 是对本模块之外的契约 bucket（跨模块端口与对外事件，如 `DataSourceReferenceProvider`、`DataSourceChangedEvent`）。它自己不 import 任何内部 package，只能被别的 package 依赖，因此不会形成回边。

## 2. 主要方向

```text
HTTP / Controller
      ↓
Role Components
      ↓
Business Port
   ┌──┴─────────────┐
Repository        Gateway
   ↓                 ↓
DAO/PO       Adapter / Registry
                     ↓
                Plugin SPI
```

`domain` 和 `config` 不依赖上层业务 package。`security` 是窄的文本安全能力，不持有业务生命周期。

## 3. Controller corridor

Controller 可以组合明确角色和 mapper，但：

- 不依赖 DAO / PO / MyBatis；
- 不依赖 `gateway.adapter`；
- 不依赖 raw Datasource Plugin SPI；
- HTTP DTO/VO/Map 不向下层泄漏。

`controller -> gateway` 的当前合法用途是 `DataSourceViewMapper -> DataSourcePluginGateway` 做 Business-owned 脱敏投影，不允许 Controller 直接操作 physical plugin。

## 4. Application role corridor

以下角色属于主要业务链路：

```text
management
query
connection
catalog
execution (除 adapter 外)
```

它们不得 import：

```text
io.yak.ops.spi.datasource
gateway.adapter
DataSourcePluginRegistry
bean.po
MyBatis
```

例外：`execution.audit` 是 observability read-side，当前通过 `SqlExecutionAuditDao` 读取审计投影，因此 `execution -> dao` 是显式允许 edge；它不能扩散到普通 SQL Runtime。

## 5. Gateway / Plugin corridor

`gateway` 定义 Business Port；`gateway.adapter` 才能翻译 raw Datasource Plugin SPI。

```text
Role -> Gateway interface <- Adapter -> Plugin Registry / SPI
```

`plugin` package 只负责 raw Plugin discovery/registry，不能 import `gateway`，防止 `plugin <-> gateway` 环。

`query.DataSourcePluginReader` 是 Business read-side，因此位于 query 并通过 `DataSourcePluginGateway` 获取 Business Descriptor。

## 6. Execution outward adapter

`execution.adapter.BusinessDataSourceExecutionProvider` 是明确的外向 Adapter：

```text
Task Plugin SPI -> BusinessDataSourceExecutionProvider
                -> Repository + Plugin Registry
                -> DataSourceSqlExecutor
```

因此 `execution.adapter` 可以接触 Datasource Plugin Registry / raw execution SPI。该例外不得被 Runtime/Policy/Aggregate 复制。

## 7. Persistence corridor

```text
Role -> DataSourceRepository
     -> DataSourceRepositoryAdapter
     -> DataSourceDao
     -> Mapper / PO
```

- Repository contract 不暴露 PO/MyBatis/DTO/VO。
- DAO 不依赖 transport DTO/VO。
- `DataSourceDefinition.restore(...)` 是 PO -> Aggregate 的重建入口。

## 8. Exception / Security corridor

`DataSourceExceptionHandler` 通过 package string 限定 controller Advice，不 import Controller class，避免 `exception -> controller` 反向 edge。

```text
exception -> security.SensitiveTextMasker
 gateway  -> security.SensitiveTextMasker
security  -> config
```

Connection JSON 的 Descriptor-aware merge/mask 留在 `gateway.adapter.DataSourceSecretCodec`；通用用户可见文本脱敏留在 `security`。

## 9. 外部类型边界

Raw Datasource Plugin SPI 只允许出现在：

```text
plugin
 gateway.adapter
 execution.adapter
```

HTTP DTO/VO 只属于 controller boundary。`Map<String,Object>` 兼容 Catalog 请求只允许在 controller/controller.mapper。

PO/MyBatis 只属于 dao/repository persistence boundary；`execution.audit` 可读取 DAO 审计投影，但不把 PO 变成公共 contract。

## 10. `@Service` 与模糊 package

当前 Datasource 没有 `@Service` allowlist。

禁止新增生产顶层 bucket：

```text
service
common
helper
utils
util
base
```

如果未来真的需要稳定 Application Service，必须先修改 `ARCHITECTURE.md` 和本文件，再更新 Architecture Guard；不能直接新增 `XxxServiceImpl`。

## 11. 自动护栏

`DataSourceDependencyBoundaryTest` 扫描真实 Java import：

- 顶层 package edge 必须在矩阵内；
- 依赖图必须无环；
- broad bucket 不得出现；
- raw SPI / persistence / HTTP Map corridor 不得扩散；
- `@Service` 必须在明确 allowlist（当前为空）。

`DataSourceCodeStyleConventionTest` 负责源码风格层面的回退检查。

## 12. 变更协议

需要新增 package 或 edge 时：

```text
1. 说明长期职责和 owner
2. 验证不会形成 cycle
3. 更新 DEPENDENCIES.md
4. 更新 Architecture Guard
5. 同 PR 提交
```

不要先把测试矩阵放宽，再寻找理由。
