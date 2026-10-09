# P0 Golden 真实环境验收执行与退出签收包（2026-10-09，第四批）

> **用途**：#336 的 QA / Release / Product 签收交接，**不是新需求、不是实测 PASS、不是生产操作授权**。
> **本批授权**：只收口、核证据、消除旧状态歧义；不开发任何新功能。Model / Metric / MDM 样本扩建、P1 E1～E4、AI 能力和未 ACCEPTED 的 PD-005/006/007/008 均不在本次执行范围内。
> **代码参考 SHA**：`main@bbbb24ea1ca406a15b2d78dbe3ed1b4cf09cc348`（含合并 #467 的静态证据台账）。这**不是部署身份**。如实测时 `main` 变化，QA 以部署的精确 commit/构建收据/流程身份重新绑定。
> **主追踪**：[P0 #336](https://github.com/gitfortian/data-ops/issues/336) · [当前证据台账](p0-closeout-20261009.md) · [现有 Golden 工具使用说明](../../../scripts/product/golden-sample/README.md) · [历史 R2](golden-sample/r2-consumption-20261004.md)。

## 1. 已确认事实与不能签收的事实

- [PR #463](https://github.com/gitfortian/data-ops/pull/463)、[#466](https://github.com/gitfortian/data-ops/pull/466)、[#467](https://github.com/gitfortian/data-ops/pull/467)、[#469](https://github.com/gitfortian/data-ops/pull/469) 均已于 2026-10-09 **MERGED**。这些批次是文档/静态核销，不是新的 Golden 部署。#469 最终 Product Guard、Golden 离线、Architecture 工作流均 SUCCESS；但没有真实部署签收。
- 仓库有 **R1、R2 历史真实 API/DB 样本**：[`physical-acceptance-20261004.json`](golden-sample/physical-acceptance-20261004.json) 的 `deploymentCommit=null`；[`consumption-acceptance-20261004.json`](golden-sample/consumption-acceptance-20261004.json) 的 `deploymentCommit=null`。后者源审计基于不同历史 repositoryCommit；没有可核验的“当前 SHA 已部署”映射。
- 2026-10-09 的仓库目录下**没有已提交**的 R3～R8 真实环境 `--apply` 结果报告；该结论只针对版本库内留存内容，**不推断运行环境、QA 私有存储或 CI artifact 一定不存在**。
- [Golden CI 工作流](../../../.github/workflows/golden-consumption-evidence.yml) 的实际步骤为语法检查、Python fixture unittest、Node lossless identity 测试；**不会执行实际 HTTP POST/登录/应用库核对**，不能作为 E2E_PASS。
- [R8 离线证据 Gate](../../../scripts/product/golden-sample/golden_evidence_gate.py) 核对证据结构/前后阶段对应及可选 JAR SHA-256 + 双声明，即使全部结构一致也只生成 `CONSISTENT_PARTIAL_EVIDENCE`、`automatedProductPass=false`、`deploymentIdentity=UNVERIFIED`，并以 **exit 2 / PENDING** 结束。双运维声明不是运行中进程使用该 JAR 的独立证明；前端包及浏览器需要另行核实。
- [Phase5 Real Environment Acceptance](../../../.github/workflows/phase5-real-env-acceptance.yml) 是独立的 Metric/J2 真实环境工作流，确有 `workflow_dispatch` 和 JSON artifact 路径，但不得把它的存在或某次 CI 成功当作 #336 Dataset/Service J3/J5 Golden 当前已通过；若可获得与部署 SHA/账号绑定的运行 artifact，再单独评估可复用的事实。

## 2. QA / 环境 Owner 必须提供的输入（缺一不能启动对应环节）

| ID / Owner（角色） | 执行前输入 | 无输入时如实记录 |
| --- | --- | --- |
| E-00 · Release/部署 | 经过授权的**隔离非生产环境**、可访问 URL、实际运行进程/容器/部署控制器的 `deployedCommit` 证明、构建 job/后端 JAR SHA-256/前端 bundle SHA-256；验证 URL 与 manifest 相同 | `BLOCKED_DEPLOYMENT_IDENTITY`；不能仅把 GitHub main SHA 或两份声明填为已部署 |
| E-01 · QA/RBAC | 真实管理员、同 Project 业务用户、属于同 Project 但权限受限的**另一账号**、跨 Project 账号；实际 membership + 权限码的脱敏快照；不能仅看 HTTP 403 | `BLOCKED_AUTHORIZATION_EVIDENCE` |
| E-02 · QA/DB | R1/R2 标记为 `yak-golden-sample-v1` 的 Project A/B manifest、Dataset/Service 与 exact source version、独立应用数据库 **SELECT-only** 账号；稳定的真实 SUCCESS 审计和 Consumer identity | `BLOCKED_SAMPLE_OR_SOURCE_AUDIT`；不创建伪 SUCCESS 记录、不在库中删除 Usage |
| E-03 · QA/Consumer/Security | 使用现有可授权 Key/IP 策略的合成 Consumer、有效和无效/撤销 Key 的安全测试计划、真实外部 Invoke 路径；**不保存原始 Key、密码、Cookie、Token** | `BLOCKED_CONSUMER_NEGATIVE_CASE` |
| E-04 · QA/UI | 可登录的真实浏览器与可保存**脱敏**屏幕/HTTP trace 的工作区；当前前后端部署同源身份、网络及角色可用 | `BLOCKED_BROWSER_EVIDENCE` |
| E-05 · 运维/QA | 对 R5 的归一化缺口、R6 的来源 Reader 失联分别具备**获批准的可逆故障**布置与恢复手段、操作时间/原因/日志索引；不修改生产、不由脚本制造错误 | `BLOCKED_FAULT_PRECONDITION` |

**机密性边界**：仓库只留受控环境标识、build/run/receipt URL、脱敏角色/权限快照路径、对象 ID 与不可变版本、来源审计 ID、经脱敏 JSON 报告、日志索引与摘要；禁提交密钥/认证 Header、原始 SQL、凭证、真实业务行。所有脚本只操作已有 Golden 专用项目/源域 API，应用库必须只读。样本初始化/真实 Query/Invoke 会追加**真实历史**，先获环境授权；不能宣称脚本全程只读。

## 3. 推荐执行顺序（各子场景均对应原仓库现成工具）

| 顺序 | 实际动作 / 使用入口 | 独立证据与判定 |
| --- | --- | --- |
| 0 · 环境封存 | Release 记录部署进程镜像与 backend/frontend SHA-256；QA 核对服务器 URL、Project A/B、当前权限快照；登记 `G-00` | E-00～E-02 如不齐，**停止真实验收**；不要在未知版本机器上做破坏性/误判测试 |
| 1 · 恢复 R1/R2 | 按 [Golden README R1/R2](../../../scripts/product/golden-sample/README.md) 在**新授权隔离环境** bootstrap + `consumption.py --apply --accept`；既有历史 JSON 只作为 schema/样本参照 | 真实 Dataset Query + Data Service API Key Invoke；记录 exact sourceVersion/revision、Consumer、query/invocationId、source SUCCESS audit、normalized Usage 和回链；不能复用 10-04 历史结果当本轮 |
| 2 · 优先 R4 / R7 | `historical_recovery_denials.py --apply`，`historical_recovery_roles.py --apply`；均依赖已有 SUCCESS 与已 normalized 的样本。每种来源 `--kind BOTH`，遵循 README 完整参数和输出规范 | 拒绝路径及对照 Project 不改变源审计/Usage；R7 同 Project 受限身份的**拒绝事实**另配权限码归因证明，否则细分 RBAC 仍 Pending |
| 3 · R3 历史恢复 | `historical_recovery.py --apply --kind BOTH`，只在每种来源真的具有 **>200 条已保留 SUCCESS** 且旧审计实际缺 Usage 时运行 | 旧审计补偿精确版本/Consumer/游标、跨 Project 与持久化幂等。**没有天然历史样本则记 BLOCKED，不生成假审计或改库做出缺口** |
| 4 · R5 两阶段 | 用**已保留、尚未归一化**的真实 SUCCESS，先由环境 Owner 批准布置归一化故障，再 `historical_recovery_retry.py --apply --stage blocked --confirm-fault-staged`；恢复后用原 blocked 报告 `--stage recovered` | 必须为同一 Project + SourceVersion + auditId + cursor；故障期间不新增 Usage，恢复后新增正确的一行且重试幂等。Dataset 与 Data Service 分别留证；无故障不能冒跑 blocked POST |
| 5 · R6 两阶段 | 用**已 normalized** 的另一真实 SUCCESS 审计；Operator 布置**Reader 级故障**后 `historical_recovery_reader_outage.py --apply --stage outage --confirm-reader-outage`；恢复后 `--stage restored` | 失联必须是明确 5xx/内部失败而不是成功空页；回读同一审计且 Usage 完全不变。必须单独留 Reader 根因日志，不能仅凭 HTTP 500 归因 |
| 6 · R8 聚合 | `golden_evidence_gate.py --assess`，按 README 指定 `--r3/--r4/--r5-blocked/--r5-recovered/--r6-outage/--r6-restored/--roles`；可选真实 JAR + build/runtime 两收据 | 7 个 slot 的阶段/版本/audit/cursor/项目关系正确；**退出码 2 是设计内 PENDING，不等于 E2E FAILED 或 PASS**；退出码 1 是校验失败须处理；留 JSON 和构建关联状态 |
| 7 · R8 以外的正式用户路径 | QA 真实浏览器从 Consumption → Dataset/Service → Asset/Producer → 原上下文往返；测试空态、403/404/500、旧响应、受限/跨项目、错误/撤回 Key、IP 策略（适用时）、恢复；对照 UI、API、DB/审计 | 保存脱敏截图、HTTP trace、source-owned SUCCESS/拒绝状态、normalized Usage、精确版本与复核者。R8 不包含此签收，不得用 R8 代替 |
| 8 · 退出裁决 | Product + QA + Source Truth Owner 对照下方签收表逐项审阅 | 全链相同部署构建且核心 DoD 通过才能正式 PASS；缺前提为 BLOCKED，若有延期必须附批准人/风险/限制与重新验收入口 |

**注意**：R5 与 R6 需要**不同前置证据状态**：R5 目标 Usage 必须缺失；R6 目标 Usage 必须已存在。不能用 R6 的“读回已有 Usage”当作 R5 的“缺口恢复”。R3、R5、R6 的故障场景可能不易安全构造，此时正确做法是记外部 BLOCKED + Owner，而非扩新功能或改写历史数据。以上为操作规划，**不表示任何步骤已执行**。

## 4. 验收归档清单（同一环境、同一部署，逐场景填写）

| 字段 | 当前值 / 填写规则 |
| --- | --- |
| environmentId / executedAt / operatorRole | `NOT_PROVIDED / NOT_EXECUTED / UNASSIGNED` |
| mainSnapshot / buildCommit / actualDeployedCommit | `bbbb24ea... / UNKNOWN / UNKNOWN`（mainSnapshot 不是部署） |
| buildRun / backendJarSHA256 / frontendBundleSHA256 / process attestation | `NOT_PROVIDED` |
| projectA / projectB / marker + role/membership/permissionRefs | `NOT_PROVIDED`；账号凭据不可留存 |
| datasetId + exactDatasetVersion + queryId + sourceAudit + ConsumerRef + Usage row | `NOT_EXECUTED` |
| serviceId + exactRevision string + invocationId + sourceAudit + ConsumerRef + Usage row | `NOT_EXECUTED`（BIGINT 字符串） |
| R3 / R4 / R5-blocked / R5-recovered / R6-outage / R6-restored / R7 evidence file links | `NOT_PROVIDED` |
| R8 evidenceGateOutput / artifact declaration relation / independent process attestation | `NOT_PROVIDED` |
| browser / negative Key+IP / role / outage root-cause / recovery evidence | `NOT_PROVIDED` |
| per-scene verdict + blockerOwner + releaseImpact + next action | `BLOCKED_EVIDENCE / UNASSIGNED / UNKNOWN / REQUEST_EVIDENCE` |
| QA signature / Product signature / sourceOwner signature / approved defer boundary | `NOT_SIGNED / NOT_SIGNED / NOT_SIGNED / NONE` |

不要在缺证据时把单测成功写为 `E2E_PASS`；不要用 `report.result=REAL_*` 推出最终产品验收已完成。拒绝、错误、空结果应记录实际 HTTP/应用码及写前写后真实性，不能推算或硬编码。

## 5. P0 #336 最终裁决所需人工签收

- **可放行**：G-00～G-05、R1/R2、R3～R8 适用项、真实浏览器与负向角色/API Key/精确版本及 V1～V5 高风险反例，有相同构建且可重放的可信证据，QA/Owner/Product 明确签署。
- **阻塞退出**：缺真实部署进程身份、受限账号、源审计、故障能力或浏览器，而没有经 Product+QA 批准的明确延期边界。状态保持 `BLOCKED_EVIDENCE` 与 #336 OPEN。
- **经批准延期**：必须明示 **具体场景、影响用户/发布范围、不能证明的风险、补救/禁止上线动作、Owner、截止条件、Product+QA 批准记录**；不能单纯用“CI 通过”替代批准。
- **不得触发新开发**：本轮的全部工作是对已有运行工具和验收合同取证；后续 P1 E1～E4 与 Model/Metric/MDM 的新样本能力，不应借“缺证据”为由在本 #336 自动开工。

**当前裁决（2026-10-09）**：`GOLDEN=BLOCKED_EVIDENCE`、`P0_EXIT=NOT_SIGNED`。此文档没有新增 E2E PASS、运行结果、授权或部署环境。

> **2026-10-09 D-04/D-05 后续收口**：旧导航 #288/#289 的当前代码和登录态验收边界已单独写入 [P0 最终退出与 P1 证据移交](p0-product-exit-p1-evidence-handoff-20261009.md)。本执行包的 Golden E-00～E-05 仍是全部真实 QA 的前置条件；无环境输入不新增同类 PR，未取得签收时继续 `BLOCKED_EVIDENCE`。

## 6. 四个综合 PR 的最终统一退出证据门禁（第 4/4 个综合 PR）

本节为 2026-10-09 用户指定的 **四个综合 PR 总收口**，优先于本文开头的历史 main SHA。#487 已合并，B-01～B-06 代码与旧问题证据集中核销；#489 已合并，双 Project / 受限身份 / 版本隔离工程检查；#490 已合并，Dataset Query 与 Data Service Invoke 的精确 Source Audit、Usage 对账和恢复页保护。上述 CI 的绿灯**仅代表工程门禁通过**，不代表真实 QA 及正式产品退出已签收。

新增 `scripts/product/golden-sample/p0_exit_readiness.py` 对 R1～R8 与跨域实测证据作统一**脱敏索引与哈希校验**，作为 Product / QA / Release / Truth Owner 手工审查的输入。它和既有 R8 `golden_evidence_gate.py` 的分工是：

- **R8**：原始 R3～R7 的场景结构、跨阶段 audit/cursor/sourceVersion 不变性，可选 JAR 与双声明校验，始终只给出 PENDING；
- **P0 退出门禁**：在不触发实际 HTTP/DB/故障的条件下，核对本次 Golden 专用**同一提交、同一环境、Project A/B** 的后端 JAR + 前端压缩包真实 SHA-256、Release 运行进程观察索引、R1/R2/R3～R8、Phase4 Source/Usage、Phase5 Metric、真实 Project/权限、Key/IP、J1～J5 浏览器路径、版本并发/失败恢复以及四个角色的审批引用；
- **人工正式裁决**：不能由任何 JSON 声明或脚本自动完成。即使全部证据结构齐备、文件哈希一致，程序最多输出 `READY_FOR_HUMAN_REVIEW`、`automatedProductPass=false`、`p0ExitSigned=false`。Release 必须独立核对**运行中的容器/进程**而非仅比对构建声明，QA 与 Product 再正式签署。

### QA 受控环境输入及执行

QA 在非生产隔离环境统一运行 R1/R2、Phase4/5、双 Project 及 R3～R8（**需授权才能执行真正 Query/Invoke、恢复写操作或故障布置**），将经过脱敏的原始 JSON、浏览器交互索引和负向场景证明保存在受控 `<evidence-root>`。不要把 Cookie、认证头、真实 API Key、密码、用户业务行、原始 SQL、未经审查的屏幕或未批准的故障日志提交到 Git。

由 QA 和 Release 另外制作受控的 `p0-exit-receipt.local.json`：

- 根字段：`schema="P0-GOLDEN-EXIT-HANDOFF-V1"`、`repositoryCommit`（当前实际**已部署**的 40 位 SHA）、`environmentRef`、`environmentClass="AUTHORIZED_NON_PRODUCTION"`、`deployedAt`（带时区 ISO8601）、`projectPair:{primary,control}`（两个不同正整数 ID）。
- `deployment.backend` 和 `deployment.frontend`：各提供 `commit`、`artifactSha256`、`artifactFile`（相对于受控 evidence-root 的**实际 JAR 或前端压缩包**，不可仅空填声明）、`evidenceRef`（构建 Job/制品校验号）；`deployment.process`：`commit`、与后台产物相同的 `artifactSha256`、`evidenceRef`；另需 `releaseVerifiedProcess=true`、`processObservationRef`（Release 从进程/部署控制器独立观察的证据索引）。程序不会替 Release 验证日志真实性。
- `evidence` 为**完整场景键 → 收据**的映射：每项 `file`（相对路径）、`sha256`（文件真实 SHA256）、`deploymentCommit`、`projectId`、`controlProjectId`、`observedAt`（部署后）、`evidenceKind`、`reviewerRef`。R1/R2/R3～R7 要同时核对**文件内部**的 `repositoryCommit/projectId/capturedAt`，不能把历史 2026-10-04 R1/R2 用新的外部清单冒充当期验收。R8 必须是 `CONSISTENT_PARTIAL_EVIDENCE` 的 7 项结构验证，Phase4 的 16 条精确 Source Audit/Usage 断言必须全部为 true，Phase5 必须保留实际 Metric 发布/版本与来源断言，受限 Project HTTP 探针必须有至少 9 条 PASS。
- 场景键完整集由 `python scripts/product/golden-sample/p0_exit_readiness.py` 的 `requiredSlots` 输出确定，包括 21 个来源/角色/浏览器/Key-IP/并发及故障恢复证据键（含 Phase5 Metric）；R8 的 `evidenceKind=OFFLINE_STRUCTURAL_GATE`，Project/Phase5 = `LIVE_HTTP`，浏览器 = `LIVE_BROWSER`，其余使用 `REAL_API_DB`。**严格拒绝用 OFFLINE_MOCK 顶替真人/真实环境证据。**
- `signoffs.QA/Product/Release/TruthOwner` 各需 `decision="APPROVED"`、`reviewer`、`approvalRef`。这些只是审查索引与人类批准的声明，不代表校验器验证了签名真实性；**必须有可复核的独立审批系统记录**。如依法/合规需要延期，Product+QA 必须另行明确“延期场景、风险、Owner、补救及期限”，不能通过填一个模拟 APPROVED 自动放行。

示例执行：

```bash
# 无参数：打印完整必需证据清单；退出码 2，永不视为 E2E 通过
python scripts/product/golden-sample/p0_exit_readiness.py

# QA/Release 仅在所有输入由真实部署取证后执行：
python scripts/product/golden-sample/p0_exit_readiness.py \
  --manifest /secure/qa/p0-exit-receipt.local.json \
  --evidence-root /secure/qa/reports \
  --expected-commit YOUR_ACTUAL_DEPLOYED_40_CHAR_SHA \
  --output /secure/qa/p0-exit-review.local.json
```

**退出码**：`1=证据自相矛盾、签署/版本/哈希字段非法`（拒绝；需重取证）；`2=仍缺证据或者结构完整但待独立人工裁决`（不能视作 CI 成功或正式 PASS）；本脚本不产生 `0=产品正式放行`。GitHub Golden CI 只覆盖离线测试和默认 BLOCKED 反例，不访问真实部署。

**最后裁决**：当前缺实际同部署 QA 环境与 R1～R8/Phase4/5、前后端真实产物和浏览器、Key/IP、故障恢复、正式四方签名证据，因此 `#336 OPEN / GOLDEN=BLOCKED_EVIDENCE / P0_EXIT_NOT_SIGNED`。这不是在证明业务失败；待 QA/Release 提供可信实测资料或经 Product+QA 批准限范围延期，再由**有权人员**裁决，而不创建第五个 PR。
