# IA-550｜正式前端开发合同台账（以现有真实后端为准）

> **状态：REVIEW_REGISTER / OWNER_SIGNOFF_PENDING**。日期：2026-10-14。目标是冻结**实现边界**，不是发明新的全域状态机、审批或任务服务。与 [UX 签收门禁](IA-550-v0.8.2-signoff-gate.md)、[v0.8.2 源码审查](IA-550-v0.8.2-code-grounded-final-review.md) 配套。  
> 判定方法：前端每个**可点击写动作**需要 owner/service/方法/授权/请求/响应/错误/版本/回链七项；未知即 `NEW_CONTRACT` 或 `BLOCKED`，不以 HTML 原型的内存状态伪装为 API。表中路径为已核查源码依据，不自动保证所有部署版本一致。

## 0. 分类与责任

- **DIRECT**：现有后端端点与现有前端 service 可实现业务操作；仍需验证实际返回、RBAC、Project 和回链。不表示新视觉布局能未经测试交付。
- **ADAPTER**：原域写合同存在，但跨模块串联、路由上下文、只读汇总或异常状态映射要补前端 BFF/adapter/read model；任何新增聚合 endpoint 须指定 Owner，不得修改被聚合对象事实。
- **NEW_CONTRACT**：产品写语义、稳定身份、版本、持久状态或权限合同尚未确认。不得在正式 UI 开启“保存/批准/自动建任务”按钮。
- **E2E_GATE**：现有 API 存在不等于生产实现已满足 G1/G2；无真实身份/部署/执行/审计回执就保持未验收。

## 1. 功能—事实 Owner—实现分级

