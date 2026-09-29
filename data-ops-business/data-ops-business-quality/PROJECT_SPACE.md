# Data Quality Project Space

本文件定义 Data Quality 当前有效的 Project Space 数据边界。全局产品语义以仓库级 [`docs/architecture/PROJECT_SCOPE.md`](../../docs/architecture/PROJECT_SCOPE.md) 为准；本文件只收敛 Quality 自身的归属、查询和后台恢复契约。

## 1. Ownership

| Quality fact | Project ownership | Rule |
| --- | --- | --- |
| 内置规则模板 | `GLOBAL` | 平台级只读能力，不随 Project 切换 |
| 自定义模板与模板目录 | `GLOBAL` | 当前版本仍是平台共享模板库 |
| `TableAsset` | `PROJECT_ROOT` | 归属必须来自当前 Project 内的 Datasource |
| `Monitor` | `PROJECT_ROOT` | 目标 TableAsset 与 Datasource 必须属于同一 Project |
| `Rules` / `MonitorSettings` | `INHERITED` | 通过 Monitor 继承，不独立接受 Project 参数 |
| `Execution` | `PROJECT_RUNTIME` | 直接持久化 `project_id`，支持独立查询和后台恢复 |
| `RuleExecution` / `AlertEvent` | `INHERITED` | 通过 Execution / Monitor 证明归属 |
| Yak Schedule definition | runtime projection | payload 与 metadata 必须携带持久化 Project ID |

Quality 不支持跨 Project TableAsset、Monitor、Execution 引用，也不提供全局管理旁路。

## 2. HTTP Boundary

以下接口要求 trusted `CurrentProject`：

```text
/api/v1/data-quality/table-asset/**
/api/v1/data-quality/monitor/**
/api/v1/data-quality/execution/**
/api/v1/data-quality/overview/**
```

模板接口保持显式全局：

```text
/api/v1/data-quality/template/**
```

请求头只负责选择 Project。业务归属由服务端完成身份、成员与状态校验后建立的 `CurrentProject` 决定；DTO、Path 或 Query 中的 ID 不能覆盖该上下文。

## 3. Persistence Boundary

项目级根事实直接保存 `project_id`：

```text
yak_quality_table_asset.project_id
yak_quality_monitor.project_id
yak_quality_execution.project_id
```

普通 DAO / Repository 访问必须 fail closed：

- 列表、详情、更新、删除、执行和报表都带当前 `project_id`；
- 创建时忽略外部归属，统一写入 `CurrentProject.requireProjectId()`；
- 预填的其他 Project ID 被视为不可见资源；
- Rule、Settings、RuleExecution、Alert 写入前先证明父 Monitor / Execution 属于当前 Project；
- 跨 Project ID 对业务层表现为当前 Project 下不存在，避免资源枚举。

全局模板查询不注入 Project 条件；模板使用量等统计仍表达平台级模板库事实。

## 4. Datasource Consistency

TableAsset 注册只能通过 `QualityDataCatalogGateway` 进入 Datasource typed Catalog。Datasource 自身已经按 `CurrentProject` 隔离，因此：

```text
CurrentProject
  == Datasource.project_id
  == TableAsset.project_id
  == Monitor.project_id
```

数据库迁移从 Datasource 根事实确定性回填 TableAsset / Monitor，再由 Monitor 回填 Execution。迁移不猜测默认 Project ID；存在孤立数据时由 `NOT NULL` contract 阻止部署继续。

## 5. Async Execution

`QualityExecutionPlan` 在受理时冻结 `projectId`。事务提交后的线程池任务不能依赖 HTTP ThreadLocal，而必须显式恢复：

```text
QualityExecutionPlan.projectId
  -> ProjectContextScope
  -> QualityExecutionWorker
  -> project-scoped Repository / Datasource Catalog
```

队列拒绝后的 Execution/Monitor 补偿写入也必须在相同 Project Context 中执行。

## 6. Schedule and Recovery

Schedule definition 的 payload 和 metadata 同时保存 `projectId` 与 `monitorId`。回调处理顺序固定为：

```text
read persisted projectId
  -> ProjectContextScope
  -> load current Monitor / Settings
  -> QualityExecutionManager.runScheduled
```

启动恢复使用一个显式、只返回 `(projectId, monitorId)` 的系统级扫描端口。扫描结果不能直接执行业务操作；每个候选必须先恢复对应 Project，再进入普通 Repository 和 Lifecycle。

## 7. Review Checklist

变更 Quality 项目数据时至少验证：

- Project A / B 的列表、详情、修改、删除和运行互不可见；
- A 不能注册或监控 B 的 Datasource / TableAsset；
- 执行记录、规则执行工作台、质量报告与总览不聚合其他 Project；
- 手动执行、线程池 Worker、队列拒绝补偿都保留受理时 Project；
- Schedule callback、misfire 与启动恢复都能恢复持久化 Project；
- 模板接口继续全局，且前端不会向其附加 Project Header；
- 迁移不存在硬编码 `project_id = 1` 或其他猜测默认空间的逻辑。
