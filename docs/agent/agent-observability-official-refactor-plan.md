# Agent 可观测性 · 迁移官方方案重构计划（v1.0）

> 日期：2026-09-03
> 定位：把 agent 模块自建的可观测性（自建 span/step 体系、私有 SSE 事件流）整体迁移到 AgentScope 2.0.2 官方方案（OTel 分布式追踪 + AG-UI 标准事件流 + Studio 可视化）的**完整实施计划**。本文档是评审/排期/落地依据，不替代代码实现。
> 决策输入：用户明确选择"用 OTel 整体替换自建 span + 迁到 AG-UI 标准 + 一次性大改（含 Studio）"。
> 相关文档：`agent-observability-system-design.md`（既有自建体系设计）、`agent-observability-development-plan.md`（既有排期）、`agentscope-framework-audit-report.md`（框架能力审计）。

---

## 实施状态（2026-09-03 审核通过后）

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Phase 0 官方构件验证 | ✅ 完成 | `agentscope-extensions-agui/studio:2.0.2` 已下载（D 仓）。AG-UI = `AgentEventConverterRegistry` + `AguiEventEncoder` + `AguiAgentAdapter`（业务流内编码）；OTel = `TelemetryTracer`（官方 Tracer，随 studio 构件发布）；Studio = `StudioManager/StudioClient` |
| Phase 1 OTel | ✅ 完成 | `pom` 加 `agentscope-extensions-studio`；`AgentObservabilityOtelConfiguration`（`yak.agent.observability.otel.enabled/endpoint/headers` → `TelemetryTracer` + `TracerRegistry.register`）；yml + `docker-compose-jaeger.yml` |
| Phase 2 删自建 span/step | ✅ 完成 | 删除 `telemetry/`、`AgentObservationCollector`、`ToolAuditMiddleware`、`TraceViewAssembler`、`AgentStep*`、trace/observability 端点；剥离中间件/executor 观测调用；`yak_agent_step` 表归档保留 |
| Phase 3 AG-UI | ✅ 完成（自研编码） | 前端/后端事件名改 AG-UI 风格（RUN_STARTED/TEXT_MESSAGE_CONTENT/TOOL_CALL_START/TOOL_CALL_END/RUN_FINISHED/RUN_ERROR/CUSTOM）；**见下"决策记录"** |
| Phase 4 Studio | ✅ 完成（已接通） | `AgentObservabilityStudioConfiguration` 同步初始化 + `AgentRuntime.registerStudioHookIfEnabled` 挂 `StudioMessageHook` |

### 审核决策记录（2026-09-03）

1. **AG-UI 取舍（①）→ 已被 v2 升级（2026-09-03）**：v1 接受自研 `AguiEventMapper`（未引 `agentscope-extensions-agui`）。v2 已用官方模型升级（见 `agent-agui-official-v2-plan.md`）：官方 `AguiEventType` 现已含 `REASONING_MESSAGE_*` 全套标准事件（当初"非标准事件名"顾虑不成立），线上帧改用官方 `AguiEvent` + `AguiEventEncoder`，yak-ops 扩展（iter/phase/服务端计时/tokens）经官方 `rawEvent` 槽透传，`TOOL_RESULT` 按官方语义拆 `TOOL_CALL_END`+`TOOL_CALL_RESULT` 双帧；保留 `id:` 续播扩展头与 `yak_agent_turn_event` 日志真相（官方 Adapter 全接管仍留作 v2.1 独立协议端点）。**仍不承诺 CopilotKit 等生态前端互操作主链路**（载荷含 yak-ops 扩展），但协议模型/事件名已官方化。
2. **Studio（②）**：已接通——配置类同步 block 初始化（消除与懒组装 ReActAgent 的竞态），`AgentRuntime` 在开关开启且 `StudioManager.getClient()` 非空时挂 `StudioMessageHook`。默认关闭。
3. **Jackson（④）**：主 ObjectMapper 加 `JavaTimeModule` + `disable(WRITE_DATES_AS_TIMESTAMPS)`（日期→ISO-8601 字符串）。与 Spring Boot 默认一致，agent VO 的 `LocalDateTime` 序列化必需；**全局影响已评估为可接受**，但需留意任何依赖 epoch 毫秒日期的前端。
4. **project_id 项目空间隔离（④，范围外）**：V2 迁移 + agent/ontology repository 加 `project_id` 并统一 `currentProject.requireProjectId()`。**超出可观测性计划范围**，但与既有"项目空间"方向一致、实现一致；作为独立关注点跟踪，评审/回滚时与可观测性改动分开。
5. **契约冗余（2026-09-03 全量审核发现）**：`TURN_STARTED`/`RUN_STARTED` 在 `AguiEventMapper` 与前端 `index.tsx` 有映射/分发分支，但**当前无事件生产者主动发出**（首帧为推理的第一个增量）。不影响功能；待消费方确认后收敛（删除映射或补生产者）。

