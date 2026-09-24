# F-002 — Data Development Hub 产品化

Status: APPROVED  
状态说明：产品契约已冻结，可进入 Phase 2 实施  
Feature ID: F-002  
负责人：Product  
目标版本：Phase 2  
创建日期：2026-09-24  
关联产品决策：沿用现有 Product Capability Map / User Journeys，不新增一级产品能力  
关联 Issue：#75、#76、#77、#78、#79  
关联领域契约：`yak-ops-business/yak-ops-business-data-development/REQUIREMENTS.md`、`DOMAIN.md`、`DEPENDENCIES.md`、`EXECUTION_CONTROL_PLANE.md`

> 本 Feature 的目标不是重写 Data Development，而是把现有开发、运行、发布、Dataset、Data Service、Lineage 能力收敛成一条用户可理解、可恢复、可追溯、可治理的数据开发产品闭环。  
> Phase 2 验收以真实用户 Journey 为准，不以“已有页面 / 已有接口 / 某个 Provider 已完成”为完成标准。

## 1. 目标与价值（必填）

**功能：** Data Development Hub 产品化

**主要用户：**

- 数据工程师 / 数据开发人员
- 需要编写 SQL、Shell、Python、Java、HTTP Task 的开发人员
- 负责发布、上线、回滚数据任务的数据发布人员

**次要用户：**

- 数据治理人员
- 运维 / Release Operator
- 需要消费 Dataset / Data Service 的下游用户
- 从 Asset / Lineage 回溯开发来源的专业用户

**用户问题：**

当前 Data Development 已经具备较完整的工程能力，但产品体验仍然围绕内部对象和分散页面组织。用户需要理解 Node、Draft、Revision、Execution、Task Catalog、Dataset、Data Service、Lineage 等工程边界，才能回答最基本的问题：

> “我正在开发什么？当前内容保存了吗？我刚才运行的是什么？为什么失败？哪一版已经发布并上线？最终产物在哪里？这个产物从哪里开发出来？”

现有能力包括工作区、多编辑器、Draft/Revision、Editor Run、Execution History、Release、Dataset、Data Service、Lineage Outbox，但这些能力尚未被统一成一条清晰用户旅程。

**期望结果：**

用户不需要理解内部实现模型，也可以完成：

1. 找到或创建开发对象；
2. 编辑并安全保存当前 Draft；
3. 清楚区分当前编辑内容、Draft、Published Revision；
4. 直接运行当前编辑器内容；
5. 查看运行状态、日志、结果和错误；
6. 刷新或离开页面后仍能恢复运行上下文；
7. 将明确 Draft 发布为 immutable Revision；
8. 理解 Published / Online / Offline / Active Revision；
9. 交付 Dataset 或 Data Service，而不把它们伪装成普通 Task；
10. 看到发布产生的 Lineage / Evidence；
11. 在 Asset Governance Hub 找到受治理产物；
12. 从 Asset / Lineage 返回对应的 Development Node / Revision / Execution 上下文。

**为什么现在做：**

Data Development 的底层领域能力已经足够丰富，如果继续以“再补一个接口 / 页面 / 编辑器”的方式扩展，Node / Draft / Revision / Execution 等内部概念会进一步泄漏到用户体验，Release、Dataset、Data Service、Lineage 也会继续形成孤岛。

Phase 1 已开始建立 Asset Governance Hub 的统一治理上下文，因此 Phase 2 最有价值的工作是把“开发出来的数据”真正接入治理闭环，而不是继续扩大模块功能表。

## 2. 成功指标

