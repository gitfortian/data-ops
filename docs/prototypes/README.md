# 两层建模 · 前端页面功能规划与 HTML 原型

> 对应设计文档：`docs/two-layer-modeling-design.md`
> 原型说明：纯静态 HTML（内联 CSS/JS，无外部依赖），双击即可在浏览器打开查看效果。视觉基调沿用现有 UI（antd 5 浅色、品牌色 #FE2C55、reactflow 三栏画布）。

---

## 1. 信息架构（菜单调整）

现有「语义层」菜单组改造为：

```
语义层
├── 本体建模          /semantic/ontology            （保留：模型卡片列表）
│   ├── 本体画布       /semantic/ontology/canvas/:id （改造：剥离指标）
│   ├── 记录详情       /semantic/ontology/records/:id（改造：指标分区迁出）
│   └── 业务动作       /semantic/ontology/actions    （新：从函数注册表拆入）
├── 语义建模          /semantic/calibers            （★新：口径字典）
│   ├── 指标列表+口径编辑器（同页双栏）
│   └── 函数管理       （从函数注册表迁入）
├── 绑定管理          /semantic/binding             （★新：本体↔数据映射）
├── Agent 契约预览     /semantic/agent-contracts     （★新：WorldView/CaliberView）
├── 语义控制台         /semantic/console             （保留）
└── 导出              /semantic/export              （★新：三产物）
```

删除：`函数注册表 /semantic/registry`（Functions 迁语义建模、Actions 迁本体建模）。

## 2. 页面功能清单

| # | 页面 | 功能点 | 复用/新增 |
|---|---|---|---|
| 1 | **本体建模·模型列表** | 域卡片网格、新建向导（基本信息→进画布）、搜索 | 复用现页面 |
| 2 | **本体画布** | 三栏画布（对象列表/节点关系图/检查器）；对象节点（identity/进度/规则角标）；关系连线（cardinality/verbalizes）；**业务规则编辑（requires 条件构造器）**；业务动作管理；术语表抽屉；上线自查（含元数据契约校验：Definition 必填、IRI）；**移除指标抽屉/指标注册入口** | OntoNode/InspectorPanel/RelationConfirmModal/RulesModal 改造；新增 ActionModal/TermDrawer |
| 3 | **记录详情（对象）** | 六分区改为：基本信息/字段含义(含值域)/数据绑定/关系/**业务规则**/变更单；发布预检加元数据契约项 | 改造 |
| 4 | **语义建模·口径字典** | 左：指标库（跨对象搜索、类型/状态筛选、一句话口径）；右：**口径编辑器**（聚合+度量+过滤条件+口径例外 / 派生链 / 适用前提 appliesTo / 审计引用 ruleRefs 只读）+ 版本历史 + 发布闸门；函数管理 Tab | MetricsDrawer/MetricFormModal/metricOneLiner 升级为独立页面；**规则不再合并进口径** |
| 5 | **绑定管理** | 锚定总览（对象→数据集、属性→字段映射表）、健康度（BROKEN/命中率）、重绑/重校验 | AnchorModal/AnchorInvalidation 升级为独立页面 |
| 6 | **Agent 契约预览** | WorldView 卡（对象/关系/规则/动作/术语）+ CaliberView 卡（指标/口径/函数）+ 工具清单 + 决策流 | 新增（读时视图） |
| 7 | **导出** | 三产物预览/下载：ontology.yaml（flights 形状）/ semantic_model.yaml（tpcds 形状）/ mappings.yaml | OssieExporter 对应改造 |

## 3. 原型文件

| 文件 | 内容 |
|---|---|
| `index.html` | 原型导航首页：分层总览 + 信息架构 + 页面链接 + 现有→新页面迁移映射 |
| `ontology-canvas.html` | 本体画布：三栏布局、对象节点/关系、业务规则 requires 构造器弹窗、动作/术语入口 |
| `semantic-workspace.html` | 语义建模：指标库 + 口径编辑器（聚合/过滤/派生链/适用前提/审计引用/版本） |
| `binding.html` | 绑定管理：锚定总览、字段映射、健康度 |
| `agent-contracts.html` | Agent 契约：WorldView / CaliberView 双栏 + 工具清单 + 决策流 |
| `export-preview.html` | 导出：ontology / semantic_model / mappings 三产物 YAML 预览 |

## 4. 交互约定（原型演示点）

- 顶栏左侧：层切换徽标（本体=蓝 / 语义=红 / 绑定=紫 / Agent=绿），强化"两层"心智；
- 画布对象节点的 ⋮ 菜单不再出现"登记指标"，改为"编辑规则/绑定数据/上线"；
- 口径编辑器中"依据规则"为只读审计引用（灰），口径条件区为可编辑（红主题）——可视化"规则≠口径"；
- 每页底部有「设计要点」折叠条，说明该页对应设计文档哪一节。
