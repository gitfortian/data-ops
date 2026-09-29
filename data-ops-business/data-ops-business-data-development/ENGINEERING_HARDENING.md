# Data Development Stage 3：Engineering Hardening

Stage 3 不新增产品语义，目标是把 Stage 1/2 已经建立的执行控制面和项目治理变成可长期维护的工程结构。

## Persistence boundary

应用角色不再直接持有 `JdbcTemplate`：

```text
execution / editor / lineage
        -> repository contract
        -> JDBC repository adapter
```

本阶段完成：

- Execution history SQL 从 `DevelopmentTaskExecutionService` 下沉到 `DevelopmentTaskExecutionRepositoryAdapter`；
- Editor Settings SQL 下沉到 `DevelopmentEditorSettingRepositoryAdapter`；
- Lineage Outbox SQL 下沉到 `DevelopmentLineageOutboxRepositoryAdapter`；
- architecture test 禁止 application role 重新 import `JdbcTemplate`。

Repository adapter 负责 project predicate、SQL 与 row mapping；Application Service 负责业务归一化、JSON contract、Runtime 编排和用户可见错误语义。

## Background project restoration

Execution Reconciler 是跨项目 dispatcher，但每条 durable execution 必须携带其持久化 `project_id`：

```text
cross-project scan
   -> ReconciliationCandidate(project_id)
   -> ProjectContextScope
   -> project-scoped get/update/runtime reconciliation
```

后台线程不得因为没有 HTTP Header 而退化成 global write。

## Legacy island reduction

Data Service Runtime Source Provider 已从 `service` 迁入 `dataservice` owner package。

`DevelopmentDataServiceSqlCompiler` 的核心实现也已迁入 `dataservice`；旧 `service` 包仅保留一个无业务逻辑的 Spring compatibility shell，等待大体量 `DevelopmentDataServiceNodeService` 在独立、可验证的机械迁移中一起归位。

本阶段不搬动 2~3 万行 SQL Lineage parser / Data Service Node 大实现，避免把结构迁移和算法/产品行为变化混在同一个 PR。

## Frontend workbench decomposition

`DevelopmentWorkbench` 不再直接拥有所有稳定视图与纯算法：

```text
DevelopmentWorkbench
├── StandaloneResourceEditors
├── UnsavedChangesModal
├── workbenchTabs          # pure close/focus rules
└── workbenchResponse      # response contract
```

`workbenchTabs.test.ts` 锁定 close-current / close-all / close-others / close-left / close-right 以及关闭 active tab 后的焦点选择。

Data Service Node Editor 仍是已知大组件债务。本阶段不同时大改 Workbench 与 Data Service Editor 两个核心交互面；后续拆分必须优先抽取 contract panels / publication state / resize state，并保持 DOM/交互语义不变。

## Guardrails

Stage 3 之后：

1. `execution/editor/lineage` 不能直接访问 DAO 或持有 `JdbcTemplate`；
2. Data Service Runtime Provider 不能回流到 frozen `service`；
3. 新持久化能力默认建立 Repository contract；
4. 跨项目后台 dispatcher 必须从 durable record 恢复 Project Context；
5. Workbench Tab close/focus 行为必须通过纯函数测试。
