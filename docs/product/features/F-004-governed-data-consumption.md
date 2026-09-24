# F-004 — Governed Data Consumption & Data Product Hub

Status: APPROVED  
状态说明：产品模型与消费契约已冻结，可在 Phase 2 收口后进入 Phase 4 主体实施  
Feature ID: F-004  
负责人：Product  
目标版本：Phase 4  
创建日期：2026-09-24  
关联产品决策：`PD-002-governed-consumption-contract.md`  
关联 Epic / Issues：#100、#101、#102、#103、#104

> 本 Feature 的目标不是创建第二套 Dataset / Data Service 产品模型，而是把已有生产与治理 Truth 组合成一条稳定、安全、可追溯的消费路径，并让 Subscription 与真实 Usage 成为可治理的消费侧事实。

## 1. 目标与价值

**功能：** Governed Data Consumption & Data Product Hub

**主要用户：**

- 需要发现、理解并使用 Dataset 的数据消费者；
- 需要发现并集成 Data Service 的应用/数据开发人员；
- 需要判断访问状态、质量、安全、可用性后再使用数据的业务与分析人员。

**次要用户：**

- Dataset / Data Service Producer；
- 数据 Owner / Steward / Governance 用户；
- 需要识别 known consumers 与变更影响范围的维护人员；
- Dashboard、downstream job、Data Service 等机器 Consumer 的 Owner。

**用户问题：**

用户已经能在平台中生产 Dataset / Data Service，也能在 Asset 中理解治理上下文，但消费时仍缺少一套统一答案：

> “这是什么？我能不能用？应该怎么用？哪个版本可用？质量和安全状态怎样？谁负责？谁正在使用？如果我改它会影响谁？”

如果这些答案分别存在于 Asset、Dataset、Data Service、Quality、Security、Lineage、Runtime 页面，用户仍然要自己拼接产品事实；如果重新复制一份 Data Product Truth，又会引入一致性风险。

**期望结果：**

用户能够：

1. 从 Search、Consumption Hub 或 Asset 找到可消费 Dataset / Data Service；
2. 在一个 canonical Consumption Detail 中理解公共治理上下文；
3. 看到 Dataset 或 Data Service 各自正确的消费 contract，而不是被统一成假同构对象；
4. 明确知道当前 lifecycle、availability 与 access 状态；
5. 在被允许时进入正确 Query / Preview / Export / Invoke 消费动作；
6. 在无权限时看到 Request Required / Forbidden，而不是误认为对象不存在；
7. 在 provider 故障时看到 Unavailable，而不是假空数据；
8. 区分声明依赖（Subscription）与实际使用（Usage Evidence）；
9. 看到 known Consumers 与 evidence scope；
10. 从 Consumption 稳定返回 Asset、Dataset/Data Service、Producer/Development；
11. 从 Asset 或 source detail 稳定回到同一 Consumption context；
12. 为后续 Dashboard / Agent / Semantic consumption 提供可复用 contract，而不提前实现这些产品。

## 2. 产品模型冻结

F-004 以 PD-002 为唯一产品模型来源。

### 2.1 Data Product

`Data Product` 是用户可见的 governed projection，不是独立 source-of-truth entity。

```text
Data Product View
   |
   +-- DATASET:<datasetId>      -> Dataset Contract
   |
   +-- DATA_SERVICE:<serviceId> -> Data Service Contract
```

稳定 identity：

```text
ProductKey = <productType>:<sourceIdentity>
```

不得：

- 使用展示名作为 identity；
- 复制 Dataset / Data Service / Asset owning fields 后把 projection 表当 Truth；
- 把多个无共同 owning identity 的 Dataset / Data Service 任意包装成一个新的 Data Product entity。

一个 Product 可以有多个 endpoint，但必须来自同一个 owning object / active release contract。

### 2.2 Truth Owner

| 事实 | Owner |
|---|---|
| Dataset definition / release | Dataset owning domain |
| Data Service definition / revision | Data Service owning domain |
| Data Service runtime / endpoint health | Data Service / runtime owning domain |
| Asset identity / governance context | Asset domain |
| Lineage relation | Lineage domain |
| Quality result | Quality domain |
| Security classification / policy | Security domain |
| Data Product View | Consumption projection；derived, rebuildable |
| Subscription | Consumption domain |
| normalized Usage Evidence | Consumption domain |
| Consumer object | respective consumer domain |

## 3. Consumption Contract

### 3.1 公共 Contract

