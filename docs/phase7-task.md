# Phase 7 收尾方案

**建议把 Phase 7 当作一次产品验收与范围校准，而不是一次批量关 issue。**当前 `main` 上已有不少治理能力，但尚不能证明 [总目标 #185](https://github.com/gitfortian/data-ops/issues/185) 所说的“围绕一个资产完成发现、理解和治理查看”已经闭环。`phase7` 标签目前仍是 **18 个 open、8 个 closed**。

## 一、先统一完成口径

目标用户是查找和使用数据的业务用户，以及处理元数据、质量、血缘和安全问题的治理人员。用户应从 **Asset 目录找到对象**，在同一个资产上下文判断它是什么、谁负责、质量与安全状况如何、来源及下游是什么，再进入拥有事实的专业域处理问题，并返回原资产。

这遵循已接受的 [PD-001 产品决策](../docs/product/decisions/PD-001-asset-governance-hub.md#L89) 和已批准的 [F-001 Feature Spec](../docs/product/features/F-001-asset-governance-hub.md#L585)：Asset 负责入口和聚合；Metadata、Quality、Lineage、Security、Lifecycle、Metric 仍各自拥有事实。现有 Section、搜索、资产目录、专业工作台和回链应继续复用。[Section 契约](../docs/product/features/F-001-A-asset-section-contract.md#L336)要求明确事实 Owner、适用性、状态、原因和下一步操作。

这也解释了 issue 的一部分分歧：早期 Phase 7 任务希望建设独立的 Metadata Profile、Metadata Detail 和单体 Asset Profile；现行产品契约选择 Asset 主入口及独立加载的治理 Section。**这些任务需要按现行契约修订，不能仅凭原定类名或页面不存在就判定失败，也不能将重定向页面算作旧方案全部完成。**

## 二、26 个 issue 的处理清单

| 处理方式 | Issue | 具体动作 |
| --- | --- | --- |
| 保持 open，作为总验收 | [#185](https://github.com/gitfortian/data-ops/issues/185) | 七项能力和跨域 E2E 证据齐备后最后关闭。 |
| 更新后可关闭规划任务 | [#193](https://github.com/gitfortian/data-ops/issues/193) | 实施任务已经拆出；先把旧的“Metadata → Asset”入口顺序改成 PD-001 的 Asset 主入口，补上新范围和子任务链接，再以“规划完成”关闭。 |
| 保持 open，补交付与验收 | [#186](https://github.com/gitfortian/data-ops/issues/186)、[#187](https://github.com/gitfortian/data-ops/issues/187)、[#189](https://github.com/gitfortian/data-ops/issues/189)、[#191](https://github.com/gitfortian/data-ops/issues/191)、[#192](https://github.com/gitfortian/data-ops/issues/192) | 分别按下文的资产、质量、血缘、生命周期、安全验收清单推进。 |
| 保持 open，先改写范围 | [#188](https://github.com/gitfortian/data-ops/issues/188)、[#194](https://github.com/gitfortian/data-ops/issues/194) | Metadata 定位为采集、对账、技术实体查询和诊断；通用发现与治理画像由 Asset 承载。 |
| 保持 open 的实施父任务 | [#195](https://github.com/gitfortian/data-ops/issues/195)、[#196](https://github.com/gitfortian/data-ops/issues/196)、[#199](https://github.com/gitfortian/data-ops/issues/199)、[#200](https://github.com/gitfortian/data-ops/issues/200) | 更新子任务、真实缺口与验收证据；在对应 Feature 验收前不关闭。 |
| 旧设计需重写或标记被替代 | [#201](https://github.com/gitfortian/data-ops/issues/201)、[#204](https://github.com/gitfortian/data-ops/issues/204)、[#205](https://github.com/gitfortian/data-ops/issues/205)、[#206](https://github.com/gitfortian/data-ops/issues/206) | 将“Metadata 跨域画像／专属治理详情／独立治理 SPI／单体 Asset Profile”逐项映射到现有 Asset Section 和 Metadata 技术详情。保留仍未满足的用户结果，关闭已被新契约替代的实现要求，并在 issue 中说明依据。 |
| 保持 open，确有差距 | [#209](https://github.com/gitfortian/data-ops/issues/209) | 现有 [HealthScorer](../data-ops-business/data-ops-business-asset/src/main/java/io/yak/ops/business/asset/health/HealthScorer.java#L10) 有可解释评分，但与 issue 要求的质量、安全、使用、生命周期统一健康画像并不等同；先确定产品口径，再实现或修订该票。 |
| 已 closed，补核验记录 | [#190](https://github.com/gitfortian/data-ops/issues/190)、[#197](https://github.com/gitfortian/data-ops/issues/197)、[#198](https://github.com/gitfortian/data-ops/issues/198)、[#202](https://github.com/gitfortian/data-ops/issues/202)、[#203](https://github.com/gitfortian/data-ops/issues/203)、[#207](https://github.com/gitfortian/data-ops/issues/207)、[#208](https://github.com/gitfortian/data-ops/issues/208)、[#210](https://github.com/gitfortian/data-ops/issues/210) | 不因父 Feature 仍 open 而机械重开；逐项补对应的 `main` 提交、测试和用户路径。若验收不成立，再重开或建明确的缺口票。 |

## 三、开发与产品决策的顺序

### 1. 先完成范围校准

把 #185、#186–#200 的验收项整理成一份矩阵，逐条标记：

- **已实现并有证据**：保留代码，补验收链接。
- **已有实现但不满足用户结果**：明确最小差距和事实 Owner。
- **旧方案与当前契约冲突**：写出 PD-001、F-001 的对应条款，修订或标记被替代。
- **需要新增产品概念**：先走现有产品治理流程，再拆开发任务。

最后一类尤其包括：质量问题处理生命周期、统一质量评分、资产健康度新公式、归档管理、对象级合规状态。它们不能因为旧 issue 写过，就直接增加状态机、第二份事实或新导航。[PD-001](../docs/product/decisions/PD-001-asset-governance-hub.md#L398)本身也没有决定健康度最终评分公式或各专业域内部实现。

### 2. 优先补 Asset 主路径

先验证“搜索 → 资产详情 → 专业域 → 返回资产”在物理表、Model、Metric、Dataset 上成立。针对现有 [Asset Detail Section 实现](../data-ops-business/data-ops-business-asset/src/main/java/io/yak/ops/business/asset/application/AssetDiscoverService.java#L155)，补齐对象身份、事实来源、状态原因、权限与回链，而不是创建新的聚合真相。

Metadata 的采集、搜索、实体技术详情保留；旧 `/metadata/explorer`、`/metadata/detail/:id` 深链已经[映射至现有入口](../data-ops-ui/config/routes.ts#L76)，需要验收的是用户能否定位同一个实体及其 Asset，而不是再造一套并列治理详情。

### 3. 再补各治理域的真实缺口

- **Quality**：现有规则模板、监控报告和资产质量摘要可以复用。若 #187 的“谁处理、是否恢复”仍是目标，应先定义问题身份、责任与状态归属，再实现处理和回链。监控执行中的失败规则不能直接冒充有生命周期的“质量问题”。
- **Lineage**：核验字段级来源/去向、证据、影响范围与 Asset/Metric 入口。影响结果须标明是血缘关系还是已知消费证据；[J4 用户旅程](../docs/product/USER_JOURNEYS.md#L39)不允许把下游血缘推断成真实 Consumer。
- **Metric/Semantic**：已关闭的 #190、#198 以现有指标定义、标准关联、版本、血缘和使用引用为基础，补一条业务用户按术语找到指标并解释口径与来源的 E2E 记录。
- **Lifecycle**：现有 Asset Section [只支持 Model TTL](../data-ops-business/data-ops-business-lifecycle/src/main/java/io/yak/ops/business/lifecycle/asset/LifecycleAssetSectionProvider.java#L32)。先确认 #191、#199 是否要求扩大资产类型、展示执行历史和存储趋势，以及“归档管理”的确切用户动作；已批准范围之外的部分先形成 Feature 契约。
- **Security**：现有资产摘要能展示分级、适用 READ 规则数及脱敏覆盖，但[对象级合规结果仍明确为不可用](../data-ops-business/data-ops-business-security/src/main/java/io/yak/ops/business/security/asset/SecurityAssetSectionProvider.java#L173)。先确定合规事实能否稳定映射到对象；同时验证访问策略展示不被误读为某用户的最终访问裁决。

实施上建议每个 PR 只覆盖一个稳定边界：**源域只读事实与测试 → Asset Section 适配 → UI 状态和回链 → 对应 E2E 证据**。这样每个 issue 都能独立审核和关闭。

## 四、Phase 7 最终验收包

至少准备以下真实数据场景，并保存 API 结果、页面操作记录、关键测试与 `main` 提交：

| 场景 | 必须证明 |
| --- | --- |
| 物理表 | 从 Asset 搜索进入；查看技术字段、Owner、Quality、Security、Lineage、Usage；能进入对应专业对象并返回。 |
| Model | Lifecycle 展示真实 TTL 策略和状态；不适用的 Quality／技术元数据分区如实显示 `NOT_APPLICABLE`。 |
| Metric | 能看定义、标准、版本与来源；Asset 与 Metric 专业详情可往返；不适用的物理表事实不伪造。 |
| Dataset | 页面浏览、声明订阅、实际成功消费分别标明来源；不能把浏览次数称作业务使用。 |
| 异常与权限 | 某个源域不可用时仅该 Section 为 `UNAVAILABLE`；明确无记录为 `EMPTY`；无权限为 `PERMISSION_DENIED`；跨 Project 不串数据。 |
| 治理问题 | 若批准质量问题处理或对象级合规等新增范围，展示从发现、定位、处理到结果复核的完整路径。 |

这与 [F-001 的四类 E2E 场景和证据要求](../docs/product/features/F-001-asset-governance-hub.md#L585)一致。**验收证据**证明功能符合契约；**成功指标**另行记录用户是否更容易找到对象、理解风险和完成处理，不能用测试通过替代产品效果。

## 五、关闭规则与交付顺序

1. 先修订旧 issue 的范围和关联，再补缺口；不批量关闭。
2. 每项实现进入默认分支 `main`，相关 CI、契约测试及对应 E2E 通过后，关闭细任务。
3. 细任务全部有结论后关闭实施父任务；Feature 的用户结果达成后关闭 Feature；最后关闭 #185。
4. 同步更新产品文档状态和实施证据。当前 [PD-001](../docs/product/decisions/PD-001-asset-governance-hub.md#L3)仍写 `Implementation: NOT_STARTED`，与已存在的实现不符；应依据核验结果更新为准确的部分实施状态，完成所有接受条件后才写 `DONE`。
5. 先解决交付门槛：[PR #219](https://github.com/gitfortian/data-ops/pull/219)说明未运行测试，其 [Product Guard](https://github.com/gitfortian/data-ops/actions/runs/36545395281)因缺少变更类型声明失败。后续不能仅凭 PR 已合并或页面可见判定 Phase 7 已验收。

按这个顺序，**#193 最可能是规划完成后的收尾遗漏；#201/#204/#205/#206 主要是旧设计需要治理；#209 及质量、生命周期、安全等 Feature 则包含真实未完成项。**当前不宜宣布 Phase 7 完成。