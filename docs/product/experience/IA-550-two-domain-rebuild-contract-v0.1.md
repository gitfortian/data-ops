# #550 两业务域前端重建合同与统一设计底稿 v0.1

> **状态：DRAFT / 待设计冻结；仅用于评审，不等于 D1～D8 已批准，也不授权更改领域状态机或上线新菜单。**  
> 日期：2026-10-13。上游：[#550](https://github.com/gitfortian/data-ops/issues/550)（尤其评论 [深度复审](https://github.com/gitfortian/data-ops/issues/550#issuecomment-6136909226) 和 [双域范围](https://github.com/gitfortian/data-ops/issues/550#issuecomment-6136948422)）、[IA V3.1](IA-V3.1-navigation-page-composition-plan.md)、[106+4 去向矩阵](IA-V3.1-route-disposition.md)、[#539 J1 冻结体验](https://github.com/gitfortian/data-ops/issues/539)。  
> 本文“重建”是**预定工作范围**，不是实现完成状态。配套交互样板：[两域可点击原型](prototypes/IA-550-two-domain-workbench-v0.1.html)（静态演示，不接真实 API，不是可用性测试证据）。

## 一、目标、角色与边界

**产品目标**：使用者不再在一堆 CRUD 页中猜测入口，而是在同一工作台沿着 **明确身份 → 当前状态 → 下一动作 → 事实证据 → 返回来源** 完成任务。验收以同角色真实连续任务而非菜单数量或局部截图为准。

**纳入**：① 标准、指标与建模（18 个当前 appRoutes）；② 数据资产与治理（32 个当前 appRoutes，含技术元数据项目级专业功能）；技术 Metadata 旧地址别名 4 条另作兼容回归。对象 Owner 与业务权限不因视觉整合改变。

**不纳入**：接入、开发运行、消费服务、首页、我的待办、平台设置的视觉重构；不执行全站 20 入口菜单收敛、不迁移其他域 Flyway、不新增通用全域 Run/Asset/Task 真相、不批准非 ODS 的 PD-010/F-040 业务范围。跨域仅验证已有跳转、参数、权限、返回及数据事实。

**角色画像**：业务负责人/指标 Owner、建模实施者、资产 Steward、Metadata 管理人员、质量专业人员、安全审计人员、MDM 专业人员，以及只拥有单项 leaf 授权的受限账号。画像是测试任务定义，**不等于**真实 RBAC 授权；每次走查必须记录真实 grant。

## 二、统一的信息与交互设计系统（两个域共享）

1. **工作台外壳**：统一顶栏（项目、面包屑、任务标题、对象身份、主 CTA）、左边可选对象树/任务导航、中央主要工作区、右侧按需展开证据/影响/帮助。不能每个工作台都套相同三栏：模型画布允许全宽；审计表格以数据密度为先。
2. **列表发现**：搜索、过滤、排序、分页、结果数量、可恢复的筛选；“当前项目／对象类型／生命周期状态”与全局项目切换可辨。资产与技术实体并列时必须显示**类型和治理状态**，不能把实体发现误写为已上架资产。
3. **详情身份栏**：对象类别、名称、稳定 ID、所属项目、Owner、版本/草稿、状态、来源与证据时间；历史版本与当前版本标记不同。只展示有来源的事实，不以 UI 汇总推断成功。
4. **编辑器**：工作区重点突出字段级错误；**保存草稿、校验、发布/生效、执行**分开；不可用动作解释条件。未保存退出给“继续编辑／放弃变更／保存（若允许）”确认，保存失败保留输入。严禁“保存成功=发布成功”。
5. **状态语义**：独立呈现 LOADING、READY、EMPTY、FORBIDDEN、UNAVAILABLE、UNKNOWN、PARTIAL（部分上游不可用）、NOT_APPLICABLE；处理中/异步回执同样独立。EMPTY 必须有真实成功查询证据；请求失败不得清空表后伪装 EMPTY。每个状态有可行下一步，未授权操作不发 API。
6. **动作分级**：主动作仅代表当前阶段最可能的下一步；次动作保持可发现；危险动作二次确认并说明对象、范围与不可逆结果；审批、上架、策略生效、运行不能共享一个泛化“完成”。
7. **跨页连续性**：保留 projectHint（待核验）、Owner 对象 ID、版本、Run/Execution、来源页、合法筛选与 Tab；目的地**重新读取并鉴权**。切项目/撤权后清理旧对象、忽略迟到响应；已保存可回读、未保存不保证刷新保留。
8. **权限与导航归属**：默认视觉归属与原 parentId / leaf menuCode / permissionCodes 分离；工作台各 Tab 逐项鉴权，专业集合工具独立可发现。原 route、query、grant 保留，不通过嵌套旧路由页面拼装“新工作台”。
9. **视觉 Token**：统一页面最大内容宽度、8px 间距层级、字体层级、灰阶、主色、风险色、阴影层级、圆角、输入焦点、空态和反馈。Token 以现有 Yak Design System / `@/components/ui` 演进；业务样式限定模块，禁止全局 DOM 选择器、`!important` 补丁。
10. **响应式与可访问性**：1440/1024/768/390/320 CSS px 的布局、主动作/过滤器换行；抽屉与表单在 320px 仍可操作；图谱/复杂表格只在其局部滚动。键盘 Tab、焦点可见、Escape、对话框焦点返回、错误提示定位是强制验收项。

**职责分离**：Route/Page 负责上下文组装；业务 Hook 负责数据、权限、竞态、恢复；领域 Component 负责表达；Service 负责 Owner API 与响应校验；Yak UI 只负责跨域视觉交互原语。禁止复制旧页面大块 JSX 然后换色，也不能把现成旧页嵌为 Tab 交差。

## 三、两条首批完整任务旅程与屏幕合同

### J2-A：业务过程 → 标准字段 → 模型 → 指标（定义域样板）

| 场景屏幕 | 首要信息 | 主操作 / 下一步 | 失败、权限与返回合同 |
| --- | --- | --- | --- |
| D-01 业务语义工作台 | 业务域树、过程列表、过程粒度与确认状态、来源关系 | 选中已有过程 → 看标准字段或在有权时创建过程 | 过程不存在≠列表失败；未确认粒度解释阻断；返回保留业务域、过程 ID |
| D-02 数据标准工作台 | 标准/标准字段两种稳定对象、状态与引用 | 按过程上下文筛选/核对字段 → 继续模型 | 仅可读无权修改；缺标准时展示治理待办而不是凭空创建 |
| D-03 模型工作台 | 逻辑/物理模型的明确类别、层级、版本、来源映射 | 进入已有模型 → 属性/映射/核验/保存 → 后续指标 | ODS 不被强制要求 Asset Write；非 ODS 未批准规则不自动启用；切对象脏表单提示 |
| D-04 指标中心 | 指标定义、口径、版本、校验、发布/引用状态 | 建立/查看与模型关联的指标；独立校验、发布，定位上下游影响 | 发布受域合同与授权限制；只读可看影响；上游模型/标准反向影响从项目级工具可达 |

**演示样本只能使用明确标注的样例业务过程/字段/模型/指标，不充当真实接口证据。** 设计必须支持“从标准字段直接反查影响”和“从逻辑/物理模型返回过程”，而非强制一路线性向前。

### J-Asset-A：资产发现 → 详情证据 → 治理处理（治理域样板）

| 场景屏幕 | 首要信息 | 主操作 / 下一步 | 失败、权限与返回合同 |
| --- | --- | --- | --- |
| G-01 资产目录 | 资产/技术实体的明确视图区分、目录树、搜索、上架状态、Owner 与来源 | 选择对象 → 详情证据 | 只有技术实体并不等于资产上架；不因切换视图扩大读权 |
| G-02 资产对象 360 | 稳定对象身份、技术来源、映射、质量/安全摘要与有证据的时间 | 查看证据 → 进入与该资产有关的治理队列 | 不能通过摘要伪造专业模块结果；不可用展示 PARTIAL/UNKNOWN |
| G-03 治理工作队列 | 盘点上架、变更、对账的独立类型与真实回执、处理人 | 核对后按已有权限处理、查看回执 → 返回资产 | PENDING 不叫已上架；RECONCILE ACCEPTED 不叫已完成；无写权只读回执 |
| G-04 专业工具 | 项目级 Metadata 采集、血缘、质量检查、编目、安全、生命周期 | 由资产回链或直接在域内合法发现 | 无已知 Asset ID 也能进入项目级集合工具；每个模块独立鉴权 |

## 四、逐页重建台账（以 V3.1 的现有 Route ID 为准）

下面每条均对应代码实际存在的路由；“重建”表示**新交互实现取代旧路由页面的主体布局与状态组织**，不表示删除既有 URL。具体后端读写、参数与回链 Schema 应在每一批编码前逐项用真实调用点核验。此表覆盖 18 + 32 = **50/50**，不是全站 106/106 的代替品。

| 现有路由 ID | 保留旧 URL | 目标工作台 | 页面处理 | 批次 | Truth Owner |
| --- | --- | --- | --- | --- | --- |
| modeling-workspace | `/modeling` | 模型工作台 | 重建工作台/专业视图 | R1 | Modeling |
| semantic-standards | `/semantic/standards` | 业务语义／标准 | 重建工作台/专业视图 | R1 | Semantic |
| semantic-domains | `/semantic/domains` | 业务语义／标准 | 重建工作台/专业视图 | R1 | Semantic |
| semantic-processes | `/semantic/processes` | 业务语义／标准 | 重建工作台/专业视图 | R1 | Semantic |
| semantic-process-edit | `/semantic/processes/:id/edit` | 业务语义／标准 | 重建详情/编辑 | R1 | Semantic |
| semantic-fields | `/semantic/fields` | 业务语义／标准 | 重建工作台/专业视图 | R1 | Semantic |
| semantic-layers | `/semantic/layers` | 业务语义／标准 | 重建工作台/专业视图 | R1 | Semantic |
| metric-manage | `/metric/manage` | 指标中心 | 重建工作台/专业视图 | R1 | Metric |
| metric-detail | `/metric/manage/:id` | 指标中心 | 重建详情/编辑 | R1 | Metric |
| metric-lineage | `/metric/lineage` | 指标中心 | 重建工作台/专业视图 | R1 | Metric |
| metric-impact | `/metric/impact` | 指标中心 | 重建工作台/专业视图 | R1 | Metric |
| metric-service | `/metric/service` | 指标中心 | 重建工作台/专业视图 | R1 | Metric |
| modeling-logical-draft | `/modeling/logical` | 模型工作台 | 重建工作台/专业视图 | R1 | Modeling |
| modeling-model-detail | `/modeling/models/:id` | 模型工作台 | 重建详情/编辑 | R1 | Modeling |
| modeling-model-mapping | `/modeling/models/:id/mapping` | 模型工作台 | 重建详情/编辑 | R1 | Modeling |
| modeling-model-layer-mapping | `/modeling/models/:id/layer-mapping` | 模型工作台 | 重建详情/编辑 | R1 | Modeling |
| modeling-mainline | `/modeling/mainline` | 模型工作台 | 重建工作台/专业视图 | R1 | Modeling |
| modeling-impact | `/modeling/impact` | 模型工作台 | 重建工作台/专业视图 | R1 | Modeling |
| data-analysis-lineage | `/data-analysis/lineage` | 血缘专业 | 重建工作台/专业视图 | R3 | Lineage |
| mdm-overview | `/mdm/overview` | 主数据专业 | 重建工作台/专业视图 | R4 | MDM |
| mdm-modeling | `/mdm/modeling` | 主数据专业 | 重建工作台/专业视图 | R4 | MDM |
| mdm-identification | `/mdm/identification` | 主数据专业 | 重建工作台/专业视图 | R4 | MDM |
| mdm-cleansing | `/mdm/cleansing` | 主数据专业 | 重建工作台/专业视图 | R4 | MDM |
| mdm-approval | `/mdm/approval` | 主数据专业 | 重建工作台/专业视图 | R4 | MDM |
| mdm-entity-detail | `/mdm/modeling/:id` | 主数据专业 | 重建详情/编辑 | R4 | MDM |
| data-lifecycle-policy | `/data-lifecycle/policy` | 生命周期 | 重建工作台/专业视图 | R3 | Lifecycle |
| data-lifecycle-monitor | `/data-lifecycle/monitor` | 生命周期 | 重建工作台/专业视图 | R3 | Lifecycle |
| data-lifecycle-storage | `/data-lifecycle/storage` | 生命周期 | 重建工作台/专业视图 | R3 | Lifecycle |
| data-security-overview | `/data-security/overview` | 安全治理 | 重建工作台/专业视图 | R3 | Security |
| data-security-classification | `/data-security/classification` | 安全治理 | 重建工作台/专业视图 | R3 | Security |
| data-security-access | `/data-security/access` | 安全治理 | 重建工作台/专业视图 | R3 | Security |
| data-security-masking | `/data-security/masking` | 安全治理 | 重建工作台/专业视图 | R3 | Security |
| data-security-audit | `/data-security/audit` | 安全治理 | 重建工作台/专业视图 | R3 | Security |
| data-security-compliance | `/data-security/compliance` | 安全治理 | 重建工作台/专业视图 | R3 | Security |
| data-metadata-overview | `/data-metadata/overview` | 技术元数据专业 | 重建工作台/专业视图 | R2 | Metadata |
| data-metadata-collect | `/data-metadata/collect` | 技术元数据专业 | 重建工作台/专业视图 | R2 | Metadata |
| data-metadata-collect-detail | `/data-metadata/collect/:id` | 技术元数据专业 | 重建详情/编辑 | R2 | Metadata |
| data-asset-overview | `/data-asset/overview` | 资产目录／治理 | 重建工作台/专业视图 | R2 | Asset |
| data-asset-catalog | `/data-asset/catalog` | 资产目录／治理 | 重建工作台/专业视图 | R2 | Asset |
| data-asset-inventory | `/data-asset/inventory` | 资产目录／治理 | 重建工作台/专业视图 | R2 | Asset |
| data-asset-taxonomy | `/data-asset/taxonomy` | 资产目录／治理 | 重建工作台/专业视图 | R2 | Asset |
| data-asset-detail | `/data-asset/detail/:id` | 资产目录／治理 | 重建详情/编辑 | R2 | Asset |
| data-quality-overview | `/data-quality/overview` | 质量治理 | 重建工作台/专业视图 | R3 | Quality |
| data-quality-table-config | `/data-quality/table-config` | 质量治理 | 重建工作台/专业视图 | R3 | Quality |
| data-quality-monitor-create | `/data-quality/monitor/create` | 质量治理 | 重建详情/编辑 | R3 | Quality |
| data-quality-monitor-detail | `/data-quality/monitor/:id` | 质量治理 | 重建详情/编辑 | R3 | Quality |
| data-quality-monitor-edit | `/data-quality/monitor/:id/edit` | 质量治理 | 重建详情/编辑 | R3 | Quality |
| data-quality-execution | `/data-quality/execution` | 质量治理 | 重建工作台/专业视图 | R3 | Quality |
| data-quality-execution-detail | `/data-quality/execution/:executionNo` | 质量治理 | 重建详情/编辑 | R3 | Quality |
| data-quality-rule-template | `/data-quality/rule-template` | 质量治理 | 重建工作台/专业视图 | R3 | Quality |

**四条旧 Metadata 别名（只做本轮兼容回归）**：`/data-metadata/catalog`、`/metadata/explorer`、`/metadata/detail/:id`、`/data-metadata/search`。必须验证入参、合法重定向、技术实体/资产区分、返回/刷新，不默认宣布可以移除。

**近期资产/元数据 UI 已有改善（例如 PR #551）**：先提炼已验证的 Owner Service、请求竞态控制、Asset 双视图和状态分类；若工作台被本轮认定“重建”，应重画其信息结构和生命周期交互，**不得把最近修复的旧 route JSX 直接嵌入新工作台**。成熟 SQL 编辑器、Model 画布等可复用独立能力，但需通过新交互合同。

## 五、分批落地与退出规则

| 批次 | 真正交付单元 | 验收必须连续覆盖 | 不许用来凑交付 |
| --- | --- | --- | --- |
| **R0 统一设计冻结** | 两域原型、Token、角色与权限矩阵、每页异常状态合同、人员责任分配 | 产品/UX/前端架构共同评审原型；签收 D6/D7/D8 与首批目标 | 只有文字说明、菜单截图、无状态样例 |
| **R1 定义域完整样板** | 业务语义、标准、模型、指标的工作台及详情/编辑/影响 | 过程→字段→模型→指标；返回、保存、校验、发布分别验真；项目/权限、脏表单与失败恢复 | 四个列表各自换肤却无法交接 |
| **R2 资产发现与治理** | 资产目录、详情、治理队列、技术 Metadata 专业工具 | 资产/实体双视图、来源证据、变更/对账回执；无 Asset Write 用户仍能合法只读 | 把技术实体误写成上架资产、把 PENDING 写成完成 |
| **R3 专业治理工作台** | 质量、安全、生命周期、跨对象血缘 | 项目级工具首次发现、最小授权；策略与执行区分，质量仅执行权限反例 | 只做安全/质量概览，把专业页留作未知 URL |
| **R4 主数据专业** | MDM 总览、实体、识别、清洗、变更完整连续任务 | 领域权限、实体身份、变更/审核证据与回退 | 将 MDM 当作 Asset 详情的一张 Tab |
| **R5 双域回归与退出** | 各批新实现与旧版退出条件、真实 E2E 证据 | 回归本轮 50 + 4，相关跨域链接；部署 commit、真实 grant、同角色效率对照 | PR 合并或 mock 录屏当作部署签收 |

批次可再按工作台拆小 PR 以控制冲突，但**任何批次对外称“工作台已重构”前，必须完成列表、详情、编辑、状态恢复、移动端和合法返回**。未完成的子页面维持旧实现并标记“过渡”，不能混入完成统计。

**发布/回退策略**：采用现有 URL、原 Owner 服务和授权；必要时仅为新工作台渲染做受控呈现开关。保留旧实现只作短期回退，记录何时允许删除；不撤销角色菜单 grant、不触碰已执行 Flyway、不另增假业务状态机。跨页兼容测试失败直接阻断切换。

## 六、统一责任（岗位待落实人选）

| 角色（单一责任源） | 应交付的批准结果 | 当前指派状态 |
| --- | --- | --- |
| Product Owner | 冻结两域范围、D6 页面重建/退出、D8 指标和重要行为合同 | **待明确签署** |
| UX 总负责人 | 统一原型版本、Token、状态/反馈/可达性规则，所有批次视觉交互终审 | **待指定实际负责人** |
| 前端架构负责人 | 共享组件/页面结构、访问轴与呈现轴解耦、工程兼容、增量迁移方案 | **待指定实际负责人**（可与 UX 兼任） |
| R1 定义域集成负责人 | 四工作台跨域接续的端到端真实旅程 | **待指定** |
| R2–R4 治理域集成负责人 | 资产与专业治理真实旅程，Owner 边界与恢复 | **待指定** |
| QA/真实角色验收负责人 | 角色 grant、项目、对象/版本/Run、浏览器记录、新旧效率对照 | **待指定** |

多人/多 Agent 并行须使用同一原型版本、交互 Token 与接口合同；公共模块变更先经统一负责人审查。不虚构具体人名或表示签收已完成。

## 七、可执行 Gate 与证据目录

**R0 / 设计 Gate**：50/50 路由归宿确认、4/4 alias、两条完整任务原型、各页面主状态和动作、两个域的受限角色画像、五种以上屏宽/键盘、真实责任人、D1～D8（本轮适用的）决定。未确认不得宣称正式页面设计已冻结。

**工程 Gate**：禁用新增全局 DOM 补丁；目标模块类型诊断为零；现有 TS/Jest/菜单契约/代码风格与架构校验通过；旧 URL/query/权限/业务事实不退化；新增的 menu 目录变更若确有需要则通过增量 MySQL/PostgreSQL 迁移验证，不编辑既有 V2 baseline。

**真实旅程 Gate**：在实际环境由受限用户连续完成任务，记录部署 SHA、grant、项目、精确对象、版本、回执、截图/录像和失败恢复；对照相同角色/任务的旧版完成时间与重复输入。中位完成时间改善 30% 可作**待基线测量的建议目标**，不是既定事实/已签 KPI。

**拒绝签收**：仅减少菜单、换肤、旧页嵌套、复制旧 JSX、局部截图、管理员测试、Mock 成功、接受任务就显示执行成功，任一均不能作为完成依据。

### 实施前尚需关闭的决定

- 首批样板原型的具体页面/状态/视觉签收，D6–D8 的采纳与正式责任人。
- 各 route 的真实 query 键、上游调用入口、权限与 API 对照；尤其项目级独立工具的单项授权反例。
- 两域当前合法状态和支持的后端动作；不能为了重构而补齐尚未批准的 PD-010 或 Agent 自动发布能力。
- R1/R2 的真实环境基线测试与目标阈值，以及上线切换和旧实现退出周期。

**交付说明**：本文件 + 静态交互原型是下一轮设计评审材料，不是代码重构的完成证明。