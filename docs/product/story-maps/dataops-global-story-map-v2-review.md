# data-ops 全产品用户故事地图 V2 · 产品评审纪要

Status: PRODUCT_DESIGN_REVIEW = CONDITIONAL_PASS（**有条件通过，不是 Product Owner 正式批准**）  
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