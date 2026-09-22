# Yak Ops Project Space 数据边界

> 状态：PR1 设计基线  
> 目标：在任何业务表增加 `project_id` 之前，先固定 Project Space 的产品语义、数据归属和迁移规则。

## 1. 为什么先定义边界

Yak Security 已经提供 Project、项目成员和 `X-YAK-SECURITY-PROJECT-ID` 上下文，但 Yak Ops 当前大部分业务数据仍是全局数据。此时直接给所有表增加 `project_id`，很容易出现三个问题：同一业务链路的父子表归属不一致、异步任务丢失项目上下文、某些详情接口只按 ID 查询导致跨项目访问。

因此 Project Space V1 先约定：

```text
角色 / 权限     决定用户“能做什么”
项目成员关系    决定用户“能在哪个空间做”
project_id      决定业务数据“属于哪个空间”
```

Project Space 是 Yak Ops 内部的业务工作空间和数据隔离边界，不新增一套租户体系。Yak Security 的 `application-name` 继续承担应用级隔离，Project 是应用内部更细的一层工作空间。

PR1 只建立契约，不修改数据库、不改变接口行为、不开放项目切换器。

## 2. V1 的硬规则

1. **角色保持全局。** V1 不实现“同一用户在项目 A 是管理员、在项目 B 是只读成员”的项目级角色覆盖。
2. **不支持跨项目引用。** 数据集、任务、工作流、仪表盘、API 等只能引用同一项目中的业务资源。
3. **`project_id` 由服务端可信上下文决定。** 业务创建请求不得把请求体中的 `projectId` 当作最终归属依据。
4. **项目隔离覆盖完整 CRUD 和执行链路。** 不能只过滤列表，详情、编辑、删除、复制、发布、执行、下载、导出都必须校验项目。
5. **异步运行记录必须能独立恢复项目上下文。** HTTP 请求结束后不能依赖 ThreadLocal、Session 或前端状态。
6. **不通过物理外键连接 Yak Security Project。** Yak Security 和业务库可以使用独立数据源，关系完整性由 Service 层维护。
7. **有业务数据的项目以停用/归档为主，不直接物理删除。** 空项目才允许删除。

## 3. 数据归属类型

后续改造统一使用以下类型，不再逐表临时决定。

| 类型 | 含义 | `project_id` 策略 |
|---|---|---|
| `GLOBAL` | 应用级公共能力，不随项目切换 | 不增加 |
| `PROJECT_ROOT` | 可以独立创建、查询、授权的项目业务对象 | 直接保存，最终 `NOT NULL` |
| `INHERITED` | 只依附父聚合访问，本身不独立选择项目 | 通过父对象继承，通常不重复保存 |
| `PROJECT_RUNTIME` | 执行、调度、日志、事件等异步/运行态事实 | 直接保存，便于后台恢复和独立查询 |
| `PROJECT_PROJECTION` | 从源业务对象投影出的目录/索引/派生数据 | 保存源对象的项目，调用方不得自行指定 |

判断原则：如果对象会被独立分页、后台扫描、重试、恢复或长期留存，即使理论上能从父表反查，也优先使用 `PROJECT_RUNTIME` / `PROJECT_PROJECTION` 直接保存 `project_id`。

## 4. 当前基线

当前仓库已经有两个“部分项目化”字段，但还不能视为 Project Space 已接入：

- `yak_dev_node.project_id`：当前允许 `NULL`；
- `yak_task_asset.project_id`：当前允许 `NULL`，且 Task Catalog 本身只是源任务的投影。

其余核心业务表，包括数据源、数据集、文件资源、同步任务、工作流、质量监控、分析、仪表盘、数据服务、血缘等，目前没有统一的项目归属约束。

这两个已有字段后续必须统一到本契约：它们指向 Yak Security Project，并且不能继续作为可选的、来源不明确的业务参数。

## 5. 全局数据

V1 明确保留为 `GLOBAL`：

