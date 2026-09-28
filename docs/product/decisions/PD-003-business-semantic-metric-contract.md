# PD-003 — Business Semantic & Metric Product Contract

Status: ACCEPTED  
Implementation: IMPLEMENTING
Date: 2026-09-24  
Owner: Product  
Related Feature: F-005  
Related Issues: #120, #121

## Context

DataOps 已经具备较完整的 Semantic、Modeling、Metric、Lineage、Asset 与消费侧基础能力：

- Semantic 拥有数据标准、业务域、业务过程、标准字段、数仓分层等业务语义基础；
- Modeling 拥有逻辑模型、物理模型、稳定 `modelKey` 与不可变 Model Version；
- Metric 已经拥有 ATOMIC / DERIVED / COMPOSITE 指标、稳定 `metricCode`、版本快照、依赖登记、Lineage 登记、引用使用记录与基础影响分析；
- Phase 4 正在建立 Dataset / Data Service 的 governed consumption、Subscription、Usage Evidence 与 Consumer / Impact contract。

当前缺口不是“再做一个指标模块”，而是缺少一套跨域产品契约，把这些既有事实组织成一条用户可以完成的 J2：

```text
Business Domain / Process
 -> Standard / Caliber
 -> Model
 -> Metric
 -> Validation
 -> Publication
 -> Governed Consumption
 -> Usage / Impact
```

如果 Phase 5 直接从 UI 或 API 开始补功能，容易产生长期问题：

1. Semantic、Modeling、Metric 各自形成后台，用户仍需自己拼业务语义；
2. 把 Metric 当成 Dataset / Data Service，或为了“统一消费”再复制一份 Metric Product Truth；
3. 把 `ENABLED`、保存成功、版本快照、验证成功、正式发布混成同一个状态；
4. 下游引用当前可变 Metric，导致后续编辑静默改变已经使用的业务口径；
5. 把 MetricUsage、Lineage 与 Phase 4 observed Usage Evidence 混成一个“使用量”；
6. 为了让 Metric “可消费”而顺手自研新的 Semantic Query / OLAP Runtime。

本 Decision 用于冻结 Phase 5 的产品模型。F-005-B/C/D 在本 Decision ACCEPTED 前不得把以下规则写死在局部实现中。

## Current Behavior

### Semantic

Semantic 当前是标准与业务语义基础的 Truth Owner：

- `Standard` 统一承载 NAMING / TYPE / CODE / UNIT / CALIBER / SECURITY；
- `(project_id, kind, std_code)` 唯一，标准编码创建后不可修改；
- 业务域 / 业务过程 / 标准字段由 Semantic 拥有；
- Modeling 等消费方只保存松散 ID，通过 SPI 解析，不直接复制语义内容。

### Modeling

Modeling 当前拥有：

- Physical Model 与稳定 `modelKey`；
- Logical Model；
- Model Relation；
- 不可变 Model Version；
- source mapping 与模型结构；
- 模型发布历史和结构 diff。

Modeling 不应因为 Phase 5 被降级为 Metric 的内嵌字段集合。

### Metric

Metric 当前已经拥有：

- ATOMIC / DERIVED / COMPOSITE 三类指标；
- `(project_id, metric_code)` 唯一且 `metricCode` 创建后不可变；
- Caliber / Unit / Domain / Model 等松散引用；
- 每次创建、更新、状态变化产生 Metric Version 快照；
- `metric_dependency`、`metric_composition` 与 Lineage 登记；
- `MetricUsageApi.record` 由 Dataset / Dashboard / Data Service 等在保存引用时上报；
- dependency version compare 可表达 `UP_TO_DATE / OUTDATED / REMOVED`。

这里的 Metric Usage 是“已记录引用”，当前契约已经明确它**不等于实时 API 调用量**。

### Consumption

PD-002 / F-004 已经把 Dataset / Data Service 的运行消费事实与消费侧关系定义为独立 Truth：

- Data Product View 是 Dataset / Data Service 的 governed projection；
- Subscription 是声明依赖；
- normalized Usage Evidence 是实际消费证据；
- Lineage 不等于 Usage。

Phase 5 必须复用这一边界，而不是重新定义 Usage。

## Decision

### D1. Phase 5 不新增一级 Product Capability

Phase 5 仍属于现有“标准、指标与建模”产品能力，并与“数据消费与服务”建立受治理交接。

不新增新的顶级“Semantic Layer”或“Metric Product”产品域。

产品心智保持：

```text
标准、指标与建模
  ├─ Business Domain / Process
  ├─ Standard / Caliber / Unit
  ├─ Logical / Physical Model
  └─ Metric

Metric published contract
  -> downstream governed reference
  -> existing Dataset / Data Service consumption when applicable
```

