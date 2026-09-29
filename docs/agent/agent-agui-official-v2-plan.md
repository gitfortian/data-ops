# Agent AG-UI 官方化 v2 · 技术方案（双路径适配）

> 日期：2026-09-03
> 定位：把 v1 自研的 AG-UI 风格 SSE 编码（`runtime/AguiEventMapper`，扁平 record）**升级为官方 `agentscope-extensions-agui` 标准事件模型**（`AguiEvent` + `AguiEventEncoder`），在**不破坏 yak-ops 自控真相**（断线续播 / 服务端权威计时 / 终态收敛 / 记忆提取 / 历史 trace）的前提下拿到 AG-UI 生态互操作。
> 决策输入：`docs/agent/agent-observability-official-refactor-plan.md` 决策记录 ① 的重新评估——当时"官方无 REASONING 标准事件"的顾虑已不成立（官方 `AguiEventType` 已含 `REASONING_MESSAGE_START/CONTENT/END/CHUNK`、`TEXT_MESSAGE_START/END/CHUNK`、`TOOL_CALL_ARGS/RESULT/CHUNK`、`STEP_STARTED/FINISHED` 全套）。
> 依赖基线：`agentscope-extensions-agui:2.0.2`（本地仓已下载，`D:\tianxy\soft\apache-maven-3.9.16\repository\io\agentscope\agentscope-extensions-agui`）。
> 相关文档：`agent-observability-official-refactor-plan.md`（决策记录 ①）、`agent-module-summary.md`（§3.2 SSE 帧契约）、`data-agent-business-flow.md`（§6.1）、`data-agent-technical-architecture.md`（§1.4）。

## 实施状态（2026-09-03 全部完成）

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| 阶段 1 后端模型+映射 | ✅ 完成 | pom 加 `agentscope-extensions-agui`；新增 `runtime/ChatTurnToAguiMapper`（ChatTurnEvent→官方 AguiEvent）+ `ChatTurnToAguiMapperTest`（11 用例）；不接线编译通过 |
| 阶段 2 后端切换 | ✅ 完成 | `AgentStreamCoordinator` 改官方 `AguiEventEncoder` 编码（保留 `id` 续播头、去 `event:` 名）；`AgentEventStreamTailer` 由轮次记录构建 `AguiContext`（sessionId/turnId/assistantMessageId）；`AgentChatService.watch` 传 record；agent 模块全量测试 169/169 绿 |
| 阶段 3 前端切换 | ✅ 完成 | `services/agent/types.ts`（AG-UI 事件联合 + rawEvent 扩展 + 官方字段）、`api.ts`（type 从 data 内取 + rawEvent 摊平 + 官方字段别名映射）、`index.tsx`（AG-UI 事件名 + TOOL_RESULT 双帧 + CUSTOM 分发）、`stream-runtime.ts`（isDeltaType AG-UI 名）、`trace-runtime.ts`（applyToolResult 双帧保字段）；ai-agent jest 74/74 绿 |
| 阶段 4 清理 + 文档 | ✅ 完成 | 删除 `runtime/AguiEventMapper.java`（旧自研编码，无引用）；契约文档同步（本文件 + observability 决策记录 ① + module-summary/business-flow/technical-architecture） |

**顺带修复的预先存在欠账（非本方案引入，阻碍全量测试绿）**：
- `AgentProperties.Observability` 补 `Otel`/`Studio` 嵌套配置类（`AgentObservabilityOtel/StudioConfiguration` 依赖，缺失导致编译失败）；
- 架构守护测试同步 skill/observability 工作：`AgentArchitectureTest` Facade 白名单加 `AgentSkillManageService`；`AgentConditionalWiringTest` 补技能适配器 `@ConditionalOnAgentEnabled`；`AgentDependencyBoundaryTest` SDK 白名单改"最长前缀优先"并登记 skill/agui/tracing/studio 具体化豁免；`AgentFlywayContractTest` 迁移清单加 `V2__agent_add_project_id.sql`；`AgentDependencyBoundaryTest`（原 `AgentSkillManageService` 命中即报）与 `AgentArchitectureTest` 一并收敛；
- datasource 模块测试编译错误 `isRuntimeException()` → `isInstanceOf(RuntimeException.class)`。

---

## 一、背景与目标

### 1.1 现状（v1，自研编码）