Dataset 与 Data Service 的 canonical Consumption Detail 都必须能表达：

```text
productKey
productType
sourceRef
producerRef
assetRef
owner
project
visibility
activeVersion
lifecycle
availability
qualitySummary
securitySummary
lineage/provenance
access
consumption endpoints/capabilities
subscription/usage summary
stable backlinks
```

公共字段只表示共同产品语义，不要求底层同构。

### 3.2 Dataset Contract

Dataset 必须保留以下专业语义：

```text
schema / columns
release or dataset version
freshness / SLA when applicable
query / preview / export capability
```

默认消费动作至少有一条真实、受治理路径，Phase 4 Golden E2E 可选择 Query、Preview 或 Export 中项目真实支持的一条作为主路径。

### 3.3 Data Service Contract

Data Service 必须保留：

```text
interface schema
protocol / method / route or equivalent invocation contract
active revision
runtime endpoint(s)
availability / SLA when applicable
```

Golden E2E 必须至少验证一个 active runtime endpoint 的真实 invoke / integration 路径。

### 3.4 不适用字段

某字段只适用于一个 product type 时，必须返回/显示 `NOT_APPLICABLE`，不能伪造空值来追求统一。

例如 Dataset column schema 与 Data Service invocation interface 可以占据同一 UI 区域，但 payload contract 不相同。

## 4. Consumer / Access / Subscription / Usage

### 4.1 Consumer identity

Consumer 统一表达为 tagged reference：

```text
ConsumerRef {
  consumerType
  sourceDomain
  sourceIdentity
  displayHint?
}
```

Phase 4 至少能表达：

```text
USER
TEAM
DASHBOARD
DATA_SERVICE
JOB
```

Consumer object 本身仍由 respective domain 拥有。

### 4.2 Access

Access 回答“当前主体是否被允许执行某种消费动作”。

```text
ALLOWED
REQUEST_REQUIRED
FORBIDDEN
NOT_APPLICABLE
```

Access Provider 不可用时，provider state=`UNAVAILABLE`，不得猜测 authorization result。

### 4.3 Subscription

Subscription 回答“某个 Consumer 是否声明依赖此 Product”。

最小状态：

```text
ACTIVE
SUSPENDED
REVOKED
```

Subscription 不是 Security Policy、Approval Request 或 Usage Counter。

### 4.4 Usage Evidence

Usage Evidence 回答“实际发生过什么消费”。至少包含：

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

- Access Allowed ≠ Usage；
- Subscription ≠ Usage；
- Lineage ≠ Usage；
- Subscription 存在而 Usage 为空是正常状态；
- Usage 存在而 Subscription 不存在也可能正常；
- provider 故障不能显示成 `0 usage` / `0 consumers`。

## 5. Lifecycle / Availability / Evidence State

F-004 禁止使用一个总状态枚举吞掉所有语义。

### Source lifecycle

```text
NOT_PUBLISHED
PUBLISHED
DEPRECATED
RETIRED
```

### Availability

```text
AVAILABLE
UNAVAILABLE
UNKNOWN
```

### Access

```text
ALLOWED
REQUEST_REQUIRED
FORBIDDEN
NOT_APPLICABLE
```

### Section / Provider evidence state

```text
READY
EMPTY
UNAVAILABLE
FORBIDDEN
NOT_APPLICABLE
```

必须满足：

| 场景 | 正确表达 |
|---|---|
| Product 存在但当前用户不能消费 | access=`FORBIDDEN` 或 `REQUEST_REQUIRED` |
| Product 存在但 runtime/backend 不可用 | availability=`UNAVAILABLE` |
| Product 尚未发布 | lifecycle=`NOT_PUBLISHED`；默认不作为正式可消费结果 |
| Product 已废弃 | lifecycle=`DEPRECATED` |
| Product 已退休 | lifecycle=`RETIRED` |
| provider 正常但无 Consumer | consumer section=`EMPTY` |
| Usage provider 失败 | usage section=`UNAVAILABLE` |
| 某 section 对当前 type 不成立 | `NOT_APPLICABLE` |
| 当前用户看不到某 section | `FORBIDDEN` |

UI 可派生 `AVAILABLE / RESTRICTED / DEPRECATED / UNAVAILABLE / RETIRED` 等友好 badge，但 badge 不是新的 Truth。

## 6. UX / Navigation Contract

### 6.1 默认入口

