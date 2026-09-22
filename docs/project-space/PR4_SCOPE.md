# Project Space PR4：数据生产链迁移

> 状态：逐模块 Contract rollout；Data Development 已进入 `PROJECT_REQUIRED`，其余生产链按各自迁移进度继续收口。

PR4 将以下数据生产链统一接入 PR2 的可信 `CurrentProject`：

```text
Data Development
  -> Task Catalog
  -> Offline Sync
  -> Realtime Sync
  -> Workflow
```

## 目标

- 项目业务根对象直接保存 `project_id`；
- 异步执行、调度和部署记录直接保存 `project_id`；
- 从属版本、节点、事件通过父聚合继承项目；
- 创建归属只读取服务端可信项目上下文；
- 列表、详情、编辑、删除、发布、执行和运行记录查询均按项目隔离；
- Workflow、同步任务和开发任务不得引用其他项目的业务资源；
- 继续采用 `Expand -> Backfill -> Contract`，不要求所有模块在同一个 PR 同时切 `NOT NULL`。

## 数据边界

| 模块 | PROJECT_ROOT / PROJECT_PROJECTION | PROJECT_RUNTIME | INHERITED / GLOBAL |
|---|---|---|---|
| Data Development | directory、node | task execution、lineage outbox | draft、revision；Data Service Runtime 仍是相邻消费投影 |
| Task Catalog | task asset（来源投影） | - | revision snapshot |
| Offline Sync | job definition | batch execution、job execution | execution event |
| Realtime Sync | job definition | deployment | definition version、job event；compute environment 与 runtime lease 保持 GLOBAL |
| Workflow | workflow definition | schedule、execution、schedule trigger、backfill | version、node execution、attempt |

## 兼容与 Contract 策略

- Expand 阶段 Controller 使用 `PROJECT_OPTIONAL`，合法 Project Header 存在时所有项目化查询和写入使用可信项目条件；
- Data Development Stage 2 已完成历史 NULL 数据兼容回填，并将 `/api/v1/data-development/**` 收紧为 `PROJECT_REQUIRED`；
- Data Development 前端由现有 Project Switcher 提供当前项目，并只向已完成后端隔离的 API 家族附加 Project Header；
- 其他 PR4 模块仍按 `Expand -> Backfill -> 双项目验收 -> PROJECT_REQUIRED` 顺序独立切换，不能因为 Data Development 已收口就统一强切；
- 历史兼容不得使用 `OR project_id IS NULL` 弱化项目隔离；Contract 前先把 NULL 根对象和运行态明确归属到项目；
- 物理 `NOT NULL` 可以晚于 HTTP/Application Contract，在所有依赖方完成回填后再单独收紧。

## Data Development Stage 2 补充

Data Development 的 Contract 不只覆盖 HTTP：

- `lineage outbox.project_id` 在后台 Worker 中恢复为受信 `ProjectContext`；
- Outbox 与 Node 的 project identity 不一致时直接失败，不能退化成 global read；
- Data Development 来源的 Data Service Runtime mutation 必须从 Data Development authoring boundary 进入，通用 Data Service 管理接口不能绕过 `data-development:release`；
- Data Service Runtime 本身仍按相邻消费投影处理，本阶段不把整个 `yak-ops-business-data-service` 偷渡成 Project Root。

## 验收门槛

1. Project A 无法通过修改 ID 读取、编辑、运行或删除 Project B 的任务；
2. Project A 的同步任务不能引用 Project B 的 DataSource；
3. Project A 的 Workflow 不能编排 Project B 的 TaskAsset；
4. Scheduler、重试、部署 reconcile 和后台 outbox 消费能从持久化记录恢复 `project_id`；
5. 历史记录全部回填默认空间，项目化根表和运行态表不存在空项目；
6. 同一名称可以在不同项目内创建，同一项目内仍保持唯一。

Data Development 进入 `PROJECT_REQUIRED` 代表其 Contract 实现已经具备上述机制；正式生产启用前仍需按 `PR4_BACKFILL.md` 补跑数据库与双项目验收。其他模块继续按各自 PR 的验收结果切换。
