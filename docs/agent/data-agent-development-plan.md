# yak-ops 数据智能体统一开发计划（v1.2）

> 日期：2026-08-27（v1.1：确立"Agent 主线先行"；v1.2：并入 AgentScope 框架蒸馏审计，修正重复造轮子与过度设计，见第十章）
> 定位：**唯一权威排期文档**。统一此前两条并行线——`docs/agent-optimization-plan.md`（Agent 生产化：稳定性/可观测性/体验）与 `knowledge-docs/ontology/06、07`（本体+语义层建设与 MVP）——并结合代码实际进度校正后的完整开发计划。
> 输入材料：knowledge-docs 下 `agent/01-04`、`ontology/01-07` 全部蒸馏与方案；`docs/architecture/PROJECT_SCOPE.md` 项目空间契约。
> 场景前提：**内部数据平台，单模型 OpenAI 协议，不计费、不多租户、不托管多模型**——文中一切取舍均以此为前提。
> 适用对象：`yak-ops-business-agent` + `yak-ops-business-ontology` / `yak-ops-business-semantic` + `yak-ops-ui`（ai-agent 页面及待建本体页面）。

---

## 一、统一分析与评审结论

### 1.1 对现有方案的评审判定

| 文档 | 判定 | 说明 |
| --- | --- | --- |
| `agent/01-03` 三篇蒸馏 | 合理 | 作为方法论来源质量高，提炼出的"提交执行分离/步骤记录/闸门四件套/渲染管线"等模式均适用于单体场景 |
| `docs/agent-optimization-plan.md` | 方向正确，四处需修 | ①工作量整体偏乐观（详见 §5 各期修正）；②与本体线的关系未串联（验收实验依赖本计划的计量能力）；③完全未提 Project Space 对齐，存在返工风险；④两项与本体 MVP 方案重复（semantic_ref、审批续跑），本次合并去重 |
| `ontology/06 design-v1` | 合理 | A1~A8 设计公理扎实，Ossie 对齐克制（只做交换格式不做运行时依赖），Non-Goals 清晰防过度设计 |
| `ontology/07 mvp-v1` | 合理但进度假设过时 | WBS 按"全部从零开始"估算 27 PD；实际后端主干（DDL/编译器/Guard/M6 工具切换）已落地大半，剩余量集中在前端与验收 |

### 1.2 必须正视的三个事实（代码为准）

1. **M6 已提前合入**。原始方案（agent/04）要求"M6 语义切换必须在 L1 地基第一梯队完成后才合入"，实际 `feat(agent): switch to semantic tools behind yak.semantic.enabled (M6)` 已先落地。语义工具目前跑在和旧工具相同的同步 SSE 请求内——地基不稳则两条路径一起不稳。这是顺序债，由 Phase 1 稳定化偿还，且**实验期间保持 `yak.semantic.enabled` 按业务方开关控制，不建议全局默认开**。
2. **语义链路"建了但未验证"**。编译器、Guard、本体七张表后端已成体系，但前端建模工作台（M7）、查询调试台（M8）、订单域种子建模（M9）、对照验收实验（M11/D1~D5）全部未做。当前无法回答"走语义层是否真的更准更省"。
3. **验收实验硬依赖可观测性**。D4 指标（Token 平均下降 ≥30%、推理步数下降 ≥30%）的度量前提是**每次 LLM 调用独立计量**（现 `AgentStepRecorder.KIND_LLM_CALL` 常量已定义但无人调用）。所以"可观测性"在本计划中不是锦上添花，而是本体验收的前置条件——这是两条线最关键的咬合点。

### 1.3 现状盘点（截至 2026-08-27，以代码为准）

