# PD-008 — 数据产品来源版本变更协同与消费者确认

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-10-08  
Owner: Product  
Related: PD-002 / F-004（消费契约）、PD-007（来源废弃与退休，仍为 PROPOSED）、#180 / #336、PR #393 / #396。

> **非正式决定。** 本文只提出正式 Consumer 变更确认和变更计划的候选模型。只有状态更新为 ACCEPTED、配套 Feature Spec 为 APPROVED 且源域 Domain/Requirements 核准后，才允许持久状态、消费者响应接口、消息发送、Approval/Publish 集成。本提案本身不构成实施指令。

## Context

来源 Owner 准备修改已发布的 Dataset / Data Service 版本，需要确认受影响消费者的迁移/兼容安排。已有 Consumer Impact 和人工沟通功能可以识别**已知**使用者与声明依赖，但不能把剪贴板内容变成已送达通知，也不能把前端勾选变成已获正式批准。

真实任务是：提出版本变更 → 冻结所针对的来源版本和拟变更定义 → 找到可核实的下游 → 逐一获得可信的解释或回复 → Owner 根据事实作出变更决策 → 保留可复查审计与下游恢复路径。

## Current Behavior

- Dataset 与 Data Service 源域分别拥有 source identity / immutable version 或 revision / 发布与运行状态（PD-002 ACCEPTED）。
- Consumption 拥有 ACTIVE/SUSPENDED/REVOKED 声明订阅和归一化真实成功 Usage Evidence，SourceVersionRef.identity 保留字符串 BIGINT；版本影响聚合来自有限同步窗口，UNKNOWN/UNAVAILABLE/FORBIDDEN 不等于“0 影响”（#393）。
- Source Invoke 历史详情支持 `GET /api/v1/data-service/{id}/logs/{invocationId}` 的精确 Project/API/Invocation 过滤（#386）。
- #396 允许 Owner 按 Consumer 复制**人工**沟通草稿，拟变更内容只在浏览器内临时维护；无法证明已送达、对方身份、回执或发版许可。
- 已读取既有 Approval Domain/Requirements/Architecture：Approval 拥有 Flow/Instance/Step、Project 边界、在途唯一、同事务 handler callback，审批与业务事实不混淆；Audit 已承接部分业务操作。ConsumerRef 可定位业务对象，不代表合法账号或通讯录地址。
- PD-007 仍是 PROPOSED；DEPRECATED/RETIRED、替代版本、阻断新消费与自动迁移不得由本提案提前落地。

## Decision（候选规则，仍待产品评审）

### D1. 只覆盖两个已批准消费产品类型

候选首期仅 `DATASET:<datasetId>`、`DATA_SERVICE:<serviceId>`；Metric、MDM、Model、Task、Agent 不能通过类型转换接入。

### D2. 精确且可追溯的变更输入

建议一条变更提案在提交时冻结：
- `projectId + productType + sourceIdentity`；
- `sourceVersion.identity`（**字符串**；显示版本仅描述，不能作身份键）；
- 拟变更范围/摘要及用户提供的可复查变更标识（精确新版本 ID 若尚未存在，应显式记录“未确定”，不可假造）；
- 提交者和所属 Project、提交时间、当时来源版本/定义快照及校验依据；
- 已知 Consumer / Subscription / Usage evidence 的**覆盖范围、来源状态、时间窗口和来源标识**（只存引用或有权脱敏快照，不复刻 Usage/Subscription Truth）。

来源发生后续版本切换或提案内容变化时，已采集意见不能自动作为新 proposal 的批准，必须存在明确的版本指纹匹配和失效/重新评估规则。

### D3. 把五种事实分开

