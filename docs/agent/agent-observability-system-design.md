# 数据智能体可观测性体系 · 系统设计（v1.0）

> 日期：2026-08-29
> 定位：Agent 可观测性的**体系级设计**——回答"为什么每次要个新观测维度都得临时加代码"，并给出"新维度=声明式注册，零侵入零改表"的灵活体系。
> 输入材料：`agent-observability-distill-report.md`（bkn-foundry 蒸馏）、`agent-observability-optimization-issues.md`（I1~I10）、`agentscope-framework-audit-report.md`、两份长期记忆文档（记忆线 KIND_MEMORY_* 需接入本体系）。
> 代码基线：`future/data-agent`，I1~I7 已落地（step 读接口/服务端权威计时/工具结果进账/parent 链/错误码入帧/前端计时切换）。
> 前置决策继承：不引入 Langfuse/APM；自建 truth + 字段向 OTel gen_ai.* 靠拢作投影预留。

---

## 一、问题定义：体验差的根源不是"缺数据"，是"缺体系"

### 1.1 一个反直觉的事实

以用户视角的四个诉求逐项核对代码，会发现**数据大都在库里**：

| 诉求 | 数据现状 | 为什么体验仍然差 |
| --- | --- | --- |
| 工具调用时长 | step `statsJson.latencyMs` + 事件帧 `durationMs`（服务端权威）都有 | 链路 UI 只显示"耗时 x.x 秒"标签，**无时间轴占比、无慢调用定位** |
| 工具入参 | `ToolAuditMiddleware` 已捕获 `ToolUseBlock.getContent()` 落 `requestJson` | **前端链路里根本不展示入参**；要看只能查库 |
| 工具结果 | `responseJson` 已落（成功时） | 同上不展示；失败时 `errorPreview` 是模板句（"工具执行未成功(state=ERROR)"），真实失败详情埋在 responseJson 的业务 JSON 里无人解析 |
| 关键过程 | LLM 每次尝试、思考块计时都有 | **LLM_CALL step 零内容观测**（request/response 均为 null，只有 token/耗时）；Guard 拒绝、Compaction 触发、HITL 挂起/恢复**完全没有 step**（`KIND_GUARD` 定义了无人写入） |

### 1.2 "临时加"模式的七个结构性根源

每次新增一个观测维度（比如即将到来的记忆观测），当前都要动 5~6 处代码——这就是"临时加到工具或侵入编码"的根源：

| # | 根源 | 证据 |
| --- | --- | --- |
| G1 | **无统一契约**：SSE 帧、step 表、前端 TraceStep 三处各自演化，同一个"耗时/状态/错误码"概念有三份定义和两条计算路径（mapStream 一份、ToolAuditMiddleware 一份） | `ChatTurnEvent` 7 个计时字段 vs step `statsJson` vs `TraceStep`，语义靠人肉对齐 |
| G2 | **Kind 无治理**：无类型注册表与准入流程；DDL 注释宣称 6 种 kind，常量只有 4 个，`KIND_GUARD`/`KIND_COMPILE`/`KIND_CLARIFY` 无写入方；每种 kind 的 statsJson 字段是隐式契约 | `AgentStepRecorder` 常量 vs `V4__agent_steps.sql` 注释 |
| G3 | **载荷无策略**：8000 字符头尾截断是散落的 magic number；截断破坏 JSON 完整性（前端无法 pretty-print）；敏感 payload 明文滞留（I8 挂起）；无 per-kind 差异化策略 | `AgentStepRecorder.truncate()` |
| G4 | **读取侧不做组装**：trace 接口返回平铺 steps 列表，parent 链不在读取侧组装成树；无 kind 维度聚合；断链/缺失静默（I9 挂起） | `TurnTraceDetail.steps` 平铺 |
| G5 | **LLM 调用零内容观测**：`recordLlmCall` 的 request/response 恒为 null——"模型想了什么、被喂了什么"只有 token 数没有快照 | `LlmResilienceMiddleware` → `recordLlmCall(request=null)` |
| G6 | **前端不消费事实侧**：trace REST API（`GET /turns/{id}/trace`）上线后前端零调用；历史回放 trace 仍是旧的 think/call 文本重建，当次会话的观测数据刷新即失 | `services/agent/api.ts` 无 trace 调用 |
| G7 | **生命周期观测盲区**：Guard 拒绝只在语义路径错误码出现、Compaction 无记录、HITL 挂起/恢复无 step、重试的"分支感"只有 attempt 数字 | grep 全模块无 KIND_GUARD 写入 |