正式确认：**Consumption Hub / Data Product Discovery 是“数据消费与服务”产品域的默认消费入口。**

它不是新一级产品域。

入口关系：

```text
Global Search ---------------------------+
                                         |
Consumption Hub -------------------------+--> Canonical Consumption Detail
                                         |       |
Asset Detail -- Consume -----------------+       +--> Dataset specialist detail
                                                 +--> Data Service specialist detail
                                                 +--> Asset Governance
                                                 +--> Producer / Development
                                                 +--> Consumer / Impact
```

### 6.2 页面职责

**Consumption Hub** 回答：

- 有什么我可以消费；
- 哪些结果与当前 Project / visibility 匹配；
- Dataset / Data Service 是什么类型；
- 当前是否可用、是否受限、Owner 是谁；
- 下一步去哪里消费。

**Canonical Consumption Detail** 回答：

- 这是什么；
- 当前稳定版本/contract 是什么；
- 我能不能用、怎么用；
- Quality / Security / Lineage / Availability 怎样；
- known Consumers / Usage Evidence 怎样；
- source、Asset、Producer 在哪里。

**Asset Detail** 继续回答治理问题，并提供 `Consume` CTA，不复制 Consumption Detail。

**Dataset / Data Service specialist detail** 继续保留专业 schema/interface/runtime 操作，不变成第二个 canonical product page。

### 6.3 Stable backlinks

必须使用稳定 identity：

```text
Consumption -> sourceRef
Consumption -> assetRef when present
Consumption -> producerRef when present
Consumption -> consumerRef when resolvable
Asset -> productKey/sourceRef -> Consumption
```

不得使用 display name/path/title 猜测跳转目标。

## 7. Discovery Contract

Search / Consumption Hub 至少支持：

- product type；
- name / description（仅检索与展示，不作 identity）；
- owner；
- project / visibility；
- lifecycle；
- availability；
- access summary；
- quality/security summary（可按 provider 能力渐进增强）。

Discovery result 必须链接到 canonical Consumption Detail。

受限对象是否可被发现由 visibility/security policy 决定；允许“可发现但不可消费”。

## 8. Golden Journey

### 8.1 Dataset Golden Journey

```text
Development
 -> Publish Revision
 -> Dataset Delivery
 -> Asset Governance
 -> Consumption Discovery
 -> Dataset Contract
 -> Access State
 -> Query / Preview / Export
 -> Usage Evidence
 -> Consumer / Impact
 -> Asset / Development backlink
```

关键验收：

- productKey 来源于真实 datasetId；
- schema/version 来自 Dataset Truth；
- Asset/Quality/Security/Lineage 为 projection；
- Access 与 provider failure 可区分；
- 至少一条真实消费动作产生或关联可验证 Usage Evidence；
- Subscription 可独立于 Usage 存在；
- Consumer/Impact 能回到 evidence source；
- 回链不用 name matching。

### 8.2 Data Service Golden Journey

```text
Data Service Publish
 -> Active Endpoint
 -> Asset / Consumption Discovery
 -> Service Contract
 -> Access State
 -> Invoke / Integration
 -> Usage Evidence
 -> Consumer / Impact
 -> Asset / Development backlink
```

关键验收：

- productKey 来源于真实 dataServiceId；
- interface/active revision 来自 Data Service Truth；
- endpoint availability 来自 runtime evidence；
- invoke 与 Usage Evidence 可关联；
- Dataset-only 字段明确 `NOT_APPLICABLE`；
- 回链保持 source identity。

## 9. Phase 4 分工与实施边界

### #101 / F-004-A — Contract Gate

完成：

- PD-002 ACCEPTED；
- F-004 APPROVED；
- Capability Map / User Journey / Glossary 对齐；
- 后续票据不得重新发明产品模型。

### #102 / F-004-B — Data Product Discovery & Consumption Hub

实现：

- product projection composition；
- discovery/search entry；
- canonical Consumption Detail；
- Dataset/Data Service type-specific contract rendering；
- source/asset/producer backlinks；
- partial provider states。

不实现 Subscription/Usage owning workflow 的主体逻辑。

### #103 / F-004-C — Access, Subscription & Usage Evidence

实现：

- ConsumerRef；
- access projection；
- Subscription truth；
- Usage Evidence normalization/providers；
- Consumer / Impact view；
- evidence scope / unavailable semantics。

不得修改 Data Product source Truth 模型。

### #104 / F-004-D — Golden E2E & Product Acceptance

验证：

