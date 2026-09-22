# F-001 — Asset Governance Hub Convergence

Status: DRAFT  
Feature ID: F-001  
Owner: Product  
Target Release: TBD  
Created: 2026-09-22  
Related Decisions: PD-001 — Asset Governance Hub (ACCEPTED)  
Related Issues / Designs: docs/reviews/productization-review-v1.md

> 本 Feature 把 PD-001 从已接受产品方向转成可交付、可验收的产品契约。  
> 它不要求一次 PR 完成全部实现；允许拆成多个 implementation slice，但所有 slice 必须服务同一用户闭环。

## 1. Objective — Required

**Feature：** Asset Governance Hub Convergence

**Primary User：** 需要发现、判断和使用数据对象的普通数据使用者 / 数据分析人员

**Secondary Users：**

- 数据工程师
- 数据管理员 / 数据治理人员
- 平台管理员
- 需要通过 Agent / Home / Search 进入数据对象的后续消费者

**Problem：**

当前平台已经有 Asset、Metadata、Quality、Security、Lineage、Lifecycle 等成熟或半成熟能力，但用户仍需要理解工程模块边界才能回答一个最基础的问题：

> “这是什么数据，我能不能信、能不能用、从哪来、谁负责、出了问题去哪处理？”

Asset 与 Metadata 目前都能承担“找数据 / 看详情”的一部分职责：

- Asset 更接近治理台账和跨域上下文；
- Metadata 更接近技术目录和物理结构；
- Quality / Security / Lineage / Lifecycle 又各自保留专业页面。

这导致用户必须先选择模块，再拼接事实。

**Expected Outcome：**

用户从 Asset 入口找到一个治理对象后，可以在同一个上下文中判断：

1. 这是什么对象；
2. 谁负责；
3. 来自哪里；
4. 当前治理 / 上架状态；
5. 是否有质量问题；
6. 是否敏感；
7. 上下游影响；
8. 生命周期 / TTL 状态；
9. 谁在使用；
10. 遇到问题应进入哪个专业域继续处理。

同时：

- Metadata / Quality / Security / Lineage / Lifecycle 等域继续拥有各自 Truth；
- Asset 只聚合、索引和导航，不复制第二份业务真相；
- 专业用户仍然可以进入 Metadata 技术工作台做采集、对账和深度诊断。

**Why Now：**

PD-001 已经 ACCEPTED，而当前代码已经出现 Asset 360、AssetProvider、Metadata Explorer 嵌入 Asset Catalog 等“半收敛”状态。

如果此时继续独立扩展 Asset 与 Metadata 两套详情、搜索和治理入口，重复产品面会快速固化，后续再收敛成本更高。

## 2. Success Metrics

> Acceptance 证明“功能按约定实现”；Success Metrics 证明“产品心智和用户结果真的改善”。

### MVP Success Signals

| Metric / Signal | Baseline | Target | Measurement / Evidence |
|---|---|---|---|
| 普通数据发现默认入口 | Asset 与 Metadata 均可承担发现职责 | 产品文案和主流程明确 Asset 为默认治理发现入口 | 导航/入口测试 + 用户路径验收 |
| 关键治理信息聚合完整度 | Quality / TTL / Fields 等存在 UNAVAILABLE 或割裂 | 对 MVP 资产类型，关键 Section 均能返回 OK / EMPTY / NOT_APPLICABLE / 有原因的 UNAVAILABLE | Asset 360 E2E evidence |
| Truth 回链率 | 部分块可回源，部分仅展示摘要 | 所有跨域摘要都能识别 Owner，并在可操作时提供专业域回链 | Section contract review + UI evidence |
| Provider coverage 可见性 | Provider 已存在但缺少统一产品可见性 | 主要治理对象的 Provider 注册/失败/缺失可被诊断 | Provider diagnostics evidence |
| “使用”语义真实性 | 主要是 Asset 页面浏览量 | 至少能区分“页面浏览”与“真实下游消费/引用” | Usage contract + E2E evidence |
| 双详情重复增长 | Asset Detail / Metadata Detail 边界靠开发人员理解 | 两者职责在产品契约和 UI 文案中明确，新增字段有归属规则 | Product review + regression checklist |