### 1.3 设计目标与非目标

**目标**：新增任何观测维度（新 kind、新属性、新载荷策略）= **注册一份 KindSpec 声明**（+可选一个 Middleware），不改表结构、不改采集器代码、不改前端渲染组件；四个用户诉求（时长/入参/过程/结果）在链路 UI 一等公民化。

**非目标**：不引入外部 APM/Langfuse（继承既有决策）；不做实时监控大盘（内部平台，事后溯源优先于实时告警）；不追全量 LLM 原文落库（隐私与成本，见 §五 策略分级）。

---

## 二、已有资产评估：保留什么

体系化不等于推倒重来。以下资产直接作为体系的组成部分保留：

| 资产 | 体系角色 |
| --- | --- |
| 框架三拦截点（onModelCall / onActing / onSystemPrompt）+ 既有三 Middleware | **唯一采集面**——体系的地基已经正确，零侵入采集正是 Middleware 模式的产出 |
| `yak_agent_step` 表 + `parent_step_id`/`tool_call_id`（V7） | 事实层 truth 载体；只需增量升列（§六），不动已有列语义 |
| `AgentStepRecorder` 单写入口 + best-effort + 失败也记账 | 采集协议的落库端点，收敛后保留 |
| `GET /turns/{turnId}/trace` + turn 归属校验 | 读侧 v2 的基座 |
| `tool_call_id` 双向 join（I5） | 事件投影 ↔ 执行事实联通键，"记与证分离"的既有实现 |
| bkn 四原则（三层同 ID 贯穿 / hash 不原文 / 缺失显式化 / 记证分离） | 体系设计原则，全部继承并落地 |

---

## 三、体系总览：四层一架