### D2. Metric 是业务度量定义，不是 Dataset / Data Service，也不是新的 Data Product Truth

Metric 的正式产品含义为：

> **对业务度量含义、计算口径和依赖关系的受治理定义。**

它回答“业务看什么、怎么算、依赖什么”，不天然等于可直接执行的 Dataset / Data Service runtime endpoint。

Phase 5 默认：

- 不新增 `METRIC:<id>` ProductKey；
- 不把 Metric 伪装成 Dataset；
- 不把 Metric 伪装成 Data Service；
- 不创建第二份 Metric Product 表复制 Metric 定义；
- 如果未来要让 Metric 本身成为 Phase 4 Data Product source type，必须另立 Product Decision。

### D3. Truth Ownership 固定如下

```text
Business Domain / Process              = Semantic domain
Standard / Caliber / Unit              = Semantic domain
Logical / Physical Model               = Modeling domain
Model Version                          = Modeling domain
Metric definition                      = Metric domain
Metric Version                         = Metric domain
Metric Validation                      = Metric domain product truth
Metric Publication                     = Metric domain product truth
Metric dependency / composition        = Metric domain
Metric Reference Usage                 = Metric domain
Lineage relation truth                 = Lineage domain
Asset governance context               = Asset domain
Dataset / Data Service source truth    = respective owning domain
Subscription / observed Usage Evidence = Consumption domain
Consumer object truth                  = respective consumer domain
```

`Metric Validation` 与 `Metric Publication` 的存储形态由技术设计决定；本 Decision 只冻结产品语义与 Truth Owner，不要求一定新增特定表。

### D4. Metric identity 与版本 identity 分离

Metric 有稳定业务 identity 与不可变版本 identity。

#### Stable Metric Identity

跨域稳定引用至少必须包含：

```text
project context
metricId
metricCode   // stable business key / presentation & integration hint
```

规则：

- `metricId` 是平台内稳定对象 identity；
- `metricCode` 在 Project 内唯一且创建后不可修改；
- 展示名不能作为 identity；
- 跨 Project 不能只用 `metricCode` 猜测或重新解析对象；
- deep-link / backlink 使用稳定 ID，并由服务端校验 Project / RBAC。

#### Immutable Metric Version Identity

需要绑定精确定义时，使用：

```text
MetricVersionRef {
  metricId
  versionNo
}
```

历史版本是不可变快照。Published Contract、Validation Evidence 与稳定下游引用都必须能够指向精确版本，而不是只指当前可变主表。

### D5. `Metric != MetricVersion != Validation != Publication`

Phase 5 正式冻结四层语义：

```text
Metric
  = 稳定业务对象 + 当前可编辑定义

MetricVersion
  = 一次不可变定义快照

Validation
  = 对一个精确 MetricVersion 的验证证据

Publication
  = 明确宣布某个精确 MetricVersion 是当前可稳定引用的业务契约
```

因此：

- Save 不等于 Validate；
- `ENABLED` 不等于 Published；
- 有 Version 快照不等于 Published；
- Validation Passed 不自动 Publish；
- Publish 不允许引用“最新版本”这种会漂移的别名作为唯一事实。

### D6. Validation 必须绑定精确 MetricVersion，并区分验证类型

Phase 5 至少定义两类 Validation。

#### Definition Validation

Definition Validation 是 Phase 5 发布前的最低要求，至少检查：

- ATOMIC 所需 Model / measure expression；
- DERIVED 的上游 Metric 引用；
- COMPOSITE composition / token / cycle；
- Domain / Process / Caliber / Unit / Model 等引用是否可解析；
- Project / RBAC；
- dependency 是否 `REMOVED`；
- dependency version 是否 `OUTDATED`；
- 其它 owning Domain Contract 已经规定的阻断条件。

#### Data / Execution Validation

Data / Execution Validation 只有在存在真实、可复用、安全的执行能力时才成立。

Phase 5 **不为了完成这一项而创建新的 Semantic Query Engine**。

如果当前指标无法通过已有 Dataset / Data Service / query provider 做真实执行验证，必须显示 `NOT_APPLICABLE` 或 provider `UNAVAILABLE`，不能伪造“数据验证通过”。

#### Validation State

最小结果语义：

```text
PASSED
FAILED
NOT_APPLICABLE
```

Provider / evidence state 另行表达：

```text
READY
UNAVAILABLE
FORBIDDEN
```

`FAILED` 表示真实验证已经执行并发现业务/定义问题；`UNAVAILABLE` 表示验证无法可靠执行。两者不得互换。