| 能力 | 当前对象/表 | 说明 |
|---|---|---|
| 身份与 RBAC | Yak Security 用户、部门、角色、功能权限 | 角色决定“能做什么” |
| Project 元数据 | Yak Security Project、成员、负责人关系 | Project 自身不是某个 Project 的子数据 |
| 实时计算环境 | `yak_compute_environment` | Flink CDC 等运行环境由平台管理员维护，可被多个项目使用 |
| 实时运行租约 | `yak_realtime_runtime_lease` | 平台内部协调状态 |
| 系统环境变量 | `yak_system_env_var` | 平台 Runtime 环境配置 |
| 告警渠道 | `yak_ops_alert_channel` | 当前模型按渠道类型全局唯一，V1 继续全局共享 |
| 质量内置模板 | `yak_quality_rule_template` 中 `builtin=1` | 平台级规则能力 |
| 质量自定义模板库 | `yak_quality_rule_template` / `yak_quality_template_folder` | V1 暂按全局模板库处理，项目私有模板延后 |
| Job Registry / Runtime 能力 | `yak-ops-business-job` | Job 是任务发现、快照和执行路由基础设施，不拥有业务 Task Source Truth |
| 数据源插件/连接器元数据 | 插件注册和字段定义 | 平台能力，不属于项目业务数据 |
| Yak Security 操作日志 | 系统管理审计 | V1 仍作为系统级审计；业务执行记录单独保存项目 |

全局资源可以被项目业务对象引用，但引用权限仍受功能权限和业务校验约束。例如 Project A 可以选择全局 Compute Environment，但不能因此访问 Project B 的实时任务。

## 6. 项目数据矩阵

### 6.1 基础资源

| 模块 | 当前核心数据 | V1 归属 | 关键约束 | 计划 |
|---|---|---|---|---|
| 数据源 | `yak_ops_data_source` | `PROJECT_ROOT` | 名称唯一从全局改为项目内唯一；所有 ID 查询带项目条件 | PR3 |
| 文件资源 | `yak_ops_resource` | `PROJECT_ROOT`（每个节点直接保存） | 父子节点必须同项目；根目录按项目隔离；路径/同级名称在项目内唯一 | PR3 |
| 数据集 | `yak_dataset` | `PROJECT_ROOT` | 数据集引用的数据源、开发任务必须同项目 | PR3 |
| 数据集版本 | `yak_dataset_version` | `INHERITED` | 从 `dataset_id` 继承，不接受独立 project 归属 | PR3 |
| 数据集字段 | `yak_dataset_field` | `INHERITED` | 从 version → dataset 继承 | PR3 |

数据源当前 `name` 是全局唯一；Project Space 生效后必须调整为 `(project_id, name)` 维度，否则两个项目无法创建同名数据源。

文件资源建议每一行都直接保存 `project_id`，而不是只在根节点保存。目录树、路径查询和删除通常是独立 SQL，直接带项目条件更安全。

### 6.2 数据开发与任务目录

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_dev_directory` | `PROJECT_ROOT` | 每个项目拥有独立开发目录树，兄弟目录唯一约束增加 project 维度 |
| `yak_dev_node` | `PROJECT_ROOT` | 已有 nullable `project_id`，迁移后收紧为必填 |
| `yak_dev_task_draft` | `INHERITED` | 从 node 继承 |
| `yak_dev_task_revision` | `INHERITED` | 从 node 继承 |
| `yak_dev_task_execution` | `PROJECT_RUNTIME` | 执行记录直接保存 project，支持独立查询和异步恢复 |
| `yak_dev_lineage_outbox` | `PROJECT_RUNTIME` | Outbox 后台消费时不能依赖原 HTTP 上下文 |
| `yak_task_asset` | `PROJECT_PROJECTION` | 已有 nullable `project_id`；必须由源 Task 决定，不能由 Catalog 自行选择 |

Task Catalog 只是发现投影。Project 的 Source Truth 必须来自发布该 Task 的业务域，Catalog 只能复制该 project，并校验 descriptor / revision 与源任务一致。

### 6.3 离线同步

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_offline_job_definition` | `PROJECT_ROOT` | Source / Sink DataSource 必须与任务同项目 |
| `yak_offline_batch_execution` | `PROJECT_RUNTIME` | 批次独立保存 project |
| `yak_offline_job_execution` | `PROJECT_RUNTIME` | Attempt / Execution 独立保存 project |
| `yak_offline_execution_event` | `INHERITED` | 从 execution 继承，查询必须经过所属 execution |