```text
┌─────────────────────── 采集层（唯一采集面，零侵入不变） ───────────────────────┐
│  框架拦截点                  生命周期钩子                                     │
│  onModelCall ──► LlmResilienceMiddleware      executor finish* ──► 轮次终态    │
│  onActing    ──► ToolAuditMiddleware          HITL 挂起/恢复 ───► (新增钩子)    │
│  onSystemPrompt ─► (预留)                     Guard 拒绝 ──────► (新增 Middleware)│
│         全部只调用 ────────────┐                                               │
└──────────────────────────────┼───────────────────────────────────────────────┘
                               ▼
┌─────────────────────── 协议层：AgentObservationCollector ────────────────────┐
│   统一 API：begin(kind, name) → observe(...) → end(status, payload, error)    │
│   职责：KindSpec 校验（未注册 kind 拒绝落库）→ 载荷策略执行 → 组装 AgentSpan    │
│         → 单写入口（AgentStepRecorder）落库。新维度不改此层。                  │
└──────────────────────────────┬───────────────────────────────────────────────┘
                               ▼
┌──────────── 模型层：AgentSpan 契约 + KindSpec 注册表 + PayloadEnvelope ────────┐
│   span = {traceId=turnId, spanId, parentId, kind, name, seq, attempt,         │
│           startedAt/endedAt/durationMs, status, errorCode, attributes, payload}│
│   KindSpec = 每种 kind 的声明式元数据（属性 schema/载荷策略/渲染提示/聚合维度）   │
└──────────────────────────────┬───────────────────────────────────────────────┘
                               ▼
┌─────────────────────── 消费层：三视图 + 双通道 ──────────────────────────────┐
│  轮次 trace 树 API v2（树组装+聚合+partial）  会话级观测视图（turns×kinds 矩阵）│
│  前端通用 TraceTimeline（数据驱动渲染，新 kind 自动出现）                       │
│  实时通道（SSE 帧）= 体验流；权威通道（trace API）= 事实流；回放/审计走事实流     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、模型层：AgentSpan 契约与 KindSpec 注册表（灵活性核心）

### 4.1 AgentSpan 统一契约

一套契约同时约束落库形态、API 输出与前端渲染输入：

```text
AgentSpan {
  traceId      = turnId（沿用 V6 语义，不新造 ID）
  spanId       = step.id
  parentId     = parent_step_id（TOOL_CALL→LLM_CALL 已实现；扩展规则见 4.2）
  kind         = 受治理类型（§4.2 注册表）
  name         = 模型名/工具名/阶段名
  seq          = turn 内执行序（id 单调性推导，既有决策不变）
  attempt      = 尝试序（重试/并行批内序号，升列）
  startedAt / endedAt / durationMs   ← 新列：SQL 可聚合（现状埋在 statsJson 里）
  status       = COMPLETED/FAILED/REJECTED（终态制，best-effort 不变）
  errorCode    = 分类错误码白名单
  attributes   = 类型化属性（对齐 OTel gen_ai.*：gen_ai.usage.input_tokens 等），per-kind schema
  payload      = PayloadEnvelope（§五）
}
```

关键裁决：**不新增 attributes 列**——`stats_json` 列保留并升级为"该 span 的类型化属性"载体，schema 权威从"隐式约定"移到 KindSpec。避免一次大迁移，契约变化由注册表版本管理。

### 4.2 KindSpec：声明式类型注册表

每种观测类型一份声明，注册即生效（采集校验、策略、渲染、聚合四处自动消费）：

```java
/** 新增观测维度 = 新增一份 KindSpec，采集/策略/渲染/聚合零代码改动。 */
record KindSpec(
    String kind,                       // "LLM_CALL" / "TOOL_CALL" / "MEMORY_FLUSH" ...
    String title,                      // 渲染标题："模型调用" / "长期记忆提取"
    Set<String> allowedStatus,         // 状态白名单（治理：注册即准入）
    AttributeSchema attributeSchema,   // attributes 字段白名单 + 类型 + 是否敏感
    PayloadPolicy payloadPolicy,       // request/response 的截断/脱敏/-hash 策略（§五）
    ParentRule parentRule,             // 父链规则：LLM_CALL / LAST_LLM / TURN / NONE
    RenderHint renderHint,             // 图标/颜色/属性展示优先级/结果折叠策略（前端数据驱动）
    Aggregation aggregation            // 聚合维度：哪些 attributes 进 SUM/AVG（会话级视图用）
) {}
```

首批注册（存量收编 + 盲区补齐 + 记忆线预留）：

| kind | parentRule | 载荷策略要点 | 填补的缺口 |
| --- | --- | --- | --- |
| `LLM_CALL` | TURN | request=**摘要+hash**（消息条数/末条预览/字符数，默认不落原文）；response=hash+长度 | G5 零内容观测 |
| `TOOL_CALL` | LAST_LLM（既有） | request=inline 截断（JSON 感知）；response=inline 截断；失败时**解析工具业务错误 JSON** 优先于模板句 | G3/G"失败详情不可见" |
| `GUARD_REJECT` | LAST_LLM | attributes: 规则名/拒绝对象/建议动作；request=inline | G7 Guard 盲区（KIND_GUARD 收编更名） |
| `HITL` | LAST_LLM | attributes: 挂起/恢复/放弃；request=澄清问题摘要 | G7 HITL 盲区 |
| `COMPACTION` | TURN | attributes: trigger 前后消息数/token | G7 Compaction 盲区 |
| `TURN_SUMMARY` | TURN（根） | attributes: tokens/elapsed/memoryHit | 既有收编 |
| `MEMORY_FLUSH`/`MEMORY_RECALL`/`MEMORY_CONSOLIDATE` | LAST_LLM | 记忆线规划的三 kind 直接以 KindSpec 注册，**验证本体系的灵活性主张** | 记忆线接入 |

治理规则：`AgentStepRecorder.insert` 收到最后一道校验——**未注册 kind 拒绝落库并告警**（防"定义了没人用/用了没人管"再次发生）；DDL 注释与注册表从两处真相收敛为注册表单一真相（DDL 注释只指向注册表）。

### 4.3 父链规则扩展（G7 的"分支感"）

- `TOOL_CALL → LAST_LLM`：既有语义不变；
- `GUARD_REJECT / MEMORY_* → LAST_LLM`：守卫与记忆归属触发它们的推理步；
- `HITL → LAST_LLM`：挂起与恢复各一条 span，同 toolCallId 关联（复用既有 join 键）；
- 重试：同 turn 的 N 次 `LLM_CALL` 天然兄弟节点 + `attempt` 列（现状 retryCount 埋在 statsJson，升列后可 SQL 过滤）。

---

## 五、载荷策略引擎（G3 治理 + I8 落地）

**PayloadEnvelope** 取代裸截断字符串，四档模式由 KindSpec 声明、`AgentProperties.Observability` 可按 kind 覆写：

| 档位 | 内容 | 适用 |
| --- | --- | --- |
| `INLINE` | 原文，超 `maxInlineChars` 做 **JSON 感知截断**（超限时降级为合法 JSON 骨架 + `truncated:true`，不破坏可解析性） | 工具入参/结果、Guard 上下文 |
| `SUMMARY_HASH` | 结构摘要（条数/字符数/末条预览）+ sha256 + 长度；**原文不落库** | LLM request（消息内容属敏感面） |
| `HASH_ONLY` | sha256 + 长度 | 预留（如未来落完整 prompt 的开关档） |
| `OMITTED` | 显式标记"策略性省略"，不冒充不存在 | 任何 kind 的可选字段 |

配套纪律：

1. **forbiddenRawKeys 负清单**：`raw_prompt / token / 行级数据值` 等键拒绝入 INLINE 档（对齐 bkn 原则，I8 的落地形式）；
2. 截断长度从 magic number 变配置：`yak.agent.observability.payload.max-inline-chars`（默认沿用 8000），per-kind 覆写；
3. **I9（partial 显式化）收编**：trace 读侧输出 `completeness: {complete, reasons[]}`——TOOL_CALL 无父 LLM、TOOL_RESULT 帧与 step 断链、turn 无 TURN_SUMMARY 等断链模式显式化，不静默脑补；
4. 采样与开关：`enabled` 全局开关 + per-kind `enabled`（调试期可只开 TOOL_CALL 高保真），采样率字段预留不实现（内部流量暂无必要，防过度设计）。

---

## 六、数据模型演进（一次增量迁移）

```sql
-- 迁移版本号以合入顺序为准（记忆线已规划 V8，本线示例 V9）
ALTER TABLE `yak_agent_step`
    ADD COLUMN `started_at`  DATETIME(3) NULL COMMENT 'span 开始时刻（服务端权威）',
    ADD COLUMN `ended_at`    DATETIME(3) NULL COMMENT 'span 终态时刻；create_time 语义不变',
    ADD COLUMN `attempt`     INT NULL COMMENT '尝试序：重试/并行批内序号，1 起',
    ADD KEY `idx_agent_step_turn_kind` (`turn_id`, `kind`, `id`);
