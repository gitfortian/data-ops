# Agent Architecture

本文件定义 Agent 的**长期架构 contract**。它描述稳定边界、角色、运行真相与依赖方向，不记录 Stage / Wave 过程；历史演进以 Git / PR 为准。

需求语义看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，包依赖看 `DEPENDENCIES.md`，代码风格看 `CODE_STYLE.md`，Review 标准看 `REVIEW.md`。

## 设计原则

1. **业务子系统优先。** package 本身表达架构。
2. **稳定入口，隐藏内部角色。** Controller 只进入 Application Facade；跨子系统只走声明过的 corridor。
3. **名字表达角色。** Service / Coordinator / Manager / Resolver / Reader / Gateway / Repository 不互相冒充。
4. **Truth 只有一个 owner。** 消息历史、会话元数据、报告、查询证据各自边界清晰。
5. **外部系统停在边界。** AgentScope、dataset 契约、Python 进程等实现细节不进入 Core Domain。
6. **LLM 输出是输入不是真相。** 一切模型产出必须经校验或与 Evidence 对照后才可使用。
7. **Query 与 Command 分离。** 会话/报告 read model 不修改推理状态。
8. **结构重构不偷改行为。** package move、class split 与 REST / DB / Domain semantic change 分开。
9. **架构规则必须可执行。** 文档 contract 由 architecture tests 与 dependency scan 守住。

## Package Map

```text
io.yak.ops.business.agent
├── controller          # HTTP inbound + SSE 端点 + transport mapper（事件 DTO 定义于此）
├── conversation        # 会话 command side：流式编排、HITL 恢复、生命周期命令、动态配置治理
│   └── query           # 会话列表 / 历史 read model
├── runtime             # AgentScope 边界：ReActAgent 组装、StateStore 装配、middleware、事件→domain 映射
├── toolset             # 分析与治理工具（@Tool 薄壳，委托 catalog/gateway/report）
├── catalog             # 数据集目录视图：schema 格式化、字段白名单校验
├── gateway             # outbound：DatasetCatalogGateway / DatasetQueryGateway / PythonRunnerGateway
├── report              # 报告保存与管理（command + read）
├── repository          # persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── domain              # framework-free core domain / value objects
├── memory              # 长期记忆读写（M1 已接线：写经 MemoryFlushService、读经
│                       #   LongTermMemoryPromptMiddleware；M2 巩固管线（合并/固化/归档）
│                       #   为规划中 WIP，repository 已有扩展方法但零调用者，见 Git 提交
│                       #   dea24fb5c「consolidation repository extensions (unwired)」）
└── config              # module configuration（Properties / ConditionalOnAgentEnabled）
```

production 不允许重新创建 `service / common / helper / utils` 业务大桶。完整 top-level dependency matrix 和 corridor 见 `DEPENDENCIES.md`。

## Core Domain Model

```text
AgentSession ──Start──> ReasoningTurn ──ToolCall──> QueryEvidence
                                    ├──> PendingClarify(HITL)
                                    └──> AgentReport
```

核心关系：

```text
Session != ReasoningTurn != Evidence != Report
```

`domain` 包内对象不依赖 Spring、AgentScope、dataset 与 MyBatis 类型。

## Truth Ownership

```text
官方 StateStore 表               = 对话消息与推理状态 truth
yak_agent_session                = 会话身份 / 归属 / 标题 truth
yak_agent_turn                   = 推理轮次生命周期 truth（提交与执行状态机）
yak_agent_turn_event             = 事件投递日志（可重建投影，非事实源）
yak_agent_report                 = 报告 truth
yak_agent_query_log              = 查询证据 trace truth
dataset 模块                      = 数据集与字段语义 truth（外部事实）
SSE 连接                          = 传输通道（非 truth）
```

## Stable Application Entries

仅以下类型是稳定 `@Service`：

