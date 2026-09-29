# yak-ops Agent 模块功能总结

> 日期：2026-08-27 | 模块：`data-ops-business-agent` + `data-ops-ui` ai-agent 页面
> 定位：Yak Ops 平台的**自然语言数据分析智能体**，业务人员用自然语言提问，Agent 在平台数据集与语义层上完成取数、统计与报告生成，以 SSE 流式对话返回结果与证据。

---

## 一、技术栈

| 层 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3 + MyBatis-Plus + Flyway |
| Agent 框架 | AgentScope 2.0.2（ReActAgent） |
| 模型接入 | OpenAI 兼容协议（`OpenAIChatModel`） |
| 响应式 | Project Reactor（runtime 内部事件流） |
| 数据库 | MySQL（StateStore 复用平台共享数据源，auto-DDL） |
| 前端 | React + TypeScript + Ant Design |

---

## 二、架构总览

```text
io.yak.ops.business.agent
├── controller/v1        # HTTP 入口 + SSE 端点 + DTO/VO
├── conversation         # 会话 command side：提交/执行分离、流式编排、HITL 恢复、生命周期
│   └── query            # 会话列表 / 历史 / 审计 read model
├── runtime              # AgentScope 边界：ReActAgent 组装、中间件链、事件→domain 映射
├── toolset              # 12 个业务工具（@Tool 薄壳）
├── catalog              # 数据集目录视图：schema 格式化、字段白名单校验、查询参数解析
├── gateway              # outbound：Dataset / Semantic / Python 网关
├── report               # 报告保存与管理
├── repository           # 持久化契约 + 适配器（7 个 Repository）
│   └── support          # JSON codec（TurnInput / TurnEvent / QueryRequest）
├── dao                  # MyBatis mapper + PO（7 张表）
├── domain               # framework-free 核心领域对象
├── telemetry            # 步骤级执行记录（yak_agent_step 唯一写入口）
└── config               # 模块配置、条件装配
```

**稳定 Application Facade（仅 3 个 @Service）**：

```text
conversation.AgentChatService              # 提交/恢复/取消/会话管理
conversation.query.AgentSessionQueryService # 会话列表/历史/审计 read model
report.AgentReportService                  # 报告保存/分页/详情/删除
```

---

## 三、已实现功能清单

### 3.1 提交/执行分离架构

| 能力 | 实现 |
| --- | --- |
| 提交即返回 | `POST /chat/turns` 验证+落库 QUEUED 后立即返回 `turnId`，HTTP 线程不做任何推理 |
| 后台异步执行 | `AgentTurnDispatcher` 调度 + `AgentTurnExecutor` 执行，线程池消费（默认 2 worker） |
| DB 行作队列 | `yak_agent_turn` 表，QUEUED→RUNNING 条件 UPDATE 抢占（天然幂等） |
| SSE 事件订阅 | `GET /chat/turns/{turnId}/events`，按 turnId 订阅事件流，支持 `Last-Event-ID` 断线续播 |
| 事件持久化 | `AgentTurnEventRepository` 逐帧落库 `yak_agent_turn_event`，SSE 只是可水平扩展的订阅者 |
| 尾随拉取 | `AgentEventStreamTailer` 按游标增量补发（`sseTailPollMillis=200ms`，`replayBatchSize=100`） |
| 即时唤醒 | 提交侧 `turnDispatcher.kick()` 即时唤醒调度器，不等轮询周期 |
| 启动清障 | `AgentTurnDispatcher.bootstrap()` 将遗留 RUNNING 孤儿置为 INTERRUPTED |
| 会话维提交串行化 | `SUBMIT_STRIPES` ConcurrentHashMap 保证同会话 check-then-insert 竞态安全 |

### 3.2 流式对话（SSE · 官方 AG-UI 帧，官方化 v2）

