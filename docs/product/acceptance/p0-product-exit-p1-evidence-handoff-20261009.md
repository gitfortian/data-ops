# P0 D-04/D-05 最终核销与 P1 证据移交（2026-10-09）

> **用途**：完成 [#336](https://github.com/gitfortian/data-ops/issues/336) D-04 历史规划核销、D-05 P1 **证据输入包**以及 P0 正式退出前的有限工作分界。**不是新的 Feature、审批单、开发委托或生产放行决定**。用户已明确冻结全部新功能开发。
>
> **固定核查代码基线**：`main@5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf`（包含 [#469](https://github.com/gitfortian/data-ops/pull/469) 合并）。它**不是**运行中的部署 commit。
>
> **事实等级**：`MERGED` / `STATIC_REVIEWED` / `HISTORICAL_TEST_RECORDED` / `PENDING_REAL_QA` / `BLOCKED_EVIDENCE` 不互相替代。没有当期真实登录、浏览器、应用数据库和 Process/Bundle 身份记录时，不得写 `E2E_PASS`。

## 1. D-04：旧 #288 / #289 计划与当前事实对账

| 原计划或历史缺口 | 本批直接核实到的证据 | 裁决与后续责任 |
| --- | --- | --- |
| [#288](https://github.com/gitfortian/data-ops/issues/288) 五域功能树 | [Capability Map](../CAPABILITY_MAP.md) 已列五域 + MDM 专业方案 + 横切平台能力；[navigation.ts 五个业务顶层组](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/data-ops-ui/src/config/navigation.ts#L55-L72) 与之相符 | **STATIC_REVIEWED**，不再将“建立五域菜单分组”派生为 P0 新开发；#288 的 Product Owner 分类校准及 J1～J6 真实旅程仍未签收，Issue 保持 OPEN |
| [#289](https://github.com/gitfortian/data-ops/issues/289) 菜单改组/消费主入口 | [PR #296](https://github.com/gitfortian/data-ops/pull/296) 在 **2026-09-30 MERGED**；当前 [navigation.ts](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/data-ops-ui/src/config/navigation.ts#L75-L101) 已让 Consumption Catalog 可见、详情按 parentId 继承目录；Approval 待办/平台设置已归组；[F-008](../features/F-008-task-oriented-navigation.md) 有落地事实 | **MERGED + STATIC_REVIEWED**，原“Consumption Hub 仍需新增菜单”不再成立；#289 **OPEN/PENDING_REAL_QA**，不单凭代码关闭 |
| 菜单后端目录/RBAC 漂移 | [navigationMenuContract.test.ts](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/data-ops-ui/src/config/navigationMenuContract.test.ts#L89-L232) 对可见/隐藏菜单码、后端 Flyway 生效目录的父级、名称、路由、权限比对；[孤立 MySQL V2045 校验脚本](https://github.com/gitfortian/data-ops/blob/5b5318c5e8705e6d3795e9dafcfdae59b95d0dcf/scripts/security/test-task-oriented-menu-migration.py) 校验权限保持/幂等。原 [导航验收快照](task-oriented-navigation.md) 记录 5 suites / 33 Jest、本地 Build 与 MySQL 8 测试 | **HISTORICAL_TEST_RECORDED**；这些是 9-30 留证/当前测试存在，不是本轮重新执行的数据库、浏览器或真实授权 PASS。菜单重分组不授予新数据权限 |
| 登录态真实旅程与角色切换 | [导航历史验收](task-oriented-navigation.md) 明示本地浏览器使用**模拟 root 登录**；J1～J5 真实账号、Project 切换、旧角色授权、深链与返回仍未执行正式验收 | **PENDING_REAL_QA** / QA + RBAC + Product/UX；可复用 [Golden E-00～E-05 执行签收包](p0-golden-operational-handoff-20261009.md) 的部署/账号前置条件并补菜单路径证据，不新建导航功能 |

**D-04 结论**：历史 #288/#289 不应该当作“需要开始一个新的菜单项目”。它们仍是**产品审查/真实验收**载体；当前的文档或 PR 不能代替 Product Owner/QA 的勾选和关闭裁决。F-008 保持 `IMPLEMENTING`，不能因为 PR #296 早已合并就写 SHIPPED。原 PR 描述“V2042”是合并前号码，仓库最终迁移归档使用 **V2045**，须以真实目录为准。

## 2. D-05：P1 仅证据输入/决策门禁（不是开发路线启动）

| 用户任务与证据输入 | 已接受/已存在事实 | 必须由 Owner 取得的缺口、准入或争议 | 不得在当前 P0 偷做 |
| --- | --- | --- | --- |
| **J1 接入 → 可治理资产** | Datasource、Metadata、Sync、Asset 现有来源各自拥有 Truth；[Capability Map](../CAPABILITY_MAP.md) 与 [J1](../USER_JOURNEYS.md) 描述用户路径 | Product/Integration/QA：确认真实 Source→Harvest/Sync→Asset 项目隔离、异常扫描/恢复、失败重试与专业回链；写清当前默认入口 | [PD-004](../decisions/PD-004-enterprise-data-integration-contract.md) **PROPOSED/NOT_STARTED**：不得擅自引入统一 Integration 对象/全域默认写入流程 |
| **J2 业务定义 → 精确 Metric 发布/复用** | [PD-003](../decisions/PD-003-business-semantic-metric-contract.md) ACCEPTED/PARTIAL；[F-005](../features/F-005-business-semantic-metric-productization.md) APPROVED/IMPLEMENTING；版本/CAS/指纹静态防护已定位于 [P0 台账 §7](p0-closeout-20261009.md) | Metric/Modeling/Approval/QA：真实同版本 publish/reject、模型送审后改结构、跨 Project 与消费者引用/观测运行证据。另有独立 Phase5 real-env workflow，但不自动覆盖 P0 Golden | 不新建指标运行消费者、不为完成静态矩阵而生产化新 Metric 验收能力 |
| **J3/J5 两类 Governed Consumption** | [PD-002](../decisions/PD-002-governed-consumption-contract.md) ACCEPTED/PARTIAL；[F-004](../features/F-004-governed-data-consumption.md) APPROVED；Dataset/Data Service 有历史 R1/R2 API/DB 证据及现成 R3～R8 runners | Dataset/Data Service/Security/QA/Release：严格按 [Golden 执行包](p0-golden-operational-handoff-20261009.md) 补**同部署**确切版本、真实 Query/Invoke、exact Source Audit+Usage、双 Project/权限、Key/IP、真实浏览器和外部故障根因；不得把声明订阅视为有效访问/真实使用 | [PD-005 质量发布门禁](../decisions/PD-005-quality-publication-gate.md)、[PD-006 安全对象映射](../decisions/PD-006-consumption-security-object-mapping.md)、[PD-007 生命周期范围](../decisions/PD-007-governed-source-lifecycle-scope.md)、[PD-008 变更协同](../decisions/PD-008-source-version-change-consumer-coordination.md) 均 **PROPOSED/NOT_STARTED**，禁止擅自增加状态机、权限/发布阻断、通知确认 |
| **J4/J5 资产治理与专业回链** | [PD-001](../decisions/PD-001-asset-governance-hub.md)、Phase7 旧取证与当前 Asset/Metadata 纠偏已有事实；#185/#264 CLOSED 是 Issue 状态 | Asset/Metadata/Quality/Security/QA：源域独立失败、空/不可用/无权限分类，扫描异常/恢复，专业处理返回原资产；当前部署的角色浏览器还缺签收 | 不新建重复 Asset Truth、不把 Quality 上游状态推成发布通过或将 CLOSED 等同 E2E_PASS |
| **J6 AI 证据回答** | [USER_JOURNEYS.md](../USER_JOURNEYS.md) J6 为现有用户旅程 | 属于**其他 Agent** 负责；此线程只允许转交权限/真实 Usage/Source 证据需求，不判定 AI 产品功能完成度 | 不在本批开发、审核或扩展 AI 模块 |

### 最小可交付交接材料（现有证据，不新增功能）

- **用户/问题/结果**：以上 J1～J5 的真实业务 Owner、希望完成的既有任务、可观察成功结果与不允许的失败状态；J6 保留明确的其它 Agent 归属。
- **来源事实/边界**：PD-001/002/003 为现有已接受边界；Source-owned 版本/审计、Consumption Usage、Project RBAC、Asset 的治理只读投影必须区分，不复制权威 Truth。
- **要复用的入口**：现有 Dataset Query、Data Service Invoke/Consumer Key、Consumption Catalog、Asset Detail、Metric Publish/Approval、Golden R1～R8、导航 F-008；只引用已存在接口、功能和证据记录。
- **未决决策/未授权**：PD-004/005/006/007/008 需走正常 Product Decision（以及对应获批 Feature 和领域契约）后才可能启动**未来独立授权的实施**；没有这套批准时本包仅能用于讨论与取证。
- **验收环境/清单/风险**：[P0 Golden 执行包](p0-golden-operational-handoff-20261009.md) E-00～E-05、[P0 台账](p0-closeout-20261009.md) V1～V5 和 G-00～G-05 形成复用证据，不再产生另一份来源数据或第二套 E2E。

## 3. 正式 P0 退出评审记录（截至本轮）

| 退出面向 | 已核事实 | 当前裁决 |
| --- | --- | --- |
| A 合并与已见 CI | #463、#466、#467、#469 均 MERGED；#469 的 Product Guard、Golden 离线、Architecture 五个 jobs 均 SUCCESS | **ENGINEERING_MERGED/RECORDED**，不是部署 E2E |
| B 指定高风险静态核查 | Metadata、Semantic、Metric、Modeling、Approval/Audit 具体保护点与局限有 [台账](p0-closeout-20261009.md) | **STATIC_REVIEWED / PENDING_RUNTIME**；拒绝从“代码存在”推断全部正确 |
| C Golden 真实用户/双来源 | 2026-10-04 历史 R1/R2 报告 `deploymentCommit=null`；已有 R3～R8 工具，但无本轮可核验的真实登录/数据库/浏览器报告 | **BLOCKED_EVIDENCE**；须 Release/QA/Truth Owner 提供同部署真实证据 |
| D-04/D-05 文档与 P1 交接 | #288/#289 当前实现/历史测试边界已和 PR #296、F-008 对账；P1 仅证据包与未授权决策门禁 | **DOCS_RECONCILED / NO_NEW_FEATURES**，不等于 F-008/用户旅程 SHIPPED |
| Product/QA 正式签收 | 尚无具有相同部署身份的完整签字报告、明确受控延期审批 | **P0_EXIT_NOT_SIGNED**，#336 应保持 OPEN |

**合法的下一步仅有两种**：① QA/部署按已有工具提供真实证据、签收对应反例；② Product + QA 对不可执行场景明确批准限范围延期（作用对象、风险、Owner、限制、解除条件和签字）。**在这两个输入都不存在时，不再批量创建等价的 P0 文档/工具/功能 PR 来替代外部验收。** 任何额外实施都需新的明确授权，且不从本文件自动派生。

签收栏（目前均未签）：**QA：NOT_SIGNED｜Product：NOT_SIGNED｜Release：NOT_SIGNED｜Truth Owner：NOT_SIGNED**。本轮没有自动更新 Issue 状态为完成，也没有触发新的部署、业务操作或审批。