### 审核修复清单（2026-09-03）
- 修复另一个 agent 引入的编译错误：`OntologyActionManager.listByStatus` / `OntologyFunctionManager.listByStatus` 误返回 `LambdaQueryWrapper`（改为 `selectList(wrapper)`）。
- 清理死代码：`trace-runtime.ts` 的 `hydrateTraceSteps`/`prettyPayload`/本地 `SpanNode`/`StepPayload` + 对应测试用例。
- 预先存在的测试欠账已全部修复（agent 模块 test-compile 通过）。

---

## 一、背景与目标

### 1.1 为什么要重构

现有 agent 可观测性以"自建 truth"为核心，存在大量与官方方案重复的实现：

| 自建实现 | 官方 AgentScope 2.0.2 对应 | 结论 |
| --- | --- | --- |
| `yak_agent_step` 表 + AgentStepRecorder + AgentObservationCollector + KindSpec 注册表 | `OtelTracingMiddleware` + `TracerRegistry`（OTel span 体系） | **重复造轮子** → 用 OTel 替换 |
| `TraceViewAssembler` + `/turns/{id}/trace`（树/时间轴/聚合） | OTel span → Jaeger/Tempo 瀑布视图 | **重复造轮子** → 用 OTel/Jaeger 替换 |
| `/sessions/{id}/observability` 观测矩阵 | 无前端消费点（死接口） | 随自建体系一并删除 |
| 私有 SSE 事件名（TURN_STARTED/TEXT_DELTA/THINKING_DELTA/TOOL_CALL/TOOL_RESULT/…） | AG-UI 标准事件流（RUN_STARTED/STEP_STARTED/TOOL_CALL_START/TOOL_CALL_FINISH/TEXT_MESSAGE_CONTENT/RUN_FINISHED/ERROR） | 私有协议 → 迁到 AG-UI 标准 |
| 无开发期可视化调试器 | AgentScope Studio（独立 Web UI） | 新增 |

### 1.2 目标

- **生产/运维观测**：OTel span 导出到 Jaeger/Tempo，turn 级 trace 贯穿 agent.invoke / model.chat / tool.execute，支持跨进程/跨服务关联。
- **用户侧实时流**：SSE 事件按 AG-UI 标准协议编码，为将来对接 AG-UI 生态前端（如 CopilotKit）留缝。
- **开发期调试**：接入 AgentScope Studio 可视化调试（可选，独立 Web UI）。
- **删除**：自建 span/step 体系及纯观测展示代码与端点。
- **保留**：一切业务核心（见 §五）。

### 1.3 非目标

- 不引入外部 APM/Langfuse（继承既有决策）。
- 不整体改用 `agentscope-agui-spring-boot-starter` 自带会话模型（会丢失 HITL/游标续播/审计语义），AG-UI 采用"业务流内事件编码"。
- 不做实时告警大盘（内部平台，事后溯源优先）。

---

## 二、现状评估（探索结论）

### 2.1 自建可观测性构成

| 块 | 实现 | 定位 |
| --- | --- | --- |
| DB 持久化 | `yak_agent_turn`（状态机）、`yak_agent_turn_event`（SSE 日志）、`yak_agent_step`（span）、`yak_agent_query_log`（审计）、`yak_agent_message`（消息树）、`agentscope_sessions`（官方 StateStore） | 前两者+审计+消息树+StateStore 为**业务**；`yak_agent_step` 为**纯观测** |
| SSE 事件流 | AgentEventStreamTailer（DB 轮询补帧）+ AgentStreamCoordinator（SseEmitter 帧发布）+ ChatTurnEvent 自定义事件名 | 用户侧实时流；传输语义（游标/终态收敛）为业务契约 |
| trace 视图 | `/turns/{id}/trace` → TraceViewAssembler → TurnTraceView（树/时间轴/聚合/completeness/kinds） | 纯观测展示 |
| 观测矩阵 | `/sessions/{id}/observability` | 纯观测；前端死接口 |
| 采集 | AgentStepRecorder + AgentObservationCollector + 中间件内 step 记录调用 | 纯观测 |

