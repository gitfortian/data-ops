# 数据智能体业务流程梳理（as-is）

> 定位：以代码为准的**整链路业务流**说明——从「本体建模」到「自然语言提问」到「最终结果输出」。
> 技术实现细节看 `docs/agent/data-agent-technical-architecture.md`；历史演进看 `data-agent-development-plan.md`。
> 适用对象：`yak-ops-business-ontology` / `yak-ops-business-semantic` / `yak-ops-business-dataset` / `yak-ops-business-agent` / `yak-ops-ui`（ai-agent、semantic-console、ontology 工作台）。

## 0. 整链路总览

```text
① 本体建模            ② 数据集与锚定       ③ 语义层                 ④ 智能体会话          ⑤ 工具执行/证据         ⑥ 结果输出
ontology 工作台  ──▶  dataset 表 +         SemanticQuery DSL  ──▶  用户提问 POST         run_semantic_query  ──▶  SSE 事件流
对象/属性/指标/        object.dataset_id     Guard 五项校验      ──▶  /chat/turns           run_dataset_query        ──▶  前端渲染
关系/术语/函数        （物理字段锚定 /      编译（A/B 两路线）      入库 QUEUED        ──▶  白名单/只读/留痕          思考卡｜正文｜工具卡
    │                 物理四元组）        → 参数化执行            调度器认领          request_clarification      ｜澄清｜口径卡｜回合统计
PUBLISHED 发布   ──▶   ONLINE 可见          → QueryResult+口径    ──▶  ReAct 推理           save_analysis_report    ──▶  报告保存/审计追溯
allowed_operators      只读审计             caliber 附尾           LLM→Thinking→Tool         │                       │
状态机 DRAFT→PUB              │                                    ──▶ 证据落 query_log      └──▶ 报告/trace v2 可查
```

核心原则（贯穿全链路）：

- **本体是查询的地基，发布即承诺**：只有 `PUBLISHED` 对象/属性/指标进入查询面，DRAFT/DEPRECATED 不可见。
- **结构化分层**：LLM 只负责"问什么语言"与"调用哪个工具"，SQL 只是语义层编译产物——**模型永不接触物理 SQL 拼接**。
- **单一取数执行面**（统一计划 T5 后）：语义层收敛为「守卫 + 结构翻译」壳，`SemanticQuery` 一律经 `DatasetAnchoredQueryGateway` 翻译为 `DatasetQueryRequest` 下沉 dataset 运行时执行（dataset 是唯一 SQL 生产面）；原 A 路线 `SemanticSqlCompiler` 已随 T5 移除；明细/直接取数走 `run_dataset_query` 结构化规格。
- **每次取数必然留痕**：query_log（语义含 semantic_ref）、执行性能、步骤级观测（`yak_agent_step`）三层证据都可追溯到具体一次回答。

---

## ① 本体建模（ontology 模块）

**入口**：前端「本体建模工作台」`/ontology`（`yak-ops-ui/pages/ontology/index.tsx`）；REST 前缀 `/api/v1/ontology`（建模/发布需 `ontology:model:manage`，读需 `ontology:model:read`，`ontology/OntologyPermissionCode`）。

### 1.1 建模对象（八张表，对应 8 类实体）

| 概念 | 表 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| 业务域 | `yak_onto_domain` | — | 域分组（如"订单域"） |
| **对象**（业务概念） | `yak_onto_object` | `status`(DRAFT/PUBLISHED/DEPRECATED)、`dataset_id` 锚定、`identify_by` 主键属性集 | 查询的统计主体（如"销售订单"） |
| **属性** | `yak_onto_attribute` | `role`(DIMENSION/MEASURE)、`dataType`、`datasetFieldId`、`allowedOperators`(物化白名单)、`requiresJson`、`aiContext` | 对象字段的语义化定义；`isTime` 时间维标记 |
| **指标** | `yak_onto_metric` | `metricType`(ATOMIC/DERIVED/COMPOSITE)、`aggregation`(SUM/AVG/COUNT/COUNT_DISTINCT/MAX/MIN)、`derivationJson`(经 `expression_json` 列)、`businessFilterJson`、`status` | 度量口径的第一语义 |
| 关系 | `yak_onto_relation` | `joinType`(DIRECT/INDIRECT)、`joinConditions`、`source`(MANUAL/INFERRED) | MVP 仅 DIRECT 参与 JOIN 编译 |
| 术语 | `yak_onto_glossary` | `term`、`targetType`(OBJECT/METRIC/ATTRIBUTE)、`targetCode`、`aiContext` | 自然语言→概念的匹配词表（同义词/句式） |
| 函数 | `yak_onto_function` | `bindingType`(v1 仅 SQL_TEMPLATE)、`sqlTemplate`、`useRule` | "过程计算"型取数（FUNC-1，只读强制） |
| 动作 | `yak_onto_action` | `riskLevel`(HIGH/MED/LOW) | **仅建模登记，执行端明确拒绝**（ACT-1） |