Validation Evidence 至少可追溯：

```text
metricVersionRef
validationType
result
checkedAt
validator/provider
blocking findings
warnings
providerEvidenceRef? 
```

### D7. Publication 是 Metric domain 的显式、版本绑定事实

Publication 回答：

> “下游现在可以稳定依赖哪一个指标定义版本？”

最小语义：

```text
UNPUBLISHED
PUBLISHED
WITHDRAWN
```

规则：

1. Publish 是显式用户动作；
2. Publish 绑定精确 `MetricVersionRef`；
3. 发布前必须通过 Definition Validation 的 blocking gate；
4. Data / Execution Validation 是否强制，由实际 provider 能力与后续 policy 决定，Phase 5 默认不把不存在的执行验证设为硬前置；
5. 后续编辑 Metric 会产生新的 MetricVersion，但不会静默移动 active published version；
6. 发布新版本必须再次显式操作；
7. Published historical version 永不因上游变化而被重写；
8. 上游变化后通过 current dependency health 表达 `OUTDATED / REMOVED` 风险；
9. WITHDRAWN 不删除历史 publication / validation evidence。

Publication 的物理实现可以是 active version pointer、release record 或其它形式，但必须满足以上用户语义。

### D8. Published Metric Contract

用户和下游看到的 Published Metric Contract 至少包含：

| Field / Section | Truth source |
|---|---|
| metric identity / code | Metric |
| name / description / owner | Metric |
| metric type | Metric |
| business domain / process | Semantic reference |
| caliber | Semantic |
| unit | Semantic |
| model / upstream metric dependency | Modeling / Metric refs |
| measure / filter / dimensions / period | Metric Version |
| published version | Metric Publication |
| validation summary | Metric Validation |
| dependency health | Metric + owning provider evidence |
| lineage | Lineage |
| reference usage summary | Metric Usage |
| governed consumption handoff | downstream reference + Phase 4 contract when available |

ATOMIC / DERIVED / COMPOSITE 可以共享页面外壳，但其 definition payload 不应被伪装成完全相同。

### D9. Downstream reference 默认绑定 Published Metric，不能静默跟随 Draft

Phase 5 的稳定下游引用原则：

- 新的受治理引用默认引用 Published Metric Contract；
- 能保存 version 的消费者应记录 `MetricVersionRef`；
- 只支持 legacy `metricId` 的现有记录必须明确为“version unknown / follows legacy semantics”，不能伪造精确版本；
- 保存引用不自动授予数据访问权限；
- 保存引用不代表发生了真实运行消费。

现有 `MetricUsageApi.record` 可以继续作为 Reference Usage 的 owning mechanism，但 Phase 5 应逐步让新记录具备更稳定的 consumer identity / referenced version 语义；具体 migration 由 Feature 技术设计决定。

### D10. Metric Reference Usage 与 Observed Runtime Usage 是两个事实

#### Metric Reference Usage

由 Metric domain 拥有，回答：

> “谁保存/声明了对该 Metric 定义的引用？”

典型 Producer：Dataset、Dashboard、Data Service、其它 downstream definition。

它可以支持 dependency / impact，但不代表真实调用。

#### Observed Runtime Usage

由 PD-002 定义的 Consumption domain / source evidence producer 拥有，回答：

> “哪些 Query / Preview / Export / Invoke 等实际消费事件发生过？”

两者允许在一个 Impact context 中并列展示，但不得：

- 把 Reference Usage 当 API 调用次数；
- 从 Reference Usage 自动生成 Usage Evidence；
- 从 observed Usage 自动反写 Metric reference；
- 把两者合并成一张无来源说明的“Usage”表。

### D11. Lineage 与 Usage 继续分离

Lineage 回答技术 / 定义关系；Usage 回答声明引用或实际消费。

例如：

- Model -> Metric 可以是定义/技术依赖；
- Metric A -> Metric B 可以是 DERIVES_FROM / composition dependency；
- Dashboard 引用 Metric 是 Reference Usage；
- 用户 Query 某 Dataset 是 Observed Runtime Usage。

不能因为下游 Lineage edge 存在就自动声明某 Consumer 正在使用该 Metric。

### D12. Impact 是组合视图，不成为新的关系 Truth Owner

Metric Impact View 可以组合：

```text
Semantic dependency
Modeling dependency
Metric dependency / composition
Lineage
Metric Reference Usage
Phase 4 Observed Usage when a stable governed product mapping exists
```

每条关系必须保留：

```text
source/provider
stable identity
version when applicable
time / observedAt when applicable
coverage / limitation
```

