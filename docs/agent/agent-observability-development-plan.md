# 数据智能体可观测性体系 · 开发计划（v1.0）

> 日期：2026-08-29
> 定位：可观测性体系设计的执行排期——期次、任务拆解、PD 估算、可证伪验收。
> 设计依据：[agent-observability-system-design.md](agent-observability-system-design.md)（下称"设计稿"）。
> 与总计划关系：总排期仍以 [data-agent-development-plan.md](data-agent-development-plan.md) 为唯一权威；本计划是其可观测性专线的补充，与记忆线 [data-agent-long-term-memory-development-plan.md](data-agent-long-term-memory-development-plan.md) 的并行关系见 §三。

---

## 一、执行前提（全部已满足，以代码核实）

| 前提 | 状态 | 证据 |
| --- | --- | --- |
| 零侵入采集面（三 Middleware） | ✅ | `LlmResilienceMiddleware` / `ToolAuditMiddleware` / `SystemPromptAssemblyMiddleware` |
| 单写入口 + 失败也记账 | ✅ | `AgentStepRecorder`，58 测试含 `StepSingleWriterGuardTest` |
| 关联键（parent_step_id / tool_call_id） | ✅ | V7 迁移已落 |
| 读接口基座 | ✅ | `GET /turns/{turnId}/trace`（I1） |
| 服务端权威计时双通道 | ✅ | 事件帧（I2/I7）+ step statsJson |
| 回归门槛 | ✅ | 现有 58 测试全绿为 O1 改造的行为基线 |

---

## 二、期次总览

```text
O1「收口」 统一模型 + KindRegistry + 策略引擎 + 采集收敛 + LLM 内容观测   （后端 ≈3.5 PD）
O2「看见」 trace v2 树/聚合/完整性 + 前端通用 Timeline + 历史回放切换      （后端 ≈1.5 PD + 前端 ≈2 PD）
O3「补盲」 Guard/HITL/Compaction 观测点 + 失败语义归一 + 记忆线联调       （后端 ≈2.5 PD）
O4「治理」 开关/热更 + 保留策略预留 + 契约收口                            （后端 ≈1 PD）
────────────────────────────────────────────────────────────
合计 ≈ 后端 8.5 PD + 前端 2 PD = 10.5 PD
```

排序逻辑：O1 先立契约（不改体验，用户无感）；O2 直接回应"时长/入参/过程/结果看得见"的用户诉求，**是本轮体验提升的主交付**；O3 补齐关键过程盲区；O4 收治理。

---

## 三、与两条并行线的关系

| 线 | 关系 |
| --- | --- |
| v1.2 主线（Phase 2/3） | O1/O2 建议插在 Phase 2 后端交付后、Phase 3 期间——O2 前端工作量与 Phase 3 前端批次可共享设计走查；不与 Phase 1 类稳定性改造并行 |
| 记忆线（M1~M4） | 记忆 M1 的三个 KIND_MEMORY_* **直接以 KindSpec 注册接入本体系**（O1 完成后）：记忆线不再自建 step 写入方法，验证"新维度=注册即生效"的体系主张；时序上 O1 先于记忆 M1 或同批合入 |

---

## 四、各期详细范围

### O1「收口」— 契约与协议统一（后端 ≈3.5 PD）