### 1.2 能力物化（allowed_operators）

属性保存时 `OntologyEntityManager.doReplaceAttributes` → `OntologyCapabilityDeriver.apply(po)`：按数据类型物化算子白名单并回填 `isTime`：

- 数值：`eq/neq/gt/gte/lte/lt/in/not_in/between`；字符串：`eq/neq/in/not_in/like`；
- 时间维：`eq/between` + 指标算子 `yoy/mom`；boolean：仅 `eq`。

物化结果落入 `allowedOperators`（JSON 列），是**属性级算子法律**——语义守卫与 dataset 通道都据此拒绝白名单外算子（`SemanticQueryGuard` 检查 `attr.allowedOperators().contains(filter.op())`）。

### 1.3 发布即承诺（DRAFT → PUBLISHED）

每个实体有 `POST /{id}/publish` 与 `POST /{id}/deprecate`（controller 层）。**对象发布校验**（`OntologyEntityManager.publish`）保证：

- 仅 DRAFT 可发布；空属性、无主键属性拒绝；
- PROCESS 概念必须含 `isTime=1` 时间维与 `role=MEASURE` 事实属性；
- 每个属性要么锚定 `dataset_field_id`、要么物理四元组 `ds_id/db_name/tbl_name/col_name` 齐备（A2 映射完整性）；
- `identify_by` 必须是已登记属性；通过后置 PUBLISHED 并记 `PUBLISH` 审计。

**指标按类型分闸发布**（`OntologyMetricManager.publish`）：

- ATOMIC：统计主体必须 PROCESS、base_attribute 必须属于主体且 role=MEASURE；
- DERIVED：`derivationJson={atomicCode, timeCycle?, modifier?}`，引用的原子指标必须已 PUBLISHED 且同统计主体（派生沿"派生→原子→字段"口径链）；
- COMPOSITE：`derivationJson={refs[...], formula}`，禁自引用、`dependsOn` 递归防环 DAG——**当前仅登记，执行端拒绝**（`COMPOSITE_NOT_EXECUTABLE`）。

**已发布不可静默改**（GOV-6）：已发布对象换属性集走「变更单」通道（`change-orders`），apply 原子生效并按需 `bumpVersionsViaChangeOrder` 递增指标版本。

---

## ② 数据集与锚定（dataset 模块）

**对象锚定**：`yak_onto_object.dataset_id` 指向 dataset 数据集；属性的 `dataset_field_id` 指向该数据集字段。锚定在 DRAFT 期绑定（`bindAnchor`），发布后切换受控。

**数据可查前提**：数据集状态必须 `ONLINE`（`DatasetStatus`）——`DatasetQueryCoordinator` 在查询入口对 OFFLINE 直接拒绝「只有 ONLINE Dataset 可以查询」（agent 网关将其标记为 `[DATASET_OFFLINE]` 并落 REJECTED 留痕）。

**dataset 查询运行时**（详情见架构文档 §6）：`DatasetQueryService.query` — 护栏（Governor：短 TTL 缓存/并发信号量/审计留痕）— 协调器（ONLINE 检查、版本解析、分阶段留痕）— 编译器（只读强制 + 算子/时间桶/聚合编译）— 适配器（SQL_QUERY / QUERY_REVISION）— `SqlExecutionRuntime` 执行。

---

## ③ 语义层：把业务提问变成可执行查询（semantic 模块）

**入口**：REST `/api/v1/semantic/**`（调试台 `semantic-console` 页面），以及智能体工具（Bean 直调，图 ④⑤ 详述）。

### 3.1 结构化 DSL（SemanticQuery）

```text
SemanticQuery(objectCode, dimensions[], metrics[{code, timeGrain?}], filters[{logicalName,op,value}],
              timeRange{field?,start?,end?}?, limit?)
```

- 字段引用一律用**逻辑名**（`logicalName`），物理列名只在编译管线内部出现；
- 指标引用 `metricCode`，timeGrain 支持 DAY/WEEK/MONTH/QUARTER/YEAR，yoy/mom 以 `comparePeriod` 出现（MET-3，编译器生成当期/上期/同比三列，不生成窗口函数）；
- 它同时是 **MCP/Agent/调试台的公共契约**（`api` 包零依赖）。

### 3.2 守卫（Guard，五项检查）