Impact View 不拥有第二份依赖、Lineage 或 Usage Truth。

Provider 故障不能显示成“0 affected”。

### D13. Governed Consumption Handoff 复用 Phase 4，不建设新 Runtime

Metric 本身不提供新的 Phase 5 query runtime。

当一个 Published Metric 被 Dataset / Data Service 等受治理消费对象引用时：

```text
Published Metric
 -> downstream governed reference
 -> DATASET:<datasetId> / DATA_SERVICE:<serviceId>
 -> canonical Consumption Detail
 -> actual Query / Preview / Export / Invoke
 -> Phase 4 Usage Evidence
```

Metric Detail 可以展示“在哪里被实现 / 被引用 / 可进入哪个 governed consumption target”，但：

- source availability 仍由 Dataset / Data Service owning domain 提供；
- Access 仍由对应执行入口真实裁决；
- Usage Evidence 仍由 Consumption contract 产生；
- Metric 不复制 endpoint / runtime / access Truth。

### D14. Canonical UX Context 是 Metric Detail，不新增新的 Semantic Hub Truth

Phase 5 推荐围绕现有 Metric Detail 产品化成一个 Metric 的 canonical business context。

它至少回答：

```text
What is it?
Who owns it?
Which business domain/process?
What caliber/unit?
How is it calculated?
Which model/upstream metric does it depend on?
Which exact version is published?
Was that version validated?
Are dependencies still healthy?
Who references it?
Where can governed consumption continue?
What would a change affect?
```

Semantic / Modeling 继续拥有自己的专业 authoring workspace；Metric Detail 通过稳定 deep-link / backlink 串联它们，不复制它们。

### D15. 状态语义保持正交

Phase 5 不使用一个“大状态”吞掉所有语义。

至少分开：

- Metric authoring status（现有 ENABLED / DISABLED 等 owning status）；
- Metric Version identity；
- Validation result；
- Publication state；
- Dependency health (`UP_TO_DATE / OUTDATED / REMOVED`)；
- Provider evidence state (`READY / EMPTY / UNAVAILABLE / FORBIDDEN / NOT_APPLICABLE`)；
- Phase 4 consumption lifecycle / availability / access。

例如：

- Published + dependency OUTDATED 是合法但有风险的状态；
- Validation provider UNAVAILABLE 不等于 Validation FAILED；
- Reference Usage EMPTY 不代表 Observed Usage EMPTY；
- Dataset runtime UNAVAILABLE 不代表 Metric definition 不存在。

## Product Outcome

本 Decision 直接服务 J2：

```text
Business Domain / Process
 -> Standard / Caliber
 -> Model
 -> Metric
 -> Validation
 -> Publication
 -> downstream governed reference
 -> Consumption
 -> Usage / Impact
```

用户结果是：

- 业务指标从“定义台账”变成可验证、可发布、可稳定引用的业务契约；
- 用户可以解释一个 Metric 的业务含义、计算逻辑、依赖、精确发布版本与验证证据；
- 下游引用不会因 Draft 编辑静默漂移；
- 影响分析能够区分依赖、Lineage、Reference Usage 与 observed Usage；
- 实际消费继续使用 Phase 4 的 governed contract，而不是出现第二套消费平台。

## Alternatives Considered

### Option A — 新建独立 Semantic Query / Metric Runtime

方案：Phase 5 同时建设语义 DSL、查询规划、SQL 生成、OLAP 运行时、缓存等完整语义查询引擎。

不采用作为 Phase 5 默认方案。

原因：

- 当前核心缺口是产品契约与 Journey，而不是缺一个新的执行引擎；
- 会显著扩大 Phase 5 范围并与现有 Dataset / Data Service / Development runtime 重叠；
- 在没有稳定 Metric Publication / Usage semantics 前建设 runtime 会放大错误模型。

未来如确有统一语义查询需求，应单独 Product Decision。

### Option B — 把 Metric 直接加入 Phase 4 Data Product Type

方案：直接新增 `METRIC:<id>` ProductKey，并把 Metric 当成 Dataset / Data Service 同类 source。

不采用作为 Phase 5 默认方案。

原因：

- Metric 当前首先是业务定义，不天然拥有 runtime endpoint；
- 会把“业务度量定义”与“可执行消费源”混为一谈；
- 容易迫使 Metric 复制 availability / endpoint / access 等不属于它的 Truth。

### Option C — 保持现状，只优化 Metric CRUD 页面

不采用。

原因：

- 无法解决 J2 的 Validation / Publication / downstream reference / Impact 闭环；
- 用户仍需跨模块拼接业务含义；
- 版本快照仍无法回答“哪个定义可稳定依赖”。

