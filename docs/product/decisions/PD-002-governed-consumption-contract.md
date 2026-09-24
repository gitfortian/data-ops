# PD-002 — Governed Consumption Contract

Status: ACCEPTED  
Implementation: NOT_STARTED  
Date: 2026-09-24  
Owner: Product  
Related Feature: F-004  
Related Issues: #100, #101

## Context

DataOps 已经具备 Development、Dataset、Data Service、Asset、Lineage、Quality、Security 等生产与治理能力，但“用户如何稳定消费一个已治理对象”尚缺少统一产品契约。

如果 Phase 4 直接从页面或 API 开始实现，最容易产生四类长期问题：

1. 为统一 UI 再复制一份 Dataset / Data Service / Asset Truth；
2. 把 Dataset 与 Data Service 强行抽象成同一种底层对象；
3. 把 Access、Subscription、Usage、Lineage 混成一个“使用关系”；
4. Asset、Dataset、Data Service、Consumption 各自形成入口并解释不同 Truth。

本 Decision 冻结 Phase 4 的产品模型。后续 F-004-B/C/D 只能实现或扩展本契约，不得在局部页面、DTO、Provider 中重新发明产品语义。

## Current Behavior

当前 Capability Map 已将 Dataset、Data Service、Analysis、Dashboard、Agent、downstream consumption 归入“数据消费与服务”，但默认消费契约仍未正式决定。

当前 J3 仅定义：

```text
Development / Workflow
 -> governed consumption contract
 -> Analysis / Dashboard / API / Agent / downstream
```

因此 Phase 4 的缺口不是“再造一个产品域”，而是把 governed consumption contract、consumer identity、subscription 与 usage evidence 的边界冻结下来。

## Decision

### D1. Data Product 是用户可见的 governed projection，不是新的 source-of-truth entity

DataOps 保留 `Data Product` 作为面向用户的产品概念，但其正式含义为：

> **对一个可消费 owning object 的受治理产品投影（governed product projection）。**

Phase 4 首批可投影对象只有：

- `Dataset`
- `Data Service`

`Data Product View` 不拥有 Dataset definition、Data Service revision/runtime、Asset governance、Quality result、Security policy 或 Lineage relation 的第二份 Truth。

Data Product 可以有稳定的 projection identity，但该 identity 必须由 owning object identity 派生：

```text
ProductKey = <productType>:<sourceIdentity>

examples:
DATASET:<datasetId>
DATA_SERVICE:<dataServiceId>
```

规则：

- `sourceIdentity` 必须来自 owning domain 的不可歧义 identity；
- 展示名、路径、标题不能作为 identity；
- `assetId` 是治理回链 identity，不替代 source identity；
- Consumption layer 可以缓存/read-model 化 projection，但缓存永远不是 owning Truth；
- 删除缓存后必须能够从 owning contracts 重建 Data Product View。

### D2. 一个 Data Product 不聚合彼此独立的 owning objects

允许一个 Data Product 暴露多个消费 endpoint，但所有 endpoint 必须属于**同一个 owning object / active release contract**。

例如：

- 一个 Dataset 可以同时提供 Preview / Query / Export；
- 一个 Data Service 可以在同一 active service contract 下提供多个 runtime endpoint；
- 不允许为了“产品包装”把多个无共同 owning identity 的 Dataset、Data Service 任意合并为一个新的 Data Product Truth；
- 若未来需要 Bundle / Domain Product / Semantic Product，必须另立 Product Decision。

### D3. Dataset Contract 与 Data Service Contract 共享外壳，不共享底层 Truth

所有 Data Product View 必须提供一组稳定的公共消费语义：

| Contract field | 语义 | Truth source |
|---|---|---|
| `productKey` | 稳定 projection identity | derived from owning identity |
| `productType` | `DATASET` / `DATA_SERVICE` | owning type |
| `sourceRef` | owning object identity + type | Dataset / Data Service |
| `producerRef` | 生产来源回链 | Development / owning producer |
| `assetRef` | 治理上下文回链；不存在时明确为空 | Asset |
| `owner` | 业务/责任 Owner | owning source / governance policy |
| `project` | Project Space | owning source |
| `visibility` | 可发现边界 | owning source / Security |
| `activeVersion` | 当前稳定消费版本或 active revision | owning source |
| `lifecycle` | 发布/废弃/退休语义 | owning source |
| `availability` | 当前是否可实际消费 | runtime/provider evidence |
| `qualitySummary` | 质量摘要与 evidence ref | Quality |
| `securitySummary` | sensitivity / classification / policy summary | Security |
| `lineageRef` | provenance / lineage 导航 | Lineage |
| `access` | 当前 Consumer 的 access decision | Security / Access provider |
| `endpoints` | 允许的消费方式 | owning source + runtime |
| `usageSummary` | known consumer / observed usage 摘要 | Consumption Evidence |

