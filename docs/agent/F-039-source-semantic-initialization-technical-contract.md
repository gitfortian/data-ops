# F-039 / #494 — 来源语义初始化跨域接口与任务存储技术合同（评审稿）

Status: PROPOSED / NOT_APPROVED  
Date: 2026-10-10  
Related: #493、#494、PD-009（PROPOSED）、F-039（DRAFT）、PR #499  
Baseline: main `63a654b842328621c5cfdc359763987762778fa3`。

> 这里只描述可评审的拟议接口和不变量，不创建 API、不注册生产工具、不对源域实施 schema 迁移。未经 Product/Metadata/Semantic/Agent Owner 评审，不作为编码指令。

## 1. 当前实现事实 vs 本次缺口

| 子系统 | 已有接口/调用路径（真实源码） | 当前缺口 / 不能推导 |
| --- | --- | --- |
| Metadata | `MetadataQueryApi.search/getEntity/listEntities/listChildren/listPhysicalColumns/findPhysicalTable`; `EntityDTO` 含表列坐标与 facts/attributes/slotValues | 无一个已证明携带采集 epoch、逐表列指纹、分页完整性和源权限重查的不可变初始化快照；`listPhysicalColumns` 返回 List，不声明截断 |
| Modeling | `MappingSuggestionQueryApi.require` 只读固定来源表列；`Context.truncated` 显示候选范围不完整 | 针对**单模型/单目标字段**，不能冒充一次来源系统的完整采集 |
| Semantic | `SemanticDomainApi.CreateRequest`、`SemanticProcessApi.CreateRequest`、`SemanticFieldApi.CreateRequest`、`SemanticStandardApi.CreateRequest/CodeSetSaveRequest` | 这些是领域写入 DTO，不等于跨多个对象的依赖预检+幂等批量命令 |
| Semantic | `StandardQueryApi.get/labels/existsCodeSet` | 不提供“完整域/过程/字段/标准匹配、覆盖结果”合同 |
| Semantic | `StandardCaptureApi.capture`：同 kind+code 返回 `created=false` 已存在定义 | `created=false` **不能证明已有同码定义与本次语义相同**；不能直接作为 F-039 的保存幂等回执 |
| Agent | `AgentRuntime` 使用 `ReActAgent`；`AgentTurnExecutor`/StateStore 保存轮次；`StandardMatchGateway` 只经建模/标准 public API | 尚无 task 级 plan/step/候选/额度的唯一拥有者；旧 ToolResultMessage 澄清和 PlanMode 权限 ASK 非同一协议 |

依据：上述类各自的 Java public 源文件、Agent `ARCHITECTURE.md`、`DEPENDENCIES.md`、`DOMAIN.md`。

## 2. 允许的调用图（草案）

```text
原 Datasource / Metadata UI
      | (授权、显式选择)
      v
Agent Task Application  ───────> Agent 原 Turn Dispatcher / Executor
      |                           |       |
      |                           |       └── SDK StateStore / Permission HITL
      |                           └── Agent Runtime → SDK Plan workspace
      |
      +---> Agent Gateway ---> Metadata 公共只读 Evidence API
      |
      +---> Agent Gateway ---> Semantic 公共只读 Match/Preflight API
      |
      └──(用户独立确认保存请求)──> Semantic 应用 Command API
                                   ├── Domain/Process/Field/Standard 原存储/约束
                                   ├── 原审批/审计/状态
                                   └── 幂等回执与正式引用
```

禁止：Agent → Metadata/ Semantic Mapper/Repository；Metadata/Semantic → Agent；Model → Semantic 写；Agent 工具箱调用 Semantic 保存；跨 Project 或任意 SQL/JDBC。

现有 Agent `DEPENDENCIES.md` 的 `gateway` corridor 尚未列 Metadata：**不得**为了绕过架构测试把任意 metadata 类型引入 `runtime`/`toolset`。若最终批准 API，先修改 Owner 的 `DEPENDENCIES.md` + `AgentDependencyBoundaryTest`，只放行 `gateway → metadata.api`。审查直接 Maven 依赖的循环，考虑独立公共 SPI 而非复用 metadata 内部实现。

## 3. Metadata 所需的只读快照合同（拟议）

一项已授权任务的一次物理来源范围是不可变身份：

```text
SourceScopeRef {
  projectId  // 只能由服务器可信 ProjectContext 取得，不接受模型决定
  datasourceId, database, schema
  explicitTableIds + explicitColumnIds
  captureIdentity, captureReadAt
}
```