`POST /api/v1/semantic/guard/validate` 或 Bean 直调：对象/属性/指标存在且 PUBLISHED → 过滤算子 ∈ allowed_operators → requires 约束求值（`ATTR_VALUE_DOMAIN` / `METRIC_APPLIES_TO`）→ 权限（`semantic:query`）→ 失败返回 `GuardReport{passed:false, violations[], suggestions[]}`（200 + GuardReport，业务语义非 HTTP 错误）。

### 3.3 执行（统一计划 T5：单执行面 = dataset 锚定通道）

`SemanticQueryManager.execute`：快照装载（`DefinitionSnapshot`，只含 PUBLISHED、编译期冻结）→ Guard → 对象锚定检查：

- **对象必须锚定数据集**（`ObjectDefinition.datasetId` 非空）；未锚定显式拒绝 `[NOT_ANCHORED]`（统一计划：全量对象锚定，不静默降级）。
- **结构翻译**：`gateway/dataset/DatasetAnchoredQueryGateway` 把 `SemanticQuery` 翻译为 `DatasetQueryRequest`（维度/指标聚合/过滤/时间桶/JOIN/businessFilter/MET-3 对比）下沉 dataset 运行时执行；dry-run 与 execute 共用同一翻译管线，产物一致。
- **守卫统一前置**：存在性/算子白名单/requires/权限（`semantic:query`）在所有执行路径一致生效；能力未承接时显式拒绝（`[DATASET_CHANNEL_UNSUPPORTED]`），不静默错数。

**返回**：`QueryResult{columns, rows, caliber, compiledSql, elapsedMs, truncated}`。`caliber`（口径：指标编码/版本/时间粒度/业务过滤摘要）**必须附尾**——"口径透明"是缺省即缺陷的合同项。

---

## ④ 智能体会话：自然语言提问进入推理

### 4.1 用户交互入口

前端 `yak-ops-ui/pages/ai-agent/index.tsx`（Tabs：对话 / 查询审计 / 配置 / 分析报告）→ `POST /api/v1/agent/chat/turns`（`{sessionId, message, toolResults?}`，需 `agent:chat:run`）→ 返回 `turnId` 立即应答；SSE 订阅同轮 `GET /chat/turns/{turnId}/events`。

### 4.2 提交侧（HTTP 线程绝不推理）

`AgentChatService.submitTurn`：归属校验（首访自动绑定 `yak_agent_session`）→ 会话级 stripe 串行化 → 单飞检查 `hasActiveTurn`（**QUEUED/RUNNING/WAITING_INPUT 任一存在即拒绝**）→ 输入投影落 `yak_agent_turn`（QUEUED）→ 唤醒调度器 → 返回 turnId。

### 4.3 调度与执行

`AgentTurnDispatcher`（固定池 `yak-agent-turn-worker`，启动时先清孤儿 RUNNING→INTERRUPTED，`@Scheduled(2s)` 批量出队）→ `AgentTurnExecutor.execute`：CAS `claimForExecution`（QUEUED→RUNNING）→ 解码输入 → 驱动 `AgentRuntime` 的 ReActAgent 推理 → 逐帧落 `yak_agent_turn_event` 投递日志 → 终态收敛。

### 4.4 推理（ReAct：Thinking → Tool 循环，最多 maxIters=10 轮）

模型（OpenAI 协议，`openAiModel()`，stream=true）经中间件洋葱链：

```text
LlmResilience（单次硬超时/重试分类/调用级记账）
  → ToolAudit（工具零侵入落 step）
  → SystemPromptAssembly（能力域提示词贡献者按序追加）
  → LongTermMemoryPrompt（长期记忆召回注入）
  → Compaction（跨轮上下文压缩，防超窗静默失败）
```

模型输出经 `AgentEventCodec` 防腐映射为领域事件（见 ⑥），其中 `GenerateReason.TOOL_SUSPENDED` → **HITL 反问挂起**（`request_clarification` 工具，`CLARIFY_REQUESTED` 事件，轮转 `WAITING_INPUT`）。

---

## ⑤ 工具执行与证据（toolset → gateway）

### 5.1 工具全集（12 个，`@Tool` 薄壳 + 能力域路由）

