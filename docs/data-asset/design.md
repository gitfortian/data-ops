# 数据资产（Asset Center）—— 模块设计说明

> 配套：[requirement.md](./requirement.md)（需求基线）、[information-map.md](./information-map.md)（信息来源矩阵与业务闭环，**读侧总账**）、[dev-plan.md](./dev-plan.md)（硬性约束）、[menu.md](./menu.md)（菜单契约）、原型 `docs/prototypes/data-asset/`
> 模块：`yak-ops-business-asset`，包根 `io.yak.ops.business.asset`，表前缀 `yak_asset_`
> Flyway：自持 `db/migration/yak-asset`（V1 起编，历史表 `flyway_schema_history_asset`）；菜单注册 yak-security 链 `V2032`
> 错误码段：48001~48099
> 核心原则：**管目录不管内容（D1）；对账复用 task-catalog 范式（D2）；一切统计服务端聚合（D11）**

---

## 一、模块定位

**职责**：跨域资产台账、自动对账盘点、上架状态机、统一负责人、跨域目录与编目规则、业务标签、健康度评分、统一搜索发现、360° 详情聚合、治理驾驶舱。

**不职责（复用 + 跳转）**：
- 不存储业务事实内容（模型结构、指标口径、数据集 schema）→ 实时读源域 SPI
- 不做血缘图存储与渲染引擎 → 内嵌/跳转 `lineage`
- 不定义安全等级/分类 → 读 `security`（`SecurityClassificationQueryApi`）
- 不定义分层/业务域字典 → 读 `semantic`
- 不做质量规则与执行 → 读 `quality`
- 不做 TTL 策略与下发 → 读 `lifecycle`
- 不做任务编目本体 → `task-catalog` 作为来源域接入
- 不做物理表元数据采集引擎 → 范围外（requirement §九）

## 二、模块依赖（单向，禁止反向）

```
modeling / metric / dataset / dashboard / task-catalog
        │实现(只读)                     ▲消费(只读)
        ▼                              │
  AssetProvider SPI ──► asset ◄── AssetCatalogApi
        │                │  │
        │                │  ├──► semantic   (LayerConfigApi / 业务域字典)
        │                │  ├──► quality    (表资产质量摘要 SPI)
        │                │  ├──► security   (SecurityClassificationQueryApi)
        │                │  ├──► lifecycle  (TtlPolicyApi 摘要)
        │                │  └──► lineage    (LineageQueryService 局部图)
        │                ▼
        └────── yak-schedule(ASSET) / audit(BusinessAuditService) / yak-security(RBAC注解)
```

- 源域 → asset 之间**只经 SPI 接口**（接口定义在 asset 的 `api/` 包或 common，实现注册在源域模块，参照 `ModelTtlQueryApi` 先例：接口由消费方定义、提供方实现）。
- 详情页 fan-out：asset 聚合调用各只读 SPI，逐分区 try-catch 容错，失败分区返回 `UNAVAILABLE` 状态（不伪造空）。

## 三、复用 + 新建清单

| 能力 | 复用/新建 | 说明 |
|------|-----------|------|
| 对账/编目范式 | 复用（照抄） | task-catalog `TaskAssetCatalogReconciler` 的分批游标 + upsert 模式 |
| 统一资产节点表 `yak_metadata_asset` | 不动（D6） | 仍归 lineage；assetKey 与其键规范对齐以便跳转 |
| 目录树 | 新建 | 跨域目录；modeling 目录不回迁（并存，详情页回显） |
| 业务标签 | 新建 | 跨域标签字典；与指标标签/security 分级三套并列 |
| 负责人 | 新建（唯一事实源） | D4 |
| 上架状态机 | 新建 | 平台无先例 |
| 健康度 | 新建（纯函数派生） | D7 |
| 浏览流水 | 新建 | 活跃度数据源；各域均无现成流水 |
| 用户选择器/审计/权限/定时 | 复用 | yak-security / audit / YakScheduleNamespaces |