### Post-MVP Product Signal

上线后观察：

- 用户从 Asset 进入专业 Metadata / Quality / Security / Lineage 的跳转是否增加；
- 从 Metadata 返回 Asset 治理上下文是否顺畅；
- 新 Feature 是否减少“再做一个详情页 / 再做一套标签 / 再做一个 Owner”的重复需求。

暂不设未经真实基线验证的虚假百分比目标。

## 3. Product Context — Required

**Capability：** 数据资产与治理

**User Journeys：**

- J1 — 从外部数据到可信、可治理的数据对象
- J4 — 从发现问题到定位影响
- J5 — 从敏感数据到安全消费

**Entry Point：**

默认：

- Data Asset / Asset Catalog

上下文入口：

- Home / My Work（后续）
- Agent / Global Search（后续）
- Metadata technical workspace
- Quality / Security / Lineage specialist pages

**Previous Step：**

可能来自：

- Metadata Harvest / Reconcile
- Modeling / Metric / Dataset / Dashboard / Development Provider
- 手工登记

**Next Step：**

根据发现的问题进入专业域：

- Metadata technical detail
- Quality monitor / execution
- Security classification / policy
- Lineage graph / impact
- Lifecycle / TTL
- source-domain detail
- downstream consumer

**User Story：**

> As a 数据使用者, I want 从一个统一资产入口判断数据是否可信、敏感、可追溯和正在被谁使用, so that 我不需要理解平台模块边界就能决定是否使用它以及下一步去哪处理问题。

## 4. Assumptions & Validation

| Assumption | Why we believe it | How to validate | Result |
|---|---|---|---|
| 普通用户更需要“治理后的对象”而不是先进入技术元数据 | PD-001 + 当前 Asset Provider / 360 方向 | F-001 UX review + E2E path | Pending |
| Metadata 深度能力适合作为专业工作台保留 | Metadata 已有表/列/属性/采集/变更历史等深能力 | 不删除 Metadata；对典型排障场景做验收 | Pending |
| Asset 可以做聚合层而不成为 God Module | 当前 AssetDiscoverService 已采用分区容错和 Query API 思路 | Architecture review 每个 Section 的 Owner / API | Pending |
| 主要治理对象都可映射稳定 asset_key | 多个 Provider 已复用 Lineage identity | Provider coverage matrix | Pending |
| 用户真正关心的 Usage 不等于页面浏览量 | 当前 view trend 与业务消费不是同一概念 | 定义 Usage owner 和消费来源 | Pending |
| 不同资产类型的治理块适用性不同 | Table / Metric / Dataset / Dashboard 天然不同 | Section applicability matrix | Pending |

AI 不得把 Pending Assumption 自动升级为 Product Truth。

## 5. Truth & Ownership — Required

### Asset owns

- Asset ledger identity / projection
- governance listing state
- governance owner/contact
- Asset directory/taxonomy
- Asset-owned business tags
- reconcile/change handling state
- governance precheck
- derived Asset health summary
- Asset page activity / view telemetry
- cross-domain summary presentation

### Source domains own

| Fact | Truth Owner |
|---|---|
| physical table / column / schema metadata | Metadata |
| model definition | Modeling |
| metric formula / semantic definition | Metric / Semantic |
| dataset contract | Dataset |
| dashboard / analysis content | Dashboard / Analysis |
| quality rules / runs / results | Quality |
| classification / access / masking truth | Security |
| lineage graph / impact relations | Lineage |
| retention / storage lifecycle policy | Lifecycle |
| workflow / task definition | owning execution domain |

**Producer(s)：**

- Metadata
- Modeling
- Metric
- Dataset
- Dashboard
- Analysis
- Development / Task
- Quality
- Security
- Lineage
- Lifecycle

**Consumer(s)：**

- Asset UI
- Home / workspace
- future Global Search
- Agent
- governance workflows
- impact / trust views

**Source of Truth：**

每个 Section 必须声明自己的 Source Domain。Asset 不因展示需要复制第二份业务事实。

是否新增第二份业务真相：**否**

## 6. Options & Scope

### Chosen approach