```text
conversation.AgentChatService              # SubmitTurn / ResumeTurn / OpenEventStream / Cancel / Rename / Delete 编排入口；Trace 读模型经 conversation.query.turnTrace
conversation.query.AgentSessionQueryService # 会话列表 / 历史 / turnTrace v2 / 会话观测矩阵 read model（组装委托 conversation.query.TraceViewAssembler）
conversation.AgentConfigManageService      # 运行时动态配置治理读/写（稳定 use-case：治理 API + 前端配置面板消费）
report.AgentReportService                  # 报告保存 / 分页 / 详情 / 删除
```

入口关系：

```text
AgentController
   ├── conversation.AgentChatService
   ├── conversation.query.AgentSessionQueryService
   ├── conversation.AgentConfigManageService
   └── report.AgentReportService

内部专业角色使用 @Component 或普通对象。
```

## Conversation Subsystem

历史引用由 runtime 在原 START USER 消息 metadata 写入，仅引用已有 turnId；分组投影透传给原 read-side。AgentSessionQueryService 经原仓储核对引用唯一性、归属及完成状态，明确关联后读取原 trace，不按序号补配。不改变 StateStore 正文 owner、history HTTP 形状或依赖走廊。

会话继续读取由既有 AgentSessionQueryService 组合 Session / 最新 TurnInput / 反问投递投影；仍只通过 AgentRuntime.history 读 SDK 消息，不新增 runtime 命令走廊。Controller 新增兼容只读 continuation 视图，原 history 协议不变；无新状态或 persistence owner。

恢复页状态跟随只消费既有读取。精确停止经 AgentChatService → AgentTurnRegistry / AgentTurnRepository，按 turnId 与认领登记串行；Executor 继续拥有运行终态收尾。客户端检查调度不成为运行真相，无新增依赖走廊。

```text
AgentChatService
   ├── AgentSessionOwnerValidator # 会话归属校验与首访绑定（互斥真相已收敛到 turn 状态机，见 DOMAIN）
   ├── AgentTurnDispatcher        # 轮次调度：启动时 RUNNING 孤儿清障(INTERRUPTED) + QUEUED 周期扫描分发
   ├── AgentTurnExecutor          # 认领执行：CAS RUNNING -> runtime 推理消费 -> 事件帧持久化 -> 消息树落笔 -> 终态收敛
   ├── AgentEventStreamTailer     # SSE 订阅端尾随器：按 event_id 游标增量补发至终态（纯读，不影响执行）
   ├── AgentEventPublisher        # domain 事件 -> SSE 帧（含终态与错误映射）
   └── AgentStreamCoordinator     # SseEmitter 生命周期：建立、心跳、完成与断连清理

AgentTurnRegistry                # 运行中句柄登记（turnId 维度）：跨请求"停止生成"路由到 Disposable；
                                 # Flux.doOnCancel 只覆盖订阅端取消，覆盖不了该路由，故保留此登记
```

职责约束：

- 提交（QUEUED 落库）与执行（Dispatcher/Executor）分离：HTTP 线程绝不推理；
- 执行事件先落 `yak_agent_turn_event` 投递日志再被订阅端拉取，订阅端不反向影响执行；
- StreamCoordinator 不理解业务语义，只管理传输生命周期与游标补发；
- InvocationManager 是归属校验与 pending 校验的唯一执行点；
- 事件发布顺序遵循 runtime 上游顺序，不重排、不合并 thinking 片段以外的增量。

## Runtime Subsystem

```text
AgentRuntime                             # 无状态 ReActAgent 单例组装；Middleware 声明式装配（洋葱模型）
LlmResilienceMiddleware                  # onModelCall：单次硬超时/重试分类/每次尝试调用级记账（KIND_LLM_CALL）
ToolAuditMiddleware                      # onActing：全工具零侵入落 KIND_TOOL_CALL 步骤记录
SystemPromptAssemblyMiddleware           # onSystemPrompt：能力域提示词贡献者按序追加（失败静默降级）
CompactionMiddleware(harness)            # onReasoning：跨轮上下文压缩，防长对话超窗静默失败
TurnCorrelation                          # 会话 -> 活跃轮次关联（记账归因 turn_id）
runtime.AgentStateStoreWiring            # 官方 MySQL / PostgreSQL AgentStateStore 装配（复用平台共享数据源；PostgreSQL database 配置映射为 schema）
AgentEventCodec                          # AgentScope 事件流 -> domain ChatStreamEvent（防腐层）
TraceIdMiddleware                        # MDC traceId 传播
DynamicPromptMiddleware                  # 当前日期 / 数据集上下文注入
```

