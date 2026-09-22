# 数据智能体技术架构梳理（as-is）

> 定位：以代码为准的**技术架构**说明——模块图、分层、核心运行时、语义层（守卫+结构翻译，T5 单执行面）、持久化与 truth、边界与安全、可观测、契约守护。
> 业务流看 `docs/agent/data-agent-business-flow.md`；历史决策与排期看 `data-agent-development-plan.md`；模块级契约看各模块 `ARCHITECTURE.md / DEPENDENCIES.md / REVIEW.md`。

## 0. 总览：三层两通道

```text
┌──────────────────────────── yak-ops-ui（前端）────────────────────────────┐
│  ai-agent（对话/审计/配置/报告） · semantic-console（语义调试台）· ontology 工作台 │
└────────────────────────────────────────────────────────────────────────────┘
                                    │ REST / SSE
┌───────────────────────── yak-ops-boot（装配面）────────────────────────────┐
│  @MapperScan("io.yak.ops") + scanBasePackages；权限/开关条件注解统一装配      │
└────────────────────────────────────────────────────────────────────────────┘
┌────────────────────────── agent（智能体编排面）────────────────────────────┐
│  controller → conversation(3+1 Facade) → runtime(ReAct 洋葱)                │
│  toolset(12 工具) → gateway(唯一跨模块面) → report/repository/telemetry     │
└────────────────────────────────────────────────────────────────────────────┘
┌────────────── semantic（语义层：守卫+结构翻译，T5 单执行面）────┐   ┌──── dataset（数据执行面）────┐
│  SemanticQueryManager → Guard → DatasetAnchoredQueryGateway     │   │  Governor → Coordinator →    │
│  （唯一执行通道：SemanticQuery → DatasetQueryRequest 结构翻译）───│──▶│  Compiler → Adapter → 执行     │
└──────────────────────────────────────────────────────────────┘   └────────────────────────────┘
┌────────────────── ontology（本体 truth 面）─────────────────────────────────┐
│  8 张 yak_onto_* 表 · 建模/发布/变更单 · 能力物化(OntologyCapabilityDeriver) │
└────────────────────────────────────────────────────────────────────────────┘
```

模块关系：`boot` 统一装配；`agent` 经 **gateway** 直调 `semantic`/`dataset`；`semantic` 经 **两个 gateway 边界** 消费 `ontology`（定义）与 `datasource`（执行）及 `dataset`（锚定通道）；`dataset` 内部经 SPI/`SqlExecutionRuntime` 执行。

## 1. agent 模块内部架构

### 1.1 包结构与角色（包即架构，无 service/common/helper/utils 大桶）

```text
io.yak.ops.business.agent
├── controller        # HTTP inbound + SSE + transport mapper（端点契约见 §1.2）
├── conversation      # 会话 command side：编排 / HITL 恢复 / 生命周期 / 配置治理
│   └── query         # 会话列表 / 历史 / trace v2 / 观测矩阵 read model
├── runtime           # AgentScope 边界：ReActAgent 组装 / 中间件 / 事件防腐 / StateStore
├── toolset           # 12 个业务工具（@Tool 薄壳，无状态、零直连数据面）
├── catalog           # 数据集目录视图 + 字段白名单校验（FieldWhitelistValidator）
├── gateway           # 唯一跨模块面：DatasetCatalog/DatasetQuery/Semantic/PythonRunner
├── report            # 报告保存/分页/详情/删除
├── memory            # 长期记忆读写（M1 已接线；M2 巩固管线规划中 WIP）
├── telemetry         # yak_agent_step 唯一写入口 + kind 注册表单一真相
├── repository        # 持久化契约 + 适配器（接口不泄漏 PO/Mapper）
├── dao               # MyBatis-Plus 持久化原语（PO/Mapper）
├── domain            # framework-free 核心域（事件/轮次/值对象）
└── config            # AgentProperties / ConditionalOnAgentEnabled / 动态配置读取
```

### 1.2 稳定入口与 REST 契约（`/api/v1/agent`）

Controller 只依赖 4 个稳定 Facade（`ARCHITECTURE.md` 登记）：`AgentChatService` / `AgentSessionQueryService` / `AgentConfigManageService` / `AgentReportService`。

