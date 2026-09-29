# Lineage Review Guide

Lineage PR 优先评审领域语义、角色边界、公共 API、依赖方向和 runtime owner，而不是只看能否编译。

## Scope

- 结构调整是否偷改 REST、DB、Flyway 或领域语义？
- package move、角色拆分和行为变化能否分开？
- 新抽象是否有真实调用方？

## Domain

- Asset identity 是否保持统一？
- Relation 是否始终表达 upstream source -> downstream target？
- Evidence 是否只说明来源？
- replacement / revision ordering 是否保守且并发安全？
- Draft 是否继续停留在内部写入过程？

## Roles

- `@Service` 是否只用于 Query、Registration、Maintenance 三个稳定 facade？
- Reader、Registrar、Coordinator、Guard 是否停在自己的角色 package？
- Analyzer、Converter、RepositoryAdapter、DAO 是否边界明确？
- 是否重新创建了 `service/common/helper/utils/base` 大桶？
- 是否创建没有真实调用链的 Collector 或技术栈平行业务层？

## Public API

跨模块只允许依赖 Analyzer、稳定 Domain 类型和三个角色 facade。Controller、Config、Repository、DAO、PO 与 Draft 不得成为外部编译 contract。新增公共类型必须同步更新 `ARCHITECTURE.md`、`DEPENDENCIES.md` 和 `LineagePublicApiBoundaryTest`。

## Dependencies

- Controller 是否只进入 Query/Registration facade 与 Domain？
- Query/Registration/Maintenance 是否绕过 Repository 直接进入 DAO/Mapper/PO/Config？
- Domain 是否出现 Spring、MyBatis 或平台 SDK？
- package 图是否与声明精确一致且无环？
- Datasource import 是否仍停留在两个 config corridor 文件？

## Persistence

Domain 与 PO 转换停在 RepositoryAdapter；Mapper/XML 只承担数据库 primitive；replacement/revision 的业务安全不能下沉为 SQL 偶然行为。

## Maven

Driver、Flyway database extension、JSqlParser 等 runtime 能力应由真正的装配 owner 持有；不因为目录美观提前拆 Maven artifact。

## Compatibility

纯角色重构默认保持：

```text
REST contract unchanged
DB schema unchanged
Flyway unchanged
Asset / Relation semantics unchanged
transaction boundary unchanged
existing behavior tests unchanged or strengthened
```

## Tests

至少检查 `LineageArchitectureTest`、`LineageDependencyBoundaryTest`、`LineageMavenDependencyBoundaryTest`、`LineagePublicApiBoundaryTest`、`LineageCodeStyleConventionTest`、`LineageDocumentationContractTest` 和受影响行为测试。

## Reject Signals

- 为让测试通过扩大 package/public API/dependency 白名单；
- 把内部组件重新命名成一批 `*Service`；
- 保留旧 `service` compatibility wrapper 形成双入口；
- Controller 直接操作 Repository/DAO；
- Facade 公开方法暴露 Repository、DAO、Config、PO 或 Draft；
- Domain 携带 Mapper、PO、HTTP DTO 或平台 SDK；
- 为 Flink/Spark/Hadoop 提前复制平行业务层；
- 一个 PR 同时大规模改 package、表结构、REST 和领域行为。