| 能力 | 实现 |
| --- | --- |
| SSE 流式推送 | `AgentStreamCoordinator` 管理 SseEmitter 生命周期 |
| 心跳保活 | 注释行 `:ping` 3s 保活 |
| 反缓冲 | `X-Accel-Buffering: no`、`Cache-Control: no-cache`、`Connection: keep-alive`、`Content-Encoding: identity` |
| 线上帧 | 官方 `AguiEvent` 模型 + `AguiEventEncoder`：`data: {"type":"REASONING_MESSAGE_CONTENT"|"TEXT_MESSAGE_CONTENT"|"TOOL_CALL_START"|"TOOL_CALL_END"|"TOOL_CALL_RESULT"|"RUN_FINISHED"|"RUN_ERROR"|"CUSTOM", ...}`（类型在 data 内，无 `event:` 名） |
| 映射器 | `runtime/ChatTurnToAguiMapper`：领域 `ChatTurnEvent` → 官方 `AguiEvent`（纯函数；threadId=sessionId、runId=turnId、messageId=assistantMessageId）；实时与日志重放同一映射，字节一致 |
| TOOL_RESULT 双帧 | 官方语义 `TOOL_CALL_END`（闭合+耗时/状态）+ `TOOL_CALL_RESULT`（结果内容） |
| 扩展透传 | yak-ops 权威计时/迭代序/文本分层（iter/phase/thinkingElapsedMs/startedAt/durationMs/toolStatus/elapsedMs/totalTokens/errorCode）经官方 `rawEvent` 槽透传 |
| HITL/终态 | `CUSTOM(customName=clarify_requested/turn_cancelled/exceeded_max_iters)` 官方扩展点承载 |
| 断线续播 | 帧头 `id:{eventId}`（投递日志自增主键）= Last-Event-ID 游标（yak-ops 对 AG-UI 的加法扩展头）；`cursor` 查询参数等价 |
| 事件去重 | 已流式推送正文时，`AgentResultEvent` 整段全文不再重复下发 |
| Token 统计 | 从 `ModelCallEndEvent.chatUsage` 累计 inputTokens + outputTokens，在 `RUN_FINISHED` 中携带 |
| 断连处理 | 断连仅取消推送并终止推理，已发生事实不回滚 |
| 终态收敛律 | 任何终态帧发出前，未闭合工具调用由执行器兜底补发 `TOOL_CALL_END(toolStatus=ABORTED)` |
| 服务端权威计时 | `iter`/`thinkingElapsedMs`/`startedAt`/`durationMs`/`elapsedMs`/`toolStatus` 全部服务端计算 |

> 注：`TURN_STARTED`/`RUN_STARTED` 有映射分支与前端处理分支，但当前无事件生产者主动发出（首帧为推理的第一个增量），属已知轻微契约冗余。
> 详见 `docs/agent/agent-agui-official-v2-plan.md`。

### 3.3 LLM 调用工程

| 能力 | 实现 |
| --- | --- |
| 整轮硬超时 | `withTurnTimeout()` 300s 超时闸门，超时映射为 `[TURN_TIMEOUT]` 错误 |
| 单次调用超时 | `LlmResilienceMiddleware` 120s 单次 LLM 调用硬超时 |
| 错误分类重试 | 5xx/网络错误指数退避 + jitter 重试 ≤3 次，每次尝试独立落 step 记录 |
| TimeoutError 不重试 | 超时错误明确不重试 |
| 模型调用记账 | 每次 LLM 调用独立落 `yak_agent_step` 记录（promptTokens/completionTokens/latencyMs/status） |

### 3.4 人机反问（HITL）

| 能力 | 实现 |
| --- | --- |
| 反问挂起 | `RequestClarificationTool`（`externalTool=true`）使框架抛出 `ToolSuspendException`，挂起当前轮 |
| 状态机落库 | `AgentTurnRepository.markWaitingInput()` RUNNING→WAITING_INPUT |
| 反问事件 | `AgentEventCodec` 检测 `GenerateReason.TOOL_SUSPENDED` → 映射为 `CLARIFY_REQUESTED` |
| 恢复推理 | `submitResume()` 携带匹配的 `toolCallId` + `toolName` + `output`，CAS 回 QUEUED 续跑同一 turnId |
| 单飞约束 | 同一时刻至多一个 pending 反问；`latestWaitingTurnId()` 定位目标 |
| 孤儿恢复 | `enablePendingToolRecovery(true)` — 用户放弃应答时自动补合成结果，避免会话永久卡死 |

### 3.5 长对话压缩（Compaction）

| 能力 | 实现 |
| --- | --- |
| 框架集成 | `CompactionMiddleware`（AgentScope Harness）注册到 ReActAgent 中间件链 |
| 显式配置 | `AgentProperties.Compaction`：`contextWindowSize`(32000) / `triggerMessages`(50) / `reserved`(4000) / `keepMessages`(20) |
| 动态触发 | `triggerTokens = contextWindowSize - reserved`，消息数/token 数双维度触发 |
| 摘要收敛 | 超窗时由 LLM 摘要压缩历史对话，保留 `keepMessages` 条尾部消息 |
| 降级容错 | 压缩失败仅 warn 日志，不阻断推理；workspace 初始化失败降级关闭压缩 |

