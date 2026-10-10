# data-ops 全产品用户故事地图 V2 · 产品评审纪要

Status: PRODUCT_SCOPE_PRIORITY = CONFIRMED_BY_REQUESTER（**规划范围与优先级已确认，详细产品/领域规则尚未批准**）  
Date: 2026-10-10  
Review targets: [V2 主地图](./dataops-global-user-story-map-v2.md)、[能力追踪与三角色纸面走查](./dataops-coverage-and-walkthrough-v2.md)  
Source of truth: [PRODUCT_VISION](../PRODUCT_VISION.md)、[PRODUCT_PRINCIPLES](../PRODUCT_PRINCIPLES.md)、[CAPABILITY_MAP](../CAPABILITY_MAP.md)、[USER_JOURNEYS](../USER_JOURNEYS.md)、[PD-002 ACCEPTED](../decisions/PD-002-governed-consumption-contract.md)、[PD-003 ACCEPTED](../decisions/PD-003-business-semantic-metric-contract.md)  
Historical evidence: [#512](https://github.com/gitfortian/data-ops/issues/512)；Planning parent: [#513](https://github.com/gitfortian/data-ops/issues/513)  
First detailed story: [#515 F-040](https://github.com/gitfortian/data-ops/pull/515)（Draft，不能代表全产品）

> **评审范围**：静态产品合同 + 两份 V2 地图的故事结构与相互一致性。本轮**没有**真实用户访谈/多角色产品签署、全仓库逐模块代码审计、线上业务数据读写、交互可用性实测或 E2E。评审意见只作为下一轮决策输入。

## 1. 核心结论

**有条件通过 STORY SCOPE（故事范围）与 OWNERSHIP PRINCIPLE（唯一事实归属）**：

- 六条主旅程 **J1 数据建设、J2 业务指标、J3 数据消费、J4 异常恢复、J5 安全消费、J6 可信 AI 问答** 应当保留为**相互引用但可以独立进入**的用户工作路径。它们不能合成要求每位用户从连接数据库开始的超级向导。
- 现行五组正式产品能力、MDM 专业解决方案与 Project/RBAC/Approval/Audit/Scheduler 等横切平台均已有故事目标。V2 从 **66 补齐至 74 条**候选故事，原有 Story ID 完全保留。
- 不同旅程可共享已存在的 Metadata、Process/StandardField、Logic/Physical Model、Task/Execution、Metric Version/Publication、Dataset/Service、Access/Usage 等正式引用；同一事实只有一个 Owner，新的全域地图本身不成为技术模块/数据表。
- [PD-002](../decisions/PD-002-governed-consumption-contract.md) 与 [PD-003](../decisions/PD-003-business-semantic-metric-contract.md) 的 ACCEPTED 规则不因本地图重提案而被覆盖；PD-010 仍 PROPOSED，F-040 仍 DRAFT。
- **不能宣布“所有模块用户体验已评审通过”**。深度真实走查只覆盖了 #512 中的订单建设局部；J3/J4/J5/J6、实时同步、Dashboard 和 MDM 尚需代表用户验证任务与成功标准。

## 2. 已在同一个 V2 PR 修复的八处 P0/P1 故事缺口

| 严重度 | 原问题 | 处理结果与新故事 |
|---|---|---|
| P0 | J3 写消费者发现/调用，没有 Producer 如何把可信生产结果定义、验证、发布为 Dataset/Service | **GM-J3-10** 补充正式来源合同生产；Consumption Hub 只做 governed projection |
| P0 | J1 数据接入→任务执行有，但缺真正的任务依赖编排/运行计划 | **GM-J1-14** 补调度、依赖、任务 Revision 与真实 Instance 区分 |
| P0 | 质量结果/故障处置很多，却缺可维护的规则定义与真正校验 | **GM-J1-15** 补规则/阈值/责任人/执行结果，Quality 维持 Owner |
| P1 | 资产纳管≠有权审核上架/下架，缺正式发布者故事 | **GM-J1-13** 补 Asset 生命周期决定与消费回链 |
| P1 | 标准字段创建≠其被使用后变更审查与影响确认 | **GM-J2-09** 补 Semantic 的标准/字段变更管理与版本影响 |
| P1 | 导出/查询与可持续看板/大屏属于不同业务结果 | **GM-J3-11** 补可重复分析/看板发布、刷新与真实合同 |
| P1 | 模型 TTL 展示不代表组织定义、审核或实际执行留存删除 | **GM-X-10** 补 Retention/Deletion 策略与落地证据 |
| P1 | 权限拒绝已有横切规则，但项目/成员第一入口与项目切换的操作成果缺失 | **GM-X-11** 补可信 Project Scope 与授权范围回读 |

这些只是**故事范围缺口**，不是要求立刻新增八个服务模块或八个 PR。现有领域能力的实际可复用程度仍需进入对应切片时核实。

## 3. 重叠故事的 Owner 裁决：同一事实不重复定义

| 易混淆的两个故事 | 必须分开的正式事实 | 体验处理 |
|---|---|---|
| J1-02 元数据发现 vs J1-03 资产纳管 | Metadata 采集快照 ≠ Asset 上架/责任 | Metadata 提供来源证据，Asset 提供治理状态/Owner |
| J1-09 执行 vs J1-14 工作流调度 | Task/Revision 配置 ≠ Schedule/Instance/Execution 回执 | 编排审阅、发布、运行要有不同确认/状态 |
| J1-11/15 质量 vs J4-01～06 事故恢复 | 质量规则、结果 ≠ 告警/恢复命令 | J4 消费真实质量结果，不能写第二套质量规则 |
| J1-05 标准创建 vs J2-09 标准演进 | 一个 Semantic 标准字段与其版本/引用影响 | 两入口指向同一 StandardField/Version，禁止影子字段 |
| J1-07 逻辑设计 vs J2-02 指标引用 | Logic Attribute/Entity ≠ Metric Definition | Metric 引用已确认模型/标准，不复制逻辑实体结构 |
| J3-10 Dataset/Service 生产 vs J3-01 消费发现 | 正式 Dataset/Service Source Truth ≠ Data Product View | 产品投影可以重建，不持有第二份可编辑 Dataset |
| J3-04 订阅 vs J3-08 实际 Usage | 声明依赖 ≠ 真实使用事件 | 分开显示，不能从 Subscribe 自动写 Usage |
| J4-02 影响 vs J3-08 消费影响 | Lineage 技术依赖、Subscription、Usage 三种关系 | Impact 是组合只读视图，不创造新 Truth |
| J3-03 Access vs J5-03/04 安全授权 | 访问状态投影 ≠ 审批记录 ≠ 实际访问裁决/执行 | 有受权的复用入口，消费 Query/Invoke 必须真实校验 |
| J3-07/11 Dashboard vs J6-05 AI Query | AI 查询、Dashboard 刷新只是多个正式消费渠道 | 均受 Dataset/Service/Metric 合同及 Security/Usage 约束 |
| 生命周期 X-09/10 vs 物理 Model TTL | 策略/审批 vs 物理执行/消费影响 | 各有权限与结果，不把设置字段值当实际销毁 |

**结论**：这些是有目的的**多入口同一事实**，不是需要删除的“重复功能”；决定是否合并具体页面要等各故事的实际 UI/Domain 证据。PD-002/PD-003 已接受的 Truth Owner 高于本文件的提案。

## 4. 覆盖与优先级评审

| 判据 | 审查结论 | 未满足部分 |
|---|---|---|
| 六主旅程/七组角色类型都有入口与产出 | **文本覆盖通过** | 仍需真实用户分别验证目标 |
| 对现有 Capability Map 的映射 | **范围覆盖通过** | 数据源文件/实时流、工作流、Analysis/Dashboard/MDM 仅浅层故事，不能称功能通过 |
| 来源→StandardField→逻辑/物理→DataDev→Metric→Dataset | **逻辑关系合理** | PD-010 非 ODS 标准门禁、逻辑→物理合同未获签署 |
| 数据消费者不经建模也能完成工作 | **设计原则正确** | Dataset/Service 真实消费 E2E 未验收 |
| OPS/SEC/AI 可独立开始任务 | **文本覆盖通过** | 告警修复、撤权安全、AI 证据问答尚无实机体验验证 |
| V0→V7 纵向价值切片 | **可以作为优先级讨论基线** | 尚没有商业收益、用户频率、风险/投入与跨团队依赖的真实测量 |
| 对旧页面保留/重构/删除的裁定 | **方法已定义** | 除 #512 涉及页面外，不能先裁决具体页面 |
| 真实 E2E | **NOT EXECUTED** | 需要后续每条切片真实数据/角色/权限/恢复证据 |

### 4.1 排序建议（仍待批准）

- **基础安全/权限/审计不是 V5 才开始**：GM-X-01～05 等控制点与第一条业务 Slice 同步验证。
- **V1 与 V2 贯通首个业务价值**：F-040 的 ODS/标准/逻辑/物理 + DataDev/指标/真实 Dataset 消费形成第一次可信数据成果；必须同时复用 PD-002 的原消费合同。
- **J3 已有正式 PD-002 与部分实现**：消费者访问与 E2E 可与首条构建切片并行形成验收，不必等物理模型全部交付后才规划。
- **J4/J5/J6 需独立价值评审**：故障恢复、敏感访问、AI 问答应由代表用户提供频率/风险/收益，再定研发次序；安全强制控制始终随所有 Slice 而非仅后期上线。
- **MDM 不自动启用新一级域**：产品形态、真实主人、支持哪些数据对象、是否有业务验收必须先 Product Decision。

不能因为“AI 很有吸引力”或“后台有几千行代码”而自动超越风险更高的运行/消费闭环。

## 5. 尚须 Product / Domain Owner 决议的真正问题

| 编号 | 问题 | 谁参与 | 需要的输出 |
|---|---|---|---|
| R-01 | 六旅程与 74 条故事是否真实反映各角色主要任务，是否有缺少的高频入口？ | Product + BIZ / BUILD / CONS / OPS / SEC 代表 | Scope ACCEPT / REVISE、缺口与高频任务 |
| R-02 | 首个价值切片是建设→消费，还是消费先闭环？优先级依据是什么？ | Product / 业务 Owner / Engineering | 一个明确的首条 Slice、下一条独立 Slice、衡量方式 |
| R-03 | PD-010 非 ODS 标准字段强制/逻辑版本等规则怎样与已 ACCEPTED PD-003 对齐？ | Product + Semantic + Modeling | PD-010 的正式决议/迁移边界 |
| R-04 | Dataset/Service 发布与模型/Task/Metric 的精确版本关联如何满足 PD-002？ | Consumption + Dataset/Service + Metric/Modeling | 不复制 Data Product Truth 的交接合同 |
| R-05 | 真实质量/安全/生命周期治理门禁何时阻断写入、发布或消费？ | Quality / Security / Asset / Platform | 规则 Owner、环境支持、负向 E2E |
| R-06 | Dashboard/大屏/AI 查询是不是只消费受治理数据，哪些专业表达式需要审批？ | Analysis + Consumption + Security | Access/Usage/Query 合同与人工确认点 |
| R-07 | MDM 是专业模块还是产品解决方案，是否有业务客户和数据 Owner？ | Product + MDM 专家 | 是否开启专项 Product Decision |
| R-08 | 旧页面保留/重构的产品 UX 验收怎样量化？ | Product + 真实用户 | 完成率、反复输入次数、阻断错误率、首次可信价值时间基线 |

**此文档无法替 Owner 填写这些决议**。若未确认，V2 依然是评审草案，不应把任何 Feature 改为 APPROVED，也不能因为全域地图扩大就直接实施新业务合同。

## 6. 本轮正式评审状态

- \`SCOPE_REVIEW=CONDITIONAL_PASS\`
- \`CAPABILITY_TRACEABILITY=PASS_AS_STORY_COVERAGE_ONLY\`
- \`DUPLICATE_TRUTH_REVIEW=PASS_WITH_OWNER_CONSTRAINTS\`
- \`STORY_COUNT=74\`（J1 15 / J2 9 / J3 11 / J4 7 / J5 7 / J6 8 / MDM 6 / Cross 11）
- \`REAL_USER_ROLE_REVIEW=PENDING\`
- \`PRODUCT_OWNER_APPROVAL=PENDING\`
- \`DOMAIN_CONTRACT_REVIEW=PENDING\`
- \`REAL_E2E=NOT_EXECUTED\`

下一步应由产品 Owner 组织全域范围/价值优先级评审，确认 R-01、R-02。之后才定稿对应深度切片，例如 #515 F-040；不必等其它所有页面 UX 都设计完才实现已正式批准的切片。

## 7. R-01/R-02 深度裁决建议（2026-10-10 第二轮审查）

### 7.1 R-01 · 角色、入口与 0→1 智能协作战略的一致性

**审查结论：\`SCOPE_RECOMMEND_ACCEPT_WITH_ONE_ADDITION\`，用户角色签署仍 PENDING。**

原 V2 的 J1～J6 与 MDM、横切产品覆盖基本符合已接受能力地图，但长期思想指引 [#509](https://github.com/gitfortian/data-ops/issues/509) 将**低专业门槛的 0→1 数据建设与 Agent 辅助编排**作为产品的差异化方向；原 V2 把 AI 主要放在 J6“可信问答”与通用 X-06 的“建议校验”，未足够表达**从业务目标组织端到端专业建设**的独立用户成果。该长期思想仍是 STRATEGIC PROPOSAL，不能冒充已接受的产品决策。

因此**新增 GM-J1-16**：
> 业务实施者从业务目标/授权数据出发，Agent 提供来源/标准/逻辑/物理/开发的证据驱动建议、待确认业务问题、可审阅执行计划和专业接管；只有原领域 API 在独立授权/发布/执行确认后写入正式事实，最终有真实模型/任务/消费回执。Agent 不建立第二套 Truth 或 Runtime。

**J1 Agent 辅助建设 ≠ J6 智能消费问答**；后者从已有受治理指标/Dataset 开始，用授权查询回答问题。**不能把首条 AI 协作建设需求全部延迟到 V6**，但同样不能把“AI 可以完全自动建任意数仓”当作第一个 Slice 的完成标准。首期可从有证据的“解释下一步/标准字段候选/人工确认/专业交接”开始，逐阶段验证可用能力。

#### 不同角色的「入口→成果→误区」复核

| 真实角色 | 能独立进入的入口 | 不可省略的成果 | 地图覆盖/特别风险 |
|---|---|---|---|
| 业务负责人（BIZ） | 业务问题/过程、现有指标 | 确认业务事件、统计范围与口径，并实际拿到合法结果 | J1-04/J2；不能被迫写 SQL 或默认回答 SUM |
| 兼任建设的实施者（BUILD） | 业务目标或已授权数据源 | 有证据的计划、ODS/标准/逻辑/物理与运行交接，可切换专家模式 | J1-01～16；J1-16 当前**只是故事**，AI 执行成熟度需工程核实 |
| 专业数据工程师（DEV） | DataDev/Workflow/Sync | 真实 Task Revision、执行、调度和安全恢复 | J1-09/14、J4；不能以模型发布冒充任务部署 |
| 治理者（STEWARD） | Standard/Asset/Quality/Impact | 合法标准、上架/质量/版本影响与责任 | J1-05/13/15、J2-09、X-10 |
| 独立数据消费者（CONS） | Consumption Hub、Dataset/Service、Dashboard | 权限内 Query/Invoke/分析及真实消费证据 | J3，**无需建模权限**；PD-002 Implementation PARTIAL |
| 运维负责人（OPS） | Alert/Instance/质量异常 | 确切故障影响、受权补数/恢复和用户通知 | J4；血缘/订阅/实际 Usage 不可混成单一人数 |
| 安全/审批负责人（SEC） | 敏感分类、策略/授权申请 | 实际访问/脱敏裁决、撤销与审计 | J5 + X；策略 UI 存在≠真正拒绝生效 |
| AI 业务问答用户（AI-USER） | 业务问题/数据分析 | 授权数据查询与可验证解释，可拒答 | J6；不能拥有自由无界数据读取 |
| MDM 负责人（专业领域） | 专项主数据来源/匹配审批 | 可信黄金记录、来源追溯和受权分发 | MDM 6 故事；具体产品形态/真实客户 PENDING |

**结论：现版本 75 条候选故事（J1 16、J2 9、J3 11、J4 7、J5 7、J6 8、MDM 6、横切 11），其中 1 条为本轮新增。** 角色与目标有合理覆盖，但没有真实业务用户/运维/安全代表的确认，不能声称正式范围审批完成。

### 7.2 R-02 · 按依赖和真实证据排序，而不是凭感觉给“功能模块排名”

优先级建议只做**定性门禁分析**：用户价值（是否第一次真实交付/可用）、现有证据（是否已有人实际使用）、复用已有实现/正式契约、合规与风险、是否阻断后续价值。**尚无用户频率、商业收益/人天数据，不给伪精确权重分数、成本估算或交付日期。**

| 工作轨 | 推荐优先级 | 用户结果/范围 | 前置依赖/暂停条件 | 当前事实依据 |
|---|---|---|---|---|
| **G0 产品范围与第一金样冻结** | **立即（规划门禁）** | 评审 J1～J6 + Agent 两种任务、确定“每日下单订单量”只是试点候选，明确口径确认人 | R-01/R-02 Product Owner 确认前不可升级 PD-010/F-040 | 本 V2 及 #509 |
| **G1 消费基础真实验收** | **最高，优先处理既有缺口** | 受限角色合法访问已有 Dataset/Service、真实 Query/Invoke、Source Audit + Usage Evidence，可重现错误/恢复 | 授权环境、部署 commit、Golden 双 Project/账号/数据产品、QA/Product 签署；证据缺失时保持 BLOCKED，不能写 E2E_PASS | [PD-002 Implementation PARTIAL](../decisions/PD-002-governed-consumption-contract.md)、[#336](https://github.com/gitfortian/data-ops/issues/336) |
| **G2 首次可信数据成果（F-040 Slice A+B 一条纵向目标）** | **最高，与 G1 契约验收可并行** | 有证据的业务目标/来源 → 标准→逻辑→物理→正式 DataDev 运行→质量/指标→真实消费；人能确认、AI 可建议并可接管 | PD-010 的强制标准字段及逻辑/物理映射待批准；ModelVersion→DataDev 精确版本；业务口径明确；必须复用 G1 的消费事实 | #512 静态/实机缺口、#515 DRAFT、#509 战略 |
| **G3 消费者自主体验与生产者交接** | **高，和 G1/G2 部分共享** | Consumption Hub 查找、评估、申请访问、订阅、Dataset/Service/看板可用；Producer 发布 source contract | 禁止另建 Data Product Truth；Access/Usage/Lineage 分开，首期可从已有对象开始 | PD-002 ACCEPTED/PARTIAL |
| **G4 运营/安全专项深化** | **按事故与风险证据排序** | 实际告警定位→受控重跑→质量恢复，敏感分类→策略→撤权验证 | 基础权限/审计**从 G1/G2 就是硬要求**；专项深入需要真实 OPS/SEC 高频路径和批准方 | J4/J5 专题尚未实机走查 |
| **G5 可信 AI 问答（J6）** | **在有可信数据和授权消费后扩展** | AI-USER 根据已发布 Metric/Dataset 回答业务问题，证据可复核；与 G2 的 AI 建设不同 | 不自行运行自由 SQL、不能绕过查询/访问许可；先有 G1 消费成功证据 | J6 / PD-002 |
| **G6 专项与复杂场景** | **条件触发** | MDM 黄金记录、复杂 CDC/流式/文件、跨源专业方案 | 必须存在真实业务 owner、目标和收益；产品形态需 Decision | Capability Map 存在，不代表代码/交互可用 |

**G1 + G2 的关系**：G1 首先验证已存在的可消费对象，不必等 G2 新建订单 DWD；G2 后续重用该受治理路径完成首次完整业务结果。若没有足够人员并行，按依赖先收集 G1 真实消费证据，再让 G2 集中实施；**不要用“消费最终会做”来跳过真实 E2E**。

**G2 内部里程碑**：V1/Slice A 的来源/标准/逻辑/物理设计可回读，只是进度里程碑；只有 V2/Slice B 的真实任务执行、质量、指标与一次合法消费才是第一次业务价值。AI 可以先做可解释建议/专业接管，不强制完全自动化是首期出关条件，但“缺乏数据工程师的用户能否自己完成”必须是可观测验收目标。

**G4 的注意**：核心跨项目 RBAC、敏感访问判定、审计、错误/恢复处理属于 G1/G2 同期基础控制；“安全专项在 G4”仅指专业 UX 和高级策略的深入，不表示早期允许无访问门禁。

### 7.3 可落地的产品评审结论（建议值）

| 决策 | 建议决议 | 为什么仍不自动签署 |
|---|---|---|
| R-01 故事范围 | **RECOMMEND_ACCEPT_WITH_AGENT_STORY**；确认 75 条为全域候选基线、六 Journey + MDM/横切，允许后续切片按实际需求微调 | 没有真实角色用户评审，#509 是战略提案 |
| R-02 交付次序 | **RECOMMEND_G1_CONSUMPTION_E2E + G2_FIRST_VALUE_PARALLEL_BY_DEPENDENCY**；G0 范围冻结先行，G3/G4/G5/G6 按证据 | 资源容量、业务收益、真实用户频率未核实 |
| 设计 PR #515 | **保留并以全图校准**，不推倒 F-040 十屏；需明确 AI J1 与正式 DataDev/Consumption 交接范围 | PD-010 PROPOSED、F-040 DRAFT |
| 业务代码 | **NOT_AUTHORIZED**，只有对应 Product Decision/Feature 获批准后才启动已确定的交付切片 | 产品审批与领域合同/真实 E2E 仍未完成 |

### 7.4 一次性评审所需的最小签署材料（不制造无休止的规划）

下一次 Product Owner 可以只确认两个**产品层面问题**：是否将 75 条候选故事与六条 Journey 作为规划基线（无需逐一冻结 UI）；是否认可 G1 消费真实验收 + G2 首次业务价值的依赖驱动首批优先级。必要领域例外（非 ODS 技术列、复杂实体关系、MDM 产品形态）**留给各自 PD/Feature 明确决策**，不再阻断全域地图范围的基线确认。

在得到 Owner 的明确确认前，记录 \`R01_SCOPE_RECOMMEND_ACCEPT\`、\`R02_PRIORITY_RECOMMEND_ACCEPT\`、\`PRODUCT_OWNER_SIGNOFF=PENDING\`、\`REAL_USERS_VALIDATED=NO\`、\`E2E=BLOCKED_EVIDENCE/NOT_EXECUTED\`。

**整个 V2 仍为 DRAFT 评审材料，PR #516 的合并不代表 Product Truth 或业务上线。**


## 8. R-01/R-02 需求提出者确认及评审出口（2026-10-10）

本条为前述 1–7 节“建议/待确认”之后的**最新范围决议**。需求提出者审阅了两项明确的规划问题并回复“好的，同意，继续”。根据该授权，记录：

- **R-01 = CONFIRMED_BY_REQUESTER**：认可 75 条用户故事与 J1–J6、MDM/横切作为**全域候选规划基线**；编号和领域归属有持续复核机制，不锁死未来用户反馈。
- **R-02 = CONFIRMED_BY_REQUESTER**：认可 G1 当前 PD-002 的真实消费环境/权限/Usage E2E 证据优先收口，以及 G2 F-040 从来源/业务目标到真实运行/指标/合法查询的首次业务价值交付，按依赖可并行；J1 Agent 辅助建设从首批规划。
- **全域 V0 规划工作 = EXIT_READY_FOR_PR_REVIEW**：不再要求把每个菜单的详细交互画完才可批准本规划稿。后续变更仍应附 Story ID 和理由。

### 不可扩张解释的边界

该决议不是 Product Decision PD-010 ACCEPTED，不是 Feature F-040 APPROVED，不是 PD-002 的实际 Golden E2E PASS，也不是跨域治理/技术列例外和 Agent 自主写入的授权。此前关于缺真实多角色走查的事实继续成立。详细规则仍需 Semantic / Modeling / DataDev / Security/Consumption 的 Domain Contract Review；G1 真实验收受 #336 当前证据/环境前提制约。

### 进入下一阶段时的最小交付

1. **#516 全域地图 PR 准备正常评审/合并**：仅文档范围基线，不影响生产行为。
2. **#515 F-040 实施准备**：把 J1 的 Agent 证据候选/人工确认/专业接管与 G1 既有受治理消费对接落实为可测试的第一条纵向 Slice，冻结 Stage A 设计里程碑与 Stage B 真消费业务价值门槛。
3. **PD-010 的未决业务选择**：非 ODS 全列标准字段及技术列例外、逻辑版本/映射、模型→DataDev 版本交接，需要显式批准；不借 R-01/R-02 的总方向代替审批。
4. **#336 消费验收**：只使用可信授权环境+实际部署与真实 Provider/审计证据，由具备权限的 QA/Release 运行已有 runner；未取得则保持 BLOCKED_EVIDENCE，不重复创造消费系统或伪造测试结果。

\`GLOBAL_STORY_SCOPE=BASELINED_BY_REQUESTER\`；\`PR516_IMPLEMENTATION=DOCS_ONLY\`；\`PD010=PROPOSED\`；\`F040=DRAFT\`；\`G1_E2E=BLOCKED_EVIDENCE\`；\`G2_E2E=NOT_EXECUTED\`。
