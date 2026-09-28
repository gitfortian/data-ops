# Phase 4 最新 main 深度审核

审核日期：2026-09-28。审核提交：`56eefb6e378c291ff3e47079ecf5f52d2edc003d`。

**结论：当前实现不能按现行 PD-002 / F-004 判为完成受治理消费闭环。** 模型和部分来源执行边界已经落地，但实际消费到 normalized Usage 的生产接线、代表 Consumer 的订阅授权、可解释 Access、治理证据和真实生产者回链仍有断点。任务关闭记录也明确承认真实环境验收被延后。

本报告的问题编号描述审核时的 `main` 基线。随后按用户要求在 `codex/fix-phase4-review` 实施了非人工修复：S-01/S-02/S-03、F-01/F-03/F-04/F-05/F-06/F-09/F-10 已有代码修复；F-07/F-08 已接入成功来源事件和 Impact 读取时的来源重对账，并在来源不完整或达到 200 条上限时 fail closed。F-02 目前只能把缺失的分区和原因明确投影为 `UNAVAILABLE`，并取消仅凭 `updateTime` 伪装治理 READY；可信 Quality/Security/Lineage 读取仍需现有 owner 提供可安全关联的证据接口。F-07/F-08 的跨窗口持久化补偿与最终状态、F-11 的外部任务关闭记录冲突也仍未解决。真实环境 E2E 和故障注入按用户要求未执行。

发现分为两个独立审核轴：工程规范（Standards）3 项，需求符合性（Spec）11 项。下文区分确定缺陷、验证限制和待验证风险；没有把推测性代码气味算作缺陷。

## 审核基线与产品范围

- 已执行 `git fetch origin main`，本地 main、origin/main、HEAD 都为上述 SHA。
- 用 `git diff ea9cf13...56eefb6 -- <Phase4 相关路径>` 追溯从产品契约冻结到当前 main 的实现；同时检查当前完整文件与生产调用关系。期间穿插的 Phase 2 / 5 / 6 不作为 Phase 4 新需求。
- 当前权威：`PRODUCT_STYLE.md`、`docs/product/README.md`、**ACCEPTED** PD-002、**APPROVED** F-004、Dataset / Data Service / Security 的领域和工程契约、`CODE_STYLE.md`。
- #100–#104 及其评论只作为交付状态和验收证据，不替代上述权威契约。
- 检查范围覆盖 Consumption 后端、Dataset Query、Data Service Public Invoke 与来源记录、订阅/Usage 持久化、Boot 接线、消费目录/规范详情/导航、真实验收脚本及 CI。

| 必答项 | 本次审核的产品解释 |
|---|---|
| User | Dataset 消费者、Data Service 集成开发者、判断已知消费者和变更影响的 Owner |
| Problem | 生产、治理、访问裁决和消费证据分散，不能完成可信消费与影响判断 |
| Capability | 现有“数据消费与服务”能力内的 Consumption Hub |
| User Journey | J3：发现 → 规范契约 → 真实授权消费 → Usage → Consumer/Impact → 稳定回链；支持 J4/J5 |
| Expected Outcome | 用户知道能否使用、如何使用、用了哪个版本、证据来自哪里、影响谁 |
| Truth Owner | Dataset/Data Service 拥有来源；Security/既有机制拥有授权；Consumption 仅拥有 Subscription 和 normalized Usage；Asset/Quality/Lineage 保留各自事实 |
| Producer / Consumer | 来源发布及实际执行入口生产事实；Hub、Asset、Consumer/Impact 消费事实 |
| Reuse | Project Space、RBAC、Dataset Query、Data Service Consumer/API Key/IP policy、Asset、Quality、Security、Lineage、来源审计 |
| E2E acceptance evidence | 登录态 Dataset Query 与真实 Consumer/API Key 公共 Invoke 两条可重复路径，含授权、稳定 ID、版本、Usage、Impact、回链、失败及恢复 |

## Standards：工程规范审核

### S-01 [P1] CI 白名单遗漏新增 Golden 回归，当前 main 已有失败却不受保护

