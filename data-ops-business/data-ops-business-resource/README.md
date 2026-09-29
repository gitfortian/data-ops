# Yak Ops Resource

文件资源模块负责 Resource Namespace、文件内容、存储插件路由、运行时资源解析，以及资源变更后的外部同步传播。

代码按业务子系统和明确角色组织，不以通用 `service/impl` 作为业务入口。

## Read First

开始修改 Resource 前建议按顺序阅读：

1. [`REQUIREMENTS.md`](./REQUIREMENTS.md)：必须长期保持的业务行为；
2. [`DOMAIN.md`](./DOMAIN.md)：Resource Truth Ownership、Revision 与一致性语义；
3. [`ARCHITECTURE.md`](./ARCHITECTURE.md)：package map、角色与主要调用 corridor；
4. [`DEPENDENCIES.md`](./DEPENDENCIES.md)：允许的依赖方向与 forbidden dependency；
5. [`REVIEW.md`](./REVIEW.md)：PR 评审和最终一致性检查清单。

## Architecture at a Glance

```text
Controller
   -> Namespace Manager / Reader
   -> Content Manager / Reader
   -> Storage Reader

Namespace / Content
   -> Resource Domain
   -> ResourceRepository -> DAO -> MyBatis
   -> ResourceStorageGateway -> StorageOperator SPI
   -> ResourceChangeDispatcher -> ResourceFileSyncProvider SPI

ResourceResolver SPI
   -> Resolution Adapter
   -> Namespace / Content Read Side
```

主要 package：

```text
resource
├── controller/v1/mapper
├── namespace
├── content
├── storage
├── resolution
├── sync
├── repository
├── dao
├── domain
├── config
└── exception
```

## Truth Ownership

```text
Database / ResourceRepository
    owns Resource identity / namespace / current revision metadata

Storage Plugin
    owns physical bytes

Resolution
    owns temporary materialization only

Resource Sync
    owns external propagation only
```

`ResourceNode` 和物理文件不是同一份 Truth。

`version` 表示当前 Resource Revision / fencing value，不是 historical version store。`version = 5` 不意味着 revision 1~4 仍然可以下载。

## Role Vocabulary

| Role | Responsibility |
|---|---|
| `Manager` | mutation lifecycle owner |
| `Reader` | read-side entry，无业务 mutation |
| `Resolver` | reference / runtime materialization |
| `Policy` | 可测试约束和业务决策 |
| `Gateway` | Resource-owned external capability Port |
| `Adapter` | transport / persistence / SPI 边界翻译 |
| `Registry` | installed plugin/capability registry |
| `Lifecycle` | compensation / after-commit lifecycle |
| `Dispatcher` | commit 后外部传播 |
| `Repository` | Domain persistence Port |
| `DAO` | MyBatis persistence access |
| `Mapper` | HTTP 或 Persistence 模型转换 |
| `Command` | immutable mutation input，不是 HTTP DTO |

新增代码不要重新创建 `Support/Helper/Utils/Common/Base/ServiceImpl` 兜底角色。

## Consistency Semantics

当前必须保持：

- 创建目录、上传、在线创建：先写物理 Storage，再写 Metadata；Metadata 创建失败时 best-effort 删除新 Storage Object；
- Move：先移动 Storage Object，再批量更新当前 Resource 和 descendants Metadata；Metadata 失败时 best-effort 回滚 Storage Path；
- 当前不支持 cross-storage move；
- Delete：先提交 Metadata 删除，再 after-commit 删除 Storage Object；
- Resource Sync 在 commit 后 best-effort 执行，Provider 失败不回滚 Resource Truth；
- Resource Resolver 物化到临时目录并校验 SHA-256；指定 version 时只允许当前 matching revision。

## Persistence

当前 Resource Metadata 只维护：

```text
yak_ops_resource
```

资源模块复用 `yak.database` 对应的 Business DataSource、MyBatis SessionFactory 和 TransactionManager，并继续拥有独立的：

```text
yak_resource_schema_history
```

Flyway 历史边界。

## Architecture Guards

Resource 架构由以下测试作为 executable contract：

```text
ResourceArchitectureDocumentationTest
ResourceDependencyBoundaryTest
ResourceLayeringConventionTest
ResourceCodeStyleConventionTest
ResourceRoleConventionTest
```

架构约束需要变更时，代码、`ARCHITECTURE.md`、`DEPENDENCIES.md` 和对应测试必须一起修改，而不是单独放宽测试。
