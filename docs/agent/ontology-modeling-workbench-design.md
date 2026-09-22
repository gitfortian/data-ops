# 本体建模工作台 · 设计稿（Dataset 锚定版）

> 日期：2026-08-27 | 状态：设计稿 v1 待评审
> 定位：MVP 验收通过后的前端+后端建模功能升级（对应 next-steps 二期 E1/E2 的 UI 载体）
> 核心决策（产品主确认）：**本体映射对象 = 数据集（dataset）；物理库/表/连接全程对用户不可见**

---

## 一、设计原则（四条铁律）

| # | 原则 | 含义 |
| --- | --- | --- |
| P1 | **Dataset 是唯一公开事实源** | 建模者只见 dataset/字段；物理四元组由服务端按 dataset→datasource→catalog 解析派生并落库，UI 全程不出现 ds_id/db/tbl/col 输入框 |
| P2 | **权限交集最小化** | 可映射的 dataset 候选集 = 项目成员关系 ∩ dataset 访问权 ∩ ontology:manage —— 本体自动继承数据集可见性，不另建授权面 |
| P3 | **能力承诺反向派生（A2）** | allowed_operators 由 dataset_field.dataType 归一推导（首轮）＋Catalog 采集回填 physical_type 复核（后台巡检），建模者只看到派生结果徽标 |
| P4 | **INFERRED 永不直接生效（A6）** | 血缘/推荐引擎产候选（SqlColumnLineageParser 列级 DERIVES_FROM），必须人工确认才转 MANUAL |

---

## 二、数据结构改动（ontology 模块）

```sql
-- yak_onto_attribute 增加两列（Expand 迁移 V2__ontology_dataset_anchor.sql）
ALTER TABLE yak_onto_attribute
  ADD COLUMN dataset_id       BIGINT UNSIGNED NULL COMMENT '锚定数据集',
  ADD COLUMN dataset_field_id BIGINT UNSIGNED NULL COMMENT '锚定字段（唯一权威引用）';
-- 修正(2026-08-27 决策 PRE)：三原 ds_id/db_name/tbl_name/col_name 四元组【删除】而非降级——
-- dataset 逻辑视图无物理表可解析（DatasetVersionPO 实证）；语义执行改为下沉 DatasetQueryService。
-- 原 plan 的“内部投影保留”作废：
-- 由 DatasetMappingService 在保存时经 dataset 公共契约解析写入；REST 入参与响应均不再接受/返回，标注 @Hidden。
```

- 同一 `yak_onto_object` 内所有属性应锚定同一 dataset（同对象同源校验；跨 dataset 建模需拆对象——与 JOIN 限同源约束一致）。
- 新增**映射健康**三态（不建新表，惰性计算）：`NORMAL / STALE(字段改名或类型变更) / MISSING(字段删除)`。健康检查消费 dataset version 变更事件（DataDevelopment 的血缘 Outbox 已有先例）。

## 三、后端配套（ontology 模块改造清单）

| 组件 | 动作 |
| --- | --- |
| `DatasetMappingService`(新) | 入参 (objectId, datasetFieldId, 业务列) → 经 `dataset.DevelopmentDatasetFacade` 公共契约解析四元组 → 写 attribute；类型归一映射表 number/string/date/timestamp/boolean/json |
| `WorkbenchCandidateQuery`(新) | `GET /api/v1/ontology/workbench/candidates?objectId=`：待映射属性 × 推荐列（融合 Catalog remarks 相似度 + 列级血缘 confidence），返回 `{attrId, candidates:[{fieldId,fieldPath,score,source:LINEAGE/NAME_MATCH}]}` |
| `MappingHealthService`(新) | 惰性比对 dataset 当前版本 vs attribute 快照 → STALE 标记 + alert 通道通知 owner |
| REST 收敛 | attributes 批量保存接口入参收敛为 `datasetFieldId`（legacy 四元组字段标 @Deprecated 保留一版供种子/测试）；响应隐藏内部投影列 |
| Ossie 导出 | 不变（仍从内部四元组生成 source:db.tbl），因为交换格式面向机器 |
| 契约同步 | ontology 模块 REQUIREMENTS 增补「dataset 锚定映射」能力与 P2 权限规则；ARCHITECTURE 补 corridor：ontology → dataset 公共契约（新增）、ontology → datasource Catalog（E1 之后） |

## 四、工作台 UX（pages/ontology）

### 4.0 信息架构：三栏工作台

```text
┌─────────────┬───────────────────────────┬──────────────────────────┐
│ 左·业务域树   │ 中·对象卡列表               │ 右·详情面板(Tabs)          │
│ order        │ ┌───────────────────────┐ │ [属性][关系][指标][术语]     │
│ ├ 订单域      │ │ sales_order ●PUBLISHED│ │ ┌──────────────────────┐ │
│ ├ 客户域(预留) │ │ 健康:NORMAL 属性9 指标5 │ │ │ 映射表格 / 约束构建器  │ │
│ └ …          │ │ [重新绑定] [发布]       │ │ │ 口径预览 / AI上下文    │ │
│ [+新建域]     │ └───────────────────────┘ │ └──────────────────────┘ │
└─────────────┴───────────────────────────┴──────────────────────────┘
```

### 4.1 核心交互：属性映射表格（体验重心）

两面板式（参考 dbeaver 列映射但业务化）：

