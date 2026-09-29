# Agent 可观测性蒸馏报告：bkn-foundry 思路借鉴

> **状态：已归档（superseded）**。本报告的方法论与差距分析已由 agent-observability-system-design.md（体系设计）与 agent-observability-development-plan.md（执行排期）承接并实现（O1~O4，2026-08-29）；I1~I7 见 optimization-issues，I8/I9 已收编落地。本文保留作历史设计论证，不再更新。

> 日期：2026-08-27 | 范围：`data-ops-business-agent` 现状 vs `D:\tianxy\code\bkn-foundry` 可观测性设计
> 背景：Agent 已实现事件级观测（思考/工具调用可见），但用户感知不到思考用时、工具调用结果与用时，且链路混乱、无法溯源。
> 结论：**不是没记数据，而是记了没人读、没有结构。**

---

## 0. TL;DR（一句话结论）

`yak_agent_step` 表里其实已经把 LLM 耗时、token、重试次数、工具耗时、工具成败全部落库，`yak_agent_query_log` 也带 `elapsedMillis`——但这些表 100% 只有写路径没有读路径，前端看到的耗时全是 `Date.now()` 客户端自己掐表。bkn-foundry 的核心思路恰好是解决这件事：**分层（技术 span / 受管事实 / 业务证据）+ 同一 trace_id 贯穿 +「没观测到 ≠ 成功」显式化 + 隐私用 hash 不用原文**。它不是靠某个强力框架，而是一套可自研的骨架——现有四表结构离这个骨架只差两三步（读接口 + 关联键 + 服务端权威计时）。

---

## 1. 现状盘点：yak-ops agent 已经有什么

关键事实（代码真相）：

| 维度 | 数据落点 | 可否读取 | 说明 |
|---|---|---|---|
| 轮次生命周期 | `yak_agent_turn`（7 态状态机） | 后端可读，前端只凭 turnId 订阅、看不到 status | `start_time/end_time` 可算轮次总耗时 |
| 事件流 | `yak_agent_turn_event`（SSE 投影，`id`=投递游标） | SSE 增量订阅 + `Last-Event-ID` 断线续播 | 10 种事件类型，payload=ChatTurnEvent JSON |
| LLM 调用计量 | `yak_agent_step`（KIND_LLM_CALL） | **无读取路径** | `statsJson{promptTokens,completionTokens,latencyMs,retryCount}` 已落库 |
| 工具调用计量 | `yak_agent_step`（KIND_TOOL_CALL） | **无读取路径** | `statsJson{latencyMs}` + 成功/失败 errorCode 已落库，**不落工具输出** |
| 查询证据审计 | `yak_agent_query_log` | POST `/queries/page`，前端 AuditTable 展示 | 成功/失败/拒绝 + 行数 + 截断 + **elapsedMillis**，是唯一有耗时且有读路径的表 |
| 历史 trace（重建） | `yak_agent_turn_event` 回放重建 | GET `/sessions/{id}/history` 返回 `HistoryVO.trace` | `"think"` / `"call"` 两段式纯文本，无状态/耗时/时间戳 |

写入链条：

- **事件链路**：`AgentTurnExecutor.execute` → `AgentRuntime.stream()/resume()` → `AgentEventCodec` 映射为 `ChatTurnEvent` → 逐帧 `AgentTurnEventRepository.append` 落 `yak_agent_turn_event`。
- **步骤链路**：`LlmResilienceMiddleware.onModelCall` 每次尝试成功/失败 → `AgentStepRecorder.recordLlmCall`；`ToolAuditMiddleware.onActing` 成功/失败 → `recordToolCall`；轮次收尾 `recordCompleted(KIND_TURN_SUMMARY, {totalTokens})` 落 `yak_agent_step`。
- **证据链路**：`DatasetQueryGateway.execute` / 语义网关成功或异常 → `QueryEvidenceRecord` → `QueryLogRepository.record` 落 `yak_agent_query_log`。
- 所有记录均为 best-effort（失败仅 `log.warn`），不反向影响执行事实——这一点已经符合生产预期。

