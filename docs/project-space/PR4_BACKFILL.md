# Project Space PR4 回填与切换手册

> 适用范围：Data Development、Task Catalog、Offline Sync、Realtime Sync、Workflow。  
> 当前阶段：`PROJECT_OPTIONAL` + nullable `project_id`。  
> 目标：在切换 `PROJECT_REQUIRED` 和 `NOT NULL` 之前，安全完成历史数据归属与双项目隔离验收。

## 1. 执行原则

- 不在 Flyway 中硬编码默认项目 ID；默认空间来自 Yak Security Project。
- 先备份，再统计，再回填，再验证；所有更新都只处理 `project_id IS NULL`。
- 根对象先回填，运行态和投影数据再从根对象继承。
- 来源关系不清晰的数据必须人工确认，不能为了消除 NULL 随意归入某个项目。
- 本手册中的 `:default_project_id` 是执行工具的绑定变量，不是字面 SQL。

建议先记录待回填数量：

```sql
SELECT 'yak_dev_directory', COUNT(*) FROM yak_dev_directory WHERE project_id IS NULL
UNION ALL SELECT 'yak_dev_node', COUNT(*) FROM yak_dev_node WHERE project_id IS NULL
UNION ALL SELECT 'yak_task_asset', COUNT(*) FROM yak_task_asset WHERE project_id IS NULL
UNION ALL SELECT 'yak_offline_job_definition', COUNT(*) FROM yak_offline_job_definition WHERE project_id IS NULL
UNION ALL SELECT 'yak_realtime_job_definition', COUNT(*) FROM yak_realtime_job_definition WHERE project_id IS NULL
UNION ALL SELECT 'yak_workflow_definition', COUNT(*) FROM yak_workflow_definition WHERE project_id IS NULL;
```

## 2. Data Development

目录和已有节点先归入默认空间：

```sql
UPDATE yak_dev_directory
SET project_id = :default_project_id
WHERE project_id IS NULL;

UPDATE yak_dev_node
SET project_id = :default_project_id
WHERE project_id IS NULL;
```

运行记录与血缘 Outbox 必须从节点继承：

```sql
UPDATE yak_dev_task_execution e
JOIN yak_dev_node n ON n.id = e.node_id
SET e.project_id = n.project_id
WHERE e.project_id IS NULL;

UPDATE yak_dev_lineage_outbox o
JOIN yak_dev_node n ON n.id = o.node_id
SET o.project_id = n.project_id
WHERE o.project_id IS NULL;
```

校验：

```sql
SELECT e.id, e.node_id, e.project_id, n.project_id AS node_project_id
FROM yak_dev_task_execution e
JOIN yak_dev_node n ON n.id = e.node_id
WHERE e.project_id IS NULL OR e.project_id <> n.project_id;

SELECT o.task_id, o.node_id, o.project_id, n.project_id AS node_project_id
FROM yak_dev_lineage_outbox o
JOIN yak_dev_node n ON n.id = o.node_id
WHERE o.project_id IS NULL OR o.project_id <> n.project_id;
```

## 3. Task Catalog

Task Catalog 是源任务投影，优先从 Data Development 节点恢复项目：

```sql
UPDATE yak_task_asset a
JOIN yak_dev_node n
  ON a.source = 'DATA_DEVELOPMENT'
 AND a.source_ref = CAST(n.id AS CHAR)
SET a.project_id = n.project_id
WHERE a.project_id IS NULL;
```

其他来源必须按各自 Source Truth 回填。只有确认历史资产全部属于默认空间后，才执行兜底：

```sql
UPDATE yak_task_asset
SET project_id = :default_project_id
WHERE project_id IS NULL;
```

不要在未核对 `source/source_ref` 的情况下直接执行兜底。

## 4. Offline Sync

定义属于项目；Batch 和 Execution 从定义继承：

```sql
UPDATE yak_offline_job_definition
SET project_id = :default_project_id
WHERE project_id IS NULL;

UPDATE yak_offline_batch_execution b
JOIN yak_offline_job_definition d ON d.id = b.job_definition_id
SET b.project_id = d.project_id
WHERE b.project_id IS NULL;

UPDATE yak_offline_job_execution e
JOIN yak_offline_job_definition d ON d.id = e.job_definition_id
SET e.project_id = d.project_id
WHERE e.project_id IS NULL;
```

校验运行态与定义一致：

```sql
SELECT e.id, e.job_definition_id, e.project_id, d.project_id AS definition_project_id
FROM yak_offline_job_execution e
JOIN yak_offline_job_definition d ON d.id = e.job_definition_id
WHERE e.project_id IS NULL OR e.project_id <> d.project_id;
```

还必须检查每个定义引用的 Source/Sink DataSource 与定义属于同一项目。

## 5. Realtime Sync

Compute Environment 和 Runtime Lease 继续是全局平台能力；任务定义与 Deployment 属于项目：

```sql
UPDATE yak_realtime_job_definition
SET project_id = :default_project_id
WHERE project_id IS NULL;

UPDATE yak_realtime_job_deployment p
JOIN yak_realtime_job_definition d ON d.id = p.definition_id
SET p.project_id = d.project_id
WHERE p.project_id IS NULL;
```

校验：

