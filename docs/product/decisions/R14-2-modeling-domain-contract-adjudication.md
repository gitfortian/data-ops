# R14-2｜DR-06 / DR-07 / PD-010 领域合同审定记录（评审稿）

Status: PROPOSED
Implementation: NOT_STARTED
Approval State: OWNER_SIGNOFF_REQUIRED / NO_WRITE_AUTHORIZATION  
Baseline: `main@709d02266a8e355a489788da9e9bca8774248b9d`  
Parent: [R14-2 #614](https://github.com/gitfortian/data-ops/issues/614) · [Story map #513](https://github.com/gitfortian/data-ops/issues/513) · [R12 design #576](https://github.com/gitfortian/data-ops/issues/576)  
Decision subjects: [DR-06 #556](https://github.com/gitfortian/data-ops/issues/556) · [DR-07 #557](https://github.com/gitfortian/data-ops/issues/557) · [PD-010](../decisions/PD-010-guided-business-to-data-journey.md)

> 本文件是基于 main 源码的正式审定建议，供 Product、Modeling、Semantic、DataDev、Runtime、Security Owner 签收。它不伪造任何 Owner 的批准，不把文档合并视为领域合同 ACCEPTED，也不授权开启生产绑定、SQL 执行或目标数据写入。

## 1. 本轮结论摘要

| 决策 | 推荐处置 | 当前实际状态 | 生效前置条件 |
|---|---|---|---|
| DR-06：逻辑版本 → 物理映射/物理版本 | **有条件接受其领域方向**：Modeling 持有持久 MappingSet、不可变 MappingRevision，并将精确逻辑快照、映射修订和物理版本绑定为一组可回读证据 | #556 仍 DECISION_REQUIRED | Modeling + Semantic + Security Owner 正式签收；明确逻辑版本稳定身份和跨分层支持范围 |
| DR-07：物理版本/映射修订 → DataDev TaskRevision | **接受 DataDev 持有任务绑定与执行事实的原则；当前只保留已合并、默认禁用的 DWD 能力** | #557 仍 DECISION_REQUIRED / NO_WRITE_AUTHORIZATION | Modeling + DataDev + Runtime + Security Owner 签收；安装经批准的目标写策略，明确层级合同与真实验收 |
| PD-010：非 ODS 数据标准强制 | **推荐接受核心硬约束**：非 ODS 正式发布列均须有效标准字段引用和适用的数据标准；草稿可留缺口但不得发布；首期不设未批准的技术列豁免 | 现有 PD-010 仍 PROPOSED | Product + Semantic + Modeling + Security Owner 签收发布闸门、校验责任和历史兼容策略 |

**禁止混淆：** 代码存在 ≠ 决策批准；Feature Flag 默认关闭 ≠ 目标写策略已获批准；TaskRevision 已绑定 ≠ SQL 已执行；Runtime 已接单/成功 ≠ 目标数据已对账；设计版本已发布 ≠ 质量通过或已消费。

## 2. 基于当前 main 的源码事实

核验基线为 main commit `709d0226`，并交叉查看已合并 PR #579、#589、#590 及其相关合同。

| 领域 | 已有事实 | 仍缺的能力 / 决策 |
|---|---|---|
| 逻辑→物理映射 | `LogicalPhysicalHandoffReviewService` 是临时、只读的 `REVIEWABLE_ONLY` 审阅；DWD 有持久 MappingSet、CAS 草稿、append-only MappingRevision、发布历史回读 | 通用逻辑映射服务仍未覆盖全部非 ODS 层；当前逻辑身份主要为 modelId + versionNo + snapshot SHA，是否存在独立稳定 version ID 需由 Logical Owner 明确，不能自行虚构 |
| 物理版本 | DWD 的 `ModelVersionService` 可把 MappingRevision、MappingSet、LogicalSnapshot SHA、PhysicalStructure SHA、MappingContent SHA 写入发布元数据；同结构但映射不同不得复用旧版 | 正式发布的标准来源证据尚未成为所有非 ODS 发布入口的统一阻断合同；DWS/DIM/ADS 的映射/上游版本引用须明确 |
| 物理列身份 | 物理结构保存可能全量替换列行；旧数据库行 ID 不适合作为跨修订永久身份 | 映射必须用规范化目标列标识 + 冻结结构摘要进行复核；列重命名、类型/语义漂移必须重审，不可仅按名称自动重绑 |
| 标准治理 | `PhysicalStandardCoverageInspectionService` 只提供 DWD/DIM 的证据报告，能区分缺失、无效、需人工审阅等情况 | 该服务不是通用发布阻断器；尚未证明 DWS/ADS 和所有直接发布、审批回调、派生、复制入口都使用统一强校验 |
| DataDev 绑定 | PR #589 已合并：DataDev 自有绑定记录、精确 ModelVersion/MappingRevision 与 TaskRevision/checksum、CAS、幂等和项目隔离；绑定状态为 `BINDING_RECORDED_NOT_EXECUTABLE` | 当前实现以 DWD 为范围，不能将其直接称为 DIM/DWS/ADS 通用能力；#557 仍未授权启用 |
| Runtime 执行 | PR #590 已合并：执行尝试持久化、人工确认、双 Feature Flag、运行请求去重；不自动重放 UNKNOWN；响应不含 SQL 原文 | `DwdFrozenExecutionTargetPolicy` 无宽松默认实现；仅有 flags 不足以授权。必须由 Security/Runtime Owner 提供并签收目标写策略，真实目标对账仍独立 |
| ADS Metric pin | PR #608 已合并并由 PR #611 只读证据卡复用 | Metric 已发布身份可核验不代表 DataDev/Runtime、Quality、Dataset/Data Service 或 Consumption 已接通 |

### 已验证的合并后门禁

- PR #611：已合并，commit `709d02266a8e355a489788da9e9bca8774248b9d`。
- 合并后 Architecture run [39114561607](https://github.com/gitfortian/data-ops/actions/runs/39114561607)：完整后端、完整前端、Architecture Gate、分发源码/制品一致性全部 SUCCESS。
- Product Guard run [39114561557](https://github.com/gitfortian/data-ops/actions/runs/39114561557)：SUCCESS。
- 以上 CI 只证明相应代码/合同检查通过，不等于真实环境验收或跨域 Owner 批准。

## 3. DR-06 推荐合同：持久、精确、不可猜测的映射身份

### 3.1 Owner 与身份

- **Modeling** 是 MappingSet、MappingRevision、物理发布版本及其历史回读的唯一真相 Owner。
- MappingSet 根至少绑定项目、逻辑模型/冻结逻辑版本、物理模型和目标层；MappingRevision 是 append-only 的不可变内容快照。
- 每个映射规则须明确目标列、来源逻辑属性/来源列、有效标准字段引用和映射种类。禁止以模型名称、列名相似、URL 或“最新版本”代替稳定身份。
- 逻辑版本若当前仅由 `logicalModelId + versionNo + logicalSnapshotSha256` 识别，第一阶段沿用该真实合同；不得自行假设存在 `logicalVersionId`。若 Logical Owner 决定引入稳定 version ID，须另行设计兼容/迁移并同时保留内容摘要校验。
- 物理列不能只依赖可被 replace 的数据库行 ID。应以规范目标列标识 + `physicalStructureSha256` 固定结构身份，并在冻结/发布时重新验证列、类型和标准引用。

### 3.2 生命周期与原子性

推荐状态：`DRAFT → VALIDATED → FROZEN → PUBLISHED`，另有显式 `REVOKED / REVALIDATION_REQUIRED`；具体状态编码须在 API/DTO 决策中冻结。

- 草稿修改要求 `expectedRevision` CAS；幂等键只允许同一规范化请求重放。
- 冻结修订 append-only；发布版本同时固定逻辑快照摘要、映射内容摘要、物理结构摘要和标准来源证据摘要。
- 直接发布和审批回调都必须调用同一领域校验合同；映射修订与 PhysicalModelVersion 的发布身份在 Modeling 自有事务中原子提交。
- 只有单一 ODS 来源的 DIRECT/RENAME 可作为已批准的首期可发布规则。JOIN、LOOKUP、DERIVED、聚合、SCD 和多来源基数，在各自语义/可执行合同与负例未通过前必须阻断正式发布或明确标记 DEFERRED。
- 逻辑版本变化、来源漂移、标准停用、列改名/删除/类型变化、跨项目引用或权限撤销，不得静默改写历史；保留旧快照并要求重审。
- 旧版缺少映射/标准证据时显示 `LEGACY_UNBOUND` 或相应的证据不足状态，不进行猜测式自动补链。

### 3.3 跨分层适用

MappingSet/Revision 的领域合同应允许通过明确的 layerCode 扩展，但**不能把 DWD 专用 API 改名后就声称所有层已支持**：

- DWD：已有 DIRECT/RENAME 基础，作为第一条验证切片。
- DIM：必须另外冻结业务键、代理键、SCD 类型、生效区间及事实关联键；无可信来源不创建“可用维度”。
- DWS：冻结精确上游 DWD/DIM 版本、分组键、时间粒度、聚合定义、重算范围与 MetricVersionRef。
- ADS：冻结精确上游版本、应用用途、目标结构与 Metric 发布身份；只有消费/性能需求确实需要物化时才创建。
- 未完成相应 layer contract 的层保持阻断/只读，不为凑层级而生成模型。

## 4. PD-010 推荐合同：标准字段与数据标准是正式发布门禁

### 4.1 生效规则

1. **ODS 例外**：保留来源事实，不强迫在 ODS 贴源阶段先完成标准字段绑定；必须准确展示来源与尚未治理状态。
2. **非 ODS 草稿**：允许保存未完成的设计以支持渐进式建模，但每列必须显示缺少、停用、跨项目、角色不匹配或标准版本漂移等问题。草稿不能显示为发布就绪。
3. **非 ODS 正式物理版本发布**：DWD、DIM、DWS、ADS 每个目标列必须引用当前项目内有效、启用、角色兼容的标准字段，并满足 Semantic Owner 返回的适用数据标准（类型、单位、码值/业务口径等按标准定义校验）。只有 `stdTypeId` 或列名相同不算满足。
4. **技术列例外**：首期不接受未批准的例外。若业务确需处理时间、批次 ID 等列，先绑定正式标准字段，或通过后续正式决策定义白名单、生成规则、版本影响和审计；不能用普通用户可关闭的层级开关绕过。
5. **所有入口同一合同**：直接发布、审批回调、派生、复制、逻辑转物理及任何批量入口调用同一服务端校验。前端警告不能代替后端拒绝。
6. **历史兼容**：历史已发布版本保持只读可查，不自动改写；新发布依批准后的生效策略重新校验。标准停用/漂移影响当前可用性和后续交接，但不篡改历史事实。

### 4.2 校验 Owner 与缺口

- Semantic 是标准字段/数据标准合法性、状态、项目可见性及角色语义的真相 Owner。
- Modeling 负责在发布事务内调用 Semantic 的正式验证接口，并对自身物理类型、转换规则、层级和映射一致性做最终阻断。
- 当前 DWD/DIM 覆盖报告是证据服务而非通用发布闸门；须补齐服务端共用 validator 和所有发布入口覆盖。
- 若 Semantic provider 不可用、无权限、身份不完整或版本无法核验，返回阻断/UNKNOWN，不降级为 EMPTY/通过。
- 当前没有获批技术列白名单；实现前按“零豁免”处理。

## 5. DR-07 推荐合同：DataDev 拥有任务绑定，Runtime 拥有执行事实

### 5.1 精确关联

DataDev 持有绑定事实；Modeling 仅提供只读已发布设计证据；Runtime 持有执行事实。绑定最少需要：

- 项目与层级；ModelVersion ID/版本号；MappingRevision ID/内容 SHA（若该层映射适用）；LogicalSnapshot SHA；PhysicalStructure SHA；绑定时观察到的标准证据 SHA（必须标记为当前观察，不伪称历史冻结标准）。
- DataDev Node ID、不可变 TaskRevision ID/修订号、实际任务定义 SHA/checksum。
- 用户确认的目标数据源/数据库/schema/table、写入模式、创建者/时间、绑定摘要与幂等键。
- 稳定 ID 用字符串穿越 JSON/JavaScript，服务端在每次 bind、publish、run 时重检项目、权限、目标和版本，不相信客户端项目 ID 或导航 URL。

### 5.2 运行与未知状态

- 绑定记录、TaskRevision 发布、Runtime execution、目标数据对账、Quality check、消费授权是不同事实，不得由上一步推断下一步。
- 只对已发布的精确 TaskRevision 执行；不能以当前编辑器草稿代替冻结版本。
- 执行尝试使用持久请求键和原域幂等合同；提交前先持久化预留，Runtime 已可能接单而回执丢失时保持 `UNKNOWN`，不得自动重放 SQL。
- `RUNTIME_ACKNOWLEDGED` 仅表示 Runtime 返回接单/执行事实；即使 Runtime 返回成功，也不能声称目标表已对账、质量通过或消费者已获授权。
- 只有通过明确授权的目标写策略才能执行；现有双 Feature Flag 与 `DwdFrozenExecutionTargetPolicy` 必须同时满足。没有策略 Bean、授权、人工确认或目标权限时，必须拒绝执行。
- 当前 PR #589/#590 的持久绑定与执行尝试是 **DWD 专用、默认门禁关闭的代码基座**。DR-07 接受前不启用 flags、不注册宽松策略、不向 DIM/DWS/ADS 暴露通用写接口。
- DWS/DIM/ADS 的后续绑定须先验证各层特有的输入版本、指标引用、目标结构、质量和消费依赖；不得仅通过放宽 `layerCode` 即开放。

## 6. 正式签收与实施顺序

| 事项 | 必须签收的 Owner | 签收内容 |
|---|---|---|
| DR-06 | Modeling + Semantic + Security | MappingSet/Revision 身份、物理列身份、生命周期、发布原子性、层级适用范围、负例 |
| PD-010 | Product + Semantic + Modeling + Security | ODS 例外、非 ODS 全字段强制、零技术列豁免首期策略、发布入口覆盖、历史兼容 |
| DR-07 | Modeling + DataDev + Runtime + Security | DataDev binding Owner、精确 TaskRevision/Runtime 关联、目标写策略、双开关、幂等、UNKNOWN 恢复和审计 |
| 实际业务验收 | 有权限的环境/业务 Owner | 订单明细业务键、目标表/连接、行数/重复键/转换规则对账；不得由 CI 代签 |

实施顺序：
1. Owner 对本文件提出 ACCEPT / REVISE / REJECT，并更新 #556、#557 与 PD-010 的正式决策状态。
2. 只有 DR-06 与 PD-010 获批后，才实施统一非 ODS 发布 validator、精确逻辑/映射/物理版本关联及跨入口回读。
3. 只有 DR-07 获批并提供正式目标写策略后，才允许进一步验收/扩展 DataDev 绑定与 Runtime；生产开关默认关闭直至真实目标写入与恢复验收。
4. DWD 基础闭环验收后，按实际业务需求逐层扩展 DIM/DWS/ADS；不机械地创建所有层。
5. 自动化测试、PR 合并、真实环境验收分别记录；未执行真实验收的项目保持 `DEFERRED_REAL_ENV`。

## 7. 明确不在本提案中授权

- 不开启 `data-ops.dwd.binding-write-authorized` 或 `data-ops.dwd.execution-write-authorized`。
- 不新增默认允许的 `DwdFrozenExecutionTargetPolicy`。
- 不执行 SQL/DDL，不创建或发布新 TaskRevision，不向真实目标写入数据。
- 不自动回填旧逻辑/物理/任务历史绑定。
- 不将现有 DWD-only 代码标记为 DIM/DWS/ADS 通用能力。
- 不将本文件的合并、CI PASS 或用户“继续实施”解释为所有 Domain Owner 已签收。

## 8. Reviewer checklist

- [ ] Product Owner accepts/revises the proposed PD-010 core rule.
- [ ] Modeling Owner signs DR-06 identity and publication transaction contract.
- [ ] Semantic Owner signs standard-field/data-standard validation semantics.
- [ ] Security Owner signs project isolation, permission rechecks and target-write boundary.
- [ ] DataDev Owner signs immutable TaskRevision binding, CAS/idempotency and audit.
- [ ] Runtime Owner signs exact execution identity, idempotency and UNKNOWN recovery.
- [ ] The status of #556, #557 and PD-010 is updated only after actual recorded approvals.