可观测性框架：pom.xml **未接入** OpenTelemetry / Micrometer / Prometheus / Actuator；`docs/agent/agent-optimization-plan.md` 明确"Langfuse/Prometheus 等 APM 接入推迟——先把计量数据落库，有真实流量后再接入"。`AgentProperties` 无任何观测配置项（无 step 开关、无抽样率）。

---

## 2. 用户抱怨对应的三个真实缺口

1. **思考用时：完全不存在。** 后端没有 thinking 时间戳，`THINKING_DELTA` 帧无时间字段，也没有对应 step。前端展示耗时全是 `Date.now()` 客户端掐表（混入网络/渲染噪声）；前端类型 `TraceStep.durationMs/startedAt` 是**声明了但从未赋值的死字段**。
2. **工具结果缺失。** `recordToolCall` 不传工具输出（签名无 result 参数）；工具失败时往往没有文本帧，失败原因只在 `yak_agent_step.errorMessage` 有 ≤300 字符预览，且**无读取接口**。
3. **用时「记了但看不到」。** `yak_agent_step.statsJson{latencyMs}` 里 LLM/工具耗时、重试次数全都在，但全项目没有任何 select/分页读路径（grep `AgentStepMapper` 只出现在写侧）。

对应链路乱/无法溯源的结构缺陷：

- 无 `traceId/spanId/parent_id`；四张表全为平铺记录。
- 所谓 trace 只存在于**读取时重建算法**里：`buildTrace` 把连续 THINKING_DELTA 攒成 `"think"`、TOOL_CALL/TOOL_RESULT 按 `toolCallId` 配对成 `"call"`——顺序线性拼接，无父子/嵌套关系；一轮多次工具迭代无法表达"哪段思考→哪次调用"，重试/被拒分支无结构化表达。
- **事件日志与 step 表互不关联**：`yak_agent_turn_event.id`（SSE 游标）与 `yak_agent_step.id`（自增）无引用；step 仅通过 `turn_id`（V6 新增列）弱关联，且 `recordToolCall` 不落 `toolCallId`。
- 历史对齐是「数数」式对号入座：`enrichWithTrace` 用 `traceIdx` 把第 N 条 assistant 轮次与第 N 条完成的 turn 对齐，混入失败轮次即错位。

---

## 3. bkn-foundry 可观测性思路（三层骨架）

调研范围：`bkn-trace/agent-observability`（trace core）、`comm-go/otel` + `comm-go/logger`（统一封装）、`adp/` 下三个 producer（context-loader/agent-retrieval 对话生命周期、execution-factory/operator-integration 工具执行、bkn-backend 知识网络）。

```
技术层     OTel span（trace_id / span_id / parent_span_id + start/end duration）
          → otelcol-contrib Collector（mapping.mode=ss4o）→ OpenSearch 索引
          + 事件/指标注入 trace_id；HTTP 响应统一回写 x-trace-id 头
受管事实层  conversation → interaction → operation(attempt) → receipt
          每次调用/工具/重试是幂等受管资源：attempt 序号、started/finished 时间、
          idempotency_key / lease_token / row_version 防重放
业务证据层  evidence event 链：event_id + event_type(白名单) + causation_event_id + payload(白名单)
          事件之间用 causation_event_id 串成因果链，回答"哪段意图→哪次动作→哪个结果"
```

### 3.1 关键技术事实