其中结构契约必须保持 type-specific：

#### Dataset Contract

至少表达：

```text
schema / columns
release or dataset version
freshness / SLA when applicable
query / preview / export capabilities
physical/logical location only when policy allows
```

Dataset 的核心语义是“稳定数据契约”。

#### Data Service Contract

至少表达：

```text
interface schema
protocol / method / route or equivalent invocation contract
active revision
runtime endpoints
availability / SLA when applicable
```

Data Service 的核心语义是“稳定服务接口 + active runtime”。

UI 可以共享 Product Header、Owner、Quality、Security、Lineage、Access 等 section，但不能把 Dataset schema 与 Service interface 伪装成同一字段模型。

### D4. Truth Ownership 固定如下

```text
Dataset definition / release truth   = Dataset owning domain
Data Service definition / revision   = Data Service owning domain
Data Service runtime truth           = Data Service / runtime owning domain
Asset identity / governance context  = Asset domain
Lineage relation truth               = Lineage domain
Quality result truth                 = Quality domain
Security classification / policy     = Security domain
Consumption product projection       = Consumption layer (derived read model)
Subscription truth                   = Consumption domain
Usage Evidence truth                 = Consumption domain
Consumer object truth                = respective consumer owning domain
```

Consumption domain **只拥有消费侧 relationship / evidence Truth**，不接管 source product Truth。

### D5. Consumer 使用统一 tagged reference，而不是统一复制 consumer object

`Consumer` 是“对 Data Product 产生声明依赖或实际使用的主体”。

统一引用形态：

```text
ConsumerRef {
  consumerType
  sourceDomain
  sourceIdentity
  displayHint?   // presentation only, never identity
}
```

允许的 consumer type 是开放集合。Phase 4 至少必须能表达：

- `USER`
- `TEAM`
- `DASHBOARD`
- `DATA_SERVICE`
- `JOB` / downstream job

`AGENT` 可以沿用同一 identity model，但 Agent 产品本身不是 Phase 4 交付范围。

规则：

- Consumer 的名称、Owner、生命周期仍由 respective consumer domain 拥有；
- Consumption 不复制一份 Dashboard/Job/User Truth；
- 同一主体在 Subscription 与 Usage Evidence 中使用同一个 `ConsumerRef` 表达。

### D6. Access、Subscription、Usage Evidence 是三个不同概念

#### Access

Access 表示“当前主体是否被允许执行某种消费动作”。它是授权决策，不是使用事实。

标准语义：

```text
ALLOWED
REQUEST_REQUIRED
FORBIDDEN
NOT_APPLICABLE
```

如果 Access Provider 本身不可用，不得伪造 `FORBIDDEN` 或 `ALLOWED`；section/provider state 必须表达 `UNAVAILABLE`。

#### Subscription

Subscription 表示 Consumer 对某个 Data Product 的**声明依赖 / 订阅关系**，不是权限本身，也不是实际调用次数。

Consumption domain 拥有 Subscription record，稳定 identity 为 `subscriptionId`，至少引用：

```text
subscriptionId
productKey
consumerRef
consumptionMode
status
createdAt / updatedAt
```

最小 lifecycle：

```text
ACTIVE
SUSPENDED
REVOKED
```

如果未来接入 Access Request / Approval，申请单属于 Access/Approval workflow；批准后可创建或激活 Subscription，但 Subscription 不取代授权策略。

#### Usage Evidence

Usage Evidence 表示“某个 Consumer 在某个时间通过某种方式实际消费过某个 Data Product”的可验证事实。

Consumption domain 负责规范化并持有 Usage Evidence contract；原始事实可以来自 Query、Data Service runtime、Export、Dashboard、downstream job 等 provider。

至少引用：

```text
usageEvidenceId
productKey
consumerRef
observedAt
consumptionMode
provider
providerEvidenceRef
outcome/status when available
```

规则：

- 有 Subscription 但没有 Usage 是合法状态；
- 有 Usage 但没有 Subscription 也可能合法，例如无须订阅的公开/直接授权消费；
- Access `ALLOWED` 不等于已经 Usage；
- Usage 数量为 0 不得推断“没有 Consumer”，除非 evidence provider 可用且查询结果明确为空；
- provider 不可用时必须返回 `UNAVAILABLE`，不能返回假空列表。

### D7. Lineage 与 Usage 不互相冒充

Lineage 表示技术数据来源、加工、依赖与影响关系；Usage Evidence 表示产品消费事实。

因此：

