# AgentScope-Java 框架蒸馏与 yak-ops Agent 模块审计报告

> **审计时间**：2026-08-27  
> **框架版本**：agentscope-java v2.0.2  
> **审计范围**：`yak-ops-business/yak-ops-business-agent` 全部 82 个 Java 源文件 vs `agentscope-java` 全模块

---

## 目录

- [一、审计背景与方法](#一审计背景与方法)
- [二、框架能力全景](#二框架能力全景)
- [三、问题一：是否使用了框架已有能力而非重复造轮子](#三问题一是否使用了框架已有能力而非重复造轮子)
- [四、问题二：是否存在过度设计或使用没必要功能](#四问题二是否存在过度设计或使用没必要功能)
- [五、问题三：有哪些重要框架特性未在项目中应用](#五问题三有哪些重要框架特性未在项目中应用)
- [六、各层逐文件审计明细](#六各层逐文件审计明细)
- [七、行动路径与优先级](#七行动路径与优先级)
- [八、风险与约束](#八风险与约束)

---

## 一、审计背景与方法

yak-ops 的 agent 模块基于 `agentscope-java` 框架实现，当前 pom.xml 仅引入了框架的 **core** 和 **extensions-model-openai** 两个模块，未引入 **harness**（网关/中间件/通道）、**extensions-spring-boot-starters**（自动装配）、**extensions-agui**（AG-UI 协议）等模块。

审计方法：
1. 逐文件阅读 yak-ops agent 模块全部 82 个 Java 源文件
2. 逐模块阅读 agentscope-java 的 core / harness / extensions 关键类
3. 将每个自建类与框架等价能力做功能级对比
4. 按"重复造轮子 / 过度设计 / 遗漏特性"三维度分类输出

---

## 二、框架能力全景

### 2.1 模块职责

| 模块 | 职责 | yak-ops 是否引入 |
|------|------|-----------------|
| `agentscope-core` | Agent/ReActAgent、Toolkit、Msg/ContentBlock、Middleware（5 拦截点）、AgentStateStore、PermissionEngine、SkillBox | ✅ 已引入 |
| `agentscope-harness` | HarnessGateway（单飞+通道+子Agent路由）、SessionTurnGate、ChannelManager、9 个 HarnessRuntimeMiddleware（Compaction/Transcript/Workspace/Inbox/Subagents/Teams/PlanMode/AgentTrace/MemoryFlush）、MessageBus、WorkspaceManager、TranscriptStore | ❌ 未引入 |
| `agentscope-extensions-model-openai` | OpenAI 协议模型适配 | ✅ 已引入 |
| `agentscope-extensions-mysql` | MySQL AgentStateStore 适配 | ✅ 已引入 |
| `agentscope-openai-spring-boot-starter` | OpenAI 模型自动装配 + 配置属性 + 条件装配 | ❌ 未引入 |
| `agentscope-agui-spring-boot-starter` | AG-UI 协议 SSE 对话接口 + 会话管理 | ❌ 未引入 |
| `agentscope-admin-spring-boot-starter` | 会话管理、快照、压缩、指标 | ❌ 未引入 |
| `agentscope-dashscope-spring-boot-starter` | DashScope（阿里云通义）模型自动装配 | ❌ 未引入 |
| `agentscope-ollama-spring-boot-starter` | Ollama 本地模型自动装配 | ❌ 未引入 |

### 2.2 框架关键能力速查

```
┌─ Core ─────────────────────────────────────────────────────────────┐
│ ReActAgent          — ReAct 循环 + Middleware 链 + StateStore     │
│ MiddlewareBase       — 5 拦截点（onAgent/onReasoning/onActing/    │
│                        onModelCall/onSystemPrompt）               │
│ Toolkit              — @Tool + ReflectiveFunctionTool 声明式注册   │
│ AgentStateStore      — SPI 可插拔状态持久化 + 乐观并发版本         │
│ PermissionEngine     — DENY→ASK→ALLOW→BYPASS 工具级权限           │
│ SkillBox             — 运行时动态技能激活/去激活                    │
└────────────────────────────────────────────────────────────────────┘
┌─ Harness ──────────────────────────────────────────────────────────┐
│ HarnessGateway       — 单飞闸门 + 通道管理 + 子Agent路由 + 唤醒   │
│ SessionTurnGate      — Per-session 互斥（LocalSemaphore / 分布式） │
│ ChannelManager       — SSE/WebSocket 出站推送                       │
│ CompactionMiddleware — LLM 驱动对话压缩（防超窗口）                 │
│ TranscriptMiddleware — JSONL 会话转写（审计回放）                   │
│ WorkspaceCtxMw       — AGENTS.md/MEMORY.md/knowledge 注入提示词   │
│ InboxMiddleware      — 消息总线收件箱 → 自动注入推理上下文         │
│ AgentTraceMiddleware — 推理步骤日志（PRE/POST 推理 + 工具调用）    │
│ MemoryFlushMiddleware— 对话结束时自动提取记忆                       │
└────────────────────────────────────────────────────────────────────┘
```

---

## 三、问题一：是否使用了框架已有能力而非重复造轮子

### 3.1 确认正确使用的框架能力

| # | 使用点 | 框架能力 | 评价 |
|---|--------|---------|------|
| 1 | `ReActAgent.builder()...build()` + `streamEvents()` | ReActAgent 核心 | ✅ 正确使用 |
| 2 | `Toolkit.registerTool()` + `@Tool` / `@ToolParam` | 声明式工具注册 | ✅ 正确使用 |
| 3 | `MysqlAgentStateStore` + `AgentStateStoreWiring` | StateStore SPI | ✅ 正确使用 |
| 4 | `RequestClarificationTool` 的 `externalTool=true` | HITL 挂起机制 | ✅ 正确使用 |
| 5 | `RuntimeContext.builder().userId().sessionId()` | 身份传播 | ✅ 正确使用 |
| 6 | `AgentEventCodec` 映射 28 种 `AgentEvent` | 事件体系消费 | ✅ 正确使用（防腐层） |

### 3.2 确认重复造轮子的自建实现

#### 🔴 高度重复（框架已有等价能力，应替换）

##### 1. AgentInvocationManager ↔ SessionTurnGate

| 维度 | 自建 `AgentInvocationManager` | 框架 `SessionTurnGate` + `LocalSessionTurnGate` |
|------|------------------------------|-----------------------------------------------|
| **核心语义** | `ConcurrentHashMap<sessionId, expireAt>` 做单飞互斥 | `acquire(key)` → `TurnLease.close()` 做单飞互斥 |
| **释放保证** | TTL 15min 兜底防泄漏（异常路径可能泄漏） | `doFinally` 保证 lease 一定释放，无泄漏可能 |
| **Busy 策略** | 抛异常"正在推理中" | 抛 `TurnBusyException`，语义一致 |
| **代码量** | 81 行（含归属校验） | 框架内建，接入约 10 行 |

**结论**：核心互斥语义完全重复，框架实现更健壮。归属校验（`ensureOwner`/`assertOwner`）是 yak-ops 特有业务逻辑，可保留但与互斥锁解耦。

##### 2. AgentTurnRegistry ↔ withGatedStream 返回的 Flux

| 维度 | 自建 `AgentTurnRegistry` | 框架 `HarnessGateway.withGatedStream()` |
|------|--------------------------|----------------------------------------|
| **核心语义** | `ConcurrentHashMap<turnId, Handle>` 登记 dispose 句柄 + cancel 回调 | `Flux` 本身即 `Disposable`，`doFinally` 自动释放 TurnLease |
| **Cancel 语义** | `cancelBySession()` → dispose + cancelFinalizer | `Flux.doOnCancel()` 可注册取消回调 |
| **代码量** | 61 行 | 框架内建，接入约 5 行 |

**结论**：手动维护的运行中轮次登记表完全重复，响应式流自带 cancel 语义。

##### 3. AgentSystemPromptContributor ↔ onSystemPrompt Middleware

| 维度 | 自建 `AgentSystemPromptContributor` + `appendContributions()` | 框架 `MiddlewareBase.onSystemPrompt()` |
|------|------------------------------------------------------------|--------------------------------------|
| **核心语义** | 遍历 `List<Contributor>` 拼接段落 | `onSystemPrompt(agent, ctx, currentPrompt) → Mono<String>` 管道 |
| **排序** | 无排序（按 Spring 注入顺序） | 按 `order()` 排序，可精确控制 |
| **异步** | 同步 `contribute()` | 返回 `Mono<String>`，可做异步 I/O |
| **代码量** | 接口 12 行 + 拼接 15 行 + 2 个实现 | 框架内建，每个 Contributor 改为 Middleware |

**结论**：提示词拼接管道完全重复，框架 Middleware 有排序和异步优势。

#### 🟡 部分重复（框架有等价能力，但 yak-ops 有架构差异需保留部分）

##### 4. AgentStepRecorder ↔ Middleware（onModelCall / onActing）

| 维度 | 自建 `AgentStepRecorder` | 框架 `MiddlewareBase` 的拦截点 |
|------|--------------------------|-------------------------------|
| **LLM 调用记录** | `GatedChatModel` 通过 Reactor Context 传 `LlmCallObserver` → 手动回调 `stepRecorder.recordLlmCall()` | `onModelCall(agent, ctx, input, next)` 可拦截原始模型调用，零侵入 |
| **工具调用记录** | 在 `RunDatasetQueryTool` / `RunSemanticQueryTool` 内部手动调用 `stepRecorder.record()` | `onActing(agent, ctx, input, next)` 可拦截所有工具执行，零侵入 |
| **持久化** | 写入 `yak_agent_step` 表 | 框架 `AgentTraceMiddleware` 只做日志，需自定义 Middleware 做持久化 |

**结论**：记账的"拦截方式"是重复的——框架 Middleware 已提供零侵入拦截点，但"持久化到 yak_agent_step 表"是 yak-ops 特有需求。**应将侵入式调用改为 Middleware 拦截，但保留 step 表写入逻辑**。

##### 5. GatedChatModel ↔ onModelCall Middleware

| 维度 | 自建 `GatedChatModel` | 框架 `onModelCall` Middleware |
|------|----------------------|-------------------------------|
| **超时** | `delegate.stream().timeout(callTimeout)` | `next.apply(input).timeout(...)` 在 Middleware 内 |
| **重试** | `retryWhen(Retry.max(maxRetries).transientErrors(true).filter(this::retryable))` | 同理 |
| **错误分类** | `errorCode()` / `translate()` | 同理 |
| **计量** | Reactor Context 传 `LlmCallObserver` | `doOnComplete`/`doOnError` 在 Middleware 内 |
| **模型代理** | `implements Model`，包一层 delegate | 不需要代理，Middleware 在调用链上 |

**结论**：GatedChatModel 的 timeout/retry/observer 逻辑完全可以用 `onModelCall` Middleware 替代，且消除了"包一层 Model 代理"的复杂度。**但 GatedChatModel 的错误分类常量和翻译逻辑是高质量的自建代码，应抽取为 Middleware 的内部实现**。

##### 6. AgentStreamCoordinator ↔ ChannelManager + ChatUiChannel

| 维度 | 自建 `AgentStreamCoordinator` | 框架 `ChannelManager` + `ChatUiChannel` |
|------|------------------------------|----------------------------------------|
| **心跳** | `ScheduledExecutorService` 3s 发 ping | `ChatUiChannel` 内建心跳 |
| **断连检测** | `onCompletion/onTimeout/onError` → `SseDisconnectedException` | `Channel` 的 `deliver` 异常处理 |
| **发布** | `SseEmitter.event().name().data()` | `Channel.deliver(address, messages)` |

**结论**：SSE 生命周期管理的逻辑重复。但 yak-ops 采用"投递日志 + 轮询拉取"架构（事件先落 DB 再被 `AgentEventStreamTailer` 轮询），框架的 Channel 是纯推模式。**如果保留投递日志架构，只能用 ChannelManager 替代心跳/断连部分；如果切换为推模式，可全量替代**。

---

## 四、问题二：是否存在过度设计或使用没必要功能

### 4.1 🔴 提交/执行分离状态机（8 类 + 7 表）

**现状**：为"提交后立即返回 turnId"构建了完整的状态机：

```
AgentTurnDispatcher（调度器）
  ├── AgentTurnExecutor（执行器）
  ├── AgentTurnRepository（7 种状态转移）
  ├── AgentTurnEventRepository（事件投递日志）
  ├── AgentInvocationManager（单飞锁）
  ├── AgentTurnRegistry（运行中登记）
  ├── AgentStreamCoordinator（SSE 通道）
  └── AgentEventStreamTailer（尾随拉取）
```

配套 7 张 DB 表：`yak_agent_turn`、`yak_agent_turn_event`、`yak_agent_message`、`yak_agent_step`、`yak_agent_session`、`yak_agent_query_log`、`yak_agent_report`。

**评估**：

| 场景 | 是否需要完整状态机 |
|------|-------------------|
| 单节点、2 worker | **不需要**。框架 `HarnessGateway.withGatedStream()` + Reactor 的 `subscribeOn(boundedElastic)` 已提供异步执行 + 单飞互斥 |
| 多节点分布式调度 | **需要**。DB 行队列 + CAS 认领 是正确的分布式方案 |
| 崩溃续播 | **需要**。QUEUED/RUNNING 状态落库保证重启后可恢复 |

**结论**：当前规模下过度设计，但**如果产品路线图包含多节点部署和崩溃续播**，则当前方案有前瞻价值。建议：保留 DB 持久化方案，但**用 `SessionTurnGate` 替代内存锁**，用 **Middleware 替代侵入式记账**，逐步收敛与框架的重复。

### 4.2 🟡 投递日志表 `yak_agent_turn_event`（每帧 1 次 DB 写入）

**现状**：每个 SSE 帧先 `INSERT` 到 `yak_agent_turn_event`，再由 `AgentEventStreamTailer` 200ms 轮询拉取。

**代价**：
- 每帧 1 次 DB 写入（一轮 10 次推理 × 平均 20 帧 ≈ 200 次 INSERT/轮）
- 200ms 轮询延迟（用户感知到的响应延迟）
- 额外的 Mapper / PO / Codec 代码

**价值**：
- 崩溃一致性：进程重启后订阅端仍可从 DB 续播
- Last-Event-ID 断线续播：客户端断线后可增量补发

**替代方案**：框架 `ChatUiChannel` 纯推模式，延迟从 200ms 降到实时，但失去崩溃续播。

**结论**：如果"崩溃续播"是产品刚需，保留投递日志；否则过度设计。**建议先评估前端对断线续播的实际需求**——如果用户都是局域网内使用，网络断线概率极低，推模式更优。

### 4.3 🟢 消息树表 `yak_agent_message`（第二份对话历史）

**现状**：`MessageTreeRepository` 维护 USER/ASSISTANT 节点的树形结构。

**评估**：框架的 `AgentStateStore` 已持久化完整 `AgentState.context`（含所有 Msg），`AgentRuntime.history()` 已经从 StateStore 重建对话历史。消息树表是**第二份**对话历史存储。

**结论**：轻度过重。如果前端只需要轮次粒度的问答历史，`history()` 完全够用。消息树的价值在于"分支切换导航"（如 regenerate 产生的多条分支），但当前前端未实现此功能。**建议暂缓消息树扩展，待前端有明确需求时再投入**。

### 4.4 ✅ 合理但需区分层次的功能

| 功能 | 评价 |
|------|------|
| `AgentPermissionCode` + `@RequiresPermission` | ✅ 接口级权限是业务需要，与框架 `PermissionEngine`（工具级权限）是不同层次，**不要试图用 PermissionEngine 做接口鉴权** |
| `GatedChatModel` 的错误分类常量 | ✅ 高质量的错误分类（TIMEOUT/USER_ERROR/PROVIDER_ERROR），应保留但移入 Middleware |
| `AgentEventCodec` | ✅ DDD 防腐层，合理自建 |
| `ChatTurnEvent` 领域事件 | ✅ 领域模型，合理自建 |
| 业务工具集（list_datasets / run_dataset_query 等） | ✅ 业务领域工具，框架无等价物 |

---

## 五、问题三：有哪些重要框架特性未在项目中应用

### 5.1 🔴 必须引入（缺失 = 缺陷）

#### CompactionMiddleware — LLM 驱动的对话压缩

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-harness` → `CompactionMiddleware` |
| **功能** | 当对话 token 超过阈值时，自动用 LLM 压缩历史消息，保留关键信息，释放上下文空间 |
| **当前风险** | yak-ops 的 `maxIters=10` 限制可防无限循环，但**无法防止单轮对话累积的上下文超窗口**。长会话（10+ 轮问答 + 工具调用结果）的 context 会自然超过 128k token，此时模型调用将失败或截断，**用户看到的是静默失败** |
| **引入难度** | 低。加依赖 + 创建 Middleware 实例 + 注册到 ReActAgent |
| **配置** | `CompactionConfig` 支持动态触发阈值（从模型 `contextWindowSize` 自动推算）、保留 token 数、reserved 预留 |

**为什么这是必上项**：这是生产环境的基本健壮性要求。没有 Compaction，长对话必然失败，且错误信息对用户不可操作。

### 5.2 🟡 强烈推荐引入（显著提升代码质量）

#### Middleware 洋葱模型 — 统一横切关注点

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-core` → `MiddlewareBase` |
| **5 个拦截点** | `onAgent`（整个 Agent 调用）、`onReasoning`（推理阶段）、`onActing`（工具执行）、`onModelCall`（原始模型 API 调用）、`onSystemPrompt`（系统提示词管道） |
| **当前问题** | AgentRuntime.doAssemble() 手动拼接 systemPrompt、手动在工具内部调用 stepRecorder、手动用 GatedChatModel 包一层 Model 做重试/超时/计量。这些全是横切关注点，散落在多个类里，无法独立扩展 |
| **引入收益** | (1) GatedChatModel 的 timeout/retry/observer 移入 `onModelCall` Middleware；(2) `AgentSystemPromptContributor` 移入 `onSystemPrompt` Middleware；(3) `AgentStepRecorder` 的工具记账移入 `onActing` Middleware；(4) CompactionMiddleware 注册到 `onReasoning` |

**引入后的 AgentRuntime.doAssemble() 将从手动组装变为声明式链**：

```java
ReActAgent.builder()
    .name("YakOpsAgent")
    .model(openAiModel())  // 不再需要 GatedChatModel 包装
    .toolkit(toolkit)
    .stateStore(stateStore)
    .middleware(new CompactionMiddleware(workspaceManager, model, compactionConfig))
    .middleware(new LlmCallMetricsMiddleware(stepMapper))     // 替代 GatedChatModel 的 observer
    .middleware(new LlmRetryTimeoutMiddleware(timeout, maxRetries)) // 替代 GatedChatModel 的重试
    .middleware(new ToolCallAuditMiddleware(stepMapper))      // 替代侵入式 stepRecorder.record()
    .middleware(new SystemPromptAssemblyMiddleware(contributors)) // 替代 appendContributions()
    .build();
```

#### agentscope-openai-spring-boot-starter — 自动装配 OpenAI 模型

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-extensions` → `agentscope-openai-spring-boot-starter` |
| **功能** | `agentscope.openai.*` 配置属性自动装配 OpenAI 模型，条件装配，`ChatModelBuilderCustomizer` 扩展点 |
| **当前问题** | `AgentRuntime.openAiModel()` 手动 `OpenAIChatModel.builder()...build()`，provider 硬编码只支持 openai，apiKey 明文配置 |
| **引入收益** | (1) 消除手动组装；(2) 获得多 provider 支持（DashScope/Ollama/Gemini/Anthropic 均有 starter）；(3) 配置属性统一管理 |

#### SessionTurnGate — 替代 AgentInvocationManager

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-harness` → `SessionTurnGate` / `LocalSessionTurnGate` |
| **功能** | Per-key 互斥锁，`acquire(key)` → `TurnLease.close()`，`isRunning(key)` 查询 |
| **当前问题** | `AgentInvocationManager` 用 `ConcurrentHashMap<sessionId, expireAt>` + TTL 15min 兜底，有泄漏风险 |
| **引入收益** | (1) 消除 TTL 漏洞；(2) 消除重复代码；(3) 未来可无缝切换为分布式 TurnGate |

### 5.3 🟢 推荐引入（增强功能与体验）

#### TranscriptMiddleware — 会话转写持久化

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-harness` → `TranscriptMiddleware` |
| **功能** | 每轮推理结束时自动将完整对话（含工具调用细节）追加写入 JSONL 文件 |
| **当前缺失** | `AgentStepRecorder` 只记录步骤摘要（请求/响应截断到 8000 字符），不能还原完整对话。TranscriptMiddleware 是零代码成本的补充 |
| **用途** | 审计回放、调试排查、训练数据采集 |

#### PermissionEngine — 工具级权限引擎

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-core` → `PermissionEngine` |
| **功能** | DENY→ASK→ALLOW→BYPASS 优先链，支持运行时动态添加规则 |
| **当前风险** | yak-ops 没有工具级权限控制——**模型可以调用任何已注册工具，包括执行 Python 代码**（`analyze_with_python`）。PermissionEngine 可做：(1) 默认 ALLOW 所有读工具；(2) `analyze_with_python` 设 ASK（需用户确认）；(3) `run_dataset_query` 对敏感数据集设 DENY |

#### SkillBox — 动态技能注册/激活/去激活

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-core` → `SkillBox` |
| **功能** | 运行时动态激活/去激活一组工具 |
| **当前问题** | 工具是静态全量注册，无法按场景裁剪。工具过多会稀释模型推理质量（增加选错工具的概率） |
| **应用场景** | "数据分析模式"激活 query+python，"报告模式"只激活 report，"语义模式"激活 semantic 工具集 |

#### agentscope-agui-spring-boot-starter — AG-UI 协议

| 项 | 说明 |
|----|------|
| **框架位置** | `agentscope-extensions` → `agentscope-agui-spring-boot-starter` |
| **功能** | AG-UI 协议的 SSE 对话接口，内置线程管理、断连处理、session 绑定 |
| **当前状态** | yak-ops 使用私有 SSE 协议（`ChatTurnEvent` 序列化为 SSE event），与 AG-UI 生态不兼容 |
| **远期价值** | 如果前端想对接 CopilotKit 等 AG-UI 生态客户端，直接引入即可 |

---

## 六、各层逐文件审计明细

### 6.1 conversation 层（对话编排）

| 文件 | 行数 | 性质 | 框架对应 | 审计结论 |
|------|------|------|---------|---------|
| `AgentInvocationManager` | 81 | 单飞锁 + 归属校验 | `SessionTurnGate` | 🔴 互斥锁重复，归属校验保留 |
| `AgentTurnRegistry` | 61 | 运行中轮次登记 | `withGatedStream` 的 Flux | 🔴 重复 |
| `AgentTurnDispatcher` | 91 | QUEUED 队列调度 | 框架无等价（DB 队列是 yak-ops 特有） | 🟡 保留（如果保留提交/执行分离） |
| `AgentTurnExecutor` | 245 | 轮次执行器 | 框架无等价（状态机驱动是 yak-ops 特有） | 🟡 保留（如果保留提交/执行分离） |
| `AgentStreamCoordinator` | 118 | SSE 生命周期 | `ChannelManager` + `ChatUiChannel` | 🟡 心跳/断连重复，投递日志架构保留 |
| `AgentEventStreamTailer` | 97 | 轮询补帧 | 框架无等价（投递日志拉取是 yak-ops 特有） | 🟡 保留（如果保留投递日志） |
| `AgentChatService` | 149 | 对话 Facade | 框架无等价（业务编排层） | ✅ 保留 |
| `AgentSessionQueryService` | — | 只读查询 | 框架无等价 | ✅ 保留 |

### 6.2 runtime 层（Agent 运行时）

| 文件 | 行数 | 性质 | 框架对应 | 审计结论 |
|------|------|------|---------|---------|
| `AgentRuntime` | 387 | Agent 组装 + 事件流映射 | `ReActAgent` + Middleware 链 | 🟡 组装方式应改为 Middleware 声明式 |
| `GatedChatModel` | 206 | 模型调用闸门 | `onModelCall` Middleware | 🟡 timeout/retry/observer 应移入 Middleware |
| `AgentEventCodec` | 103 | 事件防腐映射 | 无等价 | ✅ DDD 防腐层，合理自建 |
| `AgentStateStoreWiring` | — | StateStore 接线 | 框架 SPI | ✅ 正确使用 |
| `LlmCallObserver` | — | 计量观察接口 | `onModelCall` Middleware | 🟡 应移入 Middleware |
| `TurnSubscription` | — | 取消句柄 | `Disposable` | ✅ 薄封装 |

### 6.3 telemetry 层（可观测性）

| 文件 | 行数 | 性质 | 框架对应 | 审计结论 |
|------|------|------|---------|---------|
| `AgentStepRecorder` | 217 | 步骤级记录 | `onModelCall`/`onActing` Middleware | 🟡 拦截方式重复，持久化逻辑保留 |

### 6.4 toolset 层（业务工具）

| 文件 | 行数 | 性质 | 框架对应 | 审计结论 |
|------|------|------|---------|---------|
| `AgentToolBox` | — | 标记接口 | 无等价 | ✅ 保留 |
| `AgentSystemPromptContributor` | 12 | 提示词贡献 | `onSystemPrompt` Middleware | 🔴 重复 |
| `SemanticPromptContributor` | 40 | 语义域提示词 | `onSystemPrompt` Middleware | 🔴 重复 |
| `RequestClarificationTool` | 40 | HITL 反问 | `externalTool=true` | ✅ 正确使用 |
| `RunDatasetQueryTool` | 114 | 结构化取数 | 无等价 | ✅ 业务工具 |
| `RunSemanticQueryTool` | 130 | 语义取数 | 无等价 | ✅ 业务工具 |
| `SaveAnalysisReportTool` | 50 | 报告沉淀 | 无等价 | ✅ 业务工具 |
| `AnalyzeWithPythonTool` | 41 | Python 分析 | 无等价 | ✅ 业务工具 |
| 其余工具 | — | 领域工具 | 无等价 | ✅ 业务工具 |

### 6.5 domain / repository / controller 层

| 分类 | 文件数 | 性质 | 审计结论 |
|------|--------|------|---------|
| domain（ChatTurnEvent 等） | 11 | 领域模型 | ✅ 合理自建 |
| repository（接口 + Adapter） | 10 | 持久化契约 | 🟡 部分可与框架 TranscriptMiddleware 互补 |
| dao（Mapper + PO） | 14 | MyBatis 数据层 | ✅ 合理自建 |
| controller + dto + vo | 5 | API 层 | ✅ 合理自建 |
| config | 5 | Spring 配置 | ✅ 合理自建 |
| gateway | 5 | 外部网关 | ✅ 合理自建（业务域边界） |

---

## 七、行动路径与优先级

### Phase 0：必做（缺失 = 缺陷）

| # | 动作 | 工作量 | 收益 |
|---|------|--------|------|
| 1 | 引入 `agentscope-harness` 依赖 | 0.5 PD | 解锁 Middleware 全家桶 |
| 2 | 给 ReActAgent 注册 `CompactionMiddleware` | 1 PD | **修复长对话崩溃**——当前缺失，生产环境会静默失败 |
| 3 | 用 `onSystemPrompt` Middleware 替代 `AgentSystemPromptContributor` + `SemanticPromptContributor` + `appendContributions()` | 1 PD | 统一提示词管道，获得排序和异步能力 |

### Phase 1：强推（消除技术债）

| # | 动作 | 工作量 | 收益 |
|---|------|--------|------|
| 4 | 用 `onModelCall` Middleware 替代 `GatedChatModel`（保留 timeout/retry/observer 逻辑，移入 Middleware） | 2 PD | **消除最大侵入式横切**，AgentRuntime 不再手动包装 Model |
| 5 | 用 `onActing` Middleware 替代 `AgentStepRecorder` 的侵入式调用（从 `RunDatasetQueryTool` / `RunSemanticQueryTool` 内部移出） | 1.5 PD | 工具代码零侵入记账 |
| 6 | 用 `SessionTurnGate` 替代 `AgentInvocationManager` 的互斥锁部分（保留归属校验） | 1 PD | 消除 TTL 漏洞 + 消除重复 |
| 7 | 引入 `agentscope-openai-spring-boot-starter` 替代手动模型组装 | 1 PD | 简化配置 + 多 provider 支持 |

### Phase 2：推荐（增强功能）

| # | 动作 | 工作量 | 收益 |
|---|------|--------|------|
| 8 | 注册 `TranscriptMiddleware`，获得 JSONL 会话转写 | 0.5 PD | 审计回放 + 调试排查 |
| 9 | 引入 `PermissionEngine`，对 `analyze_with_python` 等危险工具设 ASK/DENY | 1 PD | 安全增强 |
| 10 | 引入 `SkillBox`，按场景动态裁剪工具集 | 1 PD | 提升模型推理精度 |
| 11 | 评估 `agentscope-agui-spring-boot-starter`，对接 AG-UI 协议生态 | 2 PD | 开放协议兼容 |

### Phase 3：远期（架构优化）

| # | 动作 | 工作量 | 收益 |
|---|------|--------|------|
| 12 | 评估投递日志 vs 框架推模式，决定是否用 `ChatUiChannel` 替代 `AgentEventStreamTailer` | 3 PD | 延迟从 200ms 降到实时，或保留崩溃续播 |
| 13 | 评估 `HarnessGateway` 全量替代 `AgentTurnDispatcher` + `AgentTurnExecutor` | 5 PD | 如果不需要 DB 队列调度，可大幅简化 |
| 14 | 消息树表的去留决策 | 1 PD | 避免第二份对话历史的维护成本 |

---

## 八、风险与约束

### 8.1 引入 harness 依赖的风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| harness 传递依赖引入不兼容版本 | 编译/运行时错误 | 在 yak-ops BOM 中显式声明版本覆盖 |
| CompactionMiddleware 依赖 WorkspaceManager | 需要配置 workspace 路径 | 使用内存 Workspace 或最小化配置 |
| Middleware 链的执行顺序影响行为 | 行为变化 | 按 `order()` 精确控制，加集成测试验证 |

### 8.2 保留 yak-ops 自建的理由

| 自建 | 保留理由 |
|------|---------|
| `AgentEventCodec` + `ChatTurnEvent` | DDD 防腐层，隔离框架事件与领域事件 |
| `GatedChatModel` 的错误分类常量和翻译逻辑 | 高质量的错误分类，移入 Middleware 时保留实现 |
| 提交/执行分离的 DB 队列 | 如果产品路线图含多节点部署和崩溃续播 |
| 投递日志表 | 如果"断线续播"是产品刚需 |
| 归属校验（ensureOwner/assertOwner） | yak-ops 特有业务逻辑 |

### 8.3 不应引入的框架能力

| 框架能力 | 不引入理由 |
|---------|-----------|
| WorkspaceContextMiddleware（AGENTS.md/MEMORY.md/knowledge） | yak-ops 是数据分析场景，不需要代码助手的工作区概念 |
| InboxMiddleware + MessageBus | yak-ops 没有异步工具/子 Agent 协作场景 |
| SubagentsMiddleware / TeamsMiddleware | yak-ops 是单 Agent 架构，不需要多 Agent 协作 |
| PlanModeMiddleware | yak-ops 的数据分析不需要计划模式 |
| MemoryFlushMiddleware / MemoryMaintenanceMiddleware | yak-ops 不需要跨会话记忆持久化 |
| agentscope-a2a-spring-boot-starter | yak-ops 不需要 Agent-to-Agent 协议 |

---

> **总结**：yak-ops agent 模块对框架 core 层的使用是正确的，但未引入 harness 层导致了 3 个高重复和 2 个部分重复的自建实现。**CompactionMiddleware 的缺失是当前最大的生产风险**（长对话会静默失败），Middleware 洋葱模型的缺失是最大的技术债（横切关注点散落各处）。建议按 Phase 0 → Phase 1 顺序执行，预计 5-6 PD 工作量即可消除所有重复和风险。