| 事项 | 状态 | 证据 |
| --- | --- | --- |
| 步骤级记录 `yak_agent_step`（V4 迁移 + Recorder） | ✅ 已落地 | 失败也记账、8000 字符截断、TURN_SUMMARY Token 汇总 |
| 每次 LLM 调用独立计量落一行 | ❌ 未接线 | `KIND_LLM_CALL` 定义了但无调用点 |
| 消息树 v2 双写（V5 迁移） | ✅ 后端到位 | `MessageTreeRepository` + 双写 |
| M6 语义工具切换（search_concepts / get_object_schema / run_semantic_query） | ✅ 已落地 | feature flag `yak.semantic.enabled`，可一键回退 |
| HITL 澄清暂停/恢复 | ✅ 已落地 | `RequestClarificationTool` + resume 集成测试 |
| SSE 心跳保活 | ✅ 已落地 | `AgentStreamCoordinator` heartbeat executor |
| 提交/执行分离（turn 持久化队列） | ❌ 未开始 | Controller 仍为同步 `POST /chat/stream`；step 表无 `turn_id` 列 |
| LLM 单次超时 + 指数退避重试 | ❌ 未开始 | `AgentProperties` 仅 `turnTimeoutSeconds=300` 整轮超时 |
| 结构化输出闭环（随机标签+回喂重试） | ❌ 未开始 | — |
| 断线续播 / 配置热更新 / Token 预算 / 日志截断器 | ❌ 未开始 | — |
| 前端渲染管线 / 连接状态机 / 错误映射表 | ❌ 未开始 | ai-agent 页面仍是 SSE 直驱动 setState |
| 本体层（7 张表 + CRUD + 能力派生 + Ossie 导出） | 🔶 后端骨架成型 | `V1__init_ontology.sql`、capability/export/glossary 包已建 |
| 语义层（Guard/结构翻译/schema/search API） | ✅ 已收敛（统一计划 T5） | `SemanticQueryManager` → Guard → `DatasetAnchoredQueryGateway`（唯一执行通道）；`SemanticSqlCompiler`/`JoinPathResolver`/`IdentifierGuard` 已随 T5 删除，`compile/` 仅留共享契约件（MetricDerivationExpander/RowPolicyProvider）。详见 `semantic-unify-plan.md` |
| 前端建模工作台（M7）/ 查询调试台（M8） | ❌ 未开始 | UI 无 ontology / semantic-console 页面 |
| 订单域种子建模（M9）/ 验收实验（M11） | ❌ 未开始 | 依赖 M7、依赖调用级计量 |

### 1.4 两份方案重复项合并裁决

| 项 | agent 方案位置 | 本体方案位置 | 裁决 |
| --- | --- | --- | --- |
| semantic_ref 溯源 | §5.5（P2） | MVP §7（M6 配套加列） | **前移并入 Phase 1**。M6 已上线而溯源字段缺席，属欠账非远期项 |
| 审批暂停续跑 | §6.7（P2 体验升级） | design §4 / MVP 二期外（NL2Query 执行确认场景） | 合并为一个工作项入 Phase 3（Agent 主线）：把现 HTTP 会话内 resume 升级为"中间态+恢复元数据落库"，同时服务语义 SQL 执行确认与通用 HITL |
| GuardReport 结构化呈现 | （缺失） | design §4.3 / 原 04 方案 L2#3 | 并入 Phase 4（语义开关重开前完成）：守卫拒绝渲染结构化卡片，计入"口径拦截成功"而非回答失败 |
| 口径卡强制附尾 | （缺失） | design §5.1 | 并入 Phase 4：回答尾部附对象/指标/版本/时间粒度/过滤条件 |

---

## 二、产品北极星与阶段定位

```text
台阶①（当前→Phase 2 完成）   稳定的 NL2Query 智能数据分析：断了能续、错了能查、慢了能省
台阶②（Phase 4 完成）        本体驱动的数据智能体首个域验证：订单域上以 D1~D5 出数证明语义层价值
台阶③（Phase 5 起）          自动化建模 + 口径治理 + 对外开放（Data Service / MCP）
```

北极星指标：`D3 问答通过率相对基线 ≥ +15%`、`D4 Token/推理步数平均下降 ≥30%`、`任意回答可由 step 表重建完整证据链`。

---

## 三、总路线图（六期，Agent 主线严格先行）

