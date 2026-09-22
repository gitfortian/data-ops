# PD-001 — Asset Governance Hub

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-09-22  
Owner: Product

## Context

DataOps 当前已经同时存在两套面向“找数据 / 看数据”的用户入口：

1. **Asset Center**
   - 资产概览
   - 资产目录
   - 盘点上架
   - 目录与标签
   - Asset 360° 详情

2. **Metadata Center**
   - 元数据概览
   - 采集与对账
   - 跨类型搜索
   - 元数据实体详情

两者在技术职责上并不相同，但在用户心智上已经发生明显重叠：

> “我要找一张表 / 一个指标 / 一个 Dataset，并判断它是否值得使用，到底应该从哪里进入？”

如果长期保持两个平级发现入口，未来很容易形成重复搜索、重复详情、重复标签/负责人/状态解释以及不同治理口径。

本 Decision 只决定**用户产品入口与职责边界**，不决定删除 Metadata 模块，也不迁移任何业务 Truth。

## Current Behavior

### Asset 当前真实能力

Asset 模块已经具备跨域治理台账形态：

- 通过 `AssetProvider` SPI 接收多个源域的资产投影；
- 已存在 Metadata Table、Model、Metric、Dataset、Chart、Dashboard、Development Task 等 Provider；
- Asset Item 使用与 Lineage 同源的 `asset_key`；
- 拥有独立的治理状态机：PENDING / PUBLISHED / OFFLINE / IGNORED / SOURCE_GONE；
- 拥有 Owner、目录、标签、盘点变更、健康度、浏览趋势等治理事实；
- Asset 详情已经聚合：
  - 源域实时属性
  - Lineage
  - Security classification
  - Health
  - Usage / view trend
  - Governance actions
- 前端 Asset Catalog 已经把“台账资产”和“元数据实体”放在同一页面入口中切换。

同时，当前 Asset 360° 仍存在明确缺口：

- Quality 区块仍可能显示 UNAVAILABLE；
- Lifecycle / TTL 区块仍可能显示 UNAVAILABLE；
- Fields 依赖源域能力，覆盖不完整；
- Asset “使用”目前主要是资产页浏览趋势，不等于完整下游消费 Usage；
- Provider 覆盖虽广，但不同类型的治理丰富度并不一致。

因此，当前 Asset 是“统一治理入口原型”，还不能视为实现完成的 Hub。

### Metadata 当前真实能力

Metadata 模块负责更深的技术目录与物理元数据能力：

- Metadata Harvest / Registration / Reconcile；
- 物理表 / 列 / 数据库等技术实体；
- 跨类型统一搜索；
- 类型定义与元模型；
- 属性与字段详情；
- 变更历史；
- 元数据标签；
- Source projection；
- 技术 Lineage 跳转；
- 表存储量等专业技术信息。

Metadata Entity Detail 比 Asset Detail 更适合数据管理员和技术排障场景。

### 当前产品问题

Asset 与 Metadata 的工程职责基本可区分，但产品入口没有被正式定义：

- Asset 已经承担业务/治理视角；
- Metadata 仍然具备完整的发现与详情体验；
- 用户需要自己理解“台账资产”和“元数据实体”的差异；
- 后续 Quality / Security / Lifecycle / Usage 接入时，如果没有主入口约束，两套详情会继续同时膨胀。

## Decision

**将 Asset 定义为 DataOps 的统一数据发现与治理主入口（Asset Governance Hub）。**

同时明确：

**Metadata 保留为 Technical Metadata Capability，而不是被 Asset 吞并。**

目标用户心智：

~~~text
User
  |
  v
Asset Governance Hub
  |
  +-- Technical Metadata  -> Metadata
  +-- Quality             -> Quality
  +-- Security            -> Security
  +-- Lineage             -> Lineage
  +-- Lifecycle           -> Lifecycle
  +-- Usage               -> consuming domains
  +-- Source truth        -> source domain
~~~

Asset 的职责是：

> 提供“这个对象是什么、谁负责、是否可信、是否敏感、从哪里来、被谁使用、当前治理状态如何”的统一入口。

Asset **不拥有**：

- 表/列结构本体；
- 指标口径；
- 模型结构；
- Quality rule / execution；
- Security policy / classification truth；
- Lineage graph truth；
- Lifecycle policy / TTL truth；
- Dataset / Dashboard / Metric 等源对象本体。

Asset 聚合和索引这些事实，但 Truth 继续由源域拥有。

Metadata 的产品定位调整为：

> 面向数据管理员 / 数据工程人员的专业技术元数据工作台，负责采集、对账、技术搜索、物理结构和诊断，不再作为与 Asset 平级的通用“数据发现门户”。

