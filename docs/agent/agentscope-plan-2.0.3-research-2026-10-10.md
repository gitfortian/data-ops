# AgentScope Java 2.0.3 PlanMode 与 F-039 前置技术核查

Status: SDK_ISOLATED_CI_VERIFIED / PRODUCTION_INTEGRATION_NOT_APPROVED  
Issue: #494 / Epic #493  
Baseline: gitfortian/data-ops main，2026-10-10  
SDK pin: data-ops-business-agent/pom.xml 中 AgentScope 2.0.3  
禁止事项：不升级 SDK、不替换生产 ReActAgent、不打开生产 Plan/写工具。

## 1. 可直接证实的当前源码事实

| 事实 | 定位 | 结论 |
| --- | --- | --- |
| SDK 2.0.3 harness 依赖已加入模块 | data-ops-business/data-ops-business-agent/pom.xml | **静态确认**；依赖存在不等于 Plan 已接线 |
| 普通/结构化场景使用 ReActAgent.builder | AgentRuntime.java 的 assemble 与 structuredScenario | **静态确认**；不是生产 HarnessAgent PlanMode |
| 原澄清由 externalTool 与 ToolResultMessage 恢复 | RequestClarificationTool / AgentRuntime.resume | **静态确认**；不等价于 permission ASK 的 ConfirmResult |
| StateStore 是 Mysql/Postgres 官方实现 | AgentStateStoreWiring.java | **静态确认**；只承诺消息/状态，没有 PLAN.md 正文 |
| Turn 由 Dispatcher/Executor/Registry 拥有 | AgentTurnExecutor、Agent DOMAIN.md | **静态确认**；孤儿 RUNNING → INTERRUPTED，不中途续推 |
| 轮次工具预算有 StateStore 辅助槽 | TurnToolBudgetState.java | **静态确认**；不是 F-039 跨 turn 的总预算 |
| Metadata 有 MetadataQueryApi | metadata/api/MetadataQueryApi.java | **静态确认**；不能凭此断定已有初始化需要的采集覆盖/指纹快照 |

当前最大集成风险不是 SDK jar 缺失，而是 **既有 ReActAgent 与 Harness PlanMode 生命周期/权限确认协议不同**。#495 不应直接调用 builder.enablePlanMode 就宣称接入完成。

## 2. 上游 v2.0.3 源码（阅读证据，非本仓库行为测试）

固定 tag： https://github.com/agentscope-ai/agentscope-java/tree/v2.0.3

- PlanModeManager：agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java；仅将 active 与 path 放 AgentState，计划正文经 WorkspaceManager 写文件。
- PlanModeTools：agentscope-harness/src/main/java/io/agentscope/harness/agent/tool/PlanModeTools.java；plan_enter / plan_write / plan_exit；plan_exit 使用权限 ASK。
- PlanModeMiddleware：agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/PlanModeMiddleware.java；拦截 plan 阶段的非白名单工具。
- 官方示例：agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/harness/planmode/PlanModeManualExample.java；RequireUserConfirmEvent 后用 ConfirmResult 恢复。

特别注意：上游 issue agentscope-java#1910 报告 PlanModeMiddleware 白名单与 PermissionEngine DEFAULT 的 ASK 策略可能不同步。该报告 **不能直接证明** v2.0.3 本仓库仍有同一问题，必须复现并记录。在此之前禁止默认依靠白名单自动授权 plan_write，也禁止使用 BYPASS 回避问题。

SDK 官方文档：https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/harness/plan-mode.md

## 3. 已加入的无模型 SDK 隔离实验

隔离测试类：

- data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/runtime/SourceSemanticPlanModeSdkContractTest.java
- data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/runtime/SourceSemanticPlanPermissionContractTest.java
- data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/runtime/SourceSemanticPlanRecoveryContractTest.java
- data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/runtime/SourceSemanticPlanHitlSdkContractTest.java
- data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/runtime/SourceSemanticPlanMysqlStateContractTest.java

- 使用固定 SDK 的 WorkspaceManager/PlanModeManager，两个不同任务分离的临时 workspace，验证相同逻辑 plan 路径各自写入不同内容。
- 直接使用 2.0.3 PermissionEngine 与原生 PlanModeTools 验证 DEFAULT 下 plan_write 的 ASK、显式最小 ALLOW、plan_exit 的 ASK，以及 DONT_ASK 不能直接放行退出。
- 直接使用官方 JsonFileAgentStateStore 和 WorkspaceManager 验证关闭并重建后的计划状态/正文回读、缺失文件不能凭 state path 假定成功、内容变更后 hash 不同；确认审批 envelope 应为 ConfirmResult + Msg.METADATA_CONFIRM_RESULTS。注意这只是 SDK 文件存储行为与消息形状，**不是** MySQL/PostgreSQL 的生产 StateStore 完整恢复或真正 HITL 事件交互。
- 验证 enter/write/exit 不变量：mode flag、plan path、退出后引用保留，不操作生产 AgentTurn，也不访问模型、数据库或真实 Semantic。
- 此测试是 **SDK 文件/状态的最小实验证据**，不是 permission ASK、StateStore round-trip、真实长任务或生产 E2E 的替代。