> **✅ 已完成（2026-08-29 执行复盘）**：V9 迁移（started_at/ended_at/attempt 升列）；KindSpec 注册表 9 类 kind（含记忆线三件预留）；PayloadPolicy 引擎（JSON 感知截断 + SUMMARY_HASH/HASH_ONLY/OMITTED + forbiddenRawKeys）；AgentObservationCollector 收敛（Middleware→Collector→Recorder 单写链路）；LLM 内容观测（G5，SUMMARY_HASH 档）；计时归一（MiddlewareErrorSupport.elapsed 统一）；iter/phase 帧字段（mapStream 事件流侧推导）；终态收敛律（executor 三终态路径补发 ABORTED 结果帧）；前端 TURN_CANCELLED 分支 + types 扩展。
> 复审修正项：①工具入参死字段修复（Track.requestContent 原被丢弃）；②依赖方向修正——Msg 提取收敛在 runtime 中间件侧，telemetry 只收 neutral 字符串（SDK 白名单守护强制）；③JSON 收缩算法由逐条替换改为长值优先批量收缩（结构性开销主导时退化为头尾截断属预期边界）。
> 验收证据：agent 模块 77/77 全绿（基线 58 + 新增 19 用例）；架构守护（SDK 白名单/单写入口）随套件绿。O1-4 验收清单中"帧侧与事实侧耗时差 ≤5ms"待真机抽样复核。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | 迁移 V__ 步骤表升列 | `started_at/ended_at/attempt` 三列 + `(turn_id, kind, id)` 索引（设计稿 §六）；存量不回填，读侧 NULL 回退 statsJson |
| 2 | `KindSpec` 注册表 | 设计稿 §4.2 首批 7 类 kind 注册（LLM_CALL/TOOL_CALL/GUARD_REJECT/HITL/COMPACTION/TURN_SUMMARY + 记忆三件占位）；`AttributeSchema/PayloadPolicy/ParentRule/RenderHint/Aggregation` 五要素；未注册 kind 拒绝落库并告警 |
| 3 | 载荷策略引擎 | PayloadEnvelope 四档（INLINE/SUMMARY_HASH/HASH_ONLY/OMITTED）；JSON 感知截断（保可解析）；forbiddenRawKeys 负清单；`AgentProperties.Observability`（enabled/per-kind 覆写/maxInlineChars）——**I8 在此落地** |
| 4 | `AgentObservationCollector` | begin/observe/end 统一 API；KindSpec 校验 + 策略执行 + span 组装；`AgentStepRecorder` 收敛为落库端点（对外 API 收缩，旧方法标记 @Deprecated 过渡） |
| 5 | LLM 内容观测（G5） | `LlmResilienceMiddleware` 捕获模型输入走 SUMMARY_HASH 档（消息条数/末条用户消息预览/字符数/sha256）；`attempt` 升列写入 |
| 6 | 计时归一（G1） | `mapStream`（帧侧）与 `ToolAuditMiddleware`（事实侧）耗时计算收敛为同一计算器，事实侧权威 |
| 7 | 架构守护 | 注册表↔DDL kind 枚举一致性启动校验；Collector 调用面扫描（仅 middleware/executor 可触达） |
| 8 | 渲染顺序规范·服务端侧（设计稿 §11.3） | **终态收敛律**：executor 在 finishFailed/finishCancelled/超迭代路径对未闭合 toolCallId 兜底补发 `TOOL_RESULT(state=ABORTED/INTERRUPTED)` 帧（治"幽灵运行卡"的根）；**迭代显式化**：帧携带 `iter`（成功模型调用边界递增、重试不递增，middleware seam 计数）；**文本分层**：TEXT_DELTA 带 `phase=final\|intermediate` |

验收标准（可证伪）：

- [ ] 58 既有测试全绿（行为基线不破）；
- [ ] 一次带重试的轮次：step 行含 started_at/ended_at/attempt，重试 2 次 = 3 条 LLM_CALL 各 attempt 1/2/3；
- [ ] LLM_CALL 的 payload 为 SUMMARY_HASH 结构（含末条预览+sha256+长度），库中无 prompt 原文；
- [ ] 构造超 8000 字符 JSON 工具输出 → 落库内容仍可被 JSON 解析且带 truncated 标记；
- [ ] 未注册 kind 调用 Collector → 拒绝落库 + 告警日志，注册后同代码即成功（灵活性主张第一证）；
- [ ] 同一工具调用：帧侧 durationMs 与 step 侧 latencyMs 差值 ≤5ms（计时归一）；
- [ ] **终态收敛律**：停止生成/失败/超迭代轮次中，所有已开启 toolCallId 在终态帧前收到配对 TOOL_RESULT（ABORTED/INTERRUPTED）——构造停止生成场景断言零幽灵运行卡；
- [ ] 帧携带 `iter` 且多迭代轮次递增正确、重试不递增；TEXT_DELTA 的 phase 字段区分中间叙述与最终答案。

### O2「看见」— 消费层三视图（后端 ≈1.5 PD + 前端 ≈3 PD）