### 2.2 业务强依赖（必须保留/迁移，删除时不可触碰）

- turn 状态机 + 提交/执行分离：`AgentChatService`、`AgentTurnDispatcher`（孤儿收敛/轮询认领）、`AgentTurnExecutor`（claim/CAS/终态收敛）、`AgentTurnRegistry`。
- HITL：`RequestClarificationTool`、WAITING_INPUT、resume 防伪造校验（`latestClarifyToolCallId`）、CLARIFY_REQUESTED 帧对刷新/重连客户端的重放。
- SSE 传输契约：游标断线续播（Last-Event-ID ↔ eventId）、终态收敛律（未闭合 toolCallId 兜底补 ABORTED/INTERRUPTED 帧）。
- 消息树（编辑/分叉/regenerate 前提）、会话/技能/配置治理、查询审计留痕（`/queries/page`）、StateStore（对话真相）。

### 2.3 前端消费现状（探索结论）

- 页面 `/ai-agent`，tab：对话（内嵌 ThoughtChain 时间线）、查询审计、配置、分析报告、技能管理。
- SSE 事件名被前端强耦合：`THINKING_DELTA / TEXT_DELTA / TOOL_CALL / TOOL_RESULT / CLARIFY_REQUESTED / TURN_FINISHED / TURN_CANCELLED / EXCEEDED_MAX_ITERS / ERROR`（index.tsx applyEvent switch、stream-runtime.ts、trace-runtime.ts）。
- trace 端点：`hydrateTrace`（index.tsx:379）只消费 `TurnTraceView.spans`；历史回放（index.tsx:503）用 HistoryVO.trace。
- `observability` 端点：**无任何前端消费**（死接口，删除零影响）。
- 若 SSE 改 AG-UI 标准事件名，前端 8 处文件级改动点（types.ts / api.ts / index.tsx / stream-runtime.ts / trace-runtime.ts + 3 个测试）。

---

## 三、官方 AgentScope 2.0.2 能力核实（已反编译验证）

### 3.1 OTel 追踪（可用，无需额外引 OTel API）

- `io.agentscope.core.tracing.OtelTracingMiddleware`（实现 MiddlewareBase）：onAgent / onModelCall / onActing 包住 Flux 创建 span，走 `GlobalOpenTelemetry`，自动注册 Reactor context 传播（opentelemetry-reactor-3.1）。
- `io.agentscope.core.tracing.TracerRegistry`：`register(Tracer)` / `resetToNoop()` / 默认 NoopTracer；register 非 NoopTracer 时自动装 Reactor 上下文钩子。
- `io.agentscope.core.tracing.Tracer` 接口：callAgent / callModel / callTool / callFormat / runWithContext / shutdown。
- core 已传递引入 `io.opentelemetry:opentelemetry-api:1.61.0` + `opentelemetry-reactor-3.1:2.27.0-alpha`。
- **结论**：把 `OtelTracingMiddleware` 加入 `ReActAgent.builder().middlewares(...)` + 配置 `GlobalOpenTelemetry`（SDK/exporter 需自行添加）即可产出官方 span。

### 3.2 追踪日志（可选）

- `HarnessAgent.Builder.enableAgentTracingLog(boolean)` → `AgentTraceMiddleware`（SLF4J 结构化日志，PRE/POST 推理 + 工具调用）。
- core 侧 `JsonlTraceExporter`（Hook，JSONL 输出）。
- 注意：本项目组装的是 core `ReActAgent`（非 HarnessAgent），enabled 开关在 ReActAgent 上不存在；如需日志追踪用 `JsonlTraceExporter` hook 或显式加 `AgentTraceMiddleware`。