| 指标 / 信号 | 当前基线 | 目标状态 | 如何验证 |
|---|---|---|---|
| Golden SQL Path 完整度 | 能力已存在但分散 | 登录态可从 Workspace 连续完成 Save → Run → Result/Error → Publish → Release/Dataset → Lineage → Asset → 回到 Development | Golden E2E evidence |
| Draft / Revision / Execution 可理解性 | 依赖用户理解内部模型 | UI 中三者状态、身份和下一步操作不会互相混淆 | UX acceptance + user path review |
| 运行失败可诊断性 | 有 Execution History，但用户上下文仍割裂 | FAILED / TIMEOUT / CANCELLED / runtime-state-lost 都有明确原因、证据和恢复动作 | failure-state E2E |
| 运行上下文恢复 | 控制面已支持 durable execution / reattach | 页面刷新、切换节点后可恢复 active execution，并能从历史记录回原 Node | reconnect E2E |
| 发布状态可解释性 | Publish / Release / Task Catalog 能力存在 | 用户可明确识别当前 Draft、Published Revision、Active/Online 状态以及历史版本 | Release E2E |
| 数据产品交付一致性 | Dataset / Data Service 与 Task 并列但语义不同 | Dataset / Data Service 使用各自正确生命周期，同时在同一 Hub 中形成一致交付体验 | product delivery acceptance |
| 治理回链 | Lineage 已有 evidence，Asset 回链未形成完整产品 Gate | Phase 2 至少保证 Dataset 进入 Asset Governance，并支持 Asset ↔ Development 返回路径 | #79 Golden E2E |
| 权限 / Project 隔离 | 后端已有 RBAC / Project Contract | UI、API 和跨域跳转都保持 Project / Permission 边界，不通过隐藏按钮代替后端授权 | role matrix + direct URL/API test |

暂不制造没有真实观测基线的百分比指标。Phase 2 首要成功信号是 Journey 闭环与失败态可解释性。

## 3. 产品上下文（必填）

**所属产品能力：** 开发与运行

**服务用户旅程：**

- J1 — 从外部数据到可信、可治理的数据对象
- J3 — 从数据生产到受治理消费
- J4 — 从发现问题到定位影响

**主入口：**

- Data Development Hub

**上下文入口：**

- Execution History
- Release
- Asset Governance Hub
- Lineage
- Data Service specialist context
- Workflow / Runtime（仅在需要进一步运行或编排时）

**前置步骤：**

可能来自：

- DataSource / Metadata
- Modeling / Semantic / Metric
- 已存在 Development Node / Directory
- Project Space / RBAC

**下一步：**

根据产物类型进入：

- Release / Task Catalog
- Dataset / Asset Governance
- Data Service Runtime
- Lineage / Impact
- Workflow / Schedule
- 下游 Consumption

**核心用户故事：**

> As a 数据开发人员, I want 在一个工作区里完成编辑、运行、调试、发布和追踪产物, so that 我不需要理解平台内部模块边界，也能确定“当前内容是什么、运行了什么、发布了什么、最终产物在哪里”。

## 4. 假设与验证

| 假设 | 为什么这样判断 | 如何验证 | 当前结果 |
|---|---|---|---|
| SQL 最适合作为第一条 Golden Path | SQL 已具备最完整的 datasource / run / lineage / dataset 关联能力 | #76–#79 登录态 E2E | Accepted for Phase 2 MVP |
| 现有 `Node != Draft != Revision != Execution` 领域边界应保留 | 已被现有 Domain / Requirements / Control Plane 保护 | 实施不得通过 UI 方便性破坏该语义 | Accepted |
| 用户不需要直接理解所有内部对象名 | 产品目标是完成任务而非学习内部架构 | UX review / Golden E2E | To validate in implementation |
| Dataset 是 Phase 2 最适合作为强制治理闭环的产物 | F-001 已把 Dataset 作为关键治理对象；Dataset 天然是可消费数据产品 | #79 Asset registration + backlink | Accepted for MVP |
| Published Task 不必在 Phase 2 强制成为一等 Asset | Task 是生产过程，不等同于默认数据消费对象 | 先通过 Execution/Release/Lineage 可追溯；后续按真实需求评估 | Accepted for MVP |
| Data Service 不必为了 Phase 2 被强制转换为 Dataset/Task Asset | Data Service 有独立 authoring/runtime Truth | 通过 source-managed identity + specialist backlink 保持治理证据 | Accepted for MVP |
| 分布式 Runtime 可以独立在 Phase 3 替换 | 当前 Data Development 已通过 TaskExecutionGateway 隔离运行时 | Phase 2 不依赖 Runtime 内部实现，只依赖 gateway/control contract | Accepted |

## 5. 事实归属与所有权（必填）

### Data Development 自己拥有