## 四、实体设计（表前缀 `yak_asset_`；均含 `project_id/created_by/create_time/update_time/deleted`；无物理外键；逻辑外键用 ID/编码）

### 4.1 `yak_asset_item` 资产台账（核心）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AI | PK |
| project_id | BIGINT | 服务端可信上下文 |
| asset_key | VARCHAR(256) | **直接复用各源域血缘登记键生成器**（实测 lineage 键为小写前缀式：MODEL→`modeling:model:{modelId}`（建模表模型即血缘 TABLE 资产）、METRIC→`metric:{metricId}`、DATASET→`dataset:{id}`、CHART→`chart:analysis:{id}`、TASK 同其既有登记约定；MANUAL→`manual:{code}`），与 `yak_metadata_asset` 天然同源，不造第二套键、不做适配层；生成器全仓唯一出处（`ModelingLineageRegistrationService.modelAssetKey` / `MetricLineageRegistrationService.metricAssetKey`），provider 与血缘登记共用；`uk_item_key(project_id, asset_key)` |
| source_type | VARCHAR(16) | `MODEL`/`METRIC`/`DATASET`/`DASHBOARD`/`CHART`/`TASK`/`MANUAL` |
| source_id | VARCHAR(64) | 源域主键/编码（字符串容纳） |
| asset_type | VARCHAR(16) | 展示类型：`TABLE`/`METRIC`/`DATASET`/`DASHBOARD`/`CHART`/`TASK`/`DOC`（MANUAL 用；与 `LineageAssetType` 命名对齐） |
| name / description | VARCHAR(128)/VARCHAR(1024) | 快照，可编辑（编辑权在资产中心；META_CHANGED 确认时更新） |
| layer_code / domain_code | VARCHAR(32) NULL | 快照自源域（semantic 字典值），过滤维度 |
| directory_id | BIGINT NULL | 所属目录（`uk` 无；一资产一个主目录） |
| owner | VARCHAR(64) | 统一负责人（平台用户标识）；登记时自源域带出 |
| status | VARCHAR(16) | `PENDING`/`PUBLISHED`/`OFFLINE`/`IGNORED`（忽略抑制，对账不复活）/`SOURCE_GONE` |
| content_hash | VARCHAR(64) | 源域 descriptor 指纹 → META_CHANGED 判定 |
| source_updated_at | DATETIME NULL | 源对象最近更新时间（快照） |
| security_level_code | VARCHAR(32) NULL | **快照**自 security（对账刷新；详情页实时值优先） |
| health_score / health_grade | INT / VARCHAR(2) | 派生缓存（每日重算）；`A/B/C/D` |
| health_detail | JSON/TEXT | 评分明细（每项得分与缺口） |
| view_count_30d | INT | 派生缓存（每日聚合） |
| access_uri | VARCHAR(512) NULL | MANUAL 资产的访问入口 |
| first_listed_at / last_listed_at / last_offline_at | DATETIME NULL | 上架流水时间戳 |
| last_offline_reason | VARCHAR(512) NULL | 下架原因（必填于人工下架） |
| reconciled_at | DATETIME | 最近一次对账确认存在时间（SOURCE_GONE 判定窗口用） |

索引：`uk_item_key(project_id, asset_key)`、`idx_item_status(project_id, status)`、`idx_item_type(project_id, asset_type)`、`idx_item_dir(project_id, directory_id)`、`idx_item_owner(project_id, owner)`、`idx_item_name(project_id, name)`（搜索前缀 LIKE）。

### 4.2 `yak_asset_directory` 目录（树）

| 字段 | 类型 | 说明 |
|---|---|---|
| id, project_id | | |
| dir_code | VARCHAR(64) | 自动生成 `{父编码}_{序号}`（交互原则§3），可改，项目内唯一 |
| dir_name | VARCHAR(128) | 模板初始化时自动取层名/域名 |
| parent_id | BIGINT | 根=0 |
| path | VARCHAR(512) | 物化路径 `/1/4/9/`，子树查询与移动用 |
| sort_order / icon_key / description | | 常规 |
| builtin | TINYINT | 1=模板初始化产生（可改不可删，有子/有资产不可删） |