**位置**：[consumption-checks.yml:45](D:/tianxy/code/data-ops/.github/workflows/consumption-checks.yml:45)、[CODE_STYLE.md:241](D:/tianxy/code/data-ops/CODE_STYLE.md:241)。

该工作流只选择 10 个基础契约、Provider、Service 和 Normalizer 测试，另一步只跑 Boot assembly。新增 `*Golden*`、Data Service 公共授权和 runtime failure 测试没有进入白名单。本次扩展执行已发现 Consumption 2 个失败、Data Service 1 个失败，原白名单不能发现这些回归。Dataset 诊断测试也出现 5 个断言失败和 1 个错误。

CODE_STYLE §14 要求保护领域行为、幂等/恢复和架构边界。这里不是代码格式问题，而是关键验收证据实际失效。

**最小修复边界**：Consumption CI 与两类来源的 Phase 4 行为回归。执行完整 Consumption 测试，并包含 Query / Invoke / 诊断 / 授权回归；修正失效测试，保留业务断言。**验证**：下文列出的当前失败应能使 CI 失败，修复后全绿，不能通过删测试或忽略失败达成。

### S-02 [P2] TypeScript 未正常启动也能被报告为通过

**位置**：[consumption-checks.yml:79](D:/tianxy/code/data-ops/.github/workflows/consumption-checks.yml:79)、[check-consumption-type-errors.mjs:23](D:/tianxy/code/data-ops/yak-ops-ui/scripts/check-consumption-type-errors.mjs:23)。

工作流容忍 tsc 非零退出，checker 只识别 `error TS数字` 行。因此 `error Command "tsc" not found.` 这样的启动失败既没有业务文件诊断，也被输出为 PASS、退出 0。已用合成失败日志实际复现。

CODE_STYLE §14 要求验证规则可执行；“没有解析到诊断”不能证明编译器执行成功。

**最小修复边界**：工作流和 checker。分别识别正常完成且存在允许的历史诊断、启动/配置/进程失败。**验证**：缺 tsc、致命错误、空输出且异常退出均失败；合法历史诊断按既定策略处理。此次实际 tsc 运行完成，退出 2，184 条历史诊断未归属 Phase 4 页面；这与合成启动失败是两个不同证据。

### S-03 [P2] 并发重复 Usage 投递被误报为 Provider 不可用

**位置**：[UsageEvidenceService.java:20](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/UsageEvidenceService.java:20)、[MybatisUsageEvidenceRepository.java:37](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/persistence/MybatisUsageEvidenceRepository.java:37)、[V2__usage_evidence_truth.sql:19](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/resources/db/migration/yak-consumption/V2__usage_evidence_truth.sql:19)。

`findByDeduplicationId → insert` 没有处理两实例同时读到不存在的竞争。唯一键保证不产生第二行，但输者收到 `DuplicateKeyException`；两个 Normalizer 捕获后返回 `UNAVAILABLE`，把正常重复投递解释成证据服务故障。临时 Java probe 已验证异常外溢路径。

违反 CODE_STYLE §1/§14 对线性化点、幂等和重试的要求。**不能据此断言已保存的赢者证据丢失。**

**最小修复边界**：Usage Repository 的原子写入和已有记录读回。**验证**：两个并发投递得到同一 evidence ID，双方不返回 UNAVAILABLE；保留真实数据库故障的失败语义。

## Spec：需求符合性审核

### F-01 [P1] 真实生产者回链不能返回实际 Development 对象

**位置**：[DatasetDataProductProvider.java:113](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DatasetDataProductProvider.java:113)、[CanonicalProductService.java:83](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/discovery/CanonicalProductService.java:83)、[task/index.tsx:10](D:/tianxy/code/data-ops/yak-ops-ui/src/pages/development/data-development/task/index.tsx:10)。