```text
━━━━ Agent 主线（能力 + 交互 + 地基） ━━━━━━━━━━━━━━━━━━━━━━━━
Phase 1 「稳」  提交/执行分离 · LLM 闸门四件套 · 调用级计量 · semantic_ref   （后端 ~6 PD）
Phase 2 「续」  断线续播 · 配置热更 · Token 预算 · 日志截断 · 前端三件套     （后端 ~4 PD + 前端 ~4 PD）
Phase 3 「磨」  消息树 UI · 审批暂停续跑升级 · Loading/进度诚实化
               · 协作式取消增强 · 测试基建                                （前后端 ~6 PD）
──────────────────────────────────────────────────────────────
Phase 4 「验」  调试台 · 建模工作台 · 订单域种子 · GuardReport/口径卡
               · 对照验收实验                                            （前端 ~5 PD + 后端 ~2 PD + 业务 ~1.5 PD）
Phase 5 「治」  Catalog 采集落库 · 血缘推荐映射 · LLM 补描述护栏 · 冲突检测 → 指标审批流/影响分析/行级权限（本体二/三期）
Phase 6 「放」  Data Service SEMANTIC API · MCP Server · Prometheus/Langfuse（按需）
```

排序原则（v1.1 修订）：**Agent 单线串行推进到底**——稳定性（P1）→ 可用体验（P2）→ 能力与交互补齐（P3），三期交付完才启动本体投入。理由：内部平台使用频次高、容错低，地基和交互是每天被感知的部分；语义栈在 `semantic.enabled=false` 下冻结不构成线上风险，晚启动的代价只是延后出数，而双线并行的代价是两边都慢。两条不变的硬约束：①**调用级计量必须在 Phase 1 完成**——无论验收实验何时跑，D4 都靠它出数；②Phase 1~3 期间本体模块执行**冻结令**（只修 bug、不加功能），编译器快照测试保持 CI 常绿防止冻结老化。Phase 5 在台阶②出数后再投入，避免在未证明价值的层上提前堆自动化。

---

## 四、各期详细范围

### Phase 1「稳」— 稳定性地基（后端 ≈6 PD）

对应原方案 §4.1/§4.2/§4.3/§5.2 + semantic_ref 前移。

| # | 任务 | 要点与修正说明 |
| --- | --- | --- |
| 1 | 提交/执行分离（DB 队列版） | 新增 `yak_agent_turn` 表（QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED，白名单状态转移下沉 SQL 条件 UPDATE）；`POST /chat/turns` 验证+落库即返回；SSE 改为按 turn_id 订阅事件（DB 事件表 + Last-Event-ID 增量拉取）；协作式取消。**设计时即加入 `project_id` 列与后台上下文恢复通道**（PROJECT_SCOPE 规则 5：异步任务不得依赖 ThreadLocal/Header）；step/message/事件表补 `turn_id` 归属链路。⚠️ 工作量比原文档预期重：`AgentChatService/AgentInvocationManager/AgentStreamCoordinator` 三者深度耦合需重构拆分 |
| 2 | LLM 单次超时 + 重试分类 | 120s 硬超时（orTimeout），TimeoutError 不重试；401/403/429=USER_ERROR 直接返回用户可读文案；5xx/网络错误指数退避+jitter ≤3 次，每次尝试独立落 step |
| 3 | 结构化输出闭环 | 一次性随机标签 `<json_output id>` + 三级解析 + 校验失败回喂重试 ≤2 次，失败也记账 |
| 4 | 调用级计量接线 | `record()` 包装每次模型调用落 `KIND_LLM_CALL` 行：promptTokens/completionTokens/latencyMs/retryCount/status/error_code。这是 D4 度量的数据基础，也是 Phase 4 验收实验的硬前置 |
| 5 | semantic_ref 落库 | `yak_agent_query_log` 加 NULLABLE `semantic_ref` 列（SemanticQuery 摘要 JSON），run_semantic_query 成功即写入 |

验收标准（可证伪）：

- [ ] 注入 500ms 延迟 mock LLM → 无请求悬挂；构造 500 → 自动重试且 step 表 ≥2 条尝试记录；构造非法 JSON → 回喂重试且 step 表 ≥2 条尝试记录；
- [ ] `POST /chat/turns` 秒回 turn_id，kill -9 执行线程所在进程 → 重启后从最后一个 COMPLETED 步骤之后继续；
- [ ] 同一问题的 step 记录含 LLM_CALL 行级 tokens/latency，可重建证据链；语义查询在 query_log 带 semantic_ref。

