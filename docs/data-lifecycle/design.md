# 数据生命周期（TTL）—— 模块设计说明

> 配套：[requirement.md](./requirement.md)（需求基线）、[dev-plan.md](./dev-plan.md)（硬性约束）、[menu.md](./menu.md)（菜单契约）
> 模块：`yak-ops-business-lifecycle`，包根 `io.yak.ops.business.lifecycle`，表前缀 `yak_lc_`
> Flyway：自持 `db/migration/yak-lifecycle`（V1 起编，历史表 `flyway_schema_history_lifecycle`）；菜单注册 yak-security 链 `V2031`
> 错误码段：47001~47099

## 一、实体设计

### 1.1 `yak_lc_policy` TTL 策略

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AI | PK |
| project_id | BIGINT | 服务端可信上下文 |
| policy_code | VARCHAR(64) | 自动生成（层默认 `ttl_{layer小写}_default`；自定义 `ttl_custom_{ts}`），可改，项目内唯一 |
| policy_name | VARCHAR(128) | 自动生成 `{层名}默认策略` / 手填 |
| scope_type | VARCHAR(16) | `LAYER_DEFAULT` / `CUSTOM` |
| layer_code | VARCHAR(32) NULL | 仅层默认策略；唯一约束按 (project_id, layer_code) WHERE LAYER_DEFAULT（应用层保证） |
| partition_granularity | VARCHAR(8) | `DAY`/`MONTH`/`YEAR`，默认 DAY |
| hot_days | INT NULL | 热窗口（天）；NULL=无热段/永久 |
| cold_days | INT NULL | 冷边界（≥hot） |
| destroy_days | INT NULL | 删除边界；NULL=永久保留 |
| builtin | TINYINT | 1=初始化模板产生（不可删，可改） |
| status | VARCHAR(16) | `ENABLED`/`DISABLED`（停用后模型视为未配置） |
| remark / created_by / create_time / update_time / deleted | | 常规列 |

索引：`uk_policy(project_id, policy_code)`；`idx_policy_layer(project_id, layer_code)`。

### 1.2 `yak_lc_model_binding` 模型绑定（行存在=覆盖；不存在=继承层默认）

| 字段 | 类型 | 说明 |
|---|---|---|
| id, project_id | | |
| model_id | BIGINT | `uk_binding(project_id, model_id)` |
| policy_id | BIGINT | 指向自定义或层默认策略 |
| created_by, create_time, update_time, deleted | | 解绑=软删（回到继承） |

### 1.3 `yak_lc_dispatch_record` 下发流水

| 字段 | 类型 | 说明 |
|---|---|---|
| id, project_id | | |
| model_id, policy_id | BIGINT | 下发时快照引用 |
| policy_updated_at | DATETIME | 下发时策略 update_time 快照 → **漂移判定**：当前策略 update_time > 此值即 DRIFT |
| trigger_type | VARCHAR(16) | `MANUAL`/`RETRY`/`BATCH` |
| datasource_id, database_name, table_name | | 目标定位（层配置解析所得） |
| storage_type | VARCHAR(16) | `DORIS`/`PAIMON`（按方言解析结果） |
| statement | TEXT | 实际执行的语句（所见即所发） |
| status | VARCHAR(16) | `SUCCESS`/`FAILED`/`RETRYING`/`EXHAUSTED` |
| attempts | INT | 含首发；上限 5 |
| next_retry_time | DATETIME NULL | 重试闹钟扫描用 |
| error_message | TEXT NULL | |
| deleted_partitions / partition_hot / partition_cold | INT NULL | 下发时分区归类快照（监控"清理推算"用） |
| operator, create_time, finish_time | | |

索引：`idx_dispatch_model(project_id, model_id, id)`、`idx_dispatch_retry(status, next_retry_time)`。

### 1.4 `yak_lc_storage_snapshot` 存储快照（每日）

| 字段 | 类型 | 说明 |
|---|---|---|
| id, project_id, snapshot_date DATE | | `uk_snap(project_id, snapshot_date, datasource_id, table_name)` |
| layer_code, datasource_id, database_name, table_name | | |
| size_bytes BIGINT | `SHOW DATA` 采集 | |
| create_time | | |

### 1.5 `yak_lc_setting` 模块设置（单行 KV）

`project_id, setting_key('cost_price_per_gb_month'), setting_value` —— 成本单价（元/GB/月），未配置成本列显示"—"。

## 二、包结构（模板 = yak-ops-business-metric）

```
io.yak.ops.business.lifecycle
  api/          TtlPolicyApi(预留)
  config/       ConditionalOnLifecyclePersistence + LifecyclePersistenceConfiguration(yakLifecycleFlyway)
  policy/       TtlPolicyService, TtlPolicyRepository(+mapper/dao), LayerDefaultInitializer
  binding/      ModelTtlBindingService(继承解析)
  generate/     TtlStatementGenerator(纯函数), TtlStatement(view: storageType/script/writable/noOpReason)
  preview/      TtlPreviewService(SHOW PARTITIONS→归类;降级)
  dispatch/     TtlDispatchService(单/批量), TtlSqlGateway(包 DataSourceExecutionProvider 的端口+adapter)
  monitor/      TtlMonitorService(状态机推导)
  stats/        StorageSnapshotService, StorageStatsService
  schedule/     LifecycleScheduleEngineBridge/Handler/Lifecycle(命名空间 yak-ops-lifecycle;两个 job: RETRY 每30min、SNAPSHOT 每日02:00)
  controller/v1/ TtlPolicyController, TtlModelLifecycleController, TtlDispatchController, TtlMonitorController, TtlStorageController
  exception/    LifecycleException, LifecycleExceptionHandler(@RestControllerAdvice)
```