采用 PD-001 已接受的模式：

> Asset = Governance / Discovery aggregation shell  
> Source domains = Truth owners  
> Metadata = specialist technical metadata workspace

### Alternatives considered

已在 PD-001 评估：

- Metadata primary hub
- long-term dual center

本 Feature 不重新打开已接受的战略选择。

### MVP In Scope

#### A. Asset 360 Information Architecture

定义稳定 Section：

1. Overview
2. Technical Metadata
3. Quality
4. Security
5. Lineage
6. Usage
7. Lifecycle
8. Governance

允许 UI 合并视觉 Tab，但产品契约必须保留这些职责。

#### B. Section Contract

每个跨域 Section 必须有：

- Owner
- applicability rule
- status
- summary
- reason / message
- source updated time（适用时）
- deep link / next action（适用时）

状态至少区分：

- OK
- EMPTY
- UNAVAILABLE
- NOT_APPLICABLE

不得把“依赖不可用”渲染成“没有问题”。

#### C. Critical Object Classes

MVP 先保证：

- Physical Table
- Model
- Metric
- Dataset

Dashboard / Chart / Development Task 保持现有 Provider，但不要求第一阶段拥有和 Table 相同的所有治理 Section。

#### D. Technical Metadata Corridor

Asset 能够：

- 展示技术元数据摘要；
- 回链 Metadata technical detail；
- 对物理 Table 看得到字段/Schema 的有效入口；
- 不复制 Metadata 的技术实体存储。

#### E. Quality Corridor

Asset 展示：

- 是否适用；
- 是否有监控；
- 最近质量结论 / issue summary；
- 最近更新时间；
- 去 Quality 专业页处理的入口。

Asset 不拥有 rule / execution。

#### F. Security Corridor

Asset 展示：

- classification summary；
- access/masking relevant summary（若适用）；
- 未定级 / 不适用 / 查询失败要区分；
- 回链 Security。

#### G. Lineage Corridor

Asset 展示：

- 本体 identity；
- 1-hop summary；
- upstream / downstream summary；
- full graph / impact deep link。

Lineage graph 仍由 Lineage owner。

#### H. Lifecycle Corridor

Asset 展示：

- lifecycle applicability；
- TTL / retention / storage summary；
- policy status；
- 专业域回链。

#### I. Usage Contract

必须把两类概念分开：

1. **Asset page activity**
   - view count
   - recent viewers / page activity（若实现）

2. **Business consumption / dependency usage**
   - Dashboard usage
   - API usage
   - Agent / Dataset consumption
   - downstream references

F-001 MVP 至少需要定义 business usage 的 Owner、数据源和展示语义，即使第一阶段只接入部分消费者。

#### J. Provider Coverage Diagnostics

提供可诊断事实：

- 哪些 AssetSourceType 有 Provider；
- 最近对账状态；
- source object 是否还存在；
- provider refresh 是否失败；
- 该类型支持哪些 Section。

不要求暴露为普通用户主页面，可以先作为管理/诊断能力。

### Out of Scope — Required

F-001 不做：

- Dataset 默认消费契约（PD-002）
- Asset PUBLISHED 是否阻断下游消费
- 合并 Data Governance 与 Data Asset 一级导航
- 删除 Metadata 模块
- 重写 Metadata Search
- MDM 产品定位
- Approval 产品定位
- Asset 健康度最终评分公式重设计
- Global Search 技术实现
- Agent 产品重构
- 一次性让所有 Asset Type 拥有完全一致的 360° 信息
- 把所有专业域配置页面搬进 Asset

## 7. Reuse — Required

必须优先复用：

- Project Space / RBAC
- AssetProvider
- Metadata query/detail
- Quality monitor / query
- Security classification / decision APIs
- Lineage query
- Lifecycle query
- Audit
- Approval（仅已有 Asset 上架流程）
- existing source-domain deep links

禁止为了 F-001 再建：

- Asset 自己的 Quality 表
- Asset 自己的 Security classification 表
- Asset 自己的 Lineage 边表
- Asset 自己的 TTL policy
- 第二套 Metadata entity store
- 第二套 Metric / Dataset / Model snapshot truth