### 3.6 数据集发现与查询（Dataset 工具集）

| 工具 | 功能 |
| --- | --- |
| `list_datasets` | 列出全部 ONLINE 数据集（id、名称、描述），由 `DatasetViewFormatter` 格式化 |
| `get_dataset_fields` | 获取指定数据集字段清单（fieldId、显示名、类型、维度/度量角色、描述） |
| `run_dataset_query` | 结构化聚合查询：维度分组 + 指标聚合（SUM/AVG/COUNT/COUNT_DISTINCT/MAX/MIN）+ 过滤（13 种操作符）+ 排序 + 行数上限 |

**查询安全链路**：

1. 模型产出扁平 mini 语法参数 → `QuerySpecParser` 解析为结构化 `DatasetQuerySpec`
2. `FieldWhitelistValidator.requireKnownFields()` 校验所有 fieldId 存在于 ONLINE 数据集
3. 行数上限截断（`maxLimit=1000`，`defaultLimit=200`）
4. `DatasetQueryGateway` → `DatasetQueryService.query()` 执行
5. 每次执行（成功/失败）都落一条 `yak_agent_query_log`

OFFLINE 数据集一律拒绝查询，无绕行开关。

### 3.7 语义层查询（Semantic 工具集）

| 工具 | 功能 |
| --- | --- |
| `search_concepts` | 自然语言检索业务概念（业务对象、指标、术语），返回候选列表 |
| `get_object_schema` | 获取业务对象完整 Schema：属性清单（含可用算子）+ 指标口径说明 |
| `run_semantic_query` | 结构化语义查询：维度 + 指标（含时间粒度 TIME_GRAIN）+ 过滤 + 时间范围 + 行数上限 |

**条件装配**：`yak.semantic.enabled=true`（默认开启），关闭后 Agent 自动回退到 Dataset 工具集。

**语义层提示词预加载**：`SemanticPromptContributor` 将业务对象总览注入系统提示词（Schema 预加载，减少推理步数与 Token 消耗）。best-effort，失败静默降级。

**口径说明**：查询结果携带 `caliberSummary`（指标口径版本 + 时间粒度 + 单位）。

### 3.8 日期推算工具

| 工具 | 功能 |
| --- | --- |
| `current_date_info` | 获取当前日期事实（今天/昨天/明天/本周一/本月第一天/今年），支持可选时区参数（默认 Asia/Shanghai） |

### 3.9 Python 统计分析工具

| 工具 | 功能 |
| --- | --- |
| `analyze_with_python` | 执行 Python 代码做统计分析（pandas/numpy/scipy），数据必须内联 |

**安全控制**：

- 默认关闭（`yak.agent.python.enabled=false`），显式开启才装配
- 并发信号量限流（默认 `maxConcurrent=5`）
- 单次超时强杀（默认 `timeoutSeconds=30`）
- 临时目录创建 → 执行 → 清理（`Files.walk` 逆序删除）
- 进程执行卸载到 `boundedElastic`，避免阻塞事件循环

### 3.10 分析报告

| 能力 | 实现 |
| --- | --- |
| 保存报告 | `SaveAnalysisReportTool` → `AgentReportService.saveFromTool()` — 走 toolset→report corridor |
| 报告格式 | Markdown 正文 + ECharts 配置块 |
| 报告分页 | `POST /reports/page` — 按归属人 + 关键词搜索 |
| 报告详情 | `GET /reports/{reportId}` — 元数据与正文分两次读取 |
| 删除报告 | `DELETE /reports/{reportId}` — 逻辑删除（`is_deleted=1`） |
| 独立生命周期 | 删除会话不级联删除报告 |

### 3.11 查询审计

| 能力 | 实现 |
| --- | --- |
| 审计分页 | `POST /queries/page` — 按当前用户，可选会话/数据集过滤 |
| 全量留痕 | 每次 `run_dataset_query` 恰好落一条记录（成功/失败/拒绝），含 datasetId、queryId、请求投影、行数、耗时、状态 |
| 联表归属 | 审计查询经 `yak_agent_session` 联表限定 `user_id`，防止暴露他人查询痕迹 |

### 3.12 步骤级执行记录（Telemetry）