| 事实 | 候选 Owner / 说明 |
|---|---|
| 拟发布版本、真实发布动作及生命周期 | Dataset / Data Service Source Owner |
| 对某版本的声明依赖、观察到的真实消费 | Consumption / source-owned execution evidence |
| 已送达消息 | 真实 Notification/Delivery Provider（不能由 UI copy 推断） |
| Consumer 回复 | **待裁决：**独立的 Project-scoped 协同记录或来源提案子记录；必须绑定实际身份、版本、回复证据 |
| 正式变更审批 | 复用既有 Approval Flow/Instance/Step；审批通过不自动推断 Consumer 逐一同意 |

建议**不要让 Approval Step 兼做每位 Consumer 的协商回执**：其当前设计为有限静态审批人、ANY 同级及 1～2 级审批，不等价于任意类型 Consumer 的协作场景。

### D4. 消费者回复的候选语义

对明确身份与权限的每个 Consumer，候选回复值建议从以下集合评审：
- `NEEDS_REVIEW`：无法确认兼容，需要补充信息（**不是拒绝**）；
- `NO_DEPENDENCY`：Consumer 负责人明确表示无此依赖（**不覆写 Usage Truth**）；
- `COMPATIBLE`：负责人已核对当前拟变更输入并表示兼容；
- `MIGRATION_NEEDED`：需明确迁移任务/窗口/Owner。

这是回复语义候选，**不是当前产品支持的状态集合**。已联系 / 未联系、已送达 / 未送达、已读 / 未读仍是不同证据，不以这些回复自动产生。

### D5. 必须先确认主体与权限映射

每种 Consumer 类型至少明确：
- `USER` / `TEAM` 谁可代表确认；团队关系是否以实时 Role / Member 解析，变动如何留痕；
- `DASHBOARD` / `JOB` / `DATA_SERVICE` 的 Owner 来源和可否委托；
- Console 登录 USER 与外部 Data Service API Key 是不同主体；外部 Key 不得直接被当成人工“同意”身份；
- Project/RBAC、跨 Project、撤权、Consumer 被删除和身份未知时禁止越权回复。
- `ConsumerRef.displayHint` 不是人员目录，不能作为可验证收件人。

### D6. 审计与并发

若正式批准：
- Proposal/Response 必须有幂等键、版本指纹校验、并发更新约束、actor + Project + evidenceRef；
- 同一回复重试不产生重复虚假回复；回应旧版本或旧变更内容必须显示版本冲突；
- 修改回复或撤回有自己的审计轨迹，不覆盖历史；不得在 ConsumerImpact 里直接回写虚构执行；
- Source evidence 不可用时保留 `UNAVAILABLE` / `FORBIDDEN` / 未知的可见原因；不能以“所有人已确认”跳过不可覆盖来源；
- 保留记录、敏感数据脱敏、历史清理期限需与 Audit/Privacy Owner 明确。

### D7. 默认不建立发布阻断

即使协同回复能力后续获批，Consumer 全部回复仍不等于 Quality/Security 许可，也不等于 Publication Approval。只有来源 Domain + PD-007（及涉及到的 PD-005/006）获得独立 ACCEPTED 规则时，才能启用明确范围的阻断/豁免。未经批准，最多为 Owner 提供知情判断和链接。

## Product Outcome

- Owner 在 J4 知道要联系谁、为什么联系、哪些证据仍缺失，能将版本变更和消费者回复保留为有身份的事实，而不是共享电子表格。
- Consumer 在 J3 能看到真实拟变更的范围、所依据的精确来源版本、自己的当前已知使用事实和可追溯回复。
- 同时不把可见消费者名单伪装成所有实际消费者，不允许从零 Usage 或 Approval 状态推断无影响。

## Alternatives Considered

### Option A（倾向评审）：Source-owned Change Proposal + 专用 Consumer Response 归属

来源域拥有 Proposal 和发布契约；协同层仅拥有回复/沟通事实（不拥有来源版本、Usage、Permission），以 Project/ProductKey/SourceVersion/version fingerprint 关联。优点：责任清晰、未来可扩展；成本：跨域交易、回复主体映射和持久化治理要设计清楚。