SQL_QUERY Dataset 的版本没有 TaskAsset 来源，实际字段值为 0，Provider 仍创建 `TASK_ASSET:0`，链接为 `/data-development/task/0`。QUERY_REVISION 的 TaskAsset ID 也不能直接当作 Development Node ID。该前端路由还会无条件重定向 `/data-development`，丢失定位对象。

真实 Data Service source type 是 `DATA_DEVELOPMENT_DATA_SERVICE`，canonical producer resolver 只认识 TASK/TASK_ASSET/DEVELOPMENT_TASK，因此真实来源生成空 producerHref。测试使用的虚拟 DEVELOPMENT_TASK 掩盖了这个断点。两种结果均已用生产类复现。

**违反**：PD-002 D11，F-004 §6.3、§15.5，要求稳定返回生产者。**最小修复边界**：来源 owning API 提供真实 producer/node identity，导航复用 `/data-development?nodeId=…` 的现有定位能力。**验证**：SQL_QUERY、QUERY_REVISION、真实 Data Service 来源分别定位正确 Node；不允许 0 ID、名称匹配或丢失目标的重定向。

### F-02 [P1] 必填 Quality/Security/Lineage 分区缺失，READY 空事实不足以构成治理证据

**位置**：[DatasetDataProductProvider.java:105](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DatasetDataProductProvider.java:105)、[DataServiceDataProductProvider.java:118](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DataServiceDataProductProvider.java:118)、[CanonicalProductService.java:98](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/discovery/CanonicalProductService.java:98)。

两 Provider 只提供 source-governance、ownership、visibility、asset 四类 section。quality、security、lineage/provenance 连明确状态也没有。Canonical 把 section 复制为 `facts={}`，有 Asset 时补几项 Asset 摘要，并未组合 Quality/Security/Lineage 的来源证据。

`source-governance=READY` 只依赖来源对象有 updateTime；没有治理事实或 evidence ref。复现的无 Asset 对象仍返回 READY 空 facts，不能据此判断可信、安全或来源关系。

**违反**：PD-002 D3/D9/D12，F-004 §10（487–489 行）、§15.5（600 行）。**最小修复边界**：只读证据组合；来源正常为空、不可用、无权读取、不适用分别表达，不复制 owning Truth。**验证**：真实质量/安全/血缘或明确状态、来源引用和时间；至少一项可解释真实治理事实，单凭 updateTime 不能使 Golden 通过。

### F-03 [P2] Data Service interface 没有完整请求和响应 schema

**位置**：[DataServiceContractPayload.java:6](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/model/DataServiceContractPayload.java:6)、[DataServicePublisher.java:145](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/publication/DataServicePublisher.java:145)、[DataServiceDocumentationReader.java:31](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/documentation/DataServiceDocumentationReader.java:31)。

canonical payload 只有 runtimePath、参数名及运行设置，没有 method/protocol、参数类型/必填约束或响应 schema。发布器已经保存 ParameterDoc/ResponseFieldDoc，当前投影未复用这份真实契约，集成用户仍需离开规范详情拼接接口定义。

**违反**：PD-002 D3（126–130 行），F-004 §3.3（149–155 行）。**最小修复边界**：Data Service 专业 payload、既有 Documentation Reader、规范详情展示。**验证**：参数/响应类型和约束来自 owning contract；republish 后 active revision 与显示 schema 一致。

### F-04 [P1] 已发布 Dataset 的停用被混入不可发现状态

**位置**：[DatasetDataProductProvider.java:61](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DatasetDataProductProvider.java:61)、[DatasetDataProductProvider.java:152](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DatasetDataProductProvider.java:152)。

已有 immutable version 的 Dataset 转 OFFLINE 后，get 返回 NOT_DISCOVERABLE，search 也排除它。Dataset 来源契约规定下线只改变身份状态，不修改已发布版本，只禁止 Query；F-004 又要求停用不能改写发布 lifecycle。当前 canonical 上下文随运行停用消失，用户无法从这里理解停用原因或进入既有证据。Impact API 本身仍可读关系记录，不能把此问题写成“历史证据已删除”。

