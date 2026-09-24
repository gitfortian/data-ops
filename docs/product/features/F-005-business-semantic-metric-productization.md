# F-005 — Business Semantic & Metric Productization

Status: DRAFT  
状态说明：Phase 5 产品方案草案；仅用于 #121 Product Review，PD-003 ACCEPTED 前不得指导主体实现  
Feature ID: F-005  
负责人：Product  
目标版本：Phase 5  
创建日期：2026-09-24  
关联产品决策：`PD-003-business-semantic-metric-contract.md`（PROPOSED）  
关联 Epic / Issues：#120、#121、#122、#123、#124

> 本 Feature 的目标不是重做 Semantic / Modeling / Metric，也不是建设新的语义查询引擎，而是把已有标准、业务域、模型、指标、版本、血缘和使用事实组织成一条可验证、可发布、可稳定引用、可追踪影响的业务语义产品闭环。

## 1. 目标与价值

**功能：** Business Semantic & Metric Productization

**主要用户：**

- 定义业务指标的数据产品经理、数据分析师、数据治理人员；
- 维护业务域、业务过程、标准/口径/单位的数据治理用户；
- 建立逻辑/物理模型的数据建模人员；
- 需要在 Dataset、Dashboard、Data Service 或 downstream 中复用统一指标定义的数据开发人员。

**次要用户：**

- 数据 Owner / Steward；
- 需要判断指标变更影响的维护人员；
- 需要理解指标引用、血缘与真实消费关系的治理/运维人员；
- 未来 Agent / semantic query capability 的产品与开发人员。

**用户问题：**

当前平台已经存在标准、业务域、模型和指标，但用户仍容易把它们理解成多个后台管理模块。用户需要自己回答：

> “这个指标到底代表什么？属于哪个业务过程？依赖什么口径和模型？当前哪个版本可以稳定引用？它真的验证过吗？谁在引用？谁实际消费了对应数据？如果我修改它会影响谁？”

如果只能通过 CRUD、版本列表、血缘图和分散 usage 页面拼答案，J2 仍未完成。

**期望结果：**

用户能够：

1. 从 Business Domain / Process 出发定位或创建业务 Metric；
2. 在 Metric canonical detail 中理解完整业务定义与跨域依赖；
3. 清楚区分 Metric 当前可编辑定义、immutable MetricVersion、Validation 与 Publication；
4. 对精确 MetricVersion 执行可解释 Validation；
5. 显式发布精确版本，使下游稳定引用不跟随 Draft 漂移；
6. 看到依赖 `UP_TO_DATE / OUTDATED / REMOVED` 与 provider availability；
7. 让 Dataset / Dashboard / Data Service 等下游声明对 Published Metric 的引用；
8. 区分 Metric Reference Usage 与 Phase 4 Observed Runtime Usage；
9. 在存在稳定映射时从 Metric 进入既有 governed Dataset / Data Service consumption context；
10. 通过 Impact View 理解上游定义依赖、下游引用、Lineage 与真实消费证据；
11. 从 Metric 稳定返回 Semantic / Model / downstream Consumer，反向也可回到同一 Metric identity；
12. 为未来 Metric API / Agent / Semantic Query Engine 提供稳定 Published Metric Contract，但本期不实现这些新运行时产品。

**为什么现在做：**

Phase 1/2 已经让数据生产结果进入治理上下文，Phase 4 正在建立受治理消费契约；如果业务指标仍停留在定义台账，平台虽然能“生产和消费数据”，却无法保证不同下游使用的是同一业务口径。Phase 5 应先把已有 Metric 能力产品化成稳定业务契约，再考虑更高级语义查询或 AI。

## 2. 成功指标

