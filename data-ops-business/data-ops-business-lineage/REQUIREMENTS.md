# Lineage Requirements

本文描述 Lineage 需要长期保持的业务能力与边界。

## Responsibilities

- 注册、更新并查询统一 `LineageAsset`；
- 注册带类型、来源证据、置信度、版本与观测时间的有向 `LineageRelation`；
- 支持有界批量写入和同批稳定去重；
- 支持 upstream / downstream / both 图查询；
- 对自动生成血缘按 evidence scope 做安全替换和清理；
- 提供 source-neutral SQL projection analysis contract；
- 为真实平台来源提供统一 Domain 与角色化接入边界。

## Public Contract

邻接模块只允许使用：

```text
analysis.sql.SqlProjectionLineageAnalyzer
query.LineageQueryService
registration.LineageRegistrationService
maintenance.LineageMaintenanceService
```

以及稳定的 Asset/Relation/Graph 只读领域类型与枚举。Controller DTO/VO、Repository、DAO、Mapper、PO、Persistence Config、`LineageAssetDraft`、`LineageRelationDraft` 都不是跨模块 contract。

## Stable Behavior

- 图查询根资产必须存在，当前最大深度为 10；
- 遍历过程中节点与关系去重；
- source/target 方向不在查询层反转；
- 关系不能自指，confidence 保持 `[0, 1]`；
- replacement 清理必须满足 ownership 与引用安全；
- 新 revision 已提交后旧 revision 不能覆盖新结果；
- 角色拆分不改变 REST、DB、Flyway、领域语义和事务边界。

## Analysis Boundary

`SqlProjectionLineageAnalyzer` 只拥有 source-neutral contract，具体 parser 留在拥有 parser 的邻接上下文。

## Platform Extension

当前没有活动 Collector。出现真实平台事件或 SDK 时，先明确 evidence owner、重放/重复处理、replacement scope、SDK 到统一 Domain 的转换、失败恢复和公共 contract 影响，再新增角色 package。

## Non-goals

Lineage 不负责调度 Flink/Spark/Hadoop 作业，不成为第二套任务状态中心，不为每个来源复制 Asset/Relation/Service/DAO，不在 Domain 中依赖 Spring/MyBatis/平台 SDK，也不为了目录完整创建没有调用方的角色。

## Acceptance

- 三个稳定 `@Service` 位于 query/registration/maintenance 角色 package；
- 顶层 `service` package 不存在；
- 内部 Reader/Registrar/Coordinator/Guard 不伪装成 Service；
- package 图、公共 API、Maven、代码角色和文档 guard 保持通过；
- DAO/PO 不进入稳定 facade 与 Domain contract；
- 跨模块调用方只依赖声明的公共 contract；
- 业务行为测试继续通过。