```text
┌─ 源：数据集字段(已选 sales_ds.v_sales_order) ─┐   ┌─ 目标：本体属性行 ──────────────┐
│ 🟦 order_no    VARCHAR  "业务单号"            │⇄│ logical_name=order_no           │
│ 🟨 order_date  DATE     "下单时间" ⭐置信0.92  │  │ data_type=date [枚举] role[DIM] │
│ 🟧 amount      DECIMAL  "实付金额"            │  │ is_time✅算子徽章 eq/between     │
│ ⬜ (未映射)                              缺口高亮│ requires[构建器] ai_context[抽屉]  │
└──────────────────────────────────────────────┘   └────────────────────────────────┘
```

- **即时反馈闭环**：建立映射瞬间调 `POST .../attributes/rederive` → 返回归一 data_type + allowed_operators 徽标更新（P3 可视化）；
- 未映射字段灰显缺口提示；STALE 行红边+"一键重绑到最新版本同名字段"；
- INFERRED 候选行带 ⭐ 置信度徽章，点击采纳需二次确认转 MANUAL（P4 交互化）。

### 4.2 富语义组件

| 组件 | 交互要点 |
| --- | --- |
| `ConstraintBuilder`（requires_json） | 结构化三段选择器 [本对象属性][op∈白名单][value]，禁自由表达式（MVP 决策延续）；多条件 AND 拼接预览为自然语言 |
| `AiContextDrawer` | instructions 文本区 + synonyms chips（支持整列粘贴批量导入）+ examples 列表（可从一期问答日志的高频问题一键导入示例——复用 query_log） |
| `VerbalizesEditor` | 句式模板编辑 + 占位符 {object}/{attr}/{time_grain} 下拉插入 + 实时渲染预览（语言化传统 NIAM/Ossie verbalizes） |
| `MetricWizard`（指标向导） | 步骤式：选 base MEASURE 属性 → aggregation 枚举 → business_filter 构建器（可视化条件，右侧始终**实时 SQL 预览**，复用 dry-run 接口）→ grain/unit/owner → verbalizes → requires(METRIC_APPLIES_TO) |
| `ChecklistDialog`（发布闸门） | 服务端强校验的前置镜像：PK 已绑定 / 属性全映射 / 关系两端就绪 / 指标 base 就绪 / owner 已填——任一不过，发布按钮禁用并跳转到问题项 |

### 4.3 术语 Tab
term/target_type/target_code 三联筛选；synonyms 批量导入；**歧义前哨**：同一 term 命中多个 target 高亮警示（与 Guard 歧义检测同规则的前端镜像，提前治理而非运行期报错）。

## 五、角色权限矩阵

| 角色 | 前置权限 | 能力 |
| --- | --- | --- |
| 建模者 | `ontology:manage` ＋ 目标 dataset 读权限（P2 交集） | 建/改草稿、绑定映射、提交发布申请 |
| 口径负责人(owner) | 建模者身份且被指派 | 提交发布 |
| 审批人 | `semantic:admin`（暂代，G1 审批流三期落地） | publish/deprecate |
| 业务消费者 | `semantic:query` | 只读 PUBLISHED（Agent 页面走此权限） |

## 六、测试要点

1. `DatasetMappingService` 单测：datasetField→四元组解析矩阵（含 dataType 归一六类）；跨 dataset 绑定拒绝；
2. Testcontainers：attributes 批量保存含 datasetFieldId → 断言四元组内部投影正确落库 且 REST 响应不含物理信息（隐私边界断言）；
3. Guard 回归：mapping 缺失对象的语义查询失败原因文案含 solution（Agent-first 契约）；
4. 架构守护：ontology 仅允许 import dataset 公共契约顶层类型（禁止 dao/model 泄漏——对齐 agent 模块 SDK 白名单手法）。

## 七、分期（MW 系列）与工作量

| ID | 内容 | 估时 | 依赖 |
| --- | --- | --- | --- |
| MW-1 | 后端：V2 迁移 + DatasetMappingService + attributes 接口收敛 + REST 隐藏内部列（含单测/隐私断言） | 2 PD | 无 |
| MW-2 | candidates 推荐 API（catalog remarks + 列级血缘 confidence 打分） | 1.5 PD | E1 Catalog 采集(二期)；MVP 期间可先 NAME_MATCH 简版 |
| MW-3 | 前端：三栏骨架 + 属性映射表格 + 即时派生徽标（pages/ontology + services/ontology） | 4 PD | MW-1 |
| MW-4 | 关系 Tab + 指标 MetricWizard + ConstraintBuilder/BusinessFilter 预览 | 3 PD | MW-3 |
| MW-5 | 术语 Tab + 歧义前哨 + AiContextDrawer + VerbalizesEditor | 2 PD | MW-3 |
| MW-6 | 发布 ChecklistDialog + MappingHealthService(STALE) | 2 PD | MW-4；dataset 变更事件（可延后惰性版先行） |

合计 ≈14.5 PD（比 MVP WBS M7 表单版 4 PD 大，差异即本次"体验好/语义丰富/交互好"的目标增量）。

## 八、明确不做（防过度设计，承接既有 Non-Goals）

1. 不做自由表达式约束解析器（requires 保持结构化数组，三期评估）；
2. 不做 reactflow 大画布版关系建模（当前表格+双下拉足够；画布仅作概览只读视图，四期视需求）;
3. 不做物理直连映射入口（与 P1 冲突，永不提供）；
4. AI_SUGGESTED 自动采纳不上线（A6 硬规矩）；