| 指标 / 信号 | 当前基线 | 目标状态 | 如何验证 |
|---|---|---|---|
| Golden Metric Journey | 标准/模型/指标能力存在但跨模块闭环未固化 | 一条登录态 J2 可重复完成 | #124 真实环境 E2E evidence |
| Published contract stability | 当前版本快照存在，但“稳定发布版本”用户语义未冻结 | Published Metric 绑定 exact MetricVersion，Draft 后续编辑不漂移 | 发布后修改 Draft，再核 active published version |
| Dependency explainability | 已有 dependency / impact 基础 | OUTDATED / REMOVED / UNAVAILABLE 可区分并有 next step | failure injection + API/UI evidence |
| Usage semantics | MetricUsage 与 runtime usage 容易在产品心智混淆 | Reference Usage / Observed Usage / Lineage 分开展示 | #124 正反例验收 |
| Cross-domain truth duplication | 多域各自持有 Truth | Phase 5 不新增第二份 Standard / Model / Metric / Consumption Truth | schema / architecture review |
| Governed consumption handoff | Metric 与 Phase 4 consumption 尚无统一产品路径 | 有稳定 target 时可进入 canonical Consumption context | stable deep-link + identity evidence |

## 3. 产品上下文

**所属产品能力：** 标准、指标与建模；与数据资产与治理、数据消费与服务形成跨域闭环。

**用户旅程：** J2 — 从业务定义到统一指标。

```text
Business Domain / Process
 -> Standard / Caliber
 -> Model
 -> Metric
 -> Validation
 -> Publication
 -> governed downstream reference
 -> Consumption
 -> Usage / Impact
```

**入口：**

- Business Domain / Process；
- Metric Center / Metric Detail；
- Model / Asset / downstream reference backlink。

**前置步骤：**

- Project Space 已建立；
- 用户拥有对应 Semantic / Modeling / Metric 权限；
- 所需标准、模型或上游指标存在，或用户有权限创建。

**下一步：**

- Published Metric 被 Dataset / Dashboard / Data Service / downstream 稳定引用；
- 在存在 governed Dataset / Data Service target 时进入 Phase 4 canonical Consumption；
- 查看 Reference Usage / observed Usage / Impact；
- 后续 Phase 可进一步建设 Reliability / Secure Consumption / Evidence-driven Agent。

**用户故事：**

> 作为业务指标 Owner，我希望发布一个绑定精确版本且通过定义验证的 GMV 指标，使 Dashboard、Dataset 或服务可以引用同一业务口径，并在我调整口径前看到真实影响范围。

## 4. 假设与验证

| 假设 | 为什么这样判断 | 如何验证 | 当前结果 |
|---|---|---|---|
| 现有 Semantic / Modeling / Metric Domain 足以承载 Phase 5 owning Truth | 三个模块已有稳定身份、版本、依赖和 SPI 边界 | Domain / schema review | 已有代码证据，待 #121 正式确认 |
| Phase 5 不需要新建 Semantic Query Runtime 就能交付核心价值 | J2 当前最大缺口是稳定契约、发布和影响闭环 | Golden Journey 不依赖新查询引擎完成 | 待 #124 验证 |
| Metric 不应默认成为 Phase 4 Data Product source type | Metric 是业务定义，当前不天然拥有 runtime endpoint | Product Review + consumption handoff prototype | 待 #121 决策 |
| exact MetricVersion 对稳定下游引用是必要的 | 当前 MetricVersion 已存在，Draft 可持续编辑 | 发布后编辑 Draft 的稳定性测试 | 待 #123 验证 |
| MetricUsage 更适合作为 Reference Usage 而非 runtime usage | 当前 record 由下游保存引用时触发 | 对照 PD-002 Usage Evidence | 已有合同证据，待产品命名确认 |

## 5. 事实归属与所有权

**Truth Owner：**

| 事实 | Owner |
|---|---|
| Business Domain / Process | Semantic |
| Standard / Caliber / Unit | Semantic |
| Logical / Physical Model / ModelVersion | Modeling |
| Metric definition / type / composition | Metric |
| MetricVersion | Metric |
| Metric Validation | Metric（PD-003 proposed） |
| Metric Publication | Metric（PD-003 proposed） |
| Metric Reference Usage | Metric |
| Lineage | Lineage |
| Asset governance context | Asset |
| Dataset / Data Service source/runtime | respective owning domain |
| Subscription / normalized observed Usage Evidence | Consumption |
| Dashboard / downstream object | respective consumer domain |