任务名称的唯一约束改为项目内唯一。计划在 PR4 接入。

### 6.4 实时同步

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_compute_environment` | `GLOBAL` | 计算环境平台共享 |
| `yak_realtime_job_definition` | `PROJECT_ROOT` | 实时任务属于项目 |
| `yak_realtime_definition_version` | `INHERITED` | 从 task/definition 继承 |
| `yak_realtime_job_deployment` | `PROJECT_RUNTIME` | 部署记录直接保存 project，后台 reconcile 可恢复上下文 |
| `yak_realtime_job_event` | `INHERITED` | 从 definition/deployment 继承 |
| `yak_realtime_runtime_lease` | `GLOBAL` | Runtime 协调设施 |

实时任务可以使用全局 Compute Environment，但定义、发布、部署、停止等操作仍以当前项目为边界。计划在 PR4 接入。

### 6.5 工作流与调度

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_workflow_definition` | `PROJECT_ROOT` | Workflow 引用的 Task 必须同项目 |
| `yak_workflow_version` | `INHERITED` | 从 workflow 继承 |
| `yak_workflow_schedule` | `PROJECT_RUNTIME` | Scheduler 会独立扫描，直接保存 project |
| `yak_workflow_execution` | `PROJECT_RUNTIME` | 执行实例直接保存 project |
| `yak_workflow_schedule_trigger` | `PROJECT_RUNTIME` | 定时触发后台创建，直接保存 project |
| `yak_workflow_backfill` | `PROJECT_RUNTIME` | 补数任务可跨进程恢复，直接保存 project |
| `yak_workflow_node_execution` | `INHERITED` | 从 workflow execution 继承 |
| `yak_workflow_node_attempt` | `INHERITED` | 从 node/workflow execution 继承 |

Workflow V1 禁止把 Project A 的节点指向 Project B 的 Task。计划在 PR4 接入。

### 6.6 数据质量

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| 内置/自定义规则模板、模板目录 | `GLOBAL` | V1 使用平台级模板库 |
| `yak_quality_table_asset` | `PROJECT_ROOT` | 注册表资产跟随 DataSource 项目 |
| `yak_quality_monitor` | `PROJECT_ROOT` | DataSource / Table Asset 必须同项目 |
| `yak_quality_rule` | `INHERITED` | 从 monitor 继承；template 可引用全局模板 |
| `yak_quality_execution` | `PROJECT_RUNTIME` | 独立保存 project |
| `yak_quality_rule_execution` | `INHERITED` | 从 quality execution 继承 |

质量表资产当前唯一约束基于 DataSource + Database + Schema + Table；DataSource 完成项目化后该引用天然只能落在同项目，但 Service 层仍必须显式校验。计划在 PR5 接入。

### 6.7 分析、仪表盘与大屏

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_analysis` | `PROJECT_ROOT` | Dataset 必须同项目；分析可独立列表，因此直接保存 project |
| `yak_dashboard` | `PROJECT_ROOT` | 仪表盘独立归属项目 |
| `yak_dashboard_version` | `INHERITED` | 从 dashboard 继承 |
| widget/filter/binding/interaction | `INHERITED` | 从 dashboard version 继承 |
| 数字化大屏持久化对象 | `PROJECT_ROOT` | 当前若无独立后端持久化，后续引入时直接按本契约设计 |

Dashboard 的 active dataset、Widget 的 Analysis / Dataset 引用必须与 Dashboard 同项目。计划在 PR5 接入。

### 6.8 数据服务

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_ops_data_service_api` | `PROJECT_ROOT` | DataSource / Dataset 来源必须同项目；API path 唯一性改为项目维度产品路由规则 |
| `yak_ops_data_service_api_key` | `INHERITED` | Key 属于 API，不独立选择 project |
| `yak_ops_data_service_call_log` | `PROJECT_RUNTIME` | 即使 API 后续归档，也保留调用时 project 快照 |