| 旅程/功能与操作 | 真实 Owner + 已核查 service/代码 | 分级 | 前端不得遗漏的内部能力、失败态与交接 |
|---|---|---|---|
| 工作首页：项目范围、数据源数、运行、近期工作 | Home `/api/v1/home/cockpit`、data-center/recent/schedule；`home/api.ts`、`approval/api.ts` | DIRECT + ADAPTER | 真实项目计数；Quality/Resources `available=false` 应“未知”；我的待办分权限/角色；模型草稿、指标、治理资产等跨域卡片按各 Owner 读模型聚合，不能估算或建立第二状态 |
| 导航/页面组合：五大业务域、20 候选入口、106+4 路由 | `src/config/navigation.ts`、菜单码/权限与 Flyway；IA V3.1 route disposition | ADAPTER | 分离访问轴/高亮呈现轴/精确上下文轴；保留老 URL、仅特定专业权限的独立入口、Project 校验/授权；切忌改 `parentId` 造成 RBAC 漏洞。#550 D1–D5 待签 |
| 数据源连接、测试、源表浏览 | DataSource；现有页面和 service | DIRECT | 保存连接≠测试成功≠采集成功；已有真实源表精确跳转；无权/超时/凭据与刷新后的状态 |
| 采集任务保存/dry-run/run、冻结范围、变化对账 | Metadata 物理采集 API；`metadata/api.ts` | DIRECT + ADAPTER | 来源 Project/dataSource/schema/table/列身份和 RunScope；保存→预演→运行；不将“查询 Schema”充当数据同步；返回原表/异常恢复 |
| 贴源 ODS 结构导入、列映射、校验、保存 | Modeling `modeling/import.ts`, `modeling/api.ts#commitOdsManualImport`, `modeling/mapping.ts`, `OdsSourceJourney.tsx` | DIRECT + ADAPTER | ODS 无逻辑前置；不需要 Asset WRITE；源列/技术列、指纹漂移、幂等与冲突、保存后回来源；既有模型精确复用，不按 layer 自动覆盖 |
| 业务域/过程、标准字段和绑定 | Semantic `semantic/api.ts` | DIRECT | 订单头/明细的不同粒度，`order_create` 过程；标准字段 ID/禁用/版本及审批；非 ODS 发布门禁取决于 PD-010 决策，**不能擅自生效** |
| Logical Draft 实体/属性/关系/版本编辑 | Modeling `modeling/logical.ts`; Java `LogicalDraftController`/`LogicalDraftService` | DIRECT + ADAPTER | 创建、编辑、冻结、历史/semantic-review、expectedRevision 冲突；复杂多实体图交互、删除/撤销、复合键和 join cardinality 要单列设计确认，原 API 无法凭空推断 |
| Logical→Physical 一对一审阅 | `LogicalPhysicalHandoffReviewService`（Ephemeral/read-only） | DIRECT（**只读**） | 只显示签名/SHA/已知标准 ID/对应属性与列；不能自称写映射、发布或支持多源 Join |
| Logical→Physical 正式映射保存/批准/多对一派生 | Modeling Owner（DR-06） | **NEW_CONTRACT** | 稳定 MappingSet/Revision 与 LogicalVersionRef、PhysicalModelVersionRef；列级多源/表达式、Join/SCD、粒度、标准与 lineage；乐观并发、冲突、撤回、历史、Project、角色审批；批准前禁写 |
| Physical 模型分层/DWD/DIM/DWS/ADS/版本 | Modeling `modeling/api.ts`, `modeling/types.ts`, `ModelingModelController`, `ModelingDeriveController`, `ModelingLayerFieldMappingController` | DIRECT + ADAPTER | 精确 modelId/版本；导入标准与字段、类型、分层、DDL 预检、保存/冻结/发布/撤回/审批/回滚；多源真实关系和 join 语义不能凭字段级 import lineage 假定；技术列及非 ODS 门禁待 PD |
| Model→DataDev readiness | `PhysicalDataDevHandoffReadinessService`, `modeling/api.ts#inspectPhysicalDataDevHandoff` | DIRECT（**只读**） | 仅已发布 DWD/DIM 的精确模型版本；`NOT_AUTHORIZED_FOR_TASK_CREATION` 不等于任务建成；ODS/DWS/ADS 不要误用 |
| ModelVersion→DataDev Task/Revision 创建与追溯 | Modeling + DataDev Owner（DR-07） | **NEW_CONTRACT** | immutable ModelVersionRef/源版本/目标环境/转换版本/TaskRevision、幂等键、权限、无效化、冲突/审批/回滚/恢复/审计；未批准不可自动建 task |
| 已存在 DataDev Node、Draft、Revision、Execution | DataDev `data-development/api.ts` | DIRECT + ADAPTER | 原节点编辑、SQL、发布、Release、Activate、Run/Cancel/Retry；版本 0 的编辑器 Run 不是发布修订号执行；从模型跳转仅携来源上下文，不冒充原生 provenance |
| Metric 定义/校验/版本/发布/撤回/影响 | Metric `metric/api.ts`、Semantic 口径/标准 | DIRECT | 独立 MetricVersion、validate/readiness/publish/withdraw/Usage；业务确认≠校验≠真实数据验证；变更影响须有确切来源 |
| Dataset/Service 生产、消费与证明 | Dataset `dataset/api.ts`、Service `data-service/api.ts`、Consumption `consumption/api.ts`、Security | DIRECT + E2E_GATE | Query/Invoke 实际响应、认证/授权/脱敏、SourceAudit/Usage/订阅/调用记录；FORBIDDEN/UNKNOWN/UNAVAILABLE/NOT_FOUND 正确分态；G1 #336 真实 E2E 未签 |
| 数据需求、消费问题、指标口径协作 | 需确立业务 Owner；Approval 只提供通用审批 | **NEW_CONTRACT** | Demand/Issue/MetricClarification 是否分表或同域受限类型；状态转换、Project、角色、精确 ProductKey/type/version/MetricVersionRef、幂等/写审计、证据附件、回链、列表权限、取消与重新打开。不能把 DEMO ISS-LOCAL 当生产记录 |
| Quality、Security、Lineage、Asset、MDM 与 Agent | 对应各原域 API / 专业页面；Agent Session/Trace/Source Semantic 建议 | DIRECT + ADAPTER + E2E_GATE | 复用现有规则/运行报告/审批/访问裁决/黄金记录/Agent trace；J1 Agent 人审建议≠J6 已授权数据问答，没真实执行证据必须拒答或升级专业处理 |

## 2. 三项必须由领域 Owner 冻结的写合同（决策提纲）

### DR-06｜Modeling Logical↔Physical Versioned Mapping

**现状**：`LogicalPhysicalHandoffReviewService` 只读、一对一、临时 review；未发现正式映射持久化 CRUD/审批/发布版本。**设计提议，非已批准接口**：

- MappingSet 唯一根：`projectId + physicalModelId + logicalModelId`，正式变更以 ID、`logicalVersionId`、`physicalDraftRevision`/`physicalVersionId`、`expectedRevision` 定位。复合映射/Join 是否属于同一 MappingSet，Owner 决策。
- 每个目标列的合法来源为 `LogicalAttributeVersionRef[]`、表达式/transform type、SourceColumnRef、StdFieldRef，区分 DIRECT / DERIVED / AGGREGATE / DEFERRED，并有缺口理由和血缘精度等级。
- 版本冻结、历史回读/审批与失效：上游变更、停用标准、目标结构漂移使当前审阅失效；不删除旧版本及审计；拒绝跨 Project、过期/冲突、未经授权的绑定。
- 不把映射批准等同 DDL、DataDev 任务创建。**Owner 必须决定 cardinality、Join 位置、复合键、映射审批与撤回**，然后签具体 REST 端点/响应/迁移/权限/负例。