**事实生产方：** Semantic、Modeling、Metric、Lineage、downstream reference consumer、Phase 4 runtime evidence producer。

**消费方：** Metric Detail、Asset / Governance、Dataset / Dashboard / Data Service、Impact View、未来 Agent / semantic query capability。

**真相来源：** 各 owning Domain Contract + PD-003；Phase 5 projection / UI 只聚合，不复制 owning fields 成新的 Truth。

是否新增第二份业务真相：**否。** Validation / Publication 是 Metric domain 新增产品事实，而不是复制 Metric definition。

## 6. 方案与范围

### 已选择方案（待 PD-003 ACCEPTED）

采用：

```text
Existing Semantic Truth
        +
Existing Modeling Truth
        +
Existing Metric Truth / Versions
        ↓
Metric canonical business context
        ↓
Validation(exact MetricVersion)
        ↓
Publication(exact MetricVersion)
        ↓
Governed downstream reference
        ↓
Reference Usage
        ↓
Existing Phase 4 Consumption when applicable
        ↓
Observed Usage / Impact
```

不建设新的 Semantic Query Runtime，不创建第二份 Metric Product Truth。

### 已评估方案

1. **完整 Semantic Query Engine**：范围过大，且不是当前 J2 首要缺口；后续独立决策。
2. **Metric 直接成为 Phase 4 ProductType**：当前会混淆业务定义与 runtime source；默认不采用。
3. **只优化 CRUD / 详情页**：无法形成发布、稳定引用和影响闭环；不采用。

### 本次范围

#### F-005-A / #121 — Product Contract

冻结：

- Metric product meaning；
- Truth ownership；
- stable identity / version identity；
- Validation；
- Publication；
- downstream reference；
- Reference Usage vs observed Usage；
- consumption handoff；
- canonical UX / failure semantics。

#### F-005-B / #122 — Business Definition & Metric Authoring

交付：

- Business Domain / Process → Metric 的可理解路径；
- Metric canonical detail；
- Semantic / Modeling stable backlinks；
- ATOMIC / DERIVED / COMPOSITE type-specific authoring；
- current definition vs MetricVersion；
- dependency health 与失败语义；
- authoring → validation next step。

#### F-005-C / #123 — Validation / Publication / Consumption Handoff

交付：

- exact-version Definition Validation；
- 可选 Data / Execution Validation（只复用真实 provider）；
- explicit Publication；
- published contract stability；
- downstream governed reference；
- stable handoff to Phase 4 canonical Consumption when available。

#### F-005-D / #124 — Usage / Impact / Golden E2E

交付：

- Reference Usage product semantics；
- observed Usage integration where stable mapping exists；
- Impact composition；
- Golden Metric Journey；
- failure / permission / enterprise evidence。

### 明确不做

- 不重写 Semantic / Modeling / Metric 模块；
- 不新增一级 Product Capability；
- 不创建第二份 Standard / Model / Metric Truth；
- 不建设完整 Semantic Query / OLAP Engine；
- 不默认新增 `METRIC:<id>` ProductKey；
- 不重做 Dashboard / BI；
- 不做完整 ABAC / row / column policy；
- 不做完整 Incident / Reliability 闭环；
- 不做 Agent-first / free-SQL AI；
- 不把 Reference Usage / Lineage / observed Usage 合并成一个无来源的关系事实；
- 不要求错误回填所有 legacy usage 的精确 MetricVersion。

## 7. 复用要求

必须优先复用：

- **Project Space / RBAC**：所有跨域 ID 解析、deep-link、查询均在服务端验证；
- **Semantic**：Domain / Process / Standard / Caliber / Unit；
- **Modeling**：Logical / Physical Model / ModelVersion；
- **Metric**：Metric identity / version / dependency / composition / usage；
- **Asset**：治理上下文，不复制 Metric definition；
- **Lineage**：技术与定义关系；
- **Audit**：创建、修改、Validation、Publication 等关键行为；
- **Dataset / Data Service**：已有 source/runtime contract；
- **Consumption**：PD-002 / F-004 的 access / Subscription / Usage Evidence / Consumer semantics；
- **Alert / Notification**：Phase 5 只在已有能力适用时复用，不单独新建告警平台。

