# AgentScope Java 2.0.3 PlanMode 与 F-039 前置技术核查

Status: EXPERIMENT_IN_PROGRESS  
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

测试类：data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/runtime/SourceSemanticPlanModeSdkContractTest.java

- 使用固定 SDK 的 WorkspaceManager/PlanModeManager，两个不同任务分离的临时 workspace，验证相同逻辑 plan 路径各自写入不同内容。
- 验证 enter/write/exit 不变量：mode flag、plan path、退出后引用保留，不操作生产 AgentTurn，也不访问模型、数据库或真实 Semantic。
- 此测试是 **SDK 文件/状态的最小实验证据**，不是 permission ASK、StateStore round-trip、真实长任务或生产 E2E 的替代。

建议复核命令（有 Java/Maven 环境的 CI/工作站执行）：

    ./mvnw -pl data-ops-business/data-ops-business-agent -am -Dtest=SourceSemanticPlanModeSdkContractTest -Dsurefire.failIfNoSpecifiedTests=false test

此文档提交时，没有本地 Maven/数据库运行证据；测试结果仍是 NOT_RUN，需据实际 GitHub CI 状态更新。

## 4. 尚需逐项补齐的行为实验

| 编号 | 实验 | 必须看到的行为 | 当前状态 |
| --- | --- | --- | --- |
| P01 | 最小 SDK 2.0.3 编译 | 精确依赖、工具注册与类签名可编译 | NOT_RUN |
| P02 | plan_enter/write/exit | 计划正文由 workspace 写入，state path 不是正文 | NOT_RUN（代码已准备） |
| P03 | DEFAULT permission 与 ASK | enter/write 不出现意外要求；exit 必须要求确认 | NOT_RUN |
| P04 | plan_exit 批准/拒绝/重复 | RequireUserConfirmEvent ↔ ConfirmResult(toolCall) 逐项匹配；拒绝不解锁 | NOT_RUN |
| P05 | 本项目 resume 适配 | Msg.METADATA_CONFIRM_RESULTS，禁止 ToolResultMessage 冒充审批 | NOT_RUN |
| P06 | 双任务隔离 | 同用户不同任务计划、权限、上下文、文件不串读 | NOT_RUN（最小文件隔离试验已提交） |
| P07 | 任务 workspace 重建 | state 恢复 + 正文真实回读 hash；失败明确停止 | NOT_RUN |
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