- 真相链：`AgentEventCodec`（AgentScope 事件 → 10 种领域 `ChatTurnEvent`，富化 iter/phase/服务端计时）→ `AgentTurnExecutor.consume` 逐帧落 `yak_agent_turn_event`（自增 eventId = 游标）→ `AgentEventStreamTailer` 按游标补发 → `AgentStreamCoordinator.publish` 用 **自研 `AguiEventMapper`** 产出 `AguiFrame(eventName, 扁平 record)` 发出。
- 线上帧：`event:{AG-UI 名}` + `id:{eventId}` + `data:{扁平载荷}`。
- 自研载荷是**自定义扁平 record**（`{delta, toolCallId, toolName, iter, phase, startedAt, durationMs, toolStatus, elapsedMs, totalTokens, errorCode, customName}`）——事件名大体标准，但**载荷结构与官方 `AguiEvent` 不兼容，AG-UI 生态客户端无法解析**。

### 1.2 v2 目标

1. **线上帧载荷官方化**：改用官方 `AguiEvent` 密封接口（25 种 record）+ `AguiEventEncoder` 序列化，`data` 内 `"type"` 为标准事件名——AG-UI 生态前端（CopilotKit 等）可直接消费。
2. **自控真相不变**：`yak_agent_turn_event` 日志仍存富化 `ChatTurnEvent`（真相），断线续播（`id` 游标）、服务端权威计时、终态收敛律、记忆提取、历史 trace **全部保留**。
3. **前端改动最小**：现有 `dispatchBlock` 已有 `payload.type` 兜底，仅需显式切换 type 来源 + 扩展字段改从 `rawEvent` 读。
4. **回滚确定**：后端切换 = 单文件翻转；前端+后端同 commit 原子回滚；旧 mapper 延迟删除。

---

## 二、双路径适配设计（核心）

### 2.1 总体架构：一条真相 + 两段编码

```text
┌────────────────────────────── 真相路径（不变，自控真相） ─────────────────────────────┐
│  AgentEventCodec ──▶ ChatTurnEvent(富化 iter/phase/计时) ──▶ yak_agent_turn_event 落库 │
│  AgentTurnExecutor：pendingTools 终态收敛 / answer+tokens 累加 / 记忆提取 / 历史 trace   │
└────────────────────────────────────────────────────────────────────────────────────┘
                              │ 同一 ChatTurnEvent 帧
                              ▼
┌────────────────────────────── 协议路径（v2 官方化） ─────────────────────────────────┐
│  ChatTurnToAguiMapper（纯函数）──▶ 官方 AguiEvent ──▶ AguiEventEncoder ──▶ SseEmitter │
│  实时（executor 回调）与重放（tailer 从日志拉取）走【同一 mapper】→ 线上帧字节一致      │
└────────────────────────────────────────────────────────────────────────────────────┘
```

- **真相路径**：一切执行事实、续播、计时、收敛、记忆的唯一下游。**零改动**。
- **协议路径**：只负责"把一帧领域事件翻译成官方 AG-UI 帧发出去"。v2 唯一改动面。

### 2.2 为什么不做官方 `AguiAgentAdapter` 全接管（v2 边界，显式拒绝）

官方 `AguiAgentAdapter` + `AguiMvcController` 是**完整协议服务端**：直接消费 ReActAgent 原始 `AgentEvent` 流、自管 run/thread 生命周期、自出 SseEmitter。若全接管：

- ❌ 绕过 `yak_agent_turn_event` 日志 → 丢失 `Last-Event-ID`/cursor 续播；
- ❌ 丢失服务端权威计时（iter/phase/thinkingElapsedMs/durationMs）与终态收敛记账；
- ❌ 丢失记忆提取输入（toolNames/answer）与历史 trace 重建；
- ❌ 需并行订阅同一事件流做持久化，存在双路径分叉风险。

**裁决**：主链路采用"官方模型 + 自控真相"双路径（本方案）；官方协议服务端留作 **v2.1 可选独立端点**（`/api/v1/agent/agui/**`，供 CopilotKit 等生态客户端接入），不与主链路共享状态机，另行立项。

### 2.3 ID 映射与官方字段

| 官方概念 | yak-ops 映射 |
| --- | --- |
| `threadId` | `sessionId` |
| `runId` | `turnId` |
| `messageId`（文本/推理消息） | `assistantMessageId`（`TurnInput.assistantMessageId`，提交时冻结） |
| `timestamp` | 框架事件时间（无则 null）；yak-ops 权威计时仍走 `rawEvent` |

### 2.4 事件映射表（`ChatTurnEvent.TurnEventType` → 官方 `AguiEvent`）

