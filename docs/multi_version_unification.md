# 重要业务对象统一多版本 · 设计基线与实施方案

> 日期：2026-09-21 范围：`data-ops-business/*` 全部需版本化对象 + `data-ops-ui`
> 定位：[multi_version.md](./multi_version.md)（2026-09-19 能力评审）的**立项落地版**。本文所有现状均已按 2026-09-21 代码重新核实（9-20/9-21 大量提交后），并修正了评审文档的两处表述错误。
> 结构：一、对象分域 → 二、现状档案 → 三、统一契约 → 四、共享支撑 → 五、逐对象迁移方案 → 六、实施批次 → 七、待拍板裁决点。
> 工单拆分：见 [multi_version/README.md](./multi_version/README.md)（2026-09-21 按 Wave 落为 S/W1/W2/W3 四卷可执行 issue）。

## 一、对象分域与立项范围

按「是否影响线上执行正确性」分三域：

| 域 | 判定 | 对象 |
|---|---|---|
| **A：必须真闭环**（草稿→发布→历史→回滚，编辑不影响线上） | 配置错误会直接打到生产/执行 | 开发任务、模型、TTL 策略、离线同步作业、实时同步作业、工作流定义、数据服务节点、质量监控、数据标准、指标 |
| **B：版本语义纠偏**（已有版本载体，语义或 UI 残缺） | 有快照/流水但非发布触发或不可回切 | dataset、metric（变更流水→发布触发）、semantic（改前快照→当前版快照）、workflow/dataset/realtime（缺回切端点） |
| **C：明确不做版本化**（判定归档，避免反复评审） | 回滚旧值有安全/语义风险 | datasource 连接参数（补 diff 审计+改密重测连通）、资源文件、系统环境变量、task-catalog（current-revision 指针登记表，回滚由发布中心代理） |

已具备完整闭环、仅需向契约收敛的：digital-screen（范式）、dashboard、data-development 开发任务。asset 的 `yak_asset_change_record` 已于 M2-2 接上写入方（`AssetReconcileService.java:517`、`AssetUpsertRepository.java:243,320`），定位是**对账漂移台账**而非配置版本史，不列入 A 域。

## 二、现状档案（2026-09-21 核实）

### 2.1 能力矩阵（对 multi_version.md 矩阵的状态更新）

| 对象 | 评审结论 | 9-21 复核 | 关键证据 |
|---|---|---|---|
| lifecycle TTL | P0 最高风险 | **仍存在**，就地覆盖+空审计未动 | `TtlPolicyService.java:192-202`；lifecycle 仅 V1 迁移无版本表 |
| sync-offline | P0 | **仍存在**，definitionJson 原地覆盖 | `OfflineJobDefinitionService.java:257,282,297-310` |
| modeling | P0 | **仍存在**（仅回滚按钮加了 Popconfirm） | `ModelStructureService.java:126` 置回 DRAFT；`ModelVersionService.java:114,121-125` 回滚即发布；`detail.tsx:1496-1510` 发布不先保存 |
| workflow | P0 | **仍存在**，`DefinitionState` 死枚举、无 activate 端点、版本抽屉只读 | `DefinitionState.java:4`；`WorkflowDefinitionController.java:146` 仅 list；`WorkflowToolbar.tsx:213-280` |
| mdm 审批 | P0 假闭环 | **部分修复**：前端台账/版本 diff 已接（`services/mdm/api.ts:252-291`、`pages/mdm/approval/index.tsx`、`ChangeHistoryTab.tsx:67-83` 有属性级 diff）；**实体/属性/规则配置侧仍零版本** | `MdmEntityService.java:101-126` PUT 就地生效，审计 `Map.of()` |
| metric | P1 | **部分修复**：版本列表 UI 与 getVersion 端点已建，但快照仍为自动流水、snapshot 不渲染、无回滚 | `MetricCatalogService.java:139,211,248`；`MetricVersionController.java:50-60` 无调用方 |
| semantic 标准 | P1 | **仍存在**，payload_json 仍存"改前"状态，全模块零 rollback；业务过程/域无状态无版本 | `StandardCatalogService.java:149-150`；`SemanticProcessPO/DomainPO` 无 status/version |
| dataset | P1 | **仍存在**，无回切、上下线与版本不绑 | `DatasetVersionWriter.java:81` 唯一写点即移指针；`DatasetManager.java:29-42` |
| quality | P1 | **仍存在**，revision 表仅服务 workflow 执行固定，零端点零 UI | `QualityTaskRevisionProvider.java:16-45` |
| realtime sync | P1 | **部分修复**：publish/apply-published-version 已有且前端接了按钮；仍无 GET /versions、不能按旧版回切 | `RealtimeJobController.java:137,178` |
| 大屏/仪表盘/开发任务 | 完整 | 完整（范式参照） | 见 2.2 |

