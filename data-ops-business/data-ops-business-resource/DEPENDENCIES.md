# Yak Ops Resource Dependencies

本文件定义 Resource 模块内部 package 依赖方向和高风险 corridor。实际 Java import graph 必须由 `ResourceDependencyBoundaryTest` 验证。

## 1. Top-Level Dependency Matrix

`io.yak.ops.business.resource.<package>` 的允许依赖如下：

| Source | Allowed Resource packages |
|---|---|
| `controller` | `config`, `content`, `domain`, `exception`, `namespace`, `storage` |
| `resolution` | `config`, `content`, `domain`, `namespace` |
| `content` | `config`, `domain`, `exception`, `namespace`, `repository`, `storage`, `sync` |
| `namespace` | `config`, `domain`, `exception`, `repository`, `storage`, `sync` |
| `storage` | `config`, `domain`, `exception` |
| `sync` | `config`, `domain` |
| `repository` | `config`, `dao`, `domain` |
| `domain` | none inside Resource |
| `exception` | none inside Resource |
| `dao` | none inside Resource |
| `config` | none inside Resource |

`controller/v1/mapper` 属于 `controller` top-level package。

## 2. Intended Direction

```text
controller
   +-> namespace
   +-> content
   +-> storage(read side)
   +-> domain mapping

resolution
   +-> namespace(read side)
   +-> content(read side)

content
   +-> namespace(read-side policies)
   +-> repository
   +-> storage
   +-> sync

namespace
   +-> repository
   +-> storage
   +-> sync

repository
   +-> dao
   +-> domain

storage
   +-> domain

sync
   +-> domain

domain / exception / dao / config
   -> no higher Resource package
```

实际图必须保持无环。

## 3. Narrow Corridors

### 3.1 Controller -> business roles

Controller 可以依赖：

```text
ResourceNamespaceManager
ResourceNamespaceReader
ResourceTreeReader
ResourceContentManager
ResourceContentReader
ResourceStorageReader
ResourceRequestMapper
ResourceViewMapper
```

Controller 不得依赖：

```text
ResourceRepository
ResourceRepositoryAdapter
ResourceDao
ResourceMapper
ResourceStorageGateway
ResourceStorageRegistry
StorageOperatorGatewayAdapter
StorageOperator
ResourceChangeDispatcher
```

HTTP Exception Advice 也属于 `controller` boundary，不属于业务 `exception` package。

## 4. Content -> Namespace

Content 子系统只允许使用 Namespace 的窄角色：

```text
ResourceNamespaceReader
ResourceParentResolver
ResourceNamePolicy
```

禁止 Content 直接调用 `ResourceNamespaceManager` 产生隐式复合 mutation。

## 5. Namespace / Content -> Storage

业务子系统只能通过 Resource-owned Storage contract：

```text
ResourceStorageGateway
ResourceStorageLifecycle
```

不得直接 import：

```text
io.yak.ops.spi.storage.StorageOperator
StoragePluginException
StorageObjectMetadata
```

Storage SPI translation 只允许存在于 `storage` package。

## 6. Namespace / Content -> Sync

唯一允许的 mutation -> sync corridor：

```text
ResourceChangeDispatcher
```

Namespace / Content 不直接调用 `ResourceFileSyncProvider`。

## 7. Resolution -> Resource

Resolution 只通过 read-side 获取当前 Resource：

```text
ResourceDownloadProviderAdapter
   -> ResourceNamespaceReader
   -> ResourceContentReader

ResourceResolverAdapter
   -> ResourceDownloadProvider SPI
```

禁止 Resolution 依赖：

```text
ResourceNamespaceManager
ResourceContentManager
ResourceRepository
ResourceDao
ResourceStorageGateway
```

它是消费 adapter，不是 Resource command corridor。

## 8. Repository -> DAO

```text
ResourceRepository
     ^
     |
ResourceRepositoryAdapter
     |
     v
ResourceDao -> ResourceMapper -> ResourcePO
```

- Repository contract 不暴露 DTO / VO / PO / MyBatis；
- DAO 不暴露 HTTP DTO/VO；
- Domain <-> PO 映射由 Repository Adapter 显式完成；
- Namespace/Content/Resolution 不允许 import DAO/PO。

## 9. Domain

`domain` 允许：

- JDK；
- Resource 稳定 shared enum/value（例如 `ResourceNodeType`, `ResourceStorageType`）。

`domain` 禁止：

- Spring；
- MyBatis / MyBatis-Plus；
- HTTP DTO / VO；
- Resource PO；
- StorageOperator；
- Controller / Manager / Repository implementation；
- Lombok `@Data` 作为领域模型语义替代。

## 10. Sync

`sync` 可以依赖 Domain 生成传播上下文，并依赖跨模块 `ResourceFileSyncProvider` SPI。

禁止：

- Sync 反向调用 Namespace/Content Manager 修改 Resource truth；
- Provider 实现被 Resource business role 直接感知；
- Sync failure 参与数据库事务回滚。

## 11. Config and Exception

`config` 只负责 wiring、properties、Flyway/MyBatis configuration，不承载 Resource business decision。

`exception` 只保留 Resource business exception 类型。HTTP exception translation 属于 Controller boundary，避免形成：

```text
controller -> domain/exception -> controller
```

这种 package cycle。

## 12. Forbidden Buckets

Production 下不得重新创建：

```text
service/
common/
helper/
utils/
util/
base/
persistence/
```

如果一个类无法放入 `namespace/content/storage/resolution/sync/repository/dao/domain/controller/config/exception`，应先重新定义其角色，而不是创建新的兜底包。

## 13. Change Rule

修改依赖方向时必须同时更新：

1. `ARCHITECTURE.md`；
2. 本文件；
3. `ResourceDependencyBoundaryTest`；
4. 对应行为测试。

不得为了让架构测试通过而无理由扩大 Allowed Matrix。