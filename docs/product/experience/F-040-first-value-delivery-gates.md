# F-040 首次可信数据交付｜两条工作轨和实施准入

Status: REVIEW_READY（仍非开发授权）  
Date: 2026-10-10  
Depends on: [全域故事地图 #513](https://github.com/gitfortian/data-ops/issues/513)、[规划 PR #516](https://github.com/gitfortian/data-ops/pull/516)、[F-040](../features/F-040-data-construction-experience.md)、[PD-010](../decisions/PD-010-guided-business-to-data-journey.md)  
Evidence: [订单走查 #512](https://github.com/gitfortian/data-ops/issues/512)、[消费真实验收 #336](https://github.com/gitfortian/data-ops/issues/336)、[PD-002](../decisions/PD-002-governed-consumption-contract.md)  
UX: [十屏 V2](./F-040-screen-contracts-v2.md)、[领域合同 V3](./F-040-contract-resolution-proposal.md)

> 用户已明确同意 R-01「75 条候选故事、六 Journey 的全产品规划基线」和 R-02「G1 已有受治理消费验收 + G2 首次可信业务结果，按依赖优先推进，J1 AI 协作从首批开始设计」。这不构成 PD-010 或 F-040 业务规则的正式审批。本文不是实际代码、DDL、测试结果或生产数据改动。

## 1. 从模块实施转为两条真实用户价值工作轨

| 工作轨 | 用户要得到的成果 | 为什么先做 | 真实出关证据 |
| --- | --- | --- | --- |
| **G1 · 既有消费真验收** | 消费者通过已有 Dataset/Service 成功、合法地查询和调用 | PD-002 虽 ACCEPTED，Implementation 仍 PARTIAL；#336 的双 Project/受限角色 Query/Invoke、Usage/Source Audit 与故障恢复尚无有效当前环境签收 | 授权部署 commit、当前登录/权限、真实 Query/Invoke、Source Audit、Usage Evidence、失败/恢复，由 QA/Product/Release 签收 |
| **G2 · 第一次可信业务结果** | 无需全栈数据专家，用户从业务目标或 MySQL 订单库得到已确认口径、真实运行、质量合格且可消费的每日下单订单量 | #512 只有 ODS 设计发布和 DataDev 首页走查；真正的业务桥梁、任务和受治理消费仍断开 | 可回读 Source→StandardField→LogicalVersion→PhysicalVersion→Task Execution→MetricVersion→Dataset Query + Access/Usage 证据 |

G1 可以使用**已有** Dataset/Service 对象独立验收，不等 G2 建模；G2 的最终真实消费必须复用 G1 的受治理能力，不能另建第二个消费引擎，也不能用 mock 冒充完成。G1 与 G2 的设计准备可并行；有权限的 QA/Release 才能在真实环境运行已有验收入口。

**设计不是首次业务成功**：F-040 Slice A 的 ODS/逻辑/物理模型草稿与设计版本只是阶段成果；Slice B 真运行、质量/指标验证和成功消费才构成 FIRST_TRUSTED_VALUE。

## 2. 目标驱动、来源驱动、专业模式与 J1 AI 协作

**业务目标入口**：用户说“我想每天看到可信的订单量”。平台优先核对业务过程、统计时间点/时区、取消单处理、去重订单键、来源范围。业务用户需要回答业务问题，不应首先选择 Doris 方言、DDL、SQL 或工作流节点。

**数据来源入口**：已授权用户从 trade_db.trade_order、trade_order_detail、Metadata/Asset 或已有 ods_orders 设计版本出发，查看当前采集快照、字段覆盖、来源漂移和已有真实模型，避免重复创建同名对象。

**J1 智能协作故事 GM-J1-16（首批体验，而非“等 J6”）**：Agent 读取受授权的来源与现有标准，给出有证据的业务过程/属性候选、逻辑实体/关系/粒度候选、标准字段复用和待业务澄清问题，展示各项证据范围、冲突和推荐下一步。用户可以“采纳候选并交给正式专业域校验”“进入专业工作台调整”“暂不处理”。

Agent **不能**建立另一套业务标准、逻辑实体或开发运行平台，不能根据自己的一条自然语言输出就宣称已保存/发布/执行；只有 Semantic、Modeling、DataDev、Metric 和消费原域的正式身份、版本与回执才是真相。

**人工路径始终可行**：Agent 不可用、超时、候选冲突或用户关闭建议时，专业工作台和导航上下文继续可用。不强迫首次价值必须“完全无人干预全自动建仓”，但必须让用户能由业务语言理解每一步。

**J6 是另一条主线**：可信 AI 问答复用已发布 Metric/Dataset/Service 和合法 Access 回答业务问题；不能取代 J1 的 AI 辅助建设。

## 3. 一条黄金用户故事的六个必要交接关口

| 关口 | 谁必须确认 | 有权 Owner 与实际完成证据 | 防止的假成功 |
| --- | --- | --- | --- |
| **C1 来源与 ODS** | 用户审阅来源快照、真实 17 列与额外技术列的生成依据 | DataSource/Metadata Source Evidence，Modeling 保存结构、源表身份与逐列 Mapping，刷新能回读 | “字段已导入”≠“列已映射”，结构设计≠数据入库 |
| **C2 业务标准** | BIZ/STEWARD 确认下单过程、订单头/明细粒度、金额和状态含义、标准复用 | Semantic ProcessRef / 已启用 StandardFieldRef / 过程引用与标准状态 | 类型标准存在≠合法标准字段；同名字段不等于同义 |
| **C3 逻辑模型** | BIZ/BUILD 确认订单、订单明细、标识、关系基数，未知保持待确认 | Modeling Logical Model / Entity / Attribute / Relation / 确切版本 + Semantic Ref | 不是“同名就可信关系”；不是“现有 PO 就算逻辑建模完成” |
| **C4 物理落地** | BUILD 审阅目标 DWD/DIM、类型转换、旧模型复用和标准字段覆盖 | Physical Draft、逻辑→物理 Mapping、设计版本；目标方言/环境状态独立 | 模型 Published≠Doris 已部署。非 ODS 门禁与技术列豁免**待 PD-010 决策** |
| **C5 数据开发/运行** | DEV 选择真实目标实例、全量/增量、转换异常规则、运行授权 | DataDev Task Draft/Revision/Execution 与精确 PhysicalVersionRef provenance、实际环境/执行日志 | Save Draft≠Publish Task≠Run；不能把模型任务占位页当交付 |
| **C6 指标与消费** | BIZ 确认订单量时区、订单去重/取消状态；SEC/系统真实 Access 判定 | Metric Version/Validation/Publication；Dataset Query 或 Service Invoke、Source Audit 与 normalized Usage | 已发布 Metric≠数据可查询；Subscription/Lineage≠真实 Usage |

**字面角色不能机械映射**：Semantic 的 PROCESS/DIMENSION/METRIC StandardField role 与 Modeling 的 DIMENSION/MEASURE 物理分析字段角色是两个不同合同，不能为了“通过标准”就自动给 DWD 金额创建 SUM 聚合。

## 4. 十屏 V2 的最小交互补充

- P01 入口加“业务目标→证据驱动建设计划→待业务确认问题→专业接管”，不要再添一个独立 Agent 控制台的 Truth。
- P02–P03 区分元数据证据、ODS 结构候选、正式源列映射、目标物理技术列，提供 Preview/Confirm/Verify；重新导入不能无审查覆盖人工映射。
- P04 标准与过程复用真实 Semantic ID，AI 只做候选，停用/跨项目/同名冲突不能自动采纳。
- P05 逻辑编辑优先业务实体/属性/关系与粒度，明确标准字段引用、业务确认/未知状态和独立版本；不把方言/索引当首屏。
- P06–P08 预览标准化非 ODS 物理生成、复用已有设计、差异与真正设计发布，**不能隐含 Doris 部署或运行**。
- P09 进入现有 DataDev 的正式任务 Draft/Revision/Run；没有正式 ModelVersionRef/权限/目标环境时给出阻断项，不用 URL 参数假造 provenance。
- P10 输出业务认可的口径、质量、指标版本和真实访问/Usage/失败回执，符合 PD-002，允许消费者直接从 Consumption Hub 进入。

## 5. 正向、反向与证据门槛

**G1 最少真实验证**：Dataset Query 与 Data Service Invoke 的成功、有权/无权、真实空数据与 Provider UNAVAILABLE 区分；双 Project 隔离；原始 Source Audit 和 normalized Usage 可匹配；故障恢复；有权 QA/Product/Release 签收。缺部署 commit/真实授权账号/源对象时记 BLOCKED_EVIDENCE，不生成虚假的验证报告。

**G2 最少真实验证**：ODS 源列保真 + 技术列来源分组；已启用的标准字段身份；逻辑模型及版本关系回读；非 ODS 的 StdFieldRef 正式校验（审批后实施）；Model Draft/Published 与实际物理环境区别；真实 DataDev Task/Execution 与失败恢复；业务统计口径签署；Metric 验证/版本、质量、安全和 Dataset/Service 真消费回执。Agent 不可用时人工仍可完成，Agent 成功建议不能独立作为域对象保存证据。

必须覆盖未授权、跨项目、来源漂移、标准字段停用/同名冲突、历史模型不具逻辑版本、数据类型异常、转换失败、部分成功与重试、真实 Provider 不可用、未执行但展示“已发布”、取消订单/时区口径争议等反向路径。

## 6. 进入业务代码的门禁和集中 PR 粒度

| 项目 | 当前已确认/未确认 | 下一动作 |
| --- | --- | --- |
| **全域 R-01 / R-02** | 需求提出者已确认规划基线与优先级，#516 可正常评审 | 不再重复扩大全域故事地图 |
| **PD-010 / F-040** | 仍 PROPOSED / DRAFT；非 ODS 全列标准字段硬规则、技术列例外、逻辑/物理版本与 DataDev 交接合同还没有正式领域签署 | Product / Semantic / Modeling / DataDev 明确规则后再批准 Feature |
| **G1 #336** | 真实消费验收尚缺可信环境/角色/对象/审计收据 | 环境和权限到位后由有权限 QA/Release 执行**已有** Golden Runner，不另造第二系统 |
| **G2 实施** | 尚未获得完整 PRODUCT/DOMAIN 实施授权 | 代码按完整业务结果组织少量集中 PR；不按页面、按钮、DTO 分散交付 |

**推荐实施结构（批准以后）**：集中 PR-A 完成 Slice A 可信设计基础（业务目标/来源入口 + AI 证据候选/人工路径 + ODS 正式映射 + Semantic/逻辑模型 + DWD 预检）；集中 PR-B 完成 Slice B 真运行/质量/指标/真实消费。若架构评审确认某一风险必须分阶段，须说明为什么拆分仍保持完整可验收的用户成果。

**审批边界**：同意 R-01/R-02 ≠ 授权未经用户确认发布 SQL/执行 DDL ≠ 允许改写旧物理模型/标准字段数据 ≠ G1/G2 E2E PASS。首个业务成果只有真实数据被授权用户成功使用并有可核验来源/口径/使用证据才算达成。