### 2.2 既有实现横向档案（统一契约的输入）

| # | 对象 | 版本表 | 快照粒度 | 版本号 | 回滚语义 | 端点 | 主表指针 |
|---|---|---|---|---|---|---|---|
| 1 | digital-screen | `yak_digital_screen_version` | 分列全量 | MAX+1 | **追加式**：恢复草稿+新版+置发布（范式，`DigitalScreenPublisher.java:51-73`） | publish/offline/versions/`{no}/rollback` | published_version_id + revision/published_revision |
| 2 | dev-task | `yak_dev_task_revision`（+data-service 同构双表） | 全量 JSON+SHA256 | MAX+1(SQL)，(node_id,checksum) 幂等索引 | **指针回切**：activate 直指历史 revision 不追加（`DevelopmentReleaseService.java:119-140`） | draft/publish/revisions/releases/activate/{no} | 无（发布中心读 catalog） |
| 3 | dashboard | 版本头表+**子表按 version_id 分行** | 结构化分行 | MAX+1 | restore=历史→新草稿；activate 已 @Deprecated | publish/versions/restore/{no} | current_version_id + published_version_id 双指针 |
| 4 | modeling | `yak_modeling_model_version`（V16 迁移） | structure_json 全量（不含元数据） | **selectCount+1（有删除计数风险）** | 追加但**立即置 PUBLISHED**、覆盖草稿 | publish/versions/`{no}/rollback` | published_version_id + latest_version_no |
| 5 | workflow | `yak_workflow_version` | 全量引擎 JSON | 聚合内 +1 | 无端点；active_version_id pin 执行 | online/offline/versions | active_version_id(String) |
| 6 | realtime | `yak_realtime_definition_version` | definition_json+双 digest | MAX+1 | apply 仅收敛最新版 | publish/apply-published-version | published_definition_version_id |
| 7 | dataset | `yak_dataset_version` | 混合：SQL 型全量 / 任务源为 revision **指针** | MAX+1 | 只追加无回切 | publish/versions/online/offline | current_version_id |
| 8 | semantic 标准 | `yak_semantic_standard_version`（V3） | payload=**改前状态（语义倒置实锤）** | 主表 version 自增 | 无 | PUT 即快照+GET versions | 无 |
| 9 | quality | `yak_quality_monitor_revision`（V2） | definition_json+SHA256，checksum 幂等复用 | MAX+1 | 无用户面 | 零端点 | 无 |
| 10 | metric | `yak_metric_version` | 手写 toJsonSnapshot（**漏 compositions**，`MetricCatalogService.java:544-592`） | 主表 version 直落；**级联物理删版本行**（:299-301） | 无（变更流水） | 自动写入 | 无 |
| 11 | mdm 记录 | `yak_mdm_record_version`（V15） | attributes 全量+change_id 溯源 | 与 record.version 对齐 | 无回滚 | submit/withdraw 专属，approve/reject 在通用审批中心 | 主表 version |

**对 multi_version.md 的两处修正**：
1. 大屏回滚端点实际命名为 `/versions/{versionNo}/rollback` 而非 activate（docs §六.3 引用有误）；全库唯一 `activate/{revisionNo}` 命名在 dev-task 发布中心。
2. MDM 的 approve/reject 不在 `MdmApprovalController`，走通用 `ApprovalController:98,110`；专属控制器只有 submit/withdraw/查询。