允许 Asset 存储的仅限它明确拥有的治理事实和必要的派生缓存。

## 8. User Experience — Required

### Happy Path

1. 用户进入 Asset Catalog；
2. 搜索“订单”；
3. 找到目标 Table / Model / Metric / Dataset；
4. 进入 Asset Detail；
5. Overview 明确对象身份、Owner、状态、来源；
6. 用户查看 Technical Metadata / Quality / Security / Lineage / Lifecycle / Usage；
7. 如果发现问题，可直接进入对应专业域；
8. 处理完成后能够回到原 Asset 上下文。

### Empty State

区分：

- 当前对象确实没有该类事实；
- 当前类型不适用；
- 尚未配置；
- Provider / owning service 不可用。

### Error / Blocking State

单个 Section 出错不能让整份 Asset Detail 不可用。

本体 Asset 不存在时才允许整页失败。

### Permission Denied

- Asset 基础可见性遵守 Project Space / Asset 权限；
- 专业 Section 若无对应权限，不伪装为 EMPTY；
- 应显示无权限或隐藏具体敏感内容，策略由 owning domain 决定。

### Loading / Long-running

Asset 详情不得被最慢的跨域 Section 整体阻塞。

允许：

- section-level loading
- lazy load
- timeout + UNAVAILABLE
- cached derived summary

### Cross-domain backlink

每个可操作 Section 至少提供：

- View details / Open specialist workspace
- 返回 Asset context 的稳定 assetKey / source reference

## 9. Governance Impact

### Asset State

F-001 不改变现有 PENDING / PUBLISHED / OFFLINE / IGNORED / SOURCE_GONE 语义。

### Lineage

只读取/展示，不新增第二套 graph truth。

### Quality

从“health 评分内部引用 + 详情 UNAVAILABLE”提升为正式 Asset 360 corridor。

### Security

继续由 Security owner；Asset 只显示摘要与行动入口。

### Approval

保持现有 Asset 上架审批，不扩展审批产品范围。

### Audit

治理动作继续产生 Audit；纯查看是否审计按现有策略。

### Lifecycle

从当前缺失/独立视图接入 Asset 360 summary。

### Usage / Impact

必须正式区分页面 activity 与真实业务 consumption。

## 10. Open Questions

| Question | Blocking? | Owner | Decision / Answer | Date |
|---|---|---|---|---|
| Physical Table / Model / Metric / Dataset 各自哪些 Section 是 NOT_APPLICABLE？ | Yes | Product + Domain Owners | Pending | |
| Quality summary 的最小稳定 Query Contract 是什么？ | Yes | Quality / Asset | Pending | |
| Lifecycle / TTL summary 的最小稳定 Query Contract 是什么？ | Yes | Lifecycle / Asset | Pending | |
| Business Usage 的 Truth Owner 是 Asset、Lineage 还是各消费域提供 usage events？ | Yes | Product / Architecture | Pending | |
| Technical Metadata 在 Asset Detail 中采用摘要 + deep link，还是部分 inline 展开？ | No | Product / UX | Pending | |
| Asset Catalog 是否保留当前“台账资产 / 元数据实体” Segmented 双视图？ | No | Product / UX | Pending | |
| Provider coverage diagnostics 面向管理员还是仅运维/日志？ | No | Product / Architecture | Pending | |
| 是否需要正式 PRODUCT_USERS.md 统一 Persona 名称？ | No | Product Governance | Pending | |

**DRAFT -> APPROVED 前必须解决所有 Blocking = Yes。**

## 11. Supporting Evidence

### Product Decisions

- `docs/product/decisions/PD-001-asset-governance-hub.md`

### Product Reviews

- `docs/reviews/productization-review-v1.md`

### Asset evidence

- `yak-ops-business/yak-ops-business-asset/DOMAIN.md`
- `yak-ops-business/yak-ops-business-asset/REQUIREMENTS.md`
- `AssetProvider`
- `AssetDiscoverService`
- `AssetLifecycleService`
- `AssetOverviewService`
- Asset health scoring
- current Provider implementations

### Metadata evidence

- Metadata Architecture / Domain
- `MetadataSearchController`
- `MetadataEntityController`
- `AssetExplorer`
- `AssetDetailDrawer`