| 工具 | 命名 | 能力域 | 条件 |
| --- | --- | --- | --- |
| 语义取数 | `run_semantic_query` | 聚合/口径（指标、跨对象） | `yak.semantic.enabled` |
| 概念检索 | `search_concepts` | 语义检索、消除术语歧义 | 同上 |
| 对象 schema | `get_object_schema` | 概念卡（对象+属性+算子+指标口径） | 同上 |
| 函数执行 | `run_function` | 过程计算（FUNC-1，只读模板） | 同上 |
| 实例查询 | `get_object_instance` | 对象实例态取数 | 同上 |
| 数据集明细 | `run_dataset_query` | 分组聚合/过滤/排序（结构化规格） | 常驻 |
| 数据集目录 | `list_datasets` / `get_dataset_fields` | 目录/字段发现 | 常驻 |
| 当前日期 | `current_date_info` | 相对时间（"上个月"）基准 | 常驻 |
| 澄清反问 | `request_clarification` | HITL：问题不明确时打断向人提问 | 常驻 |
| Python 分析 | `analyze_with_python` | 本地进程计算（信号量限流/超时强杀/产物清理） | `yak.agent.python.enabled=true` |
| 报告保存 | `save_analysis_report` | 结论落 `yak_agent_report` | 常驻 |

**能力域路由**（`CapabilityRoutingPromptContributor` 注入提示词，指导模型选工具）：口径二义 → `search_concepts`/澄清；聚合指标 → `run_semantic_query`；明细 → `run_dataset_query`；实例 → `get_object_instance`；过程计算 → `run_function`；相对时间 → `current_date_info`。

### 5.2 工具 → 网关 → 数据面（证据同边界落账）

```text
run_semantic_query ─▶ gateway.SemanticGateway(DataSourceSemanticGatewayAdapter)
                        └─▶ semantic api/SemanticQueryManager（Bean 直调，不过 HTTP）
                        └─▶ 每次执行恰落一条 query_log（status=SUCCESS|FAILED|REJECTED，含 semantic_ref）

run_dataset_query  ─▶ gateway.DatasetQueryGateway
                        └─▶ FieldWhitelistValidator（字段白名单前置，拒绝带 [FIELD_WHITELIST_REJECTED]）
                        └─▶ query_log 同边界落账（OFFLINE/白名单拒绝 → REJECTED）

run_function       ─▶ gateway.SemanticGateway.executeFunction（只读兜底在执行边界统一完成）
get_object_instance ─▶ gateway（对象主键快照查询）
```

**模型永不接触 SQL**：工具返回结构化结果文本；SQL 只存在于编译产物与执行审计。**工具失败/拒绝以精确错误文本回喂模型同一轮自纠**，不升级为整流终止；致命错误（超时/提供方错误）才终态化并带 `errorCode`。

### 5.3 HITL 澄清闭环

模型发现提问歧义 → `request_clarification`（挂起 `WAITING_INPUT`，事件落 CLARIFY_REQUESTED）→ 前端展示澄清选项卡 → 用户作答 → `POST /chat/turns`（toolResults）→ `submitResume`：**校验 feedback.toolCallId 必须匹配反问帧**（防伪造/并发第二反问）→ CAS 回 QUEUED → 续跑同一轮。

---

## ⑥ 结果输出：SSE 事件流 → 前端渲染

### 6.1 SSE 帧契约（官方 AG-UI 帧，官方化 v2）

内部领域类型（10 种，`ChatTurnEvent.TurnEventType`）：`TURN_STARTED / THINKING_DELTA / TEXT_DELTA / TOOL_CALL / TOOL_RESULT / CLARIFY_REQUESTED / TURN_FINISHED / TURN_CANCELLED / EXCEEDED_MAX_ITERS / ERROR`；线上事件名经 `ChatTurnToAguiMapper` 映射为官方 AG-UI 标准：

```text
REASONING_MESSAGE_CONTENT（思考增量）/ TEXT_MESSAGE_CONTENT（正文增量）
/ TOOL_CALL_START / TOOL_CALL_END + TOOL_CALL_RESULT（工具结果双帧）
/ RUN_FINISHED / RUN_ERROR / CUSTOM(customName=clarify_requested|turn_cancelled|exceeded_max_iters)
```

- 帧格式：`data: {"type":"...", ...}`（类型在 data 内，官方 `AguiEvent` + `AguiEventEncoder`，无 `event:` 名）+ `id:{eventId}`（yak-ops 续播扩展头）；`AgentEventStreamTailer` 按 event_id 游标增量补发（断开后用 `cursor`/`Last-Event-ID` 续播，WAITING_INPUT 轮从 0 重放）；
- 心跳 `:ping` 3s；终态帧兜底 RUN_FINISHED/RUN_ERROR；未闭合工具调用在终态前强制闭合（`TOOL_CALL_END ... toolStatus=ABORTED`）；
- yak-ops 权威计时/迭代序/文本分层（iter/phase/thinkingElapsedMs/startedAt/durationMs/toolStatus/elapsedMs/totalTokens/errorCode）经官方 `rawEvent` 槽透传。