| 能力 | 实现 |
| --- | --- |
| 步骤记录 | `AgentStepRecorder` — `yak_agent_step` 唯一写入口 |
| 步骤类型 | `LLM_CALL` / `TOOL_CALL` / `GUARD` / `TURN_SUMMARY` |
| 错误分类 | `TIMEOUT` / `USER_ERROR` / `PROVIDER_ERROR` / `GUARD_REJECTED` |
| 失败也记账 | 记录失败只告警，不影响已发生的执行事实 |
| 文本截断 | 请求/响应摘要超过 8000 字符自动截断（首尾各 4000） |
| Token 汇总 | 轮次结束时 `TURN_SUMMARY` 记录 `totalTokens` |
| 工具审计 | `ToolAuditMiddleware` — 全部工具零侵入落步骤记录 |

### 3.13 消息树（消息模型 v2）

| 能力 | 实现 |
| --- | --- |
| 树状结构 | `yak_agent_message` 表，`parentId` 链接支撑编辑分叉 / regenerate / 分支导航 |
| 双写 | 对话真相在 AgentScope StateStore，消息树表承载产品化交互（流式追加 + 完成覆写） |
| 双写容错 | 写失败只告警，不影响主流程 |

### 3.14 Runtime 运行时

| 能力 | 实现 |
| --- | --- |
| ReActAgent 单例 | `AgentRuntime` 无状态单例，所有会话并发复用 |
| 懒组装 | 首次对话时才组装模型/工具/StateStore，配置缺失时应用照常启动 |
| 中间件链 | `LlmResilienceMiddleware` → `ToolAuditMiddleware` → `SystemPromptAssemblyMiddleware` → `CompactionMiddleware` |
| StateStore | `MysqlAgentStateStore`（官方扩展），复用平台共享数据源，auto-DDL |
| 事件防腐 | `AgentEventCodec` 穷尽已知 AgentScope 事件类型，未知事件降级为忽略 + debug 日志 |
| 历史投影 | `projectHistory()` 按轮次分组，只保留每轮最终回答文本，中间推理步骤不进入正文 |
| 系统提示词 | 基础提示词 + 各 `AgentSystemPromptContributor` 贡献段落（失败静默降级） |

---