### Phase 2「续」— 可用体验与断线兜底（后端 ≈4 PD + 前端 ≈4 PD）

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | SSE 断线续播（依赖 Phase 1 提交/执行分离） | 事件表全量帧带递增 event_id；Last-Event-ID 增量；60s 内重连无重复无丢失；超时未重连 REST 兜底取结果 |
| 2 | 配置热更新 | `yak_config` per-key 表 + env 只作种子；首批纳管 llm.timeout / llm.max-iters / semantic.default.limit / semantic.enabled 开关 / 审批策略开关；严禁模块级静态缓存 |
| 3 | 日志截断器 | TruncatedLogger 超 1000 字符头尾截断。（原 Token 预算/jtokkit 自研方案已被框架 CompactionMiddleware 替代，见 §10） |
| 4 | 前端三件套（一次做完才有效果） | ①SSE 渲染管线（30ms throttle + 词级平滑，禁止直驱 setState）；②连接状态机（五态 + 60s 看门狗 + visibilitychange 重同步）；③错误码→文案→动作四级分发 + 同源去重 |

验收标准：

- [ ] SSE 断开 60s 内重连续播无重复无丢失；
- [ ] 运行时改 `llm.timeout` / 切 `semantic.enabled` 立即生效不重启；
- [ ] 前端无"卡死转圈"；所有错误均为结构化文案+动作按钮，无裸 `e.message`。

### Phase 3「磨」— Agent 能力与交互补齐（前后端 ≈6 PD，仍属 Agent 主线）

本体线执行冻结令期间，把日常使用中被直接感知的交互与能力短板一次补齐。四项均可独立交付、按序渐进。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | 消息树 UI 消费层 | 分支导航 `<n/m>`、regenerate 带"修改建议输入框"、hover-reveal 操作钮——后端消息树 v2 已就绪（V5 双写到位），纯前端活 |
| 2 | 审批暂停续跑升级 | 中间态 + 恢复元数据落库（重复 resolve 返回 409）；SQL 执行类动作可配置人工确认——能力级安全交互，断连后批准仍续跑，同时为后续语义取数确认铺路 |
| 3 | Loading 分层 + 进度诚实化 | 无内容期拟人短语轮换 + 20s 后显示真实耗时；TurnStatsBar 本回合工具数/累计耗时、流结束冻结——直接消费 Phase 1 调用级计量数据 |
| 4 | 协作式取消增强 | CANCELLED 状态位持久化到 DB、执行循环每步检查、轮询终态而非硬杀线程（依赖 Phase 1 turn 化） |
| 5 | 测试基建随期落地 | ToolSpec 内嵌示例 + CI 参数化全量遍历（新工具随手带契约）；SSE 事件序列测试脚手架（Phase 2 断线续播的回归保障）；**k6 压测降级为可选观察项**——内部用户规模先用真实流量观察，出现性能争议再补 |

验收标准：

- [ ] 同一问题触发需审批动作 → SSE 正常收尾、批准接口续跑返回完整结果、重复批准返回 409；
- [ ] 消息树分支可导航，regenerate 可带修改建议；进度条数字来自 step 表计量且流结束冻结；
- [ ] CI 对全部注册工具跑内嵌示例无失败。

### Phase 4「验」— 本体 MVP 收尾与验收实验（Agent 主线交付后启动；前端 ≈5 PD + 后端 ≈2 PD + 业务 ≈1.5 PD）

对应 ontology MVP WBS 剩余项：M7 / M8 / M9 / M11 + 两处呈现强化。实验消耗 Phase 1 的调用级计量数据，启动时点不构成欠账。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | 查询调试台（M8，≈1 PD） | DSL 编辑 + dry-run SQL 展示 + 结果网格——**先做这个**：它是实验前的自证工具，也用于解冻回归 |
| 2 | 建模工作台（M7，重估 ≈4 PD） | 域树/对象/属性映射选择器（复用 datasource Catalog 只读 API，原生 select 降级交互先行）/关系/指标/术语表单；发布状态机按钮触发校验 |
| 3 | 订单域种子建模（M9） | ≥3 对象 / ≥2 关系 / ≥5 原子指标 / ≥10 术语全 PUBLISHED；前置 0.5 天口径澄清会（最大风险项，见 §六） |
| 4 | GuardReport 结构化卡片 + 口径卡 | 守卫拒绝渲染为结构化卡（错误码+文案+suggestion），计入"口径拦截成功"指标；回答尾部强制附口径卡（对象/指标/版本/时间粒度/过滤条件）。**这两项完成并验证后才建议向业务放开 `semantic.enabled`** |
| 5 | 对照验收实验（M11/D1~D5） | 25 问双通道盲评；D1 dry-run SQL 全评审通过、D2 数值一致率 100%、D3 通过率 ≥+15%、D4 Token/步数 ≥-30%（消费 Phase 1 计量数据）、D5 资产完备 + Ossie YAML 过 schema 校验 |