| 领域类型 | 官方 AguiEvent | 官方字段填充 | 扩展载荷（rawEvent） |
| --- | --- | --- | --- |
| TURN_STARTED | `RunStarted(threadId, runId, parentRunId=null, input=null)` | — | —（无生产者，保留映射分支） |
| THINKING_DELTA | `ReasoningMessageContent(threadId, runId, messageId=assistantId, delta)` | delta | `{iter, thinkingElapsedMs}` |
| TEXT_DELTA | `TextMessageContent(threadId, runId, messageId=assistantId, delta)` | delta | `{iter, phase}` |
| TOOL_CALL | `ToolCallStart(threadId, runId, toolCallId, toolCallName)` | — | `{iter, startedAt}` |
| TOOL_RESULT | `ToolCallEnd(threadId, runId, toolCallId)` + `ToolCallResult(threadId, runId, toolCallId, content=toolResult, role="tool", messageId=assistantId)` | 两帧 | `{iter, durationMs, toolStatus}` |
| CLARIFY_REQUESTED | `Custom(threadId, runId, name="clarify_requested", value={toolCallId, toolName, question, options?})` | — | `{toolCallId, toolName, delta}` |
| TURN_FINISHED | `RunFinished(threadId, runId, result=null, outcome=RunFinishedSuccessOutcome())` | — | `{elapsedMs, totalTokens}` |
| TURN_CANCELLED | `Custom(threadId, runId, name="turn_cancelled")` | — | — |
| EXCEEDED_MAX_ITERS | `Custom(threadId, runId, name="exceeded_max_iters", value=errorMessage)` | — | — |
| ERROR | `RunError(threadId, runId, message, code=errorCode)` | — | — |

> **设计要点**：
> 1. **TOOL_RESULT 发两帧**（官方语义：`TOOL_CALL_END` 表示调用完成、`TOOL_CALL_RESULT` 承载结果内容）——这是 v1 单帧 `TOOL_CALL_END(toolResult=...)` 的结构升级，前端需按官方语义消费。
> 2. **HITL**：`CLARIFY_REQUESTED` 用官方 `CUSTOM` 扩展点承载（yak-ops WAITING_INPUT 非终态，不映射官方 `RunFinished(outcome=interrupt)` 的 `Interrupt` 模型；`Interrupt` 模型留给 v2.1 协议端点）。
> 3. **扩展字段透传**：iter/phase/计时/tokens/errorCode 全部塞进官方 `rawEvent` 字段（官方 record 面保持干净、生态可解析；`rawEvent` 是官方预留的"原始数据透传"槽位）。

### 2.5 线上帧格式（官方标准 + yak-ops 扩展头）

```text
官方标准：data: {"type":"TOOL_CALL_START","threadId":"sess-xxx","runId":"turn-xxx",
                 "toolCallId":"call_1","toolCallName":"run_semantic_query",
                 "rawEvent":{"iter":1,"startedAt":...}}
yak-ops 扩展：id:{eventId}          ← SSE 帧头，Last-Event-ID 断线续播（官方客户端忽略）
无 event: 名    ← 类型从 data 内 "type" 取（官方 `@JsonTypeInfo` 多态序列化）
心跳：:ping 3s（官方 AguiEventEncoder 支持 comment 编码，或沿用现有注释行）
```

- 编码器：官方 `AguiEventEncoder.encodeToJson(event)` 得 `" {json}"`（前导空格适配 Spring `data:` 前缀），或 `encode(event)` 得完整 `data: {json}\n\n`。MVC `SseEmitter` 用 `.id(eventId).data(json)` 组合。
- **`id:` 保留是 yak-ops 对 AG-UI 标准的加法扩展**（标准客户端忽略未知 SSE 字段），续播语义不变。

---

## 三、改动清单

### 3.1 后端（`data-ops-business-agent`）