## 四、REST API 清单

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/agent/chat/turns` | `agent:chat:run` | 提交推理轮次（立即返回 turnId） |
| GET | `/api/v1/agent/chat/turns/{turnId}/events` | `agent:chat:run` | SSE 订阅轮次事件流（支持 Last-Event-ID） |
| GET | `/api/v1/agent/sessions` | `agent:session:read` | 当前用户会话列表 |
| GET | `/api/v1/agent/sessions/{sessionId}/history` | `agent:session:read` | 回放会话历史 |
| PUT | `/api/v1/agent/sessions/{sessionId}` | `agent:session:update` | 重命名会话 |
| DELETE | `/api/v1/agent/sessions/{sessionId}` | `agent:session:delete` | 删除会话（报告保留） |
| POST | `/api/v1/agent/sessions/{sessionId}/cancel` | `agent:chat:run` | 停止生成 |
| POST | `/api/v1/agent/queries/page` | `agent:session:read` | 查询审计分页 |
| POST | `/api/v1/agent/reports/page` | `agent:report:read` | 报告分页 |
| GET | `/api/v1/agent/reports/{reportId}` | `agent:report:read` | 报告详情 |
| DELETE | `/api/v1/agent/reports/{reportId}` | `agent:report:delete` | 删除报告 |

---

## 五、业务工具清单（12 个）

| # | 工具名 | 子系统 | 说明 |
| --- | --- | --- | --- |
| 1 | `list_datasets` | catalog → gateway | 列出 ONLINE 数据集 |
| 2 | `get_dataset_fields` | catalog → gateway | 获取数据集字段清单 |
| 3 | `run_dataset_query` | catalog → gateway → query_log | 结构化聚合查询 |
| 4 | `search_concepts` | gateway (semantic) | 自然语言检索业务概念 |
| 5 | `get_object_schema` | gateway (semantic) | 获取业务对象 Schema |
| 6 | `run_semantic_query` | gateway (semantic) | 结构化语义查询 |
| 7 | `current_date_info` | 本地时钟 | 日期事实推算 |
| 8 | `analyze_with_python` | gateway (python) | Python 统计分析（默认关闭） |
| 9 | `request_clarification` | HITL 中断信号 | 人机反问（externalTool） |
| 10 | `save_analysis_report` | report (corridor) | 保存分析报告 |
| 11 | `SemanticPromptContributor` | toolset (prompt) | 语义层 Schema 预加载注入系统提示词 |
| 12 | `AgentSystemPromptContributor` | toolset (prompt) | 系统提示词贡献者 SPI 接口 |

---

## 六、数据库 Schema（7 张表 + 1 张外部表）

| 表 | Flyway | 用途 |
| --- | --- | --- |
| `yak_agent_session` | V1 | 会话身份/归属/标题 truth |
| `yak_agent_report` | V1 | 报告内容 truth（逻辑删除） |
| `yak_agent_query_log` | V1, V2 | 查询证据 trace truth |
| `yak_agent_step` | V4 | 步骤级执行记录（LLM/工具/守卫/轮次汇总） |
| `yak_agent_message` | V5 | 消息树（parentId 树状结构，产品化交互支撑） |
| `yak_agent_turn` | V6 | 推理轮次执行队列（QUEUED/RUNNING/WAITING_INPUT/终态） |
| `yak_agent_turn_event` | V6 | 轮次事件帧持久化（SSE 断线续播依据） |
| `agentscope_sessions` | auto-DDL | 官方 StateStore 管理表（对话消息与推理状态 truth） |

---

## 七、配置体系

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `yak.agent.enabled` | `false` | 模块总开关 |
| `yak.agent.model.provider` | `openai` | 模型提供商 |
| `yak.agent.model.name` | — | 模型名称 |
| `yak.agent.model.base-url` | — | API 地址 |
| `yak.agent.model.api-key` | — | API 密钥（环境变量注入） |
| `yak.agent.model.reasoning-effort` | — | 思维链级别（high/medium/low） |
| `yak.agent.chat.max-iters` | `10` | 最大推理轮数 |
| `yak.agent.chat.turn-timeout-seconds` | `300` | 单轮整体硬超时 |
| `yak.agent.chat.llm-call-timeout-seconds` | `120` | 单次模型调用硬超时 |
| `yak.agent.chat.llm-max-retries` | `3` | 可重试错误最大重试次数 |
| `yak.agent.compaction.enabled` | `true` | 长对话压缩开关 |
| `yak.agent.compaction.context-window-size` | `32000` | 模型上下文窗口（token 数） |
| `yak.agent.compaction.trigger-messages` | `50` | 触发压缩的消息数阈值 |
| `yak.agent.compaction.reserved` | `4000` | 预留系统/输出 token 数 |
| `yak.agent.compaction.keep-messages` | `20` | 压缩后保留的消息数 |
| `yak.agent.turn.worker-pool-size` | `2` | 后台执行线程池大小 |
| `yak.agent.turn.queue-poll-millis` | `2000` | 排队扫描周期 |
| `yak.agent.turn.sse-tail-poll-millis` | `200` | SSE 尾随拉取周期 |
| `yak.agent.turn.replay-batch-size` | `100` | 单次最大补发帧数 |
| `yak.agent.query.default-limit` | `200` | 查询默认行数 |
| `yak.agent.query.max-limit` | `1000` | 查询最大行数 |
| `yak.agent.python.enabled` | `false` | Python 工具开关 |
| `yak.agent.python.max-concurrent` | `5` | Python 并发上限 |
| `yak.agent.python.timeout-seconds` | `30` | Python 超时 |
| `yak.agent.cors-allowed-origin-patterns` | `http://*:8000,https://*:8000` | CORS 来源 |

---

## 八、前端（data-ops-ui）

### 文件结构

```text
src/pages/ai-agent/
├── index.tsx                    # 页面入口：会话列表 + 对话面板 + 报告/审计 Tab
├── types.ts                     # 前端类型定义（ChatTurnEvent / AgentSession / ToolFeedback 等）
└── components/
    ├── AuditTable.tsx            # 查询审计表格
    ├── MarkdownContent.tsx       # Markdown 渲染（含 ECharts 图表）
    ├── pageStyles.ts             # 页面样式常量
    └── ReportsTab.tsx            # 报告分页 Tab

src/services/agent/
├── api.ts                       # 后端 API 封装（agentSessionApi / agentReportApi / streamTurnEvents）
├── types.ts                     # API 类型定义
└── index.ts                     # 公开出口
```

### 前端能力

