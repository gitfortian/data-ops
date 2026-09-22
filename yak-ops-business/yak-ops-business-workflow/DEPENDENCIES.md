# Workflow Dependencies

本文件定义 Workflow package 的**允许依赖方向、跨子系统 corridor 和外部边界**。原则：**显式、窄、无环**。

架构角色看 `ARCHITECTURE.md`；统一工程规则看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。代码与本文件冲突时，先判断 ownership/placement 是否错误，不要第一反应扩大白名单。

## 1. Top-level Dependency Matrix

Workflow production 内部允许的 top-level 依赖：

| Source | Allowed Workflow packages |
| --- | --- |
| `controller` | `backfill`, `definition`, `domain`, `execution`, `runtime`, `schedule` |
| `backfill` | `dao`, `definition`, `domain`, `execution`, `repository`, `schedule` |
| `schedule` | `dao`, `definition`, `domain`, `execution`, `repository` |
| `execution` | `dao`, `domain`, `repository`, `runtime` |
| `definition` | `dao`, `domain`, `repository`, `runtime` |
| `runtime` | `domain`, `observability`, `repository` |
| `observability` | none |
| `repository` | `dao`, `domain` |
| `dao` | none |
| `domain` | none |

同一 top-level package 内部可以协作，但不会因此自动成为其他 package 的公共 API。声明图和实际源码图都必须无环。

## 2. Controller Corridors

Controller 只能进入稳定业务/运行入口：

```text
definition -> WorkflowDefinitionManager (read/control)
           -> WorkflowDefinitionAuditCoordinator (audited definition mutations + editor pause/resume)
execution  -> WorkflowLauncher / WorkflowExecutionManager / WorkflowExecutionReactivator
           -> WorkflowExecutionControlAuditCoordinator (pause/resume/cancel)
runtime    -> WorkflowRuntime
schedule   -> WorkflowScheduleAuditCoordinator / WorkflowScheduleQuery + trigger query
backfill   -> WorkflowBackfillAuditCoordinator / WorkflowBackfillManager (preview) / WorkflowBackfillQuery
```

Controller 不直接依赖 DAO、Repository、Schedule Engine Bridge 或 Trigger Admission/Coordinator。

### Definition Audit Corridor

Definition mutation 的审计入口固定为：

```text
WorkflowDefinitionController
    -> WorkflowDefinitionAuditCoordinator
    -> WorkflowDefinitionManager

WorkflowDefinitionAuditCoordinator
    -> shared BusinessAuditService
```

约束：

- Audit 是 fail-open side effect，不能改变 Workflow command 结果；
- Domain / Repository / DAO 不依赖 Audit；
- Audit payload 只保存显式 allowlist 的业务差异，不复制 `input`、`editorMeta`、节点完整配置或 TaskVersionSnapshot；
- 无实际 Definition 变化的保存不创建 AuditOperation；
- Runtime / Schedule / Backfill 审计不属于 Definition lifecycle corridor。

编辑器现有 `/definitions/{id}/pause|resume` 路由虽然挂在 Definition HTTP surface 下，实际控制的是 `latestExecutionId`。因此这两个入口仍由 Definition facade 委托 Runtime，但 AuditOperation 的 `resource_type` 必须是 `WORKFLOW_EXECUTION`，并且不得替换该 Execution 的长期 carrier。

### Execution Audit Corridor

Workflow Execution 的启动相关性固定为：

```text
WorkflowLauncher
    -> WorkflowExecutionAuditBridge
    -> shared BusinessAuditService
    -> WorkflowAuditCorrelationRepository
    -> WorkflowExecutionDao
    -> yak_workflow_execution.audit_carrier_json
```

Runtime 不直接依赖 Audit。终态继续由既有 durable truth 发布：

```text
WorkflowExecutionRepositoryAdapter
    -> WorkflowExecutionTerminalEvent
    -> AFTER_COMMIT WorkflowExecutionAuditTerminalListener
    -> WorkflowExecutionAuditBridge
```

人工 `retry/continue` 会复用同一个 WorkflowExecution，因此必须在重新激活前创建新的 `WORKFLOW_EXECUTE` operation 并替换 carrier；失败时恢复旧 carrier。`pause/resume/cancel` 是独立短操作，不替换 execution carrier。

Audit correlation 更新只能修改 `audit_carrier_json`，不能顺手更新 Runtime status、runtime metadata 或 `updated_at`。

### Schedule Audit Corridor

直接 Schedule 配置/生命周期操作与无父业务操作的 Scheduler 自动动作进入：

```text
HTTP create/update/online/offline/delete
Yak Schedule automatic expire/disable
        -> WorkflowScheduleAuditCoordinator
        -> existing CreateCommand / Revision / Lifecycle
        -> shared BusinessAuditService
```

约束：

- Schedule AuditOperation 的 resource 固定为 `WORKFLOW_SCHEDULE`；
- Cron、timezone、日期区间和策略可以作为业务事实记录；
- `input` 原值不能复制到 Audit，只允许 `inputConfigured/inputChanged`；
- Scheduler 自动过期/停用使用 `source=SCHEDULE`；
- Workflow online/offline 引起的 Schedule 联动**不创建第二条 AuditOperation**，父 `WORKFLOW_ENABLE/DISABLE` 已经表达用户意图，这也避免抢占父操作的 deferred authorization decision；
- Trigger Ledger 本身不为每个触发额外创建 AuditOperation，成功启动后的业务历史由 `WORKFLOW_EXECUTE` 承担。

### Backfill Audit Corridor

Backfill 管理动作固定为：