**违反**：F-004 §15.2（576 行）；Dataset REQUIREMENTS §5。**最小修复边界**：先由 Dataset owner 明确发布、availability、discoverability 的映射，再投影；保持 Query 阻断。**验证**：ONLINE→OFFLINE→ONLINE 不改变稳定 ProductKey/版本，已发布详情和历史证据入口可解释，执行权限与 availability 正交。

### F-05 [P1] Golden runner 接受 Usage/治理/授权/导航均不完整的响应

**位置**：[phase4-real-env-acceptance.mjs:197](D:/tianxy/code/data-ops/scripts/product/phase4-real-env-acceptance.mjs:197)、[phase4-real-env-acceptance.mjs:253](D:/tianxy/code/data-ops/scripts/product/phase4-real-env-acceptance.mjs:253)。

captureConsumption 只检查 FOUND、ProductKey 和 navigation 非 null。返回 `{state:NOT_FOUND, canonicalHref:null}` 的导航仍过关；Access=UNAVAILABLE、治理=[]、Usage=EMPTY、consumers=[] 也不失败。对原脚本函数进行 Node vm 复现，结果为 accepted=true。Dataset 子 runner 只保存 QueryPerformance，不要求该 queryId 对应 normalized Usage。

因此 bundle 中 `sourceNavigationResolved=true`、`consumerImpactCaptured=true` 只证明响应被读取，不能证明其产品语义成立。脚本诚实保留 remainingManualFaultInjection；本项是验收门缺陷，不是伪造真实执行。

**违反**：F-004 §8/§10/§15.5（598–602 行）。**最小修复边界**：runner 强断言 active contract、动作/平面裁决、此次成功消费关联的 Usage/Consumer、真实治理证据、导航 FOUND 与正确目标；未执行矩阵项必须阻止完整产品通过。**验证**：上述不完整模拟响应逐一失败，两条真实路径分别带稳定关联证据通过。

### F-06 [P1] 通用只读用户能够冒充 Consumer 创建和取消订阅

**位置**：[SubscriptionController.java:29](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/SubscriptionController.java:29)、[SubscriptionService.java:35](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/SubscriptionService.java:35)、[SubscriptionService.java:82](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/SubscriptionService.java:82)。

创建/取消入口仅继承 asset:read。ConsumerRef 完全来自请求；service 只检查 actor 非空、当前 Project 和产品可发现性，不验证操作者能否代表该 User/Team/Dashboard/Job/Service，也不验证 consumer 是否真实存在、属于哪个 Project。取消时仅按 Project+subscriptionId 定位，任意同项目读者可撤销他人的声明依赖。

临时 Java probe 使用真实 service 已复现：mallory 为 USER:alice 创建 ACTIVE 订阅，并取消 alice 创建的订阅。测试 double 只替代持久化/项目/产品查询，没有替代待审核授权逻辑。

**违反**：F-004 §15.4（588 行）、#103 的“谁可代表 Consumer 创建/撤销”。**最小修复边界**：Consumption command 授权与 Consumer owning domain 的代表权限解析；不同 Consumer 类型的合法代表规则先冻结，不能直接用通用 read 代替。**验证**：alice/mallory、同/跨 Project、真实/不存在 Consumer、管理员合法代操作矩阵，拒绝后零写入；订阅仍不授予 Query/Invoke 权限。

### F-07 [P1] 实际 Query/Invoke 到 Usage 的生产链路没有闭合，最近 200 条回放也不足以保证恢复

**位置**：[DatasetUsageEvidenceSynchronizer.java:20](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/source/DatasetUsageEvidenceSynchronizer.java:20)、[DataServiceUsageEvidenceSynchronizer.java:25](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/source/DataServiceUsageEvidenceSynchronizer.java:25)、[DataServiceUsageEvidenceController.java:38](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/source/DataServiceUsageEvidenceController.java:38)。

全仓生产调用检索确认 Dataset 同步器只有定义和内部重载调用，没有 Controller、事件、后台任务或实际执行入口调用它。成功 Query 只记录 QueryPerformance，normalized 表不会因这次 Query 自动产生证据。Golden 单测由测试代码显式调用 normalizer，不能证明生产接线。