```sql
SELECT p.id, p.definition_id, p.project_id, d.project_id AS definition_project_id
FROM yak_realtime_job_deployment p
JOIN yak_realtime_job_definition d ON d.id = p.definition_id
WHERE p.project_id IS NULL OR p.project_id <> d.project_id;
```

## 6. Workflow

工作流定义先归入默认空间；Schedule、Backfill、Trigger 和 Execution 从所属定义继承：

```sql
UPDATE yak_workflow_definition
SET project_id = :default_project_id
WHERE project_id IS NULL;

UPDATE yak_workflow_schedule s
JOIN yak_workflow_definition d ON d.id = s.workflow_id
SET s.project_id = d.project_id
WHERE s.project_id IS NULL;

UPDATE yak_workflow_backfill b
JOIN yak_workflow_definition d ON d.id = b.workflow_id
SET b.project_id = d.project_id
WHERE b.project_id IS NULL;

UPDATE yak_workflow_schedule_trigger t
JOIN yak_workflow_definition d ON d.id = t.workflow_id
SET t.project_id = d.project_id
WHERE t.project_id IS NULL;

UPDATE yak_workflow_execution e
JOIN yak_workflow_version v ON v.id = e.definition_id
JOIN yak_workflow_definition d ON d.id = v.workflow_id
SET e.project_id = d.project_id
WHERE e.project_id IS NULL;
```

重启/重跑实例可从来源执行继承：

```sql
UPDATE yak_workflow_execution e
JOIN yak_workflow_execution source ON source.id = e.source_execution_id
SET e.project_id = source.project_id
WHERE e.project_id IS NULL
  AND source.project_id IS NOT NULL;
```

仍为空的 Ad-hoc/Test Run 必须先核对来源。确认它们属于默认空间后才兜底：

```sql
UPDATE yak_workflow_execution
SET project_id = :default_project_id
WHERE project_id IS NULL;
```

## 7. 全局一致性检查

```sql
SELECT 'yak_dev_directory' AS table_name, COUNT(*) AS null_count
FROM yak_dev_directory WHERE project_id IS NULL
UNION ALL SELECT 'yak_dev_node', COUNT(*) FROM yak_dev_node WHERE project_id IS NULL
UNION ALL SELECT 'yak_dev_task_execution', COUNT(*) FROM yak_dev_task_execution WHERE project_id IS NULL
UNION ALL SELECT 'yak_dev_lineage_outbox', COUNT(*) FROM yak_dev_lineage_outbox WHERE project_id IS NULL
UNION ALL SELECT 'yak_task_asset', COUNT(*) FROM yak_task_asset WHERE project_id IS NULL
UNION ALL SELECT 'yak_offline_job_definition', COUNT(*) FROM yak_offline_job_definition WHERE project_id IS NULL
UNION ALL SELECT 'yak_offline_batch_execution', COUNT(*) FROM yak_offline_batch_execution WHERE project_id IS NULL
UNION ALL SELECT 'yak_offline_job_execution', COUNT(*) FROM yak_offline_job_execution WHERE project_id IS NULL
UNION ALL SELECT 'yak_realtime_job_definition', COUNT(*) FROM yak_realtime_job_definition WHERE project_id IS NULL
UNION ALL SELECT 'yak_realtime_job_deployment', COUNT(*) FROM yak_realtime_job_deployment WHERE project_id IS NULL
UNION ALL SELECT 'yak_workflow_definition', COUNT(*) FROM yak_workflow_definition WHERE project_id IS NULL
UNION ALL SELECT 'yak_workflow_execution', COUNT(*) FROM yak_workflow_execution WHERE project_id IS NULL
UNION ALL SELECT 'yak_workflow_schedule', COUNT(*) FROM yak_workflow_schedule WHERE project_id IS NULL
UNION ALL SELECT 'yak_workflow_schedule_trigger', COUNT(*) FROM yak_workflow_schedule_trigger WHERE project_id IS NULL
UNION ALL SELECT 'yak_workflow_backfill', COUNT(*) FROM yak_workflow_backfill WHERE project_id IS NULL;
```

所有结果必须为 `0`，并且父子项目不一致查询必须返回空集。

## 8. 双项目验收

准备 Project A、Project B，各放一组同名对象，至少验证：

1. 两个项目可创建同名目录、离线任务、实时任务和工作流。
2. A 的列表、详情、编辑、删除、执行、重试、日志和调度记录均看不到 B。
3. 修改 URL、任务 ID、Execution ID、Schedule ID、Trigger ID 访问 B 时返回不存在或被拒绝。
4. Offline/Realtime 任务不能引用另一项目的 DataSource。
5. Workflow 不能引用另一项目的 TaskAsset，调度与 Backfill 不能绑定另一项目的 Workflow。
6. 后台重试、Reconcile、Schedule Trigger 和 Outbox 消费后，运行记录仍保留正确 `project_id`。

## 9. Contract 切换

只有在以下条件全部满足后才能进入收口 PR：

- 上述 NULL 和父子不一致检查全部通过；
- 双项目接口与后台任务验收通过；
- 所有 PR4 Controller 切换为 `PROJECT_REQUIRED`；
- 前端已能稳定选择并保存当前项目；
- 再把根对象和运行态表的 `project_id` 修改为 `NOT NULL`；
- 项目空间菜单和项目切换器仍应等 PR5 全模块完成后再开放。