### Option D — Published Metric Contract + Phase 4 governed consumption handoff

采用。

原因：

- 最大化复用当前 Semantic / Modeling / Metric / Consumption 能力；
- 补齐稳定业务合同而不重造 runtime；
- 让未来 Semantic Query Engine 或 Agent 有可信的 Published Metric Contract 可复用。

## Consequences

### Positive

- Metric 从后台定义升级为稳定业务契约；
- Draft 编辑与生产使用解耦；
- existing MetricVersion 获得明确产品价值；
- Reference Usage 与 runtime Usage 不再混淆；
- Phase 4 成为统一真实消费面；
- 为未来 Agent / Semantic Query / Metric API 提供清晰前置契约。

### Trade-offs

- 引入显式 Validation / Publication 会增加用户步骤；
- legacy 下游仅保存 `metricId` 时无法天然恢复精确版本；
- 在没有统一 query runtime 的情况下，部分 Metric 只能作为“受治理定义”而不能直接查询值；
- Impact 需要组合多个 provider，因此必须接受 partial / unavailable 状态。

### Risks

- 团队可能把 Publication 简化为一个 status 字段，又重新与 ENABLED 混淆；
- 可能为了展示“可消费”复制 Dataset / Data Service runtime 信息到 Metric；
- 可能把 MetricUsage 直接改名成 Usage Evidence，造成事实污染；
- 可能要求所有历史 usage 立即精确回填 version，导致错误数据；
- 可能在 F-005-C 中顺手实现大规模 semantic query runtime，造成范围失控。

## Truth / Ownership Impact

- Truth Owner: Semantic / Modeling / Metric / Lineage / Consumption 各自保持现有领域所有权；新增的 Validation / Publication 产品事实归 Metric domain。
- Producer(s): Semantic authoring、Modeling、Metric authoring/validation/publication、downstream definition consumers、Phase 4 runtime evidence producers。
- Consumer(s): Metric Detail、Asset/Governance context、Dataset / Dashboard / Data Service 等 reference consumer、Impact View、未来 Agent / semantic query capability。

不新增第二份 Standard、Model、Metric、Dataset、Data Service 或 Consumption Truth。

## Navigation / UX Impact

默认 UX：

```text
Business Domain / Process
 -> Metric Detail
    -> Semantic refs
    -> Model / upstream Metric refs
    -> Versions
    -> Validation
    -> Publication
    -> Reference Usage
    -> Governed Consumption target(s)
    -> Impact
```

所有跨域导航使用 stable identity，不用 display name 匹配。

Metric Detail 是单 Metric 的 canonical business context；它不是新的 Truth Owner。

## Migration Plan

本 Decision 被 ACCEPTED 后按以下顺序迁移：

1. F-005 Feature Spec 升为 APPROVED；
2. 复用现有 Metric stable identity / MetricVersion，不重建历史模型；
3. 新增/扩展 Validation 与 Publication contract；
4. 现有 Metric 默认保持现状，不自动宣称 Published；
5. 需要生产使用的 Metric 通过显式 validation + publish 进入 Published Contract；
6. 新的 governed downstream reference 优先记录 exact published version；
7. legacy 仅记录 metricId 的 usage 保留，并显式标记 version unknown / legacy semantics；
8. 不自动把历史 MetricUsage 转换为 Phase 4 Usage Evidence；
9. 只有存在稳定 Dataset / Data Service mapping 时，才提供 observed Usage / canonical Consumption backlink；
10. Product / Domain / Architecture review 确认没有新建第二份 owning Truth。

`Status: ACCEPTED` 只表示产品规则已冻结；只有 Golden E2E、迁移与长期契约完成后才能把 `Implementation` 更新为 DONE。

## Acceptance Evidence

Implementation 更新为 DONE 前必须有：

- 一个真实 Metric 的 Business Domain / Process / Standard / Model / Metric authoring evidence；
- exact MetricVersion；
- Definition Validation evidence；
- explicit Publication evidence；
- Draft 在 Publish 后继续编辑而 active published version 不漂移的证据；
- ATOMIC + DERIVED 或 COMPOSITE 的依赖 evidence；
- dependency OUTDATED / REMOVED 的负向证据；
- downstream governed Reference Usage；
- 至少一个 Dataset / Data Service governed consumption handoff（若 Golden Metric 有对应 target）；
- Reference Usage 与 observed Usage 分离展示；
- Impact source / time / coverage evidence；
- Project / RBAC / provider unavailable / stale publish 负向验证；
- Product / Domain / Architecture 审核确认无第二份 Truth。

## Supersedes

None