Data Service 也没有自动交付调用者，只有人工 POST synchronize 和验收脚本触发。同步器仅回放最新最多 200 条，没有 pending/cursor/分页扫描、持久投递状态或按历史 evidence ID 重试。故障期间若新增 201 条，最老未投递记录被挤出窗口；正常 retention 后还可能永久失去来源记录。

**违反**：F-004 §15.4（590–592 行）、§15.5（597–598 行）。**最小修复边界**：来源持久证据到 Consumption 的可恢复投递接线；使用 owning boundary，不让归一化失败改变成功消费响应。**验证**：无需管理员操作的真实 Query/Invoke→Usage；重启/双实例/故障期间超过 200 条；重试同一来源引用且不重复；积压及最终状态可诊断。

### F-08 [P1] Impact 把未归一化、未归属和来源写入缺口显示为 EMPTY

**位置**：[ConsumerImpactService.java:43](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/relationship/ConsumerImpactService.java:43)、[DatasetQueryPerformanceRecorder.java:94](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-dataset/src/main/java/io/yak/ops/business/dataset/observability/DatasetQueryPerformanceRecorder.java:94)、[DataServiceInvoker.java:126](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/execution/DataServiceInvoker.java:126)。

Impact 只读 normalized repository，列表为空就 EMPTY。它没有来源 provider 健康、来源写入缺口、待归一化积压、anonymous/legacy 未归属结果等输入。Normalizer 的 GAP/UNAVAILABLE 结果没有持久状态供 Impact 读取；来源写入失败只留 fallback/日志，进程重启后本地记录可能消失。于是“成功调用已发生但证据尚未投递/无法归属/已丢失”与“已确认没有 Usage”呈相同状态。

服务查询异常会正确返回 UNAVAILABLE，但 normalized 表可读不代表来源覆盖完整。此次 probe 确认空 normalized repository 就得到 EMPTY，与来源状态无关。

**违反**：PD-002 D6/D9，F-004 §4.4、§15.4（590–592 行）。**最小修复边界**：来源覆盖/投递诊断事实及 Impact 只读组合，不创建第二份调用业务结果。**验证**：源记录失败、归一化失败、legacy/匿名、恢复、确实无调用分别表达；0 只能解释为当前已归一化窗口内的结果，不能推断无消费者。

### F-09 [P1] Access 永久硬编码 UNAVAILABLE，未表达主体、动作和调用平面

**位置**：[DatasetDataProductProvider.java:123](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DatasetDataProductProvider.java:123)、[DataServiceDataProductProvider.java:136](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/provider/source/DataServiceDataProductProvider.java:136)、[AccessProjection.java:4](D:/tianxy/code/data-ops/yak-ops-business/yak-ops-business-consumption/src/main/java/io/yak/ops/business/consumption/product/model/AccessProjection.java:4)。

两 Provider 对每个产品都返回“由 #103 交付”的 UNAVAILABLE，Discovery/Canonical 没有后续 enrichment；AccessProjection 只有 decision/state/reason，没有主体、动作、plane 或可执行下一步。生产里真实 RBAC/API Key/IP gate 已部分实现，但规范详情无法解释谁能看、谁能 Query/Invoke，也无法区分 provider 真故障和能力尚未接入。前端仍显示“待 #103 接入”，而 #103 已关闭。

Dataset Query 当前只调用通用 dataset:query RBAC。未发现 Security owner 明确 Dataset 对象/动作与物理资源策略关系的新增正式契约；不能把这一点宣称为全部对象级 Security 已闭环，也不能据此扩展完整 ABAC 范围。

**违反**：PD-002 D3/D6，F-004 §15.3（580–584 行）。**最小修复边界**：动作/主体/平面 Access 只读投影及 Dataset 的适用策略契约；继续由实际执行入口重新裁决，公共 Key 的许可不由登录用户权限冒充。**验证**：allowed view+forbidden consume、真实 provider unavailable、可执行 Request/Owner 联系路径、登录平面与公共 Invoke 分开。