索引：`uk_dir_code(project_id, dir_code)`、`idx_dir_parent(project_id, parent_id, sort_order)`。

### 4.3 `yak_asset_tag` 业务标签字典 + `yak_asset_tag_rel`

- `yak_asset_tag`：`tag_code`（自动生成 `tag_{ts}`，可改）、`tag_name`、`color`（预置色板下拉）、`description`、`builtin`。`uk(project_id, tag_code)`。
- `yak_asset_tag_rel`：`asset_id` + `tag_id`，`uk_rel(project_id, asset_id, tag_id)`；批量打标/去标事务内完成。

### 4.4 `yak_asset_assign_rule` 编目规则

| 字段 | 类型 | 说明 |
|---|---|---|
| id, project_id | | |
| rule_name | VARCHAR(128) | 手填（给示例） |
| rule_type | VARCHAR(16) | `DIRECTORY`（归目录）/`TAG`（打标签） |
| conditions | TEXT(JSON) | `{assetTypes:[], layerCodes:[], domainCodes:[], nameRegex:"", keyword:"", sourceTypes:[]}`，全部可空=通配，多条件 AND |
| target_directory_id / target_tag_id | BIGINT | 按 rule_type 二选一 |
| priority | INT | 小者优先；同类型首条命中即停 |
| enabled | TINYINT | 默认 0，**试跑通过后才可启用**（D10） |
| last_apply_hit | INT NULL | 最近试跑/重应用命中数（列表展示） |

### 4.5 `yak_asset_change_record` 盘点变更记录

| 字段 | 类型 | 说明 |
|---|---|---|
| id, project_id | | |
| asset_id | BIGINT | 关联台账（NEW 时即已建行） |
| change_type | VARCHAR(16) | `NEW`/`META_CHANGED`/`SOURCE_GONE`/`REAPPEARED` |
| diff | TEXT(JSON) | 变更前后字段级差异（META_CHANGED）；GONE 记录消失时间 |
| handle_status | VARCHAR(16) | `OPEN`/`CONFIRMED`/`IGNORED` |
| snapshot_* | | 源域新值快照（确认前详情页提示"源域已变"） |
| handled_by / handled_at | | 确认人/时间 |

索引：`idx_change_handle(project_id, handle_status, change_type)`、`idx_change_asset(project_id, asset_id, id)`。

### 4.6 `yak_asset_view_record` 浏览流水

`(project_id, asset_id, viewer, view_time, entry)`；复合索引 `idx_view_asset(project_id, asset_id, view_time)`、`idx_view_time(project_id, view_time)`。只写不读详情（聚合走 SQL）；保留 90 天，定时清理（与生命周期理念一致，配置写死本期不做页面）。

### 4.7 `yak_asset_health_snapshot` 健康度快照（P2，趋势用）

`(project_id, snapshot_date, layer_code, grade_a/b/c/d 计数, published_count, ...)` **按日聚合而非按资产**，控制体量；资产级历史本期不存。

### 4.8 `yak_asset_setting` 模块设置（单行 KV）

SOURCE_GONE 判定窗口天数（默认 7）、对账开关、概览单价类未来扩展位。

## 五、包结构（模板 = yak-ops-business-lifecycle/metric）

```
io.yak.ops.business.asset
├── api/            AssetProvider(接口,源域实现) · AssetDescriptor · AssetCatalogApi(对外提供)
├── controller/v1/  AssetController · AssetDirectoryController · AssetRuleController
│                   · AssetTagController · AssetInventoryController · AssetOverviewController
├── application/    AssetAppService(台账/状态机) · AssetDiscoverService(搜索/详情聚合)
│                   · AssetOverviewService(聚合KPI)
├── reconcile/      AssetReconcileService(编排) · AssetProviderRegistry(多bean收集)
│                   · ChangeRecorder(NEW/CHANGED/GONE 判定与落库)
├── catalog/        DirectoryService(树/物化路径) · AssignRuleService(匹配+试跑) · TagService
├── health/         HealthScorer(纯函数) · HealthRecomputeService(定时入口)
├── stat/           ViewRecorder(限流写) · HealthSnapshotJob
└── dao/            mapper/ + model/(PO 在 common: io.yak.ops.common.bean.po.asset)
```