建议复核命令（有 Java/Maven 环境的 CI/工作站执行）：

    ./mvnw -pl data-ops-business/data-ops-business-agent -am -Dtest=SourceSemanticPlan*ContractTest -Dsurefire.failIfNoSpecifiedTests=false test

**精确 CI 证据（2026-10-10）**：PR #499 commit `e8632ab892309d679847cc4c8a574e8b5e0cfd78`，Product Guard 和 Architecture Checks 均 SUCCESS。Architecture 后端 job `114107317073`：Data Ops Business Agent **378 tests / 0 failures / 0 errors / 0 skipped，BUILD SUCCESS**。本项五组 SDK 隔离测试分别 4 + 2 + 5 + 2 + 1 = **14/14 PASS**。

本次真实 HarnessAgent SDK 脚本模型实验验证：plan_exit 发出 RequireUserConfirmEvent；同一请求的 ConfirmResult(true) 放行、false 保持计划、过期/重复 ID 拒绝；JsonFile StateStore 关闭重建后可恢复待确认。MySQL 环境中验证 SDK AgentState 的 plan flag/path 按 user/session 隔离并可重开。**均不代表**本项目生产 AgentTurn/Executor 已实现上述行为。

有一轮失败的真实记录：commit `cd89ad4` 的 CI run `38016005789` 中，模拟「plans 目录与文件冲突就必须抛异常」的测试失败。根因是文件后端的 overlay/namespace 与该本地假设不同，不能用该冲突模拟后端不可用。改为直接注入 WorkspaceManager 后端写异常并验证传播；在上述最新 CI 中 **PASS**。不得删掉或隐瞒这条历史失败。

## 4. 尚需逐项补齐的行为实验

| 编号 | 实验 | 必须看到的行为 | 当前状态 |
| --- | --- | --- | --- |
| P01 | SDK 2.0.3 隔离测试编译 | 已按模块完成 Maven 编译 | PASS（commit 103b71a，CI 38014050444） |
| P02 | plan_enter/write/exit | SDK 状态切换/文件写入与计划引用保留 | PASS（隔离 workspace，commit 103b71a） |
| P03 | DEFAULT permission 与 ASK | SDK DEFAULT 下 plan_write ASK，明确局部 ALLOW；plan_exit 保持 ASK | PASS（SDK PermissionEngine + Harness HITL；生产工具白名单待审） |
| P04 | plan_exit 批准/拒绝/重复 | SDK Harness 完整事件、批准/拒绝、伪造/重复拒绝及文件 Store 待确认恢复 | PASS（SDK 隔离 4/4；生产 Executor 未接入） |
| P05 | 本项目 resume 适配 | SDK ConfirmResult / Msg.METADATA_CONFIRM_RESULTS 完整恢复；本项目仍只使用原 ToolResultMessage | PARTIAL（SDK PASS，生产 AgentRuntime/Executor 适配 NOT_RUN） |
| P06 | 双任务隔离 | 独立 task workspace 的计划写入互不覆盖 | PARTIAL（隔离文件 PASS；同用户真实并发/权限/StateStore 还未验证） |
| P07 | 任务 workspace 重建 | JSON Store + workspace 正文回读/缺失/漂移、明确后端异常传播；MySQL Store 状态重建 | PARTIAL（隔离 5/5 + MySQL 1/1 PASS；跨节点正文持久化/生产阻断未接线） |
| P08 | 权限决策前预算 | 含拒绝/重试/HITL、取消、不双扣，不允许额度超支调用 | NOT_RUN |
| P09 | Executor/Registry 兼容 | QUEUED/RUNNING/WAITING_INPUT 与 permission pending 不冲突；重启 INTERRUPTED | NOT_RUN |
| P10 | 工具边界/跨域 | 无 shell、Python、SQL、业务写工具；仅显式只读投影 | NOT_RUN |

说明：P01–P04 的 **SDK 隔离环境**已取得真实 CI 行为证据；P05–P07 只有部分完成；P08–P10 未验证。全部仍不足以关闭 #494 的正式业务接线/Owner 审批。

## 5. 候选技术方案（待架构评审）

用户一次初始化任务的唯一推荐身份：projectId + userId + taskId；每个计划/片段绑定精确 taskId、sessionId、turnId、source fingerprint、Skill revision，避免同用户两个 task 共享默认 plans/PLAN.md。即使 SDK 按 (userId, sessionId) 隔离推理状态，也需要证明文件系统实际按 task namespace 隔离。

