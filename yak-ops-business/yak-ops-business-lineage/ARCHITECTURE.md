# Lineage Architecture

本文定义 Lineage 当前长期 contract：package 表达业务角色，稳定 facade 作为应用入口，专业能力使用 Reader、Registrar、Coordinator、Guard 等名称。

## Design Principles

1. **Package expresses architecture.** 不建立通用 `service` 业务大桶。
2. **Stable facade, specialized roles.** `@Service` 只用于跨 HTTP / module caller 的稳定入口。
3. **Transaction stays at the facade.** 内部角色组件不扩散事务边界。
4. **One lineage model.** Dataset、SQL Task、未来平台来源复用统一 Asset / Relation。
5. **External technology stops at boundary.** Parser、SDK、MyBatis、PO 不穿透 Domain。
6. **Active structure is exact.** 只为真实存在的 package 和依赖建白名单。
7. **Architecture is executable.** Package、公共 API、Maven、角色和文档都有测试保护。

## Active Package Map

```text
io.yak.ops.business.lineage
├── analysis
│   └── sql
├── config
├── controller
│   └── v1
├── dao
├── domain
├── query
│   ├── LineageQueryService
│   ├── LineageAssetReader
│   ├── LineageGraphReader
│   └── LineageOverviewReader
├── registration
│   ├── LineageRegistrationService
│   ├── LineageAssetRegistrar
│   ├── LineageRelationRegistrar
│   └── LineageRegistrationDraftFactory
├── maintenance
│   ├── LineageMaintenanceService
│   ├── LineageEvidenceReplacementCoordinator
│   └── LineageRevisionGuard
└── repository
```

`service / common / helper / utils / base` 不允许成为顶层业务 package。

## Application Roles

### Query

`LineageQueryService` 只保留只读事务与稳定入口；`LineageAssetReader` 负责资产定位/搜索，`LineageGraphReader` 负责有界图遍历，`LineageOverviewReader` 负责首页等轻量消费者需要的数量统计和有界最近关系读取。

### Registration

`LineageRegistrationService` 保留注册事务；`LineageAssetRegistrar` 和 `LineageRelationRegistrar` 负责写入及批次去重，`LineageRegistrationDraftFactory` 负责校验、归一化和 Draft 构造。

### Maintenance

`LineageMaintenanceService` 保留维护事务；`LineageEvidenceReplacementCoordinator` 负责 evidence replacement/清理，`LineageRevisionGuard` 负责 revision 并发保护。

内部角色使用 `@Component` 或普通对象，不用更多 `*Service` 掩盖职责。

## Public API Boundary

可跨模块编译依赖的类型根为：

```text
analysis.sql.SqlProjectionLineageAnalyzer

domain.LineageAsset
domain.LineageAssetType
domain.LineageDirection
domain.LineageGraph
domain.LineageRelation
domain.LineageRelationType

query.LineageQueryService
registration.LineageRegistrationService
maintenance.LineageMaintenanceService
```

Controller、Config、Repository、DAO、PO、`LineageAssetDraft` 与 `LineageRelationDraft` 不属于公共 contract。调用方继续通过自身 Gateway/Adapter 隔离 Lineage。

## Domain Boundary

`domain/` 不依赖 Spring MVC、Spring Service、Repository、DAO、MyBatis、PO、具体 SQL parser 或平台 SDK。角色拆分不改变 Asset、Relation、Evidence、Graph 语义。

## Analysis Boundary

`analysis/sql/SqlProjectionLineageAnalyzer` 是 source-neutral contract。Data Development 持有具体 parser 实现，Dataset 通过自身 Gateway Adapter 消费；Lineage 不反向依赖 parser 实现。

## Persistence Boundary

```text
role facade
    ↓
role component
    ↓
LineageRepository / LineageOverviewRepository
    ↓
LineageRepositoryAdapter
    ↓
LineageDao
    ↓
Mapper / PO / DB
```

Facade 和内部角色不直接访问 DAO、Mapper、PO 或 persistence config。RepositoryAdapter 是 Domain 与 Persistence 的转换位置；`LineageOverviewRepository` 只承载轻量统计和最近关系投影，不成为跨模块 contract。

## Persistence Configuration Corridor

Datasource 只允许从两个 config 文件进入：

```text
ConditionalOnLineagePersistence
    -> ConditionalOnDataSourceEnabled

LineagePersistenceConfiguration
    -> BusinessDatabaseConfiguration
    -> DataSourceProperties
```

DAO 只感知 Lineage-owned condition。

## Extension Protocol

当前没有活动 `collector` package。首个真实 Flink、Spark、Hadoop 接入必须同时说明 evidence ownership、事件重放、replacement scope、SDK adapter、失败恢复并补行为测试。禁止提前创建空 Collector，禁止复制 `flink/domain`、`spark/service` 等平行业务层。

## Root Package Rule

`io.yak.ops.business.lineage` 根包保持空白；公开 contract 必须归属 analysis/domain/query/registration/maintenance 的明确角色路径。

## Executable Guards

- `LineageArchitectureTest`：反射可见的层次与 Domain 规则；
- `LineageDependencyBoundaryTest`：精确 package 集合和依赖图；
- `LineageMavenDependencyBoundaryTest`：Maven runtime owner；
- `LineagePublicApiBoundaryTest`：跨模块公共 surface；
- `LineageCodeStyleConventionTest`：角色位置与源码约定；
- `LineageDocumentationContractTest`：文档 contract。

## Change Rules

- package move 与业务行为修改分开；
- 新入口稳定后不保留旧兼容 facade；
- 新 `@Service` 必须证明它是新的稳定应用入口；
- 新 package、公共类型和 dependency 必须有真实调用方；
- 架构变化同步更新代码、文档和 executable guard。