任何跨域重复存储必须在 Product / Architecture Review 中说明为什么不是第二份 Truth。

## 8. 用户体验

### 正常路径

```text
进入 Business Domain / Process
→ 查看相关标准 / 口径 / 单位
→ 进入或创建 Model
→ 创建 ATOMIC Metric
→ 创建 DERIVED / COMPOSITE Metric（如适用）
→ 查看 Metric Detail
→ Save 形成 current definition / MetricVersion
→ Validate exact MetricVersion
→ 查看 findings / dependency health
→ Publish exact version
→ 下游对象引用 Published Metric
→ 查看 Reference Usage
→ 进入 governed Dataset / Data Service target（如适用）
→ 查看 observed Usage / Impact
→ 返回 Metric / Semantic / Model
```

### Metric Detail 最小信息架构

#### Header

- metric identity / code；
- name / type；
- owner；
- authoring status；
- current version；
- published version；
- validation summary；
- dependency health；
- next primary action。

#### Definition

- Business Domain / Process；
- Caliber / Unit；
- Model / upstream Metric；
- measure expression / filter / dimensions / statistical period；
- type-specific composition。

#### Versions / Publication

- immutable MetricVersion list；
- exact validation evidence；
- active publication；
- published vs current diff / drift warning when applicable。

#### Dependencies / Lineage

- semantic/model/metric dependencies；
- dependency health；
- professional Lineage backlink。

#### Usage / Impact

- Reference Usage；
- governed consumption targets；
- observed Usage（可得时）；
- evidence source / time / coverage。

### 空状态

- 没有关联业务过程：明确 EMPTY，并提供有权限时的 next step；
- 没有 Reference Usage：仅 provider READY 且确认无记录时显示 EMPTY；
- 没有 governed consumption target：表示当前只有业务定义/引用，没有可进入的 Phase 4 runtime target；不伪造成 unavailable；
- 没有 observed Usage：区分“provider 正常且无 usage”与“无稳定 mapping / provider unavailable”。

### 异常 / 阻断状态

- dependency REMOVED；
- dependency OUTDATED；
- Semantic / Modeling provider UNAVAILABLE；
- validation FAILED；
- validation provider UNAVAILABLE；
- stale MetricVersion publish；
- concurrent edit；
- publication target version changed；
- consumption target deleted/unavailable；
- usage provider unavailable / evidence gap。

异常必须显示：发生了什么、影响什么、是否阻断、下一步做什么；不能统一成“加载失败”。

### 无权限状态

- Metric 可见但 Semantic reference 不可见：对应 section FORBIDDEN；
- 跨 Project ID：服务端拒绝，不泄漏对象存在性；
- 无 Publish 权限：仍可按权限查看 definition，但 Publish action 服务端真实阻断；
- Consumption target access 继续由 Phase 4 owning action gate 决定。

### 加载与长耗时

Metric Detail 各 section 应允许独立 provider state；Semantic / Modeling / Lineage / Usage provider 局部故障不能抹掉已知 Metric identity / published version。

Validation 若长耗时，应有稳定 validation identity / state，而不是依赖页面 session；具体同步/异步机制由技术设计决定。

### 跨域回链

所有 deep-link/backlink 使用：

- semantic stable ID；
- model ID / modelKey context；
- metricId / MetricVersionRef；
- Dataset / Data Service ProductKey（Phase 4）；
- downstream Consumer stable identity。

禁止 display-name matching。

## 9. 治理影响

### Asset / governed object state

Metric 继续作为业务指标 owning object；Asset 可以承载治理 projection/context，但不复制 Metric definition。

### Lineage

复用 existing Metric dependency / Lineage registration。Phase 5 不让 Impact View 接管 Lineage Truth。

### Quality

Phase 5 不自动把 Quality result 当 Metric Validation。未来若有 Metric-specific quality policy，需要单独产品契约。

### Security / Masking