**已核实的共性（可直接固化为契约）**：
- 版本表一律 append-only + `uk(biz_id, version_no)` + create_time；版本号从 1 递增。
- 5 个模块自发形成「**checksum 不变则不产生新版本**」的幂等发布心智，应上升为框架默认。
- 主表普遍收敛于「发布指针 published_version_id + 草稿修订 revision」两件套。
- **无任何公共版本化抽象**：common/core/spi 中 grep `VersionSupport|AbstractVersion|统一状态枚举引用` 零命中；`AuditTransactions.java` 被逐字复制 8 份；前端 5 处版本 UI 各自手写，零 diff 组件。

## 三、统一契约（Single Source of Truth）

以下五条为本项目多版本的**规范条款**，所有 A/B 域对象的改造与新增一律对齐；偏离需在 §七 立项裁决。

### C1 状态机
- 共享枚举落 `data-ops-common`：`PublishState { DRAFT, PUBLISHED, OFFLINE }`。现成候选：`DefinitionState.java`（已存在、零引用、取值恰好一致）→ 复活并全项目引用，禁字符串字面量。
- ENABLED/DISABLED（semantic、metric、quality）保留为**可用性开关**，与发布态正交：状态机管"哪版生效"，开关管"整体是否可用"。
- dataset 补 DRAFT 语义（当前仅 ONLINE/OFFLINE）。

### C2 版本表 DDL 规范
```sql
CREATE TABLE <biz>_version (
  id            BIGINT PRIMARY KEY,
  <biz>_id      BIGINT NOT NULL,
  project_id    BIGINT,
  version_no    INT NOT NULL,
  payload_json  LONGTEXT NOT NULL,   -- 当前已发布/待发布内容的【全量】快照
  checksum      CHAR(64) NOT NULL,   -- SHA-256(规范化 JSON)
  source_draft_revision BIGINT,      -- 对应草稿修订号（幂等发布用）
  created_by    VARCHAR(64), created_at DATETIME,   -- 命名统一 created_by/at
  UNIQUE KEY uk (<biz>_id, version_no)
);  -- 只追加；禁止物理删除版本行（metric 现状违规，需改）
```
- 快照粒度统一为**单 payload_json 全量 blob**（含名称/描述等元数据，修 modeling 只存结构的缺陷）。dashboard 子表分行是唯一豁免（结构化恢复需要，见 §七 Q2）。
- 主表标准字段：`status`、`published_version_id`、`latest_version_no`、`draft_revision`（乐观锁计数器，前端 PUT 携带）。
- 版本号一律 `MAX(version_no)+1` 查询式（禁 selectCount+1，修 modeling）。Flyway 迁移一旦应用不可改（项目约束），新表一律新 V 号。

### C3 端点命名规范
```
PUT  /{id}/draft                     保存草稿（不影响线上；draft_revision 自增）
POST /{id}/publish                   发布当前草稿 → 追加版本+移指针（checksum 幂等：与当前已发布相同则 no-op）
POST /{id}/offline                   下线
GET  /{id}/versions                  版本列表（倒序）
GET  /{id}/versions/{no}             单版快照内容（前端 diff 数据源）
POST /{id}/versions/{no}/rollback    回滚
```
- 回滚统一为**追加式 activate**（digital-screen 语义：恢复内容+追加新版本+置发布态；append-only，不抹历史）。dev-task 指针回切为存量例外，保留但新对象不得仿（§七 Q1）。
- 命名统一用 `rollback`（大屏/modeling 现状即此，两处已占用）；`activate/restore/apply-published-version` 为待收敛别名。

### C4 草稿物理分离
- 草稿与已发布内容**不得同列**：dev-task 双表（draft/revision）为正解；modeling「活表就地覆盖+置回 DRAFT」为反例，消费方（血缘/指标/派生/DDL 下发）必须读 `published_version_id` 指向的快照而非活表。

### C5 审计
- 所有 publish/offline/rollback 与 `*_UPDATE` 审计必须携带**脱敏 before/after diff**，禁 `Map.of()`（现状违规：lifecycle/datasource/mdm 配置侧）。
- 复用 `AuditTransactions.completeOnCommit` 语义，但先收敛 8 份复制为 data-ops-common 单实现（§四）。