对外调用 API 时不依赖浏览器当前 Project；API 定义自身已经绑定 project，运行时从 API 身份恢复项目上下文。计划在 PR5 接入。

### 6.9 数据血缘

| 当前核心数据 | V1 归属 | 说明 |
|---|---|---|
| `yak_metadata_asset` | `PROJECT_PROJECTION` | 每个资产保存 project；`asset_key` 唯一性改为项目维度 |
| `yak_metadata_relation` | `PROJECT_PROJECTION` | Relation 保存 project，Source/Target 必须同项目 |

V1 不产生跨项目血缘边。如果未来需要共享资产，先设计“共享/发布”模型，再决定跨项目图的展示权限。计划在 PR5 接入。

### 6.10 告警

当前持久化的 `yak_ops_alert_channel` 保持 `GLOBAL`。后续如果增加与质量监控、任务、工作流等资源绑定的告警规则和告警事件：

```text
AlertChannel      = GLOBAL
AlertRule         = PROJECT_ROOT
AlertEvent        = PROJECT_RUNTIME
```

项目告警规则只能引用同项目资源。

## 7. 跨域引用约束

以下关系必须在 Service 层执行“同项目”校验，而不是只验证 ID 存在：

```text
Dataset          -> DataSource / Development Task
Offline Sync     -> Source DataSource / Sink DataSource
Realtime Sync    -> Project-owned source/sink resources
Workflow         -> Task Asset / Task Definition
Quality Monitor  -> DataSource / Table Asset
Analysis         -> Dataset
Dashboard        -> Dataset / Analysis
Data Service     -> DataSource / Dataset
Lineage Relation -> Source Asset / Target Asset
```

统一语义：

```text
selectedProject == ownerProject(reference A) == ownerProject(reference B) ...
```

V1 不提供“引用 Project B 的共享数据源”“跨项目工作流”“跨项目仪表盘”等旁路。

## 8. 服务端 Project Context 契约

业务 HTTP 请求使用 Yak Security 已定义的：

```http
X-YAK-SECURITY-PROJECT-ID: <projectId>
```

但请求头只是“选择哪个项目”，不是授权证明。服务端必须按以下顺序建立 CurrentProject：

```text
用户已认证
  -> projectId 格式有效
  -> Project 存在且启用
  -> 当前用户是该 Project 的负责人或成员
  -> 建立可信 CurrentProject
  -> 业务 Service / Repository 使用该 projectId
```

创建业务对象时，最终 `project_id` 来自可信 CurrentProject。DTO 中即使为了兼容暂时存在 `projectId`，也不得覆盖服务端上下文。

后台执行没有 HTTP Header 时，必须从任务定义、执行实例、调度记录或消息载荷中的已持久化 `project_id` 恢复上下文。

## 9. 错误与防枚举语义

PR2 实现统一 Project Context 时建议固定以下错误语义：

| 场景 | 建议状态 | 语义 |
|---|---:|---|
| 项目模式已强制，但请求缺少 Project | 400 | `PROJECT_REQUIRED` |
| Project 不存在 | 404 | `PROJECT_NOT_FOUND` |
| Project 已停用/归档且当前操作不允许 | 403 | `PROJECT_DISABLED` |
| 用户不属于指定 Project | 403 | `PROJECT_FORBIDDEN` |
| 创建/更新时引用了其他项目的资源 | 409 | `CROSS_PROJECT_REFERENCE` |

普通业务资源的详情接口要避免泄露其他项目资源是否存在。对于 `/datasets/{id}`、`/tasks/{id}` 等请求，如果该 ID 不属于当前项目，对外统一表现为“当前项目下不存在该资源”，而不是告诉调用方资源真实属于哪个项目。

## 10. 兼容迁移策略