### 6.2 前端渲染（五态连接状态机）

`stream-runtime` idle/connecting/streaming/reconnecting/error + 60s 看门狗，指数退避重连（>3 次上抛）。页面按 AG-UI 事件分发：

- REASONING_MESSAGE_CONTENT → 思考卡（antd-x ThoughtChain，服务端 thinkingElapsedMs）；TEXT_MESSAGE_CONTENT → 正文打字机；
- TOOL_CALL_START/TOOL_CALL_END/TOOL_CALL_RESULT → 工具卡（startedAt 权威计时，按 toolCallId 原位闭合，END+RESULT 双帧合流）；
- CUSTOM/customName=clarify_requested → 澄清选项卡（用户作答走 toolResults 续跑）；
- RUN_FINISHED → 收敛工具卡 + 用时统计。

### 6.3 结果呈现与追溯

- **口径卡**：`run_semantic_query` 结果尾随「本次口径：…」（正则提取展示）——回答与口径同屏可查；
- **回合统计条**：已完成 N 步 · 用时；本回合 工具调用/耗时/tokens；`!streaming` 时提供「重新生成」（重发上一条用户消息，不建分支）；
- **报告**：`save_analysis_report` → `yak_agent_report`，前端「分析报告」Tab 分页/详情/删除；
- **审计**：「查询审计」Tab（query_log 分页）；历史回放轮次 trace 由事件投递日志重建（`AgentTurnEventRepository.reconstructTrace`）；推理级观测已迁官方 OTel（`GET /turns/{turnId}/trace` 已删除）。

---

## ⑦ 全链路留痕（一次提问的可追溯性）

| 证据层 | 载体 | 写入方 | 内容 |
| --- | --- | --- | --- |
| 执行证据 | `yak_agent_query_log` | dataset/semantic gateway | sessionId、queryId、requestJson、semanticRef、status(SUCCESS/FAILED/REJECTED)、errors、rows、elapsed |
| 性能留痕 | `yak_dataset_query_performance` | DatasetQueryPerformanceRecorder | 每尝试恰一条：阶段耗时、SQL(脱敏)、sourceType、queryId |
| 步骤观测 | `yak_agent_step` | AgentStepRecorder（唯一写入口） | 9 种 kind：LLM_CALL/TOOL_CALL/GUARD/HITL/COMPACTION/TURN_SUMMARY/MEMORY_FLUSH/MEMORY_RECALL(+/MEMORY_CONSOLIDATE 预留) |
| 轮次事实 | `yak_agent_turn` + `yak_agent_turn_event` | AgentTurnExecutor | 状态机 CAS 转移；事件投递日志（可重建投影） |
| 会话真相 | `yak_agent_session` + agentscope_sessions(StateStore) | AgentChatService / AgentRuntime | 归属与标题；对话消息历史与推理状态 |

**北极星级验收（开发计划）**：任意回答可由 `yak_agent_step` 重建完整证据链；D3 问答通过率/ D4 Token 与推理步数降幅 的度量依赖每轮次的可观测数据。

---

## 附：一次"自然语言提问→最终结果"的端到端时序

```text
用户:「上个月华东区域销售额是多少，同比呢？」
 1 前端 POST /api/v1/agent/chat/turns → turnId（HTTP 线程不推理）
 2 Dispatcher 出队 → AgentTurnExecutor CAS RUNNING
 3 AgentRuntime 组装上下文（会话历史 + 记忆召回 + 能力域提示词）
 4 LLM 依次输出：THINKING → TOOL_CALL(request_clarification|run_semantic_query)
 5 模型调 run_semantic_query{object=销售订单, metrics=[销售额{timeGrain=MONTH, comparePeriod=YOY}],
                              filters=[区域=华东], timeRange=[上月]}
 6 SemanticQueryManager：快照 → Guard（白名单/requires/权限）→ 对象锚定检查
     └─ DatasetAnchoredQueryGateway（结构翻译）→ dataset 运行时（护栏/编译/执行）
 7 结果 QueryResult + caliber → 工具回喂 LLM → LLM 组织自然语言回答 + 口径卡
 8 工具/回合证据落 query_log / step / performance（同边界）
 9 TURN_FINISHED → 前端渲染：思考卡→工具卡(口径)→正文→回合统计条
10 用户可「重新生成」，或让模型 save_analysis_report 落报告
```

（本文档是 as-is 梳理，未体现 `analyze_with_python`（默认关）、COMPOSITE 指标、行级权限真实实现（三期）等未启用/未落地项的深入介绍。）