```

- `started_at/ended_at` 升列后：慢调用定位、kind 耗时分布、轮内时间轴全部 **SQL 可做**（现状必须解析 statsJson）；
- 存量行不回填（新写即有；读侧对 NULL 回退 statsJson.latencyMs 兼容）；
- 不改既有列语义，不动 V7 关联键。

---

## 七、消费层：三视图双通道（体验核心）

### 7.1 轮次 trace 树 API v2（G4 治理）

`GET /turns/{turnId}/trace` 升级为树形+聚合+完整性：

```text
TurnTraceVO v2 {
  turn: { status, elapsedMs, totalTokens, startTime, endTime }
  tree: [ SpanNode { span, children[] } ]        ← parent 链读取侧组装（TURN_SUMMARY 为根）
  timeline: [ { spanId, kind, startedAt, durationMs } ]  ← 归一化时间轴（前端直绘）
  aggregates: { byKind: { TOOL_CALL: {count, totalMs, maxMs, p95} , LLM_CALL: {count, totalTokens,...} } }
  completeness: { complete: boolean, reasons: string[] }   ← I9 落地
  kinds: { kind → RenderHint }                    ← 注册表投影，前端数据驱动渲染的依据
}
```

### 7.2 会话级观测视图（新增）

`GET /sessions/{id}/observability`：turns × kinds 矩阵（每轮 LLM 次数/重试次数/工具次数/总耗时/token），一眼定位**慢轮次、重试异常轮次、失败集中的工具**。数据全部来自既有表 JOIN，无新采集。

### 7.3 前端通用 TraceTimeline（G6 治理）

- 一个**数据驱动**组件消费 `tree/timeline/kinds`：kind→图标/颜色/标题全部来自响应里的 RenderHint，**新 kind 注册后前端自动出现，无需改组件**；
- 工具 span 卡片四要素一等公民化：**耗时**（时间轴占比条）、**入参**（格式化 JSON 折叠面板）、**结果**（折叠面板+截断标记，失败时解析业务错误 JSON 优先展示）、**状态/错误码**；
- 双通道裁决：SSE 帧=当次会话实时体验流（不动）；trace API=历史回放/刷新恢复/审计权威流——历史回放的 trace 从"think/call 文本重建"切换为消费 trace v2（`HistoryTurn.trace` 升级），刷新后观测数据不再丢失；
- 思考块/LLM 轮次在时间轴上可见（G5 的体验侧补偿：request 是 hash 摘要，展示"第 N 次推理 · 输入 x tokens · y 秒 · 重试 z 次"）。

### 7.4 失败语义归一（"结果"诉求的最后一块）

工具失败时 `errorPreview` 的模板句（"工具执行未成功(state=ERROR)"）降级为兜底：读取侧优先解析 responseJson 中的业务错误结构（业务工具失败约定返回 `{success:false, error:...}`），把**真实失败原因**提到错误展示位；无法解析才回退模板句。工具实现零改动。

---

## 八、采集层收口（G1 治理，工作量最大的一步）

1. **`AgentObservationCollector`**：统一 begin/observe/end API，内嵌 KindSpec 校验 + 策略引擎 + span 组装；`AgentStepRecorder` 收敛为其唯一落库端点（单写入口不变）；
2. **计时归一**：`AgentRuntime.mapStream`（帧侧）与 `ToolAuditMiddleware`（事实侧）的两份耗时计算收敛——事实侧（nanoTime）为权威，帧侧复用同一计算器，消除双路径漂移（G1 的具体解）；
3. **新增观测点=新 Middleware**：`GuardObservationMiddleware`（onActing 拦截 Guard 异常/拒绝路径）、HITL 钩子（executor 挂起/恢复处 emit）——业务工具与框架调用代码零改动；
4. 架构守护测试扩展：扫描禁止业务工具/非 runtime 层直接调用 Collector（维持"采集面只在 middleware/executor"边界）。

---

## 九、演进出口（不现在做，只留缝）

1. **OTLP 投影导出**：KindSpec 已带 gen_ai.* 属性映射，导出时 span→OTel span 是投影不是重构（I10 既有决策）；
2. **指标化**：aggregates 结构已是 Prometheus 直译格式，接入时反高基数纪律不变；
3. **实时告警**：completeness reasons 与错误码白名单可直接驱动规则告警（有运维诉求再立项）。

---

## 十、风险

| 风险 | 等级 | 对策 |
| --- | --- | --- |
| 收口改造（§八）触碰既有已验证路径（I1~I7 刚落地） | 高 | 行为保持测试先行：既有 58 测试全绿为门槛；mapStream 计时收敛逐字段对照回归 |
| KindSpec 注册表变成新的"三处真相"（代码/DDL/文档漂移） | 中 | 注册表为唯一真相 + 启动时校验 DDL kind 枚举一致性（架构守护测试） |
| LLM request 摘要策略过严影响排障 | 中 | SUMMARY_HASH 档保留末条用户消息预览（排障最常用）；debug 开关可临时升档 INLINE（配置热更路径） |
| 前端 Timeline 组件过度设计（试图一次做全） | 中 | 先做 kind 驱动的最小渲染集（列表→树→时间轴三步），RenderHint 只含 title/color/icon/折叠策略四项 |
| 与记忆线 V8 迁移冲突 | 低 | 版本号以合入顺序为准，文档已互引声明 |

---

## 十一、渲染顺序规范（顺序即语义）

> 用户原话："工具调用、思考、LLM 调用在页面渲染顺序看着怪怪的——下面停了上面工具还在调用。"
> 结论：这不是渲染 bug 的随机分布，而是**缺少一套顺序契约**。观测体系必须把"逻辑执行顺序"定义为事件序列不变式 + 客户端状态机，让顺序异常变成可报告的违例，而不是玄学。

### 11.1 问题定位（代码实证的四个"怪"源）

| # | 现象 | 根源（代码位置） |
| --- | --- | --- |
| V1 | 一次轮内多步推理平铺堆叠，看不出哪段思考属于哪次调用 | 前端 `trace` 是扁平列表；`ensureThinkStep` 仅按"末尾元素是否为 think"启发式分组，ReAct 的迭代结构（思考→工具→思考→…）在 UI 上无边界 |
| V2 | 答案/错误已出，上方工具卡仍转圈 | **终态不收敛**：`TURN_CANCELLED` 帧前端 switch 无分支（落入 default）；`ERROR/EXCEEDED_MAX_ITERS` 只设错误文本不清运行卡；服务端 `finishFailed/finishCancelled` 超迭代路径不对未闭合 toolCallId 补结果帧 |
| V3 | HITL 反问等待期间工具卡一直"执行中" | `CLARIFY_REQUESTED` 后工具卡没有"等待输入"态，与 RUNNING 混淆——用户在下方答题、上方卡片转圈，正是"下面停了上面在调用"的观感来源之一 |
| V4 | 中间叙述文本与最终答案混排 | `TEXT_DELTA` 单缓冲无 phase 区分，模型轮中叙述（"我先查一下…"）直接混进最终答案 |

### 11.2 逻辑结构模型（渲染语法）

```text
Turn     ≡ Iteration[1..N] + FinalAnswer
Iteration ≡ ThinkingBlock* + ToolBatch（声明序） + ToolResult*（完成序）
FinalAnswer ≡ 独立区域，恒在轮内尾部
```

渲染组件的输入必须是这棵树，而不是事件平铺列表。**迭代（一次成功的模型调用及其工具批）是渲染的基本分组单元**，思考块/工具卡是组内成员。

### 11.3 服务端不变式（事件序列契约）

1. **因果序**：迭代 i 的 THINKING/TOOL_CALL 帧严格先于其 TOOL_RESULT；迭代 i 的 TOOL_RESULT 全部先于迭代 i+1 的 THINKING/TOOL_CALL（ReAct 数据依赖使然，服务端天然满足，此处显式化为承诺）。
2. **配对律**：TOOL_CALL 按调用声明序发出；TOOL_RESULT 按**完成序**返回（并行批内乱序合法）——客户端唯一合法配对键是 `toolCallId`，禁止按位置配对。
3. **终态收敛律（新增服务端承诺）**：任何终态帧（FINISHED/CANCELLED/ERROR/MAX_ITERS/WAITING_INPUT）发出前，该轮所有已开启的 toolCallId 必须已闭合——正常结果帧，或由 executor 兜底补发 `TOOL_RESULT(state=ABORTED/INTERRUPTED)`。未闭合即发终态 = 服务端违例。这是治"幽灵运行卡"的根。
4. **迭代显式化**：帧统一携带 `iter`（迭代序，1 起，成功模型调用边界递增；由 middleware seam 计数，重试不递增）。旧帧无 `iter` 时客户端按启发式降级（结果批之后的新工具调用 = 新迭代）。
5. **文本分层**：TEXT_DELTA 携带 `phase=final|intermediate`，中间叙述不进最终答案区。

### 11.4 客户端状态机（渲染文法）

1. **ToolCard 生命周期唯一绑定 toolCallId**：`RUNNING → SUCCESS | FAILED | ABORTED | WAITING_INPUT`；终态帧到达时仍 RUNNING 的卡按 ABORTED 自愈收敛并标记违例（console + 上报），**任何路径都不允许无限转圈**。
2. **分组规则**：以服务端 `iter` 字段优先建组；启发式仅作旧帧兼容降级。
3. **对账钩子**：TURN_FINISHED 时本地对账（运行卡清零、迭代序连续、每卡有终态），违例显式上报——顺序问题从"看着怪"变成可定位的违例事件。
4. **双通道裁决衔接**：实时通道走 SSE 帧（受本规范约束）；回放/刷新走 trace v2 树（§7.1，天然满足顺序语义）。两通道渲染同一棵逻辑树。

### 11.5 视觉契约

1. 迭代组分隔可见（"第 N 步推理"），思考/工具卡纵向归属各组；**运行中的卡只允许出现在最后一个迭代组**——前置组出现运行卡即为违例的可视化呈现；
2. HITL 等待是显式状态（"等待输入"标签），不是 RUNNING 转圈；
3. 中间叙述渲染为迭代组内轻量注记，不进答案区；
4. 时间轴视角（§7.1 timeline）与顺序视角共用同一分组模型，两视图切换不改变因果序语义。

---

> 配套排期与每期 DoD：[agent-observability-development-plan.md](agent-observability-development-plan.md)。渲染顺序规范的落地任务在 O1（服务端不变式 3/4/5）与 O2（客户端状态机/视觉契约）。