- Development Directory / Node identity 与项目内组织
- executable Node 的 mutable Draft
- immutable DevelopmentTaskRevision
- Draft optimistic revision / definition digest / publish relation
- Editor 当前上下文与 Data Development editor settings
- DevelopmentTaskExecution durable client-facing identity 与开发侧历史投影
- retry_of_execution_id 等开发侧执行追踪关系
- Dataset output node 的 authoring 配置：datasource + SQL + field contract
- Data Service source-managed authoring / publication owner boundary
- “哪个 Development Revision 产生了什么 Lineage Evidence”的来源关系

### 邻接域拥有

| 事实 | Truth Owner |
|---|---|
| 实际 Task Runtime execution state / worker execution | Shared Task Runtime |
| Task 的跨模块发布状态、online/offline/activate projection | Task Catalog |
| Data Service Runtime truth | Data Service |
| 最终 Lineage Asset / Relation / Impact truth | Lineage |
| Asset ledger / governance identity / governance summary | Asset |
| physical schema / table / column facts | Metadata |
| Project membership / permission truth | Project / Security RBAC |
| Workflow / Schedule definition and orchestration | Workflow / Scheduler |

**事实生产方：**

- Data Development
- Task Runtime
- Task Catalog
- Dataset / Data Service
- Lineage
- Asset

**消费方：**

- Data Development UI
- Release / Operations
- Asset Governance Hub
- Lineage / Impact
- Workflow / Scheduler
- downstream consumption

**真相来源：**

每个状态必须回到其 owning domain。Data Development 可以聚合和导航，但不得因为 Hub 展示需要复制 Task Runtime、Task Catalog、Lineage、Asset 或 Data Service 的第二份 Truth。

是否新增第二份业务真相：**否**

## 6. 方案与范围

### 已选择方案

采用“一个 Data Development Hub + 明确生命周期边界 + 专业域回链”的产品模式：

```text
Workspace / Authoring
  -> Run / Debug
  -> Publish / Release
  -> Data Product Delivery
  -> Lineage / Evidence
  -> Asset Governance
  -> Backlink to Development
```

Hub 负责组织用户旅程，不把所有相邻域实现搬入 Data Development。

### 已评估方案

#### 方案 A：继续按模块页面独立扩展

不采用。会继续暴露内部边界并形成 Workspace / Execution / Release / Dataset / Data Service 孤岛。

#### 方案 B：把所有对象统一成同一种 Task 生命周期

不采用。会破坏 Dataset / Data Service 已明确的领域语义。

#### 方案 C：Phase 2 同时重做分布式 Runtime

不采用。会把产品化与底层执行引擎替换耦合，扩大风险并延迟用户闭环。

### 本次范围

#### A. F-002-A — Development Workspace & Authoring Experience（#76）

- 工作区信息架构
- Directory / Node 创建、搜索、移动、重命名、删除体验
- 多节点切换与编辑上下文保持
- dirty / saving / saved / save-failed / conflict 状态
- 当前编辑内容 / Draft / Published Revision 的用户可见区分
- SQL Golden Authoring Path
- 非 SQL 编辑器复用统一 Workspace shell

#### B. F-002-B — Run, Debug & Execution Experience（#77）

- Editor Run 明确运行当前 editor definition，不隐式 Publish
- durable execution submission / active reattach
- RUNNING / SUCCESS / FAILED / CANCELLED / TIMEOUT / runtime-state-lost
- 日志、结果、错误的产品层级
- cancel / retry / retry chain
- Execution History → Development Node 回链
- Runtime Gateway 边界保持可替换

#### C. F-002-C — Publish, Release & Data Product Delivery（#78）

- Draft → immutable Revision
- Publish validation / version identity
- Published / Online / Offline / Active Revision 语义
- Task Catalog Release
- Dataset output node 产品化
- Data Service 独立 Draft / Revision / Runtime 产品化
- 发布失败 / 校验失败 / 权限拒绝恢复路径

#### D. F-002-D — Development → Governance Closed Loop & E2E（#79）

- Published SQL Revision → Lineage Evidence
- durable outbox diagnostics
- Dataset → Asset Governance（Phase 2 必做）
- Asset 展示 Development source evidence / action
- Asset → Development backlink
- Development → Lineage / Asset specialist action + return context
- Golden SQL 登录态 E2E

### 关键产品决策

#### 1. SQL 是 Phase 2 Golden Path