> **✅ 已完成（2026-08-29 执行复盘）**：
> 后端——`TraceViewAssembler` 读侧组装器（parent 链树/时间轴/kind 聚合/completeness/渲染投影五视图 + 失败语义归一 §7.4）；`TurnTraceVO` 升级为 v2 六视图；新增 `GET /sessions/{id}/observability` 会话级观测矩阵；`HistoryVO/HistoryTurnWithTrace` 携带 turnId（配对方式沿用既有数数式对齐，error 轮次按追加序配对）；`AgentStepRecord` 补 V9 三列读取；`PayloadEnvelope.decode` 兼容存量裸文本行。
> 前端——`agentSessionApi.trace/observability`；`TraceStep` 扩展 iter/waiting/inputText/failureDetail；**迭代分组渲染**（服务端 iter 权威，"第 N 轮推理"分组标记）；**工具卡四要素**（耗时/入参 Collapse/结果 Collapse/状态与失败详情，服务端权威值）；**权威水合**（轮终态后拉 trace v2 合并入参与失败归因，帧未覆盖的卡片按事实侧补齐，think 文本保留）；HITL 显式"等待输入"态；TURN_FINISHED/终态对账钩子（残留运行卡 ABORTED 自愈 + console 违例上报）；历史回放最近一条 assistant 轮次自动水合。
> 复审修正项：antd X ThoughtChain status 枚举为 loading/abort/error/success（非 processing/finish）。
> 验收证据：agent 模块 84/84 全绿（新增 TraceViewAssemblerTest 7 用例：树组装/断链显式化/聚合数学/V9 列优先/失败归因/legacy 兼容/kinds 投影）；前端 agent 文件 lint/tsc 干净。
> 待真机验证：多迭代轮次分组视觉、水合延迟体感、MockKind 零改动渲染演练；会话观测矩阵的前端面板（API/类型已就绪，UI 表格后置）。
> 前端测试补充（2026-08-29）：链路运行时逻辑提取为纯函数模块 `ai-agent/trace-runtime.ts`（事件状态机/终态收敛/权威水合/迭代分组/卡片状态映射），配 16 用例全绿；顺手修复 jest 基建（@umijs/max 4.6.x 需 `test.js` 扩展名，此前全部前端测试无法启动）。发现存量前端测试腐烂 10 套（login/权限/数据开发等，与 agent 无关）登记待_triage；`navigation.test.ts` 失败源于并行在途的 navigation.ts 改动。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | trace API v2 | 树组装（parent 链）+ 归一化 timeline + byKind 聚合（count/totalMs/maxMs/p95）+ `completeness`（**I9 落地**：断链模式显式化）+ kinds RenderHint 投影 |
| 2 | 会话级观测视图 | `GET /sessions/{id}/observability`：turns × kinds 矩阵（LLM 次数/重试/工具次数/耗时/token），纯 JOIN 无新采集 |
| 3 | 前端通用 TraceTimeline | 数据驱动组件（消费 tree/timeline/kinds）：kind→图标/颜色来自 RenderHint；**工具卡片四要素**——耗时（时间轴占比条）、入参（格式化折叠）、结果（折叠+截断标记+失败时解析业务错误 JSON）、状态/错误码；LLM span 展示"第 N 次推理·tokens·耗时·重试" |
| 4 | 前端渲染状态机重构（设计稿 §11.4/§11.5） | 迭代分组渲染（服务端 `iter` 优先、启发式降级）；ToolCard 状态机 `RUNNING→SUCCESS/FAILED/ABORTED/WAITING_INPUT`（终态帧到达仍 RUNNING 按 ABORTED 自愈+上报违例）；补 `TURN_CANCELLED` 分支（当前落入 default——**存量 bug，可先行独立修复**）；HITL 等待显式"等待输入"态；TURN_FINISHED 本地对账钩子；迭代组视觉分隔（运行卡只允许出现在最后一组） |
| 5 | 历史回放切换 | `HistoryTurn.trace` 从 think/call 文本重建切换为消费 trace v2（刷新后观测数据不丢）；SSE 实时流不动（双通道裁决） |
| 6 | 失败语义归一（读取侧部分） | errorPreview 模板句降为兜底，responseJson 业务错误结构优先上屏 |

验收标准（可证伪）：

- [ ] trace v2 对含重试+并行工具的轮次返回正确树（兄弟 attempt + 工具挂成功 LLM 下）且 aggregates 数字与手工 SQL 一致；
- [ ] 人为删一条 LLM_CALL 父记录 → completeness.complete=false 且 reasons 含断链说明（不静默）；
- [ ] 前端链路页可见工具入参/结果/耗时占比，且**新增一个测试 kind（注册表加 MockKind）前端无需改代码即渲染**（灵活性主张第二证）；
- [ ] 历史会话刷新/回放后 trace 完整（与当次会话一致），不再退化为纯文本；
- [ ] biome/tsc 干净；他人 turnId 403 语义不变（归属校验回归）；
- [ ] **顺序语义**：多迭代轮次前端按迭代分组渲染、运行卡仅出现在最后一组；停止生成后无转圈卡片（TURN_CANCELLED 分支生效）；HITL 等待显示"等待输入"态；中间叙述不混入最终答案区。