common 侧：`enums/asset/AssetErrorCode`(48001~48099)、`enums/asset/AssetStatus`、`enums/asset/AssetSourceType`、`constant/asset/AssetPermissionCode`（`data-asset:read/create/update/delete`）。

## 六、关键流程

### 6.1 对账（Reconcile）

```
触发(每日 schedule / 手动按钮) ── 全局互斥锁(防重入)
 └─ for provider in AssetProviderRegistry.enabled():
      cursor = 上次游标
      loop: batch = provider.listSince(projectId, cursor, ≤500)   # 分批有上限
        upsert item(按 asset_key):
          不存在 → 建行 status=PENDING + 套编目规则(预目录/预标签) + 继承owner(源创建人)
                   + change NEW
          存在且 IGNORED → 仅刷 reconciled_at 不复活；content_hash 变化 → 回 PENDING + change NEW
          存在   → content_hash ≠ descriptor.hash → 写 change META_CHANGED(快照新值,
                   台账展示字段不自动覆盖, PUBLISHED 资产详情页挂"源已变更"黄标)
                   无变化 → 仅刷 reconciled_at
      全量后: reconciled_at 落后于(现在-窗口天数) 且非 SOURCE_GONE → status=SOURCE_GONE + change 记录
 GONE 资产在源重新出现 → REAPPEARED, 恢复原状态, 待确认
```

- 单 provider 失败：记日志 + 概览"源接入"红点，不影响其他 provider（分区容错）。
- MANUAL 资产不参与对账。

### 6.2 上架 / 下架

```
上架(单个/批量) → 预检: 负责人? 描述? 目录? 定级(建议)? 
   ├─ 全过 → 状态 PUBLISHED, last_listed_at, 审计(无任何血缘登记动作——D6:
   │         跳转由 asset_key 与 lineage 键同源保证)
   └─ 有缺 → 返回缺口清单; 每项给"一键补齐"默认(负责人=当前用户/描述取源域/目录=未分类)
             可"带风险上架"(风险项写入审计 detail)
下架(单个/批量) → 必填原因 → 预览受影响(引用该资产的报表数等, 读SPI, 可降级) → 确认 → OFFLINE
```

- 预检与批量执行分离：`precheck` 返回带 5 分钟时效 token，`publish` 必带（对齐 lifecycle"预览确认 token"防盲发先例）。

### 6.3 健康度重算（每日 + 上架/打标/确认变更时即时重算单资产）

```
输入: item + 各域摘要(质量分/血缘存在/定级/注释覆盖率/下游引用/浏览/新鲜度)
HealthScorer: 纯函数 → score/grade/detail(逐项得分与缺口文案)
失败依赖: 该项按 0 分并在 detail 标注"数据不可用"(不伪造)
不适用项(如非物理对象类的质量/定级、MANUAL 的血缘): N/A, 剔出分母
   → score = Σ实得分 / Σ适用项满分 × 100
```

### 6.4 详情聚合（360°）

```
GET /assets/{id} → item 本体(必成) + 并行 fan-out:
  源域属性(经 source_type 路由到 provider.fetchDetail) 
  字段(provider 或 dataset schema) · 血缘局部图(lineage, 1跳) · 质量(quality)
  安全(security.SecurityClassificationQueryApi) · TTL(lifecycle) · 趋势(自有流水)
每分区独立超时/容错 → {status: OK|UNAVAILABLE}; 浏览流水异步写(采样限流: 同用户同资产5分钟去重)
```

### 6.5 发现排序（固定公式，不做动态评分）

```
default_score = health_score × 0.6 + min(40, log2(1 + view_count_30d) × 8) × 0.4
```