Phase 2 首先把 SQL 路径做完整，再验证 Shell / Python / Java / HTTP 的共享 contract。第一条 E2E 不要求所有编辑器同时达到相同深度。

#### 2. Dataset 是 Phase 2 强制进入 Asset Governance 的开发产物

Dataset 必须拥有稳定 source identity，并在产出后进入 Asset Governance。Asset 不复制 Dataset authoring Truth。

#### 3. Published Task 暂不强制成为一等 Asset

Published Task 必须可通过 Revision / Release / Execution / Lineage 追溯，但 Phase 2 不为了“统一”而强制注册成 Asset。后续若存在明确发现 / 治理用户需求，再通过独立 Product Decision 扩展。

#### 4. Data Service 保持独立产品身份

Data Service 使用独立 Draft / Revision / Runtime contract。Phase 2 必须提供稳定 source-managed identity、publication state、专业域跳转与回链，但不强制伪装成 Dataset 或普通 Task。

#### 5. Workflow / Scheduling 不是 Phase 2 主工作台的一部分

Development 可把 Published Task 交给 Workflow / Scheduler，但 Phase 2 不在 Development Hub 内重建工作流编排器。

### 明确不做（必填）

F-002 不做：

- 新建分布式 Worker / Queue / Lease / Heartbeat / Failover runtime
- DolphinScheduler / Quartz 等执行引擎替换决策
- 重做 Workflow / Scheduler
- 把 Dataset / Data Service 强行改造成普通 executable Task
- 在 Data Development 内复制 Task Runtime Truth
- 在 Data Development 内复制 Task Catalog release truth
- 在 Data Development 内复制 Lineage graph truth
- 在 Data Development 内复制 Asset truth
- 把所有临时 Draft 注册成 Asset
- Agent-first authoring / AI 自动开发
- 为了 UI 简化破坏 immutable Revision / Editor Run version-0 语义
- 一次性重做所有非 SQL Editor 的深度能力

## 7. 复用要求（必填）

必须复用现有：

- Project Space / RBAC
- Development Directory / Node
- Draft / Revision
- TaskPluginRegistry / validation
- shared TaskExecutionGateway
- DevelopmentTaskExecution durable history
- Execution reconciler / cancel / retry / active reattach
- Task Catalog release projection
- Dataset capability
- Data Service publication/runtime boundary
- Lineage analysis / outbox / write contracts
- Asset Governance Hub Section / Evidence / Provenance 模型
- Audit / existing platform observability

禁止为 F-002 新建：

- 第二套 Task Definition Truth
- 第二套 Execution Runtime State
- 第二套 Release State
- 第二套 Dataset Truth
- 第二套 Data Service Runtime Truth
- 第二套 Lineage relation store
- 第二套 Asset registry

## 8. 用户体验（必填）

### 正常路径

```text
进入 Data Development Hub
→ 在 Directory 中创建/打开 SQL Node
→ 编辑
→ 看到 dirty 状态
→ Save Draft
→ Run current content
→ 查看 Execution 状态 / result
→ Publish 当前 Draft Revision
→ 查看 Published Revision / Release
→ Online 或产出 Dataset
→ 查看 Lineage Evidence
→ 打开 Asset Governance
→ 从 Asset 返回对应 Development Node
```

### 空状态

必须区分：

- Project 中没有开发资源
- Directory 为空
- Node 尚无 Draft
- 尚无 Published Revision
- 尚无 Execution
- 执行成功但无 result payload
- 尚未生成 Lineage
- 产物尚未进入 Asset

“没有数据”不能代替以上所有状态。

### 异常 / 阻断状态

至少覆盖：

- Draft save failure
- optimistic revision conflict
- Task validation failure
- runtime submit failure
- runtime state lost
- execution FAILED / TIMEOUT / CANCELLED
- Publish failure
- Release failure
- Lineage outbox failure
- Asset projection unavailable
- DataSource unavailable

每种异常必须说明：

1. 发生了什么；
2. 当前业务事实是否已提交；
3. 用户可以做什么；
4. 去哪个专业域进一步处理。

### 无权限状态