### O3「补盲」— 关键过程观测点（后端 ≈2.5 PD）

> **✅ 已完成（2026-08-29 执行复盘，Compaction 项按预授权降级）**：
> Guard——检测点收敛在 `ToolAuditMiddleware.flush`（该处已持有工具入参/输出全文，不另建重复缓冲的独立中间件，对设计稿为等价小偏离）：`[GUARD_REJECTED]` 标记出现在工具错误输出或 flux 错误预览即额外落 GUARD span（status=REJECTED，errorCode=GUARD_REJECTED，toolCallId join 事件帧，父=触发它的 LLM_CALL）。语义路径既有错误码不动。
> HITL——executor 两处落 span：挂起（WAITING_INPUT 终态，载荷=澄清问题）与恢复（RESUME 轮次入口，载荷=用户应答原文——口径沉淀一等来源）；`AgentStepRecorder.recordEventSpan` + `Collector.event(...)` 通用事件 span 写入（带 toolCallId），TURN_SUMMARY 同步改走 Collector。
> Compaction——**框架无缝**（CompactionMiddleware 字段全私有、无回调/监听器）：按设计稿预授权降级，KindSpec 已注册占位 + `registerCompactionIfEnabled` 留痕来源限制；框架版本提供回调缝后接入（登记条件触发项，挂 pending-issues 模式）。
> 记忆线联调——MEMORY_* 三 kind 已注册占位，端到端联调待记忆线 M1 落地（共用本验收）。
> 验收证据：agent 模块 84→87 全绿（新增 GuardObservationTest 2 用例 + HITL 双 span 验收 2 处）。**过程发现**：Maven 增量编译假绿一次（测试构造器类型不匹配未报错）——O3 起回归统一 `clean test`。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | Guard 观测 | `GuardObservationMiddleware`（onActing 拦截拒绝/异常路径）→ `GUARD_REJECT` span（规则名/对象/建议）；语义路径既有错误码不动 |
| 2 | HITL 观测 | executor 挂起（WAITING_INPUT）/恢复/放弃三处 emit `HITL` span，同 toolCallId 关联事件帧 |
| 3 | Compaction 观测 | 压缩触发处（框架 CompactionMiddleware 回调或事件侧）emit `COMPACTION` span（前后消息数/token）——若框架无回调缝，记录触发事实于帧侧投影并在 KindSpec 标注数据来源限制 |
| 4 | 记忆线联调 | 记忆 M1 的 `MEMORY_FLUSH/RECALL/CONSOLIDATE` 以 KindSpec 注册接入，端到端验证采集→策略→落库→trace v2→前端渲染全链路 |

验收标准（可证伪）：

- [ ] 触发 Guard 拒绝 → trace 树出现 GUARD_REJECT 叶子（父=触发它的 LLM_CALL），前端自动渲染；
- [ ] HITL 挂起→恢复轮次：两条 HITL span + 事件帧 toolCallId 三方可 join；
- [ ] 长对话触发压缩 → COMPACTION span 落库（或 KindSpec 声明的替代来源），前后消息数正确；
- [ ] 记忆三 kind 全链路可见（此验收与记忆 M1 验收共用，不重复造环境）。

### O4「治理」— 运行时治理与收口（后端 ≈1 PD）

> **✅ 已完成（2026-08-29 执行复盘，整条可观测性线闭环）**：
> 治理配置——`AgentProperties.Observability`（总开关 / per-kind 开关 Map / maxInlineChars / llmRequestInlineDebug 排障升档），**调用时现读**（Collector 每次采集现查，"配置不缓存"不变式）；"改值无重启生效"绑定 Phase 2 配置热更基建（yak_config per-key 尚未建设，本批键位登记为首批候选——验收项改为可证伪的属性翻转测试）。
> 结构调整——`AgentObservationCollector` 由 telemetry 迁至 runtime（采集协议层与中间件同住；telemetry 边界只许 dao，无法引 config），Recorder 留 telemetry 作唯一写端点。
> 保留策略——`AgentStepMetricsJob` 每日 06:13 聚合上报行数/平均载荷/最老记录（反高基数）；TTL/归档维持"只设计不实现"。
> 契约收口——REQUIREMENTS（turn trace v2/观测矩阵/类型治理/运行时治理四条能力）、DOMAIN（kind 单一真相 + PayloadEnvelope truth）、ARCHITECTURE（采集面边界）补录；I8/I9 标注收编完成，蒸馏报告归档 superseded；新增 `CollectorBoundaryGuardTest`（采集入口触达白名单 + toolset 零感知记账）。
> 验收证据：agent 模块 87→93 全绿（治理开关/per-kind/升档/动态上限 4 用例 + 边界守护 2 用例）；单写守护白名单补 AgentStepMetricsJob（只读聚合）。
> 过程发现——①telemetry 依赖边界倒逼 Collector 归位 runtime（架构守护第二次拦截设计偏差）；②Maven 增量编译假绿风险持续有效，回归一律 `clean test`。