- **两套模型并行、同 trace_id 关联**：OTel span 图是"技术链路"，evidence + session（conversation→interaction→operation/attempt→receipt）是"受管执行事实"。`TechnicalTraceDetail = Summary + SpanGraph + OperationFacts` 三合一聚合输出——一次查询同时拿到总览、技术链路图、每个 operation 的事实。
- **证据事件词汇表是白名单**（`twoPointOneEventTypes`）：`agent.interaction.started`、`retrieval.completed`、`knowledge.read.observed`、`data.query.observed`、`model.call.observed`、`tool.called`、`tool.result.observed`、`claim.created`、`evidence.refs.created`、`action.approved/executed/result_recorded` 等。事件类型与 payload 字段都是白名单校验，比自由 attributes 可治理。
- **LLM 观测模型**（`model.call.observed` 允许字段）：`model_name, model_provider, status, input_token_count, output_token_count, prompt_hash, output_hash, error_category, error_hash`——**存 hash 不存原文**。
- **工具调用**：`tool.called`（tool_id/tool_name/args_hash/visibility/version_status）+ `tool.result.observed`（result_hash/result_length/result_count/status/error_hash）；`OperationCallFact` 带 input/output/error 的 `PayloadEnvelope`（inline ≤ 1MiB / referenced / omitted）。
- **失败与不完整是 first-class**：`partial_reasons` 贯穿响应（`orphan_span`、`invalid_span_timestamp`、`missing_terminal`、`causality_missing`、`source_event_missing`…）；receipt 有 `receipt_status(pending/completed/failed)` × `evidence_durability(pending/durable/failed)` 两个独立维度——pending 绝不当作 durable。
- **隐私边界显式化**：`traceparent` 正则校验；`forbiddenRawKeys` 拒绝 `prompt/raw_prompt/raw_tool_io/raw_sql/token_data` 等敏感字段落库；`event_id = "evt_" + sha256(trace_id|operation_id|event_type|attempt)` 稳定幂等（防重放/幂等收敛）。
- **trace 与日志关联**：`otellog.LogInfo/LogError` 双写——OTLP log record（带 trace_id/span_id）+ stdout zap（`[trace=… span=…]` 前缀）；查询端可按 traceId/spanId/request_id/conversation_id/operation_id/tool_name 下钻过滤。
- **生产管道**：outbox（持久化消息）→ projection worker → OpenSearch 投影（OCC 版本写入 + alias 切换），生产端故障不阻塞读侧。

### 3.2 局限（诚实说明）

- 无 eval/LLM 评测子系统（只有 evidence 校验与摘要）。
- 未采用标准 LLM 语义约定（`gen_ai.*`），用自有 `bkn.*` 前缀。
- trace→UI 只有 OpenAPI 契约，仓库内无前端实现。

---

## 4. 差距对照表

| 维度 | yak-ops 现状 | bkn-foundry 做法 | 差距本质 |
|---|---|---|---|
| 数据落地 | step 表已记 LLM 耗时/token/重试、工具耗时/成败；query_log 带 elapsedMillis | OperationCallFact 全套字段 | **数据在库但无读路径**（step 只写不读） |
| 思考用时 | 不存在；前端 `Date.now()` 掐表 | span start/end + duration | 服务端无权威计时 |
| 工具结果 | `recordToolCall` 不落输出；失败仅 ≤300 字预览且无读接口 | input/output/error PayloadEnvelope + result_hash/status | 结果不进账、失败原因无渠道 |
| trace 结构 | 无 traceId/spanId/parent；读取时平铺重建 think/call | trace_id/span_id/parent + causation_event_id 因果链 | 事件与 step 互不关联，无法表达思考→调用、重试分支 |
| 历史对齐 | traceIdx 数数式对齐，失败轮次即错位 | 受管资源自带 ID + 幂等 | 对齐脆 |
| 失败可见性 | errorMessage 纯文本、无错误码字段 | error_code/category + partial_reasons | 「未观测到」被静默当成功 |
| 隐私 | 8000 字符截断明文存 | sha256 hash + forbiddenRawKeys | 明文滞留审计链路 |
| 框架 | 自研表驱动（plan 明确推迟 APM） | OTel + OTLP + OpenSearch | 现阶段自研闭环更合适 |

---

## 5. 借鉴落地建议（零框架起步，按优先级）

### P0（立即做，自研，不改框架）