PO/枚举在 common：`io.yak.ops.common.bean.po.lifecycle.*PO`、`io.yak.ops.common.enums.lifecycle.LifecycleErrorCode/TtlStorageType/TtlGranularity/TtlDispatchStatus/TtlModelState`、常量 `common.constant.lifecycle.LifecyclePermissionCode`（`data-lifecycle:read/create/update/delete`）。

## 三、关键流程

### 3.1 继承解析（binding 模块，所有读路径复用）

```
resolvePolicyForModel(modelId):
  ModelTtlSource = ModelTtlQueryApi.resolve(modelId)        # layerCode, dialect, tableName(兜底 code)
  binding = bindingRepo.findByModel(modelId)
  policy = binding != null ? policyRepo.get(binding.policyId) : policyRepo.findLayerDefault(layerCode)
  if policy == null && layer.lifecycleDays != null:          # D1 兜底旧字段
      policy = virtual(destroy_days = lifecycleDays)          # 只读合成,不落库
  state: UNSET | (policy==null)
         APPLIED | last dispatch SUCCESS 且 policy.updated_at <= record.policy_updated_at
         DRIFT  | 有绑定/策略但无记录 或 策略更新晚于记录
         FAILED | 最近记录 FAILED/RETRYING/EXHAUSTED
```

### 3.2 语句生成映射（TtlStatementGenerator，纯函数 + 全边界单测）

| 输入 | DORIS（dynamic_partition） | PAIMON（partition expiration） |
|---|---|---|
| DAY | time_unit=DAY, start=-destroy_days, hot_partition_num=hot_days | expiration-time='{destroy} d', formatter=yyyyMMdd |
| MONTH | time_unit=MONTH, start=-destroy_days(按月计) | expiration-time='{destroy×30} d', formatter=yyyyMM |
| YEAR | time_unit=YEAR | expiration-time='{destroy×365} d', formatter=yyyy |
| destroy=NULL(永久) | `SET("dynamic_partition.enable"="false")` | no-op（说明文案，writable=false） |
| 通用 | end=3, prefix=p（D8 默认，策略高级项可改） | check-interval='1 d' |

方言判定：`dialect ∈ {DORIS, STARROCKS}` → DORIS；模型/数据源标注 Paimon（database_name 含 catalog 或 dialect=PAIMON 预留）→ PAIMON；其余 → 生成但 `writable=false`（仅可复制，不可下发，原因明示）。

### 3.3 下发

```
dispatch(modelIds, operator):
  for each: resolve → generate → (预览确认过才可发; 前端向导保证, 后端校验 previewToken 时间窗 5min)
  open(datasourceId).execute(statement) —— 超时 30s
  写 dispatch_record(SUCCESS|FAILED, next_retry=+30min)
  audit: TTL_DISPATCH (RESOURCE_UPDATED)
```

重试闹钟：扫 `status IN(FAILED,RETRYING) AND next_retry_time<=now AND attempts<5` → 重执行；attempts=5 → EXHAUSTED 进异常区。
快照闹钟：对每个启用的层库 `SHOW DATA FROM db` → upsert 当日 snapshot。

## 四、REST 契约（前缀 `/api/v1/lifecycle`，PROJECT_REQUIRED）

| 方法/路径 | 说明 |
|---|---|
| POST `/policies/page` · POST `/policies` · PUT `/policies/{id}` · DELETE `/policies/{id}` | 策略 CRUD（删有引用保护） |
| POST `/policies/initialize-layer-defaults` | 幂等初始化分层默认 |
| GET `/policies/layer-template` | 模板值（前端预填用） |
| GET `/models/{modelId}/lifecycle` | Tab 数据：policy/state/source(继承层/覆盖/兜底旧字段)/statement/previewable |
| PUT `/models/{modelId}/lifecycle/binding` · DELETE 同 | 覆盖绑定 / 解绑回继承 |
| POST `/models/lifecycle/preview` {modelIds} | 分区归类预览（批量） |
| POST `/models/lifecycle/dispatch` {modelIds, confirmToken} | 下发（批量），返回逐表结果 |
| GET `/monitor/summary` · POST `/monitor/models/page` · POST `/dispatch-records/page` · POST `/dispatch-records/{id}/retry` | 监控 |
| GET `/storage/stats` · GET `/storage/trend?days=30` · GET/PUT `/storage/setting` | 存储统计 |

错误码：47001 策略不存在；47002 编码重复；47003 分层已有默认策略；47004 策略被引用不可删；47005 段值非法（hot≤cold≤destroy）；47006 模型无时间分区；47007 数据源不支持下发；47008 下发失败；47009 预览确认过期；47010 绑定不存在。

## 五、依赖方向

`lifecycle → semantic / modeling / datasource(plugin-api) / audit`，均经 SPI，禁止反向与直读他模块表。`yak-schedule-api` 仅 lifecycle 消费（经 common 的 YakScheduleGateway）。