### UI evidence

- Asset Catalog
- Asset Detail
- current navigation

## 12. Architecture Impact

> 本节定义产品要求对架构的约束，不预先指定具体类名/表结构。

### API

需要稳定的 read-side summary contracts：

- Technical Metadata summary
- Quality summary
- Security summary
- Lineage summary
- Lifecycle summary
- Usage summary

优先由 owning domain 提供 Query API / Gateway。

### DB

默认不新增跨域事实表。

如为性能需要缓存 summary：

- 必须明确为 derived cache；
- 必须可重建；
- 不得成为新的 Truth Owner。

### Domain

Asset 保持治理聚合域，不吸收其它域规则。

### Events

Provider reconcile / source change / governance action 可以继续通过现有机制驱动派生状态。

F-001 不要求先引入新的全局 Event Bus。

### Compatibility

现有：

- Asset IDs
- asset_key
- provider sourceType/sourceId
- Asset lifecycle

尽量保持兼容。

## 13. Acceptance — Required

### E2E Scenario A — Physical Table

**Given**

一张已被 Metadata 采集并进入 Asset 台账的物理表。

**When**

用户从 Asset Catalog 搜索并打开它。

**Then**

用户能够在一个 Asset 上下文中确认：

- table identity / source
- Owner
- governance status
- technical metadata
- Quality status
- Security classification
- Lineage
- Lifecycle
- Usage semantics

每个事实都能识别 Owner；问题可以进入专业域继续处理。

### E2E Scenario B — Metric

**Given**

一个已通过 Metric Provider 注册为 Asset 的指标。

**When**

用户打开该 Metric Asset。

**Then**

不适用于 Metric 的物理表字段/TTL 等 Section 显示 NOT_APPLICABLE，而不是错误或假数据；Metric 自身来源和 Lineage / Security / Usage 等适用事实可以正常呈现。

### E2E Scenario C — Owning Domain Failure

**Given**

Quality 或 Lifecycle owning service 暂时不可用。

**When**

用户打开 Asset Detail。

**Then**

Asset 本体和其它 Section 仍可使用；失败 Section 明确显示 UNAVAILABLE + 原因，不把失败解释成“无问题”。

### E2E Scenario D — Deep-link and Return

**Given**

用户在 Asset 发现质量或安全问题。

**When**

点击专业处理入口。

**Then**

进入 owning domain 的正确对象上下文，并能够回到原 Asset identity。

### Evidence

实施阶段至少提供：

- UI interaction evidence
- API/Query contract tests
- cross-domain ownership tests
- Product Guard / architecture tests
- 关键 Asset Type E2E
- failure/degradation evidence

## 14. Delivery Slices

> 以下是建议的实现切片，不代表现在授权编码；F-001 APPROVED 后再创建对应实施 PR。

### Slice 1 — Contract & applicability

- Section contract
- Asset type applicability matrix
- provider coverage matrix
- current-vs-target UX contract

### Slice 2 — Metadata corridor

- Technical Metadata summary
- table / field entry
- deep links
- duplicate-detail boundary cleanup

### Slice 3 — Quality + Lifecycle corridors

- replace current explicit UNAVAILABLE gaps with stable read contracts
- honest EMPTY / NOT_APPLICABLE semantics

### Slice 4 — Security + Lineage hardening

- ensure consistent summary / permission / deep-link behavior

### Slice 5 — Usage semantics

- page activity vs real consumption
- first governed consumer integrations

### Slice 6 — UX convergence

- final Asset Detail information architecture
- catalog entry wording
- remove only proven duplicate entry points

## 15. Closeout

当状态更新为 SHIPPED 时：

- Success Metrics 当前结果：
- PD-001 Implementation 是否可以从 PARTIAL 更新：
- 哪些长期规则已提升到 Product Principles / Capability Map / User Journeys：
- 哪些规则已提升到 Asset / Metadata / Quality / Security / Lineage / Lifecycle Domain Contract：
- 哪些边界已提升到 Architecture Contract：
- 哪些旧 Evidence / plans 应归档：
- 当前 E2E 实现证据：