前置检查：解冻时先跑一轮编译器快照测试回归（冻结期内该测试保持 CI 常绿）。
里程碑：W1 末调试台+dry-run 30 例 SQL 评审通过（D1）；W2 末工作台+种子建模签收（D5）；W3 末实验出报告（D2~D4）。

### Phase 5「治」— 本体自动化与治理（对应 ontology 二期/三期，二期 ≈4 周、三期 ≈6 周）

前置门槛：**台阶②验收报告通过后再启动**——先证明语义层有价值，再为其堆自动化。

- 二期：Catalog 定时采集落库（information_schema、错峰限频、变更检测→STALE 告警）；血缘推荐映射工作台（INFERRED 永不直接生效）；LLM 辅助补描述（dry_run 默认、confidence≥0.75 且 fill_empty 才落库、业务名不可覆盖）；多源口径冲突检测告警。
- 三期：指标版本审批流（IN_REVIEW + 通知）；口径变更影响分析产品化（包装血缘 upstream/downstream）；术语表运营界面；行级权限注入（`RowPolicyProvider` Hook 已就位，填实现即可）。

### Phase 6「放」— 开放生态（按需）

数据服务新增 `SEMANTIC` API 类型（继承鉴权/限流/熔断/OpenAPI 全套既有能力）；语义 MCP Server；Prometheus 指标（反高基数纪律）+ Langfuse tracing——均有真实流量再接。

---

## 四-A、真机冒烟记录（2026-08-29，V1.3 增补）

**背景**：多 agent 并行开发导致服务无法启动 + 可观测性体系（O1~O4）缺真机验证。冒烟环境：MySQL 本机 + 全新库 `yak_ops_smoke_obs2` + 本地 fake OpenAI SSE 端点（`scripts/smoke/fake-openai.js`，脚本化两轮：工具调用→最终回答）。

**启动修复（两项，均已入迁移）**：
1. `V2011__patch_user_project_unique_key.sql`：框架 jar 的 bootstrap 会为同一 (user, project) 插入两种 user_type（负责人+成员），但其自带 V1 DDL 的 `uk_user_project_active` 不含 user_type → 新库必撞唯一键、启动失败。垫片加宽唯一键（沿用 V2010 先例，待框架对齐后撤）。
2. 旧库 `yak_ops_smoke` 已与并行 WIP 的迁移改名产生脏漂移，冒烟/开发一律用全新库 + `createDatabaseIfNotExist=true`。

**验证通过的冒烟项**：
- [x] 服务正常启动（55 个 Flyway 迁移含 V9__agent_span_timing 全部应用，无启动后失败）；
- [x] 登录（bootstrap root）→ agent 端点鉴权 401/200 分界正确；
- [x] 提交/执行分离：`POST /chat/turns` 秒回 turnId；
- [x] fake 模型驱动真实 Agent 多迭代执行：LLM(1)→工具 `current_date_info` 真实执行→LLM(2)→TURN_SUMMARY，timeline offsets 正确；
- [x] trace v2：树组装（工具挂触发它的 LLM_CALL）、聚合（LLM_CALL×2/TOOL_CALL×1）、completeness=complete、attempt/durationMillis（V9 列）有值；
- [x] 载荷策略生产验证：LLM 请求 SUMMARY_HASH（preview 可见原文不落库）、工具入参/结果 INLINE 完整、toolCallId join 键；
- [x] 会话观测矩阵 `GET /sessions/{id}/observability`：turns × kinds 正确；
- [x] 中文消息 UTF-8 提交正常（此前 400 为 curl 客户端编码问题）。

**遗留**：浏览器端迭代分组视觉（headless 环境无法核验，前端逻辑有 16 用例兜底）；停止生成/ABORTED 收敛真机复核（fake 模型响应过快无法插入时序）。

