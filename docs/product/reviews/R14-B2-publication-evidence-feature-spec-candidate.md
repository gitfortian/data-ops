# B2 非 ODS 发布标准证据 — 集成准入与验收矩阵（候选）
Status: PRODUCT_SCOPE_ACCEPTED_ONLY / TECHNICAL_CONTRACT_PROPOSED / REVIEW_ONLY / NO_WRITE_AUTHORIZATION
PR: #635 | Parent: #614 | DR-06: #556 | PD-010: D4_ONLY
Baseline: PR #635 head 65f039da (2026-10-19)

> 本文件是供 Modeling / Semantic / Security 三领域**分别审查**、由项目唯一技术负责人**统一决策**的 Feature Spec 候选，不是审批票据或可启用的生产合同。**Product 的 P01–P18 功能范围已单独签收**（[R14-2 #614 签收评论](https://github.com/gitfortian/data-ops/issues/614#issuecomment-6260677412)，仅 PRODUCT_REQUIREMENTS_ONLY），但具体运行时合同仍为 PROPOSED，P01–P18 实现验收全部未通过。不能把文档合并、Product 签收、或负责人身份确认视为最终技术设计的 ACCEPT；不得据此接入发布回调、修改发布资格、写 ModelVersion、执行 SQL/DDL 或开启任务写入。

## 1. 业务结果与非目标

**目标**：用户在非 ODS（DWD/DIM/DWS/ADS）保存草稿后，正式发布前全量检验目标物理列的标准字段与适用标准；审批提交时冻结精确证据，回调时重检；新发布版本持久冻结字段级标准摘要，并可追溯；历史版本不回填、不重算。ODS 保持贴源例外。

**非目标**：DataDev TaskRevision 生成/SQL 执行/目标表写入/质量对账；以标准 VALID 替代 DWS 粒度、ADS Metric/消费或 DIM 专项校验；以候选观察 token 声称跨域事务原子性。

## 2. 当前代码实际能力（不越权）

- `DraftPublicationEvidenceVerifier`: 纯 Java 非 Spring Bean，检查 project/model/layer/structure、目标列全集、字段类型和 StandardField、CODE 完整性及候选 digest；输出 VALID_CANDIDATE/BLOCKED/UNKNOWN/ODS_OUT_OF_SCOPE。
- `DraftPublicationApprovalEvidence`: 纯 Java 非 Spring Bean，冻结 design binding + 标准证据，比较设计/标准变化；返回 CANDIDATE_MATCH/STALE/UNKNOWN。
- `evaluateAgainstModelSnapshot(submitted, physicalSnapshot, semanticObservation)`：新增独立候选列集比对；提交的列名/类型/StandardField ID、作用域、结构摘要须与另一个 Modeling 物理快照相符，且与 Semantic 响应完整一致。模型列快照入参**尚无可信来源保证**；这不是 DB 读取、SHA 重算、身份授权、事务/并发保护或发布权。调用方必须未来从 Modeling 持久事实/根锁上下文取得并验证列全集。
- `Request/Observation` 构造时防御性复制列证据列表，避免调用方在候选校验前后篡改输入集合；不等于对数据来源作可信认证。
- `DraftPublicationEpochGuard`：隔离的审批双域纪元捕获/复核策略候选，分别比较 Semantic 与 Security 的单调 revision；不自行生成可信 revision，不接入任何正式审批或发布。
- `DraftPublicationPhysicalSnapshot.from(projectId, modelId, layer, StructureView)`：**本轮真实源码口径对齐**。候选桥接层位于 `modeling.version`，只接受非 ODS 且 `structure.modelId` 对应目标模型，从完整 `StructureView.columns` 读取列名、物理类型和 `stdFieldId`，拒绝空列集/重复列/错误身份；缺少标准绑定的列仍保留在请求中，让下游明确 `BLOCKED`。物理摘要与现有 `ModelVersionService.semanticView` 同算法计算，规避原始行 ID/草稿状态等瞬态字段。这仍**不是**可信项目授权或数据库内根锁，尚未被生产入口调用。
- `DraftPublicationPhysicalSnapshotTest`：6 个纯候选回归覆盖语义结构 SHA 一致、瞬态 ID/状态稳定、类型/标准绑定漂移、无标准的技术列保留、独立 Semantic 完整列比对与拒绝、非法/重复/跨模型结构。

- 已有专项测试验证基础正常流、漂移、篡改与跨层复用；现有 CI PASS 仅证明候选实现可编译/运行。
- 未接 Semantic 正式 SPI、真实授权上下文、四发布入口、审批 payload、ModelVersion/迁移或产品 UI/E2E。

## 3. 待签收技术决策（不可用候选值代替）

| Owner | 必须书面确定 | 验收证据 |
|---|---|---|
| Semantic | 批量只读验证正式 SPI；TYPE/UNIT/CALIBER/CODE/SECURITY 角色语义；跨项目与撤权拒绝；digest algorithm ID、安全字段级 schema；批量快照/epoch、重读一致性及 ABA 策略 | API 合同、实现方测试、明确版本号和失败模式 |
| Modeling | 四入口统一 validator 位于 model 根锁内、复用/新增 ModelVersion 判定之前；标准摘要入版本身份；精确 MappingRevision/逻辑快照/物理结构 CAS；审批时冻结与回调复核；事务回滚与历史只读 | 事务/并发集成测试、MySQL/PostgreSQL 迁移和读回 |
| Security | 服务端可信项目/用户上下文，不接受客户端项目号作为授权；撤权/拒绝/UNKNOWN fail-closed；SECURITY 信息最小化、日志脱敏、审计 | 角色和跨项目负例、审批回调权限重检 |
| Product | D4_ONLY 已批准；P01–P18 产品功能范围已签收：非 ODS 逐列有效标准、零技术列豁免、ODS 例外、草稿宽限、历史不回填 | [#614 Product D4 / B2 P01–P18 功能签收](https://github.com/gitfortian/data-ops/issues/614#issuecomment-6260677412)；不代表技术合同或实际测试签收 |

**一致性关键问题**：独立 Semantic 观察 token 只是证据标识，不是跨模块锁。需选择并批准可证明的同一语义快照读取/版本 CAS/发布瞬间最终复核方案；无保证时返回 UNKNOWN，不能声称不漂移。

### S2 REVISE：双域单调纪元的可执行候选（非批准合同）

新增 `DraftPublicationEpochGuard`（纯 Java、无 Spring/IO），演示以下**候选策略**：
1. `EpochObservation` 绑定 `scheme/projectId/modelId/layer/physicalStructureSha256/authorizationScopeSha256/semanticEpoch/authorizationEpoch`；`authorizationScopeSha256` 只能由受信 Security 上下文按当前主体/角色/权限范围规范计算，不能由浏览器自选，也不暴露原始身份或策略。Semantic 与 Security 分别对所有影响当前标准合法性、项目可见性、撤权与重授权的修改**单调推进**相应 revision；不能仅使用内容 SHA、当前权限布尔值、时间戳或每次读取随机 token。
2. 审批候选冻结时绑定两种 revision；回调再次观察并比较：任一 revision **增加**即 `STALE`（即使标准/权限回到旧值，阻止 ABA）；任一 revision **减少**即 `UNKNOWN`（旧读或重放）；身份/算法/纪元/授权范围摘要缺失或篡改即 `UNKNOWN`，回调时授权主体或权限范围摘要变化即 `STALE`；两者相同且完整设计、标准证据相同时仅 `CANDIDATE_MATCH`。
3. **尚未解决的工程关键点**：这些 revision 在当前仓库尚无对应已批准 SPI、持久更新保证或安全服务端来源；冻结 hash 不具有签名/权限语义。两域分别读取与 Modeling 提交之间仍可能改变，最后一次 recheck 也不是跨数据库原子锁。正式实施须由统一技术负责人基于三域 Review 决定：以 Semantic/Security 提供经认证的同版本快照与可验证提交 fence/lease 为准，或选择明确的最终一致性业务策略并声明允许的窗口与重审条件；**未决定前不可生产接线，不得假称 ABA/TOCTOU 已完全解决**。
4. 范围仅为 DR-06/PD-010 的标准发布审批；不授权 DR-07、Runtime 或真实目标写入。

候选专项测试验证相同内容的标准 ABA / 权限撤销再授予、epoch 回退、跨项目、未知/篡改、标准与映射漂移和稳定候选路径。**测试通过只证明纯算法行为，并非有真实 epoch 提供者、并发锁或发布路径集成**。

### Modeling 根结构摘要源码审查（2026-10-19，非生产签收）

主线 `ModelVersionService.publishLocked` 目前已经在数据库模型根锁与 `structureService.getForUpdate(modelId)` 后，用 `semanticView(StructureView)` + `VersionDigests.sha256Hex` 计算结构语义身份，排除可变行 ID/status，并在版本复用判定、插入新版本和发布指针更新之间维持 Modeling 事务。新增桥接器复用**此实际 DTO 投影**，避免和原版本口径发生漂移。

**新增代码只能解决结构语义投影的算法一致性**；生产 B2 仍须在该锁和已有事务中读取真实结构/当前项目范围，调用经正式批准的 Semantic 批量 SPI、安全上下文与提交 fence，冻结标准证据与新 ModelVersion 原子写入；DWD 映射路径的已发布幂等回执也须涵盖标准状态重核验。任何调用者自己拼 `StructureView`、自带 projectId 或自行传入 SHA 仍不能获取发布权。

## 3.1 单一技术负责人决策方式（2026-10-19）

用户已明确确认 data-ops 的 Modeling、Semantic、Security 最终技术决策由同一人负责。适用的流程因此是：**三领域独立技术证据 Review → 同一技术负责人一次明确决策 → 助手在 #556/#614/#635 留档 → 代码/CI/真实验收分离**。参考 [DR-06 #556](https://github.com/gitfortian/data-ops/issues/556) 和 [R14-2 #614](https://github.com/gitfortian/data-ops/issues/614) 的最新责任人规则。

统一签收必须明确：`OWNER_OF_RECORD`、`DOMAINS`、`DECISION`、合同版本/commit、每领域条件与评审证据、允许的实施范围和不授权的风险边界。**不要求三张不同账户的投票，但三域均不能跳过技术审查。**

当前仅已确认 **OWNER_OF_RECORD_CONFIRMED / TECHNICAL_DESIGN_PENDING**；原候选里没有正式 Semantic 读一致性/ABA 协议、生产四入口集成、权限与持久迁移等证据，因此不得把本次负责人确认冒充技术合同 ACCEPT。具体策略须先明确后才能给对应的研发准入决定；任何生产开关仍受独立发布与环境验收门槛限制。

## 4. 生产接入顺序（仅在 B1 正式 GO 后执行）

1. Semantic 提交批准的只读 batch SPI + digest vN + 授权/一致性合同；Modeling 只依赖 SPI，绝不直读 Semantic DB。
2. Modeling 在模型根锁与冻结结构获取后统一调用 validator；四入口：常规直接发布、常规审批回调、DWD Mapping 直接发布、DWD Mapping 审批回调。任何未来复制/派生/批量发布也必须汇入同一闸门。
3. 审批提交：冻结项目/模型/层级/物理结构/逻辑版本/MappingRevision/标准证据及算法；审批回调：可信身份与权限重检、重读同一类语义事实、对比冻结值，任一漂移拒绝而不写版本。
4. 新发布：把标准字段级最小脱敏快照和摘要与物理版本在 Modeling 同事务落库。幂等复用必须包含标准证据身份；旧版本只读，不补 hash。
5. 前端：分别展示草稿问题、审批时快照、发布时不可变快照、当前治理健康，禁止将任一阶段说成执行/质量/消费已完成。

## 5. P01–P18 候选验收矩阵

状态：**CANDIDATE_UNIT** = 只有独立候选单元测试线索、尚非生产场景验收；**NOT_IMPLEMENTED** = 需集成实现及测试。所有 P01–P18 当前均 **NOT_ACCEPTED**，不等同于已有 18 项全通过。

| ID | 黄金/拒绝场景 | 期望行为 | 当前层级 |
|---|---|---|---|
| P01 | DWD 每个目标列标准有效 | 发布写不可变证据，新版本可回读 | CANDIDATE_UNIT |
| P02 | DIM 全列有效、CODE 完整启用 | 标准通过且仍必须经过 DIM 专项校验 | CANDIDATE_UNIT |
| P03 | DWS 全列有效 | 标准通过但不替代粒度/上游/Metric 校验 | NOT_IMPLEMENTED |
| P04 | ADS 全列有效 | 标准通过但不替代消费/授权专项校验 | NOT_IMPLEMENTED |
| P05 | 任意非 ODS 列缺 StandardField（含技术列） | 正式发布 BLOCKED，草稿可保存 | CANDIDATE_UNIT |
| P06 | 标准停用/角色不符/TYPE/UNIT/CALIBER 缺失 | BLOCKED，返回安全、可定位原因 | NOT_IMPLEMENTED |
| P07 | CODE 部分停用、全部停用、缺失/错误计数 | BLOCKED 或 UNKNOWN，不得冒充全启用 | CANDIDATE_UNIT |
| P08 | Semantic 超时/未知/部分列/算法版本不匹配 | UNKNOWN、事务回滚、无版本写入 | CANDIDATE_UNIT |
| P09 | 跨项目/跨模型/撤销权限 | 拒绝并审计，不泄露标准数据 | CANDIDATE_UNIT |
| P10 | 物理结构、字段名、类型或标准引用变化 | STALE/BLOCKED，必须重审 | CANDIDATE_UNIT |
| P11 | 审批后逻辑版本或 MappingRevision 漂移 | STALE，不复用旧审批 | CANDIDATE_UNIT |
| P12 | 审批后标准内容、CODE 内容、授权状态或观察版本变化（含变更再恢复 ABA） | STALE/UNKNOWN，不允许回调发布；须核对可信纪元与最终提交竞态 | CANDIDATE_UNIT（非生产并发证明） |
| P13 | 冻结证据/摘要被篡改，跨层复用 | UNKNOWN/STALE，拒绝伪造审批 | CANDIDATE_UNIT |
| P14 | 常规直接发布入口 | 调用同一服务端验证器，无绕路 | NOT_IMPLEMENTED |
| P15 | 常规审批发布入口 | 提交冻结、回调重读、失败不写版本 | NOT_IMPLEMENTED |
| P16 | DWD Mapping 直接与审批两个入口 | 同一校验 + Mapping 精确身份 | NOT_IMPLEMENTED |
| P17 | 物理结构相同，标准摘要变化/幂等重试 | 不错误复用旧版本；ModelVersion 指针原子一致 | NOT_IMPLEMENTED |
| P18 | ODS 例外、历史已发布版回读、回滚后再发布 | ODS 原合同有效；旧历史不改；再次发布重新验证 | NOT_IMPLEMENTED |

## 5.1 两套 P01–P18 编号防串台映射（2026-10-19）

**注意：存在两套完全不同的 P01–P18。** 本文件 §5 的 P01–P18 称为 `B2-Pxx`；已合并主线的 [R14-2 DR-06/PD-010 技术裁决候选](../decisions/R14-2-modeling-domain-contract-adjudication.md) §10.5 的 P01–P18 称为 `DR06-Pxx`。**同号不是同一测试**，Product 签收评论接受的是本文件 §5 的产品场景定义，并非自动证明 DR06-Pxx 已通过。

| B2 产品用例 | 对应 DR06 技术/拒绝场景 | 应注意的不同边界 |
|---|---|---|
| B2-P01 DWD 成功 | DR06-P01 | DWD 发布时持久冻结，不只是候选 PASS |
| B2-P02 DIM 成功 | DR06-P01、P05 | DIM 专项键/SCD 合同额外验收 |
| B2-P03 DWS 成功 | DR06-P01 | 粒度/上游/Metric 专项合同仍生效 |
| B2-P04 ADS 成功 | DR06-P01 | 消费授权/Metric 身份独立 |
| B2-P05 任一列无 StandardField | DR06-P02 | 技术列零未经批准豁免 |
| B2-P06 标准停用/角色/类型 | DR06-P03、P04 | TYPE/UNIT/CALIBER/SECURITY 各自角色适用 |
| B2-P07 CODE 不完整 | DR06-P05、P16 | CODE 摘要算法版本兼容单独验证 |
| B2-P08 Provider UNKNOWN/缺列 | DR06-P06、P17 | 单调纪元不能替代提交 fence |
| B2-P09 跨项目/权限撤销 | DR06-P07、P15、P17 | 权限/敏感信息披露独立拒绝 |
| B2-P10 物理结构漂移 | DR06-P08、P10 | 物理列全集来自服务端可信源 |
| B2-P11 逻辑/映射版本漂移 | DR06-P08、P10 | 精确稳定映射修订，不能按最新版本猜测 |
| B2-P12 标准/权限漂移和 ABA | DR06-P09、P16、P17 | 当前纯纪元算法仍无真实来源/原子提交 |
| B2-P13 篡改/跨层重用 | DR06-P08、P09、P10 | hash 完整性不等于签名或授权 |
| B2-P14 既有直接发布入口 | DR06-P12 | 必须验证正式事务路径 |
| B2-P15 既有审批回调入口 | DR06-P12 | 提交和回调均重新核验 |
| B2-P16 DWD 两条 Mapping 发布入口 | DR06-P12 | 另核 MappingRevision 绑定 |
| B2-P17 版本复用/幂等 | DR06-P11 | 同结构但新标准证据不得错误复用 |
| B2-P18 ODS/历史/回滚 | DR06-P13、P14 | 老历史只读、重新发布重新校验 |

**必须单独保持的 DR06 验收：** `DR06-P18` 无 DR-07/目标写授权时绝不生成 TaskRevision/调用 Runtime/执行 SQL/DDL；`DR06-P15` SECURITY 信息披露与安全日志需要 Security 专项用例；`DR06-P16` 摘要算法切换需代码与历史兼容专测；`DR06-P17` 最终一致性/竞态需真实 Semantic 与 Security 提供者和 Modeling 提交边界的集成证明。不得因 B2 对应单元测试而勾选这些 DR06 用例。

**验收报告必须分别计数：** `B2 Product scope approved: 18/18` 只是验收条目范围确定；`B2 implementation accepted: 0/18` 仍待生产集成；`DR06 integration accepted: 0/18` 仍待技术合同与真实集成。不得把不同台账的 18 项测试混算。

## 6. 正式 B1 GO / B2 Ready 判据

- [ ] **统一技术负责人**针对同一版合同记录一份明确的 `ROLE: Accountable Technical Owner / DOMAINS: Modeling, Semantic, Security / DECISION: ACCEPT|REVISE|REJECT / VERSION / CONDITIONS / EVIDENCE`；三项审查各须有可追溯证据，不要求三个不同账户分别投票。仅明确责任人身份不等于签收。
- [ ] Semantic 提供真实可用的 batch SPI、可验证 digest/schema 和一致性保证；Security 批准项目上下文/脱敏。
- [ ] 本 Feature Spec 由规定 Owner 审定，不以本文 PROPOSED 代替已批准的 Feature Spec。
- [ ] B2 生产集成覆盖四发布入口、ModelVersion 冻结、MySQL/PostgreSQL 迁移、P01–P18 全部适用场景及边界回归。
- [ ] Product Guard、完整前后端、Architecture Gate、数据库集成和真实业务黄金故事通过，证据指向同一最终 HEAD。
- [ ] 如真实环境不可访问，标记 DEFERRED_REAL_ENV，不宣称正式验收完成。

当前结论：B1 **CONDITIONAL_DEVELOPMENT_ONLY / PRODUCTION_NOT_ADMITTED**；#635 必须保持 **Draft / NOT_MERGED**。