Phase 5 使用现有 Project / RBAC；实际数据消费的安全裁决继续由 Dataset / Data Service / Security owning contract 执行。

### Approval

默认 Phase 5 不引入新的强制 Approval workflow。若企业需要 Published Metric 审批，后续基于现有 Approval Engine 另行 Feature / policy 决策。

### Audit

至少审计：

- Metric create / edit / status；
- validation trigger / result summary（按现有审计能力）；
- publish / republish / withdraw；
- 影响业务合同的关键配置变化。

### Lifecycle

Authoring status、Publication state、dependency health 分离，禁止一个 status 字段吞掉全部语义。

### Usage / Impact

- Reference Usage = Metric domain；
- observed Runtime Usage = Consumption；
- Lineage = Lineage；
- Impact = composition/read model，不拥有新的关系 Truth。

## 10. 未决问题

| 问题 | 是否阻断 | 负责人 | 决策 / 答案 | 日期 |
|---|---|---|---|---|
| Metric 是否新增 Phase 4 ProductType | 是 | Product | PD-003 当前建议“不新增”；待 #121 Product Review | 2026-09-24 |
| Publication 的最小 lifecycle 名称 | 是 | Product | PD-003 提议 UNPUBLISHED / PUBLISHED / WITHDRAWN | 2026-09-24 |
| MetricValidation 的持久化形态 | 否（产品语义已定义） | Architecture | Product Review 后技术设计决定 | 2026-09-24 |
| MetricPublication 的持久化形态 | 否（产品语义已定义） | Architecture | Product Review 后技术设计决定 | 2026-09-24 |
| legacy MetricUsage 如何表达 version unknown | 否 | Metric owner | #123/#124 migration design | 2026-09-24 |
| 哪个真实 Golden Metric 用于 #124 | 否 | Product / QA | 实施前选择 ATOMIC + DERIVED/COMPOSITE 场景 | 2026-09-24 |

Status 升为 APPROVED 前，以上阻断问题必须在 #121 中正式关闭。

## 11. 支撑证据

- Product Vision：`docs/product/PRODUCT_VISION.md`
- Capability Map：`docs/product/CAPABILITY_MAP.md`
- User Journey J2：`docs/product/USER_JOURNEYS.md`
- Product Decision：`docs/product/decisions/PD-003-business-semantic-metric-contract.md`
- Semantic Domain：`yak-ops-business/yak-ops-business-semantic/DOMAIN.md`
- Modeling Domain：`yak-ops-business/yak-ops-business-modeling/DOMAIN.md`
- Metric Domain：`yak-ops-business/yak-ops-business-metric/DOMAIN.md`
- Metric Requirements：`yak-ops-business/yak-ops-business-metric/REQUIREMENTS.md`
- Phase 4 contract：`docs/product/decisions/PD-002-governed-consumption-contract.md`
- Phase 4 Feature：`docs/product/features/F-004-governed-data-consumption.md`
- Epic / Issues：#120 / #121 / #122 / #123 / #124

## 12. 架构影响

本节只记录当前 proposed product shape 对未来实现的约束；在 PD-003 ACCEPTED 前不得把它解释为最终技术设计。

### API

需要稳定 contract 支持：

- Metric canonical detail；
- exact MetricVersion read；
- Definition Validation trigger/read；
- Publication read/mutate；
- dependency health；
- Reference Usage；
- Impact composition；
- governed consumption target lookup。

具体 endpoint / DTO 不在本 Feature 冻结。

### 数据库

- 必须复用既有 Metric / MetricVersion / dependency / usage；
- Validation / Publication 如需持久化，属于 Metric domain；
- 不新建复制 Semantic / Modeling / Dataset / Data Service fields 的聚合 Truth 表；
- migration 必须对存量 Metric 保持兼容，不自动把全部存量对象标记为 Published。

### Domain

不改变：

- Semantic owning standard/business definition；
- Modeling owning model/version；
- Metric owning metric definition/version；
- Lineage owning graph；
- Consumption owning runtime usage evidence。

新增产品语义：

```text
Metric Validation
Metric Publication
Published Metric Contract
Reference Usage vs Observed Usage naming boundary
```

