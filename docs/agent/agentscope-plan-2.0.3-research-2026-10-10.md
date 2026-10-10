# AgentScope Java 2.0.3 PlanMode 与 F-039 前置技术核查

Status: EXPERIMENT_IN_PROGRESS（部分 CI 已验证，仍非 #494 验收完成）  
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

- 使用固定 SDK 的 WorkspaceManager/PlanModeManager，两个不同任务分离的临时 workspace，验证相同逻辑 plan 路径各自写入不同内容。
- 直接使用 2.0.3 PermissionEngine 与原生 PlanModeTools 验证 DEFAULT 下 plan_write 的 ASK、显式最小 ALLOW、plan_exit 的 ASK，以及 DONT_ASK 不能直接放行退出。
- 直接使用官方 JsonFileAgentStateStore 和 WorkspaceManager 验证关闭并重建后的计划状态/正文回读、缺失文件不能凭 state path 假定成功、内容变更后 hash 不同；确认审批 envelope 应为 ConfirmResult + Msg.METADATA_CONFIRM_RESULTS。注意这只是 SDK 文件存储行为与消息形状，**不是** MySQL/PostgreSQL 的生产 StateStore 完整恢复或真正 HITL 事件交互。
- 验证 enter/write/exit 不变量：mode flag、plan path、退出后引用保留，不操作生产 AgentTurn，也不访问模型、数据库或真实 Semantic。
- 此测试是 **SDK 文件/状态的最小实验证据**，不是 permission ASK、StateStore round-trip、真实长任务或生产 E2E 的替代。

建议复核命令（有 Java/Maven 环境的 CI/工作站执行）：

    ./mvnw -pl data-ops-business/data-ops-business-agent -am -Dtest=SourceSemanticPlan*ContractTest -Dsurefire.failIfNoSpecifiedTests=false test

**已完成的 CI 事实**（commit \`103b71acad23b15df7e338c1468e1bb8ffd80518\`）：Product Guard 和 Architecture Checks 均为 SUCCESS；后端 Agent 模块在工作流 \`38014050444\` 的任务 \`114100392872\` 日志显示 **368 tests / 0 failure / 0 error / 0 skipped，BUILD SUCCESS**。当时两个隔离类各 2 个用例通过。后续新增的 RecoveryContractTest **尚需对应新 head CI 验证**，此旧成功不能自动覆盖新增改动。

## 4. 尚需逐项补齐的行为实验

| 编号 | 实验 | 必须看到的行为 | 当前状态 |
| --- | --- | --- | --- |
| P01 | SDK 2.0.3 隔离测试编译 | 已按模块完成 Maven 编译 | PASS（commit 103b71a，CI 38014050444） |
| P02 | plan_enter/write/exit | SDK 状态切换/文件写入与计划引用保留 | PASS（隔离 workspace，commit 103b71a） |
| P03 | DEFAULT permission 与 ASK | SDK 的 DEFAULT 下 plan_write 实际 ASK，须显式局部 ALLOW；plan_exit 保持 ASK | PARTIAL（PermissionEngine 独立测试 PASS，生产权限中间件与 HITL 未运行） |
| P04 | plan_exit 批准/拒绝/重复 | RequireUserConfirmEvent ↔ ConfirmResult(toolCall) 逐项匹配；拒绝不解锁 | NOT_RUN（新增 envelope 测试仅校验消息形状，不是流程） |
| P05 | 本项目 resume 适配 | Msg.METADATA_CONFIRM_RESULTS，禁止 ToolResultMessage 冒充审批 | NOT_RUN |
| P06 | 双任务隔离 | 独立 task workspace 的计划写入互不覆盖 | PARTIAL（隔离文件 PASS；同用户真实并发/权限/StateStore 还未验证） |
| P07 | 任务 workspace 重建 | 文件 StateStore 重建、正文缺失/改动检测的隔离实验 | NOT_RUN（新增测试待新 CI；真实生产持久化和失败阻断未接线） |
| P08 | 权限决策前预算 | 含拒绝/重试/HITL、取消、不双扣，不允许额度超支调用 | NOT_RUN |
| P09 | Executor/Registry 兼容 | QUEUED/RUNNING/WAITING_INPUT 与 permission pending 不冲突；重启 INTERRUPTED | NOT_RUN |
| P10 | 工具边界/跨域 | 无 shell、Python、SQL、业务写工具；仅显式只读投影 | NOT_RUN |

说明：P01/P02 的实际成功需要 CI 证据；P03–P10 **没有通过**。不能为了关闭 #494 把源码阅读或本地状态切换等同于生产路径通过。

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
5. 剩余 P04/P05/P07–P10、跨进程/数据库/权限预算与真实模型事件完整路径尚是阻断项，不能因为本分支 CI 绿色而放行 #495 生产接线。