- 不因为出现 Usage 自动创建技术 lineage edge；
- 不因为存在 downstream lineage 就自动断言它是 known Consumer；
- Impact View 可以并列组合 Lineage + Subscription + Usage Evidence；
- 任一来源缺失时都必须保持来源语义，不做推断填充。

### D8. 不使用一个 overloaded lifecycle 枚举表达所有状态

Consumption UI 必须组合多个正交状态，避免把“无权限”和“后端挂了”混为一谈。

#### Publication / Lifecycle

来自 owning source：

```text
NOT_PUBLISHED
PUBLISHED
DEPRECATED
RETIRED
```

#### Availability

来自 runtime/provider evidence：

```text
AVAILABLE
UNAVAILABLE
UNKNOWN
```

`UNKNOWN` 只表示尚无足够运行 evidence；不能等价为 AVAILABLE。

#### Access

```text
ALLOWED
REQUEST_REQUIRED
FORBIDDEN
NOT_APPLICABLE
```

#### Discoverability

由 Project / visibility / Security 决定对象是否可被当前用户发现；“可发现但不可访问”是合法状态。

UI 可以派生友好 badge，例如 AVAILABLE / RESTRICTED / DEPRECATED / UNAVAILABLE / RETIRED，但派生 badge 不能反向成为新的 Truth。

特别区分：

- 产品存在但无权限：source 存在 + access=`FORBIDDEN`/`REQUEST_REQUIRED`；
- 产品存在但后端不可用：source 存在 + availability=`UNAVAILABLE`；
- 产品已废弃：lifecycle=`DEPRECATED`；
- 产品未发布：lifecycle=`NOT_PUBLISHED`，默认不进入正式可消费结果；
- 产品已退休：lifecycle=`RETIRED`；
- 没有 Consumer：Usage/Subscription section=`EMPTY`，不是 lifecycle；
- Consumer Evidence Provider 不可用：Usage section=`UNAVAILABLE`，不是 EMPTY。

### D9. 所有可选 section 使用统一 evidence-state 语义

Consumption projection 的 section/provider 必须从以下状态中选择，而不是用空数组或 404 混淆语义：

```text
READY        // 有可解释数据
EMPTY        // provider 正常，确认没有数据
UNAVAILABLE  // provider/依赖不可用，无法判断
FORBIDDEN    // 当前用户无权读取该 section
NOT_APPLICABLE // 对当前 product type 不适用
```

`READY` 的具体 payload 由 owning contract 决定。

### D10. 默认 UX：Consumption Hub 是消费主入口，Asset 是治理上下文入口，specialist page 保留专业深度

默认导航模型：

```text
Global Search / Consumption Hub
            |
            v
Canonical Consumption Detail
   |        |         |
   |        |         +--> Consumer / Impact
   |        +------------> Asset Governance
   +---------------------> Dataset or Data Service specialist context
                              |
                              +--> Producer / Development
```

同时支持：

```text
Asset Detail -> Consume -> Canonical Consumption Detail
Dataset Detail -> Consume / Consumption context
Data Service Detail -> Consume / Consumption context
```

规则：

- Consumption Hub 是“我要找并使用数据”的一级产品入口，归属现有“数据消费与服务”能力，不新增第七个一级产品域；
- Asset Detail 继续回答“它是什么、是否可信、来自哪里、谁负责”的治理问题，并提供 `Consume` 下一步；
- Dataset / Data Service Detail 继续承载各自专业 contract/runtime 细节；
- Search/Discovery 发现 Data Product 后必须进入同一个 canonical consumption context；
- 多入口只能是 navigation shortcut，不能产生多份产品 Truth。

### D11. Stable backlinks 使用 source identity，不依赖展示名

Canonical Consumption Detail 必须能够稳定回链：

```text
Consumption -> Asset (assetRef when present)
Consumption -> Source Dataset / Data Service (sourceRef)
Consumption -> Producer / Development (producerRef when present)
Consumption -> Consumer detail (consumerRef when resolvable)
Asset -> Consumption (sourceRef/productKey mapping)
```

任何回链不能依赖 name/title/path 文本匹配。

### D12. Phase 4 Golden E2E 的最小 Contract 字段冻结

对 Dataset 与 Data Service，两条 Golden Journey 都必须至少验证：

```text
productKey
productType
sourceRef
owner
project
visibility
activeVersion
lifecycle
availability
qualitySummary or explicit section state
securitySummary or explicit section state
lineage/provenance ref or explicit section state
access decision or explicit provider state
at least one type-correct consumption endpoint/capability
usage/subscription section state
stable backlink to source
assetRef/backlink when an Asset exists
```

并分别额外验证：

