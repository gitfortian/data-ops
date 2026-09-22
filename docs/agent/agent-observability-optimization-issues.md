# Agent 可观测性优化 Issue 列表

> 日期：2026-08-27 | 依据：`docs/agent/agent-observability-distill-report.md`
> 定位：把蒸馏报告中的差距转化为可执行工单；每个 issue 带**验收标准**，编号与实现对应。
> 前置决策（2026-08-27 评审）：**当前不引入 Langfuse**。
> 依据：langfuse-java 是 OpenAPI 自动生成的 **API 客户端**（0.3.0），不承担 trace 上报；官方 Java 通路是 OpenTelemetry/OTLP。
> 而本模块痛点集中在**写侧埋点（计时/工具结果捕获/step↔事件关联）与产品内嵌 trace 视图**，二者 Langfuse 都不解决。
> 结论：自建 P0/P1 继续；字段命名向 OTel semconv（gen_ai.*）靠拢，作为将来导出投影的预留出口（对齐 bkn-foundry 的"事实自建 + 技术层 OTLP 并行"）。

---

## 本轮实现（P0 + P1）

### I1 【P0】yak_agent_step 读路径：轮次 trace 详情接口
- **现状问题**：`yak_agent_step` 只有写路径（`AgentStepRecorder` 唯一写入口），全项目无任何 select 读接口；LLM 耗时/token/重试、工具耗时/成败全部落库但前端拿不到。
- **改动点**：
  - 新增 `AgentStepRepository` + `AgentStepRepositoryAdapter`（`listByTurn(turnId)`，按 `id` 升序）。
  - 新增 domain `AgentStepRecord`（不泄漏 PO 到读模型层）。
  - `AgentSessionQueryService.turnTrace(turnId)`：turn 归属校验（`turn.userId == 当前用户`，归属在提交时冻结）→ 组装 `TurnTraceVO`（turn 级 elapsed 由 `start_time/end_time` 计算，steps 平铺列表）。
  - `AgentController` 新增 `GET /api/v1/agent/turns/{turnId}/trace`（权限 `SESSION_READ`）。
- **验收**：curl 该接口返回 turn 级耗时 + 全部 step（LLM_CALL 带 promptTokens/completionTokens/latencyMs/retryCount，TOOL_CALL 带 latencyMs/toolCallId/responseJson/errorCode）；他人 turnId 返回 404 语义错误（归属拒绝）。

### I2 【P0】事件帧服务端权威计时（思考/工具/轮次）
- **现状问题**：`THINKING_DELTA`/`TOOL_CALL`/`TOOL_RESULT`/`TURN_FINISHED` 帧无任何时间字段；前端用 `Date.now()` 掐表（`turnStartRef`/`firstEventRef`/`thinkStartRef`/`toolStartRef`），`TraceStep.durationMs/startedAt` 是死字段。
- **改动点**：
  - `ChatTurnEvent` record 扩展（新增字段，保留 7 参兼容构造器）：`elapsedMs`（轮次总耗时，TURN_FINISHED）、`thinkingElapsedMs`（THINKING_DELTA 思考块累计）、`startedAt`（TOOL_CALL 发起时刻）、`durationMs`（TOOL_RESULT 执行耗时）、`toolStatus`、`errorCode`。
  - `AgentRuntime.mapStream`：跟踪思考块起点与每次工具调用的 `nanoTime` 起点，在 TOOL_RESULT 映射时注入 `durationMs`（`ToolResultEndEvent.getState()` 提供 `SUCCESS/ERROR/INTERRUPTED/DENIED` 成败事实）。
  - `AgentTurnExecutor`：`TurnState` 增加 `startMillis`，TURN_FINISHED/兜底终帧注入 `elapsedMs`（服务端权威值）。
- **验收**：SSE 流中 THINKING_DELTA 携带递增 thinkingElapsedMs、TOOL_CALL 携带 startedAt、TOOL_RESULT 携带 durationMs+toolStatus、TURN_FINISHED 携带 elapsedMs；前端不再需要本地掐表。

### I3 【P0】工具结果进账 + tool_call_id 关联
- **现状问题**：`recordToolCall` 不落工具输出（签名无 result 参数），失败仅 ≤300 字预览；step 与事件帧无 join 键，链路两张表互不关联。
- **改动点**：
  - `AgentStepPO`（+`tool_call_id`）、`yak_agent_step` 加列（V7 迁移）。
  - `AgentStepRecorder.recordToolCall` 增加 `toolCallId`、`resultJson`（复用 8000 截断）。失败时 result 为 null、errorCode/errorPreview 落账；`ToolResultEndEvent.getState()` 非 SUCCESS 映射为失败（ERROR/INTERRUPTED/DENIED）。
  - `ToolAuditMiddleware` 重构为**按单个工具调用记账**（不再把一批工具名用逗号拼成一条 step）：`onActing` 里按 `input.toolCalls()` 逐个建 Track（含 `ToolUseBlock.getContent()` 作 requestJson），`doOnNext` 聚合 `ToolResultTextDeltaEvent` 文本，`doOnComplete`/`doOnError` 逐条落账。
- **验收**：一次并发多工具调用产生 N 条 TOOL_CALL step，各自带 toolCallId/输入/输出/耗时/成败；事件日志中 TOOL_CALL/TOOL_RESULT 帧可按 `toolCallId` 与 step 一对一 join。