| 能力 | 说明 |
| --- | --- |
| 会话管理 | 列表、重命名、删除、新建（首轮自动创建） |
| SSE 流式对话 | POST 提交 → SSE 订阅事件流 → 逐帧渲染 |
| 思考过程展示 | `THINKING_DELTA` 事件可折叠展示推理过程 |
| 工具调用展示 | `TOOL_CALL` / `TOOL_RESULT` 事件展示工具名与结果 |
| HITL 反问 | `CLARIFY_REQUESTED` 事件触发反问 UI，用户输入后提交恢复 |
| 停止生成 | 调用 cancel 接口终止当前推理 |
| Markdown 渲染 | 支持 Markdown + ECharts 图表渲染 |
| 报告管理 | 分页查询、查看详情、删除 |
| 查询审计 | 分页查看取数留痕 |

---

## 九、测试覆盖（18 个测试文件，56 个测试用例）

### 架构守护测试（3 个）

| 测试 | 保护内容 |
| --- | --- |
| `AgentArchitectureTest` | 角色/stereotype/Core Domain 纯度/read-side 守卫 |
| `AgentDependencyBoundaryTest` | 包依赖矩阵/无环/SDK 白名单/@Service 白名单/禁止桶 |
| `AgentConditionalWiringTest` | 条件装配正确性 |

### 行为安全测试（12 个）

| 测试 | 保护内容 |
| --- | --- |
| `AgentChatServiceSubmitTest` | 提交侧归属校验、单飞约束、stripe 串行化 |
| `AgentSessionOwnerValidatorTest` | 归属校验先于一切读写 |
| `AgentTurnExecutorTest` | 执行器生命周期（claim/fail/complete） |
| `FieldWhitelistValidatorTest` | 字段白名单校验与自纠回喂 |
| `QuerySpecParserTest` | 查询参数解析 |
| `DatasetQueryGatewayTest` | 查询网关执行与留痕 |
| `AgentReportServiceTest` | 报告保存/分页/删除 |
| `AgentSessionQueryServiceTest` | 会话列表/历史 read model |
| `RequestClarificationToolTest` | HITL 反问工具 |
| `AgentEventCodecTest` | 事件编解码穷尽 |
| `AgentRuntimeHistoryProjectionTest` | 历史投影（中间步骤不进入正文） |
| `AgentRuntimeMapStreamTest` | 流映射去重（正文播完后全文不重复） |

### 集成测试（3 个）

| 测试 | 保护内容 |
| --- | --- |
| `AgentResumeIntegrationTest` | HITL 全链路：挂起 → CLARIFY_REQUESTED → 恢复 → 终态 |
| `CompactionIntegrationTest` | 多轮对话压缩：低阈值触发 → 摘要收敛 → StateStore 验证 |
| `LlmResilienceMiddlewareTest` | LLM 韧性：超时/重试/错误分类 |

---

## 十、安全模型

| 维度 | 机制 |
| --- | --- |
| 归属校验 | `AgentSessionOwnerValidator` — 会话归属校验先于一切读写 |
| 权限码 | 6 个权限码控制全部 REST 端点 |
| 字段白名单 | `FieldWhitelistValidator` — LLM 产出的字段引用必须经白名单校验 |
| OFFLINE 拒绝 | OFFLINE 数据集一律拒绝查询，无绕行开关 |
| HITL 状态机 | 单飞约束 + toolCallId 匹配 + 伪造/复用拒绝 |
| Python 执行 | 默认关闭 + 信号量 + 超时强杀 + 临时目录清理 |
| 密钥生命周期 | 只在 runtime 模型装配边界短暂存在：不入库、不进日志、不出现在 SSE/异常 |
| 断连语义 | 断连仅取消推送并终止推理，已发生事实不回滚不伪造 |
| 工具失败 | 工具执行错误以结构化结果回喂模型继续 ReAct 循环，不中断推理 |
| 净室实现 | 禁止 DataAgent（AGPL）衍生代码，第三方依赖仅允许 Apache-2.0 及兼容许可 |

---

## 十一、代码统计

| 维度 | 数量 |
| --- | --- |
| 后端 Java 源文件 | 93 个 |
| 前端源文件 | 9 个（页面 6 + 服务 3） |
| 测试文件 | 18 个 |
| 测试用例 | 56 个 |
| 数据库表 | 7 张（+ 1 张 StateStore 外部表） |
| Flyway 迁移 | V1-V6 |
| REST 端点 | 11 个 |
| 业务工具 | 12 个（含 2 个 Prompt 贡献者） |
| 权限码 | 6 个 |
| 配置项 | 20+ 个 |