### 3.3 AG-UI（BOM 管理，本地未下载，需 Phase 0 验证）

- 构件：`agentscope-extensions-agui`（BOM 2.0.2 管理）；审计报告另提及 `agentscope-agui-spring-boot-starter`（自带 SSE 对话接口、线程管理、断连处理、session 绑定）。
- 既有审计结论（`agentscope-framework-audit-report.md:317-324`）：AG-UI 远期价值 = 对接 CopilotKit 等 AG-UI 生态客户端；当前是私有协议，与 AG-UI 生态不兼容。

### 3.4 Studio（BOM 管理，本地未下载，需 Phase 0 验证）

- 构件：`agentscope-extensions-studio`（BOM 2.0.2 管理），未下载。

---

## 四、架构决策

1. **OTel = 生产/运维分布式追踪**：用 `OtelTracingMiddleware` + `TracerRegistry` + OTLP exporter（Jaeger/Tempo），替换自建 span 体系。
2. **AG-UI = 用户侧实时事件流协议**：保留自建 turn/session/HITL 业务流与 SSE 传输契约（游标/终态收敛），**仅把事件编码改为 AG-UI 标准事件名**；HITL/取消/迭代等无 AG-UI 标准等价语义，用 AG-UI 自定义事件（custom event）承载。不整体改用 starter 自带会话模型。
3. **Studio = 开发期可视化**：新增独立 Web UI，默认关闭，配置开启。
4. **删除**：自建 span/step 体系及纯观测展示代码与端点（见 §六删留清单）。
5. **保留**：全部业务核心（§二.2）。

---

## 五、分阶段实施计划

### Phase 0 — 验证官方构件（前置硬门槛）

- 用私服 settings（同 `FRAMEWORK_UPDATE_GUIDE.md` 的 `settings(1).xml` 与隔离仓库）下载：
  - `io.agentscope:agentscope-extensions-agui:2.0.2`
  - `io.agentscope:agentscope-extensions-studio:2.0.2`
- 对下载的 jar 用 javap 验证真实 API：
  - agui：是否提供纯事件编码器/桥接器（可在既有 SSE 流内复用），还是只有完整 starter（自带会话模型）。
  - studio：StudioClient 构造/注册/启动 API、serverUrl 配置。
- **决策门**：
  - agui 提供可复用的编码器 → 在既有流内做 AG-UI 编码（推荐路线）。
  - agui 只有完整 starter → 二选一：(a) 采用 starter 并适配业务流（高风险，需重新评估 HITL/审计/游标）；(b) 自研 AG-UI 标准事件编码（实现开放协议，非重复造轮子，推荐兜底）。
  - 构件在私服不存在/下载失败 → 记录并阻塞后续 AG-UI/Studio 阶段，OTel 阶段照常。

### Phase 1 — OTel 追踪（后端）

- **依赖**：`yak-ops-business-agent/pom.xml` 增加：
  - `io.opentelemetry:opentelemetry-sdk`（1.61.0）
  - `io.opentelemetry:opentelemetry-exporter-otlp`（1.61.0）
- **配置类**：`config/AgentObservabilityOtelConfiguration`（`@ConditionalOnProperty(prefix="yak.agent.observability.otel", name="enabled", havingValue="true", matchIfMissing=false)`）：
  - 构建 `OpenTelemetrySdk` + OTLP gRPC exporter（端点 `yak.agent.observability.otel.endpoint`，默认 `http://localhost:4317`），service.name=`yak-agent`。
  - `GlobalOpenTelemetry.set(otel)`（OtelTracingMiddleware 走全局）。
  - 按需 `TracerRegistry.register(...)`。
- **接线**：`AgentRuntime.doAssemble`（约 :136-151 的 `.middlewares(...)` 列表）条件追加 `OtelTracingMiddleware`。
- **基建**：新增 `docs/agent/` 下 Jaeger docker-compose 示例（jaegertracing/all-in-one，16686/4317/4318）。
- **配置项**：`application.yml` 增 `yak.agent.observability.otel.enabled`（默认 false）、`.endpoint`。
- **DoD**：开启后 Jaeger 能看到 turn 级 trace（agent.invoke / model.chat / tool.execute span）。

### Phase 2 — 删除自建 span/step 体系（后端 + DB + 前端降级）

