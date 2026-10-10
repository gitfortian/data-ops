# data-ops V2 用户故事覆盖矩阵与三类角色纸面走查

Status: DRAFT · Review Aid（不等于代码验收/运行 E2E）  
Date: 2026-10-10  
Main: [全产品用户故事地图 V2](./dataops-global-user-story-map-v2.md)  
Evidence: [#512 首次建设巡检](https://github.com/gitfortian/data-ops/issues/512) / [#513 故事地图](https://github.com/gitfortian/data-ops/issues/513)

> **如何判断覆盖**：能力出现在正式 [CAPABILITY_MAP](../CAPABILITY_MAP.md) ≠ 有可操作页面；有 Controller ≠ 业务闭环；有 UI ≠ E2E 已通过。本矩阵的“证据深度”只描述**本轮已掌握的证据**，不充当代码完备度评级。**已实机观察**仅适用于 #512 的订单样例部分路径；其他能力未经全面代码/浏览器验证，均要等具体故事评审再定“保留/优化/重构/重设计”。

## 1. 全产品能力覆盖：每一项能回答“谁为了什么结果使用”

故事卡覆盖列使用简写 `J1-01` 等，正式稳定 ID 为 `GM-J1-01`；`X-10` 对应 `GM-X-10`。一行可引用多个故事，不能因“有覆盖”推断其已经运行验收。

证据标签：**E** = #512 订单实机走查涉及，**C** = 现行产品合同/能力图规划（不表示代码可用），**U** = 当前未做专门产品实机/代码深审，**P** = 仍需正式产品决策。多标签可以并存。

| 能力（按正式 Capability Map） | 故事卡覆盖 | 主角色 / 用户结果 | 交接对象与成功证据 | 证据深度 |
|---|---|---|---|---|
| 数据源 | J1-01、J1-02、J5-07 | BUILD 建立有权限的来源 | datasourceId / connection verdict | E + C |
| 文件资源 | J1-01、J1-09 | BUILD 引入文件并保留版本/格式/权限 | file/source identity / ingest result | C + U |
| 离线同步 | J1-09、J1-14、J4-05 | DEV 安全迁移和重跑批次 | task/run/partition offset | C + U |
| 实时同步 | J1-10、J4-01、J4-05 | DEV 维持流式数据准确性 | checkpoint/lag/replay evidence | C + U |
| Metadata Harvest | J1-02、J1-03、J1-06 | BUILD 知道具体发现了什么 | schema snapshot/fingerprint | E + C |
| 业务域 / 业务过程 | J1-04、J2-01、J2-02 | BIZ 正确描述业务事件/粒度 | ProcessRef / user confirmation | E + C |
| 数据标准 | J1-05、J2-02、J2-09、J5-01 | STEWARD 管理启用的治理标准 | StandardRef / version / validity | E + C |
| 标准字段 / 过程引用 | J1-05、J1-07、J2-02 | STEWARD 将标准业务属性交给建模复用 | stdFieldId / ProcessBinding | E + C |
| 数仓分层 | J1-06、J1-08 | BUILD 区分贴源与标准化落地 | layer policy / physical design | E + C |
| 逻辑模型 | J1-07、J1-08、J2-02 | BIZ/BUILD 设计独立于方言的业务对象 | LogicalVersionRef / entity/attribute/relation | C + U，骨架代码审查见 #513 |
| 物理模型 | J1-06、J1-08、J1-12 | BUILD 形成准确设计和版本 | ModelVersionRef / mappings | E + C |
| 指标 | J2-01～J2-08、J6-02 | BIZ 得到可验证业务口径 | MetricVersion/Validation/Publication | C + U，PD-003 |
| 数据开发 | J1-09、J1-14、J4-04、J4-05 | DEV 编辑与运行真实加工任务 | Task Draft/Revision/Execution | E（仅首页）+ C |
| 任务发布版本 | J1-09、J1-14、J4-04、J4-05 | DEV 版本一致、部署/执行可解释 | Task Revision + execution distinction | C + U |
| 工作流编排 | J1-09、J1-14、J4-02、J4-05 | DEV 将依赖任务有序执行 | workflow/version/instances | C + U |
| 调度 | J1-09、J1-14、J4-01、J4-05 | OPS 按预期触发数据生产 | schedule / actual runs | C + U |
| 实例运维 | J4-01～J4-06 | OPS 发现、诊断和恢复故障 | run/instance/log/recovery | C + U |
| 补数 / 重跑 | J4-04、J4-05 | OPS 恢复特定范围且不重写正确数据 | backfill IDs / data validation | C + U |
| Asset 目录与资产详情 | J1-03、J1-13、J1-11、J3-02、J4-02 | STEWARD/CONS 找到可信数据对象与责任人 | AssetRef/governance/owner | E + C |
| Metadata Catalog / Reconciliation | J1-02、J4-07 | BUILD 识别结构变化和影响 | source snapshot / drift | E + C |
| Lineage | J1-11、J4-02、J4-07、J3-08 | OPS/STEWARD 查真实来源/变更影响 | typed edge + evidence/version | E（CONTAINS 限制）+ C |
| 数据质量 | J1-11、J1-15、J2-05、J4-01、J4-06 | STEWARD 保证数据质量影响决策 | Quality Result / scope / failures | C + U |
| 数据安全 | J5-01～J5-07、J3-03 | SEC 证明策略真正生效 | policy/access decision/audit | C + U |
| 生命周期 | J1-11、X-09、X-10、J4-07 | STEWARD 正确留存、删除或下发 TTL | apply/dispatch result | E（模型未配置）+ C |
| 使用 / 影响分析 | J3-08、J4-02、J4-07 | PRODUCER/OPS 知道真实消费者与影响 | Usage vs Subscription vs Lineage | C + U |
| Consumption Hub / 产品发现 | J3-01～J3-03 | CONS 找到可用数据产品 | governed Product View | C + U，PD-002 |
| Data Product View | J3-01、J3-02、J3-08 | CONS 理解治理投影与来源合同 | stable product/source key | C + U，PD-002 |
| Dataset | J3-02、J3-05、J3-10、J6-05 | CONS 合法查询结构化结果 | dataset contract/query evidence | C + U，PD-002 |
| Data Service | J3-02、J3-06、J3-10、J6-05 | 应用用户合法调用正式接口 | service contract/invoke evidence | C + U，PD-002 |
| Access Projection | J3-03、J5-03～J5-05 | CONS/SEC 获得真实授权裁决 | Access Decision | C + U |
| Subscription / Usage Evidence | J3-04、J3-08、J4-06 | PRODUCER 知道声明依赖与真实消费 | subscription vs observed event | C + U |
| Analysis | J3-07、J3-11、J6-06 | CONS 分析已有授权数据 | analysis result with refs | C + U |
| Dashboard | J3-07、J3-11、J6-06 | CONS 反复查看可靠业务结论 | published view / precise data contract | C + U |
| Digital Screen（大屏） | J3-07、J3-11 | CONS 长时间展示可用指标与状态 | governed runtime/refresh evidence | C + U |
| Agent / AI 协助首次建设 | J1-16、J1-04～J1-09、X-06 | BUILD 以业务目标组织有证据的跨域建设建议，正式操作由各域执行 | goal/plan + confirmed refs + owner-domain receipts | C + U；#509 战略待确认 |
| Agent / AI 可信问答 | J6-01～J6-08、X-06 | AI-USER 获得可信回答与建议 | query/evidence/access/confirmation | C + U |
| Export / Downstream | J3-07、J5-04、J5-05 | CONS 合法导出/供下游系统用 | export result / permission audit | C + U |
| MDM | MDM-01～MDM-06 | MDM STEWARD 实现主数据一致性与分发 | versioned golden record + distribution | C + U + P |
| Project Space | X-01、X-11、J5-07 | 所有角色不会跨项目串读/串写 | trusted scope, negative permission proof | C + U |
| RBAC | X-01、J3-03、J5-03～J5-07 | 所有角色仅执行授权能力 | explicit deny/allow + audit | C + U |
| Approval Engine | X-02、X-03、J5-03、MDM-04 | SEC/STEWARD 对风险操作作正式决定 | approved/rejected decision | C + U |
| Audit | X-02、X-03、J5-06 | 责任人能回溯谁何时修改/访问 | operation/access events | C + U |
| Alert / Notification | J4-01、J4-06、X-07 | OPS/BIZ 实际接收重要变化 | delivery/reaction/evidence | C + U |
| Task Runtime | J1-09、J4-05、J6-05 | DEV/OPS 获得真实运行反馈 | execution IDs and logs | C + U |
| Plugin / SPI | X-08、J1-01、J6-05 | BUILD/CONS 知道支持的能力/缺失 | available/unavailable/not supported | C + U |
| Storage | J1-09、J1-11、X-09 | DEV/STEWARD 确保物理存储可靠 | deployed/retained/expired result | C + U |
| Scheduler | J1-09、J1-14、J4-01、J4-05 | OPS 确认按时触发和失败补偿 | schedule vs actual execution | C + U |

**矩阵输出判读**：每个正式能力均有至少一组用户目标/结果和责任角色；但大量能力还缺**独立用户实机走查与运行证据**。因此不能用本覆盖表给出“模块实现通过率”“所有功能已开发”的结论。后续按价值切片获取真实证据，而不是在规划阶段把所有菜单一次点完。

### 用户故事补充（原 66 → 74 → 75 条）

本轮核对发现**有产品能力但缺独立用户工作结果**的八条故事，已追加到 V2 主地图，保留原有 ID 不重新编号：

| 补全故事 | 为什么原故事不够 | 验收对应事实 |
|---|---|---|
| GM-J1-13 资产上架/下架 | 资产自动纳管 ≠ 治理者正式审核上架或下架 | Asset 状态、负责人、审查证据 |
| GM-J1-14 任务编排/调度 | 执行过单个 SQL/同步 ≠ 生产依赖、发布调度可运行 | Workflow/Task Revision、Schedule、实际 Run 区别 |
| GM-J1-15 质量规则定义与校验 | 质量结果/故障告警 ≠ 有规则、阈值、责任和真实执行 | Quality Rule、Check/Result、动作策略 |
| GM-J2-09 标准变更治理 | 创建标准字段 ≠ 被多模型使用后的变更审批与版本影响 | Semantic 正式标准版本与影响 |
| GM-J3-10 Dataset/Service 生产者 | 找得到数据产品 ≠ 有人从可信结果创建并发布来源合同 | Dataset/Service 正式 owning contract；Hub 仅投影 |
| GM-J3-11 分析与可持续看板 | 一次导出/Query ≠ 可重复、可授权刷新 Dashboard/大屏 | 分析/看板发布及刷新/权限证据 |
| GM-X-10 留存生命周期制定 | 显示 TTL 设置 ≠ 治理者定义、批准、执行并验证删除策略 | Policy / Apply / Disposal Evidence |
| GM-X-11 项目加入与切换 | RBAC 拦截 ≠ 用户知道自己在哪个项目、能做什么 | Project Scope/成员身份/拒绝回执 |

这八条是完整业务工作，不是逐按钮/逐 Controller 拆分需求。产品策略、服务端契约和运行结果仍需具体用户故事评审，不能认为“补图=已开发”。

**R-01 / R-02 再补 1 条 GM-J1-16（AI 协作首次建设）**：让专业能力有限的用户从业务目标开始，取得证据支持的跨域方案、业务澄清、专家接管与各领域实际回执；它与 J6 的可信 AI 问答不同。来源于 #509 的长期战略**提案**，不代表现有 AI 编排已完成。

## 2. 三个“纸面用户故事”用于检验跨模块断点

> **Paper Walkthrough，不是 E2E PASS。** 每个环节标识用户动作、权威对象和失败路径，以便后续用真实环境复现。

### W-A 建设者第一次把订单数据变成可用业务结果

**人物**：兼任数据实施者 BUILD + 业务口径确认者 BIZ。  
**发起目标**：“从 MySQL 订单库看到每日下单订单量”。  
**入口可以是** DataSource/已发现 \`trade_order\`，也可以是业务问题。  
**关键过程**：
1. 查看当前有权限的 MySQL 来源、\`trade_db.trade_order\` 和真实元数据快照；若已有 ODS 设计先复用而非重复创建。
2. 创建/核对 ODS 贴源结构，正式保存模型级来源身份、逐列 Mapping、技术列依据；保留订单时间/金额的源端脏值类型。
3. 确认「交易/下单」业务过程与订单头/商品明细的不同粒度，检查业务标准与标准字段库；区分“有标准”与“有正式字段”。
4. 用户可以选择 Agent 给出基于当前授权事实的标准字段/业务粒度/逻辑模型候选与待澄清问题，逐步采纳或切换专业工作台；Agent 不独立创建第二份标准或直接运行生产任务。
5. 从 Semantic 标准字段引用设计逻辑订单实体/属性/关系/版本；不自动推断客户↔订单基数、去重业务键。
6. 从逻辑版本预览可复用或新建的 DWD 物理模型，按非 ODS 标准字段强制规则（**待 PD-010 批准具体门禁**）校验；发布模型设计不代表 Doris 已建库。
7. 将精确设计版本有权限地交接现有 DataDev Task/Revision/Execution；明确目标 Doris 实例、转换规则、增量/重跑策略；取得真实执行结果。
8. BIZ 确认“下单量”时区、取消单是否计数、订单去重规则；Metric 验证/发布，Dataset 通过合法 Access 返回实际查询和 Usage Evidence。

**可能失败**：采集 17 列而 ODS 设计 19 列不一致未解释；标准字段库为空；订单状态跨年码值不一致；VARCHAR 日期/金额非法值；Doris 实例未配置；标准/映射后来变化；用户仅看到设计 PUBLISHED 却以为数仓完成。

**最终验收**：不仅有模型 ID，还要有模型来源/标准/逻辑版本回读、真实 DataDev Execution、指标验证/版本以及 Dataset 真正查询与访问/使用证据。#512 实机目前只走到 ODS **设计**发布和 DataDev 首屏，故这条仍**没有 E2E PASS**。

### W-B 业务消费者直接发现并使用数据（不会因为没有建模权限被阻断）

**人物**：业务分析师 CONS；可能没有 Modeling/DataDev 权限。  
**发起目标**：“查找上周订单量和订单明细，并把结果用于 Dashboard”。  
**入口**：Consumption Hub 或 Dataset/Service 正式详情，**不是**模型工作台。  
**关键过程**：
1. 搜索业务词/指标，看到真实权限可见的 Product View、其数据来源、Owner、更新与质量状态。
2. 区分提供的是 Dataset schema contract、Data Service API contract 还是 Published Metric reference；不把三者作为同一可编辑对象。
3. 请求/获取真实访问裁决，必要时审批，能看到权限拒绝而不误以为“数据不存在”。
4. Dataset Query/Preview 或 Service Invoke 获取真实结果，查询失败时区分 EMPTY、FORBIDDEN、UNAVAILABLE、STALE。
5. 若允许，订阅、导出、交给 Dashboard/分析；消费端记录实际使用，声明订阅单独保存。
6. 生产端变更后，消费者可看影响提示并回到原 Dataset/Service、Metric 精确版本，选择升级/保留/申请支持。

**可能失败**：Product View 因权限返回空、Service 提供方不可用、数据过期、Dashboard 私建一套绕过访问合同的 SQL、订阅被误显示为真实 API 调用。

**最终验收**：真实消费者角色不需要懂 Doris 就能确认合同、完成授权调用，有明确 Usage Evidence 与回链。当前仅有 PD-002 等产品合同与部分实施证据，**本地图没有运行本场景**。

### W-C 运维人员从告警定位影响并完成数据恢复

**人物**：OPS / 数据开发工程师 DEV / STEWARD。  
**发起目标**：“夜间订单 DWD 任务失败，下游 Dataset 可能变旧”。  
**入口**：Alert / 运行实例 / 质量失败通知。  
**关键过程**：
1. 告警指向真实 Task/Execution/时间窗口、错误与项目范围，不让用户从空白工作台自行找所有任务。
2. 读取当次精确任务 Revision、已发布模型版本、源表版本和失败位点；判断是读取权限、SQL、目标连接、来源漂移、质量/规则不通过中的哪一种。
3. 用运行血缘和已登记的 Metric/Dataset Subscription、Usage Evidence 分别识别技术下游和实际消费影响（不可混成单一计数）。
4. 受授权修复配置或数据，必要时审批；只对受影响分区/任务做补数、重跑、回滚，防止重复金额/订单行。
5. 重新检查质量、消费查询和新鲜度，通知已知负责人/订阅者；记录恢复证据与未恢复风险。

**可能失败**：Lineage 仅显示 MODEL CONTAINS 自有列却被当成真实下游；DataDev Run 运行了未保存编辑内容；重跑重复写入；Provider UNAVAILABLE 被显示为“无消费者”；任务恢复但指标质量仍失败。

**最终验收**：从实际告警直达根因/影响、获得正式审批/执行/回滚记录，Data Quality 和真实查询验证恢复，受影响消费者可定位并收到通知。**目前未做运行级用户走查**。

## 3. 反向 E2E 检查统一矩阵（每条后续旅程都必须适用）

| 负向用例 | 不应发生 | 正确反馈 / 可恢复性 |
|---|---|---|
| 未授权/跨项目 | 通过伪造 ID 打开标准、源表、任务或 Dataset | 服务端拒绝，不泄露私有详情 |
| Provider 不可用 | 显示“0 条数据/没有消费” | UNAVAILABLE、重试与相关对象回链 |
| 部分导入/采集失败 | 显示 100% 完成和正式血缘 | partial/unknown coverage、具体列错误 |
| ODS 来源字段变更 | 无提示覆盖已治理列与历史映射 | 指纹比对/差异预览/冲突审阅 |
| 非 ODS 无合法标准字段 | 以 stdTypeId 或模型发布成功冒充符合标准 | 依据已批准政策在后端拒绝正式生效 |
| 发布设计但无目标库 | 误显示已落地、已同步、可消费 | “模型设计已发布；部署/运行未知或未完成” |
| Metric 版本/口径变更 | 消费者自动跟随新的可变口径 | 显示精确版本、影响、受权升级 |
| 下游访问被拒绝 | 误显示 EMPTY 或悄悄执行查询 | FORBIDDEN，明确申请/批准入口 |
| 运行失败或重跑 | 没有真实 execution 也显示成功、或重复写入 | 真实执行/重试 ID、幂等及质量验证 |
| AI 无证据回答 | 自创 SQL/业务口径/标准、给出确定数字 | 追问/拒答/权限/质量说明，可引用正式证据 |
| 敏感字段撤权 | 旧缓存仍可非法导出 | 真正 Query/Invoke/Export 路径拒绝/审计 |

## 4. 产品交互后续评审与存量页面裁定方法

按“**故事发生在哪个时刻、应该有什么下一步**”评估页面，不是按菜单栏顺序打分：

- **保留**：已经帮助用户完成真实故事，且有保存/执行/访问证据和可回链；
- **细节调整**：故事闭环正确，问题在文案、默认值、展示优先级/空态；
- **流程重构**：业务模型归属正确，但需要跨模块重复选择、缺来源版本、保存不一致；
- **重新设计**：核心用户目标不一致，如用物理表技术面板来完成业务逻辑建模或让消费者先开发 SQL；
- **合并/弱化**：重复 Truth、空占位、过度公开内部 Runtime 机制；
- **未决定**：缺少真实用户任务或所有权证据，先做产品核查再决定保留/删除。

每条页面裁定至少引用故事 ID、当前证据与代码、拟变更业务合同、风险/兼容与真实 E2E；不能因为旧页面由低级模型生成就不经评审一概重写。

## 5. 下一次产品评审应首先回答

1. 是否还有正式能力地图遗漏的高价值角色/故事？如果有，补角色和成功结果，别先加菜单；
2. 选哪三个代表用户现场复述 W-A、W-B、W-C，谁有权确认指标口径、访问策略和恢复计划？
3. 六条 Journey 的 Owner/Ref 是否符合已接受 PD-002/PD-003，是否出现两套标准、指标、数据产品/运行状态？
4. V0～V7 应按真实业务收益与交付风险怎样排序，哪些横切安全/权限/审计必须和首 Slice 同时完成？
5. F-040/PD-010 与全域故事地图是否相容；能否先批准首条完整切片，而不等待其它模块全部 UI 重设计？

**R-02 现有消费验收风险**：[#336](https://github.com/gitfortian/data-ops/issues/336) 与 [PD-002](../decisions/PD-002-governed-consumption-contract.md) 显示消费合同 Implementation=PARTIAL，当前真实环境的受限角色/跨项目 Dataset Query、Service Invoke、精确 Usage/Source Audit、错误/恢复等尚未签收。F-040 金样从模型发布继续到业务消费之前，建议**优先复用并验证现有 PD-002 消费链路**，不开发第二套消费平台。

**本矩阵是全域覆盖及走查计划，不是功能开发/验收记录。**