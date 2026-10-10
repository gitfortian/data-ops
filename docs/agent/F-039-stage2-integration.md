# F-039 / #495 — 2/5 单 PR 综合集成及验收边界

Status: **ENGINEERING_INTEGRATION / FEATURE FLAG OFF BY DEFAULT** (2026-10-10)  
Delivery: PR #504, main from merged #503 (`ac8dea69c4f073f329a1e295a38e0a19e99baf75`).

## 用户链路和事实来源

| 操作 | 归属与硬边界 | 核查结果 |
| --- | --- | --- |
| 原 DataSource 卡片跳转 | 无新一级导航，携带 dataSourceId 到原 Agent 页面 | UI 显示来源选择面板 |
| 表/字段选择 | 当前登录用户 + DataSource READ + Agent CHAT_RUN；DataSourceReader 项目内已保存数据源；MetadataQueryApi 项目内统一目录、PhysicalScopeEvidenceQueryApi 完整性 | 20 表 / 500 列范围上限；缺表、部分采集、列数不一致拒绝 |
| 任务创建 | 服务端生成 UUID；来源全字段和采集指纹冻结到 AgentScope CAS StateStore；原 AgentSessionOwnerValidator 固定本人/项目 | 原上下文不允许未授权补源 |
| PLAN.md | 服务端绝对共享 workspace 路径，不接收用户 file path；写入真实 Markdown；SourceSemanticPlanDocumentGuard 回读 bytes/hash | 计划未经本人哈希确认，禁止预留原 turn |
| 下一片 | 再次核验 DataSource、完整 Metadata、PLAN.md、当前会话和用户权限后，预留 turn/tool 预算 | 原 AgentChatService → yak_agent_turn QUEUED → 原 Dispatcher；不新建 worker |
| 原 Turn 执行 | TurnInput 冻结 taskId；原 Executor 在模型调用前读取 CAS 任务，并核对冻结的 turnId/session/plan | 已取消/漂移/不存在时拒绝模型执行 |
| 工具隔离 | AgentExecutionContext 对 F-039 设置 sourceReadOnly policy；TaskToolPolicyMiddleware 隔离所有可调用工具（包括 SDK 动态工具、SQL、Python、保存报告） | 只允许基于已给定的 Schema 事实生成业务假设；不会直接采样行/写语义 |
| 真实进度与成果 | 原 AgentTurnRepository/MessageTreeRepository 是真实状态和回答；只在完成且唯一 done assistant 消息存在时，复制有界结果至 CAS immutable Artifact，按 chunk/turn/source/plan/hash 回执完成 | 缺回答/空回答、修改、来源漂移时不能伪造完成 |
| 刷新、断开和恢复 | taskId 原页面路由可回访；读取会合并原 turn 的 QUEUED/RUNNING/WAITING_INPUT/TERMINAL，SDK CAS 保留分片和额度 | 真实原 turn 可以在浏览器关闭后继续，下一片仍需本人授权触发 |
| 服务端片段自动接续 | 每个原 Turn 确认 COMPLETED 且 assistant 消息树已最终落笔后，SourceSemanticAutoContinuation 使用 UserExecutionScope 重新恢复真实用户权限、项目成员关系；核实原 turn 收据后 CAS 尝试下一片。若撤权、漂移或预算失败则保持可回访状态、不盲目重试 | 无需页面常驻；进程在终态和提交下一片之间崩溃时仍需用户恢复推进 |
| 暂停/取消/恢复 | 暂停只阻断未来片段，取消先 CAS 停新工作再请求原 AgentChatService.cancelTurn；中断重新验证来源和计划且新建 turn | 迟到成果不得让 CANCELLED 恢复为 COMPLETED |

## 重要开关与部署前提

默认 **完全不装配** SourceSemanticTaskFacade / Controller / TurnFence。必须由部署人员显式设置：

```yaml
yak:
  agent:
    enabled: true
    source-semantic:
      enabled: true
      workspace-root: /mnt/shared/yak-agent-f039
```

这必须是部署者创建、绝对、可信且在所有运行节点一致的共享目录；运行时不根据请求建立根目录。建议只允许应用服务账号读写、记录磁盘备份和删除策略。没有共享挂载、权限或 PLAN.md 文件时必须 fail closed，不能退化为本地假已确认。原 AOP 项目上下文及 READ/CHAT_RUN 权限缺失会拒绝调用。

## 安全与未完成的真实运营验收

1. F-039/PD-009 仍为 DRAFT/PROPOSED，**工程 PR 可按用户要求正常评审/合并，但不能视为合同正式获准或生产启用授权**。
2. SDK Harness PlanMode 在 #499 已核验接口，但本轮持久计划来自应用层生成的 PLAN.md 和明确 HTTP hash 审核，**不是** AgentScope 原生 plan_enter / plan_exit + ConfirmResult 的真实交互闭环。生产要使用 SDK PlanMode 还需要跨节点真实确认与权限绑定的集成证据。
3. 原 AgentTaskToolPolicy 零工具隔离可以阻止模型调用任何数据/业务工具，但基于已采集元数据得出的字段业务含义与关系仍可能是推测，需在人审中保留未知/证据。
4. 任务总预算当前有跨 turn 的调用与轮次预留及原 runtime per-turn model timeout；**还没有跨轮实测的 token 和统一 wall-time 限额**，这不应被称为已完成预算验收。
5. 每片原 Turn 完成后新增服务端 SourceSemanticAutoContinuation，通过 UserExecutionScope **实时重查**用户登录有效性、项目成员关系和授权并触发下一片；不依赖浏览器循环。若进程在成功 Turn 和异步完成信号之间崩溃、服务端队列满或用户撤权，任务保持持久状态，恢复仍需用户主动查看/核对，**不是强可靠 outbox**；不能将这一 happy-path 测试冒称多节点 Exactly-Once。
6. CAS task + 原 Turn INSERT 分别事务：当前序列“先 reserve，再 insert；失败保留活动 turn ID”防止重复提交。Executor 在模型前核验 CAS 任务，不过**无法在两个存储间实现完全原子提交/取消，且正在运行模型/工具的终止延迟仍应在多节点环境压测**。
7. F-039 模型内容不做任意 SQL/Python/Shell/业务写，不能将用户背景作为可执行工具指令；任务不会创建正式 Semantic 资产、模型或指标。真实模型效果、数据源撤权、高可用、中途重启、磁盘损坏、SSE 与原会话长期消息量场景的 E2E 尚须在原真实环境核对。

## 对 #495 Acceptance 的真实判定

工程层面的来源选择 API/UI、范围证据核验、PLAN 实际文件核验、分片 CAS/turn 绑定、消息树成果封存、基本取消/暂停/中断恢复以及正反测试已经放到**同一 PR**。但对 Issue #495 的“服务端自动串行接续在进程崩溃及多节点条件下 Exactly-Once”“SDK 官方 PlanMode HITL”“任务总 token/time 限额”“真实环境 SI 全矩阵”“跨节点撤权零窗口”等项目，本 PR **不能伪报完成**。只有上述所有行为经产品 Owner 和真实部署环境核验之后才能关闭 #495，再进入 #496。用户已要求不再拆分，因此余下验收差距在 #495 本条记录/PR 说明中集中跟踪，不按组件继续拆 PR。

验收时记录 SDK Store 与 MySQL 的 CAS version、task/turn/trace、plan SHA、source/capture SHA、每片结果 hash，确保任何一项缺失直接阻断。