| 端点 | 权限码 | 说明 |
| --- | --- | --- |
| `POST /chat/turns` | `agent:chat:run` | 提交/恢复合一（`{sessionId,message,toolResults?}`）→ 返回 turnId 立即应答 |
| `GET /chat/turns/{turnId}/events` | 同上 | SSE：`event:/id:/data:` 帧 + `:ping` 心跳，turning cursor 续播 |
| `GET /sessions`、`GET /sessions/{id}/history`、`GET /sessions/{id}/observability` | `agent:session:read` | 会话列表/历史/trace v2/观测矩阵 |
| `GET /turns/{turnId}/trace` | 同上 | trace v2 span 树（LLM_CALL/TOOL_CALL/GUARD/HITL…） |
| `DELETE /sessions/{id}`、`PUT /sessions/{id}`、`POST /sessions/{id}/cancel` | `session:delete/update/chat:run` | 会话生命周期 |
| `GET /config`、`PUT /config/{key}` | `agent:config:read/manage` | 动态配置治理（热生效，≤1s 微过期缓存） |
| `POST /queries/page` | `agent:session:read` | query_log 审计分页 |
| `POST /reports/page`、`GET /reports/{id}`、`DELETE /reports/{id}` | `agent:report:read/delete` | 报告管理（删除会话不级联删报告） |

权限码由 boot 的 `yak-security/db/migration/V2004/V2005/V2012__register_agent_permissions.sql` 注册（跨模块约定：契约变更必须同步该脚本）。

### 1.3 conversation：提交/执行分离 + 轮次状态机

```text
提交线程（HTTP）：归属校验 → stripe 串行化 → hasActiveTurn 单飞(QUEUED/RUNNING/WAITING_INPUT)
                 → 输入投影落库(QUEUED) → kick 调度器 → 返回 turnId【绝不推理】
调度线程：bootstrap 先清孤儿(RUNNING→INTERRUPTED) → @Scheduled(2s) 扫 QUEUED → 批量出队
执行线程(yak-agent-turn-worker)：CAS RUNNING → 驱动推理 → 事件先落 yak_agent_turn_event
                 → 消息树落笔 → 终态收敛 → 单飞释放
```

轮次状态机（`TurnStatus`，全部带前置状态的条件 UPDATE，CAS 单赢家）：

```text
QUEUED ──claim──▶ RUNNING ──complete/fail/cancel──▶ COMPLETED/FAILED/CANCELLED(终态)
   │                │──HITL──▶ WAITING_INPUT ──requeue──▶ QUEUED（防伪造：toolCallId 校验）
   └──cancel──▶ CANCELLED     └──孤儿恢复：启动清障 RUNNING→INTERRUPTED（事实保留，非伪造终态）
```

### 1.4 runtime：ReActAgent 洋葱中间件

- 无状态单例懒组装（双检锁）；`AgentStateStoreWiring` 装配官方 `MysqlAgentStore`（`agentscope_sessions`，复用平台数据源，autoDDL）；
- 模型：OpenAI 协议 `OpenAIChatModel(baseUrl/apiKey/modelName/stream)`（provider 断言仅 openai，dashscope 未引入）；
- 中间件链（注册顺序即执行顺序）：`LlmResilience`（单次硬超时/重试分类/调用级记账 KIND_LLM_CALL）→ `ToolAudit`（工具零侵入 KIND_TOOL_CALL+GUARD 检测）→ `SystemPromptAssembly`（能力域贡献者按序追加，失败静默降级）→ `LongTermMemoryPrompt`（记忆召回注入）→ `Compaction`（框架中间件，条件注册，默认开）；
- `enablePendingToolRecovery(true)`（HITL 孤儿 pending 收敛）+ `maxIters=10`（GenerateOptions 上限）+ `withTurnTimeout`（整轮 `[TURN_TIMEOUT]` 硬闸门）；
- 事件防腐 `AgentEventCodec`：AgentScope 事件流 → 10 种领域 `ChatTurnEvent`，未知事件显式降级忽略（不允许静默丢帧后崩溃）；
- AG-UI 官方化 v2：`runtime/ChatTurnToAguiMapper` 把领域 `ChatTurnEvent` → 官方 `AguiEvent`（threadId=sessionId、runId=turnId、messageId=assistantMessageId；TOOL_RESULT 拆 END+RESULT 双帧；扩展字段经 `rawEvent` 透传），`AgentStreamCoordinator` 用官方 `AguiEventEncoder` 发出 `data:{type...}` 帧 + `id:` 续播扩展头（见 `agent-agui-official-v2-plan.md`）；
- 调用归因 `TurnCorrelation`（会话→活跃轮次→turn_id 记账）：`AgentRuntime.activeTurnByTurnId` 承担 traceId/会话上下文职责（无独立 TraceIdMiddleware）。

