# data-ops 全产品用户故事地图 V2｜从数据建设到持续可信消费

Status: DRAFT · 全产品范围校准与用户故事规划（待 Product / 业务代表评审）  
Date: 2026-10-10  
Owner: Product（评审责任，非批准结果）  
Parent: [#513 用户故事地图](https://github.com/gitfortian/data-ops/issues/513)  
First deep vertical sample: [#512 真实订单 0→1 走查](https://github.com/gitfortian/data-ops/issues/512) / [F-040（DRAFT）](../features/F-040-data-construction-experience.md)  
Binding references: [产品愿景](../PRODUCT_VISION.md)、[原则](../PRODUCT_PRINCIPLES.md)、[能力地图](../CAPABILITY_MAP.md)、[J1–J6](../USER_JOURNEYS.md)、[PD-002 ACCEPTED](../decisions/PD-002-governed-consumption-contract.md)、[PD-003 ACCEPTED](../decisions/PD-003-business-semantic-metric-contract.md)  
Nonbinding proposal: [PD-010 PROPOSED](../decisions/PD-010-guided-business-to-data-journey.md)

> **使用方法**：本文件是全产品 **Story Map（广度）**，回答“谁在什么场景中，想完成什么，什么才算完成”，**不等于全平台 PR 计划、已实现能力清单、UX 页面稿或已批准领域合同**。每条故事独立验证、由现有 Truth Owner 承载。#515/F-040 是 J1/J2/J3 的首条纵向深入设计，不代表 J4/J5/J6 或消费全域已设计完成。需要新建/调整顶级产品域、默认跨域路径或长期治理规则，仍遵循 Product Change Process。

## 0. 地图的目标、范围和地图单位

**北极星**：让有权限的用户无须自己拼接孤立模块，能够从来源、业务需求、已发布数据产品、运行异常、安全请求或自然语言问题出发，取得**真实、可信、可追溯、可治理且能继续行动的结果**。

**故事单位**：一段有用户目标、入口、核心决策、正式事实、完成证据、回链与失败态的工作；不是「增加按钮、CRUD 页面、Controller、Maven module」。Story Map 上的故事编号稳定，之后具体 UX 设计/Feature/验收引用同一编号，不复制一套新对象 ID。

**覆盖边界**：正式 [能力地图](../CAPABILITY_MAP.md) 的五组产品能力、MDM 专业解决方案、六条核心 Journey 与横切平台能力全部占位；未评审过的能力只规划**应当服务的用户目标**，绝不因此标记「功能已实现」。

**与既有 #513 V1 的关系**：保留 0→1 订单样例、A0–A9 和 US-00～22、US-11L/11P 的历史 ID。V2 的 \`GM-J*\` ID 是全产品故事卡：映射到原 US 时引用，不重新定义另一套事实。V1 以生产者为主要视角；V2 补消费者、运维、安全、AI、专门方案。

### 0.1 角色：同一用户可以兼任多种职责

| 人物角色 | 主要工作与成功判据 | 典型入口 |
|---|---|---|
| BIZ 业务负责人 / 指标口径确认者 | 业务问题和事件口径被正确理解，结果可解释 | 业务目标、业务过程、指标详情 |
| BUILD 数据建设实施者 / 数据架构师 | 真实来源→合规模型→任务交付，少重复录入 | DataSource、资产、模型工作台 |
| STEWARD 数据治理 / 标准负责人 | 标准、资产、质量、生命周期有明确责任且真实生效 | Standard、Asset、质量/影响视图 |
| DEV 数据开发工程师 | 可审查地保存、发布、运行、恢复任务 | DataDev、Workflows、Execution |
| OPS 调度/运维负责人 | 发现问题、评估影响、完成受权恢复且可审计 | 告警、实例、血缘/影响 |
| SEC 安全管理员 / 审批人 | 敏感数据正确分类、最小授权、实际访问可审计 | 分类、策略、访问请求、审计 |
| CONS 业务分析师 / 数据消费者 | 找到合适数据产品并获得被允许的真实查询/调用 | Consumption Hub、Dataset/Service、分析 |
| PRODUCER 数据产品负责人 | 发布可理解、质量清楚、有消费者反馈的数据产品 | Dataset/Service、产品详情、Usage |
| AI-USER 业务提问者 / AI 协作者 | 得到基于批准范围的数据证据回答，可复核来源 | 业务问答、分析上下文 |
| MDM 主数据管理员（专业分支） | 维护主数据一致性与分发闭环 | MDM 专业工作台（产品形态待 PD） |

### 0.2 两个轴，绝非“所有人从数据接入开始”

**横轴 = 各用户真实的活动顺序；纵轴 = 其完成所需任务及在不同价值切片中的优先程度**。

| 独立主线 | 入口 | 活动 Backbone（由左至右） | 完成结果（不是最后一个页面） |
|---|---|---|---|
| **J1 数据建设** | 已有数据 / 业务目标 | 授权来源 → 发现/集成 → 理解/标准 → 逻辑/物理建模 → 加工/运行 → 质量/资产交付 | 一个可追溯、可运行、可治理的真实数据对象 |
| **J2 业务指标** | 要回答的业务问题 / 业务过程 | 确认语义 → 复用标准/模型 → 定义指标 → 版本化验证 → 发布 → 受治理使用/影响 | 业务部门认可、可重复使用的统一指标 |
| **J3 数据消费** | 想找数据 / 下游应用 | 发现 → 理解合同/可信度 → 授权与订阅 → Query/Preview/Invoke/Export → Usage → 反馈/影响 | 一次真实、被允许的消费和可追踪使用证据 |
| **J4 异常恢复** | 告警 / 质量不合格 / 用户报障 | 确认异常 → 定位对象 → 找原因与影响 → 授权修复/重跑 → 验证恢复 → 通知闭环 | 生产或数据质量已恢复，影响有据可查 |
| **J5 安全消费** | 敏感信息 / 访问申请 | 发现分类 → 定义策略 → 审核授权 → 执行访问判定/脱敏 → 审计 → 调整/撤销 | 受保护的数据只被正确授权者合法使用 |
| **J6 可信 AI 分析** | 自然语言业务问题 | 澄清目标 → 发现已批准语义/数据 → 规划授权查询 → 执行并取证 → 解释/回答 → 追问与复核 | 有可追溯数据来源与不确定性说明的答案 |

**横向复用**：J1/J2 生产可信数据定义；J3/J6 消费；J4 为所有生产/消费进行恢复；J5 为所有访问设置真实控制点。这些是互相连接的旅程，不应串成一个强制九步“超级向导”。

### 0.3 共享权威事实 / 引用（不得复制）

| Owner | 核心正式事实 | 可借用的上下文，但不是新 Truth |
|---|---|---|
| DataSource / Integration / Metadata | 数据源授权、采集/同步证据、表列、来源版本 | Source Evidence/Task 引用、采集批次/指纹 |
| Semantic | BusinessDomain/Process、Standard、StandardField、分层策略 | ProcessRef、StandardFieldRef、标准版本/有效性 |
| Modeling | 逻辑实体/属性/关系、逻辑版本、物理模型/列、来源/逻辑→物理映射、模型设计版本 | LogicalVersionRef、PhysicalModelVersionRef、列身份 |
| DataDev / Workflow / Runtime | 开发定义、任务 Draft/Revision、工作流/调度、执行与实例恢复证据 | Task/Run/ExecutionRef，不用模型发布替代运行 |
| Asset / Quality / Security / Lifecycle / Lineage | 各自拥有的编目、质量结果、策略/访问裁决、TTL、关系证据 | AssetRef、QualityResult、Policy/Decision、Edge/Impact |
| Metric | Metric identity/精确版本、Validation/Publication、指标依赖/声明引用 | PublishedMetricContract |
| Dataset / Data Service / Consumption | source-specific Dataset/Service 合同；governed projection、Subscription、normalized Usage Evidence | ProductKey、ConsumerRef、Access/Usage，遵循 PD-002 |
| 平台身份/审批/审计 | Project Scope、权限、审批决策、审计事件 | 原业务操作上下文，不是第二套领域真相 |

**一条不可违反的边界**：Model published ≠ physical deployed ≠ task executed ≠ metric validated/published ≠ dataset query succeeded ≠ actual usage。设计期血缘、结构包含（CONTAINS）、运行读写、消费者使用证据也互不等同。

---

## 1. J1｜用户要将外部数据变成可信且可以运行的数据对象

**主角色** BUILD / DEV；协作 BIZ / STEWARD；既有 V1 的订单案例和 F-040 对本组有较深证据。

| Story ID | 用户故事（我希望……以便……） | 入口 / 关键确认 | 成功结果与证据 | 与既有故事 |
|---|---|---|---|---|
| GM-J1-01 | 识别并接入有权限的数据库或文件来源 | DataSource / File，凭据、采集范围、测试连接 | 授权来源 ID、连接测试与责任/范围 | US-01 |
| GM-J1-02 | 发现外部表/列并清楚知道是何时看到的 | Metadata Harvest；完整/局部采集、漂移 | 来源快照/指纹、Schema/列证据、失败原因 | US-02 |
| GM-J1-03 | 将发现的对象纳入待治理资产以便分配责任 | Metadata/Asset；避免自动公开上架 | AssetRef、治理状态、责任、来源回链 | US-03 |
| GM-J1-04 | 从业务需求或物理来源确认业务域、业务过程和真实数据粒度 | 业务域/过程，证据和人工确认 | ProcessRef、业务事件、MAIN/DETAIL 的粒度边界 | US-04/05 |
| GM-J1-05 | 复用或新建标准与标准字段，而非逐列搬运数据定义 | Semantic；标准角色/启停/类型/单位/码值 | StandardFieldRef、过程字段引用、标准缺口与审计 | US-06/07/08 |
| GM-J1-06 | 快速建立保真 ODS 来源镜像，保留异常原值与来源列身份 | 表/Asset→ODS，批量差异审阅和来源确认 | 结构/源表/逐列映射真实回读，不声称同步已执行 | US-09/10/11 |
| GM-J1-07 | 以业务语言设计逻辑实体、属性、关系和粒度 | 过程+标准/ODS 候选，关系/基数人工确认 | LogicalModel/Version、Entity/Attribute/Relation refs | US-11L |
| GM-J1-08 | 从逻辑版本评审/关联已存在非 ODS 物理模型 | 逻辑→物理，分层/目标方言/冲突/来源转换 | 合规物理草稿和映射；非 ODS 字段合法 StdFieldRef | US-11P/12/13 |
| GM-J1-09 | 让已有数据确实同步/加工至目标存储 | 离线同步/SQL、全量/增量/CDC 策略、目标环境 | 正式同步/开发任务、目标授权、真实执行/失败回执 | US-15/16 |
| GM-J1-10 | 处理实时数据源、流式同步与 schema 变更 | 实时接入/集成（当前仅规划，需能力审计） | 延迟/位点/重放/漂移和运行结果，不能将预览当同步成功 | V2 新增 |
| GM-J1-11 | 将实际产出的对象质量检查并建立真实血缘和治理状态 | 质量/Lineage/Asset，缺口需明确 | QualityResult、真实 upstream/run evidence、资产态 | US-17/18 |
| GM-J1-12 | 知道哪些模型/任务可交接并继续形成消费资产 | Model Version、DataDev、资产详情 | 可返回准确设计/部署/运行状态，链接到 J2/J3 | US-20 的前置 |

**J1 特别边界**：ODS 可保真而不预先建立逻辑模型/标准字段；非 ODS 字段强制标准的发布门禁处于 PD-010 待批准状态，不能把建议当已实施事实。同步、Metadata Harvest 和开发执行可以分属不同 Owner，不能用“数据源已连接”代替“数据已生产”。

**异常与恢复**：账号无权、表消失、采集仅部分成功、元数据漂移、已有模型并发修改、金额/时间脏数据、目标方言无法保真、目标库未配置、任务超时/失败。用户应能分清“无数据”“没有采集到”“无权”“服务不可用”。

## 2. J2｜用户从业务定义得到可验证、可复用的统一指标

**主角色** BIZ / STEWARD；协作 BUILD / PRODUCER / CONS。遵守 PD-003：Metric != MetricVersion != Validation != Publication。

| Story ID | 用户故事 | 入口 / 关键确认 | 成功结果 |
|---|---|---|---|
| GM-J2-01 | 明确指标服务的业务问题、业务事件与计算粒度 | 业务目标/过程，确认时间、范围、过滤与主体 | 有责任人的业务需求与 ProcessRef；尚非 Metric 已发布 |
| GM-J2-02 | 复用正式业务口径、单位、标准字段与合法模型 | 语义/标准目录、逻辑/物理模型 | Caliber/Unit/StdField/Model 精确引用，缺口可回链 |
| GM-J2-03 | 区分原子、派生、复合指标并定义计算语义 | Metric Workspace；依赖、公式、去重/聚合 | Stable Metric ID 与 Draft，未猜默认指标 |
| GM-J2-04 | 对指标定义进行版本校验，而非看到字段就宣布可用 | Validation；定义依赖、状态/时间窗口 | 精确 MetricVersion 上的定义验证结果 |
| GM-J2-05 | 对实际数据进行可重复的数据/执行验证 | Metric Validation + DataDev Execution | 数据级验证证据，失败可回任务/源模型 |
| GM-J2-06 | 受权发布一份精确指标定义，其他消费引用不跟随可变草稿 | Metric Publication | PublishedMetricContract、版本/理由/审计 |
| GM-J2-07 | 将指标交给已有 Dataset/Service 消费，知道是否真正被使用 | Published Metric→Consumption | 引用使用（声明）与实际 Usage Evidence **分开** |
| GM-J2-08 | 变更口径后知道受影响模型、指标与消费方 | Metric Impact/Usage | Dependency UP_TO_DATE/OUTDATED/REMOVED、受影响清单与回链 |

**错误态**：口径多解、标准已停用、模型无授权/版本过期、定义验证通过但真实数据未执行、发布被拒绝、消费者引用旧版。**重要**：标准字段 ROLE_METRIC ≠ 自动创建 Metric，也不等于物理字段 MEASURE/SUM 聚合。

## 3. J3｜用户找到并实际使用一个受治理的数据产品

**主角色** CONS / PRODUCER；协作 SEC / STEWARD / OPS。**可以直接从 Consumption Hub 开始，无需进入建模工作台**。遵守 PD-002：Dataset / Data Service 保留各自 source Truth，Data Product 是投影。

| Story ID | 用户故事 | 入口 / 关键确认 | 成功结果 |
|---|---|---|---|
| GM-J3-01 | 搜索/浏览与业务问题相关的可用数据产品 | Consumption Hub / Search，权限裁剪 | Product View / source contract 引用，不产生第二产品库 |
| GM-J3-02 | 判断这个产品是什么、数据粒度、更新情况、质量与负责人 | Product Detail，Dataset schema 或 Service interface | 来源、拥有者、真实数据合同、质量/状态及可信度解释 |
| GM-J3-03 | 申请或核对是否有权使用，知道缺什么 | Access Projection / Approval | ACCESS_ALLOWED/FORBIDDEN/需要审批的真实裁决，不假授权 |
| GM-J3-04 | 建立订阅/依赖以便接收后续变更影响 | Subscription | 明确声明的 ConsumerRef/依赖关系，不冒充已执行使用 |
| GM-J3-05 | 在允许范围内预览/查询 Dataset 并看到真实响应 | Dataset Query/Preview | 权限与数据提供方响应，EMPTY 与 UNAVAILABLE 分开 |
| GM-J3-06 | 按合法接口调用 Data Service 并获得结果/错误 | Data Service Invoke | Source Service 的接口/运行回执、请求范围和访问裁决 |
| GM-J3-07 | 将结果导出或交给分析、Dashboard、大屏、下游应用 | Export/Analysis/Dashboard/Screen | 实际消费行为、精确契约/权限与错误回执；不自造 Dataset |
| GM-J3-08 | 知道真实谁在用、订阅谁、变更会影响谁 | Usage / Impact / Backlink | Usage Evidence、Subscription、Lineage **分别**展示 |
| GM-J3-09 | 遇到未上架、过期、无权限或服务不可用时找到正确下一步 | Hub / Product Detail | 可解释状态与请求上架/申请访问/联系 Owner/重试动作 |

**体验评审重点**：数据消费者不应该为一次 Dataset 预览先配置建模、元数据和调度；Analysis、Dashboard、Agent 都应复用已治理消费合同和 Access，而不是各自自由 SQL 或另做数据权限。

## 4. J4｜用户从异常恢复真实的数据生产和消费

**主角色** OPS / DEV / STEWARD；协作 PRODUCER / CONS；可以从运行故障或用户反馈直接开始。

| Story ID | 用户故事 | 入口 / 关键确认 | 成功结果 |
|---|---|---|---|
| GM-J4-01 | 收到真正相关的运行、同步或质量异常，而不是漫天技术告警 | Alert / Instance / Quality | Alert/Failed Execution ID、时间、严重度、可定位对象 |
| GM-J4-02 | 理解哪些源、开发任务、工作流、资产、指标或产品受影响 | 告警→任务/对象→Impact | 上下游有据的范围、责任人与版本；UNKNOWN 不显示 0 |
| GM-J4-03 | 区分故障是来源、权限、SQL、调度、质量还是消费 Provider 问题 | Runtime Logs / Quality / Access | 可复核根因候选、证据和负责人 |
| GM-J4-04 | 获得授权后修复配置、作业或异常数据，保证变更留痕 | 专业 DataDev/Workflow/Asset | 修复定义/审批/审计、受影响范围与回滚计划 |
| GM-J4-05 | 依据失败位点安全补数、重跑或回滚而不重复污染数据 | Instance Ops / Backfill | Run/Retry/Backfill ID、幂等区间、可查询执行结果 |
| GM-J4-06 | 验证数据与业务口径确实恢复，通知消费者并完成复盘 | Quality/Metric/Usage + Notification | 恢复验证、仍未知缺口、影响方通知和事故记录 |
| GM-J4-07 | 检查源端结构/业务定义/标准版本变化会损害哪些数据结果 | Drift / Impact | 指纹/依赖版本、可信影响链和受影响 Owner |

**J4 的核心区别**：“任务运行成功”不代表“质量恢复”；“有技术血缘”不代表“确有消费者”；不能把补数点击结果当数据已完整复原。治理/运行恢复都要有真正受权的执行回执。

## 5. J5｜用户能保护敏感数据并进行合法消费

**主角色** SEC；协作 STEWARD / PRODUCER / CONS / 审批者。安全不是 J3 最后一个可选 Tab，而是横贯发现、建模、执行、服务和导出。

| Story ID | 用户故事 | 入口 / 关键确认 | 成功结果 |
|---|---|---|---|
| GM-J5-01 | 发现可能的敏感字段并理解证据和适用范围 | Metadata/Asset/Security | 分类候选与**人工确认**、数据对象精确身份 |
| GM-J5-02 | 定义访问/脱敏策略、责任和生效范围 | Policy / Project / Role | 正式策略/版本、适用对象、默认拒绝范围 |
| GM-J5-03 | 审核具体申请，按最小权限和时效授权或拒绝 | Approval / Access Request | 决策、执行授权与审计，申请不等于已授权 |
| GM-J5-04 | 在 Query/Invoke/Export 等真实消费路径执行授权判定 | Access Decision | ALLOWED / FORBIDDEN，provider 失败不能被误报成拒绝或空表 |
| GM-J5-05 | 在支持的消费协议实施脱敏、字段限制或拒绝 | Dataset/Service Runtime / Security | 真正执行结果或显式 NOT_APPLICABLE；不以策略配置页代替生效 |
| GM-J5-06 | 回查实际访问、撤权/过期对下游影响 | Audit / Usage / Security | Access/Audit Evidence、撤销后的实际行为验证、受影响消费方 |
| GM-J5-07 | 在跨项目、跨租户、临时授权及故障情况下保持隔离 | Project Scope / RBAC | 服务端拒绝、无敏感泄露、可审计，未知时安全失败 |

**安全验收不能只测有权限角色**；需不授权、跨项目、撤权后缓存与 Provider UNAVAILABLE 的反向用例。项目/权限是正式源域能力，不新建可编辑的 “安全向导事实”。

## 6. J6｜用户提出业务问题并得到有证据的智能分析

**主角色** AI-USER / CONS；协作 BIZ / STEWARD / SEC。Agent 是可审查的协作者与消费者，不因模型会生成 SQL 就自动获数据访问权。

| Story ID | 用户故事 | 入口 / 关键确认 | 成功结果 |
|---|---|---|---|
| GM-J6-01 | 用业务语言表达问题并得到必要的业务澄清 | Chat / Analysis，时间、主体、口径、范围 | 经用户确认的问题/参数，而非猜测事实 |
| GM-J6-02 | 找到可信业务语义、标准、指标与已发布消费对象 | Semantic/Metric/Consumption Discovery | 精确可访问版本/合同与证据来源 |
| GM-J6-03 | 判断问题能否在当前权限/质量/时间范围内回答 | Access/Quality/Provider Readiness | 允许/需申请/不足证据/不可用状态与解释 |
| GM-J6-04 | 生成可审核的查询/分析计划而不是直接执行自由 SQL | Query Plan / Evidence | 指标/数据集引用、过滤/聚合依据、成本与边界 |
| GM-J6-05 | 用获授权的正式查询/调用通道取得真实数据 | Dataset/Service/Structure Query | Query/Invoke/Execution 回执、适用授权和失败恢复 |
| GM-J6-06 | 用业务可读语言回答并给出数据版本、依据与不确定性 | Analysis / Report | 可点开的引用、数据时间范围、质量限制、禁止捏造 |
| GM-J6-07 | 将建议交给人确认或复用分析结果，不自动改变业务 Truth | Agent Suggestion / Report/Follow-up | 候选/审批/保存结果与创建人，未确认不写 Semantic/Metric |
| GM-J6-08 | 在数据缺失、冲突、权限不足或服务失败时明确拒答/追问 | Agent Errors | 完整证据不足原因，绝不降级为不可验证“看似正确的答案” |

**AI 可复用**：J1 的语义/标准/逻辑候选，J4 的根因候选，J3/J5 的受治理消费；但这不等于一个 LLM 可以越过 Domain API 替用户发布、运行、审批或查询私有数据。Agent 是否有专门顶级产品入口需正式 Product Decision，不在此自行新增。

---

## 7. 专业解决方案分支：MDM 主数据

**MDM 当前是能力地图中的专业方案，不自动归类为已实现或全平台第六个一级产品域。** 以下是待业务用户评审的独立 Story Map，依赖已有集成、质量、审批、资产、安全和服务，**不能因通用资产能力存在就断言专业 MDM 已交付**。

| Story ID | 专业用户要做的工作 | 与共享平台能力的边界 | 可验证结果 |
|---|---|---|---|
| GM-MDM-01 | 选择关键主数据对象、唯一标识与治理责任 | Business Semantic/Asset，专属实体规则待定 | 主数据对象定义、Owner 和权责 |
| GM-MDM-02 | 从多个系统发现同一个客户/产品的候选记录 | Sync/Metadata、Match Evidence | 来源记录与候选相似度证据 |
| GM-MDM-03 | 审阅冲突、匹配/合并建议和不确定数据 | 专属匹配/生存规则（需独立合同） | 人工确认或拒绝的匹配/合并记录 |
| GM-MDM-04 | 通过审批形成合法主数据版本与黄金记录 | Approval / Audit / Security | 被批准的 Golden Record 与历史/血缘 |
| GM-MDM-05 | 将主数据变更按受控合同分发给下游应用 | Data Service / Integration | 分发执行回执、拒绝/重试/版本对账 |
| GM-MDM-06 | 处理重复、纠错、拆分/回退并评估关联资产影响 | Quality / Impact / Ops | 有记录的修复、版本及受影响系统恢复 |

正式规划前需询问：MDM 是独立 Solution Pack、已有通用模型上的专业工作区，还是先不列本期范围？产品形态变更必须另行 Decision，不把 MDM 与一般数据标准字段目录混为同一概念。

## 8. 横切故事：每条 Journey 都必须真实经过的控制点

| 横切 ID | 用户需要的保障 | 需要贯穿的旅程 | 可接受的证据 |
|---|---|---|---|
| GM-X-01 | 任何读取、写入、发布、运行都属于可信 Project Space，不能通过传 ID 跨项目 | J1～J6/MDM | 服务端 Project/RBAC 审核和不越权回执 |
| GM-X-02 | 高风险动作分清预览、保存、审批、发布、部署、运行、消费 | J1～J6/MDM | 操作名/状态/审计记录及真实执行 ID |
| GM-X-03 | 重要业务决策（标准、关系、口径、安全、修复）有确认与历史版本 | J1/J2/J4/J5/MDM | Owner/批准人/版本、历史回读 |
| GM-X-04 | 失败、过期、无权、暂无数据、未适用、未知必须分开呈现 | J1～J6/MDM | EMPTY/STALE/UNAVAILABLE/FORBIDDEN/FAILED/NOT_APPLICABLE/UNKNOWN |
| GM-X-05 | 来源、标准、模型、任务、指标、消费具有稳定回链与影响追踪 | J1～J6/MDM | 正式 Ref、Version、关系/证据 Owner |
| GM-X-06 | Agent/自动化产生的是有证据的建议；正式写入/执行必须受控 | J1/J2/J4/J5/J6/MDM | 建议与用户确认/权限操作可审计 |
| GM-X-07 | 告警、通知、审批的处理状态能够回到原业务对象 | J3/J4/J5/MDM | 通知目标与实际对象/任务/请求一致 |
| GM-X-08 | 专业产品缺少 Provider/插件时，不在 UI 假装该能力有数据 | J1～J6/MDM | 插件不可用/能力未支持/权限不足不同状态 |
| GM-X-09 | 生命周期、留存/删除规则在实际目标和消费中生效，不只是模型设置项 | J1/J3/J4/J5 | 下发/执行/不适用状态与安全审计 |

**架构约束**：这些是验收横切，不等于要新增一个统一 Project Truth/Approval Engine 业务域；现有 Project、RBAC、审批、审计、告警、Runtime、Plugin/SPI、存储/Scheduler 继续各司其职。

---

## 9. 价值切片：纵向切穿多个能力，而不是先做完一个模块

优先级是 **建议队列，待产品/业务代表评审**。完成标准为业务结果/E2E 证据，不是 PR 合并或菜单可点击。**全域规划不要求全域详细 UI 一次完成**。

| 切片 | 用户目标和端到端价值 | 最小贯通范围（显式复用已有能力） | 首个验收样例 | 当前依据 |
|---|---|---|---|---|
| **V0 统一故事与边界** | 所有人能解释六旅程的入口、产出和权威 Ref，没有跨域重复事实 | 全域用户故事/Owner/阶段/地图评审 | 本文件 + 覆盖矩阵评审 | 当前正在规划 |
| **V1 第一次可信数据设计** | BUILD 从真实来源得到可回读、合规的设计 | J1 来源/ODS、Semantic 标准、Logical、Physical 设计；横切权限/版本 | #512 trade_order → ODS → 订单逻辑模型 → DWD 设计 | F-040 Slice A，Draft |
| **V2 第一次可信业务结果** | BIZ/CONS 能得到一项真正可消费、经确认的订单业务结果 | J1 DataDev/Run + J2 Metric/Validation + J3 Dataset Query，质量/安全/血缘 | 每日订单量（具体口径须业务确认），真实 Dataset 查询与 Usage | F-040 Slice B，Draft |
| **V3 消费者自主使用** | CONS 不找工程师也能合法发现/判断/访问/订阅已有数据产品 | J3 全主线，J5 Access 的基础 | 消费者使用已有 Dataset 或 Data Service，含禁权/Unavailable | PD-002 Accepted，实施/验收未闭环 |
| **V4 运营异常恢复** | OPS 从一个告警定位受影响对象、恢复数据、通知消费者 | J4 + J1 同步/开发 + J3 Usage/Impact + 横切告警/审计 | 单次生产实例失败→受权重跑→质量恢复→影响通知 | 当前未实机评审 |
| **V5 安全访问闭环** | SEC 能证明敏感数据分类、访问策略与实际消费的执行结果 | J5 + J3 查询/服务 + 权限/审计 | 手机号字段敏感策略→拒绝/脱敏→撤权验证 | 当前未实机评审 |
| **V6 可信 AI 业务问答** | AI-USER 从业务问题得到引用批准指标/数据的有证据答案 | J6 + J2 Metric + J3 Consumption + J5 Access | “上周订单量变化”含时间/口径确认、访问裁决、数据依据 | 当前未实机评审 |
| **V7 专业主数据治理** | MDM STEWARD 合并受控主数据并安全分发 | MDM 支线 + Sync/Quality/Approval/Security/Service | 两个来源客户候选→人工确认→主记录/分发回执 | 产品形态与优先级待决 |

**首批优先排序建议**：V0（现在）→ V1/V2（一条建设到使用的黄金故事）与 V3 的共享消费合同收口并行 → V4/V5 → V6；V7 需实际 MDM 业务需求和独立决策。排序是产品讨论候选，不是预设所有 MDM/实时同步或安全能力都已具备；某些安全/权限保障必须在 V1/V2 即满足，不得等 V5 才上线。

### 9.1 每个价值切片都要回答八项交付判据

1. 谁从哪里进入？是否允许从中途已有对象继续，而不强迫从第一屏重新开始？
2. 用户的真正目标成果是什么？哪个 Owner 持有稳定身份？
3. 用户做哪几个不可由系统猜测的业务判断？Agent 的建议如何获得确认？
4. 跨域引用/版本来自哪一份 Truth？变更、停用、撤权会怎样？
5. 预览、保存、审批、发布、执行、查询分别会产生什么实际副作用？
6. 没权限/部分成功/来源漂移/Provider 不可用/真实空结果时如何恢复？
7. 结果如何回链到来源、上下游与责任人？Impact、Lineage、Subscription、Usage 各有什么证据？
8. 真实 E2E 怎样证明端到端完成？哪些仍只是静态方案/模拟验证？

## 10. 建议的全产品交互设计评审队列

| 顺序 | 需要做深的用户故事 | 不应当做成什么 | 深度交付 |
|---|---|---|---|
| 1 | J1/J2/J3 交集的首个设计→运行→消费：F-040 | 全产品新向导/全部物理页面重写 | 复用已有十屏草案，按 V0 修订后评审 |
| 2 | J3 独立消费者旅程：发现、访问、Dataset/Service/分析/Dashboard | 只有工程师视角的“任务后一步” | 消费者故事板+PD-002 精确行为与 E2E |
| 3 | J4 任务/质量告警到恢复：血缘/影响、补数/重跑、通知 | 监控仪表盘堆图表无修复动作 | 运营任务地图+一次真实失败恢复 |
| 4 | J5 分类/审批/访问/撤权：安全真正进入 Query/Invoke/Export | 敏感标签只展示不生效 | 安全管理员/消费者双角色故事板 |
| 5 | J2 专项指标变更和 J6 AI 可信分析 | 新建第二 Query/Metric Runtime | 版本影响故事 + Evidence-based AI Story |
| 6 | MDM 专业方案、实时/文件复杂接入与企业特殊场景 | 不加需求就无限扩模块 | 基于真实业务优先级做独立方案评审 |

队列排序会在 V0 用户/价值评审中调整；已完成 #515 的 V2/V3 交互与领域契约继续保留为证据和首个 Slice 的候选方案，不自动被认可为“全域批准”。

## 11. 全局待裁决的产品问题

| QID | 问题 | 建议与影响 | 裁决归属 |
|---|---|---|---|
| GM-Q01 | 6 条正式 Journey 能否覆盖当前全部真实用户目标？有无重大独立用户旅程遗漏？ | 先补角色/成果，而非先加一级菜单 | Product/业务代表 |
| GM-Q02 | 是否以数据建设 V1/V2 为首个深度切片？现有消费、运行用户是否有更紧急痛点？ | 按实际价值和 E2E 成本排队 | Product/业务/OPS |
| GM-Q03 | 多入口聚合任务状态是否需要新跨域状态或任务对象？ | 默认**只读投影**，按已有 Owner Ref，除非新 PD 证明需要 | Product/Architecture |
| GM-Q04 | 逻辑模型在哪些路径是必要设计桥梁？ODS 例外何时启用？ | 优先符合 F-040/PD-010 待批准规则 | Product/Semantic/Modeling |
| GM-Q05 | 非 ODS “所有字段必须在标准字段库”的校验点及合法技术列例外如何正式冻结？ | 已确认强制方向；细节待 PD-010 | Product/Semantic/Modeling |
| GM-Q06 | 消费端能否从 Asset/Dataset/Service/Dashboard 进入同一 governed contract？ | 遵守 PD-002，不能重造消费 Truth | Product/Consumption |
| GM-Q07 | 告警/血缘/Usage/影响中哪种关系必须提供真实运行证据？ | 不把 CONTAINS 和订阅当真实下游执行 | OPS/Quality/Lineage/Consumption |
| GM-Q08 | AI 辅助建设与 AI 受治理问答，谁负责确认、访问与审计？ | 不授权 Agent 直接自建业务事实或自由 SQL | Product/Security/AI |
| GM-Q09 | MDM 是否为独立专业方案？本期有真实组织与业务验收需求吗？ | 先业务需求，后 Solution Pack Decision | Product/MDM Owner |
| GM-Q10 | 数据源同步、实时/文件接入、调度恢复、Dashboard/大屏有哪些真实业务价值缺口？ | 后续专项发现，不因为代码存在就说 E2E 已通过 | Product/各能力 Owner |

**已正式接受的边界优先**：PD-002 的 Data Product governed projection/Usage，PD-003 的 Semantic–Modeling–Metric 权威归属和 MetricVersion/Publication。PD-010 和 F-040 当前仍是提案，不能用本 V2 自动更改其状态。

## 12. 当前检查状态、下一轮产物与 DoD

| 项目 | 状态 |
|---|---|
| 全域 J1–J6、五组正式能力、横切平台与 MDM 的故事地图范围 | **DRAFT 已覆盖**；需要业务代表审核“是否正确、有无遗漏” |
| 哪些模块被真实浏览器逐页审阅 | **只有 #512 订单首次建设路径的局部**；其它“已设计/已有代码”不可推导 UI 正确 |
| 各域代码和 E2E 验证 | 本次**没有**全面代码审查/运行/部署 E2E；不声明全部能力可用 |
| PD-002 / PD-003 | ACCEPTED；实施状态和 E2E 另看正式证据 |
| PD-010 / F-040 | PROPOSED / DRAFT，不在本次自动批准 |
| 详细页面布局与 PR 实施范围 | 尚未在全域决议下定稿；F-040 十屏为首个垂直候选 |

**全域用户故事地图 V2 评审 DoD**：

- [ ] 至少一个业务代表、一个实施者、一个消费者、一个运行/治理代表可以指出自己的主要目标、入口与完成结果；
- [ ] J1～J6 和 MDM/横切覆盖 CAPABILITY_MAP，缺少独立用户目标的功能被标记待裁定；
- [ ] 每条旅程标出核心决策、Owner/Ref、真实成功证据、失败/恢复和跨域回链；
- [ ] 共享标准、逻辑/物理模型、Metric、Dataset/Service、Runtime、Security、Lineage/Usage 没有双重 Truth；
- [ ] 有一条建设者、消费者、运维者的完整“纸面走查”并记录阻断问题；
- [ ] V0→V7 切片按业务价值、风险和可验收性得到排序认可；首个 Slice 深设不排斥其它能力；
- [ ] #515/F-040 的交互稿与本地图一致，不一致处回到已接受 Product Truth/领域 Owner 裁定；
- [ ] **只允许在产品批准的具体切片**进入详细 UX、领域合同、实现/真实 E2E，不以地图草案替代开发授权。

**下一步正确动作**：与代表用户评审 V2 的“用户目标/故事/优先级”是否正确；随后基于获批队列推进对应用户旅程 UX。不可重复“先铺满页面再补业务逻辑”，也不可等所有模块逐页交互设计完才开始第一条已批准的垂直切片。