- Dataset path；
- Data Service path；
- role/access matrix；
- `EMPTY / UNAVAILABLE / FORBIDDEN / NOT_APPLICABLE`；
- backlinks；
- no duplicated truth；
- 登录态真实 evidence。

## 10. Phase 4 Golden Contract 必填项

两类 Product 的 Golden E2E 都必须至少存在或显式表达状态：

| Field / Section | Dataset | Data Service |
|---|---:|---:|
| productKey / productType / sourceRef | 必须 | 必须 |
| owner / project / visibility | 必须 | 必须 |
| activeVersion | 必须 | 必须 |
| lifecycle | 必须 | 必须 |
| availability | 必须 | 必须 |
| schema / interface | Dataset schema | Service interface |
| quality | data 或明确 evidence state | data 或明确 evidence state |
| security | data 或明确 evidence state | data 或明确 evidence state |
| lineage/provenance | data 或明确 evidence state | data 或明确 evidence state |
| access | decision 或 provider state | decision 或 provider state |
| endpoint/capability | 至少一条真实消费方式 | 至少一个 active endpoint |
| Subscription / Usage | data 或明确 evidence state | data 或明确 evidence state |
| source backlink | 必须 | 必须 |
| asset backlink | Asset 存在时必须 | Asset 存在时必须 |

## 11. 成功指标

Phase 4 不制造缺少观测基线的百分比 KPI，先以 Journey 与错误语义为主。

| 信号 | 目标 | 验证 |
|---|---|---|
| 消费入口一致性 | Search / Hub / Asset 最终进入同一 canonical consumption context | navigation E2E |
| Truth 一致性 | 不存在第二份 Dataset/Data Service/Asset owning truth | architecture/storage review |
| Dataset 消费闭环 | discover -> contract -> consume -> usage -> impact -> backlink | Golden E2E |
| Data Service 消费闭环 | discover -> contract -> invoke -> usage -> impact -> backlink | Golden E2E |
| 状态可解释性 | forbidden/unavailable/empty/not-applicable 不互相伪装 | failure-state matrix |
| Consumer 可追溯 | Subscription 与 observed Usage 独立可见，evidence scope 清楚 | product acceptance |
| 回链稳定性 | 全部使用 stable identity | direct URL / ref test |

## 12. Permissions / Project Space

- Discovery、Detail、Consume、Usage/Consumer sections 都必须保持 Project Space 边界；
- UI 隐藏按钮不能替代后端授权；
- `FORBIDDEN` 只能在允许暴露对象存在性的 policy 下显示；若 policy 要求不可发现，则不泄漏对象；
- Quality/Security/Usage 等 section 可以拥有独立读取权限，局部 `FORBIDDEN` 不应自动让整个 Product 消失；
- Audit 由实际消费动作/owning subsystem 的审计契约负责，Phase 4 不另造审计 Truth。

## 13. Non-goals

Phase 4 明确不做：

- 新建第二套 Dataset / Data Service / Asset Truth；
- 任意 Bundle 多个 owning objects 成新的 Data Product entity；
- 重做 Development Node / Draft / Revision / Execution；
- 重做 Workflow / Scheduling / Distributed Worker；
- 完整 BI / Dashboard 产品；
- Agent 产品或 free-SQL AI；
- Semantic / Metric 产品化；
- 完整 ABAC / row-column policy / dynamic masking 引擎；
- 把 Usage 当 Permission；
- 把 Lineage 当 Consumer Usage；
- 因 UI 统一而抹平 Dataset / Data Service contract 差异。

## 14. Product Acceptance Gate

F-004-A / #101 可以关闭的前提：

- [x] Data Product 定义：governed projection，不是独立 source Truth；
- [x] ProductKey identity 规则冻结；
- [x] Dataset 与 Data Service 公共语义及差异冻结；
- [x] Truth Owner matrix 冻结；
- [x] ConsumerRef 冻结；
- [x] Access / Subscription / Usage Evidence 边界冻结；
- [x] Subscription / Usage Evidence owner 冻结；
- [x] Lifecycle / Availability / Access / Evidence states 冻结；
- [x] Consumption Hub / Asset / specialist page 入口职责冻结；
- [x] Stable backlinks 冻结；
- [x] Golden E2E 最小 contract 字段冻结；
- [x] #102 / #103 / #104 实施边界冻结。

F-004 整体完成仍需 #102、#103、#104 实现与 Evidence；`APPROVED` 不代表代码已经完成。