```text
create/cancel
businessDate rerun gateway
        -> WorkflowBackfillAuditCoordinator
        -> WorkflowBackfillManager
        -> shared BusinessAuditService
```

Backfill 管理 Operation 只描述批次创建/取消事实。每个真正启动的子 WorkflowExecution 继续由 `WorkflowLauncher -> WorkflowExecutionAuditBridge` 单独记录，避免把运行期事件复制进 Backfill 管理日志。

Backfill Audit payload 不允许复制 `input`、继承的 source input 或 Trigger runtime message，只保留 schedule/workflow/version、日期范围、执行策略和数量等稳定事实。

## 3. Definition -> Runtime

当前 definition-bound run/control 行为只允许：

```text
WorkflowDefinitionManager -> WorkflowRuntime
```

Graph compiler、Task binding resolver 不直接调用 Runtime。Schedule activation guard 已归属 Schedule，不允许重新建立 `definition -> schedule -> definition` 环。

## 4. Execution -> Runtime

Execution 创建/控制实例只允许进入 `WorkflowRuntime`。

```text
WorkflowLauncher                         -> WorkflowRuntime
WorkflowExecutionManager                 -> WorkflowRuntime
WorkflowExecutionReactivator             -> WorkflowRuntime
WorkflowExecutionControlAuditCoordinator -> WorkflowRuntime
WorkflowPublishedVersionRunner           -> WorkflowRuntime
```

Execution 不直接依赖 Schedule/Backfill 实现。需要反向策略时使用 execution-owned Port。

## 5. Schedule -> Execution

Schedule/Trigger 跨入 Execution 当前只允许：

```text
WorkflowScheduleTriggerCoordinator
    -> WorkflowLauncher
    -> WorkflowExecutionReactivationGuard
```

Trigger 不能直接 start Yak Workflow Engine，也不能调用 Runtime 绕过 Launcher。

## 6. Backfill Corridors

Backfill -> Schedule 允许的稳定角色：

```text
WorkflowScheduleQuery
WorkflowScheduleParameterResolver
WorkflowScheduleTriggerAdmission
WorkflowScheduleTriggerCoordinator
WorkflowBackfillTriggerGateway
```

Backfill -> Execution 只允许：

```text
WorkflowBusinessDateRerunGateway
WorkflowLauncher
```

`WorkflowBackfillAuditCoordinator` 是 execution-owned `WorkflowBusinessDateRerunGateway` 的唯一 Backfill 实现；`WorkflowBackfillManager` 保持纯 Backfill 业务 facade。`WorkflowBackfillTriggerAdapter` 实现 Schedule-owned `WorkflowBackfillTriggerGateway`；Schedule 不 import `workflow.backfill.*`。

## 7. Runtime -> Observability

Runtime 对 Observability 只有：

```text
WorkflowRuntime -> WorkflowEventStream
```

Observability 不反向依赖 Runtime、Execution、Schedule 或 Repository。

## 8. Persistence Boundary

Definition/Runtime 的 durable business contract：

```text
role -> WorkflowDefinitionRepository / WorkflowRuntimeRepository
     -> adapter -> DAO / engine SPI
```

Audit correlation 使用独立的窄 Repository，只读写 execution 的相关性列，不进入 Workflow Runtime aggregate contract。

Schedule/Trigger/Backfill 当前允许直接使用自己明确的 DAO/ledger primitive，但：

- Controller 不得依赖 DAO；
- Domain 不得依赖 DAO/Repository；
- DAO 不得反向依赖业务 package；
- Repository 只能向下依赖 DAO/Domain；
- Repository contract 不暴露 Workflow DTO/VO/PO/MyBatis 类型。

## 9. Yak Workflow Engine Boundary

Engine runtime state owner 保持在 `yak-workflow-engine`。

允许的职责：

- `runtime` 调用 Engine API/SPI；
- `repository` 适配 Engine Definition/Execution persistence；
- Execution read-side 可以通过已经注入的 engine repository 读取历史 DAG evidence，但不能写 engine state。

新的 engine command 不应出现在 Controller/Schedule/Backfill。

## 10. Business Job Boundary

Definition 可以使用 Task Registry/Catalog 来冻结 TaskVersionSnapshot；Runtime 可以使用 TaskExecutionGateway 来执行/查询具体任务。

其他 Workflow package 不直接认识具体 Offline/Realtime/SQL executor 实现。

## 11. Yak Schedule Boundary

`schedule.engine.WorkflowScheduleEngineBridge` 是 Yak Schedule 的 framework adapter。

Trigger Handler 可以接收 Schedule callback，但必须立即进入 Workflow Trigger Ledger。Yak Schedule snapshot 不成为 WorkflowSchedule 配置 owner。

## 12. No Cycles

不允许通过以下方式掩盖 package cycle：

- `@Lazy`；
- ApplicationContext lookup；
- static Service Locator；
- 把双方接口随意扔进 `common`；
- 扩大 dependency-test 白名单。

反向协作使用 owner-defined Port：

```text
execution.WorkflowExecutionReactivationGuard
    <- schedule.trigger.WorkflowScheduleTriggerCoordinator

execution.WorkflowBusinessDateRerunGateway
    <- backfill.WorkflowBackfillAuditCoordinator

schedule.trigger.WorkflowBackfillTriggerGateway
    <- backfill.WorkflowBackfillTriggerAdapter
```

## 13. Adding A New Dependency

新 import 不在矩阵时依次判断：

1. 类是否放错 package？
2. 已有 stable role / Port 是否足够？
3. 能否由 capability owner 定义一个窄 interface？
4. 架构是否真的改变？

只有第 4 种情况才在同一 PR 更新 `ARCHITECTURE.md`、本文件和 executable dependency test。