### 1.5 toolset（12 工具，薄壳契约）

`@Tool` 薄壳：不持有业务状态、不做格式化决策、不直接访问 repository/数据面；执行细节全部委托 catalog/gateway。能力域路由提示词 `CapabilityRoutingPromptContributor` 指导模型选工具（口径二义→search_concepts；聚合→run_semantic_query；明细→run_dataset_query；实例→get_object_instance；过程→run_function；相对时间→current_date_info）。Python 工具默认关闭（`yak.agent.python.enabled=true` 才注册）。

### 1.6 跨模块边界（唯一 gateway 面）

```text
gateway.DatasetCatalogGateway       → dataset 目录/字段读（OFFLINE 拒绝带 [DATASET_OFFLINE]）
gateway.DatasetQueryGateway         → dataset 结构化查询（白名单前置 + query_log 同边界落账）
gateway.SemanticGateway            → semantic 公共契约（唯一允许 import semantic.* 的文件）
gateway.PythonRunnerGateway         → 本地进程（信号量限流/超时强杀/临时目录清理）
```

`AgentDependencyBoundaryTest` 白名单：`agentscope.*` 仅 runtime（工具注解豁免 toolset）、`reactor.core.*` 仅 runtime/toolset、`dataset.*` 与 `semantic.*` 仅 gateway。**工具与 conversation 均不直连跨模块**。

## 2. 语义层：守卫 + 结构翻译（统一计划 T5 单执行面）

```text
SemanticQueryManager（编排）
  ├─ snapshotFor → DefinitionSnapshot（只含 PUBLISHED，编译期冻结，不回读本体）
  ├─ Guard 前置（唯一统一入口）：存在性/算子白名单/requires/权限（术语歧义属检索入口）
  ├─ requireAnchored（对象必须锚定 dataset_id；未锚定 [NOT_ANCHORED] 显式拒绝）
  └─ gateway/dataset/DatasetAnchoredQueryGateway（唯一执行通道）
       结构翻译：SemanticQuery → DatasetQueryRequest（维度/指标聚合/过滤/时间桶/JOIN/
                  businessFilter/MET-3 对比）→ 下沉 dataset 运行时（唯一 SQL 生产面）
       dry-run 与 execute 共用同一翻译管线（结构投影）；能力未承接显式 [DATASET_CHANNEL_UNSUPPORTED]
  └─ 返回 QueryResult{columns, rows, caliber, compiledSql, elapsedMs, truncated}
```

关键设计点：

- **语义层无第二套 SQL 生产逻辑**：T5 后 `compile/SemanticSqlCompiler`/`JoinPathResolver`/`IdentifierGuard` 已删除（净减 ~1250 行），`compile/` 仅保留共享契约件（`MetricDerivationExpander`、`RowPolicyProvider`/`NoopRowPolicyProvider`——前者供 dataset 通道展开 DERIVED，后者为行级权限 S4 预留拼接点）；
- **Guard 与执行职责分离**：Guard 回答"合法吗"（不拼 SQL），DatasetAnchoredQueryGateway 回答"长什么样"（结构翻译，不做权限决策）；
- **错误即结构化**（DOMAIN S10）：所有拒绝带 `[错误码]` + 人读原因 + solution；`SemanticExceptionHandler` 按 `[CODE]` 前缀映射 404（资源不存在类）；
- **DERIVED 机械展开**（口径链 派生→原子→字段）与 **yoy/mom 对比**（MET-3：当期/上期 CASE 期掩码 + `_yoy` 列，不生成窗口函数）已迁入 dataset 编译器并统一；COMPOSITE 仍拒绝执行；
- 统一计划验收：同一声明式查询迁移前后 golden 快照与 caliber 逐字一致（用 A 的 golden 作为 dataset 侧验收基准），歧义归零。详见 `semantic-unify-plan.md`（T4/T5 ✅ 已交付）。