拟新增一个 **Metadata-owned** 只读、强分页的 Evidence API（名称待 Owner 定）：

```text
snapshot(scope, cursor, limit) -> {
  canonical physical identities,
  whitelisted table / column schema facts,
  captureId, captureTime, readTime,
  per-object contentFingerprint, overall selected scope digest,
  page coverage { requested, returned, hasMore, nextCursor, missingIds },
  status READY | PARTIAL | UNAVAILABLE | FORBIDDEN
}
```

要求：
- 在服务端鉴权、验证 datasource/table/column 属于当前 Project；快照与后续每次读取再验证授权，禁止输入任意物理 SQL。
- 指纹稳定化方法、排序、空值、Schema 变更、采集失败/超期必须写明；不同采集批次不能静默拼成一个全量快照。
- `PARTIAL`、未采集、`UNAVAILABLE`、`FORBIDDEN` 显示真实覆盖，不能将空列表冒充“这个对象不存在”。
- 单片段与本次 task 绑定整个来源身份和已知指纹，片段过期后禁止向后继候选/保存传播旧证明。

**未决**：现有 asset catalog + 独立采集快照的哪个组合能够满足协议？确认之前不新增 metadata 第二张事实表。

## 4. Semantic 只读匹配与预检（拟议）

拟新增窄只读 Match/Preflight Facade：
- `lookupDefinitions`：业务域、过程、TYPE/UNIT/CODE、标准字段、过程字段，返回 `id + project + version/status + coding/kind + maxCoverage/truncated`，已生效/待审批要分开。
- `preflight`：唯一确定的人审选择 + 不可变 candidateRevision + scopeFingerprint + 当前授权 + expectedVersion + 完整依赖闭包 + payloadDigest。
- 可回读、可失效的结果凭据需要绑定当前 operator/project/task，不是永久写权限，也不可由 Agent/模型随意延长。
- 重复编码、同码不同义、类型不匹配、码值清单不完整、UNIT/粒度缺证据都返回明确阻断/待确认，而非随机生成新 code 绕过。
- 所有匹配均需报告 `hasMore/truncated`。覆盖不完整只说明“未确定”，不可据此新建重名对象。

预检**零正式业务写入**；缓存与 Candidate 只在 Agent 任务材料中保留，Semantic 永不把未经确认候选当正式事实。

## 5. Semantic 独立批量 Command 与线性化点（拟议）

用户在 Semantic 自己的保存/审核操作中，提交：

```text
AdoptionCommand {
  projectContext (server-resolved),
  operatorId (server-resolved),
  taskId, candidateRevision, selectedItemIds,
  scopeFingerprint, preflightReceiptId,
  payloadDigest, idempotencyKey
}
```

草案规则：
1. 调用方只传选中项的业务 payload；Semantic 对 ID、引用、版本、operator、project、来源指纹、批准范围重新校验，决策不可只依赖前端/Agent 预检结果。
2. 建议唯一幂等单位是 `project + task + candidateRevision + itemId + idempotencyKey`，另存 `payloadDigest`。**同键同内容**只返回正式已提交回执，**同键不同内容**拒绝，未选项绝不被创建。
3. 同一单元业务写与保存回执位于 Semantic 自有可证明事务线性化点；已有唯一约束处理并发，不靠先查再插；依赖链成功后创建后继，独立项允许继续。
4. 同码已有对象必须确认同一业务含义与版/状态后才能返回 `REUSED`；`StandardCaptureApi.created=false` 不等于这次创建成功，返回已有对象也不等于可以覆盖。
5. `CODE` 仍使用 `CodeSetSaveRequest` 的原整体事务；启用、审核沿现有 APPROVAL/Publish，不因保存成功自动生效。
6. 网络超时或失去核对权限时返回 `UNKNOWN / NEEDS_RECONCILIATION` 语义；先查询业务回执，不能盲重试或把 timeout 当失败。
7. 逐项回执必须有 `createdId, version, sourceRef, status CREATED|REUSED|WAITING_ENABLE|FAILED|NOT_EXECUTED|NEEDS_RECONCILIATION` 和原审计关联；原正式对象由原 Semantic API 回读验证，旧成功结果不得因后续复用定义版本变化被改写。

本节仅建议合同行为与事务边界，不预言当前数据库表/Mapper 已实现。本方案要求 Semantic Owner 确定 receipt 与真实业务操作的同一事务、部分成功和撤权语义，不能由 Agent 项目自己实现“伪幂等账本”。

## 6. Agent Task 的持久化与恢复（拟议）