- UI 可以隐藏或禁用动作，但不能以此替代后端授权；
- Direct URL / direct API 必须保持 Project / RBAC 检查；
- READ / EDIT / EXECUTE / PUBLISH / RELEASE / DELETE 行为必须按既有权限语义区分；
- 跨 Asset / Lineage / Data Service 跳转也必须保持当前 Project Context。

### 加载与长耗时

- 保存 / Run submit / Publish / Release 不使用无反馈全页阻塞；
- Editor Run 提交后立即返回 durable execution identity；
- 长耗时运行使用可恢复状态，而不是绑定单次 HTTP 请求生命周期；
- 独立专业域失败不能让整个 Hub 失去可用性。

### 跨域回链

所有专业域跳转必须尽可能保留：

- projectId
- source node identity
- revision identity（适用时）
- execution identity（适用时）
- return context

Phase 2 的关键回链：

```text
Development <-> Execution
Development <-> Release
Development <-> Lineage
Development <-> Asset
Development <-> Data Service
```

## 9. 治理影响

### Asset

- Phase 2 至少要求 Dataset 产物进入 Asset Governance；
- Asset 保存治理 identity / projection，不复制 Development Draft / Revision；
- Asset Detail 应能展示 Development source evidence 与回链动作。

### Lineage

- SQL Published Revision 可以产生 table / column lineage evidence；
- Data Development 拥有 evidence source relation；
- Lineage graph final truth 仍由 Lineage 拥有；
- Lineage 失败不得回滚已成功的业务 Publish。

### Quality

Phase 2 不新增 Quality Truth。Dataset / Asset 后续质量状态继续通过 Quality owner 接入治理上下文。

### Security

- 保持 Project / RBAC；
- Data Development 不实现第二套数据访问 / masking policy。

### Audit

至少应可追溯：

- Save / Publish / Release
- cancel / retry
- activate historical revision
- Data Service publication action
- destructive resource action

### Usage / Impact

Phase 2 可以提供 Development / Execution 证据，但不创建统一 Usage Truth。

## 10. 未决问题

Status 为 APPROVED 时不存在阻断实施的问题。

| 问题 | 是否阻断 | 负责人 | 决策 / 答案 | 日期 |
|---|---|---|---|---|
| Published Task 是否必须注册为 Asset | 否 | Product | Phase 2 不强制；先通过 Release / Lineage / Execution 可追溯，后续按真实发现/治理需求独立决策 | 2026-09-24 |
| Data Service 是否必须作为 Asset 出现在 Asset Catalog | 否 | Product | Phase 2 不强制；保留独立产品身份和 source-managed backlink，后续由消费产品契约决定 | 2026-09-24 |
| Phase 2 是否包含分布式 Task Runtime | 否 | Product / Architecture | 不包含，归 Phase 3；F-002 只保持 Gateway 可替换性和控制面恢复语义 | 2026-09-24 |
| 是否要求所有 Editor 同时达到 SQL 的完整能力 | 否 | Product | 不要求；SQL Golden Path 优先，其他 Editor 验证共享 authoring/execution shell | 2026-09-24 |

## 11. 支撑证据

- Product Capability Map：`docs/product/CAPABILITY_MAP.md`
- Core User Journeys：`docs/product/USER_JOURNEYS.md`
- Feature Spec Template：`docs/product/FEATURE_SPEC_TEMPLATE.md`
- Data Development Requirements：`yak-ops-business/yak-ops-business-data-development/REQUIREMENTS.md`
- Data Development Dependencies：`yak-ops-business/yak-ops-business-data-development/DEPENDENCIES.md`
- Execution Control Plane：`yak-ops-business/yak-ops-business-data-development/EXECUTION_CONTROL_PLANE.md`
- Stage 2 Project / RBAC rollout：`yak-ops-business/yak-ops-business-data-development/STAGE2_ROLLOUT.md`
- Epic：#75
- Implementation slices：#76 / #77 / #78 / #79

## 12. 架构影响

本节只记录产品契约已经决定的实现约束，不重新做架构设计。

### API

- 保持 `/api/v1/data-development/**` 现有 REST contract 兼容；
- 可以为 UX 闭环新增 read model / navigation context，但不得绕过 owning application boundary；
- Run 保持异步 submission + durable execution identity；
- Publish / Release / Dataset / Data Service 使用各自已有 application boundary。

### 数据库