### DR-07｜Modeling→DataDev ModelVersionRef Provenance

**现状**：DWD/DIM 只读已发布模型精确版本预检，`dataDevStatus=NOT_AUTHORIZED_FOR_TASK_CREATION`。**设计提议，非已批准接口**：

- 命令应由 DataDev Owner 受权创建/关联 `NodeDraft/TaskRevision`，引用不可变 `projectId,modelId,versionId,versionNo`、环境/方言、来源模型、映射快照、校验哈希与用户确认动作。
- 请求需鉴权、目标连接适用性与幂等键；响应明确 `created/reused/blocked`、Task/Revision IDs、缺项和返回原模型/任务深链。
- 模型变更和运行失败不能静默改历史 TaskRevision；记录影响/新 Revision 提议；执行、发布、质量、消费应各有单独 Owner 回执。
- ODS 的“继续开发”允许上下文跳转并人工在原 DataDev 创建，**但不借现有 DWD/DIM readiness 开通自动生成**；DWS/ADS 的正式交接由 Owner 单独决策适用范围。

### DR-08｜需求、问题与口径协作

**现状**：Approval 通用任务/审批不是需求、问题和指标澄清的事实 Owner。**设计提议**：

- 指定一种正式协作业务 Owner 与三种对象的稳定 ID、状态机/角色；明确业务诉求的生命周期与工程工单、Metric 口径审批、消费产品问题的关系。
- Consumer Issue 必须 `ProductKey + productType + publishedVersionRef + projectId` 精确存储；前端创建草稿与旧单/旧来源完全隔离；仅提交后写新对象。
- 要求列表/创建/详情/评论/转交/关闭/重开/验收动作的授权和审计，通知回到原 Product/Metric/Run；Approval 仅用于需要审批的子过程。
- 禁止把原型内存 `ISS-LOCAL` 或通用 Approval `flowId` 冒充真实生产实体。

## 3. 生产前端 Slice A 的最小接口冻结清单

**只允许 DIRECT 和经过明确审查的 ADAPTER**，每一项开 PR 前附：
- `route / UI action / Owner API / request fields / response ID+version / permissionCode / project scope / error cases / return URL / authoritative test`；
- `loading/EMPTY/FORBIDDEN/NOT_FOUND/UNAVAILABLE/STALE/CONFLICT` 独立状态；不从错误响应推导为空集合；
- 读 API 需要分域降级与 Project 切换取消/忽略旧请求；聚合失败不能虚构“0”；
- 已有历史深链和受限角色不得被工作台容器的父菜单 RBAC 无意拒绝；
- 完整回归包含来源 → ODS → 保存/重开/来源返回，Logical 版本 → 已有 Physical 预览与精确目标恢复。新映射保存、自动建 TaskRevision 与新协作持久化属于未来 Slice，不能偷偷塞进首批。

## 4. 冻结登记

| 决策 | 责任人 | 状态 | 允许当前 UI 行为 |
|---|---|---|---|
| #550 D1–D5 菜单、入口、专业能力可发现性 | Product + UX + Security | PENDING_SIGNOFF | 只允许设计与路线签审 |
| DR-06 正式逻辑→物理映射 | Modeling + Semantic + Security | PENDING_OWNER | 只读 preview/review |
| DR-07 物理版本→DataDev 任务 | DataDev + Modeling + Runtime | PENDING_OWNER | 只读 readiness/带精确上下文跳转 |
| DR-08 协作业务事实 | Product/Collaboration + Metric/Consumption + Security | PENDING_OWNER | 原型演示、现有 Approval 原功能；禁新类型持久化 |
| PD-010 非 ODS 标准/技术列发布门禁 | Product + Semantic + Modeling | PROPOSED（未接受） | 维持既有实现，不擅自强制/放宽发布 |
| G1/G2 真实消费/执行链 | QA + Product + Release | PENDING_E2E | 不宣称已交付 |

**审核通过本台账不等于默认批准任何新增写合同**；后续每项 `NEW_CONTRACT` 应从本表提炼独立 Owner 决策与测试，先评审再编码。