职责约束：

- runtime 是全模块唯一允许 import `io.agentscope.*` 的子系统；
- `reactor.core.*` 类型同样只允许出现在 runtime 内部；
- AgentEventCodec 必须穷尽处理事件类型，未知事件显式降级为忽略并记录 debug 日志，不允许静默丢帧后崩溃；
- 模型 Provider 构造（openai/dashscope 等）在此边界内完成，密钥不离开本子系统。

## Toolset Subsystem

```text
list_datasets / get_dataset_fields      -> catalog（格式化）+ gateway.DatasetCatalogGateway
run_dataset_query                       -> catalog.FieldWhitelistValidator -> gateway.DatasetQueryGateway -> query_log
current_date_info                       -> 本地时钟计算
analyze_with_python                     -> gateway.PythonRunnerGateway
request_clarification                       -> HITL 中断信号（由 runtime 机制承接）
save_analysis_report                    -> corridor: report.AgentReportService
```

工具类是薄壳：不持有业务状态、不做格式化决策、不直接访问 repository。

## Catalog Subsystem

```text
DatasetViewFormatter       # 面向 LLM 的目录/字段视图文本化（稳定、可截断）
FieldWhitelistValidator    # datasetId + fieldId 白名单解析，输出结构化校验结果
```

catalog 输出的校验结果是 toolset 与 Gateway 之间的唯一通行证。

## Gateway Boundary

```text
DatasetCatalogGateway   -> dataset 公共契约（目录/字段读取）
DatasetQueryGateway     -> DatasetQueryService.query()（结构化 DSL）
PythonRunnerGateway     -> 本地进程（信号量限流 / 超时强杀 / 临时目录清理）
```

规则：

- `io.yak.ops.business.dataset.*` 类型只允许出现在 gateway 内部签名与实现中；
- gateway 对上暴露的入参出参全部是 domain 值对象；
- Python 执行细节（进程、编码、清理）停在 PythonRunnerGateway。

## Report Subsystem

```text
AgentReportService
   └── (repository contract) 报告保存 / 分页 / 详情 / 删除
```

报告保存由 toolset corridor 触发，HTTP 删除与查询由 controller 直达 Facade。删除会话不级联删除报告。

## Persistence Boundary

```text
Application / internal roles
        ↓
Repository contracts
        ↓
Repository adapters
        ↓
DAO
```

规则：

- Repository contract 不暴露 DAO model / Mapper / Controller DTO；
- DAO 只处理 persistence primitives，不调用 Application / Gateway；
- Core Domain 不依赖 Repository / DAO / Gateway / Spring；
- `yak_agent_query_log.request_json` 与 turn 投递帧等持久化投影 JSON 经专用 codec 写读，该 codec 位于 `repository.support`，不进入 Core Domain。
- 观测采集面边界（架构守护 CollectorBoundaryGuardTest 锁定）：采集统一入口 `runtime.AgentObservationCollector` 仅可被 runtime 中间件与 conversation 执行器触达；业务工具（toolset）禁止 import 任何 telemetry 记录器——工具实现零感知记账。kind 枚举单一真相在 `telemetry.AgentKindRegistry`，启动一致性由注册表准入校验兜底。

## Cross-module Artifacts

权限注册迁移脚本 `V20xx__register_agent_permissions.sql` 存放于 boot 的 `yak-security/db/migration/`，属于平台安全目录数据注册，不属于本模块代码资产；本模块契约变更涉及权限码时必须同步更新该脚本。

## Dependency Governance

`DEPENDENCIES.md` 是 package dependency contract，`AgentDependencyBoundaryTest` 直接扫描 production Java import 并保护：

```text
top-level dependency matrix
acyclic graph
controller -> stable facade corridors
toolset -> report corridor
agentscope / reactor SDK 白名单（仅 runtime）
dataset 类型白名单（仅 gateway）
@Service allowlist
no service/common/helper/utils buckets
```