### 前端契约
- 公共组件 `VersionHistoryPanel`（列表 + 单版查看 + **diff** + 回滚 Popconfirm（含"将丢弃当前草稿/追加 v{n+1}"影响文案）+ 发布前未保存提示）。diff 为全库空白，`JsonDiffView` 一并沉淀；mdm `ChangeHistoryTab.tsx:67-83` 的属性级 diff 实现上浮为参照。
- 请求一律走 `services/<模块>/api.ts` + HttpUtils 模式（workflow/digital-screen 两处例外收敛）。

## 四、共享支撑层（做多少、不做什么）

**自曝方案缺口**：不建议做 `AbstractVersionService` 基类。理由：11 套实现的锁手段（FOR UPDATE / 内存 synchronized / expectedVersion 手工比对 / 全无）、快照粒度、PO 归属（common 与模块内各半）差异过大，强行基类会漏抽象、绑架存量。

采用**极简三件套**：契约（§三，文档即规范）+ 少量共享工具 + 模块内实现模板。

| 件 | 内容 | 位置 |
|---|---|---|
| S1 共享枚举 | 复活 `DefinitionState`→`PublishState`（或新增同名），全模块引用 | data-ops-common `enums` |
| S2 版本工具 | `DigestCalculator`（规范化 JSON→SHA-256，收编 dev-task `TaskDefinitionDigestCalculator` 与 quality/metric 各自手写版）；`nextVersionNo` 查询式模板（SQL MAX+1 经 mapper 统一写法） | data-ops-common 新增 `version` 包，≤2 个类 |
| S3 审计收敛 | 合并 8 份 `AuditTransactions` 为 common 单实现，模块 import 替换；审计请求体加 before/after 槽位（`AuditCarrier` 现无 diff 字段） | data-ops-common + audit 模块 |
| S4 前端组件 | `VersionHistoryPanel` + `JsonDiffView`，放 `src/components/`（现 `components/ui` 仅 5 个 Yak* 件） | data-ops-ui |
| S5 实现模板 | 以 digital-screen（后端）+ modeling V16 迁移（DDL）+ dashboard 抽屉（前端）为「新对象版本化脚手架」，写成一页 checklist 附于本文档，不产码生成器 | docs |

## 五、逐对象迁移方案

### Wave 1（P0，先修生产正确性）

| # | 对象 | 改造 | 关键触点 |
|---|---|---|---|
| W1-1 | **lifecycle TTL 策略** | 新建 `yak_lc_policy_version`（C2）+ 主表加指针；publish/offline/rollback 端点；审计补 diff；前端 PolicyEditDrawer 加"影响 N 个绑定模型、需重新下发"提示 | `TtlPolicyService.java:192-202`、`TtlPolicyController.java:45-111`；模板=modeling V16+DigitalScreenPublisher |
| W1-2 | **sync-offline** | 建 definition revision 表（照 dev-task 双表）；保存进草稿、publish 才覆盖线上定义；`version` 乐观锁语义与版本号分离 | `OfflineJobDefinitionService.java:257-310` |
| W1-3 | **modeling 纠偏** | ①消费方（血缘/指标/派生/DDL 下发）改读 published 快照；②回滚改两步（恢复为草稿→人工确认再发布）；③发布按钮先强制 saveStructure；④nextVersionNo 改 MAX+1；⑤快照扩至全量元数据 | `ModelStructureService.java:126`、`ModelVersionService.java:103-128`、`detail.tsx:1496-1510`、`ModelVersionRepositoryAdapter.java:85-95` |
| W1-4 | **workflow** | 字符串状态→PublishState；补 `POST /versions/{no}/rollback`（追加式）；版本抽屉接查看+回滚；`DefinitionState` 复活 | `WorkflowDefinitionManager.java:204,343,368`、`WorkflowDefinitionController.java:117-146`、`WorkflowToolbar.tsx:213-280` |
| W1-5 | **mdm 配置侧** | 实体/属性/清洗规则 PUT 改草稿+发布（C4）；至少先补审计 diff | `MdmEntityService.java:101-126` |