---

## 四-B、记忆线 M1 与配置热更新完成记录（2026-08-29，V1.3 增补）

**记忆线 M1「骨架」（commit 551ca9c96，测试 93→102 全绿）**：
- V8 迁移 `yak_agent_memory`（LEDGER/CURATED 两层 × USER/PROJECT/GLOBAL 三 scope × 六 type）；
- `MemoryFlushService`：COMPLETED 终态异步提取（总开关/THROTTLED 5min/实质内容三闸门）→ 数据域 LLM 提取（经 `MemoryCompletionPort` 防腐端口，memory 包零框架/零 reactor 依赖）→ JSON 解析失败放弃 → 白名单/截断/置信度钳制 → LEDGER 入库；
- `LongTermMemoryPromptMiddleware`（onSystemPrompt）：会话末条用户消息为查询 → 置信度门槛+类型权重+时间衰减 → topK+字符预算 → 注入段落；KIND_MEMORY_RECALL 计量 + 命中异步回写；
- 架构守护矩阵新增 memory 包（memory→[dao,config,repository]）；MEMORY_* 三 kind 全部经 KindSpec 注册接入观测体系（联调豁免：M1 即为联调环境）。
- M1 验收对照（开发计划记忆线）：闸门/开关/预算/解析失败/树组装全部有测试钉住；"Step kind 常量注册"验收 ✓。

**配置热更新最小版（commit 613ea0198，Phase 2 P2#2 部分）**：
- V12 迁移 `yak_config` per-key 表 + `AgentDynamicConfigService`（现读 + 1s 微过期点查，热更可见延迟 ≤1s）；
- 首批键位接线：`yak.agent.memory.enabled` / `yak.agent.observability.enabled`（记忆闸门、召回中间件、观测采集开关）；
- 归位裁决：动态配置读取是仓储读适配 → repository 包（边界矩阵裁决 config 不碰 dao）；runtime/memory 增补 repository 依赖方向。
- Phase 2 配置热更**剩余**：llm.timeout / llm.max-iters / semantic.enabled 等键位与治理界面（后置）。

**过程发现**：架构守护（条件装配/SDK 白名单/依赖矩阵/采集面边界）在 M1 开发中拦截 5 次设计偏差——守护体系价值实证。

## 五、工作量汇总与修正对比

| 阶段 | 原方案预估 | 本计划修正 | 修正原因 |
| --- | --- | --- | --- |
| Phase 1（原 §八 Phase1） | 4–5 PD | **≈6 PD** | 提交/执行分离需重构 ChatService/InvocationManager/StreamCoordinator 三件套，且新增 turn 表迁移与 project_id 预留 |
| Phase 2 | 4–5 PD（前后端混计） | 后端 ≈4 PD + 前端 ≈4 PD | 前端三件套拆开单列；只做管线不做状态机与错误映射等于白做 |
| Phase 3（原体验批次） | 按需 | 前后端 ≈6 PD | 消息树 UI 消费层为纯前端活；审批续跑升级含落库改造；测试基建随期分摊，不再单列 |
| Phase 4（原 07 WBS 剩余） | 27 PD 总包中剩余部分 | 前端 ≈5 PD + 后端 ≈2 PD + 业务 ≈1.5 PD | M0~M6 后端主干已完成，剩余集中于 M7/M8/M9/M11；排序调后不影响总量，只影响启动时点 |

---

## 六、风险登记（合并版）