## Product Outcome

主要改善：

- J1：从外部数据到可信、可治理的数据对象；
- J4：从发现问题到定位影响；
- J5：从敏感数据到安全消费。

期望用户结果：

1. 普通用户不需要判断“去 Asset 还是 Metadata 找数据”；
2. 从任意 Asset 可以继续查看技术元数据、质量、安全、血缘和生命周期；
3. 专业用户仍可进入 Metadata 做采集、对账、技术诊断；
4. 各治理域保持自己的 Truth，不因为统一入口复制业务事实；
5. 后续 Agent / Search / Home 等统一跳向同一个 Asset identity，而不是各自构造详情入口。

## Alternatives Considered

### Option A — Metadata Center as primary discovery hub

让 Metadata 成为所有数据发现入口，Asset 退化为治理状态/上架子系统。

**优点**

- Metadata 当前技术搜索和实体详情更深；
- 对数据工程师心智直接；
- 物理表/字段体验天然完整。

**问题**

- 业务资产（Metric / Dataset / Dashboard / Analysis 等）会被迫进入“技术元数据”心智；
- 上架、Owner、健康度、治理状态更像附加能力；
- 容易让产品长期以“metadata catalog”而不是“governed data asset”作为中心。

### Option B — Asset Governance Hub

Asset 作为统一数据发现与治理入口；Metadata 保留专业技术工作台。

**优点**

- 与当前 Asset Provider、Asset Detail、治理状态机方向一致；
- 可以容纳 Table / Model / Metric / Dataset / Dashboard 等不同资产类型；
- Governance 事实围绕同一 Asset identity 汇聚；
- Metadata 不需要失去技术深度；
- 最适合后续统一搜索、Agent、影响分析和“资产是否可信”体验。

**代价**

- Asset 详情需要继续补齐 Quality / TTL / Usage 等聚合；
- Metadata 的“通用发现门户”定位需要逐步降级；
- 必须持续保证 Asset 只聚合、不复制源域 Truth。

### Option C — Long-term dual center

Asset 与 Metadata 长期保持两个平级数据发现入口，通过跳转互联。

**优点**

- 改动最少；
- 短期开发最轻松；
- 两边可以独立快速迭代。

**问题**

- 用户心智长期分裂；
- 搜索/详情能力持续重复；
- 后续收藏、Usage、Owner、Tags、Security/Quality summary 更容易重复建设；
- AI / Home / Global Search 难以选择唯一数据入口。

## Consequences

### Positive

- DataOps 获得一个稳定的数据发现/治理中心；
- Asset / Metadata 的产品职责更清晰；
- Metadata 技术深度得以保留，不需要重写；
- Quality / Security / Lifecycle / Lineage 等治理能力有统一承载页面；
- 未来 Global Search / Agent / Home 可以围绕 Asset identity 建立统一跳转；
- 符合“一份 Truth 一个 Owner”的产品原则。

### Trade-offs

- Asset 页面会成为跨域聚合层，需要严格控制依赖和加载成本；
- Asset 需要定义不同资产类型哪些 Section 适用，不能强求所有资产都有同样治理块；
- Metadata Explorer 仍然存在，但其产品定位需要从“通用数据发现”收敛为“技术元数据工作台”；
- 某些深技术信息仍需要跳转 Metadata，而不是全部搬进 Asset。

### Risks

1. **God Object / God Page**
   - Asset 如果试图拥有所有字段和业务事实，会演化成第二套数据平台。
   - Mitigation：Asset 只拥有治理台账事实，业务详情实时读源域。

2. **聚合性能**
   - Asset Detail fan-out 过多可能导致页面慢。
   - Mitigation：Section 独立容错、按需加载、Summary API、明确查询预算。

3. **Provider coverage 不一致**
   - 不同 Asset Type 的质量/安全/字段/生命周期适用程度不同。
   - Mitigation：Section 必须支持 OK / EMPTY / UNAVAILABLE / NOT_APPLICABLE 等诚实状态。

4. **“上架”语义被误解为“可消费”**
   - 当前 PUBLISHED 是治理门面状态，不等于所有消费产品均可访问。
   - Mitigation：后续单独通过 Product Decision 讨论 Asset governance state 是否约束消费，不在 PD-001 顺带决定。

5. **Metadata 被错误弱化**
   - 如果为了统一入口删除专业技术能力，会损失数据工程/排障场景。
   - Mitigation：明确保留 Metadata 专业工作台。

## Truth / Ownership Impact

### Asset owns

- Asset ledger identity / projection；
- Asset directory；
- Asset business tags；
- Asset Owner（治理联系人）；
- Asset listing / offline / ignore lifecycle；
- reconciliation state；
- Asset health derived score；
- Asset view / governance activity；
- cross-domain summary presentation.

