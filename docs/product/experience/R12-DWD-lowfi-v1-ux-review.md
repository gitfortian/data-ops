# R12-0 第三轮｜DWD 低保真交互原型与走查评审（v1）

> 日期：2026-10-16  
> 状态：`PROTOTYPE_REVIEW / NOT_APPROVED`，**纯设计演示，不连接 API、不持久化、不发布业务对象**。  
> 关联：[R12 规划 #576](https://github.com/gitfortian/data-ops/issues/576)、[DR-06 #556](https://github.com/gitfortian/data-ops/issues/556)、[DR-07 #557](https://github.com/gitfortian/data-ops/issues/557)、[PD-010](../decisions/PD-010-guided-business-to-data-journey.md)。  
> 实体真相：Semantic / Modeling / DataDev 各自持有，不新造统一写入 Owner。  
> 人工建模优先：无 Agent、无自动 SQL/DDL/ETL、无新顶级导航。

**可操作原型：** [R12 DWD 工作台低保真单页 HTML](prototypes/R12-DWD-physical-workbench-lowfi-v1.html)。可保存文件后在浏览器直接打开；所有动作是本地演示状态，所有 ID/字段和发布提示均是**示意**。原型不能证明真实业务字段存在、用户授权、SQL 运行或 DB 验收。

## 1. 本轮仅评审“一条最短 DWD 人工故事”

用户已经具备某份来源的 ODS 设计引用，想建立 `dwd_order_detail`。不预设 `order_create` 实际粒度一定是明细，**必须由 Semantic Owner 确认**。若原过程是订单级，应提示选择已批准的订单明细过程/粒度；不能无证据直接接续。

用户目标不是看到 9 个模型编辑弹窗，而是达成三个可区分的成果：

| 步骤 | 唯一主要操作 | 用户明确知道什么 | 服务端事实归属 / 原型限制 |
|---|---|---|---|
| 01 选择逻辑依据 | **确认依据，继续字段映射** | 本项目的业务过程、grain、逻辑设计快照与物理 DWD 草稿、确切 ODS 来源 | Semantic Process + Modeling LogicalSnapshot；逻辑 `freezeDraft` 仍是 DRAFT，需要独立确认 |
| 02 人工字段映射 | **保存草稿 → 冻结映射**（两次有区别的操作） | 每个物理目标列对应已确认的逻辑属性、标准字段、ODS 来源与 DIRECT/RENAME 规则；草稿可修订 | 尚未有正式 MappingSet 写入；实际代码只有 `REVIEWABLE_ONLY` 只读审阅，演示按钮不可对外宣称真实持久化 |
| 03 发布设计并接续 | **发布 DWD 设计** | 物理版本绑定逻辑快照、MappingRevision、标准证据摘要；发布不等于目标库 DDL 或加工运行成功 | Modeling 的原子发布尚未实现 DR-06 扩展；DataDev 的正式 TaskRevision 交接仍须 DR-07 |

### 原型导航与原产品整合

不新增顶级菜单或平行的“专业工具”菜单。复用：
- 入口 A：业务过程 → 现有逻辑工作区 → 选定逻辑快照 → “继续 DWD”。
- 入口 B：已有 DWD 物理模型详情 → 现有 `LogicalPhysicalEvidencePanel` → 选择确切逻辑版本 → “编辑正式映射”。
- 返回：物理模型发布历史能重开相同 `logicalVersionId + mappingRevisionId + physicalVersionId`，不因当前草稿变化而替换历史。
- DataDev：设计发布后仅说明“继续数据开发”的上下文和受权手动路径；合同未批准前不提供假的“任务已创建”。

UI 保留现有 Ant Design 文案体系和命名：逻辑模型、物理模型、标准字段、DataDev。第三轮故意使用中性灰线框，没有套用 ODS 原型的独立视觉风格。

## 2. 三屏信息层级和交互规则

### 屏 01：逻辑版本选择与 grain 确认

**当前内容：** 当前项目 / 业务过程 / Process 粒度 / 精确逻辑快照及其状态 / 物理模型 / 冻结 ODS 来源。必须明确“快照已保存”和“确认可用于 DWD 发布”不是同一个状态。确认前禁止下一步。

**不允许：** 系统依字段名自动选择其他过程、以当前默认项目外的身份查询、假装 `order_create` 的 grain 已获业务批准。用户发现头明细粒度冲突时，必须回到 Semantic 处理，不用前端单选框强行覆盖来源事实。

### 屏 02：映射编辑（最重要）

**逐列结构：** 目标列名称和类型 → 逻辑属性稳定 ID → 标准字段 ID → ODS 冻结来源列 → 映射方式 → 行级阻断理由。

**冻结规则：** R12-1 只支持 `DIRECT/RENAME`，每个目标列只能一条规则。暂不支持 `DERIVED/JOIN/LOOKUP/AGGREGATE` 正式冻结。标准无权/失效、来源无法读取、逻辑实体不一致、物理摘要漂移都阻断冻结；草稿可以保留有缺口的内容（待产品最终审批）。

**操作反馈：** 保存草稿必须返回 `mappingSetId + newDraftRevision` 才能显示“已保存”，冻结则返回 `mappingRevisionId + evidenceSha` 才能显示“已冻结”。演示中的 R1 是本地标签，不能作服务端证据。编辑后旧预检必须失效；显示明确“重新保存/预检”而不是静默发布旧版本。

**重要代码事实：** `ModelStructureService.save` 全量替换物理列行，`physicalColumnId` **不是**持久的映射主键。正式映射须以目标规范列名、冻结结构 SHA 和列定义摘要定位，且任何内容漂移需要重检。

### 屏 03：精确版本发布

同时展示三个身份及标准证据：LogicalSnapshotRef、MappingRevisionRef、PhysicalModelVersionRef。发布前重检项目/权限、grain、版本、ODS 来源与当前结构、标准状态。**实际发布必须是单个 Modeling 事务，不允许先发布、再补映射。**

审批流要考虑 `ModelPublishApprovalService.submit` 和 `ModelPublishApprovalHandler.onApproved` 两个入口，提交申请冻结全部证据摘要，审批通过时按原 Owner 重新核验；审批过期不能造成旧快照生效。

原型成功提示必须写“本地模拟设计发布”，禁止“已建表”“已同步”“数据已就绪”。DataDev 按下一阶段独立合同执行。

## 3. 可直接点选的六组走查场景

| 场景选项 | 触发步骤 | 可见行为 | 服务端将来的硬拒绝 |
|---|---|---|---|
| 正常路径 | 01→02→03 | 勾选确认、下一步，映射逐列选择后保存草稿、冻结、模拟发布、查看 DataDev 接续说明 | 服务端必须回传合法持久 ID/版本/摘要才算完成；原型不执行 |
| 业务粒度冲突 | 01 | 明确“订单级 vs 订单明细级”并禁用继续 | `PROCESS_GRAIN_MISMATCH` |
| 标准字段缺失 / 停用 | 02 | 数量行高亮并禁用草稿冻结/发布（演示保守阻断） | `STANDARD_REF_INVALID` |
| 物理结构漂移 | 02 | 错误文案要求返回结构核对并重新冻结 | `PHYSICAL_STRUCTURE_STALE` |
| 项目权限撤销 | 01 | 禁止确认和继续，强调不得回显他项目数据 | `PROJECT_DENIED` |
| 审批期间映射变化 | 03 | 直接进入审批后复核阻断态；禁用模拟发布 | `APPROVAL_EVIDENCE_STALE` |

**原型交互评审关注：**（a）用户能否知道“保存草稿”与“冻结/发布”的不同；（b）能否在不阅读复杂系统术语的前提下理解阻断原因；（c）关闭后重开相同模型的版本回链应该在哪里；（d）用户何时才需要去 DataDev。

## 4. 与当前实现的精确边界

| 主张 | 当前代码事实 | R12 未来才实现 |
|---|---|---|
| 逻辑模型可保存快照 | `LogicalDraftService.freezeDraft` 写入状态为 DRAFT 的逻辑版本 | 独立人工设计确认、审批和对应的不可变证据 |
| 逻辑到物理可预览 | `LogicalPhysicalHandoffReviewService.validate` 返回 `REVIEWABLE_ONLY`，不保存 | MappingSet 草稿、CAS、MappingRevision 冻结 |
| 物理结构可发布版本 | `ModelVersionService.publishLocked` 只按结构语义比较复用 | 联合 Logical+Mapping+Standard 发布身份与 Binding 原子落地 |
| 物理可进入 DataDev | `PhysicalDataDevHandoffReadinessService` 为只读，并返回 `NOT_AUTHORIZED_FOR_TASK_CREATION` | 由 DataDev 拥有真实 TaskRevision 创建与回读 |
| ODS 支撑来源 | PR #575 的实库 J1-B 尚延后验收 | 非 ODS 的真实源数据、关联版本需后续准确验证，不预填 PASS |

## 5. 前端/接口/状态的低保真验收准则

- **信息架构**：三个步骤可理解，只有一个主 CTA；用户从已有逻辑页或物理页接续，不重复输入相同 ID。
- **边界分明**：草稿 / 确认 / 冻结 / 设计发布 / 部署 / 任务发布 / 运行不是同一状态，不出现夸大的成功文案。
- **一致性**：切换项目、模型、逻辑快照，清空旧映射选择与过期校验；异步请求必须绑定 `project+model+version+requestEpoch`，旧请求不能污染新视图。
- **字段身份**：同名不同来源/标准不自动生效；可见每列缺口与修复入口；保存后返回真实 revision 再标“已保存”。
- **返回与恢复**：保留“回业务过程/逻辑模型”“回物理工作台”“继续 DataDev”，但未授权或接口不存在时不得给一个可执行的假按钮。
- **反向场景**：至少覆盖本文件 6 个演示场景，以及第三方旧版本、CAS 冲突、缺失 StdField、审批漂移等 R12 第二轮 18 项服务端测试。
- **可访问性**：可用键盘操作原生按钮/选择/复选框；在窄屏放置横向字段表滚动，不隐去阻断原因。
- **没有隐藏写入**：原型不能访问 backend/API/storage，重置本地状态会清除所有演示成果。正式页面需授权接口和审计。

## 6. 评审结论 / 未决选择

第三轮初稿已经交付一个可操作的原型，但尚未经过用户签收，也不等于高保真 UI、生产代码或正式领域规则审批。

建议决议：
1. **UX-A**：三个主步骤保持；“保存映射草稿”和“冻结映射”必须区别，但同在第二屏，以免新增页面跳转。
2. **UX-B**：优先以**已有 DWD 物理模型**为工作台根；来自逻辑页的深链只作提示，必须重新解析当前项目/权限和确切逻辑 ID，禁止同名自动绑定。
3. **UX-C**：列映射保留专业表格；初版仅 DIRECT/RENAME，禁止复杂表达式低保真伪装成已执行。
4. **UX-D**：发布后一屏回读三版本身份与“未部署/未创建任务”状态；DataDev 接续为独立动作。
5. **UX-E**：所有负例要有明确修复方向；业务粒度冲突回 Semantic，标准缺失回标准字段库，来源漂移回 ODS/结构检查，审批过期必须重新提交。

### 后续顺序

用户走查/审核这份低保真原型 → 按意见修订并产品签署 `UX-GATE-LOWFI` → 高保真、前端合同审核 → #556 / PD-010 业务发布门禁正式批准后，R12-1 单 PR 交付实际 MappingSet/原子发布/UI 回读。

**当前关口：`UX-GATE-LOWFI = REVIEW_PENDING`；`DR-06 = DECISION_REQUIRED`、`PD-010 = PROPOSED`、`DR-07 = DECISION_REQUIRED`。**