1. **给 step 表接读路径**：新增 `GET /turns/{turnId}/trace`（或并入 history 接口），按时间序把 `LLM_CALL / TOOL_CALL / TURN_SUMMARY` step 投影成 VO。数据全现成，只差一个接口——这是成本最低收益最大的一步。
2. **服务端权威计时下发**：`ChatTurnEvent` payload 加字段（事件枚举不动）：
   - `THINKING_DELTA`：`thinkingElapsedMs`（自 `ThinkingBlockStart...` 起算）
   - `TOOL_CALL / TOOL_RESULT`：`startedAt` + `durationMs` + `status`（成功/失败/被拒）
   - `TURN_FINISHED`：轮次总耗时（`endTime - startTime`）+ token 明细
   - 前端删除全部 `Date.now()` 掐表，改用服务端字段。
3. **工具结果进账**：`recordToolCall` 增加 `resultJson` / `errorCode` 参数，成功落截断后的结果摘要（复用现有 8000 字符截断逻辑），失败落分类错误码；step 表补 `tool_call_id` 列，把事件日志的 `TOOL_CALL` 帧与 step 记录一对一关联（治"链路乱"的核心原因：两张表现在没有任何 join 键）。

### P1（结构化 trace，治"无法溯源"）

4. **最小 trace 骨架**：`yak_agent_step` 加 `trace_id`（复用 turnId 或 `sessionId|turnId`）+ `parent_step_id` + `sequence`。关系模型：`TURN_SUMMARY`（根）→ `LLM_CALL` → 其触发的 `TOOL_CALL`；重试时 `LLM_CALL.attempt`（已有）+ parent 链表达重试分支。"哪段思考→哪次调用"有树状可溯源形态，历史重建从「数数对齐」变成「沿 parent 链查」。
5. **因果链字段**（借鉴 `causation_event_id`）：`yak_agent_turn_event` 帧 payload 加 `causationRef`（如 `LLM_CALL stepId → TOOL_CALL stepId`）。事件日志（投影）与 step 表（事实）通过引用打通，读取侧可"事件回放 + step 校验"双视图。

### P2（生产化隐私与完整性）

6. **hash 代替原文**（借鉴 `PayloadEnvelope` + `forbiddenRawKeys`）：LLM 请求/工具入参/查询 SQL 等敏感大 payload，审计链路落 `sha256` 哈希 + 长度，不落原文；原文只服务端短暂保留。
7. **partial 显式化**：history/audit 接口输出明确 `completed: false + reason[]`（哪个 attempt 缺失、哪个 span 断链），不静默拼看似完整的链。
8. **错误码入帧**：事件帧 `errorMessage` 纯文本改为 `errorCode`（复用已有 `ERROR_TIMEOUT/USER/PROVIDER/GUARD_REJECTED`），前端可分类展示。
9. **trace 与日志关联**（为将来迁移铺垫）：MDC 灌入 traceId，结构化日志带 `trace_id/turn_id`，响应回写 `x-trace-id` 头。

### 后续（有真实流量后）

将 step/event 数据经 OTLP 推给 Collector（照抄 `comm-go/otel` 的 span 注入 + `otellog` 双写 + `x-trace-id` 回写），此时已有结构化模型，是导出而非重构。可选：source coverage 监控（哪些 producer 持续上报）。

---

## 6. 一句话总结

缺的不是框架，而是两件事：**让已落库的数据能被读出来（P0）+ 给 step/事件加 trace_id + parent 关联键（P1）**。bkn-foundry 值得借鉴的是四个设计原则——「执行事实 / 受管资源 / 业务证据三层同 ID 贯穿」「隐私用 hash 不用原文」「缺失显式化（未观测到 ≠ 成功）」「记与证分离（attempt 与 receipt 独立）」。现有四表架构与其三层骨架同构，补齐读链路与关联键后，即得到一个可溯源、有耗时的 trace，而非平铺事件列表。