### Asset does not own

- Metadata physical facts；
- Semantic standards；
- Metric definition；
- Model definition；
- Quality rules / execution facts；
- Security policies / classification truth；
- Lineage graph；
- Lifecycle / TTL policy；
- Dataset / Dashboard / Analysis content.

### Producers

- Metadata
- Modeling
- Metric
- Dataset
- Dashboard
- Analysis
- Development / Task
- future eligible source domains

### Consumers

- Asset UI
- Home / workspace
- future Global Search
- Agent
- governance workflows
- impact / trust views

## Navigation / UX Impact

如果本 Decision 被 ACCEPTED，目标导航语义是：

~~~text
数据资产与治理
  ├─ 资产目录          <- 普通发现主入口
  ├─ 盘点 / 上架
  ├─ 目录 / 标签
  ├─ 元数据管理        <- 专业技术入口
  │   ├─ 采集与对账
  │   └─ 技术元数据浏览
  ├─ 血缘
  ├─ 质量
  ├─ 安全
  └─ 生命周期
~~~

这只是目标产品语义。

本 Decision **不直接授权修改导航**。导航调整必须通过后续 Feature Spec / Implementation PR。

### Asset Detail target mental model

~~~text
Overview
Technical Metadata
Quality
Security
Lineage
Usage
Lifecycle
Governance
~~~

不同资产类型按适用性展示，不制造假数据。

## Migration Plan

### Phase 0 — Decision only

本 PR 只形成 Product Decision，不改代码。

### Phase 1 — Product contract

如果 PD-001 ACCEPTED：

- 更新 Product Principles / Capability Map / User Journeys；
- 创建 Feature Spec：Asset Hub convergence；
- 明确 Asset 与 Metadata 的 UX 边界；
- 定义 Section applicability matrix。

### Phase 2 — Close current gaps

按 Feature Spec 补齐：

- Quality summary；
- Lifecycle / TTL summary；
- Technical Metadata deep link / embedding；
- Usage definition；
- provider coverage diagnostics。

### Phase 3 — Product surface convergence

在已有能力稳定后再评估：

- 是否移除独立 Metadata Overview；
- 是否将通用搜索完全收敛到 Asset；
- 是否调整一级/二级导航；
- Home / Agent / Global Search 是否统一跳 Asset。

不得先改菜单再补能力。

## Acceptance Evidence

当以下条件全部成立时，可以把 `Implementation` 更新为 `DONE`：

1. **Single discovery default**
   - 面向普通数据使用者，平台有明确唯一的“找数据”默认入口；
   - Metadata 明确定位为专业技术工作台。

2. **Asset identity coverage**
   - 计划纳入治理的主要对象都能稳定映射到统一 `asset_key`；
   - provider coverage 有可观测清单和失败状态。

3. **360° governance view**
   - Asset Detail 至少能诚实呈现：
     - source facts
     - metadata context
     - quality
     - security
     - lineage
     - lifecycle
     - usage
   - 不适用 / 暂不可用必须显式显示，不得伪造成空数据。

4. **Truth ownership preserved**
   - Asset 不复制 Metadata / Quality / Security / Lineage / Lifecycle 等域的业务 Truth；
   - 聚合结果可回链到源域。

5. **E2E journey**
   - 用户从一个数据对象进入 Asset；
   - 能判断它是什么、谁负责、是否可信、是否敏感、来自哪里、下游谁在使用；
   - 能继续跳转到专业域处理问题。

6. **Navigation proof**
   - 普通用户不需要理解 Asset 与 Metadata 的工程模块边界才能完成“发现 + 判断可信度”的任务。

## Non-goals

PD-001 不决定：

- Dataset 是否成为默认消费契约；
- Asset PUBLISHED 是否强制约束 API / Dashboard / Agent 消费；
- Metadata 模块是否删除；
- 具体菜单最终名称；
- Quality / Security / Lifecycle 的内部实现；
- Global Search 技术方案；
- MDM 是否纳入 Asset；
- Asset 健康度最终评分公式。

## Supporting Evidence

Current repository evidence reviewed for this Decision includes:

- Asset DOMAIN / ARCHITECTURE / REQUIREMENTS；
- `AssetProvider` and current provider implementations；
- `AssetDiscoverService`；
- `AssetLifecycleService`；
- Asset health scoring；
- Asset Catalog / Asset Detail UI；
- Metadata Architecture；
- Metadata Search / Entity Detail；
- Metadata Explorer / Entity Detail Drawer；
- current navigation；
- productization review V1.

## Supersedes

None