| # | 文件 | 动作 | 说明 |
| --- | --- | --- | --- |
| B1 | `pom.xml` | 加依赖 | `agentscope-extensions-agui`（版本走 `agentscope-bom` 2.0.2） |
| B2 | **新增** `runtime/ChatTurnToAguiMapper.java` | 新建 | 纯函数：`AguiContext(sessionId, turnId, assistantMessageId)` + `ChatTurnEvent` → `List<AguiEvent>`（TOOL_RESULT 产两帧）；替换 v1 `AguiEventMapper` |
| B3 | `conversation/AgentStreamCoordinator.java` | 改造 | `publish(emitter, journaled)` 改用 `ChatTurnToAguiMapper` + `AguiEventEncoder`；保留 `.id(eventId)`；心跳不变。`AguiFrame` record 删除 |
| B4 | `conversation/AgentEventStreamTailer.java` | 改造 | `watch`/`drainOnce` 需要 `sessionId` 与 `assistantMessageId` 构造 `AguiContext`（tailer 已持有 turnId，经 `turnRepository.findByTurnId` + `TurnInputCodec.decode` 取得；`AgentChatService.openEventStream` 可把 record 传入避免重复查询） |
| B5 | `runtime/AguiEventMapper.java` | 删除（阶段 4） | v1 自研编码器，阶段 2 起不再引用 |
| B6 | **新增** `runtime/ChatTurnToAguiMapperTest.java` | 新建 | 10 种类型映射断言：官方类型/字段/rawEvent 透传/TOOL_RESULT 双帧/无 event: 名 |
| B7 | `AgentStreamCoordinator` 相关测试 / E2E | 增补 | SSE 帧形状断言：`data` 内 `type` 为标准名、`id` 存在、`rawEvent` 扩展可读 |

### 3.2 前端（`data-ops-ui`）

| # | 文件 | 动作 | 说明 |
| --- | --- | --- | --- |
| F1 | `services/agent/types.ts` | 改造 | `ChatTurnEvent`：新增官方 `threadId/runId/messageId`；`rawEvent` 扩展类型（iter/phase/计时/tokens/errorCode/customName）；类型联合补官方事件名（`TOOL_CALL_ARGS/RESULT`、`TEXT_MESSAGE_START/END`、`REASONING_MESSAGE_START/END`、`STEP_STARTED/FINISHED`） |
| F2 | `services/agent/api.ts` | 改造 | `dispatchBlock`：**type 改从 `payload.type` 取**（`meta.eventName` 默认值是 `'message'`，`'message' || payload.type` 会踩兜底——必须改为 `payload.type ?? meta.eventName`）；透传 `rawEvent` |
| F3 | `pages/ai-agent/index.tsx` | 改造 | `applyEvent`：`iter/phase/thinkingElapsedMs/durationMs/toolStatus/startedAt/elapsedMs/totalTokens/errorCode` 从 `event.rawEvent` 读取（保留旧字段名兼容过渡） |
| F4 | `stream-runtime.ts` | 微调 | `isDeltaType`/`coalesceEvents` 增量类型名不变（`TEXT_MESSAGE_CONTENT`/`REASONING_MESSAGE_CONTENT`），delta 读取不变 |
| F5 | `trace-runtime.ts` | 不动 | 消费 `HistoryTurn`/`ChatTurnEvent` 领域字段，与线上帧解耦 |
| F6 | 测试 | 同步 | `stream-runtime.test.ts`、`index.tsx` 相关 mock、`services/agent` 帧解析单测 |

> **前端契约要点**：`TOOL_RESULT` 双帧 → 工具卡闭合读 `TOOL_CALL_END`（toolCallId + rawEvent.durationMs/toolStatus），结果内容读 `TOOL_CALL_RESULT.content`；`CLARIFY_REQUESTED` 从 `CUSTOM(name=clarify_requested).value` 取（兼容读 rawEvent 旧字段）。

### 3.3 文档同步

- `agent-module-summary.md` §3.2 SSE 帧契约：线上帧格式改"官方 `AguiEvent` + `id:` 扩展头"；事件映射表更新。
- `data-agent-business-flow.md` §6.1、`data-agent-technical-architecture.md` §1.4：同步。
- `agent-observability-official-refactor-plan.md` 决策记录 ①：追加 v2 结论（官方 REASONING 已标准、采用官方模型、TOOL_RESULT 双帧、rawEvent 透传、协议服务端 v2.1）。

---

## 四、阶段划分与验收

| 阶段 | 内容 | 验收（DoD） |
| --- | --- | --- |
| **1 后端模型+映射（并行安全）** | B1 + B2 + B6 | `ChatTurnToAguiMapperTest` 全绿；**不接线**，编译通过即达 |
| **2 后端切换** | B3 + B4（B5 暂留） | agent 模块全量测试绿（28 文件/127 用例）；SSE 冒烟：帧 `data.type` 为标准名、`id` 存在、TOOL_RESULT 双帧、断线续播字节一致；旧 `AguiEventMapper` 保留未用 |
| **3 前端切换（与阶段 2 同 commit，原子回滚）** | F1-F6 | `tsc` 干净；交互验证：打字机/思考卡/工具卡/HITL 澄清/cancel/超迭代/断线续播（Last-Event-ID）/历史回放 trace 全部可用 |
| **4 清理 + 文档** | B5 删除 + §3.3 文档 | 删除后全量测试仍绿；契约文档已同步 |