- **删除（后端代码）**：
  - `telemetry/` 包：AgentStepRecorder、AgentKindRegistry、KindSpec、PayloadEnvelope、PayloadPolicy、RenderHint、AgentStepMetricsJob 等。
  - `runtime/AgentObservationCollector` 及中间件/executor 内的 step 记录调用点。
  - `conversation/query/TraceViewAssembler`、`AgentStepRepository(.Adapter)`、`AgentStepMapper`、`AgentStepPO`。
  - trace/observability 相关 domain 与 VO：TurnTraceView、SpanNode、TimelineEntry、KindAggregate、KindMeta、TraceCompleteness、StepPayload、SessionObservability、TurnObservability。
  - 端点：`GET /turns/{turnId}/trace`、`GET /sessions/{sessionId}/observability`。
- **剥离**：`LlmResilienceMiddleware` 保留韧性（超时/重试/错误分类），移除 step 记录；`ToolAuditMiddleware` 若仅为观测则删除（SSE 的 TOOL_RESULT 帧来自 mapStream 的 ToolResultEndEvent，不依赖它）。
- **DB**：`yak_agent_step` 表下线（历史数据归档保留，不物理删除）；保留 `yak_agent_turn_event`（SSE/HITL/游标业务）。
- **前端降级**：
  - `hydrateTrace`（index.tsx:379）与历史回放（index.tsx:503）的 trace 数据源，改为只用 SSE 事件 + turn_event + StateStore 数据；丢失 step 级入参/结果/失败归因展示（用户已确认接受）。
  - `agentSessionApi.trace`、`agentSessionApi.observability` 前端调用移除。
- **DoD**：自建 span/trace/observability 代码与端点删除，业务测试全绿。

### Phase 3 — AG-UI 标准事件流（后端 + 前端）

- **后端**：
  - 按 Phase 0 验证结果接入 agui 编码器/桥接器（或自研 AG-UI 标准编码）。
  - ChatTurnEvent → AG-UI 事件映射（建议）：
    | 现有事件 | AG-UI 事件 | 说明 |
    | --- | --- | --- |
    | TURN_STARTED | RUN_STARTED | run_id=turnId, agent_name |
    | THINKING_DELTA | 自定义 content part（AG-UI 无标准思考流事件） | 保留思考展示 |
    | TEXT_DELTA | TEXT_MESSAGE_CONTENT | AG-UI 为整段全文，前端改整文替换策略 |
    | TOOL_CALL | TOOL_CALL_START | toolCallId/name/arguments |
    | TOOL_RESULT | TOOL_CALL_FINISH / STEP_FINISHED | output/status/duration |
    | TURN_FINISHED | RUN_FINISHED | elapsedMs/usage |
    | ERROR / TURN_CANCELLED / EXCEEDED_MAX_ITERS / CLARIFY_REQUESTED | 自定义事件 | 保留错误码/停止/HITL 语义 |
  - **保留传输契约**：SSE cursor 续播（Last-Event-ID ↔ eventId）、终态收敛律、CLARIFY_REQUESTED 对重连客户端重放。
- **前端（8 处，探索已定位）**：
  - `services/agent/types.ts`：ChatEventType / ChatTurnEvent 载荷按 AG-UI 重构。
  - `services/agent/api.ts`：dispatchBlock 解析适配 AG-UI 帧（`{type, payload:{...}}` 嵌套）。
  - `pages/ai-agent/index.tsx`：applyEvent switch 重映射（含打字机整文替换策略；思考/HITL/取消走自定义事件）。
  - `stream-runtime.ts`：isDeltaType / coalesceEvents 增量类型名。
  - `trace-runtime.ts`：字段名与迭代分组适配（iter 若在 AG-UI 无承载则用自定义事件字段）。
  - 测试：`stream-runtime.test.ts`、`trace-runtime.test.ts`、SkillsTab mock 同步。
- **DoD**：前端按 AG-UI 标准事件名消费，对话/思考/工具/HITL/终态/断线续播可用。

### Phase 4 — Studio（可选，独立）

- 加 `agentscope-extensions-studio` 依赖；启动时连 Studio（serverUrl 可配 `yak.agent.observability.studio.server-url`，默认关），注册 agent。
- 独立 Web UI，不影响 yak-ops 前端。
- **DoD**：开启后 Studio 能看到 agent 消息流转/工具/模型请求。