- 输入全部为台账派生列（health_score / view_count_30d 每日重算缓存），列表查询零跨域调用；
- 用户切换排序列（时间/浏览/名称）时放弃公式分，走普通列排序。

## 七、REST 契约（前缀 `/api/v1/assets`，PROJECT_REQUIRED，权限注解按 menu.md）

| 方法/路径 | 说明 |
|---|---|
| GET `/overview` | 驾驶舱：KPI + 分布 + 待办计数 + 最近动态（固定 ≤8 查询） |
| GET `` | 列表/搜索：keyword, assetTypes, layerCodes, statuses, tags, directoryId, grades, sortBy(默认 health×活跃度加权), 分页 |
| GET `/{id}` | 360° 详情（分区容错结构见 6.4） |
| POST `` | 手工登记（仅 source_type=MANUAL） |
| PUT `/{id}` | 编辑快照字段（名称/描述/访问入口） |
| PUT `/{id}/owner` | 变更负责人 |
| POST `/{id}/tags` / DELETE `/{id}/tags/{tagId}` | 打标/去标 |
| POST `/precheck` | 上架预检（body: ids[]）→ 缺口清单 + token |
| POST `/publish` | 上架（ids + token；批量） |
| POST `/offline` | 下架（ids + reason） |
| DELETE `/{id}` | 删除（仅 OFFLINE/SOURCE_GONE 且非 MANUAL 待确认态；软删） |
| POST `/batch/move-directory` | 批量移目录 |
| GET/POST/PUT/DELETE `/directories` (+`/tree`, `/init-template`, `/move`) | 目录维护；init-template=按分层+域一键初始化 |
| GET/POST/PUT/DELETE `/rules` (+`/rules/{id}/dry-run`, `/rules/apply-again`) | 编目规则；dry-run 返回命中数+前 50 样例 |
| GET/POST/PUT/DELETE `/tags` | 标签字典 |
| POST `/reconcile` | 手动触发对账（异步，返回受理） |
| GET `/reconcile/status` | 各 provider 最近对账状态/时间/错误 |
| GET `/changes` | 盘点变更列表（handle_status 过滤） |
| POST `/changes/{id}/confirm` / `/ignore` | 确认（覆盖快照字段）/忽略 |
| GET `/{id}/views` | 浏览趋势（近 30 天） |
| POST `/{id}/view` | 浏览上报（前端进详情时调用，服务端限流去重） |

## 八、SPI 契约

### 8.1 消费向：`AssetProvider`（asset 定义接口，源域实现并注册为 Spring Bean）

```java
public interface AssetProvider {
    AssetSourceType sourceType();                                  // MODEL/METRIC/DATASET/...
    AssetPage cursorList(AssetCursorQuery q);  // projectId, updatedAfter, cursor, limit≤500
    Optional<AssetDescriptor> refresh(String sourceId);            // 详情聚合的源域属性块
}
// AssetDescriptor: assetKey(源域血缘键原文), sourceId, name, description, assetType,
//                  layerCode, domainCode, suggestedOwner, updatedAt, contentHash,
//                  extra(源域私有属性, 只读展示)
```

- 各源域实现**只读本域**既有 Service/dao（本域内部不受跨域 SPI 纪律约束），不改任何既有签名；对账/调度上下文无 `CurrentProject`，故 provider 按 `query.projectId()` 显式过滤（ticket 94 实做）。
- `contentHash` 只用源域自有字段（跨域引用取 ID 而非解析后的 code），依赖侧抖动不产生误变更。
- task-catalog 实现直接包装 `yak_task_asset` 查询；dataset/dashboard 复用其既有 list 能力加分页封装。

### 8.2 提供向

| SPI | 方法 | 消费方 | 语义 |
|---|---|---|---|
| `AssetCatalogApi` | `AssetBrief find(key)` / `AssetCountSummary summary(projectId)` | home、data-service | 只读摘要；域不可用时抛异常而非回 0 |
| `AssetHealthApi`（预留） | `HealthSummary healthByModel(modelId)` | modeling 详情页角标 | 返回 null=未编目 |