- 不为产品聚合建立第二套 Truth 表；
- 新增持久化必须先明确 owner，并遵守 Data Development Flyway namespace；
- Published Revision 保持 immutable；
- Execution history 继续作为 Data Development durable client-side execution record。

### Domain

必须保持：

```text
DevelopmentNode != DevelopmentTaskDraft != DevelopmentTaskRevision != DevelopmentTaskExecution
```

并保持：

- Editor Run 不隐式 Publish；
- Dataset 不进入 executable Task Draft/Revision；
- Data Service 使用独立 Draft/Revision/Runtime contract；
- Lineage / Asset / Runtime / Release truth 不反向进入 Data Development domain。

### Events / Async

- Lineage 继续通过 durable outbox / worker 处理；
- 下游 evidence/projection 失败不回滚已完成的业务 Publish；
- Phase 2 若新增 Asset projection，应遵守同类 eventual consistency 与可诊断原则。

### 兼容性

- 现有 Draft revision / checksum / append-reuse 语义保持；
- Task Plugin validation 保持；
- Editor Run version-0 snapshot 保持；
- Task Catalog online/offline/activate 行为保持；
- Data Service source-managed owner boundary 保持；
- Project / RBAC 强隔离保持。

## 13. 验收标准（必填）

### E2E 场景：Golden SQL Development Path

**Given / 前置条件：**

- 用户已登录；
- 用户位于一个有效 Project；
- 用户拥有 Data Development READ / EDIT / EXECUTE / PUBLISH / RELEASE 所需权限；
- 存在可用 DataSource；
- Asset / Lineage 邻接能力已启用。

**When / 用户动作：**

1. 打开 Data Development Hub；
2. 创建或打开 SQL Node；
3. 修改 SQL，并保存 Draft；
4. 在未 Publish 的情况下运行当前内容；
5. 查看 Execution RUNNING → terminal 状态；
6. 查看 result 或明确 error；
7. 刷新页面并确认执行上下文可恢复；
8. Publish 当前 Draft 为 immutable Revision；
9. 在 Release 中确认 Published Revision / Online state；
10. 形成 Dataset 或明确 SQL 产物交付路径；
11. 查看对应 Lineage Evidence；
12. 在 Asset Governance Hub 找到 Dataset；
13. 从 Asset 返回原 Development Node / Revision 上下文。

**Then / 期望结果：**

- 用户始终能知道当前编辑内容、Draft、Published Revision、Execution 分别是什么；
- Editor Run 不产生隐式 Published Revision；
- Execution 可恢复、可取消、可重试、可追溯；
- Publish 产生 immutable Revision；
- Release 状态来自 Task Catalog；
- Dataset 使用自己的 output lifecycle；
- Lineage 与 Asset 使用 owning-domain Truth；
- 跨域跳转可回到原 Development Context；
- 任一邻接域暂不可用时不制造 EMPTY 假象，也不复制第二份 Truth。

### 失败态 E2E

至少验证：

- Draft conflict
- Save failure
- validation failure
- runtime submit failure
- runtime state lost
- task FAILED
- cancel / retry
- Publish failure
- permission denied
- Lineage unavailable / outbox pending
- Asset projection unavailable

### 验收证据

- UI：Workspace / Editor / Execution / Release / Dataset / Asset 页面证据
- API：Draft / Run / Execution / Publish / Release / Dataset / Lineage / Asset 关键响应
- 持久化事实：Draft revision、immutable Revision、Execution history、outbox / projection reference
- Audit：Publish / Release / retry / destructive action
- Observability：runtime lost / outbox failure / projection failure 可诊断

## 14. 收尾与沉淀

当 F-002 更新为 `SHIPPED` 时必须完成：

- 更新成功指标当前结果；
- 将长期产品规则提升到 Product Truth；
- 将稳定 Node / Draft / Revision / Execution 规则继续维护在 Domain Contract；
- 将跨模块 corridor / Gateway / outbox 规则提升或同步到 Architecture Contract；
- 保存 Golden SQL E2E evidence；
- 关闭 #76 / #77 / #78 / #79 后再关闭 #75；
- Phase 2 遗留的 distributed/durable runtime 问题进入 Phase 3，不留在 F-002 内无限扩张。