- Dataset：schema contract + 一条真实 Query/Preview/Export 消费路径；
- Data Service：interface contract + active runtime endpoint + 一条真实 invoke/integration 路径。

## Product Outcome

用户从“发现一个受治理对象”到“理解能否使用、如何使用、用了什么、谁在使用、变更影响谁”有一条稳定且可追溯的路径，同时不会被要求理解内部模块拼接方式。

本 Decision 直接服务：

- J3 — 从数据生产到受治理消费；
- J4 — 从发现问题到定位影响；
- J5 — 从敏感数据到安全消费。

## Alternatives Considered

### Option A — 新建独立 Data Product entity/domain 并复制 source contract

拒绝。

优点是 UI/API 统一容易，但会产生 Dataset/Data Service 第二份 Truth、同步一致性问题和新的生命周期冲突。

### Option B — 不存在 Data Product，只让用户分别进入 Dataset / Data Service

拒绝。

它能避免重复 Truth，但无法形成统一 discovery、access、consumer、usage 与 impact 产品路径，J3 仍然碎片化。

### Option C — Governed projection + source-specific contract（采用）

保留统一产品心智与统一入口，同时让 Dataset/Data Service/Asset/Quality/Security/Lineage 各自继续拥有事实。

## Consequences

### Positive

- Data Product 成为稳定用户概念，但不会演化成第二套 source Truth；
- Dataset 与 Data Service 能共享消费体验，又保持不同底层契约；
- Access / Subscription / Usage / Lineage 的含义被彻底拆开；
- EMPTY / UNAVAILABLE / FORBIDDEN / NOT_APPLICABLE 有统一语义；
- #102 / #103 可以并行实现而不会互相重定义模型；
- 未来 Dashboard / Agent / Semantic Product 可以接入同一 ConsumerRef / Usage Evidence 体系。

### Trade-offs

- Consumption Detail 必须做跨域 projection/composition，不能依赖单表 CRUD；
- provider state 与 partial failure 会增加 API/UI 状态表达复杂度；
- Data Product View 的缓存若存在，需要显式保证可重建性和 source-version freshness。

### Risks

- 实现团队可能把 projection 表逐步写成 owning table；
- 为追求“统一 DTO”可能再次抹平 Dataset 与 Data Service 差异；
- Usage provider 覆盖不足时，known consumer 只能是部分证据，UI 必须显示 evidence scope；
- Access Provider 不可用若被误映射为 FORBIDDEN，会造成错误安全结论。

## Truth / Ownership Impact

- Truth Owner:
  - source product truth：Dataset / Data Service owning domains
  - consumer relationship truth：Consumption domain
  - Usage Evidence normalized contract：Consumption domain
- Producer(s): Development / Workflow / Dataset / Data Service / runtime / consumer evidence providers
- Consumer(s): Consumption Hub, Asset Governance, Impact View, downstream governance, future Agent/Analysis products

本 Decision 不迁移现有 Dataset、Data Service 或 Asset Truth Owner。

## Navigation / UX Impact

新增/正式确认“Consumption Hub / Data Product Discovery”作为“数据消费与服务”域的默认用户入口；Asset、Dataset、Data Service 页面成为有明确职责的上下文入口，并统一回到 canonical Consumption Detail。

## Migration Plan

1. **#101 / F-004-A**：接受本 Decision，批准 F-004 Feature Contract；同步 Capability Map、Journeys、Glossary。
2. **#102 / F-004-B**：实现 Data Product projection、Discovery、Canonical Consumption Detail 与 backlinks。
3. **#103 / F-004-C**：实现 ConsumerRef、Access projection、Subscription、Usage Evidence 与 Consumer/Impact view。
4. **#104 / F-004-D**：以 Dataset + Data Service 两条登录态 Golden E2E 验证真实 source truth、partial states、usage evidence 和 backlinks。

`Status: ACCEPTED` 只代表产品规则生效；在 #102/#103/#104 完成前，`Implementation` 保持 `NOT_STARTED` 或按真实进度更新，不能把设计冻结误写成实现完成。

## Acceptance Evidence

把 Implementation 更新为 DONE 前至少需要：

- Dataset Golden Journey 通过并保存 evidence；
- Data Service Golden Journey 通过并保存 evidence；
- `EMPTY / UNAVAILABLE / FORBIDDEN / NOT_APPLICABLE` provider states 有测试；
- Subscription 与 observed Usage 可独立出现并有测试；
- Usage provider unavailable 不会显示假 `0 consumers`；
- Asset ↔ Consumption ↔ source/producer/consumer backlinks 使用稳定 identity；
- 代码/存储审查确认没有第二份 Dataset/Data Service/Asset owning Truth。

## Supersedes

None
