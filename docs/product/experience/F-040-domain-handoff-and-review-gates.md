# F-040 · 领域事实交接与决策门禁 V2

Status: DRAFT（产品/领域联合评审材料，不是开发授权）  
Date: 2026-10-10  
依据：[#512 现场证据](https://github.com/gitfortian/data-ops/issues/512)、[#513 故事地图](https://github.com/gitfortian/data-ops/issues/513)、[PD-010（PROPOSED）](../decisions/PD-010-guided-business-to-data-journey.md)、[逐屏交互规格](./F-040-screen-contracts-v2.md)。

## 1. 现有基础与不能直接使用的部分

| 环节 | 已实现的证据 | 缺失/风险 | 倾向 |
|---|---|---|---|
| 来源发现 | Metadata Catalog、采集与 Source Evidence | 源表选择在多个页面重复，保存后来源映射未建 | 复用原数据来源身份、批量候选审阅 |
| 业务过程 | Semantic BusinessProcess、过程字段引用 | 建模页面过程 ID 没有可靠贯通 | 采用正式 ProcessRef |
| 数据标准 | 预置 TYPE/CODE/UNIT/CALIBER/SECURITY/NAMING 标准 | 标准存在≠订单标准字段存在 | 保留专业标准目录，显示业务缺口 |
| 标准字段 | 字段库按 PROCESS/DIMENSION/METRIC 角色校验并可引用过程 | 首次为空、只能逐项建，缺批量候选审阅 | 复用正式 stdFieldId，增加审阅流程 |
| ODS 导入 | 建模结构可从源表导入；另有逆向导入 Writer | 模型结构、源身份、列 Mapping 的保存行为分离 | 一次有回执的业务确认，不允许静默全覆盖 |
| 非 ODS | 物理列保存 stdFieldId 与 stdTypeId | 部分提示只检查 stdTypeId；绕过入口无统一强校验 | 同一服务端正式发布闸门 |
| 逻辑模型 | LogicalModel/Entity/Attribute/Relation/Version 以及逻辑→物理映射骨架 | 没有完整 CRUD/API/生成与 UI；逻辑属性没有 stdFieldId | 补领域合同，再设计业务语言工作区 |
| 模型版本 | ModelVersionService 固化物理结构 | 元快照未完整冻结标准/过程/来源/映射历史 | 版本关联及可复现合同需定义 |
| 血缘 | 当前 MODEL→COLUMN CONTAINS 图 | “下游 19”实际包括模型自己的列节点 | 区分包含、设计来源与真实读写 |
| 开发 | F-002 专业 DataDev Workspace / Draft / Revision / Execution 能力 | 模型任务 Tab 只是占位；无正式 ModelVersion→Task 交接 | 复用 DataDev，不新建执行引擎 |

代码索引：[标准字段页](https://github.com/gitfortian/data-ops/blob/main/data-ops-ui/src/pages/semantic/fields/index.tsx)、[物理模型编辑](https://github.com/gitfortian/data-ops/blob/main/data-ops-ui/src/pages/modeling/detail.tsx)、[统一模型详情](https://github.com/gitfortian/data-ops/blob/main/data-ops-ui/src/pages/modeling/unified/index.tsx)、[逻辑属性](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/domain/LogicalAttribute.java)、[逻辑实体/表映射](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/domain/LogicalEntityMapping.java)、[逻辑属性/列映射](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/domain/LogicalAttributeMapping.java)。

注：仅代码/现场证据，不代表数据库迁移成功，也不等于接口已实测。旧 FR-06 曾要求逻辑模型不关联 Semantic；现行 PD-003 已确定 Domain/Process/Standard 的 Owner 是 Semantic，必须先解决设计差异。

## 2. 正式事实身份（产品合同提案）

| 引用 | 事实 Owner | 交接时至少核验 | 不能简化为 |
|---|---|---|---|
| SourceEvidenceRef | DataSource/Metadata | project、datasourceId、schema/table/column、采集版本/指纹、覆盖、授权 | 表名字符串 |
| ProcessRef | Semantic | processId、project、业务过程是否有效 | “下单”展示名 |
| StandardFieldRef | Semantic | stdFieldId、生效/角色、合法 TYPE/UNIT/CODE 等标准引用 | stdTypeId 或同名技术列 |
| LogicalVersionRef | Modeling | logicalModelId、精确版本、实体/属性与过程/标准引用 | “最新逻辑草稿” |
| PhysicalVersionRef | Modeling | modelId/modelKey、精确 version、结构、来源/逻辑/标准/映射版本关联 | 仅列 JSON/模型名称 |
| DevTaskRef | DataDev/Runtime | node/task、draft/revision/execution 身份与运行结果 | 页面“已生成 SQL” |
| MetricVersionRef | Metric | metricId、精确版本、验证/发布状态 | 指标已启用 |
| ConsumptionRef | Dataset/Service/Consumption | productKey、Access 结果、实际 Usage Evidence | 仅保存引用或 Lineage |

区分三条关系：**物理来源→ODS 列**是来源映射；**标准字段→逻辑属性→物理列**是设计关系；**DataDev 执行/SQL→实际目标**是运行证据。禁止因为同名字段就自动登记真实血缘。

跨域 URL 只包含候选身份，目标 Owner 每次对 project、RBAC、版本、指纹和当前可用性重新鉴权。建议与正式事实必须有不同的数据状态和显示风格；读取不到 Provider 不得显示零。

## 3. 产品负责人必须明确的八项裁决

| ID | 建议选择（待批准） | 不能默认假定 | 责任人 |
|---|---|---|---|
| DR-01 入口 | 现有来源/模型/过程页提供双入口、任务化引导，不新增顶级域 | 新建完整任务中台 | Product |
| DR-02 ODS 例外 | 可直接复制来源保真结构，暂不要求逻辑/标准字段先行 | ODS 也必须走完整逻辑建模 | Product/Modeling |
| DR-03 标准化闸门 | **全部非 ODS 物理字段原则上都需要合法 stdFieldId**；正式发布硬阻断，草稿可记录治理缺口；技术列例外需显式批准 | 只检查 stdTypeId 或模糊限定“业务字段” | Product/Semantic/Modeling |
| DR-04 逻辑属性 | 逻辑草稿允许未确认标准候选；正式转化/发布前以 StdFieldRef 校验 | 在逻辑域新造第二标准字段库 | Semantic/Modeling |
| DR-05 实体关系 | 用户确认主标识、业务粒度与基数；同名字段仅做候选 | 自动猜 Customer 1:N Order 事实 | Business Owner |
| DR-06 物理生成 | 从精确逻辑版本预览，关联已有物理模型或生成草稿，支持幂等审阅 | 机械“一实体一张表” | Data Architecture/Modeling |
| DR-07 开发交接 | 精确 ModelVersion + 来源 + 目标连接预检后进入现有 DataDev Draft，运行另行授权 | Modeling 自建第二任务/执行系统 | Modeling/DataDev |
| DR-08 首个价值 | 真实“每日下单订单量”+一次 Dataset 查询/Usage 回执（业务口径需确认） | 发布 ODS 设计即算 0→1 完成 | Product/Metric/Business Owner |

**DR-03 特别需要决定**：保存草稿可以保留未治理字段，但正式发布或生成正式下游引用前不可绕过；技术审计列例外必须定义生成规则、允许名单、审计/回链和授权审批。此方案与当前可配置的分层 stdMandatory 开关如何一致，需要记录正式 Product Decision，不得在 UI 中自行绕开。

决策记录格式：每条标记 **ACCEPT / REVISE / REJECT**、结论、决策人、日期、相关兼容/迁移影响。PD-010 状态仍是 PROPOSED，F-040 仍是 DRAFT，直到评审完成前禁止以此新增生产业务写路径。

## 4. 两个跨域写入的严谨回执

**ODS “保存结构并确认来源”**：需要返回结构保存、模型 SourceRef、列映射三个独立事实的结果；底层若无法单事务提交，必须有服务端有界补偿/一致性核对及幂等回执，不能靠前端三个请求都返回 200 就展示一条“全部成功”。部分成功时必须可恢复，不重复覆盖人工治理信息。

**逻辑→物理批量生成**：精确逻辑版本 + 物理目标分层/方言 + 现存目标模型匹配策略 + 标准字段引用校验是必要输入；生成 2 个物理模型中 1 个失败时返回分别结果，不写成“成功生成 2 张”。已经发布的物理结构不可被逻辑新草稿静默更新；生成草稿≠发布设计≠执行 DDL。

## 5. 版本、权限和兼容性

- 现有物理模型允许继续独立管理；历史上没有逻辑版本的模型显示“尚未关联逻辑模型”，不自动生成猜测关系；不能删除已有 modelKey 或改写历史发布快照。
- 逻辑模型已有 Flyway 建表迁移脚本不表示实际环境完成迁移；新增关联字段与版本策略须向后兼容，不能为设计重写已经执行过的旧迁移。
- 源头表列漂移、标准停用/跨项目、逻辑版本变化、模型发布后编辑、目标 Doris 不可连接、权限收回都不能自动按旧候选继续提交。
- 来源 VARCHAR 金额/时间不可默默转数值和时间；转换函数的字符级校验≠数据库方言解析/实际行数据检验。
- Model published / Physical deployed / Dev revision published / Execution succeeded / Metric published / Dataset consumed 是不同拥有域的事实。其显示可聚合，但不能写一个万能“建模已完成”状态。

## 6. 联合评审与两条纵向交付切片

**Slice A（可信设计）**：来源证据/ODS 正式映射一致可回读、标准字段创建/复用与过程引用、逻辑模型实体/属性/关系与独立版本、非 ODS 的缺口可解释、真实回链。验收不能声称真实数据入仓。

**Slice B（第一次业务价值）**：从精确逻辑版本受控生成或关联 DWD 物理草稿，正式 stdFieldId 门禁、物理设计发布、从版本进入 DataDev 真实任务和执行、真实质量/权限/血缘、Metric 定义验证发布、Dataset/Service 实际查询和 Usage Evidence。

**评审顺序**：Product 冻结 DR-01～08 → Semantic/Modeling 确认标准字段与逻辑模型边界 → Modeling 确认来源/逻辑/物理引用与版本 → DataDev 确认真实交接 → Metric/Consumption 确认消费端 → Security/QA 审核负向 E2E。之后才能把 F-040 升为 APPROVED，并按 Slice A/B 的跨域用户价值集中排工程 PR，不按一个按钮/一个 Tab 切 PR。

**当前仍待确认**：业务指标按哪个时区统计、是否剔除取消订单、按 order_id 还是 order_no 去重、支付/下单如何区分业务事件。不得由 AI 或方案作者自行定真相。

**本次交付是设计和评审证据，未执行真实 E2E。**

## 7. 第三轮评审材料

为避免本 V2 表仅停留在系统能力罗列，已根据当前 `main` 的 `ModelVersionService`、`ModelStructureService`、`ReverseImportWriter`、`MappingService`、`DevelopmentNodeController`、`DevelopmentTaskController`、`DevelopmentTaskApi` 和 `SemanticFieldService` 补充 [V3 正式合同建议](./F-040-contract-resolution-proposal.md)。V3 对 DR-01～DR-08 给出可讨论推荐方案，**并保留产品、架构、治理 Owner 的签署门禁**。
