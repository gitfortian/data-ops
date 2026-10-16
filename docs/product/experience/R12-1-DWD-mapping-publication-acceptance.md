# R12-1｜DWD 人工映射、设计发布与历史回读验收合同

Status: IMPLEMENTED_DRAFT / MYSQL_STORAGE_SMOKE_PASS / POSTGRESQL_STORAGE_SMOKE_PASS / SAME_HEAD_COMPLETE_CI_PENDING / BUSINESS_E2E_DEFERRED

Product classification for PR governance: Change Type = PRODUCT; Product Behavior Changed = Yes. Product Impact, Truth & Ownership, and Acceptance sections are maintained in the PR description for the product governance gate.
Related: [#576](https://github.com/gitfortian/data-ops/issues/576) · [PR #578](https://github.com/gitfortian/data-ops/pull/578) · [DR-06 #556](https://github.com/gitfortian/data-ops/issues/556) · PD-010 (PROPOSED)

> 本文记录 **Draft PR 中的实际实现能力与验收门槛**；不代表合并、正式发布、产品门禁签收或真实运行成功。上游 ODS V5 的发布前置依赖见 #575。

## 1. 用户可交付成果

人工在现有逻辑模型工作台选择一个 **冻结逻辑版本** 与项目内 DWD 物理草稿，逐列选择标准字段身份一致的 DIRECT/RENAME，保存 MappingSet、冻结 MappingRevision；在明确确认后，将当前冻结映射和 DWD 物理结构作为 **一个不可变物理设计版本** 发布，回读已发布版本的历史映射证据。

**设计发布不等于**建表、创建 DataDev 任务、执行 SQL、数据入仓、质量验收或消费成功。不存在 Agent 自动决定业务键、明细粒度、JOIN 或代码生成。

### 页面体验（此 PR）

1. 在现有逻辑模型页面打开 DWD 目标，获取逻辑快照、物理结构及标准字段的审阅证据。
2. 人工选择一对一 DIRECT/RENAME；校验通过后才能保存 MappingSet。
3. 点击冻结 MappingRevision；冻结只产生不可变设计证据，不发布。
4. 单独点击“确认发布 DWD 设计版本（不执行任务）”；确认对话框解释没有 SQL/DDL/DataDev 动作。
5. 服务端校验当前项目、DWD 状态、草稿修订、冻结修订、全部映射、逻辑/物理指纹、标准字段身份；同一事务内发布物理 ModelVersion 与精确映射绑定。
6. 前端重新读取精确物理版本下的历史映射，核实版本 ID、修订 ID、内容哈希后才显示“设计已发布”。
7. 重新打开逻辑设计工作台可从服务端恢复 MappingSet 和同草稿修订的冻结版本，仍需人工重新校验；从物理模型详情可选择 Vn 独立查看不可变映射历史。
8. 在现有物理模型工作台可选择“DataDev 交接预检”：DWD 时显示所选物理版本对应的 MappingRevision、冻结逻辑版本及摘要；旧无绑定版本不能冒充可追溯 DWD。这里只允许有版本证据的**只读跳转**，不负责 DataDev 写入。
9. 没有目标物理模型时，可从逻辑版本页面进入同业务过程的现有物理模型工作台，新建 DWD 草稿后再返回审阅；不自动生成或发布物理模型。

### 版本身份（当前实现）

`LogicalVersion(no + snapshotSha) → MappingSet(id + draftRevision) → MappingRevision(id + contentSha) → PhysicalModelVersion(id + versionNo)`

绑定写入 Modeling 原有不可变 `ModelVersion.metaJson`，字段为 `mappingRevisionId`、`mappingSetId`、`logicalModelId`、`logicalVersionNo`、`logicalSnapshotSha256`、`physicalStructureSha256`、`mappingContentSha256`。完整冻结规则保存在独立 MappingRevision 的 append-only 快照中；历史查询不会使用当前 MappingSet 草稿替代历史快照。

**等值幂等规则**：针对显式绑定入口，仅当结构语义相同且精确 MappingRevision 证据相同时，才允许复用最新版；结构相同而映射版本不同，必须生成新的物理设计版本。已发布模型重复提交相同绑定只读回执，不追加历史；要更新绑定需先进入新的草稿编辑流程。

**原子性边界**：DWD 绑定重检与原有 ModelVersion 插入/发布指针更新使用相同 `yakBusinessTransactionManager` 事务。其事务原子性仍需 MySQL/PostgreSQL 实库故障注入验收；目前代码检查/单测不能代替实际数据库证据。

## 2. 接口与状态区分

| 动作 | API | 真相 |
|---|---|---|
| 保存草稿 | `PUT /api/v1/modeling/models/{id}/logical-mapping` | `DRAFT_SAVED_NOT_PUBLISHED` |
| 冻结修订 | `POST /.../logical-mapping/revisions?expectedDraftRevision=...` | `FROZEN_DESIGN_ONLY` |
| 显式设计发布 | `POST /.../logical-mapping/publish-with-mapping?mappingRevisionId=...` | `DESIGN_PUBLISHED_NOT_EXECUTED` |
| 提交映射发布审批 | `POST /.../logical-mapping/publish-approval-with-mapping?mappingRevisionId=...` | 审批中；批准前不发布 |
| 精确历史回读 | `GET /.../logical-mapping/published-versions/{versionNo}` | `LEGACY_UNBOUND` 或 `DESIGN_PUBLISHED_NOT_EXECUTED` |
| 冻结内容回读 | `GET /.../logical-mapping/revisions/{revisionId}` | 原冻结规则，不取当前草稿 |

所有 API 均受现有项目授权和 Modeling 权限保护；保存、冻结、发布、提交审批额外要求 Modeling UPDATE。审批通过后再用最新当前证据重检，且审批 payload 同时冻结物理结构指纹与映射内容摘要；审核内容改变则拒绝执行。

## 3. 不可回避的产品决策与上线边界

- **DR-06 #556 与 PD-010 仍未 ACCEPTED**。本轮使用显式 opt-in 设计发布，未更改旧的普通直接发布、原审批发布与历史兼容行为；**不得宣称非 ODS 统一强制发布门禁已经生效**。
- 尚未完成业务粒度、主键、事件时间、复杂转换、Join 基数的正式冻结及可执行规则合同。DIRECT/RENAME 仅是首批安全范围，不能将未表达的规则默认为通过。
- 审批入口在 Modeling 后端已可接受带 MappingRevision 的 payload；审批中心是否在目标环境配置 MODEL_PUBLISH 流程与实际终态回调仍需验证。
- 依赖 ODS #575 的 V5 顺序迁移后才可正式合并 R12 V6。MySQL 与 PostgreSQL 的**隔离 CI 数据库**已分别通过 V6 迁移、映射集合/冻结版本唯一性及基础事务回滚；这**不是**真实发布事务中故障注入或目标用户环境的完整验收。
- R12-2 DataDev TaskRevision 持久交接、SQL 与真实执行不属于此阶段；DR-07 #557 仍未 ACCEPTED。

## 4. 必须通过的产品级验收

| Case | 操作 | 预期 |
|---|---|---|
| DWD-01 | 人工逐列映射→保存→冻结→显式发布 | 返回真实 MappingSet/Revision/ModelVersion ID，发布指针引用新版本 |
| DWD-02 | 同结构、不同冻结映射版本再发布 | 不能复用旧 ModelVersion；新增不可变版本 |
| DWD-03 | 同修订重复发布 | 只回读同一版，不追加、不重复写 |
| DWD-04 | 冻结后更改物理列名/标准字段或逻辑快照 | 发布拒绝，模型发布指针不前进 |
| DWD-05 | 审批提交后更改结构或映射摘要 | 终态回调拒绝发布，旧版本不受影响 |
| DWD-06 | 传入另一项目、另一模型、旧修订、伪造 SHA | 拒绝，不产生成功回执 |
| DWD-07 | 已发布 V1 → 草稿重改 → 查看 V1 绑定 | V1 返回原冻结规则与摘要，不返回当前草稿 |
| DWD-08 | 历史物理模型没有映射绑定 | 返回 `LEGACY_UNBOUND`，不伪造已绑定证据 |
| DWD-09 | MySQL/PG 注入版本插入、指针更新失败 | 同事务全部回滚，无半发布/悬空绑定 |
| DWD-10 | UI 刷新/越权/冲突/服务器超时 | 不以旧浏览器状态显示假成功；重开从服务端获取真实状态 |
| DWD-11 | MappingRevision 通过但未创建 DataDev 任务 | 始终显示“设计发布/未执行”，不显示“已入仓” |
| DWD-12 | 关闭浏览器后重开已有 MappingSet 和冻结修订 | 真实接口恢复当前草稿与匹配的冻结 ID，用户可载入规则，仍需校验 |
| DWD-13 | 物理 DWD Vn 打开 DataDev 预检 | 展示准确的 LogicalVersion / MappingRevision 身份和摘要；旧版无绑定阻断该只读交接 |
| DWD-14 | 审批中途停用流程或存在未终态申请 | 原有在途审批仍阻断显式直接发布，不发生审批绕过 |
| DWD-15 | 逻辑页没有可复用 DWD 目标 | 引导用户进入同业务过程物理模型工作台新建草稿，而不是卡在选择器 |
| DWD-16 | 发布审批处于 PENDING 时尝试修改/冻结映射 | 服务端拒绝编辑；审批回调仍可在同事务内重检当前已批准快照，不发生审批死锁或绕过 |

完成条件：**同一 HEAD** 的前端、后端、架构、产品守卫绿灯；MySQL/PG 实库迁移及事务故障验证记录单列为 PASS/DEFERRED，不能混为一谈；产品 Owner 明确决定是否接受正式非 ODS 强门禁后才改旧入口。

## 5. 后续计划

1. 先解决同 SHA 全量 CI 的实际失败、复核原子事务与历史兼容测试。
2. 真实 DB 可用时完成 V5→V6 迁移、发布事务、审批回调与回滚验证，并做黄金故事手工业务签收。
3. 正式决策签收后，统一旧“直接发布”和“审批发布”的必需映射/标准规则（不能只堵一条路径）。
4. R12-2 接 DataDev **实际创建/关联 TaskRevision**，仍坚持任务 Owner、人工确认和真实执行回执，不由 ModelVersion 推断任务成功。

## 6. 本轮基于真实代码与 CI 的产品收口审查（2026-10-16）

| 功能阶段 | 用户可见结果 | 真实代码范围 | 当前结论 |
|---|---|---|---|
| 确认业务逻辑 | 浏览已冻结逻辑版本、标准字段和业务过程 | Logical / Semantic 现有服务 | 可用，真实用户签收未完成 |
| 准备 DWD 物理目标 | 选择现有草稿，或前往同业务过程创建新草稿 | Modeling 现有物理工作台 | 已接续，未自动建表 |
| 保存字段映射 | 精确 DIRECT / RENAME，MappingSet 草稿 CAS | DwdMappingDraftService | 已实现，未扩大复杂 JOIN/DERIVED |
| 冻结设计 | MappingRevision 不可变，重复操作内容幂等 | DwdMappingDraftService | 已实现 |
| 显式设计发布 | 无审批则直接发布；有审批则送审，后端重检后绑定不可变物理版本 | DwdMappingPublicationService / ModelVersionService | 已实现的 opt-in 能力，非全局强制门禁 |
| 重开历史与继续交接 | 按 Vn 查看冻结逻辑/映射；DataDev 预检核对 DWD 绑定 | 逻辑页、物理详情及 DataDev 预检面板 | 已实现**只读证据接续** |
| 真实加工与数据验收 | TaskRevision、SQL、目标库执行、运行/质量回执 | DataDev/Runtime Owner | **未实现跨域持久交接；不可判作 DWD 数据闭环完成** |

**可重复的数据库证据**：
- GitHub Architecture Checks 在提交 `1f20cfec` 的 MySQL 8 隔离库执行 `DatabaseMigrationSmokeTest`，4/4 PASS，含 MappingSet/Revision 唯一约束、项目隔离、事务回滚。
- GitHub [PostgreSQL 专项 CI #38890362071](https://github.com/gitfortian/data-ops/actions/runs/38890362071) 在 `883ea50b` 完整执行 `PostgresqlStorageSmokeTest`，1/1 PASS、0 skipped，含相同 DWD 表约束/回滚。
- 后续 UI 与单元测试提交需按**最新 HEAD**再查 Product Guard、后端、前端、PG 专项和架构门禁；不能用以上旧 SHA 的测试覆盖最新变更。

**不可提前宣布完成的三道门槛**：
1. 最新 HEAD 的完整前端/后端与架构/产品守卫全部 PASS，保留双数据库真实烟测证据；映射与模型发布在业务事务注入失败时无悬空版本，需独立验收。
2. Owner 正式评审 DR-06 #556 和 PD-010，决定是否对所有非 ODS 正式发布路径强制逻辑/标准门禁；本 PR 不偷偷改变历史旧路径。
3. R12-2 需要 DR-07 #557 授权 DataDev 持久 TaskRevision 交接、环境/目标/SQL 人工确认与运行回执；在这之前绝不可宣称完整“DWD 数据入仓闭环”。

## 7. 收尾阶段增量核验：发布指针失败与审批恢复（2026-10-16）

- **发布真实性**：新建 PhysicalModelVersion 或旧版幂等修复时，`ModelRepository.updatePublishState` 若因项目权限、模型软删除或 DB 更新失败而返回 false，建模服务必须立即抛异常，整笔 Business 事务回滚。新增 DWD 带 MappingRevision 的直接发布失败测试，不允许出现“保存了版本却未真正指向版本”的假发布。
- **真实数据库**：`DwdMappingSqlSmoke` 的 MySQL/PostgreSQL 隔离库验收覆盖冻结修订、物理版本和发布指针的同事务写入、故障回滚及正常恢复；并不证明审批终态的完整 Spring 业务 E2E。
- **审批页面恢复**：已有 DWD 映射草稿和冻结版本重新打开时，同时读取当前项目的发布审批策略；存在 `PENDING` 申请则持续显示审批中并禁用重复发布。用户点击“载入已保存映射”不清除这个来自服务器的在途状态。审批回执不能当作已发布版本或 DataDev 执行结果。
- **负例**：已保存冻结 MappingRevision + 在途审批 + 浏览器刷新，页面仍提示“审批进行中”；后端绕过接口仍阻止直接发布。申请终态之后才能按审批回调获得真实 ModelVersion，不能靠本地状态自造成功。

本阶段仍有独立合并阻断：上游 ODS #575 的 V5 迁移未合并，DR-06/PD-010、DR-07 均未批准全局规则及跨域任务写入；最新 HEAD 的完整 CI 必须同 SHA PASS。只读 DataDev 交接不等于真实 SQL 加工闭环。


> CI execution note: acceptance claims are valid only for the exact PR HEAD; a later HEAD must rerun the complete Product Guard / Architecture / Data Development / Metric / PostgreSQL evidence suite.
