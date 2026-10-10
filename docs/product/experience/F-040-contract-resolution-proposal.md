# F-040 · 关键产品决策收敛与跨域实现契约提案 V3

Status: REVIEW_READY_PROPOSAL（待 Product/领域 Owner 签署，不等于 ACCEPTED 或 APPROVED）  
Date: 2026-10-10  
Parent: [#513](https://github.com/gitfortian/data-ops/issues/513) · Evidence: [#512](https://github.com/gitfortian/data-ops/issues/512)  
Decision: [PD-010（PROPOSED）](../decisions/PD-010-guided-business-to-data-journey.md) · Feature: [F-040（DRAFT）](../features/F-040-data-construction-experience.md)  
Previous: [十屏交互 V2](./F-040-screen-contracts-v2.md) · [V2 交接清单](./F-040-domain-handoff-and-review-gates.md)

> 这是**评审准备的裁决提案**，不是擅自代表产品负责人做最终决策。源代码事实来自 2026-10-10 的 `main` 静态检查；不宣称运行 E2E 已通过。PD-010 不应在这里被悄悄改成 ACCEPTED，F-040 不应在这里被改成 APPROVED。

## 一、八项决策收敛：把选择和未决点分开

| 决策 | 推荐结论（待批准） | 为什么 | 仍需 Product/Owner 签署的点 |
|---|---|---|---|
| DR-01 两种入口 | **采用**业务目标入口和已有来源入口；以现有专业模块的上下文动作和轻量进度呈现，不增加顶级“数据建设” Truth | 避免第二业务模型库、第二任务定义与迁移负担 | 推荐入口在模型工作台、业务过程、资产页的具体显示层级 |
| DR-02 ODS 例外 | **采用**ODS 可从表直接保真建草稿，不强制先有逻辑模型或标准字段；来源证据须正式保存 | 业务数据库通常先有物理表，不能为了“流程整齐”阻碍镜像 | ODS 技术列与原始列的保真/方言转换展示策略 |
| DR-03 非 ODS 标准字段 | **采用**所有非 ODS 正式物理字段默认都要求有效标准字段 `stdFieldId`；草稿允许明确的待治理项，**设计正式发布硬拒绝**。仅预先批准的技术列可例外 | 与用户确定的治理规则一致，避免只看 `stdTypeId` 造成假达标 | 技术列白名单、版本失效策略、层级 `stdMandatory` 与统一政策的关系 |
| DR-04 逻辑属性与 Semantic | **采用**逻辑属性拥有自己的稳定 ID、含义、逻辑角色和粒度，但通过 `stdFieldId` 引用权威标准字段、通过 Ref 关联过程；草稿未匹配属性保留待治理状态 | 逻辑模型不是第二套 Semantic；业务设计早于技术类型决策 | 何种属性可暂不绑定标准、逻辑版本是否准许缺标准的“已审阅”状态 |
| DR-05 关系与粒度 | **采用**关系基数与业务主标识需可解释证据和确认人；同名字段/外键只能生成候选；先做清单+属性编辑+关系表，不强迫首期重型画布 | 防止假 Join、假 1:N 与事实粒度错位 | 订单与明细是否为一个过程下的双粒度落地、关系确认责任人 |
| DR-06 逻辑→物理 | **采用**从精确逻辑版本预览到一组物理草稿/已有模型的方案；显式选择复用、新建、取消；写入幂等，不自动发布与建库 | 避免一实体一表教条及生成重复模型 | 首版关系基数支持范围、版本如何冻结物理映射与冲突处理 |
| DR-07 Modeling→DataDev | **采用**受权 ModelVersionRef 交接给现有 DataDev；任务保存、发布、运行仍归 DataDev 各自接口 | 代码已提供 DevelopmentNode/Task/Execution 的正式职责 | 交接是新 API 还是现有 Draft API + 独立 provenance 记录；运行目标解析权威来源 |
| DR-08 首次交付价值 | **采用**“每日下单订单量”作为端到端试点候选，必须有业务口径签署、任务执行、质量/权限、Metric 版本和真实 Dataset 查询/Usage Evidence | 防止再次把模型发布当作 0→1 完成 | 时区、是否剔除取消订单、去重字段、订单明细与金额验证是否本次范围 |

**处理规则**：每项均为 `RECOMMENDED_NOT_ACCEPTED`；正式决议填写 `ACCEPT / REVISE / REJECT`、批准人/日期、理由及受影响的 Product Decisions/Feature Spec。可以一轮产品评审集中裁定八项，不要求零散地为每一项拆 PR。

## 二、领域与版本关系：精确引用，而不是字段名猜测

```text
来源 Catalog/Metadata Evidence（SourceRef + snapshot/权限）
    │
    ├── 物理 ODS 草稿/版本（SourceRef + 逐列 Mapping + 技术字段来源）
    │
Semantic：BusinessProcess / StandardField + Standard Version
    │                │
    └──→ LogicalModel / Entity / Attribute / Relation
                       │ 绑定 ProcessRef、StdFieldRef、逻辑版本
                       ↓
              PhysicalModel / Column + 设计版本
                       │ 逻辑→物理关系、StdFieldRef、来源/转换规则
                       ↓
             DataDev Node / Draft / Revision / Execution
                       │ 目标环境与运行证据（不由 Modeling 推断）
                       ↓
           Quality / Lineage / Metric / Governed Consumption
```

### 2.1 逻辑模型最小正式合同（建议）

- `LogicalModel`：项目范围、模型 ID/编码、业务域 Ref、名称、owner、草稿 revision、状态与确认版本指针。
- `LogicalEntity`：实体 ID、logicalModelId、中文/英文业务名称、定义、业务身份/类型、归属过程 Ref（必要时单独关系表，**不能默认一个过程必然只对应一个实体**）。
- `LogicalAttribute`：属性 ID、entityId、逻辑名称/类型、业务标识/粒度角色、`stdFieldId` 或显式 `PENDING_STANDARD`，候选来源 EvidenceRef 和确认人。**只记录标准字段引用 ID 与合法性/冻结版本，不复制另一套可编辑数据标准定义**。
- `EntityRelation`：两端实体 ID、基数、业务关系说明、字段配对证据、`CONFIRMED / PENDING`、确认时间/人、版本归属。不要仅因同名字段自动确认。
- `LogicalModelVersion`：与确切对象集合、已确认关系、StdFieldRef/ProcessRef 及可回溯版本关联的不可变快照/引用合同；后续标准字段状态变化可以标记“依赖失效”，但不能篡改历史。
- `LogicalPhysicalMapping`：精确逻辑版本/Entity/Attribute ID → 目标 PhysicalModel/Column 和目标设计版本的映射及状态；支持查询历史，重试不重复插入；逻辑草稿变更不反向覆盖历史物理发布。

已有 [LogicalAttribute](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/domain/LogicalAttribute.java) 只包含基础字段、逻辑类型、主标识等，没有 `stdFieldId`。已有 [LogicalEntityMapping](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/domain/LogicalEntityMapping.java) 和 [LogicalAttributeMapping](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/domain/LogicalAttributeMapping.java) 使用物理表/列 ID，但没有表达关联至哪个**不可变逻辑/物理版本**；实施应按向后兼容迁移补合同，不要重写旧 Flyway 迁移或推断历史引用。

### 2.2 模型设计发布建议的冻结范围

现在 [ModelVersionService](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/version/ModelVersionService.java) 固化物理结构及元信息，并采用结构语义指纹去重；但目标要能恢复**当时的过程、来源、标准字段、逻辑版本、字段映射及转换规则的身份**。建议引入独立不可变的 `DesignEvidenceManifest`（**命名仅为合同草案，不是新增权威模块**），随物理发布版本绑定：

| Manifest 信息 | 最小内容与来源 |
|---|---|
| 结构 | ModelVersion ID、结构/列级快照指纹、方言/分层 |
| 来源 | SourceRef、采集版本/指纹、源表/列映射确认时点 |
| 业务语义 | ProcessRef、LogicVersionRef、Entity/Attribute ID |
| 标准 | 每目标列 StdFieldRef、合法角色/标准版本、技术列例外审批（若最终允许） |
| 加工建议 | 来源转换表达式/规则引用、`VALIDATED` 范围、未知与未确认项 |
| 验证 | 发布前领域校验结果、权限/漂移/冲突及证据指纹 |

设计 Manifest 应是既有 Modeling Version 版本内容的一部分或其不可变关联，由 Modeling 拥有，不成为新标准/来源 Truth。版本去重必须同时考虑**会改变正式设计语义的引用和转换**；只比列结构可能漏掉标准/来源/逻辑版本变化。

## 三、治理门禁：一个后端合同，所有入口都受约束

### 3.1 分层策略（推荐待批准）

- **ODS 草稿及设计发布**：不要求 `stdFieldId`，但不能把来源不明的列默认为“已确认直通”；技术列有独立生成依据。发布版本与 Doris 真实部署分离。
- **DWD/DIM/DWS/ADS 等非 ODS 草稿**：允许用户设计、保存尚待治理字段，页面逐列标记缺少标准字段 / 停用 / 跨项目 / 角色不符 / 标准版本漂移。
- **非 ODS 设计正式发布**：所有目标列默认必须经有效 `stdFieldId` 校验；只有明确批准的技术列例外可不引用且必须带类型、生成规则、审计与授权记录。若例外政策未批准，则不自动允许任何列豁免。
- **派生/批量/Agent 采纳/复制/从逻辑生成**：可以生成**草稿候选**，不能把它们当已发布结构，最终都经过同一服务端正式准入校验。
- **DataDev/实际执行**：还要验证目标环境、任务权限、映射规则、SQL 类型/数据转换，不得用模型设计发布的门禁替代运行前检查。

建议领域校验伪逻辑（非接口定义）：

```text
validateForDesignPublication(model, frozenStructure, references):
  verify model exists, project scope, permissions, selected revision
  if layer == ODS:
    validate source/evidence status appropriate to requested publication;
    preserve original columns and disclose unresolved source mappings
    return review with explicitly allowed gaps
  for each target column:
    if approvedSystemColumnException(column): validate exception policy + audit
    else:
      require stdFieldId
      load StandardField from Semantic owner by ID, in current project and authorized
      require ENABLED and role-compatible; validate TYPE/UNIT/CODE/... per role
      verify model's physical target type against field standard and explicit conversion
  verify logical/version/source mappings and semantic fingerprints according to publication contract
  reject if any blocking issue; return structured field-level failures
```

**不可用名称相同、`stdTypeId` 存在、当前层开关被设置 false 来绕过已批准的非 ODS 强制规则。** 现有分层 `stdMandatory` 可配置属性的未来含义需要 PD-010 评审：建议改为“治理策略强度与例外配置（受权）”而不是普通用户任意关闭全局硬合同；兼容历史值需要迁移计划。

### 3.2 校验责任和错误响应

- Semantic 提供“引用合法性”的公共读取/验证合同（批量 ID 优先），不允许 Modeling 直接复写 Semantic 验证策略或跨项目查其私表；
- Modeling 对自身结构、层级、目标类型及引用一致性做最终发布判定；同一领域服务被所有写入口调用；
- 错误结构建议：字段 ID/目标列、违规规则编码、当前引用、允许的修复动作、是否阻断以及证据版本。无权限不泄露标准字段名称/私有详情；
- 停用、删除、标准版本更新和来源漂移不重写历史物理版本，但应该影响其**当前可用性评估**与新发布/交接许可。

## 四、ODS 来源原子性与重复导入修复

当前 [ReverseImportWriter](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/importer/ReverseImportWriter.java) 能在单表事务中保存模型结构与模型级来源引用；[MappingService](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/mapping/MappingService.java) 已具逐字段规则与部分指纹/来源新鲜度检查，但详情页从源表导入与这些权威写入之间尚未组合成同一次**产品确认**。

建议统一“来源结构导入计划”的产品提交：
1. **Preview（无副作用）**：冻结目标模型草稿修订、来源 datasource/schema/table/列快照/覆盖指纹、对应字段/类型/主键/标准字段冲突、拟新增/更改/删除的 Mapping、手工修改风险。
2. **Confirm（受权写入）**：带预览指纹、目标 revision、明确确认列集合、幂等键；服务端重验来源权限/过期；在一个 Modeling 业务事务中写可承担的结构、模型 SourceRef、列 Mapping、导入方式与审计。
3. **Verify（可恢复回读）**：返回模型 ID、新 revision、来源绑定和逐列映射结果、成功/冲突/失败与可恢复指引；不把“前端字段已展示”当入模。跨库外部数据不在同一个本地事务时，必须重验和回执，不伪装分布式原子提交。
4. **Re-import**：差异预览后支持选中应用，历史人工映射/标准字段绑定/新生成技术列默认保护；不允许整表无提示覆盖。
5. **Impact**：来源技术列新增/删除/类型变更或授权撤销要标记映射 STALE，冻结已发布版本的原始来源引用。

## 五、Modeling → DataDev：复用真实 API，不重复发明任务系统

当前 [DevelopmentNodeController](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentNodeController.java) 有 `POST /api/v1/data-development/nodes` 新建节点；[DevelopmentTaskController](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentTaskController.java) 有 Draft 读写、发布验证/发布、Run、Lineage Preview 和 Standard Check；[DevelopmentTaskExecutionController](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/controller/v1/DevelopmentTaskExecutionController.java) 有运行记录、重试与取消。

**目前 `DevelopmentTaskApi.SaveDraftRequest` 主要承载 taskType/content/baseRevision 等，不包含强制的 `ModelVersionRef` 绑定；仅把 `modelId` 写入 SQL 文本注释或 URL 参数不能得到可信 provenance。**

拟议交接流程：
- **Prepare**：Modeling 按精确已发布物理 ModelVersionRef 提供只读交接包（源/逻辑/标准/列映射/版本、方言、设计校验和缺口）；它是受限快照，不拥有任务运行状态。
- **Validate**：DataDev 重新检查项目权限、Model Version/来源、目标数据源/库连接、Task 类型支持、运行资源和写入权限；不由 Modeling 根据设计方言推测目标可连接。
- **CreateDraft**：只有用户确认后，由 DataDev 自身创建 Node + Task Draft；把来源 ModelVersionRef 及包指纹以**DataDev 自己的正式关联/provenance**持久化，保留返回 Model 详情的回链。不同步骤部分成功要幂等恢复。
- **Publish / Run**：DataDev 自己管理 revision、发布校验、运行和执行日志。模型显示其事实投影，不新建运行表，不因为 SQL 生成就标记执行成功。
- **Reconcile**：Model 版本更新、撤回、来源/标准失效后，DataDev 应能显示当前 Task 所依赖的**旧精确版本**与影响、而不是自动漂移到“最新模型”。

接口和存储选择（拓展 Task Draft DTO、关系表或已存在可扩展字段）需要领域 Owner 评审，禁止通过前端多次请求伪造跨域原子性。

## 六、版本、迁移和回退策略

- 不修改旧 Flyway 已执行迁移脚本，不自动回填历史模型的猜测逻辑/标准来源；旧物理模型可继续被查看和管理，显示“未关联逻辑模型 / 来源映射待复核”。
- 新增的逻辑/标准/映射引用应遵守项目空间和现有权限模型，支持并发冲突与防重提交；若无精确历史版本，应该显示“历史证据不足”而非虚构可复现。
- 产品新路径应可在原专业入口逐步可用，数据迁移和兼容规则由对应 Owner 审核；不为漂亮的进度条对生产模型执行 SQL、发版或重新计算数据。
- Agent 只能生成可解释候选。若 Semantic 建议/Provider unavailable，专业手工流程仍然可执行。任何自动发布、目标库操作、数据写入都必须另行取得许可。
- 不区分 `EMPTY / FORBIDDEN / UNAVAILABLE / STALE / FAILED / UNKNOWN` 的实现不应宣称引导流程已正确完成。

## 七、代码落地前的最小联合评审证明

| 评审对象 | 责任 Owner | 证明材料 | 不通过时 |
|---|---|---|---|
| DR-01～DR-08 产品裁决 | Product + Business Owner | 填写每项 ACCEPT/REVISE/REJECT 与理由 | PD-010 保持 PROPOSED |
| 标准字段公共引用验证 | Semantic + Modeling | 非 ODS 列的合法身份/角色/项目/版本矩阵 | F-040 保持 DRAFT |
| 逻辑模型版本与映射 | Modeling | 关系基数、VersionRef、保存/发布/影响的身份关系图 | 不实现新的生成器 |
| ODS 来源与列 Mapping 一致性 | Metadata + Modeling | Preview/Confirm/Verify 协议、漂移/冲突/权限恢复 | 不改真实 ODS 来源 |
| ModelVersion→Task | Modeling + DataDev | Model Version Handoff、DataDev provenance 与权限策略 | 不开放“自动创建任务” |
| Runtime/质量/Metric/消费 | DataDev + 相关 Owner | 真实运行、质量报告、口径审批、访问和 Usage Evidence | Slice B 不能认定完成 |
| 历史迁移与审计 | Architect + Security + QA | 旧版兼容、项目隔离、追溯、幂等/取消与失败恢复 | 不上线生产 |

**工程 Slice A 以后才允许开工**：非 ODS 设计发布可防绕过；逻辑模型必须业务可回读；ODS 来源保存与逐列映射可信；专业入口保持兼容。**Slice B** 需真实 DataDev/质量/指标/数据消费闭环，不能以 PR 合并数或静态页可达代替 E2E。

## 八、当前状态和下一步

- 本文件是 `REVIEW_READY_PROPOSAL`，不等于领域 Owner 认可；
- PD-010 仍是 PROPOSED、F-040 仍是 DRAFT；
- 本轮不修改运行代码、不审批业务决策、不执行 SQL/DDL 或操作真实数据；
- 评审建议优先处理三项 P0 分歧：非 ODS 全字段强制标准规则与例外、逻辑属性到 StandardField 的正式引用合同、ModelVersion 到 DataDev 的受权持久关联。完成后产品负责人决定是否接受 PD-010，再据此批准 F-040 并集中实施。

## 九、正式产品与领域评审记录（2026-10-10；技术/产品方案审查，不代表 Owner 签署）

**评审对象**：PD-010、F-040、十屏 V2、V3 领域契约，以及当前 main 的相关代码。  
**审查结论**：**方案方向有条件通过，可以进入 Product Owner 的统一裁决；仍不能标记 PD-010 ACCEPTED / F-040 APPROVED，也不能据此启动改变正式用户行为的业务代码。** 本次代码核对属于静态审查，没有运行数据库/权限/任务 E2E；当前 PR 可合并为评审材料，合并不代表产品审批。

### 9.1 DR-01～DR-08 审查处置

| 决策 | 审查建议 | 是否尚需实质裁决 | 条件 |
|---|---|---|---|
| DR-01 双入口 | **推荐接受**，复用现有专业导航；首页落点可以在交互阶段微调 | Product 签署 | 不创建独立建设任务 Truth；权限缺失不展示真实资产名 |
| DR-02 ODS 例外 | **推荐接受**，源表保真、一表一模型只是默认候选 | Product 签署 | 不要求先有逻辑/标准字段，但来源身份/列证据和技术生成列可追溯 |
| DR-03 非 ODS 标准字段 | **推荐接受核心硬约束**：所有正式物理列均有合法 \`stdFieldId\`，草稿可带缺口、设计发布阻断；**首期不允许未批准的技术列例外** | Product + Semantic/Modeling 签署；与 \`stdMandatory\` 现状协调 | 技术审计列如确实必要，先成为标准字段或另行正式批准例外策略，不能偷偷略过 |
| DR-04 逻辑属性引用 | **推荐接受**：逻辑属性有稳定 ID，已确认语义引用 Semantic；允许待确认草稿，不复制第二标准库 | Product + Semantic/Modeling 签署 | 哪种逻辑版本可以“确认但暂缺标准”需写清；正式非 ODS 物理发布不得缺标准 |
| DR-05 业务关系 | **推荐接受**：证据和确认人必需；同名/外键只是候选，清单/关系表先行 | Product + 业务 Owner 签署 | 订单主表与明细可属同一过程、不同粒度，不能硬造新事件 |
| DR-06 逻辑→物理 | **有条件推荐接受**：允许一个逻辑版本形成多个物理目标，并可关联已有物理模型；实际支持的多对多/Join 复杂度分期 | Product + Modeling 签署详细映射规则 | Slice A 先完成可回读逻辑模型，生成的完整映射/冲突放 Slice B；别承诺首期任意 N:M SQL |
| DR-07 模型→DataDev | **有条件推荐接受**：真实 DataDev 拥有节点/任务/执行，Modeling 只提供冻结设计引用 | Product + DataDev/Modeling 签署版本/provenance | \`SaveDraftRequest\` 目前无正式 ModelVersionRef，不能仅凭 URL、SQL 注释或 \`configJson\` 任意文本称已绑定 |
| DR-08 价值验收 | **推荐接受**每日下单订单量作为黄金验证样例，不能默认为业务已确认口径 | Product + 业务/Metric Owner 签署 | 明确时区、取消单过滤、订单去重字段，再定义指标与运行质量规则 |

**说明**：上表“推荐接受”是本轮审查建议，**不是**已签署的 Product Decision；需要有权限的产品/领域负责人按现行 Product Change Process 留下正式决议及日期。不要用用户一句“继续评审”冒充已对所有例外/版本细节做过批准。

### 9.2 本次发现并修正的跨域事实与 P0/P1 风险

**P0-A｜字段角色存在两套不同的分类，不可自动映射**

- Semantic 的 \`StandardField.role\` 是 \`PROCESS / DIMENSION / METRIC\`；其中 PROCESS 是**标准字段角色**，不是 BusinessProcessId。
- Modeling \`ModelStructureService\` 的物理分析字段角色是 \`DIMENSION / MEASURE\`，而 \`MEASURE\` 当前还要指定 \`aggregateFunc\`。
- **禁止**把 \`StandardField.ROLE_METRIC\` 静默映成物理 \`MEASURE\` 并替所有 DWD 金额字段设置 \`SUM\`；标准字段的业务身份与物理列在特定汇总建模中的聚合行为是两回事。金额字段可以符合标准但尚无聚合函数，后续 DWS/ADS/Metric 的口径应显式定义。
- 合同建议：字段符合标准 = 有合法标准字段稳定引用及所需数据标准，不要求“分析角色”和标准字段角色字符串相同。物理 \`fieldRole/aggregateFunc\` 由具体分层/模型用途确认，不能为通过标准准入而无依据硬加聚合。后端类型/角色校验要与此分离，非 ODS 准入不能靠角色文本对齐。

**P0-B｜标准字段准入不等于只判 \`stdFieldId != null\`**

需要 Semantic Owner 返回引用存在、当前项目合法/可访问、状态 ENABLED、角色适用、TYPE/UNIT/CODE/CALIBER/SECURITY 引用合法和适当的标准/字段版本指纹。UI“未治理=stdTypeId 缺失”不能当正式准入结果，所有物理发布入口必须走同一服务端结果。草稿缺口可提示，但非法发布拒绝须逐列给出具体原因。

**P0-C｜逻辑模型版本与物理版本不能按“当前最新”关联**

现有 \`LogicalModelVersion.create()\` 创建默认 DRAFT；当前没有完整正式发布、确认/回滚及逻辑→物理生成服务。生成方案先用明确的逻辑草稿 revision 或已确认逻辑版本作为输入，并在用户确认生成时核验其未变化；正式发布的物理模型引用其**确切**逻辑版本。不得把“已有逻辑版本表”当作可用的已批准版本机制。

**P0-D｜来源保真导入必须容纳未确认/冲突状态**

ODS 导入 UI 的同名 17/17 映射是**示例**。真正的预览必须对照采集证据、技术列、目标列是否被人工改名、数据源权限以及当前结构指纹给出准确数量；不要将“候选 100%”冒充正式保存的来源血缘。单用户提交如存在结构成功/列映射失败，回执必须分项可恢复，不得伪装为原子成功。

**P1-E｜模型版本指纹的真实现状应精确描述**

[ModelVersionService.semanticView](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/version/ModelVersionService.java) **已经包含列上的 \`stdFieldId\`、\`stdTypeId\`、各类别标准 ID、\`transformExpr\` 等结构内字段**；因此切换物理列绑定的 \`stdFieldId\` 或其结构内表达式，已可能改变当前结构语义指纹并产生新物理版本。

真正不足的是：\`serialiseMeta\` 只保存名称、描述、layer/dialect/domain，**没有将 BusinessProcessRef、SourceRef、逻辑版本、独立 MappingRepository 的来源规则、Semantic 标准字段自身的版本和有效性证据纳入同一冻结合同**。若结构不变、外部这些关系变化，当前“仅以结构语义投影去重”仍可能将新业务语义错误地沿用旧设计版本。建议扩展不可变设计证据关联并使版本判等覆盖真正变更的设计引用，保留历史兼容；不能说当前“所有标准变动一律不触发版本”。

**P1-F｜DataDev 任务定义可以直接运行当前编辑内容，不能把运行当保存**

[DevelopmentTaskApi.RunRequest](https://github.com/gitfortian/data-ops/blob/main/data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/api/DevelopmentTaskApi.java) 注释明确“运行当前编辑定义，不隐式保存或发布”；\`SaveDraftRequest\` 是 taskType/schemaVersion/content/configJson/baseRevision，未有正式 ModelVersionRef。后续若模型交接直接展示“创建并运行”，必须区分新建节点、草稿保存、发布修订版、运行当前编辑内容/发布版以及返回的真实 ExecutionRef。运行记录不能伪造模型精确版本血缘。

**P1-G｜Semantic Project Scope 不可由“全局标准字段”注释推断为跨项目共享**

\`StandardField\` 的源码注释叫“全局标准字段”，但 [PD-003](https://github.com/gitfortian/data-ops/blob/main/docs/product/decisions/PD-003-business-semantic-metric-contract.md) 已明确现有 Standard 身份采用 \`(project_id, kind, std_code)\`，写入 API 又从可信项目上下文解析。因此设计文本中的“全局”仅应理解为“当前业务体系可复用的标准字段”，**不能直接授权跨项目引用或推断所有项目共用同一 Field ID**。正式校验由 Semantic 的 Project scope 与权限合约定义，不由 Modeling 自己猜测。

### 9.3 Slice A / Slice B 实施边界审查

**Slice A 的完整成果**（下一阶段一条跨域价值切片，可用少量集中 PR）：从授权的元数据来源创建可持久回读的 ODS 结构 + 来源列引用；有业务过程/标准字段的可解释审阅与原域引用；逻辑模型实体、属性、关系草稿/版本可回读；逻辑→物理、标准治理缺口可准确预检。**绝不声称已部署 Doris、运行数据或产生正式 DWD 加工任务。**

**Slice B 的完整成果**：逻辑版本→合规 DWD/DIM 物理设计→精确物理版本→受权 DataDev Task Draft/Revision/Execution→质量、安全和血缘→已确认 Metric→实际 Dataset/Service 查询与使用回执，必须真实 E2E。Slice A、Slice B 不为了省 PR 把“真运行 E2E”做成仅 UI mock。

### 9.4 建议 Product Decision 的批准条件（可一次性裁决）

要将 PD-010 置为 ACCEPTED，必须由负责人明确批准：① DR-01～DR-08 的总体方向；② **全非 ODS 物理列** 的标准字段强约束、正式发布门禁和首期**零未批准豁免**；③ 逻辑属性引用 Semantic 而不复制标准；④ LogicVersion / ModelVersion / DataDev TaskVersion 三套身份及来源冻结关系；⑤ 首个黄金验收用“订单量”，业务实际口径另行确认。有关具体 UI 控件颜色/布局、多表 N:M 高级表达式和未来 SLA 不阻断 PD 审批，可留在 Feature/实现设计中。

批准后更新 F-040 至 APPROVED（需依赖 Owner 合同已签），准备 Domain/Architecture 更新与 Slice A 的**整体**工程实现；否则保持本次评审材料为 DRAFT，不擅自开展改变业务规则的代码修改。

**评审结论记录**：\`TECHNICAL_PRODUCT_REVIEW=CONDITIONAL_PASS\`；\`PRODUCT_OWNER_APPROVAL=PENDING\`；\`DOMAIN_OWNER_CONTRACTS=PENDING\`；\`E2E=NOT_EXECUTED\`。