| 风险 | 等级 | 对策 |
| --- | --- | --- |
| 双线并行拉扯导致 Agent 与本体两头都慢 | 高 | v1.1 已通过**严格串行 + 本体冻结令**化解：Phase 1~3 只做 Agent，本体仅修 bug 不加功能 |
| 语义栈冻结老化（冻结期内上游 catalog 表结构 / Project Space 改造波及映射与编译器） | 中 | 编译器 SQL 快照测试保持 CI 常绿（成本极低）；Phase 4 启动时先跑一轮解冻回归再续建 |
| 订单域口径争议拖住种子建模（M9 卡壳） | 高 | 建模前 0.5 天口径澄清会；争议口径标 DRAFT 不阻塞链路；owner 签收后才 PUBLISH |
| 双轨并存期语义路径故障放大信任危机 | 高 | `semantic.enabled` 保持开关受控灰度；实验组先在调试台自证再切 Agent；Phase 1 稳定性批次先落地 |
| 提交/执行分离改造伤及现有流式体验 | 中 | 保留 `/chat/stream` 兼容一个过渡窗口（内部路由同一 turn 执行体），灰度切换后下线 |
| D4 实验"看不出差异"或反向 | 中 | 失败归因分类（建模缺失/编译缺陷/Agent 行为/口径争议）直接产出 Phase 5 backlog；Schema 预加载效应有 bkn 实测背书（-47.9%），但小样本噪声要有心理预期 |
| 异步执行与 Project Space 冲突 | 中 | Phase 1 设计时即落 project_id + 上下文恢复；PR4/PR5 项目化推进时 agent 表纳入 PROJECT_RUNTIME 分类 |
| Catalog 采集压生产库 | 低 | information_schema + 错峰限频 + SSH 隧道复用 |

---

## 七、跨阶段不变式（写码纪律）

1. **幂等优先**：一切状态转移走 SQL 条件 UPDATE（乐观并发天然单赢家）；重复回调返回终态快照而非报错膨胀（审批 resolve 例外：409）。
2. **失败也记账**：任何 LLM/工具/Guard 尝试都落 step，失败不影响事实留存；Recorder 自身异常只告警。
3. **Feature flag 一键回退**：`semantic.enabled`、未来的 `agent.turn-execution.enabled` 均保持旧行为字节级一致。
4. **安全底线恒定**：物理标识符白名单正则双重校验、值一律参数绑定、算子枚举白名单、SqlExecutionPolicy 只读 + sql_execution 审计双保险。
5. **配置不缓存**：运行路径现查 + get_many 批量，严禁模块级静态变量持有配置。
6. **反高基数**：未来 Prometheus 指标不带 user_id/session_id。
7. **A1 红线**：Agent 不生成 SQL，不做过滤计算；禁止编造字段名/编码；数字必须来自 run_semantic_query；空结果明说并建议放宽哪个条件。

---

## 八、明确不做（合并防过度设计清单）

1. 不引入 DAG 图引擎/可视化编排；不上 RabbitMQ/Kafka（DB 队列够用，真瓶颈再升级）；
2. 不做多模型目录/价格管理、不计费、不做多租户 org/team；
3. 不做用户上传 Python 插件 exec（扩展走 MCP Server 路线）；
4. 不引入 Neo4j/RDF 三元组库；一期不做联邦跨源与 INDIRECT 关系；不做行动类/风险类本体模型；
5. Ossie 仅作交换格式与词汇表（导出直映 Core Spec + custom_extensions 承载私有扩展），不自造标准也不做其运行时依赖；
6. Langfuse/Prometheus 等 APM 推迟到有真实流量；先把计量落库；
7. 报告 Artifacts 化、引用三层呈现、排队草稿、IME 兼容维持低优先级（P3，知识库接入时启用）。

---

## 九、文档治理

- 本文档为**唯一权威排期**，进度以各 Phase 验收 checkbox 勾选为准；
- `docs/agent-optimization-plan.md` 标注 superseded，保留作历史设计论证，不再单独维护；
- `knowledge-docs/agent/*`、`knowledge-docs/ontology/*` 为背景调研资料，内容冻结；其中 06/07 的设计与分期论证仍有效，执行排期一律以本文为准；
- 每 Phase 结束在本文件追加一节《执行复盘》（实际 PD、偏差、归因），作为下一期估算输入。

---

## 十、v1.2 框架对齐统筹修订（依据 `docs/agentscope-framework-audit-report.md`）

背景：前期只蒸馏了 AutoGPT/Open WebUI 两个参照项目，漏掉了运行框架 AgentScope 本身，导致部分横切关注点重复造轮子、个别项相对当前规模过度设计。本章节是对全文的增量修订，与前文冲突处以本章为准。

### 10.1 采纳的审计结论