### Wave 2（P1，版本语义纠偏）

| # | 对象 | 改造 |
|---|---|---|
| W2-1 | metric | 快照触发从"创建/更新自动流水"改为**发布触发**；toJsonSnapshot 补 compositions；禁物理删版本行（级联改标记）；版本 Tab 渲染 snapshot + 接 getVersion + 回滚按钮 |
| W2-2 | semantic 标准 | 快照语义改正（存**当前/发布**状态，非改前）；补 publish/rollback 端点与 StandardVersionsDrawer 操作列；业务过程/域/分层/标准字段按 A 域判定是否纳入（建议：标准字段纳入，其余三个仅补 status） |
| W2-3 | dataset | 补 `rollback`（回切=以旧版内容追加新版并移 current 指针）；上下线绑定版本；任务源指针改为**内容拷贝**或保留指针但在契约中显式声明跨对象引用（§七 Q3） |
| W2-4 | realtime sync | 补 `GET /versions` + 按版回切（前端从"apply 最新版"升级为版本列表选择） |
| W2-5 | quality | revision 表浮出为用户可见版本：查询端点 + VersionHistoryPanel 接入；监控规则本体改草稿/发布双态 |
| W2-6 | data-service 节点 | 随 dev-task 契约收敛（同构双表已存在），补前端回滚入口 |

### Wave 3（范式对象向契约收敛 + 组件复用）

| # | 事项 |
|---|---|
| W3-1 | S4 组件落地后，dashboard/digital-screen/modeling/dev-task 四处版本 UI 替换为 `VersionHistoryPanel`，diff 能力全量铺开 |
| W3-2 | 端点别名收敛：`restore`/`activate`/`apply-published-version` 保留兼容路由但标 @Deprecated，新名 `rollback` |
| W3-3 | PO 归属约定裁决（建议：新增对象的版本表 PO 一律模块内 `dao/model`，不再进 common） |
| W3-4 | C5 审计 diff 全模块扫尾（datasource 补 diff 审计并归档 C 域判定） |

## 六、实施顺序与依赖

```
S1/S2/S3 共享件（1 个 PR，纯新增+复制收敛，不碰业务） ──┐
                                                        ├─→ W1-1..W1-5 可并行（互不依赖，按模块分 PR）
S4 前端组件（可与 W1 并行，先用 modeling 面板改造验证）──┘
W1 全部合入后 → W2（每对象独立 PR，依赖 S1）→ W3（收尾批量）
```
- 每个 W1/W2 项的验收底线（对齐项目验证工作流）：后端 offline maven 编译+模块测试通过；前端 tsc 通过；**实测页面**：编辑草稿→线上读旧版→发布→版本列表出现新版→回滚→内容等价于历史版且有二次确认。
- 迁移一律新增 Flyway V 号，禁改已应用文件。

## 七、待拍板裁决点

| Q | 问题 | 建议 |
|---|---|---|
| Q1 | 回滚语义统一为追加式，但 dev-task `activate/{revisionNo}` 是指针回切（执行侧按 revision 固定，追加会打断 task-catalog 指针链）。是否豁免？ | **豁免存量**，新对象一律追加式；dev-task 在 W3-2 仅做命名兼容 |
| Q2 | dashboard 子表按 version_id 分行（非 JSON 快照），恢复保真度高于 blob。是否强行改 blob？ | **不强行**，C2 标注唯一豁免；新对象禁仿（避免第三种粒度） |
| Q3 | dataset 任务源版本存 dev-task revision **指针**，跨对象耦合版本链。改内容拷贝？ | 建议**保持指针+契约声明**：dataset 版本的有效性依赖来源 revision 不可删（dev-task 本就禁物理删，W2-1 同规则）；改拷贝需重建 schema_snapshot，成本高收益低 |
| Q4 | semantic 业务过程/域/分层是否入版本化 | 建议仅补 status 枚举不做版本表——它们是分类目录而非可执行配置 |
| Q5 | metric 现存"创建即有 v1"的流水数据如何迁移 | 迁移脚本把最后一条快照转为 v1 发布版，其余保留为只读历史（不删，保 append-only） |