## 3. dataset 数据执行面

```text
DatasetQueryService.query
  → DatasetQueryGovernor    # 护栏：短 TTL 结果缓存(Caffeine) / 并发信号量([QUERY_CONCURRENCY_LIMIT]) / 审计
  → DatasetQueryCoordinator # ONLINE 检查 → 版本解析 → 分阶段留痕(VALIDATE→RESOLVE_DATASET→…→EXECUTE_SOURCE)
  → DatasetQueryCompiler    # 只读强制(DatasetSqlSafety) → 投影/GROUP BY(聚合算子/时间桶)
                            # WHERE(算子+值字面量转义) → 排序 → LIMIT clamp(上限 1000, 超限截断)
  → DatasetSourceQueryAdapter # SQL_QUERY(默认超时 30s/上限 120s) | QUERY_REVISION(revision 快照)
  → SqlExecutionRuntime(平台执行 SPI，含只读策略与审计)
```

- 算子全集 13 个：`EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/LIKE/NOT_LIKE/BETWEEN/IS_NULL/IS_NOT_NULL`；`IN` 上限 100 值、单值 4000 字符、过滤条件 ≤50；
- 时间桶：`DatasetTimeBucket(field, grain)`，桶字段须 DATE/DATETIME 且不得同时作普通维度，表达式经 `TimeGrainBinder`（core 公共方言件）；
- 每尝试**恰落一条** `yak_dataset_query_performance`（成功/失败都落，SQL 脱敏，失败记 failureStage/errorType）。

## 4. 持久化与 Truth Ownership

| truth | 载体 | owner |
| --- | --- | --- |
| 对话消息/推理状态 | `agentscope_sessions`（官方 StateStore） | runtime 装配 |
| 会话身份/归属/标题 | `yak_agent_session` | conversation |
| 轮次生命周期 | `yak_agent_turn`（状态机 CAS） | conversation |
| 事件投递日志（可重建投影） | `yak_agent_turn_event` | conversation（幂等追加） |
| 报告 | `yak_agent_report` | report |
| 查询证据 | `yak_agent_query_log`（semantic_ref 溯源） | gateway corridor |
| 步骤观测 | `yak_agent_step` | telemetry（唯一写入口） |
| 本体定义 | `yak_onto_*` 八张表 | ontology（只读消费） |
| 数据集/字段/性能 | `yak_dataset_*`（含 query_performance） | dataset |
| 记忆 | `yak_agent_memory` | memory（M1 已接线；M2 规划中） |

分层方向（架构守护强制）：`Controller → Facade → repository contract → adapter → DAO`，接口不泄漏 PO/Mapper/DTO；Core Domain 零 Spring/持久化依赖；`yak_agent_turn_event` 的 JSON 投影经 `repository.support` codec，不进 Core Domain。

## 5. 安全与边界

1. **权限码**：HTTP 面 `RequiresPermission`（agent 8 个 / semantic 2 个 / ontology 2 个，boot 迁移脚本注册）；Bean 直调路径（Agent→semantic）由业务层再次校验；
2. **会话归属**：所有读写入口先 `ensureOwner/assertOwner`（含报告删除/恢复/取消），越权 404/403；
3. **单飞/HITL 防伪**：`hasActiveTurn` 含 WAITING_INPUT；resume 必须匹配反问帧 toolCallId（P1-3 修复后）；
4. **只读双保险**：编译管线只产出 SELECT/DML 拒；执行边界 `DatasetSqlSafety` / `SqlExecutionRuntime` 只读策略统一兜底；函数模板执行前强制只读；
5. **算子/标识符法律**：`allowed_operators` 白名单（Guard 前置 + dataset 通道双保险）；物理标识符白名单迁入 dataset 编译器统一执行（T5 后 `IdentifierGuard` 已随 A 编译器移除）；
6. **Feature flag 可回退**：`yak.agent.python.enabled`（默认关）、`yak.semantic.enabled`（语义工具开关）、`yak.dataset.query.enabled`、`ConditionalOn*Enabled` 模块级条件装配；
7. **执行面防护**：Python 默认关/信号量/超时强杀/临时目录清理；数据集查询 OFFLINE 拒绝；LLM 密钥只存在于 runtime 装配边界，不落日志/SSE/异常。