| # | 动作 | 说明 |
| --- | --- | --- |
| 1 | **引入 `agentscope-harness` + 注册 CompactionMiddleware** | 🔴 缺失即缺陷：maxIters/轮级超时防不了跨轮上下文累积超窗，长对话会静默失败。同时替代本文 Phase 2 原"Token 预算(jtokkit)"自研项 |
| 2 | **GatedChatModel → onModelCall Middleware** | 超时/重试分类/调用级计量迁入 `LlmResilienceMiddleware`；删除 Model 装饰器与 Reactor Context 观察者管道；错误分类常量与翻译逻辑保留（审计认可的高质量实现） |
| 3 | **工具记账 → onActing Middleware** | 新增 `ToolAuditMiddleware` 落 KIND_TOOL_CALL 步骤记录；RunDatasetQueryTool / RunSemanticQueryTool 移除侵入式 stepRecorder 直调（工具回归薄壳） |
| 4 | **提示词拼接 → onSystemPrompt Middleware 管道** | AgentSystemPromptContributor 体系改造为 Middleware，获得排序与异步能力 |
| 5 | TranscriptMiddleware / PermissionEngine | 列为后续可选增强（§Phase 4 区域）：JSONL 会话转写用于审计回放；PermissionEngine 对 analyze_with_python 设 ASK 是真实安全增强 |

### 10.2 结合在途代码的再裁决（部分推翻审计建议）

| 审计建议 | 裁决 | 理由 |
| --- | --- | --- |
| 用框架 SessionTurnGate 替代内存互斥锁 | **删除内存锁且不引入 SessionTurnGate** | 提交/执行分离后，单飞真相收敛到 `yak_agent_turn` DB 状态机（hasActiveTurn 校验 + CAS 认领）；进程内锁成为第二真相源，违反 Truth 单一 owner；SessionTurnGate 会引入第三套互斥机制。提交侧 check-then-insert 竞态窗口以 JVM stripe 锁串行化（单节点内部部署），横向扩展时升级为分布式 TurnGate 是届时前置条件 |
| 删除 AgentTurnRegistry（Flux 自带 cancel） | **保留（瘦身）** | 断连与执行解耦后，"停止生成"来自另一个 HTTP 请求，需按 sessionId 路由到运行中的 Disposable；Flux.doOnCancel 只覆盖订阅端取消，覆盖不了跨请求路由。类注释说明存在理由 |
| 投递日志表 yak_agent_turn_event 可能过度设计 | **保留** | 内部平台地基稳定优先：崩溃一致性 + Last-Event-ID 断线续播的价值 > 轮询延迟成本（每轮约 200 次 INSERT 对 MySQL 可忽略；Phase 2 渲染管线平滑进一步吸收体感延迟）。ChatUiChannel 推模式列为远期性能优化选项 |
| 引入 openai-spring-boot-starter 自动装配 | **不引入** | 与"内部单模型 OpenAI 协议、明确不做多模型"前提冲突；且削弱模块显式装配文化与既有条件装配守护测试。出现 provider 多样性交付需求时重新评估 |
| 消息树表轻度过重 | 维持现状并冻结扩展 | 双写成本低则保留；分支导航交互（Phase 3「磨」）落地时再评估去留 |

### 10.3 执行序调整

原 Phase 1 在途代码（turn 化执行，未合入）拆为两个边界清晰的 commit，遵循模块 Change Rules：

```text
✅ Commit A 「turn 化执行」    提交/订阅分离 + 投递日志 + 断线续播 + 行为测试         （已完成）
✅ Commit B 「框架对齐」       Middleware 五件套 + 删内存锁 + 契约同步                （已完成，55/55 绿）
✅ UI 适配                    services/agent 迁移 + turn 协议接入                     （已完成，lint/tsc 干净）
其后 Phase 2/3/4…             按 §三 不变
```

遗留问题与条件触发项的**状态跟踪统一以 [pending-issues.md](pending-issues.md) 为准**（含 PI/PT 编号、优先级、DoD 与完成登记），本文档不再重复维护逐项状态；设计依据与裁决理由仍见 §10.2/§10.3 及审计报告。

### 10.4 同批记录的需求先行裁决

- **结构化输出闭环（随机标签三级解析+回喂重试）暂缓**：现行工具全部走 `@ToolParam` 函数调用原生结构化参数，不存在消费自由文本 JSON 的路径。待首个真实消费需求出现时按 Requirement Gap 流程立项。