任务存储只追加有限任务范围、计划 revision/hash、片段→原 turn、候选 revision、累积 token/tool/time 配额、保留/清理与回执索引；**不复制**原轮次状态与消息，不用 task 状态覆盖原 TURN truth。任务恢复先查原 Repository 的 QUEUED/WAITING_INPUT/终态和官方 StateStore，再判定是否可重建新片段。

拟新增公共合同方向（签名待 Owner 决定）：

| Corridor | 合同诉求 | 强制安全 |
| --- | --- | --- |
| Agent gateway → Metadata 公共只读投影 | datasource/schema/table/column、采集身份、读取时点、逐对象指纹、分页与不完整范围 | 服务端项目权限、白名单、强限额；无 JDBC/任意 SQL |
| Agent gateway → Semantic 公共只读匹配 | 现有业务域/过程/标准/字段、真实 ID/版本、查找覆盖 | 不完整不能认定不存在；不返内部表 |
| Semantic 原应用命令（人工独立调用） | 选定候选/依赖快照的批量创建/复用、逐项回执与审计 | 预检重查、幂等键+digest、源域事务与唯一约束；Agent 不持正式业务事务 |
| Agent → 原 Modeling/Metric 链接 | 稳定 ID/精确版本回链 | 目标页面自行检查当前项目及权限 |

**未决**：是否批准新增任务级 persistence、workspace file backend 和保留策略、总额度模型、来源指纹合同、Semantic 命令事务/回执表的归属及审批联动。在这些答案成为 ACCEPTED/APPROVED 合同前，不实施生产代码。

## 6. 向 #495 的明确交接门槛

- PD-009 ACCEPTED、F-039 APPROVED/IMPLEMENTING；相关 Domain/Architecture owner 无环评审完成。
- P01–P10 按真实可执行证据补齐；失败/阻断不可绕过，失败则修订方案并复审。
- 数据源和 Semantic 公共 API 的身份、覆盖、并发、幂等与错误语义冻结。
- 文件 backend、跨进程/实例隔离与删除时机、实际试点上限和恢复行为获准。
- 变更审计、旧助手 F-023/F-026/F-028/F-029 回归范围明确，#498 黄金样本与手工基线开始采集。

## 7. 技术结论与隔离界限（2026-10-10）

1. SDK 2.0.3 的 PlanMode 写入至少有**两道不同的工具放行机制**：PlanModeMiddleware 的规划期可执行白名单，以及 PermissionEngine 的授权决策。白名单只回答是否进入工具链，DEFAULT 仍可能 ASK。对此必须明确配置，仅可在受限计划工具上使用局部 ALLOW；`plan_exit` 的用户确认不允许一并自动放行。
2. PlanManager 只持有激活位与工作区相对路径；计划正文是 WorkspaceManager 管理的文件。未来任务若保存了 `PLAN.md` 路径但文件不可读、hash 不匹配或受其他任务覆盖，必须 **BLOCKED / NEED_REPLAN**，而不是继续分析或写入。
3. 任务级 `taskId` 应是本次隔离工作区的命名空间，光按 userId 不能隔离相同用户的并发任务。文件后端选择需要覆盖节点切换、备份恢复和 TTL，不能以临时目录代表生产承诺。
4. Plan approval 的 `ConfirmResult` 限于 SDK permission pending；它只表示用户批准计划修订，后续 Semantic 批量保存仍必须独立获得用户明确选择、权限、版本与幂等回执。
5. P04 SDK 隔离通过但不能代替生产 Executor；剩余 P05–P10 中的生产权限、任务预算、跨节点 workspace 与真实模型事件仍是阻断项，不能因为本分支 CI 绿色而放行 #495 生产接线。

## 8. 必须由应用补强的 SDK 安全限制

1. SDK 在用户批准 `plan_exit` 时**不校验**文件是否已生成/当前内容是否与待确认计划 hash 一致。隔离测试允许尚未写 PLAN.md 的脚本 Agent 获得 plan_exit 确认并进入 BUILD。因此 F-039 不能直接公开 SDK Build 模式：必须在人工展示及批准后再核对计划正文、修订、任务与来源的精确绑定；否则失败封锁。
2. `plan_enter/plan_write` 同时受到 PlanModeMiddleware 阶段白名单和 PermissionEngine 两层控制；DEFAULT 对 plan_write 为 ASK，不能借 `BYPASS` 或全局 ALLOW 跳过。
3. 当前 `TurnToolBudgetState` 是 **每 turn** 单一 StateStore slot，在新 turn 时重置，无法天然承担 F-039 跨 turn 累计预算；须在生产入口决策前建立独立 task 总额度/原子预留，失败不得调用模型；不能因为 SDK permission ASK 的确认成功而重置预算。
4. 本 PR 的 JsonFile/MySQL 证据仅覆盖 SDK 精确边界。真实生产 MySQL/PostgreSQL、现有 AgentTurn / WAITING_INPUT / INTERRUPTED / Registry 和用户授权、跨节点 workspace、TTL、取消/重放均待 Owner 批准与上线前 E2E。