### F-10 [P1] 规范消费页面尚未接入订阅、Usage、Consumer/Impact 或明确消费动作

**位置**：[detail.tsx:72](D:/tianxy/code/data-ops/yak-ops-ui/src/pages/data-analysis/consumption/detail.tsx:72)、[detail.tsx:123](D:/tianxy/code/data-ops/yak-ops-ui/src/pages/data-analysis/consumption/detail.tsx:123)、[api.ts:32](D:/tianxy/code/data-ops/yak-ops-ui/src/services/consumption/api.ts:32)。

页面只请求 getProduct，展示来源管理入口、契约、治理和字段/参数。services/consumption 没有订阅创建/撤销、Usage 或 Impact 客户端；页面没有消费动作 CTA、声明依赖操作、成功消费证据、Known Consumer 或其稳定目标链接。DataProductView 也没有 usage/subscription section。后端有关系接口不能替代用户 Journey。

**违反**：F-004 §1（目标 5/8/9）、§6.2、§10、§15.5；PD-002 D10。**最小修复边界**：既有规范详情组合已授权动作和关系/evidence API；复用 specialist 的真实 Query/Invoke 能力，以稳定身份返回同一消费上下文。**验证**：浏览器中完成两条旅程；只有订阅、只有 Usage、provider 故障及 consumer target 删除都可解释；按钮权限与执行授权一致。

### F-11 [P1] 行政关闭与当前产品验收合同冲突，真实 E2E 仍未完成