### 8.3 源域需新增的只读 SPI（均为"新增接口，不动旧签名"）

| 提供方 | 接口 | 用途 |
|---|---|---|
| modeling/metric/dataset/dashboard/task-catalog | `AssetProvider` 实现 ×5 | 对账 |
| quality | `TableQualitySummaryApi`（若无则新增） | 可信度评分 + 详情质量块；quality 的"表资产注册"是**监控对象清单**而非全量盘点，**不作为 TABLE provider 来源**，仅用其登记记录判定"质量监控覆盖" |
| lineage | 既有 `LineageQueryService` 局部查询复用 | 详情血缘块 |
| lifecycle | `TtlPolicyApi.summaryByModel`（lifecycle 设计已预留、**尚未实现**；未就绪时生命周期块降级"暂不可用"） | 详情生命周期块 + 可信度新鲜度参考 |
| security | `SecurityClassificationQueryApi`（已实现） | 定级快照 + 详情安全块 |

> 全部读侧接口的**信息来源、层级（L1/L2/L3）、降级策略与缺口归属 ticket** 以 [information-map.md](./information-map.md) §二/§四 为总账（含 G1~G6），本表不重复维护。

## 九、定时任务（`YakScheduleNamespaces` 新增 `ASSET`）

| 任务 | 频率 | 内容 |
|---|---|---|
| `asset-reconcile` | 每日 02:00 | 全 provider 对账（§6.1） |
| `asset-health-recompute` | 每日 03:00 | 全量健康度重算 + 浏览数聚合 + view_record 清理 |
| `asset-health-snapshot` | 每日 04:00 | 概览级健康分布快照（P2） |

## 十、前端结构（`yak-ops-ui/src/pages/data-asset/`）

```
data-asset/
├── index.tsx            概览(驾驶舱)
├── catalog/index.tsx    资产目录(左树右表/卡片, 搜索过滤)
├── detail/[id].tsx      资产360°(隐藏路由, Tab 分区, 血缘块复用 lineage 组件)
├── inventory/index.tsx  盘点上架(待上架池/变更确认/源接入 三段 Tab)
├── taxonomy/index.tsx   目录与标签(目录树/编目规则/标签字典 Tab)
└── components/          HealthRing · AssetCard · PublishPrecheckModal · OwnerPicker(复用) · StatusTag
```

- 登记 `securityMenuCodes.ts` + `navigation.ts` + `navigationMenuContract.test.ts` 迁移号 V2032（流程同 lifecycle menu.md §前端登记）。
- 视觉：antd 5 浅色、品牌色 #FE2C55；健康度用环形进度+等级色（A绿/B蓝/C橙/D红）。

## 十一、错误码（`AssetErrorCode`，48001~48099）

48001 资产不存在；48002 asset_key 重复；48003 状态不允许该操作（如 PUBLISHED 直接删）；48004 上架预检未通过且未接受风险；48005 预检 token 无效或过期；48006 目录不存在或有环；48007 目录非空不可删；48008 模板已初始化（幂等提示）；48009 标签编码重复；48010 规则未试跑不可启用；48011 provider 不可用（对账入口报错）；48012 对账进行中（互斥锁）；48013 下架原因必填；48014 变更记录已处理；48015 MANUAL 资产不可被对账操作；48016 仅 PENDING/OFFLINE 可忽略（IGNORED），已上架资产必须先下架；48017 通用参数不合法（枚举解析等）。

## 十二、测试与验收锚点

- 纯函数单测全覆盖：`HealthScorer`（每个缺口组合）、content_hash 判定、规则匹配（AND/优先级/通配）。
- 对账契约测试：mock provider 抛异常 → 其余 provider 正常、失败可见；SOURCE_GONE 窗口边界。
- 架构约束测试（对齐 quality 模块先例 `QualityLayeringConventionTest`）：asset 不得依赖源域内部包，只准经 `api/`。
- 前端：目录页搜索/过滤 E2E、上架向导（预检→补默认→确认）E2E。
