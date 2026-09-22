# Yak Ops Data Service

`yak-ops-business-data-service` 是 Yak Ops 的在线只读数据服务模块：把已经发布的上游数据定义解析成稳定的 Data Service，并提供受控 SQL 执行、访问控制、Runtime 保护、API 文档和调用观测。

## 模块文档

| 文档 | 作用 |
| --- | --- |
| [REQUIREMENTS.md](REQUIREMENTS.md) | 模块必须提供什么能力，以及哪些行为属于兼容性要求 |
| [DOMAIN.md](DOMAIN.md) | 核心业务事实、状态、不变量和 Truth Owner |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 包职责、角色协作、关键流程和扩展边界 |
| [DEPENDENCIES.md](DEPENDENCIES.md) | 顶层 package 依赖矩阵、允许/禁止的 corridor |
| [REVIEW.md](REVIEW.md) | 修改本模块时的设计与 Review Checklist |
| [MIGRATIONS.md](MIGRATIONS.md) | Data Service 独立 Flyway namespace、一期 V1 baseline 与后续版本规则 |
| [RUNTIME_RELIABILITY.md](RUNTIME_RELIABILITY.md) | Stage 1 调用结果/审计隔离、版本化缓存和服务级日志读取契约 |
| [PROJECT_GOVERNANCE.md](PROJECT_GOVERNANCE.md) | Stage 2 Management Plane Project/RBAC 与 Public Invocation Plane 的强边界 |
| [CLUSTER_RUNTIME.md](CLUSTER_RUNTIME.md) | Stage 3 集群限流、Runtime 指标、缓存代际、日志生命周期与审计脱敏 |
| [../../CODE_STYLE.md](../../CODE_STYLE.md) | 仓库统一 Java / Role-oriented 工程约定 |

## 能力地图

```text
Published Source
      |
      v
 Publication
      |
      v
DataServiceDefinition
  |       |        |
  v       v        v
Access  Runtime  Documentation
   \      |       /
    \     |      /
       Execution
          |
          v
  SqlExecutionRuntime
          |
          v
      Datasource
```

模块内部按能力而不是传统技术分层组织：

```text
dataservice
├── access          # API Key lifecycle / authorization / cluster-wide rate limit
├── config          # module wiring / conditions / dedicated Data Service Flyway
├── controller      # HTTP boundary only
├── dao             # MyBatis persistence projection
├── documentation   # contract docs / OpenAPI projection
├── domain          # business facts and value objects
├── execution       # invocation / SQL compilation / physical query orchestration / audit masking
├── management      # stable Data Service lifecycle commands
├── observability   # call-log / overview / retention-rollup maintenance
├── publication     # source discovery / publish / republish
├── query           # Data Service read side and HTTP projection factory
├── repository      # domain persistence + cluster coordination/metric ports and adapters
└── runtime         # node-local cache/circuit + persisted policy + cluster invocation projection
```

生产代码不使用 `service / service.impl / common / helper / utils / util / base` 作为默认业务桶。

## 最重要的边界

1. SQL 和 `dataSourceId` 只能来自服务端解析到的已发布 Source Revision，不能由 Data Service HTTP 更新接口直接提交。
2. `DataServiceDefinition` 是稳定服务身份和持久化配置的业务事实；MyBatis PO 只是数据库投影。
3. `PublishedRuntimeSnapshot` 是已发布 SQL + Datasource 的持久化快照；不是本机运行状态。
4. `RuntimePolicy` 是持久化策略 Truth；`LocalDataServiceRuntime` 的 Cache/Circuit 只代表当前实例，调用规模指标来自集群持久化 evidence。
5. `API_KEY` 的 raw secret 只在 create/rotate 成功时返回一次；数据库只保存 hash + prefix；RPM 配额由共享持久化窗口在整个集群执行。
6. 数据服务只允许单条只读 SELECT，物理执行统一走 `yak-ops-core` 的 `SqlExecutionRuntime`。
7. Data Development 等上游模块只能实现 `publication.source.DataServiceSourceProvider`，不能依赖 Data Service 内部 Manager/Repository/Runtime。
8. Invocation audit 是 evidence：日志持久化故障不能改变已经确定的业务调用结果或覆盖原始异常；请求参数必须先脱敏再落库。
9. Node-local Cache identity 必须带 persisted `runtime_generation` 和 Runtime shape，防止 republish/并发设置更新后跨节点误用旧结果。
10. Yak Ops Management Plane 必须同时通过 Project Space 和 RBAC；外部 `/runtime/{servicePath}` 不接收 Yak Project Header，只按全局 runtime path + NONE/API_KEY 调用。
11. Raw invocation log 有明确 retention，并在事务内按小时 rollup；长期统计不能依赖无限增长的 raw 表。
12. Data Service 数据库结构只由 `db/migration/yak-data-service` 和 `yak_data_service_schema_history` 管理；Datasource 拥有独立的 `yak-datasource` namespace，两个模块禁止共享 Flyway 版本序列。

## 修改入口

准备修改本模块时：

```text
1. REQUIREMENTS.md -> 需求是否已经存在？
2. DOMAIN.md       -> 哪个事实/不变量受到影响？
3. ARCHITECTURE.md -> 职责应该归哪个角色？
4. DEPENDENCIES.md -> 是否引入新的 package edge？
5. MIGRATIONS.md   -> 是否修改数据库结构/Flyway namespace？
6. Tests / Guards  -> 是否有对应行为和架构护栏？
7. REVIEW.md       -> 合并前逐项检查。
```

涉及调用审计、Cache identity 或服务级调用日志读取时查看 `RUNTIME_RELIABILITY.md`；涉及 Project ownership、RBAC 或外部 Invocation 边界时查看 `PROJECT_GOVERNANCE.md`；涉及多实例限流、指标、rollup 或敏感参数时查看 `CLUSTER_RUNTIME.md`；涉及 schema/Flyway 变更时先查看 `MIGRATIONS.md`。

如果需求无法由当前模型表达，先报告 `Requirement Gap` / `Domain Gap`，不要用临时 Map key、boolean、PO 字段或 Controller DTO 绕过模型。