**位置**：[F-004:602](D:/tianxy/code/data-ops/docs/product/features/F-004-governed-data-consumption.md:602)、[PD-002](D:/tianxy/code/data-ops/docs/product/decisions/PD-002-governed-consumption-contract.md)、[#104 最后关闭记录](https://github.com/gitfortian/data-ops/issues/104#issuecomment-5852591602)。

GitHub #100–#104 均为 closed。#104 最后评论明确记录“administratively passed; manual Golden E2E deferred”，并说明不主张那些手工场景已执行。这是明确的行政推进决定；不是伪造真实验收。

但当前 ACCEPTED PD-002 / APPROVED F-004 没有相应修订，仍要求真实 Dataset 与 Data Service 两条路径及权限/失败/恢复证据后才能关闭。现有记录仅有 runner/preflight 和未来人工注入清单，没有这些已完成的 evidence。PD-002 的 Implementation 还保持 NOT_STARTED，反映文档与交付状态也未收敛。

**违反**：PRODUCT_STYLE §Authority/§7、F-004 §15.5，以及仓库 AGENTS 的“历史 Evidence 不覆盖当前 Product Truth”。**最小修复边界**：产品治理和验收记录。若维持现有合同，应补足实现断点与真实验收；若正式改变阶段门槛，应通过现有 Product Change Process 明确修订权威决策/Spec、记录延期项目，不能仅用 Issue 关闭替代。

**验证**：保存部署 SHA、真实主体/Project、两类 source/version ID、动作裁决、执行结果、Usage ID/providerEvidenceRef、Consumer/Impact、双向页面/API 回链、失败注入和恢复记录；逐项与当前权威契约对齐。

## 验证记录与限制

### 已执行的后端检查

```powershell
mvn -B -pl yak-ops-business/yak-ops-business-consumption -am '-Dtest=*Golden*,ConsumptionContractFoundationTest,*DataProductProviderTest,ProductDiscoveryServiceTest,CanonicalProductServiceTest,SubscriptionServiceTest,UsageEvidenceServiceTest,*UsageEvidenceNormalizerTest,ConsumerImpactServiceTest,DatasetQueryPerformanceRecorderTest,DatasetQueryPerformanceReaderTest,DatasetQueryPerformanceStoreAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

首次执行被 Data Service 失败阻断。为收集所有模块结果，第二次加 `-Dmaven.test.failure.ignore=true`；**第二次日志中的 BUILD SUCCESS 不代表测试通过**。

| 模块 | 执行数 | 断言失败 | 错误 | 通过 |
|---|---:|---:|---:|---:|
| Consumption | 67 | 2 | 0 | 65 |
| Dataset 诊断回归 | 8 | 5 | 1 | 2 |
| Data Service Golden | 2 | 1 | 0 | 1 |
| 合计 | 77 | 8 | 1 | 68 |

未通过项：

1. `DataServiceGoldenPublicInvokeAccessTest:134`：期望审计耗时固定 8ms，LocalDataServiceRuntime 已按当前耗时重写，实际 0ms。这是脆弱/失效测试断言，不是 API Key 绕过证据。
2. `DataServiceGoldenSourceEvidenceGapTest:61`：期望旧 `/data-service/88`，实现返回当前 `/data-service/api/88`。测试期待过时，不能把正确新路由认定为生产缺陷。
3. `DatasetGoldenQueryExecutionTest:177`：期望旧 provider `DATASET_QUERY`，实现为 `DATASET_QUERY_PERFORMANCE`。来源标识断言未同步。
4. `DatasetQueryPerformanceReaderTest` 五项：过滤/排序/窗口及 scope 用例结果为 0；fixture 只 stub `currentProject.current()`，Reader 已改为 `requireProjectId()`，mock 返回默认值，与 buffer 的真实 Project ID 不匹配。需要保留隔离断言修正 fixture。
5. `DatasetQueryPerformanceRecorderTest.persistenceFailureFallsBackLocallyWithoutThrowing`：读回使用无 Project 的兼容 Reader，出现 ProjectContextException。不能为测试通过放宽生产的 Project 边界。

### 前端与确定性复现

- 实际运行 tsc：退出 2，184 条仓库历史诊断；现有 changed-surface gate PASS，未归属 Phase 4 消费页面。
- 临时 Java/Mockito probe：冒充订阅、取消他人订阅、并发重复写异常、normalized 空表→EMPTY 四项复现。
- 独立来源 probe：SQL_QUERY task:0、真实 Data Service producer 空链、OFFLINE→NOT_DISCOVERABLE、四类 section 无完整治理、interface 字段不完整。
- 直接取原验收脚本函数做 Node vm probe：坏导航、缺治理、Access unavailable、Usage empty、0 Consumer 的响应仍被接受。
- 合成 tsc 启动失败日志被 checker 返回 PASS。
- Boot 定向 assembly 检查：`mvn -B -pl yak-ops-boot -am '-Dtest=ConsumptionRuntimeAssemblyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，1 项通过、60 个 reactor 项编译成功。该测试只校验 Controller 在 classpath 及 component scan root 内；没有启动完整 Spring context，更不是 HTTP/E2E 验收。

所有 probe 在系统临时目录运行，没有改动生产代码，也没有创建/修改真实外部订阅或调用生产数据。未执行真实部署 Golden E2E、MySQL 多实例竞争/retention 集成或受控故障注入；本报告不将其标为通过。

复核产物保存在本机临时目录：

- [后端完整测试日志](C:/Users/tianxy105/AppData/Local/Temp/phase4-review-maven-all.log)
- [Boot 检查日志](C:/Users/tianxy105/AppData/Local/Temp/phase4-review-assembly.log)
- [tsc 诊断日志](C:/Users/tianxy105/AppData/Local/Temp/phase4-review-tsc.log)
- [权限/幂等/Impact Java probe](C:/Users/tianxy105/AppData/Local/Temp/phase4-review-probes/Phase4ReviewProbe.java)
- [来源映射 Java probe](C:/Users/tianxy105/AppData/Local/Temp/phase4-spec-audit-56eefb6/Phase4SpecProbe.java)
- [验收门 Node probe](C:/Users/tianxy105/AppData/Local/Temp/phase4-spec-audit-56eefb6/acceptance-probe.mjs)

仓库未提供 code-review 技能要求的 `docs/agents/issue-tracker.md`；本次直接使用已连接 GitHub 读取任务及评论，审核没有因此暂停。按该技能的配置建议，可运行 `/setup-matt-pocock-skills` 补齐 issue tracker 工作流文档。

## 审核基线已有基础与剩余修复边界

本次在所审持久化与 Provider 中未发现新增 Dataset/Data Service/Asset owning 副本：ProductKey 来自 source ID，来源 schema/version 保持各自专业 payload；Subscription 与 Usage 使用独立关系表；失败消费被 normalizer 排除；来源审计失败不回滚成功响应；Data Service 公共调用继续使用真实 Consumer/API Key/IP 策略。这些基础可继续复用。

审核后已实施上述多项修复；当前仍需保留以下边界，不应在缺少 owning-domain 证据时猜测事实：

补充的未计数缺口/待验证项：

- Subscription 状态已改为 ACTIVE/SUSPENDED/REVOKED，并新增旧 CANCELLED→REVOKED 数据迁移；转换历史和 source lifecycle 行为尚未持久化/冻结。
- Consumption 模块缺 DOMAIN/REQUIREMENTS/ARCHITECTURE/DEPENDENCIES/README，长期 owner 和允许依赖未形成模块级可执行契约。
- 前端 SourceLifecycleState/ProviderEvidenceState 已与后端枚举对齐。
- Impact 与来源对账仍受 200 条读取上限限制；满窗会 fail closed，但尚无分页补偿和持久化同步水位，窗口内计数也不是全历史总数。
- Subscription 竞争重读、Dataset version lock 在 MySQL REPEATABLE READ 下可能仍读旧快照；尚无数据库竞争复现，不计作已确认缺陷。

两个轴的结论保持独立：**Standards 3 项，最高风险是 CI 无法保护已失败的关键回归；Spec 11 项，最高风险是 Consumer 代表授权和真实消费→证据闭环未成立。**

## 修复分支非人工验证

- `mvn -B -pl yak-ops-boot -am -DskipTests compile`：通过（60 个 reactor 模块）。
- 扩展后的 CI 后端选择通过：Consumption 69 项、Dataset 查询诊断 8 项、Data Service Golden 2 项，合计 79 项；初次运行中暴露的 fixture/断言问题已同步修正。
- Boot assembly 门禁通过：`mvn -B -pl yak-ops-boot -am '-Dtest=ConsumptionRuntimeAssemblyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，1 项测试通过，60 个 reactor 模块成功。
- 全仓 `yarn tsc --noEmit --pretty false` 仍有 184 条历史诊断；修复后的 changed-surface checker 通过，Phase 4 消费面无诊断。
- `git diff --check` 及修改的 Node 脚本 `node --check` 均通过。
- 真实环境 Golden E2E、人工权限/失败/恢复场景和真实环境脚本未运行，按本次要求留待人工测试；因此这里的自动化通过不代表 F-011 的产品验收完成。

修复分支已处理代码与自动化范围内可确定的问题：CI 覆盖遗漏、TypeScript checker 误报通过、重复 Usage 并发处理、生产者回链、Data Service 契约投影、Dataset 停用投影、Golden runner 断言、订阅代表授权、Query/Invoke 实时证据接线、Impact 对账缺口 fail-closed、Access 解释字段和消费详情页。下列能力仍不能仅靠本分支代码宣称完成：

- Quality/Security/Lineage 的真实证据必须由 owning domain 提供可验证的物理资源或 lineage 引用；当前缺失时明确返回 UNAVAILABLE，runner 不再将空 facts 当作 READY。
- Usage reconciliation 仍是最近有限来源窗口，窗口达到上限会标记覆盖不完整；持久化水位、分页补偿或 durable outbox 尚未建立，因此不能保证长期积压跨越来源保留期后仍可恢复。
- Data Service 的逐 API Key 实际授权投影仍明确不可用；不能用登录用户权限伪装公共调用的 Consumer/API Key/IP 决策。
- 手工/真实环境证据和外部任务关闭记录不在本次修改范围；仓库当前 ACCEPTED/APPROVED 产品合同没有被静默改写。