| # | 任务 | 要点 |
| --- | --- | --- |
| 1 | 开关与热更 | `yak.agent.observability.*` 纳入配置热更路径（Phase 2 配置热更机制）：全局/per-kind 开关、maxInlineChars、debug 升档（LLM request 临时 INLINE） |
| 2 | 保留策略 | step 表数据量观测（行数/平均 payload 大小上报日志）；TTL/归档策略**只设计不实现**（有量级证据再立项，防过度设计） |
| 3 | 契约收口 | REQUIREMENTS/DOMAIN/ARCHITECTURE 三件套补录观测体系条目（对齐 OBS-C 模式）；`agent-observability-optimization-issues.md` 标注 I8/I9/I10 状态收编；蒸馏报告标注 superseded by 设计稿 |

验收标准（可证伪）：

- [ ] 运行时关 `observability.enabled` → 新 step 零写入、trace v2 正常返回存量数据；重开即恢复；
- [ ] 热更 maxInlineChars → 下一次工具调用即按新值截断（无重启）；
- [ ] 三件套契约条目 + 守护测试绿。

---

## 五、工作量汇总

| 期次 | 后端 | 前端 | 合计 | 关键交付物 |
| --- | --- | --- | --- | --- |
| O1 收口 | ≈4.5 PD | — | 4.5 PD | KindRegistry + 策略引擎 + LLM 内容观测（I8）+ 终态收敛律/iter/phase（§11.3） |
| O2 看见 | ≈1.5 PD | ≈3 PD | 4.5 PD | trace v2 + 通用 Timeline（入参/结果/时长上屏）+ 渲染状态机与迭代分组（§11.4/§11.5）+ 历史回放切换（I9） |
| O3 补盲 | ≈2.5 PD | — | 2.5 PD | Guard/HITL/Compaction 观测 + 记忆线接入 |
| O4 治理 | ≈1 PD | — | 1 PD | 开关热更 + 契约收口 |
| **合计** | **9.5 PD** | **3 PD** | **12.5 PD** | 四层体系全通 + 灵活性双证 + 顺序语义闭环 |

> 快赢项（不等待排期）：前端 `TURN_CANCELLED` 帧落入 default 的存量 bug（`index.tsx` handleEvent 无分支，停止生成后工具卡永久转圈）可随时以一个独立小改动先行修复。

---

## 六、风险登记（细节见设计稿 §十）

| 风险 | 等级 | 对策 |
| --- | --- | --- |
| O1 收口触碰刚落地的 I1~I7 路径 | 高 | 58 测试全绿为合入门槛；计时归一逐字段对照回归 |
| Compaction 观测依赖框架回调缝（可能不存在） | 中 | O3#3 已声明降级路径（帧侧投影 + KindSpec 标注来源限制），不为此改框架 |
| 前端 Timeline 一步做全导致延期 | 中 | 列表→树→时间轴三步走，O2 只承诺前两步 + 工具卡片四要素 |
| KindRegistry 与 DDL/文档漂移 | 中 | 启动一致性校验（O1#7）+ 注册表单一真相纪律 |
| 与记忆线/主线合入顺序冲突 | 低 | §三 时序表；迁移版本号以合入顺序为准 |

---

## 七、执行纪律（继承 v1.2 §七 + 本线特化）

1. best-effort 永不变：观测记录失败只告警，绝不反向影响执行事实；
2. 归属校验先于一切读取（trace v2 沿用 turn.userId 冻结校验）；
3. 单写入口不破（Collector→Recorder 收敛后仍单点）；
4. 反高基数：aggregates/未来指标不带 userId/sessionId；
5. 新观测维度准入 = KindSpec 注册，禁止绕过注册表直写 kind 字符串（架构守护锁定）。