最小稳定身份 `(projectId, operatorId, taskId)`；必须有独立 taskId workspace（不能共享 `(userId, plans/PLAN.md)`）。建议仅持久化：

| 有限材料 | 身份/版本 | 真正 Owner |
| --- | --- | --- |
| 来源范围与选择 | sourceScopeFingerprint、coverage、capture version | Metadata 真正物理事实 |
| 计划文件与 hash | planRevision、workspaceNamespace、planSha256 | Workspace 文件正文；Agent task 保存稳定引用/摘要 |
| 片段绑定 | stepId、turnId、scopeFingerprint、skillRevision | 原 Turn/StateStore 保持运行/消息真相 |
| 累计额度 | 已确认的 token/tool/time/turn reservation | Agent task 独立跨-turn 帐本；原每轮预算仍保留 |
| 候选/选择快照 | candidateRevision、preflightDigest | Agent 有界草稿；Semantic 仅持正式采纳 |
| 保存回执索引 | idempotencyKey、正式 Receipt ID | Semantic 具有权威写入/回读结果 |

- 并发继续、重试、pause/stop 要通过锁或 CAS；**只要总额度无法持久化，就不允许新模型调用**。
- 原 `RUNNING` 孤儿依现有合同转 `INTERRUPTED`；恢复只能在重查 source/skill/权限后创建**新** turn，不能静默重用旧轮并计成功。
- 仅可使用只读 schema 工具和受限计划文件写入；PlanMode `plan_exit` 允许分析 **不** 允许业务创建。
- 计划 hash 不匹配 / workspace 丢失 / 文件后端不可用 → 明确阻断；StateStore 里的 `plans/PLAN.md` 路径不是计划正文。
- 生产需要明确文件后端的实例共享与重启恢复、隔离、权限边界、保留/清理与审计，不接受临时进程目录充当跨节点承诺。

## 7. #494 前置测试门禁和后续交接

| 关键核查 | 最小证据 | 状态 |
| --- | --- | --- |
| 现有 SDK 2.0.3 plan 路径/权限引擎 | PR #499 既有两个实验，在 commit 103b71a 的 368-test CI 中通过 | PASS（隔离 SDK） |
| 计划正文重建/缺失/漂移及 ConfirmResult 消息封装 | SourceSemanticPlanRecoveryContractTest | PASS（隔离 5/5，CI 38016315346） |
| SDK permission HITL 真正 pause/approve/reject/duplicate | HarnessAgent 原生 RequireUserConfirmEvent + ConfirmResult + JSON Store 重开 | PASS（SDK 隔离 4/4，非本项目 Executor） |
| 数据库 StateStore / 原 turn 与 permission pending 交互 | MySQL 独立状态恢复 1/1；原 turn / PostgreSQL 未验 | PARTIAL（MySQL SDK Store PASS；生产旧 turn NOT_RUN） |
| 总预算原子预留与失败/取消 | 双任务、写失败、跨轮次不重置 | NOT_RUN |
| 跨域 API/依赖无环、产品合同 | Owner 评审记录 + 接口测试 | BLOCKED_BY_APPROVAL |
| #495 及后续生产实现 | PD-009 ACCEPTED / F-039 APPROVED、前置真实证据 | BLOCKED |

**当前结论**：仅隔离测试和设计可推进，不能宣告实际业务初始化已实现。后续 Owner 应依据本稿逐条 APPROVE/REVISE/REJECT，并把长期规则归入各自 DOMAIN/ARCHITECTURE，而非直接推广本提案。

## 8. SDK 2.0.3 隔离验证实绩（2026-10-10）

PR #499 commit `e8632ab892309d679847cc4c8a574e8b5e0cfd78`：Product Guard / Architecture Checks 均通过；Architecture Checks 后端 job `114107317073`，Agent 378/378 通过，新测 5 类共 14/14 通过。发生过的一次目录冲突模拟失败已按真实 SDK filesystem 分层修复为 WorkspaceManager 明确异常注入；此前失败日志保留在 CI run `38016005789`。

**边界**：SDK `plan_exit` 即使批准也不要求 `PLAN.md` 文件已存在，且不绑定用户看到的 plan/hash。F-039 必须由应用层阻断无正文、hash/范围不匹配的批准后执行。SDK 采用 ConfirmResult 不能直接复用原生产 `ToolResultMessage`；原 `TurnToolBudgetState` 是单 turn 配额而非跨 turn 总额度。本合同仍为 PROPOSED，必须待 Owner 审核才能新建 Metadata/Semantic API 或落地生产任务写入。