## 6. 可观测性架构

```text
采集面（单一入口 runtime.AgentObservationCollector，仅 runtime 中间件与 conversation 执行器可触达）
  ├─ llmCallAttempt（调用级：summaryHash 信封，排障可 INLINE 升档）
  ├─ toolCall（入参/输出 INLINE）
  ├─ guardRejected（status=REJECTED）
  ├─ completedEvent（TURN_SUMMARY：tokens/步骤统计）
  └─ event（HITL / MEMORY_RECALL / MEMORY_FLUSH / GUARD 通用 span）
落库面（telemetry.AgentStepRecorder，唯一写入口 yak_agent_step）
  ├─ kind 单一真相：AgentKindRegistry（9 kind，注册即准入，未注册拒绝并告警）
  ├─ 父链规则 LAST_LLM：工具 span 挂在最近一次 LLM_CALL 下（证据链重建基础）
  ├─ 载荷档位 INLINE/NONE/SUMMARY_HASH（PayloadPolicy）
  └─ 动态开关：yak.agent.observability.enabled + per-kind 覆盖（1s 微过期现读）
trace v2（conversation.query.TraceViewAssembler）：step 表 → span 树（含 iter/errorCode/attempt/stats）
```

每次回答的完整证据链：`yak_agent_query_log`（查询证据）↔ `yak_agent_step`（推理步骤）↔ `yak_agent_turn_event`（可重建事件流）↔ `yak_dataset_query_performance`（执行性能）。

## 7. 契约守护与测试体系

- 架构测试：`AgentDependencyBoundaryTest`（矩阵/无环/SDK 白名单/@Service allowlist/禁大桶）、`AgentArchitectureTest`（角色/读侧/Core Domain 纯度）、`SemanticDependencyBoundaryTest`（矩阵/无环/外部边界 dataset+core 扫描）、`SemanticLayeringConventionTest`；
- 行为测试：会话提交/恢复（含 toolCallId 校验）、HITL 状态机、轮次执行收敛、记忆线、观测矩阵、语义 Guard/编译快照（golden）、dataset 网关（成功/失败/REJECTED 三路留痕）；
- 契约文档即测试的输入：每个模块 `ARCHITECTURE/DEPENDENCIES/DOMAIN/REQUIREMENTS/REVIEW` 与可执行测试同步维护（改动必须同 PR 更新文档+测试）。

## 8. 已知技术债（当前 as-is 状态下不影响正确性的项）

- **memory M2**：consolidation 管线（合并/固化/归档）repository 已扩展但零调用者，`KIND_MEMORY_CONSOLIDATE` 无生产者——登记为规划中 WIP（`dea24fb5c`）；
- **上帝类**：`DatasetAnchoredQueryGateway`（~560 行）与 `AgentRuntime`（~500 行）职责过载（文档已预留 MetricExpander 拆分点）；
- **字符串级错误契约**：`[LLM_CALL_TIMEOUT]`/`[GUARD_REJECTED]` 等魔法字符串跨层 contains 判定散落（review 建议收敛为常量）；
- **两套 trace 重建**：事件帧回放（`AgentTurnEventRepositoryAdapter.buildTrace`）与 step 表 trace v2 口径不同源（review P2）；
- **消息树**：`yak_agent_message` 存储与 read 接口存在，前端未消费（线性 ThoughtChain 渲染），无 REST 暴露；
- **行级权限**：`RowPolicyProvider` 为 Noop 拼接点（S4 公理预留，三期填真实实现），dataset 通道对非空谓词显式拒绝；
- **COMPOSITE 指标**：仅登记可查 schema，执行端拒绝（`COMPOSITE_NOT_EXECUTABLE`），公式执行四期。

（本文档是 as-is 梳理，技术细节与代码为准；排期/北极星度量看 `data-agent-development-plan.md`。）