### Events

若技术设计使用 event/outbox：

- 发布成功与 evidence 投递必须可诊断；
- 下游 provider 故障不得回滚已经成立的 owning transaction，除非 Product Contract 明确该动作必须原子；
- event 不是 owning Truth 的替代品。

### 兼容性

- legacy Metric 继续可读；
- legacy usage 不能伪造精确 version；
- existing SPI consumer 不得因 Phase 5 被强制同步升级而全量失效；
- 新 exact-version reference 需要渐进迁移策略；
- Phase 4 contract 不因 Phase 5 被破坏。

## 13. 验收标准

### E2E 场景

**Given / 前置条件：**

- 登录用户位于 Project A；
- 存在真实 Business Domain / Process、Caliber、Unit 与 Model；
- 用户拥有 Metric authoring / validation / publish 所需权限；
- 存在至少一个可作为 downstream consumer 的真实 Dataset / Dashboard / Data Service；
- Phase 4 的 Dataset / Data Service canonical Consumption contract 在需要的场景可用。

**When / 用户动作：**

1. 在 Business Domain / Process context 中创建 ATOMIC Metric；
2. 创建 DERIVED 或 COMPOSITE Metric；
3. 保存定义并得到 immutable MetricVersion；
4. 对 exact version 执行 Definition Validation；
5. 处理或确认 blocking findings；
6. 显式 Publish 该 exact version；
7. 编辑 Draft 生成新 version，但不发布；
8. 在真实 downstream 保存对 Published Metric 的引用；
9. 回 Metric 查看 Reference Usage；
10. 在存在 governed target 时进入 canonical Dataset / Data Service consumption 并执行真实动作；
11. 查看 observed Usage 与 Impact；
12. 修改上游 dependency version 或注入 provider failure；
13. 验证 OUTDATED / REMOVED / UNAVAILABLE / FORBIDDEN 等状态与回链。

**Then / 期望结果：**

- Published Contract 仍指向步骤 6 的 exact version；
- Draft 新 version 不静默改变下游稳定合同；
- Reference Usage 能证明 downstream 保存了引用，但不冒充 runtime usage；
- Phase 4 Usage Evidence 只在真实消费发生后出现；
- Lineage、Reference Usage、Observed Usage 在 Impact 中来源清晰；
- dependency change 有影响证据但不篡改 historical published version；
- provider unavailable 不显示假空/假 0；
- 所有 deep-link / backlink 使用 stable identity；
- Cross-project / forbidden 请求服务端真实拒绝。

### 验收证据

#### UI / API

- Metric canonical detail；
- exact MetricVersion；
- Validation result；
- Publication result / active version；
- downstream reference；
- Reference Usage；
- governed Consumption backlink；
- Impact response；
- dependency/provider failure response。

#### 持久化事实 / Event

- existing Metric / MetricVersion；
- validation evidence；
- publication exact version pointer / release record；
- Metric reference usage；
- Phase 4 Usage Evidence（适用时）；
- provider/outbox evidence（若技术设计使用）。

#### Audit

- create / edit；
- validation；
- publish / republish / withdraw；
- 权限拒绝/关键治理动作按现有 Audit contract 留痕。

#### Observability

- Validation / Publication failure 可定位；
- provider UNAVAILABLE 可区分；
- Impact provider partial failure 不被假空掩盖；
- evidence gap / legacy version unknown 可诊断。

## 14. 收尾与沉淀

当状态更新为 `SHIPPED` 时：

- 成功指标当前结果：记录 #124 Golden E2E 和真实使用信号；
- 长期 Product Truth：将 ACCEPTED PD-003 的稳定规则同步到 Product Glossary / Capability / User Journey（如 Product Review 判定需要）；
- Domain Contract：Metric Validation / Publication / reference version contract 写入 Metric DOMAIN / REQUIREMENTS；
- Architecture Contract：跨域 SPI / dependency / persistence / event 边界写入对应 ARCHITECTURE / DEPENDENCIES；
- 当前实现证据：PR、CI、real environment E2E、failure injection、migration / compatibility review。