---

## 六、文件级删留清单（后端）

### 删除（纯观测展示）
```
telemetry/                                 # AgentStepRecorder/KindSpec/PayloadEnvelope/... 整包
runtime/AgentObservationCollector.java
conversation/query/TraceViewAssembler.java
repository/AgentStepRepository.java + Adapter + Mapper + AgentStepPO
controller/v1 中 trace/observability 端点与 VO（TurnTraceVO/SessionObservabilityVO/...）
AgentStepMetricsJob
```

### 保留（业务强依赖）
```
conversation/AgentChatService、AgentTurnDispatcher、AgentTurnExecutor、AgentTurnRegistry
conversation/AgentEventStreamTailer、AgentStreamCoordinator（SSE 传输契约，改造为 AG-UI 编码）
conversation/AgentChatService.submitResume（HITL resume 校验）
runtime/AgentRuntime（ReActAgent 组装，改造：加 OTel 中间件、AG-UI 编码）
runtime/AgentEventCodec（事件映射，改造输出）
conversation/query/AgentSessionQueryService（保留 history/会话查询，剥离 trace 组装）
repository/AgentTurnRepository(.Adapter)、AgentTurnEventRepository、AgentQueryLogRepository、AgentSessionRepository、AgentSkillRepository、AgentConfig...
StateStore 接线（MysqlAgentStateStore）
```

---

## 七、前端改动清单

| 文件 | 改动 |
| --- | --- |
| `services/agent/types.ts` | ChatEventType/ChatTurnEvent 按 AG-UI 重构 |
| `services/agent/api.ts` | dispatchBlock/streamTurnEvents 适配 AG-UI 帧 |
| `pages/ai-agent/index.tsx` | applyEvent switch 重映射；打字机整文替换；trace 水合降级 |
| `stream-runtime.ts` | isDeltaType/coalesceEvents 类型名 |
| `trace-runtime.ts` | 字段名/迭代分组适配 |
| 3 个测试文件 | 事件名/字段同步 |

---

## 八、风险与缓解

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| AG-UI/Studio 构件 API 与预期不符（未下载、2.0.2 形态未验证） | 高 | Phase 0 硬门槛；不符则回退自研 AG-UI 标准编码 |
| AG-UI 标准丢失打字机增量/迭代分组/HITL/取消语义 | 高 | 用 AG-UI 自定义事件承载；前端适配；用户已确认接受 UX 降级 |
| 一次性大改回归风险（触碰既有已验证路径 I1~I7） | 高 | 每阶段业务测试全绿为门槛；先加 OTel 后删自建；删除前 git 基线；行为保持测试先行 |
| 采用 agui starter 自带会话模型导致 HITL/审计/游标丢失 | 高 | 明确采用"业务流内 AG-UI 编码"，不整体换 starter 会话模型 |
| Studio 需独立实例/额外运维 | 低 | 默认关闭，配置开启 |

---

## 九、验收标准

1. **OTel**：开启后 Jaeger 可见 turn 级 trace（agent.invoke / model.chat / tool.execute span），耗时/Token 可查。
2. **AG-UI**：前端按标准事件名消费；对话/思考/工具/HITL/终态可用；断线续播（Last-Event-ID）正常。
3. **删除**：自建 span/trace/observability 代码与端点删除；`yak_agent_step` 表归档下线。
4. **业务**：HITL 澄清/resume、游标续播、终态收敛、查询审计、会话/技能/配置全部保持可用；既有业务测试全绿。
5. **配置**：`yak.agent.observability.otel.enabled` / `.endpoint` / `studio.server-url` 可配、默认关。

---

## 十、待办/开放问题

- [ ] Phase 0：确认 `agentscope-extensions-agui` / `agentscope-extensions-studio` 2.0.2 在私服可下载、API 形态。
- [ ] 确认 Jaeger/Tempo 部署方式（docker-compose 或已有基础设施）。
- [ ] Studio 是否本期启用（独立 Web UI）。
- [ ] 前端打字机整文替换策略与思考/HITL 自定义事件的最终 AG-UI 事件命名。