`AgentArchitectureTest` 继续保护角色、Spring stereotype、read-side、Core Domain purity、Repository contract 等结构语义。

修改依赖白名单前必须先证明真实架构需求，不能为了让测试通过直接扩大 corridor。

## Change Rules

1. 一个 PR 一个主要边界或行为关注点；
2. behavior change 与 package move 尽量分开；
3. 新入口稳定后不保留 production 双入口；
4. 不借重构改 REST / DB / Flyway / Domain semantics；
5. 新 dependency 必须同时符合 `ARCHITECTURE.md + DEPENDENCIES.md`；
6. 代码风格与角色命名遵守 `CODE_STYLE.md`；
7. behavior tests 与 architecture tests 都是长期 contract，不因“迁移完成”删除。


## F-009 只读治理接入

GovernanceEvidenceTools -> GovernanceEvidenceGateway -> AssetGovernanceQueryApi / QualityEvidenceQueryApi 是只读出站走廊，不进入源域实现包。AgentToolExecution 在每个实际工具线程调用 core UserExecutionScope；Boot 适配 Security TrustedUserScope 与 ProjectAccessGuard。GovernanceContextMiddleware 为入口选择读取初始对象；GovernanceAnswerGuard 校验本轮引用、服务端生成回链并更新官方 StateStore 的同一条 final Msg。系统文本只将描述当数据；动态源摘要按字段白名单投影，深度/条数/长度有界。TurnInput JSON 可选治理目标，无数据库迁移，旧输入继续兼容；HITL 恢复继承目标并重新读取授权证据。

## F-010 建议边界

Agent 仅消费源域 api 包的授权只读契约；Quality monitor 的 SuggestionQueryAdapter 进入既有 Reader/Policy 和 Quality-owned Catalog Gateway，DefinitionFingerprint 属于 domain；Asset 条件更新仍在 AssetAppService。候选仅存在本轮上下文与官方消息历史，不新增业务建议表。

治理入口自动预读属于 runtime 横切执行：GovernanceContextMiddleware 经现有 collector 记录上下文工具读取，补足模型没有显式调用该工具时的来源 trace；EffectiveConfigMiddleware 经同一 collector 记录首次推理的非敏感有效配置与提示词/工具哈希。两者是明确登记的 runtime 调用方，不新增工具层采集依赖；保留关闭观测即不落库与采集故障不改变业务结果的契约。

## 任务执行守卫

AgentTaskToolPolicy/ToolBudgetSnapshot 属于 framework-free domain。runtime 的 TaskScopedTool 是无任务状态的 SDK ToolBase 适配器，保留 schema、readOnly/concurrencySafe 与权限回调；实际范围与额度从调用 RuntimeContext 读取。外部 HITL 在预占后仍返回框架 suspended 结果，方法体不执行。注册仅替换适配器，不切换共享 Toolkit 的任务激活状态；当前业务工具无 preset，MCP/新分组工具不在本契约支持范围。

TaskToolPolicyMiddleware 对模型工具视图过滤、输入字符检查和 SDK 动态工具包装；toolset 的 AgentToolExecution 在真实身份线程再次检查任务，预读由 GovernanceContextMiddleware 预占后走同一工具边界。TurnToolBudgetState 在官方 StateStore 上持久化当前轮辅助额度，取消先关闭本执行上下文再 dispose。

RuntimeSkillMiddleware 记录实际 SDK 提示片段哈希；只读技能加载每次读取当前启用仓储，仅返回 SKILL.md，不开放文件资源或触发 SDK 工具组激活。EffectiveConfigMiddleware 沿现有采集入口记录实际调用的工具集合/Skill 哈希/冻结预算，采集故障不改变守卫。

现有 Skill 管理入口 conversation.AgentSkillManageService 及 repository.AgentSkillRepositoryAdapter 是官方 Skill SPI 走廊，与架构守护现行白名单一致；不新增稳定入口。

终态与草稿由原 continuation 组合 turn 输入与必要的原 Runtime.history，只读降级不改源事实。原 SubmitTurn 可选核对 expectedLatestTurnId，在原互斥区完成；无新走廊或持久化。