### Option B：把消费者回复直接转为 Approval Steps

复用审批引擎，但 Approval 当前静态审批人/ANY/有限级数不适合多 Consumer 自主回复，尤其是 JOB 或外部 API Key 所代表的业务对象。候选只适用于确需**正式批准**的 Source Owner 流程，而不是通知回执。

### Option C：仅靠 Consumption 订阅或 UI Checkbox 代表消费者确认

拒绝作为正式方案：订阅不绑定版本，执行证据不是承诺，浏览器勾选没有 Consumer 身份认证及持久审计。

## Consequences

### Positive

变更沟通、回复与正式审批拆开，复用可信身份、Audit 和 Approval，避免新增虚假 Source Truth。

### Trade-offs

需要明确 Project 内 Consumer Owner 解析、不可变变更目标/指纹、撤回/冲突和审计范围，首次实施比复制草稿复杂。

### Risks

未识别 Consumer、部分 Evidence 不可用、执行窗口截断、发布期间规则变更、权限撤回导致历史回复能否有效，均必须显式保留不确定性。不能未经受托授权给其它 Project/外部 Key 发送敏感变化消息。

## Truth / Ownership Impact

- Truth Owner: Source Proposal 候选归 Dataset/Data Service Source Domain；Consumer Response 的唯一记录 Owner **待决策**；Approval 继续持有正式审批；Audit 持有审计记录。
- Producer(s): 来源 Owner、被授权 Consumer 负责人、原始 Usage/Subscription 证据提供者。
- Consumer(s): Dataset/Data Service 发布 Owner、下游业务 Consumer、Steward、Audit Reviewer。

## Navigation / UX Impact

不新增一级菜单。优先在 Source Detail → Canonical Consumption Impact → Consumer 变更核对形成上下文。若回复机制获批，Consumer 能从身份合法的通知/待办进入同一精确来源版本上下文；未知负责人/无访问权提供明确恢复说明而非虚构发送。

## Migration Plan

未经 ACCEPTED 决议，不新增数据表、通知、Approval Flow 或 Source Publish Gate。批准后以 **Feature Spec → 源域 Domain/Requirements → Architecture & Project Security Review → Schema Migration → Tests → E2E** 顺序推进。#393/#396 的浏览器临时快照不能迁移成“已确认”或“已通知”历史记录。

## Acceptance Evidence

必须至少覆盖 Dataset / Data Service：
1. 精确版本 ID 大于 JS 安全整数；相同显示版本不同身份不可串用；
2. Consumer A 有来源成功执行，B 只有声明依赖，C 不在窗口内：不误归因；
3. Console USER、真实 Consumer Owner、被撤权者和跨 Project actor；
4. 重复回复、并发回复、旧 SourceRevision、提案更改导致回复失效；
5. 来源 UNAVAILABLE/FORBIDDEN、Consumer 身份未知、系统无法通知：保留 UNKNOWN/PARTIAL；
6. 明确的回复、审批、审计回链，以及来源发布是否真正改变（未批准 Gate 时必须不阻断）；
7. 用部署 commit、实际 UI/API、actor 权限、Project、来源版本、证据 ID 验证 Golden E2E。CI PASS 不代表已完成真实 E2E。

## 决策前待裁决的问题

1. 选择 A/B 还是其它真实归属；每类 Consumer 的可信代答人具体如何解析？
2. 首期要“回复/兼容性证明”还是仅“送达/已知悉”？何种行为必须 Audit？
3. Proposal 的不可变版本/变更摘要由哪个源域 Command 冻结，新版本未形成时如何表示？
4. 哪些字段允许持久化，什么时候过期/撤回，跨 Project 信息如何隔离？
5. 是否需要 Source Owner 最终签审？如果需要，FlowCode 和 Handler 归属在哪里？
6. Notification Provider 是否已有允许的数据与收件人映射？若无，是否先只做手动联系？

## Supersedes

None
