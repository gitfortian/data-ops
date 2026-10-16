# R12 第四轮｜DWD 高保真交互与前后端合同审查（v1）

> 日期：2026-10-16  
> 状态：**DESIGN_REVIEW_PENDING / NO_PRODUCTION_IMPLEMENTATION**。这是实施级**候选**合同，不是现有 API 的文档。  
> 关联 [R12 #576](https://github.com/gitfortian/data-ops/issues/576)、[DR-06 #556](https://github.com/gitfortian/data-ops/issues/556)、[DR-07 #557](https://github.com/gitfortian/data-ops/issues/557)、PD-010（仍 PROPOSED）。  
> 对照版本：data-ops 当前 main 的 Modeling Workspace、LogicalWorkspace、\`LogicalPhysicalEvidencePanel\`、\`ModelVersionService\`、\`ModelPublishApprovalService/Handler\`。  
> 演示文件：[高保真 HTML](prototypes/R12-DWD-physical-workbench-hifi-v1.html)；保留 [低保真 v1](prototypes/R12-DWD-physical-workbench-lowfi-v1.html)。HTML 为**独立、无 API、无写入**交互演示，不是已安装页面或真环境验收记录。

## 1. 产品范围与落点

**R12-1 最短业务成果**：真实授权用户从当前交易过程/逻辑设计进入现有 DWD 物理模型，明确事实粒度、字段标准和 ODS 冻结来源；保存正式 MappingSet 草稿、冻结 MappingRevision；完成逻辑/映射/物理发布版本的原子绑定，刷新、重新打开可回读同一组不可变事实。**不自动部署 DDL、不创建 DataDev TaskRevision、不运行作业**。

### 必须融入的既有组件
- \`src/pages/modeling/index.tsx\`：现有物理模型列表/目录作为入口，无新顶级菜单。
- \`src/pages/modeling/logical.tsx\`：业务过程与逻辑设计快照发起进入 DWD；所有 URL 参数只作定位提示，不可直接信任。
- \`src/pages/modeling/components/LogicalPhysicalEvidencePanel.tsx\`：已有**只读**逻辑→物理预检，改造时可成为正式映射的入口/只读对照，但不可将旧 \`REVIEWABLE_ONLY\` 的结果误当持久 MappingSet。
- \`src/pages/modeling/components/PhysicalStandardCoveragePanel.tsx\` 与 \`PhysicalDataDevHandoffPanel.tsx\`：分别继续担当当前标准缺口和 DataDev 只读就绪证据；不复制为另一个 Owner。
- 在**现有物理模型详情下的局部 Tab/Panel**完成三步，而不是新独立页、二级导航或大型全屏向导。原型中的左侧导航是站点布局的视觉示意，研发时应复用真实 \`SiteLayout\`。

### 视觉合同（按真实前端代码）
- 现有 \`SiteLayout\` 的商业 UI token：\`colorPrimary='#292c35'\`、\`colorLink='#344054'\`、\`borderRadius=8\`、\`controlHeight=36\`，文本主要 \`#242731\`、辅助 \`#667085\`。
- 使用 Ant Design 的 \`Card\` / \`Steps\` / \`Alert\` / \`Table size='small'\` / \`Tag\` / \`Modal\` / \`Button\`，不能直接把静态 HTML 作为组件注入生产。
- 一个主 CTA：第一步“确认依据”；第二步先“保存草稿”，保存有效后“冻结映射”；第三步“发布设计”并二次确认，状态真相以服务端回读为准。
- 目标字段应使用可滚动表格，显示：物理列、逻辑属性、标准字段、来源 ODS 冻结版本列、规则类型、行级问题；标准缺口和业务冲突不能藏在总分/Popover 中。
- 展示可区分状态：\`DRAFT_SNAPSHOT\`、\`DESIGN_CONFIRMED\`、\`MAPPING_DRAFT_SAVED\`、\`MAPPING_FROZEN\`、\`DESIGN_PUBLISHED\`、\`DATADEV_NOT_CREATED\`、\`DEPLOYMENT_UNVERIFIED\`。不要显示一个笼统的“已完成”。

## 2. 用户操作与服务端协议（建议 API，尚不存在）

所有接口仅在现有 Modeling Owner 路由下扩展；所有写入带当前 Project Scope、\`modeling:update\`，读由 \`modeling:read\` 控制；关联的 Semantic 和 ODS 版本还须 Owner 鉴权及现时状态检查。客户端不允许提交 \`projectId\` 覆盖后端主体的项目。

### 步骤 1：选定并确认逻辑依据

| 命令 / 查询 | 候选 URL | 必需参数 / 结果 |
|---|---|---|
| 读取已冻结逻辑设计 | **复用现有** \`GET /api/v1/modeling/logical-models/{logicalId}/versions/{no}\` | 返回原 DRAFT snapshot；逻辑 versionNo **不等于审批状态** |
| 读取粒度与标准诊断 | **复用现有** \`GET .../semantic-review\` | 仅当前 Semantic 授权和状态的只读报告，不能凭此发布 |
| 物理结构与映射候选 | **复用现有** \`GET .../physical-preview?physicalModelId=...\` | 仅候选规则、逻辑/物理摘要；检查严格 scoped physical ID |
| 建立逻辑快照设计确认 | **拟新增** \`POST /logical-models/{id}/versions/{no}/design-confirmations\` | \`snapshotSha256\`、\`processGrainDigest\`、\`requestKey\`、\`expectedStatus=DRAFT\`；回 \`confirmationId/sha/confirmedBy/at\` |

**独立确认记录**必须绑定精确逻辑 versionId、semanticProcessId、grain digest、actor/time。当前过程仍是订单头而逻辑事实目标是订单明细时，后端拒绝，不靠前端“确认勾选”强行放行。审批机制若要复用统一 Approval Owner，需要 DR-06 正式决策，不在第一 PR 既造自审批又绕过审批。

### 步骤 2：保存 MappingSet → 验证 → 冻结 MappingRevision

| 行为 | 拟新增接口 | 约束及返回 |
|---|---|---|
| 创建或精确获取 | \`POST /models/{modelId}/logical-mapping\` 或 \`GET .../logical-mapping\` | \`logicalVersionId\`、\`confirmationId\`、\`expectedStructureSha256\`、\`requestKey\`；幂等获取**同身份** MappingSet；不自动取“最新”替换 |
| CAS 保存草稿 | \`PUT /models/{modelId}/logical-mapping\` | \`mappingSetId + expectedDraftRevision + expectedPhysicalSha + rules[]\`；409 不丢他人的数据 |
| 实时预检 | \`POST /models/{modelId}/logical-mapping/validate\` | \`READY_FOR_FREEZE/BLOCKED\`、每列机器错误码、当前 \`evidenceSha256\`；**不写发布状态** |
| 冻结映射 | \`POST /models/{modelId}/logical-mapping/revisions\` | \`mappingSetId + expectedDraftRevision + expectedEvidenceSha256 + requestKey\`；回不可变 \`mappingRevisionId/contentSha256/frozenAt\` |

建议保存 DTO（字段用于后端合同讨论，**不是已实现接口**）：
\`\`\`json
{
  "mappingSetId": 301,
  "logicalVersionId": 8102,
  "logicalSnapshotSha256": "<sha256>",
  "physicalModelId": 201,
  "expectedDraftRevision": 4,
  "expectedPhysicalStructureSha256": "<sha256>",
  "rules": [{
    "targetColumnKey": "quantity",
    "targetColumnSpecSha256": "<sha256>",
    "logicalAttributeId": 504,
    "stdFieldId": 504,
    "mappingKind": "RENAME",
    "source": {
      "odsModelId": 701,
      "odsPhysicalVersionId": 7003,
      "sourceColumnKey": "qty",
      "sourceStructureSha256": "<sha256>"
    }
  }]
}
\`\`\`
- ID 示例只是结构样例，不能由客户端臆造实际实体。
- \`physicalColumnId\` **不能**做持久稳定主键：当前 \`ModelStructureService.save\` 会全量 replaceColumns。暂以规范列名 + 当前结构 SHA + 冻结列定义摘要作为定位；源 ODS 同样按精确物理版本身份引用。
- \`DIRECT/RENAME\` 的发布定义是简单映射语义，**不代表 DataDev 已拥有可执行数据转换**。实际字段类型兼容、sourceColumn 精确身份、标准约束必须在 Owner 后端验证。
- 未获批准的 \`JOIN/DERIVED/AGGREGATE/TECHNICAL\` 即使客户端手工构造请求也要在后端拒绝正式冻结/发布。
- 草稿保存后版本号只取服务端响应；冻结后新草稿编辑不能悄悄覆盖旧 MappingRevision。

### 步骤 3：受控发布 / 审批通过后发布 / 历史回读

必须同时覆盖当前两个既有入口：
1. \`POST /api/v1/modeling/models/{id}/publish\`（直接发布）
2. \`POST /api/v1/modeling/models/{id}/publish-approval\` → \`ModelPublishApprovalHandler.onApproved\` → \`ModelVersionService.publishApproved\`

**P0**：共同 \`publishLocked\` / 统一发布编排，在**同一个 \`yakBusinessTransactionManager\` 事务**中锁定当前物理根、重检精确逻辑快照与确认、MappingRevision、标准字段及数据标准、ODS 来源版本、物理结构 digest，再写 PhysicalVersion、Binding 和发布指针。审批申请必须冻结完整 binding digest，审批生效前重查，拒绝过期申请。

发布身份（DWD/DIM 新语义）：
\`\`\`text
publishIdentity = sha256(
    canonicalPhysicalStructure
    + logicalSnapshotSha
    + mappingRevisionContentSha
    + standardEvidenceSha
    + grainDigest
    + modelRole
)
\`\`\`
- **结构相同、映射不同 = 新的物理发布版本**。不能沿用现有结构等值复用行为。
- **完全同指纹 + 同 requestKey** 重试返回原 version ID；\`requestKey\` 相同但内容不同返回 409。
- 老版本无 Binding 显示 \`UNBOUND_LEGACY\`，不能为了绿灯自动回填假逻辑映射。ODS 发布身份/迁移独立，不因本合同发生静默变化。
- \`rollback\` 只把结构恢复为草稿；重新发布时必须重新明确逻辑/映射/标准证据，不自动冒充先前数据部署成功。

拟新增回读 \`GET /api/v1/modeling/models/{id}/versions/{no}/logical-mapping\` 返回**不可变**版本绑定三元组 + 冻结摘要，另提供 \`currentValidation\`（独立当前时点证据，不回写历史）。历史列表/详情增加入口，不在未创建 Binding 的 legacy 行上展示“已关联”。

## 3. 后端变更影响点与迁移策略

| 当前文件/模块 | 需要扩展（正式开工前须 DR-06/PD-010 签署） |
|---|---|
| \`logical/LogicalDraftService\` + \`controller/v1/LogicalDraftController\` | 独立 LogicalDesignConfirmation Service/DTO/授权；保留现有 DRAFT 快照 |
| \`logical/LogicalPhysicalHandoffReviewService\` | 保留只读诊断；新 MappingSet 应用服务消费经过校验的证据而不是复用 review 成功码当授权 |
| \`structure/ModelStructureService\` | 公共 canonical 列/表结构指纹合同；模型内行级版本变化时映射失效 |
| \`version/ModelVersionService\` | DWD/DIM 发布统一事务 gate、联合发布指纹、物理版本精确 Binding、幂等返回与 legacy 共存 |
| \`approval/ModelPublishApprovalService\` / \`Handler\` | 申请 payload 锁定联合作用域；审批回调重检并拒绝过期 |
| \`standard/PhysicalStandardCoverageInspectionService\` | 当前只读覆盖扩展为正式发布审计证据；生效逻辑需 PD-010 授权，不私自允许技术列例外 |
| \`version/PhysicalDataDevHandoffReadinessService\` | 返回精确 Binding 供 DR-07 未来跨域使用；依然只读，绝不借此创建任务 |

迁移建议的四张表：\`yak_modeling_logical_design_confirmation\`、\`yak_modeling_mapping_set\`、\`yak_modeling_mapping_revision\`、\`yak_modeling_physical_version_mapping\`，见 [R12 第二轮 ER](https://github.com/gitfortian/data-ops/issues/576#issuecomment-6211536658)。**迁移号不得在此设计稿中锁死**：当前 main 仅到 Modeling V4，但未合并的 ODS #575 占用 Modeling V5。R12 正式 PR 必须在创建迁移前与 ODS 做分支协调，MySQL / PostgreSQL 同时出等价 DDL；不得直接改历史 V1 基线或历史快照。

## 4. UI 异步一致性、权限和异常恢复

**局部状态最少须携带上下文签名：** \`(projectId, physicalModelId, logicalModelId, logicalVersionId, draftRevision, requestEpoch)\`。切换项目/模型/逻辑版本、关闭页面或撤权时立即清空旧 evidence；异步旧响应不得复绘或触发保存。直接由 URL 提供的 ID 只作定位提示，必须先由 Owner 解析同项目且当前有权。

### 后端可机读错误 → 用户修复
| 代码（候选） | UI 展示与恢复 |
|---|---|
| \`MAPPING_PROJECT_DENIED\` / 无授权 | 清空字段/证据；跳回项目工作空间或申请权限，不回显旧项目记录 |
| \`PROCESS_GRAIN_MISMATCH\` | 留在 01，链接回真实业务过程/粒度治理，不允许强制跳过 |
| \`STANDARD_REF_INVALID\` | 定位 02 的精确字段，提供标准库修复入口，不把无标准显示为绿色 |
| \`SOURCE_ODS_VERSION_DRIFT\` | 刷新 ODS 版本/来源列证据、逐列重新确认，不自动取最新 |
| \`PHYSICAL_STRUCTURE_STALE\` | 要求重载物理结构、比较差异，旧冻结 Revision 不被静默用于新结构 |
| \`MAPPING_CAS_CONFLICT\` | 展示本地未保存修改与远端最新版；用户选择对比/重载，不能自动覆盖 |
| \`MAPPING_RULE_UNSUPPORTED\` | 指明 JOIN/DERIVED 等不支持 R12-1 发布，草稿保留待专项评审 |
| \`APPROVAL_EVIDENCE_STALE\` | 保持未发布，说明重新申请审批、不能静默批准 |
| \`PUBLISH_BINDING_CONFLICT\` | 按精确指纹核对重复发布；不得返回虚假新版本 |

无权、缺失、Owner 超时、当前标准不可访问时**fail closed**，不从缓存或本地 DOM 构造已批准的版本身份。错误必须可恢复到实际治理位置，而不是只有“操作失败请联系管理员”。

## 5. 测试门禁（面向一个可见的 R12-1 PR）

**前端测试**
- 3 步可达，只有合法保存草稿后才能冻结；冻结之后才能尝试发布。
- 字段同名不同 \`stdFieldId\` 不自动匹配；复杂 JOIN/DERIVED 不允许发布；空字段 / source 身份缺失 / 明细粒度冲突为醒目的列级/步骤级 blocker。
- API 保存/冻结/发布失败后不把 UI 标“成功”；不同项目、不同物理模型或异步返回乱序不得污染当前字段表。
- CAS 409 后不能悄悄覆盖，并支持返回修改、对比状态。
- 显示真实 DesignPublished / Deployed / TaskCreated / Execution 状态区分；旧历史版本的 Binding 不受最新草稿修改影响。

**后端测试**
- R12 第二轮 [18 组核心正反向](https://github.com/gitfortian/data-ops/issues/576#issuecomment-6211536658)：Project/授权、逻辑快照失效、粒度冲突、列行 ID 变化、StdField 校验、Owner 超时、CAS 并发、表达式拒绝、OOD/ODS 来源漂移、同结构不同 MappingRevision 新版本、发布幂等、审批过期拒绝、回滚与 legacy 只读。
- 两种数据库方言迁移唯一键和事务原子性：故意在 Binding insert 失败时证明物理版本插入和发布指针整体回滚。
- Security/Architecture：Controller → Application Facade → Owner SPI，跨模块禁止绕过 DataDev/Approval/Semantic；权限撤销后不能再取历史敏感证据。
- **不要把 Product Guard 双绿等价于 MySQL/PostgreSQL 实环境验收**。不可访问的外部环境明确 \`DEFERRED_REAL_ENV\`。

## 6. 高保真 Review / Gate

### 设计需要审核的五项具体取舍
- **H1** 继续采用 3 步局部工作区；第 2 步用一张专业映射表 + “保存草稿 / 冻结映射”两段动作，不新增弹窗链。
- **H2** 配色、表格密度、侧栏、按钮与现有 data-ops 统一；不创建新菜单；不会把独立原型 HTML 直接用于 React。
- **H3** 第 3 步发布采用一次明确确认弹窗，显示三种精确证据并明确不做 DDL/ETL；正式版本在右侧状态卡回读。
- **H4** 异常按错误发生步骤停留，能直达上游修复入口；不让系统悄悄默认使用最新模型或标准替换旧引用。
- **H5** R12-1 先交付单来源 DWD 的 DIRECT/RENAME 映射和不可变发布 Binding；DIM/SCD、Join/DERIVED、DWS/ADS、DataDev 写入均在独立正式合同中演进。

当前状态：\`HIFI-UX-GATE=REVIEW_PENDING\`、\`DR-06=DECISION_REQUIRED\`、\`PD-010=PROPOSED\`、\`DR-07=DECISION_REQUIRED\`。高保真签收后，先批准核心发布合同再创建 R12-1 的**一个纵向功能 PR**。不得因为本 PR 是设计文档而声称实际 MappingSet API、发布门禁或 DataDev 已实现。