> 阶段 2+3 强制**同一 commit**（前端契约与后端线上帧是原子对），回滚 = revert 该 commit。

---

## 五、回滚点

| 回滚点 | 状态 | 回滚动作 | 代价 |
| --- | --- | --- | --- |
| **R0** | 阶段 1 前基线 | 打 tag（当前 `future/data-agent` 全绿） | — |
| **R1** | 阶段 1 完成 | 删 B2/B6 新文件（B1 依赖可留） | 编译即可恢复 |
| **R2** | 阶段 2 完成 | `AgentStreamCoordinator.publish` 翻回 v1 `AguiEventMapper`（单文件翻转）；前端未动不受影响 | 一个文件 |
| **R3** | 阶段 3 完成（前后端原子 commit） | `git revert` 该 commit | 一个 commit |
| **R4** | 阶段 4 完成 | `git revert` 删除 commit（从 git 恢复 `AguiEventMapper`） | 一个 commit |

**回滚判据**：任一阶段出现对话流式异常（帧解析失败、思考/工具卡不收敛、断线续播丢帧、HITL 澄清失效）且 30 分钟内无法修复 → 回滚到上一回滚点，并保留 SSE 抓包证据。

---

## 六、风险与缓解

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 前端 `dispatchBlock` 的 `'message'` 默认 eventName 兜底把 `payload.type` 吃掉（`'message' || payload.type`） | 高 | F2 显式改为 `payload.type ?? meta.eventName` + 帧解析单测先行 |
| `TOOL_RESULT` 双帧导致前端工具卡重复/闭合错乱 | 高 | F3 按官方语义（END 闭合 + RESULT 内容）重写工具卡分支；E2E 断言 |
| `rawEvent` 透传体积（每帧带扩展对象） | 中 | 终态帧才带全量扩展（elapsedMs/totalTokens），增量帧只带 iter/phase 等必要字段 |
| 存量 `yak_agent_turn_event` 历史帧重放编码不一致 | 中 | 映射只依赖 `ChatTurnEvent` 字段（与版本无关）；重放走同一 mapper → 与实时字节一致 |
| 官方 2.0.2 API 与本仓不一致 | 低 | 本方案已按 `agentscope-java` 本地仓 `AguiEvent`/`AguiEventEncoder`/`AgentEventConverterRegistry` 源码核实（javadoc + 字段签名） |
| `id:` 扩展头被严格标准客户端拒绝 | 低 | SSE 规范允许未知字段；如遇严格客户端再提供 `?noId=1` 开关（不阻断续播则保持现状） |

---

## 七、验收标准（北极星）

1. 线上帧 `data` 为**官方 `AguiEvent` 结构**（`type` 标准名 + threadId/runId + 官方字段），AG-UI 生态客户端可解析（用官方 `AguiEventEncoder`/模型自检）。
2. 对话全链路回归：提交 → 思考 → 工具（双帧）→ 正文 → HITL 澄清 → resume → 终态；**断线续播（Last-Event-ID）无重复无丢失**。
3. 服务端权威计时（iter/phase/thinkingElapsedMs/durationMs/elapsedMs/totalTokens）经 `rawEvent` 前端可读，行为与 v1 一致。
4. agent 模块 28 文件/127 用例全绿；前端 `tsc` + 相关单测全绿。
5. 回滚演练：R2 单文件翻转与 R3 commit revert 均能在 10 分钟内验证恢复到 v1 行为。

---

## 八、v2.1 展望（本方案不做，登记挂起）

- **官方协议服务端端点**：`AguiAgentAdapter` + `AguiMvcController` 作为独立 `/api/v1/agent/agui/**`（CopilotKit 等生态接入），HITL 映射官方 `Interrupt` 模型；与主链路（真相日志）并行，需单独设计身份/权限/项目空间接入。
- **官方转换器管线**：若未来主链路改为直接消费原始 `AgentEvent`（放弃富化），可整体切 `AgentEventConverterRegistry` 内置 9 转换器——触发条件：主链路不再需要服务端权威计时/终态收敛等 yak-ops 扩展语义。