### I4 【P0】最小 trace 骨架（parent 链）
- **现状问题**：无 traceId/spanId/parent；trace 仅存在于读取时平铺重建（think/call 线性拼接），重试分支、"哪段思考→哪次调用"无结构化表达。
- **改动点**：
  - `yak_agent_step` 加 `parent_step_id`；`AgentStepPO`/`AgentStepRecord` 同步。
  - `AgentStepRecorder` 维护 turn 级 `lastLlmStepId` 映射：`recordLlmCall` 返回插入 id 并更新映射；`recordToolCall` 的 parent 取该映射值（工具归属于触发它的 LLM 调用）。`clearTurn(turnId)` 在轮次终态收敛时清理（`AgentTurnExecutor.settle` 调用）。
  - 顺序即事实：turn 内按 `id` 升序即执行序（sequence 由 id 单调性推导，不单设列）。
- **验收**：trace 接口返回的 step 可沿 `parent_step_id` 组装出 `TURN_SUMMARY → LLM_CALL → TOOL_CALL(s)` 树；重试时 N 次 LLM_CALL 各自为兄弟节点，最后一次的 TOOL_CALL 指向成功那次。

### I5 【P1】step ↔ 事件帧因果关联（causation）
- **现状问题**：事件日志（投影）与 step（事实）通过 `turn_id` 弱关联，无法从"某段思考"定位到"哪次调用"。
- **改动点**：I3 的 `tool_call_id` 即 join 键（事件帧本就有 toolCallId，step 新增同值列）；读取侧 `buildTrace`/trace 接口可用 `tool_call_id` 与事件帧双向对照。思考→调用因果借"turn 内顺序 + TOOL_CALL 的 parent=LLM_CALL"表达。
- **验收**：事件日志任一条 TOOL_RESULT 帧能定位到 step 表中的同 toolCallId 记录与该调用之前的思考增量段（文档记录 join 语义，不改事件帧格式）。

### I6 【P1】错误码入帧（替代纯文本）
- **现状问题**：事件帧失败是纯文本 `errorMessage`，无结构化错误码；executor 已有 `classify()` 分类但没用上。
- **改动点**：`ChatTurnEvent.error(message, errorCode)` 工厂；`AgentTurnExecutor.finishFailed` 用 `classify(error)` 作为错误码入帧。
- **验收**：ERROR 终态帧携带 `errorCode`（TIMEOUT/USER_ERROR/PROVIDER_ERROR/GUARD_REJECTED/GENERIC），前端可分类展示。

### I7 【P1】前端改用服务端计时
- **现状问题**：`index.tsx` 全部本地掐表（`Date.now()`），`TraceStep.durationMs/startedAt` 从未赋值，`elapsedMs` 只在客户端算。
- **改动点**：
  - `services/agent/types.ts`：`ChatTurnEvent` 增加对应可选字段；`UIMessage.elapsedMs` 语义改为"服务端权威值，缺失才本地兜底"。
  - `index.tsx`：TURN_FINISHED/onComplete 优先用 `event.elapsedMs`（缺失才 `Date.now()` 兜底）；TOOL_CALL 写入 `startedAt`；TOOL_RESULT 写入 `durationMs` + `resultText` + 失败状态；THINKING_DELTA 实时回写 `thinkingElapsedMs`。
- **验收**：对话页耗时/工具耗时显示服务端值；断线重连后补发的帧也能带上耗时（事件帧持久化天然携带）。

---

## 后续（P2 → 已收编进可观测性体系，2026-08-29）

> I8/I9 已随体系 O1/O2 提前落地，I10 维持条件触发。后续演进以 [agent-observability-system-design.md](agent-observability-system-design.md) 与 [agent-observability-development-plan.md](agent-observability-development-plan.md) 为准，本表不再新增条目。

### ✅ I8 敏感 payload hash 化（隐私边界）——已落地（O1 载荷策略引擎）
- `telemetry.PayloadPolicy` + `PayloadEnvelope` 四档（INLINE/SUMMARY_HASH/HASH_ONLY/OMITTED）+ forbiddenRawKeys 负清单；LLM 请求默认 SUMMARY_HASH（条数/末条预览/sha256，原文不落库）；排障升档经 `AgentProperties.Observability.llmRequestInlineDebug` 运行时控制。

### ✅ I9 partial 显式化（未观测到 ≠ 成功）——已落地（O2 completeness）
- trace v2 输出 `completeness{complete, reasons[]}`（orphan_tool_call/missing_turn_summary/empty_steps），断链不静默脑补；终态收敛律（O1）从源头消除幽灵运行卡。

### ⏸ I10 【条件触发】OTel 投影导出（Langfuse/Lightstep 之类展示层）
- step/事件数据经 OTLP 导出时，span 命名与属性向 `gen_ai.model.name/usage.*` 与 `tool.name` 靠拢（KindSpec 属性已对齐）；自建表仍为 truth，OTLP 为投影。具备真实流量后再接入。

---

## 验收总则
- 所有读写均为 best-effort：记录失败只告警，绝不反向影响执行事实（既有原则，保持）。
- 归属校验先于一切读取（I1 的 turnTrace 以 turn.userId 校验，不新增安全面）。