不能从“所有数据全局”一步切到“所有接口必须有 project”。迁移采用模块级三态：

```text
LEGACY_GLOBAL
    旧模块尚未迁移，保持现状，不伪装成已隔离

PROJECT_OPTIONAL
    已增加 nullable project_id，新增数据写入 project，旧数据正在回填

PROJECT_REQUIRED
    回填和校验完成，project_id 非空，所有访问强制项目隔离
```

兼容阶段不得静默相信 Request Body / Path 中的 `projectId`。缺少可信项目上下文时，只能走明确的迁移回退策略并输出可观测日志。

### 默认空间

为了无损迁移现有数据，创建一个“默认空间”：

- 现有超级管理员作为负责人；
- 现有启用用户加入为成员，保证升级后不会突然丢失访问；
- 所有历史业务数据回填到该默认空间；
- **禁止在 SQL 中硬编码默认项目 ID 为 `1`**，必须由 Project Service 创建/查询后取得真实 ID。

由于 Yak Security 与业务模块可能使用独立数据源，默认 Project 的创建和业务数据回填不要依赖跨库物理外键。推荐由应用级 Project Migration Coordinator 获取默认 Project ID，再驱动各业务模块回填。

## 11. 数据库迁移模板

每个模块统一使用 Expand -> Backfill -> Contract，避免大爆炸式迁移。

```text
1. Expand
   ADD project_id BIGINT NULL

2. Dual Write
   新增数据开始从 CurrentProject 写 project_id

3. Backfill
   旧数据批量回填到默认空间

4. Index / Unique
   增加 project 组合索引
   把全局唯一约束调整为项目内唯一

5. Verify
   project_id 无空值
   父子对象项目一致
   跨域引用项目一致

6. Contract
   project_id -> NOT NULL
   移除不再适用的全局唯一约束/兼容逻辑
```

大表（执行记录、调用日志、血缘、事件）必须分批回填，避免一次 UPDATE 长事务锁表。

## 12. 每个模块的验收门槛

一个模块只有同时满足下面测试，才能从 `PROJECT_OPTIONAL` 切到 `PROJECT_REQUIRED`：

- Project A / B 可以创建同名业务对象（业务上允许同名时）；
- A 的列表看不到 B 数据；
- 修改 URL / ID 不能读取 B 的详情；
- 不能编辑、删除、复制、发布、执行 B 的资源；
- A 创建对象时不能引用 B 的关联资源；
- 异步任务重启、重试、定时触发后仍能恢复 A 的 project；
- 历史数据全部回填且不存在孤立 project；
- 项目停用后阻断新增和执行，历史数据仍可按归档策略访问。

前端菜单隐藏和按钮禁用只负责体验，不能作为上述任意一项的安全实现。

## 13. 后续 PR 顺序

```text
PR2  Project Context 基础设施
     - CurrentProject / ProjectAccessGuard
     - 前端统一项目 Header
     - 成员/状态校验
     - 模块迁移三态与默认空间协调机制

PR3  基础资源项目化
     - DataSource
     - File Resource
     - Dataset

PR4  数据生产链项目化
     - Data Development / Task Catalog
     - Offline Sync
     - Realtime Sync
     - Workflow / Schedule / Execution

PR5  数据治理与消费链项目化
     - Quality
     - Analysis / Dashboard / Digital Screen
     - Data Service
     - Lineage
     - 项目空间和项目切换器正式开放
     - 主业务模块关闭兼容回退

PR6  细粒度资源授权
     - YakOpsResourceExtend
     - Project 内 Dataset / Task / Dashboard 等资源级权限
```

## 14. V1 明确延后

以下能力不是 Project Space V1 的上线前置条件：

- 项目级角色覆盖；
- 跨项目资源共享与订阅；
- 项目私有质量模板库；
- 项目私有告警渠道；
- 跨项目血缘图；
- 细粒度资源授权；
- 为 Yak Security Project 建业务库物理外键。

这些能力后续应建立独立模型，不能通过放宽 `project_id` 校验来实现。
