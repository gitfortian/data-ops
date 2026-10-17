# R12-2｜DWD 发布设计 → DataDev 持久任务与真实执行：DR-07 Owner 合同评审稿

Status: **REVIEW_REQUIRED / NO_WRITE_AUTHORIZATION / NOT_IMPLEMENTED**
Baseline: `main@4b8b1fc86524aa4e8304ff39a81b0e6cd83b394d` (2026-10-17)  
Related: [DR-07 #557](https://github.com/gitfortian/data-ops/issues/557) · [R12 #576](https://github.com/gitfortian/data-ops/issues/576) · [R12-1 #583](https://github.com/gitfortian/data-ops/pull/583) · [PD-010](../decisions/PD-010-guided-business-to-data-journey.md)

> 此文是**待审批的具体研发合同**，不是已批准命令、不是已实现 API、更不代表 SQL 已执行。用户同意先完成 R12-2 再建设 DWS/ADS；这并不自动批准 #557 当前禁止的跨域生产写入。此前 R12-1 的设计发布结果不得升级显示为“已加工”。

## 1. 面向用户的最小完整成果（一个纵向 PR）

给已有 `order_create` 业务过程及 `trade_order_detail` 来源的 DWD 事实模型完成一次**人工 SQL 交付**：在模型工作台选择精确物理设计 Vn → DataDev 原工作台读取并校验 DWD 冻结映射及物理字段 → 人工选择/新建本项目 SQL Node、确认目标环境与写入策略 → 保存 SQL Draft、正式发布 `TaskRevision`、持久绑定模型版本 → **仅对精确已发布任务修订**人工执行 → 从 Runtime 回读真实 `ExecutionId` → 查询目标表并对账业务键/行数/异常；失败和未知结果均可恢复且不得盲目重放。

验收必须分别显示以下独立事实，任何一项都不能推出后一项：

| 事实 | 权威 Owner | 必需凭据 |
|---|---|---|
| `DESIGN_PUBLISHED_NOT_EXECUTED` | Modeling | immutable PhysicalModelVersion + MappingRevision；历史回读 |
| `TASK_DRAFT_SAVED` | DataDev | NodeId + DraftRevision（可变） |
| `TASK_REVISION_PUBLISHED` | DataDev | TaskRevisionId + RevisionNo + definition checksum + 经核准的模型版本绑定 |
| `EXECUTION_{RUNNING/SUCCEEDED/FAILED/UNKNOWN}` | Runtime/DataDev | ExecutionHistoryId、runtimeExecutionId、执行的 TaskRevisionId/绑定版本 |
| `DATA_RECONCILED` | QA/目标数据查询原域 | 明细业务键、目标记录与源表核对，独立对账回执 |

**不做**：AI 自动推 SQL、自动猜去重键/粒度、复杂 JOIN/DERIVED/LOOKUP、强制部署 DDL、自动调度、冒充质量或消费通过、在 Modeling 域直接插入 DataDev 表。

## 2. 对当前 main 的源码核对（非推测）

| 现有事实 | 源码 | 对 R12-2 的含义 |
|---|---|---|
| 已有 MappingSet、MappingRevision 与 DWD 物理发布版本的不可变历史关系 | `DwdMappingPublicationService.history` + `ModelVersionService` | 可以取精确 DWD 版本；历史 `LEGACY_UNBOUND` 必须阻断 |
| 当前只读预检 | `PhysicalDataDevHandoffReadinessService`、`PhysicalDataDevHandoffPanel.tsx` | `NOT_AUTHORIZED_FOR_TASK_CREATION`，导航 hint 不是绑定 |
| DataDev Node 创建与 Project Root | `DevelopmentNodeService.create` / `DevelopmentNode.requireProjectId` | 可复用现有 SQL Node，而非创建一套新任务体系 |
| 已有 CAS Draft、发布校验、不可变 Revision | `DevelopmentTaskService.saveDraft/publish` | `baseRevision` / `draftRevision` 可做防并发；发布须新鲜重检 |
| 当前手动 SQL 执行 | `DevelopmentTaskController /{nodeId}/run` → `DevelopmentTaskRunService.submit` | **执行的是提交的当前编辑器定义**；它构造 draft snapshot，不能以此证明已发布 `TaskRevision` 被执行 |
| 当前 DataDev 执行历史 | `DevelopmentTaskExecutionController` 的分页/详情/取消/重试 | 执行历史未固化对应 `PhysicalModelVersionRef + TaskRevisionRef`；单看 `SUCCESS` 不证明 DWD 已加工 |
| DWD 历史字段 | `DwdMappingPublicationService.history` 返回 MappingRevision、逻辑/物理 SHA | `PhysicalDataDevHandoffReadiness.currentStandardEvidenceSha256` 是**当前标准检查**，不能假装它是发布时冻结的历史标准 SHA |

以上结论均基于源码接口，而不是原型或旧 PR 的描述。R12-1 已合并不等于 DR-07 生产授权。

## 3. Owner 职责边界（候选决议 DR-07 D1）

- **Modeling**：提供严格项目隔离的 `PublishedDwdDesignEvidence` 只读查询，返回 `modelId, modelVersionId, versionNo, layerCode, status, logicalModelId, logicalVersionNo, logicalSnapshotSha256, mappingSetId, mappingRevisionId, mappingContentSha256, physicalStructureSha256, columns/standardFieldIds`。这些字段均需从原域历史快照获取并交叉核验；缺失即拒绝。不要把模型名称/URL 当主键。当前标准状态单独返回 `currentStandardEvidenceSha256` 与核验时间，不伪造历史证明。
- **DataDev**：唯一负责 SQL Node/Draft/TaskRevision，以及新的 `ModelVersion ↔ TaskRevision` 绑定记录、幂等键、目标环境、发布与执行入口。不能直接绕过 `DevelopmentTaskService` 和 `TaskCatalogService` 写任务表。
- **Runtime**：执行并回传真实 ID/状态，区分提交失败、运行失败、未知提交结果。任务版本必须随执行快照固定，不可用当前编辑器替代。
- **Semantic/Security**：标准字段合法性、业务过程/跨项目授权；权限在每次 precheck、bind、publish、run 重新判断。
- **Quality/Consumption**：数据对账、消费状态独立，不由 DataDev 模拟成功。

建议在公共 SPI 定义 `PublishedDwdDesignEvidenceQuery`（纯只读接口及 DTO），由 Modeling 实现，DataDev 依赖接口；**禁止** DataDev 直接依赖 Modeling 内部 DAO，防止循环依赖。

## 4. 不可变身份与 DataDev 唯一绑定（候选决议 D2）

`PublishedModelRef = (projectId, modelId, modelVersionId, versionNo, mappingRevisionId, mappingContentSha256, logicalSnapshotSha256, physicalStructureSha256)`

`PublishedTaskRef = (projectId, nodeId, taskRevisionId, taskRevisionNo, taskDefinitionChecksum)`

绑定摘要 `bindingDigest = SHA256(canonical(PublishedModelRef + PublishedTaskRef + target identity + write mode + normalized statement/config digest))`。规范化使用稳定字段排序、编码、大小写与空值策略；版本身份中的 long ID 在前端以字符串处理，禁止 JS 精度损失。

建议新 DataDev 自有持久表 `yak_development_model_task_binding`（V 迁移号先由 Flyway 现状确定，**不得**占用 ODS V5 / R12 V6）：

| 字段或组合 | 约束 |
|---|---|
| `id, project_id, node_id, task_revision_id, task_revision_no` | DataDev 项目作用域；真实发布任务 ID，不使用“尚未发布”的 DraftId |
| `model_id, model_version_id, model_version_no, mapping_revision_id` | 来自 Modeling 原域只读证据；写事务时重新核实 |
| `mapping_content_sha256, logical_snapshot_sha256, physical_structure_sha256` | 64 位十六进制；不可变绑定快照 |
| `observed_standard_evidence_sha256, observed_at` | 标为**绑定时当前标准观察**；不可冒充历史冻结标准 |
| `target_connection_id, target_database, target_schema, target_table, write_mode` | 用户明确确认、鉴权并作为冲突判定身份 |
| `task_definition_sha256, binding_digest, idempotency_key` | 绑定不可变，幂等键仅同 Project/调用者/规范请求可重复回读 |
| `created_by, created_at, revision_status` | 审计；旧绑定保留，不随新 Draft 覆盖 |

迁移必须包含 `project_id + idempotency_key` 唯一、`project_id + node_id + task_revision_id` 精确一致性/重试、项目隔离索引与避免同任务修订多重不同业务来源的业务冲突校验。不要仅通过数据表唯一约束来授权；FK 只引用 DataDev 同域可达表，跨域引用须用 Owner 重检和服务端规则。

**严禁**：用同一物理结构 SHA 复用不同 MappingRevision；用新 Draft 覆盖已发布 TaskRevision 的旧绑定；把 currentStandards 指纹标记为旧模型的已冻结标准指纹。

## 5. 新增 API 提案（未实现，签收后研发；候选决议 D3）

| 操作 | 新增端点（提案） | 所有者 / 语义 |
|---|---|---|
| 精确预检 | `POST /api/v1/data-development/model-handoffs/precheck` | DataDev 汇总 Modeling + Node + 目标环境 **只读**，返回 `READY_FOR_MANUAL_REVIEW / BLOCKED / EVIDENCE_UNAVAILABLE`，绝不写入 |
| 绑定确认 | `POST /api/v1/data-development/model-handoffs/bind` | DataDev `EDIT+PUBLISH`；提交精确 ModelRef、NodeId、已发布 TaskRevisionId、expectedDraftRevision、幂等键、人工确认信息；事务内重检并持久绑定 |
| 读回绑定 | `GET /api/v1/data-development/model-handoffs/{bindingId}` | 只读精确身份与审核/审计状态 |
| 反向查询 | `GET /api/v1/data-development/model-handoffs?modelId=&modelVersionId=` | 项目内从 DWD Vn 定位已绑定 TaskRevision，不能使用表名模糊推断 |
| 执行冻结修订 | `POST /api/v1/data-development/nodes/{nodeId}/revisions/{revisionNo}/run` | DataDev `EXECUTE`；请求 bindingId + idempotencyKey + 显式确认/目标；从**持久 TaskRevision** 取定义，不使用编辑器请求体 |
| 执行回读 | `GET /api/v1/data-development/model-handoffs/{bindingId}/executions/{executionHistoryId}` | DataDev/Runtime 返回精确 TaskRevision + Execution 与状态，无目标对账成功臆测 |

写命令请求（示意，不是可用 API）：

```json
{
  "modelRef": {
    "modelId": "701",
    "modelVersionId": "830",
    "versionNo": 3,
    "mappingRevisionId": "920",
    "mappingContentSha256": "<64-char SHA>",
    "logicalSnapshotSha256": "<64-char SHA>",
    "physicalStructureSha256": "<64-char SHA>"
  },
  "nodeId": "110",
  "taskRevisionId": "120",
  "expectedDraftRevision": 4,
  "target": {
    "connectionId": "22",
    "database": "warehouse",
    "schema": "mart",
    "table": "dwd_order_detail",
    "writeMode": "INSERT"
  },
  "idempotencyKey": "client-generated-uuid",
  "confirmed": true
}
```

所有字符串 ID 由当前授权项目验证。幂等重复返回原绑定，语义不同返回 `409 CONFLICT`。`UNKNOWN` 不允许重新自动发起 SQL，而应先按幂等键/ExecutionId 查回执。编辑器 `POST /nodes/{nodeId}/run` 继续用于现有手动调试，但**绝不可计为绑定任务的正式发布版本执行**。

### 建议事务序列

1. read precheck → 获取正式 Modeling 历史证据和当前 Semantic 标准状态，以及 DataDev node/目标候选；预检只提示，绝不授权。
2. 用户人工编辑、保存 SQL Draft（CAS）；DataDev 正常 publish 校验，并得真实 TaskRevision。
3. **绑定**命令锁定当前项目的 DataDev Node/TaskRevision，重读精确 Modeling/当前标准/目标权限；与草稿期望修订/冻结 SQL checksum 比对。
4. DataDev 同一 Business Transaction 写不可变绑定、唯一幂等回执、审计；事务失败原子回滚。跨域查询只读，不能声称整个多库事务原子。
5. 用户显式选择绑定并运行**持久 TaskRevision**；运行前复核权限、目标、绑定与模型有效性，交 Runtime 幂等执行，历史记录固定绑定及版本 ID。
6. 超时先查精确 ExecutionHistoryId/RuntimeExecutionId 及请求幂等结果，未知不得重复目标写入；终态仅由 Runtime 原域提供。
7. 目标数据回读及质量核对独立验收，不将“运行成功”自动翻译成“数据准确”。

## 6. 人工体验合同（候选决议 D4）

不新增顶级菜单、不另建一套数据开发页面：

`DWD 物理模型详情 → 查看冻结版本与映射历史 → 交接预检 → DataDev（本项目 SQL Node） → 人工审阅来源/目标/粒度/SQL/写入策略 → 保存草稿 → 发布修订 → 绑定版本 → 执行 → 查看运行历史 → 查看目标数据对账 → 返回模型`

主 CTA 分阶段只有一个：先“核对并继续”，再“保存 SQL”，再“发布修订”，再“关联并确认运行”；阻断时给出有权对象与可返回入口。无有权 SQL Node 时允许用户自主新建，**不自动创造**。用户重新打开必须从服务端查询任务和模型 ID，不能从导航 query 推断既有绑定。

状态需分别展示 `DESIGN_PUBLISHED`、`TASK_DRAFT`、`TASK_REVISION_PUBLISHED`、`TASK_BOUND`、`RUNNING`、`SUCCESS/FAILED/UNKNOWN`、`DATA_RECONCILED`；没有事实的状态为 `NOT_VERIFIED`，不把 `[]` 解释为系统无记录。

## 7. 必须通过的自动化与真实环境验收

| Case | 场景 / 不可回避的失败行为 |
|---|---|
| R12-2-01 | DWD `DESIGN_PUBLISHED_NOT_EXECUTED` 精确发布版本 + 冻结映射 + 当前合法标准 → 有权只读预检 |
| R12-2-02 | `LEGACY_UNBOUND`、损坏 meta、MappingRevision/SHA 不一致、历史指针错误 → 拒绝 |
| R12-2-03 | 跨 Project、非 SQL Node、模型已删除/撤权、无目标连接/权限 → 拒绝，不写绑定 |
| R12-2-04 | 保存 SQL Draft 使用 CAS；两个编辑者竞争仅一人成功，旧草稿不得发布 |
| R12-2-05 | 同一请求幂等重试只回读一次绑定，不追加第二 TaskRevision/执行 |
| R12-2-06 | 同结构不同 MappingRevision、同任务修订不同 SQL/目标/写入策略 → 明确冲突，不偷偷覆盖 |
| R12-2-07 | 绑定事务注入失败（索引、插入、审计）→ MySQL/PG 数据库无半绑定；注入前后历史任务版本不变 |
| R12-2-08 | 用户编辑器改 SQL 后提交冻结修订运行，必须使用原 TaskRevision 定义；记录可验证 checksum |
| R12-2-09 | 提交/执行超时、Runtime UNKNOWN、重试、回读 → 不盲重放；ExecutionId 精确查回 |
| R12-2-10 | 状态隔离：已发布设计 ≠ 有任务；有任务 ≠ 已执行；执行成功 ≠ 数据对账 |
| R12-2-11 | 使用确认业务键/粒度/时区/取消状态/金额口径与源表逐条核对订单明细目标；失败有差异证据 |
| R12-2-12 | Frontend/Jest/type-baseline/build、Backend、Architecture、Product Guard、Migration（MySQL+PG），同一 HEAD 全绿 |
| R12-2-13 | 用户关闭/刷新、切项目、权限撤销、标准失效，UI 不复用旧成功结果 |
| R12-2-14 | 实库黄金故事以真实 `TaskRevisionId/ExecutionId`、目标连接与核对报告签收；无环境证据 = `DEFERRED_REAL_ENV` |

## 8. DWS/ADS 后续复用门槛，不抢跑

- **R12-3（必要时 DIM）**：真实可信的业务维度与 SCD 证据；没有可信来源不创建示例维度。
- **R12-4 DWS**：在已发布且可运行的 DWD 上显式选择 `ModelVersionRef`，确认统计时间/时区、GroupKey、唯一订单业务键、状态、金额可加性、late-arrival 重算区间、Metric 原域正式 `MetricVersionRef`；复用 `ModelDeriveService` 现有 AGGREGATE/周期/函数白名单，但不能把其“模型编码.列名”当作 immutable Ref。设计→任务→运行→汇总对账按已验收 DR-07 路径。
- **R12-5 ADS**：先确定明确消费者；优先复用 DWS+Dataset/Data Service。确需物化才创建 ADS，复用 APPLICATION/DWD 可选上游；绑定模型/指标/消费者权限和目标，DataDev 执行、消费 Query/Invoke+Usage 实证。不能强制为了分层而创建 ADS。

## 9. 正式评审决定表（尚未批准）

| 决策 | 推荐 | Owner 签收状态 |
|---|---|---|
| D1 持久绑定归属 | DataDev；Modeling 纯只读事实 SPI | PENDING DataDev/Modeling |
| D2 版本身份、字段标准观察、DB 迁移 | 精确 ModelVersion+MappingRevision+TaskRevision+SQL SHA、DataDev 绑定根 | PENDING DataDev/Modeling/Semantic |
| D3 原子绑定、幂等与执行冻结修订 | DataDev Business Transaction + Runtime 回执；不复用编辑器 run | PENDING DataDev/Runtime/Security |
| D4 用户交互与异常恢复 | 现有 Modeling→DataDev 工作台，手动确认与只读上下文回链 | PENDING Product/UX |
| D5 生产实施授权 | DR-07 改为 ACCEPTED、相关 PD/强标准发布规则明确后才能新增 write endpoints/migration | PENDING Product/Owner |

**准入**：上述 D1–D5 获正式批准并记录审批人/日期/适用范围后，按一个纵向 PR 实施 R12-2，并在 CI 与真实环境里分别收集证据。现阶段不得调用文中拟议写 API，也不得标记实现完成。
