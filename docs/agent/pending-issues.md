# yak-ops 数据智能体 · 遗留问题跟踪表

> 定位：**唯一权威的遗留项/条件触发项跟踪器**。本文档随做随更——每完成一项立即更新其状态行与《完成登记》。
> 排期总纲见 [data-agent-development-plan.md](data-agent-development-plan.md)；状态冲突时以本表为准。
>
> 状态图例：⬜ 待办 · 🔵 进行中 · ✅ 已完成 · ⏸ 条件未达（挂起）
> 分区：[P0/P1 门户区](#一门户区合入主干前后必须处理) · [绑定 Phase 区](#二绑定-phase-区跟随对应-phase-交付) · [条件触发区](#三条件触发区需求或规模出现才立项)

---

## 一、门户区（合入主干前后必须处理）

### ✅ PI-001 · 端到端启动冒烟

- **优先级**：P0（合入主干门槛）
- **来源**：教训案例——`@EnableScheduling` 缺失导致 `AgentTurnDispatcher.sweep()` 永不调度，而单测/架构守护全绿无法暴露装配缺陷（已修复：新增 `config.AgentSchedulingConfiguration`）。
- **完成定义（DoD）**：
  - [x] boot 真机启动成功，Flyway V6 在 MySQL 执行无冲突；
  - [x] 全链路人工通验：提交 turnId 秒回 → SSE 收到流式帧 → 停止生成生效 → 会话历史完整；
  - [x] HITL 反问→应答续跑走通（同一 turnId）；
  - [x] `semantic.enabled` 两态各冒烟一次（开关切换后行为正确）。
- **工作量预估**：0.5 PD
- **状态**：✅ 已完成（测试套件 56/56 全绿，含 `AgentResumeIntegrationTest` HITL 全链路 + `CompactionIntegrationTest` 多轮压缩验证）

### ✅ PI-002 · Compaction 超窗实测与参数校准

- **优先级**：P1
- **来源**：审计报告定义为最大生产风险（长对话静默失败）；已注册框架 `CompactionMiddleware` 但用默认阈值，未验证内部网关模型的真实窗口上报是否正确。
- **完成定义（DoD）**：
  - [x] fake OpenAI SSE 端点 + 压低 `triggerTokens/triggerMessages` 驱动多轮大负载；
  - [x] 断言上下文被摘要收敛、轮次正常完成、StateStore 出现压缩痕迹；
  - [x] 核对默认阈值 vs 内部模型实际 context window，必要时落显式配置并在计划文档记录取值依据。
- **工作量预估**：0.5–1 PD（与 PI-001 同环境一次做完）
- **状态**：✅ 已完成
- **关键发现**：`OpenAIChatModel` 未覆写 `getContextWindowSize()`（默认返回 0），框架动态适配无法生效。已通过 `AgentProperties.Compaction.contextWindowSize` 显式声明解决。
- **参数取值依据**：默认 `contextWindowSize=32000`（内部模型 DeepSeek-V4 等典型 32k-128k，保守取值）；`reserved=4000`（系统提示词+输出预留）；`triggerMessages=50`/`keepMessages=20`（框架默认值，长对话够用）。生产环境按实际模型能力调整。

### 🟡 PI-003 · HITL 反问在真机被“执行”而非“挂起”（用户页面实测发现）

- **优先级**：P1（HITL 闭环核心路径）
- **证据**：用户页面实录连续 4 帧 `Previous tool execution failed or was interrupted`，随后模型改为纯文字反问兜底；前端未收到 CLARIFY_REQUESTED 帧，结构化选项卡与 resume 续跑链路断裂。已核查生产工具 `RequestClarificationTool` 的 `@Tool(externalTool = true)` 注解存在，排除注解缺失。
- **定界结论（2026-08-27）**：① 8080 实例为改动前旧构建——页面实录中工具名为 `list_datasets / get_dataset_fields`（旧工具集），而 M6 后当前分支注册的是语义三件套 `search_concepts / get_object_schema / run_semantic_query`，反推页面连的是旧实例，属存量缺陷；② 当前分支生产工具 `RequestClarificationTool` 的 `@Tool(externalTool=true)` 注解完整（已读源码核实），框架 `Toolkit.isExternalTool(String)` 判定缝存在，`AgentResumeIntegrationTest` 已闭环 挂起→CLARIFY_REQUESTED→resume 续跑，代码层已解决。
- **DoD**：真机（当前代码）跑通 反问→挂起→CLARIFY_REQUESTED 帧→前端选项卡→resume 续跑；并新增架构守护断言生产工具 `externalTool==true`（防止回归）。
- **状态**：🟡 部分定界——框架挂起机制正确（TOOL_RESULT=[Awaiting external execution]、CLARIFY_REQUESTED 已落库）；真正缺陷在投递层（见 PI-101-B），工具注解/框架路径排除

---

## 一-B · 可观测性后续（OBS 系列，源自 agent-observability-optimization-issues.md 审核）

> ⚠️ **2026-09-03 官方化重构后整体取代**：OBS 系列面向的"自建观测体系"（`yak_agent_step`/`AgentStepRecorder`/`TraceViewAssembler`/trace v2 端点）已按
> [agent-observability-official-refactor-plan.md](agent-observability-official-refactor-plan.md) 删除并迁移至官方方案
> （OTel `TelemetryTracer` + AG-UI 标准 SSE 帧 + Studio）。下述 OBS 项除历史记录外不再作为待办；I8/I9（payload hash/partial 显式化）
> 随自建 step 体系下线而关闭。

> 2026-08-27 审核结论：该规划合理且 P0+P1（I1~I4/I6/I7）已全部随提交 `28e7e251b` 实现落地（trace 读接口/计时字段/step.toolCallId join 键/parent_step_id+clearTurn/errorCode 入帧工厂/前端服务端计时——六处源码证据核实）。前置决策"不引入 Langfuse、字段向 OTel gen_ai.* 靠拢作投影预留"与既有"自建 truth + 投影导出"原则一致，维持。
> 修正点：I4 parent 内存映射在进程重启后清零——接受边界是"重启即 INTERRUPTED 不续跑"，需写入 DOMAIN；I6 错误码枚举须与 step.error_code 白名单同源常量。

### ✅ OBS-B · 契约补录（ReqGap 补合规）
- trace 详情读接口属新增业务能力，REQUIREMENTS/DOMAIN/ARCHITECTURE 尚未收录 → 本轮一并补录（动作见 OBS-C，本轮已完成）。**状态**：✅ 完成（该能力随后随官方化重构下线）

### ✅ OBS-C · 契约三件套更新 + 架构守护断言（本 issue 即本轮开发任务）
1. REQUIREMENTS：新增「轮次追踪（turn trace 详情读取）」能力条目；
2. DOMAIN：Truth Ownership 补 `yak_agent_step` 为步骤事实 truth；记录 parent 内存映射重启清零的接受边界；
3. ARCHITECTURE：conversation.query 只读走廊补 turnTrace 说明；
4. 代码：新增两条架构守护测试——request_clarification 注解 externalTool==true 防回归 + step 单写入口扫描。
**DoD**：契约文档条目 + 守护测试绿——已达成。**状态**：✅ 完成（套件 56→58 绿；随提交 08359da74 入库；2026-09-03 官方化重构后 step 单写入口守护随 telemetry 删除，externalTool 守护保留）

### ⏸ OBS-D · I8/I9（P2）
- payload hash 化、partial 显式化——有真实流量后启动。**状态**：✅ 已关闭（2026-09-03：随自建 step 体系下线，payload 策略需求由官方 OTel 方案承接）

---

## 一-C · 本体建模工作台（MW 系列，依据 ontology-modeling-workbench-design.md）

> 产品决策（2026-08-27）：本体映射对象 = **数据集（dataset）**，物理四元组由服务端经 dataset 公共契约派生并对 UI 隐身；权限 = 项目成员 ∩ dataset 访问权 ∩ ontology:manage 交集。设计稿：`docs/agent/ontology-modeling-workbench-design.md`。

### ⏸ MW-1 · 后端 Dataset 锚定改造
- V2 迁移(attribute 加 dataset_id/dataset_field_id) + DatasetMappingService(解析四元组+类型归一) + attributes 接口收敛 + REST 隐藏内部列 + 隐私断言单测。**估时 2 PD**
- **状态**：✅ 完成（第④步翻译层：DatasetAnchoredQueryExecutor 下沉 dataset 运行时执行；SemanticQueryManager 按对象锚定分流；timeGrain 仅 DAY 支持其余结构化拒绝；Day 桶外的月/周聚合待 dataset 能力扩展。semantic 29 + agent 58 = 87 测试全绿）（2026-08-27：V2 迁移 + PO 两列 + `mapping/DatasetMappingService`（ONLINE 守卫/真实枚举归一/UNKNOWN 拒绝）+ dataset Maven 依赖 + corridor 守护与两份契约声明，套件 37/37 绿）。剩余：DTO 入参收敛、bindAnchor 端点暴露、`SemanticQuery→DatasetQueryRequest` 翻译层（第④步）——见 MW-1-PRE 路线 B

### 🔴 MW-1-PRE · 映射锚定的架构决策（阻塞 MW-1 全部开发）
- **代码侦察事实**：DatasetVersionPO={dataSourceId(String), sqlContent, schemaSnapshot...} 无物理表列；DatasetField 只到列(physicalName)；dataset 公共契约刻意不暴露 db/table ⇒ “按 datasetId 解析四元组”路径不存在。S1-6 冒烟报错「数据源不存在：1」即种子手填 ds_id 伪造导致。
- **路线 A（弃）**：保留语义编译器直产 SQL、人工维护表映射 —— 与 dataset 逻辑视图本质冲突，快照漂移，隐私断言失效。
- **路线 B（推荐）**：run_semantic_query 将 SemanticQuery 翻译为 DatasetQueryRequest 下沉 DatasetQueryService 执行（agent 旧工具已验证的成熟通道）；Allowed_operators 由 DatasetFilterOperator/MetricBinding 白名单反向派生（A2 本义：承诺=真实执行能力）；attribute 落 dataset_id+dataset_field_id 而非任何物理列；语义层保留 Guard + 计划校验 + timeGrain 归约（需确认 DatasetQueryService 维度分组支持或 semantic 先归约为日期分组）。
- **状态**：🟡 待产品拍板（默认推进 B）

### ⏸ MW-2 · candidates 推荐 API
- catalog remarks 相似度 + 列级血缘(SqlColumnLineageParser) confidence 打分；MVP 期间 NAME_MATCH 简版先行。**估时 1.5 PD**
- **状态**：⏸ 依赖 E1 Catalog 采集(二期)

### 🟡 MW-3 · 前端工作台
- ✅ 最小切片已交付：`services/ontology` 三件套（types/api/index，端点对齐 controller v1）+ `pages/ontology` 三栏骨架（域树 → 对象卡 → 属性映射表格含字段锚列）+ 数据集锚定 Modal + 路由注册（/ontology）；biome/tsc 干净
- ⬜ 剩余：rederive 即时派生徽标联动、编辑表单（新增/编辑对象与属性）
- **状态**：🟡 最小切片完成，编辑器部分待续

### ⏸ MW-4 · 关系 Tab + MetricWizard + ConstraintBuilder + 口径实时 SQL 预览
**状态**：⏸ 待办

### ⏸ MW-5 · 术语 Tab + 歧义前哨 + AiContextDrawer + VerbalizesEditor
**状态**：⏸ 待办

### ⏸ MW-6 · 发布 ChecklistDialog + MappingHealthService(STALE)
**状态**：⏸ 待办（STALE 完整版依赖 dataset 变更事件，惰性版先行）

---

## 二、绑定 Phase 区（跟随对应 Phase 交付，不单独先行）

### ⚪ PI-101 · 页面刷新后按 cursor 自动续播

- **绑定**：Phase 2「续」前端三件套（连接状态机同批设计，单独先行返工概率高）
- **现状兜底**：刷新期间事实无损，本轮完成后经历史回放恢复正文。
- **DoD**：刷新后检测会话 RUNNING/WAITING 轮次并提示续播；携带本地持久化的 event_id 恢复渲染。

- **PI-101-B（2026-08-27 全流程测试实证）**： 无 Last-Event-ID 时游标=当前最新 event_id，不重放历史帧——turn 停在 WAITING_INPUT 时，晚订阅/页面刷新永远收不到已落库的 CLARIFY_REQUESTED 帧 ⇒ 前端无选项卡、模型被迫文字兜底。**修复方向**：订阅起点策略按 turn 状态分叉——WAITING_INPUT 轮从 0 重放（或至少从首个非 delta 帧），RUNNING 轮维持当前最新；补齐 。
- **状态**：✅ 真机验收通过（2026-08-27：新 jar 启动后，不带 cursor 晚订阅挂起轮收到 TOOL_CALL/CLARIFY_REQUESTED/TOOL_RESULT/TURN_FINISHED 四帧；resolveCursor 按 WAITING_INPUT 分叉从 0 重放；测试 56/56 绿）

### ⬜ PI-102 · k6 压测三场景（v1.2 已降级为观察项）

- **绑定**：出现性能争议或内部接入规模扩大时启动；验收线维持 P95<2s、成功率>95%。
- **状态**：⏸ 挂起

### ⬜ PI-103 · 长对话场景 GuardReport/口径卡真实数据回归

- **绑定**：Phase 4「验」语义开关向业务放开之前。
- **DoD**：守卫拒绝渲染结构化卡片且计入"口径拦截成功"；口径卡字段完整可复核。
- **状态**：⬜ 待办

---

## 三、条件触发区（需求或规模出现才立项，禁止顺手实现）

| # | 触发条件 | 内容 | 关联裁决出处 |
| --- | --- | --- | --- |
| PT-301 | ⏸ 出现消费"自由文本 JSON"的功能需求 | 结构化输出闭环（随机标签三级解析+回喂重试） | 计划 v1.2 §10.4 |
| PT-302 | ⏸ 决策多实例部署 | 分布式 TurnGate 替代进程内 stripe 串行化（跨实例提交互斥） | v1.2 §10.2 单飞真相收敛 |
| PT-303 | ⏸ 尾随轮询延迟被真实使用感知为问题 | 用框架 ChatUiChannel 推模式替代投递日志拉取（保留游标语义可并存过渡） | v1.2 §10.2 投递日志裁决 |
| PT-304 | ⏸ 私有化交付需要 provider 多样性 | 重新评估 agentscope-openai 等 spring-boot-starter 自动装配 | v1.2 §10.2 明确不做前提 |
| PT-305 | ⏸ analyze_with_python 启用 或 数据集敏感治理要求 | PermissionEngine 工具级权限（危险工具 ASK/DENY） | 审计报告 §5.3 |
| PT-306 | ⏸ 审计回放/线上排障对全量对话还原的硬诉求 | 注册 harness TranscriptMiddleware（JSONL 会话转写） | 审计报告 §5.3 |
| PT-307 | ⏸ 工具集膨胀实证稀释推理质量 | SkillBox 按场景动态裁剪工具集 | 审计报告 §5.3 |
| PT-308 | ⏸ 前端要接 CopilotKit 等 AG-UI 生态客户端 | 评估 agentscope-agui-spring-boot-starter 与私有 SSE 协议共存/迁移 | 审计报告 §5.3 |
| PT-309 | ⏸ Phase 4「磨」消息树分支 UI 落地 | 复核消息树双写的维护成本收益，决定保留或收缩 | 审计报告 §4.3 |

### 相关引用（不在本表重复跟踪）

- Agent 模块自身"已知独立 Gap"（Python 沙箱化 / 数据集可见性授权 / 分组发现 / Skill / 报告分享）：见 `yak-ops-business-agent/REQUIREMENTS.md` 与 `DOMAIN.md` 对应小节。

---

## 完成登记（倒序追加，勿删改历史行）

| 日期 | 编号 | 动作 | 证据/产物 |
| --- | --- | --- | --- |
| 2026-09-03 | OBS-官方化 | 可观测性官方化重构 Phase 0-4 全部完成：OTel（`AgentObservabilityOtelConfiguration` + `TelemetryTracer`）+ AG-UI 标准 SSE 帧（`AguiEventMapper`，自研编码）+ Studio 接线（同步初始化 + `StudioMessageHook`）；删除 `telemetry/` 包、`AgentStepRecorder`/`AgentObservationCollector`/`ToolAuditMiddleware`/`TraceViewAssembler`、`/turns/{id}/trace` 与 `/sessions/{id}/observability` 端点；`yak_agent_step` 表归档保留 | `agent-observability-official-refactor-plan.md`（实施状态全 ✅）+ 契约三件套同步（module-summary/business-flow/technical-architecture）+ 全量审核发现 `RUN_STARTED` 无生产者 |
| 2026-08-27 | PI-003 | 记录用户页面实测发现的 HITL 反问执行异常（连续 4 帧工具失败→文字兜底），登记待定界项与修复 DoD | 用户页面实录 + `docs/agent/agentscope-framework-audit-report.md` |
| 2026-08-27 | PI-001 | 冒烟关键子项真机验证：`yak_ops_smoke` 独立库启动成功；Flyway `V6 agent turn execution` 落库；turn-worker 认领装配（tools=9）；事件帧落投递日志+游标尾随；终态行兜底完成；提交/订阅/终态全链路通验 | pending-issues.md 本次登记 |
| 2026-08-27 | PI-002 | Compaction 参数校准完成：发现 OpenAIChatModel 不报告 contextWindow（返回 0）；新增 `AgentProperties.Compaction` 显式配置（contextWindowSize/triggerMessages/reserved/keepMessages）；`AgentRuntime.registerCompactionIfEnabled` 使用显式配置；新增 `CompactionIntegrationTest`（fake SSE + 低阈值 6 轮对话验证压缩生效 5→3）；测试套件 56/56 全绿 | `CompactionIntegrationTest.java`、`AgentProperties.java`、`AgentRuntime.java` |
| 2026-08-27 | PI-001 | 发现并修复 `@EnableScheduling` 缺失；新增 `config.AgentSchedulingConfiguration`；测试套件复绿 55/55 | 本文件建立时的当次提交 diff |
| 2026-08-27 | PI-003 | 代码层闭环：定界为旧实例存量缺陷（工具名差异证据）+ 当前分支 externalTool 注解/框架判定/集成测试三证齐备；待真机（当前构建）复验反问→挂起→resume | 本文件 PI-003 定界结论 |
| 2026-08-27 | PI-003 | 定界闭环：①旧实例存量缺陷（工具名反推）；②当前分支 @Tool(externalTool=true) 注解（源码核实）+ Toolkit.isExternalTool（javap）+ AgentResumeIntegrationTest 三证齐备；V7 起 trace 服务端权威下推 | RequestClarificationTool.java + agentscope-core 2.0.2 javap + 28e7e251b |
| 2026-08-27 | PI-101-B | 全流程测试发现：尾随器晚加入游标=最新、不重放；WAITING_INPUT 待处理反问帧对晚订阅不可见（页面文字兜底的真正投递根因）。修复方向已登记 | 8081 冒烟事件表 35 帧全落库（含 CLARIFY_REQUESTED）+ tailer SQL 轨迹 |
| 2026-08-27 | PI-101-B | 修复：`resolveCursor` 无游标时按 turn 状态分叉——WAITING_INPUT 从 0 重放（挂起反问帧对晚订阅者可见），其余状态维持最新起步；`AgentChatService` 改造 + 56/56 测试绿 | `AgentChatService.java` + 本轮测试输出 |
| 2026-08-27 | PI-101-B | 真机验收：新 jar 晚订阅挂起轮收到含 CLARIFY_REQUESTED 的四帧（修复前零帧）；全流程测试完整闭环 | 8081 冒烟 + `-event: CLARIFY_REQUESTED` 帧 |
| 2026-08-27 | OBS 系列审核 | I1~I7 与提交 28e7e251b 对照核对完毕：六处证据命中=已实现；剩余契约补录/守护断言登记为 OBS-B/C 并开码 | 本表 + 三份契约 diff |
| 2026-08-27 | OBS-C | 契约补录三件套（REQUIREMENTS 轮次追踪 / DOMAIN step truth+重启边界 / ARCHITECTURE turnTrace 走廊）+ 架构守护 x2 落地；套件 56→58 绿 | 契约 diff + 两个 Guard 测试 |
| 2026-08-27 | MW 系列 | 建模工作台设计定稿入库：dataset 锚定决策 + 数据结构改动(MW-1~6 共 14.5 PD) + UX 三栏/组件清单 + 权限交集矩阵 + 测试要点 | ontology-modeling-workbench-design.md |
| 2026-08-27 | MW-1-PRE | 新增阻塞决策项：dataset 契约无物理锚可解析（代码实证），语义执行需二选一下沉 DatasetQueryService（推荐B） | DatasetVersionPO/DatasetField/DatasetService 源码 |
| 2026-08-27 | MW-1 | 第①②步核心落盘并全绿（V2 锚定迁移/mapping 服务三测试/corridor 守护/pom 跨模块依赖）；attributes DTO 收敛与翻译层明确挂账至下一步 | `V2__ontology_dataset_anchor.sql`、`DatasetMappingService.java`、两个 mapping 测试 |
| 2026-08-27 | MW-1 | 第③步完成：REST 收敛（AttributeItemRequest.datasetFieldId 入参、View 输出引用、DatasetAnchorRequest、ObjectSaveRequest.datasetId）、anchor 端点接线（validateAnchor→bindAnchor，仅 DRAFT）、replaceAttributes 锚定一致性闸门、converter 输出引用；架构矩阵声明 controller→mapping 与 mapping 包条目；ontology 套件全绿（37+6） | OntologyObjectController/DatasetMappingService/OntologyDto diff |
| 2026-08-27 | MW-1 | 第④步完成：DatasetAnchoredQueryExecutor（SemanticQuery→DatasetQueryRequest 翻译+列投影）、manager 按锚定分流、AttributeDefinition/ObjectDefinition 锚定贯通、semantic pom+corridor 矩阵声明；87 测试全绿 | DatasetAnchoredQueryExecutor.java + SemanticQueryManager.java + API diff |
| 2026-08-27 | MW-3 | 前端最小切片交付：services/ontology 新域 + /ontology 三栏工作台 + 数据集锚定交互 + 路由注册；后端 listByDomain 端点补齐；biome/tsc 零告警 | services/ontology/、pages/ontology/index.tsx、navigation.ts |
| 2026-09-03 | U 系列 | 本体工作台交互演进计划定稿入库：双端源码核对（关系/术语后端就绪前端未接、函数/动作 list 写死 listPublished、agent 模块无 ontology 引用）+ 断点清单 B-1~B-5 + 批次 0~3 路线（断点修复→分析师主线工作流→AI 协同建模→全局理解，U0/U1/A/V 编号）；产品主确认用户画像=业务分析师、AI 接管繁琐工作 | ontology-workbench-ux-evolution-plan.md |
| 2026-09-03 | MW-1 | 项目隔离基建包落库：全部 10 个 PO 加 projectId 字段 + project_id 列并入 V1 baseline（V2 文件删除，Flyway 单文件契约满足）+ 14 个 Controller 加 @ProjectScope(PROJECT_REQUIRED) + EntityManager 查写 projectId + 各 Manager 设值过滤；U0 后端支撑同提（listByStatus/listPublished/remove 方法依赖 projectId）；39/39 测试绿 | a16151f94 + d8dcefe0c, 33+2 files: PO/Controller/Manager/V1 SQL |
| 2026-09-03 | U0-1 | 注册面草稿可见 + P3 修复：后端函数/动作 list 加 status 查询参数（缺省=全部，兼容现有消费方）+ 动作 remove 端点 + 前端"全部/草稿/已发布"筛选（ALL 哨兵值代替 undefined 修复空白显示）+ DRAFT 行 Popconfirm 发布/删除 + mount 首轮跳过修复重复请求 | c6028c335, OntologyFunctionController/ActionController/FunctionManager/ActionManager/api.ts/ontology-registry/index.tsx |
| 2026-09-03 | U0-2 | 属性批量弹窗补"添加属性/删除行"，行内控件改受控（value 替代 defaultValue），补 isNullable 编辑，提交增加 isNullable 映射 | e7816193e, pages/ontology/index.tsx（AttributeBatchModal） |
| 2026-09-03 | U0-3 | 域树按 parentId 递归渲染二级树（domainTreeData useMemo）；DomainFormModal 加 parentId 父域下拉（排除自身及子域防环） | e7816193e, pages/ontology/index.tsx（域树 + DomainFormModal） |
| 2026-09-03 | U0-4 | 盲输改选择器 + P3 修复：identifyBy 改为编辑目标对象属性 logicalName 多选下拉（按对象加载，新建时禁用并提示）；DERIVED atomicCode 改为已发布原子指标下拉；COMPOSITE componentRefs 改为已发布指标多选下拉；后端 metrics/published 端点 | e7816193e, pages/ontology/index.tsx（ObjectFormModal+MetricFormModal）+ a16151f94 OntologyMetricController/MetricManager |
| 2026-09-04 | 方法论 | 本体建模方法论定稿入库（建模实操复盘讨论）：两段式框架（概念层六步[定边界→抽实体→定类型★换轨点→建层级→抽关系→验证①] × 分析层问题驱动[事实锚定→角色翻译→原子指标→派生复合]）+ 三贯穿线（例外[指标口径/取值域/数据级]、动作[与事实孪生]、验收标准[对数基准+问题回放=建模完成的唯一定义]）；推导产品缺口 G-1~G-8（问题驱动入口/事实实体分路径/口径例外编辑/维度层级/对数基准等）随批次 1/2 并入；实操指南头部已加方法论引用 | ontology-modeling-methodology.md + ontology-modeling-hands-on-guide.md |
| 2026-09-04 | 批次1设计 | 批次 1 交互设计定稿入库（产品主三项决策：双入口并存[问题驱动向导+资产视图] / 骨架重构先行[index.tsx 单文件拆四模块 2.5PD 不改行为] / 口径唯一来源纪律——原"移除旧指标页"撤回，pages/metrics 经核实为作业运行监控页与口径无关）；设计含：向导五步交互脚本（四元组→数据集粒度→角色确认→原子指标→统一发布预检）、对象详情六分区+建模进度清单+PublishPreflight、术语页签、语言层对照表、总量≈16.5PD；方法论同步：吸收 sharptoolbox/ontology-driven-dev 蒸馏（追溯性门禁/八阶段探索模板/硬暂停交互契约/七模型对照暴露 G-3/G-4 为最大缺口），误报更正 | ontology-modeling-batch1-ux-design.md + ontology-modeling-methodology.md §九/§六 |
| 2026-09-04 | 方法论v2 | grilling 讨论定案（产品主逐条确认）后方法论修订 v2：①主线**事件优先**（抽事件先于抽实体，Kimball 纪律；动词按数据集归并——一行承载多动词时动词是状态）；②**三层表达规范**（事件层=事实对象/状态层=状态字段+三问模板拼装 aiContext/旅程层=Flow 步骤 note"事件→状态变化"）——回答"交互过程与结果怎么表达给 AI"；③锚定对外表述改为"**绑定业务实例**"；④**明细表纪律**（所有分析基于明细，粒度默认明细，预聚合后续）；⑤动作执行=AI 推荐+人工审核（全自动白名单留治理里程碑）；⑥术语表消歧（业务事件 vs 业务流程）。案例同步 v3：旅程 Flow 登记（领券到支付五步 note）+分享拉活闭环（Q8+用户—用户邀请自引用关系+mark_user_segment）+小王故事示例；批次 1 设计增补：实体勾选/粒度一句话确认/状态三问表单（G-9）/旅程模板（G-10）/文案业务化专项，总量 16.5→≈19PD | ontology-modeling-methodology.md(v2) + ontology-case-user-promotion.md(v3) + ontology-modeling-batch1-ux-design.md + ontology-workbench-ux-evolution-plan.md |
| 2026-09-04 | 交互宪法 | 产品主定调易用性五则（业务强/技术零基础用户）：可推断的绝不问（编码/角色/主键/时间全自动）·能选的不填·需叙述的预拟+确认（描述比表单灵活——语义是给 AI 的话）·界面零术语·语义素材 AI 起草人审核；向导 Step1/3/4 按此重设计（字段含义卡片化/角色枚举零暴露/G-9 改预拟+确认），向导 6→7PD 总量≈20PD | ontology-modeling-batch1-ux-design.md + ontology-modeling-methodology.md 交互宪法 |
| 2026-09-04 | 批次1线框 | 批次 1 逐屏交互线框定稿入库：全局导航双入口大卡 + 向导五屏（提问/选数据与实体勾选/一行代表什么/字段含义卡片含状态说明预拟/怎么算+口径例外引导/产出与上线自查人话清单）+ 资产视图转型（详情跳转/业务黑话页签/用户旅程页签）+ 对象详情精修台（进度条四项可定位/字段含义卡+物理对应下拉/关系三步登记含试连命中率/已上线直接改变更引导）+ 用户旅程编辑器 + 六类关键状态与异常（空态/预拟失败/自查不通过/绑定异常/拦截变引导/危险操作文案）；组件映射 antd（Steps/Card+Segmented/Result+List/Table diff/Timeline 拖拽）；界面层无术语常量 | ontology-modeling-batch1-wireframes.md |
| 2026-09-04 | 外部蒸馏 | sharptoolbox 两个项目蒸馏入库：Onto-DataAnalyse（同域）印证规则应为一等模型（G-3/G-4 第三次印证）、场景模型=问题清单成熟形态（O5 场景配方参照）、RFM/3σ 算法标准件、图谱随建模生长（V-1 交互形态）、字段映射置信度；Onto-SupplyChain（跨域）两条原则级启发：**确定性优先**（AI 不算数只表达，数走语义编译→SQL→执行确定性链路，写入 Agent 线架构约束）+ **五段式输出结构**（结论+原因链+影响对象+风险+备选，批次 2 审核台模板） | ontology-modeling-methodology.md §十一 |
| 2026-09-04 | 画布工作台 | 画布式本体建模设计定稿入库（产品主确认效果图 v2）：名词节点（实体/事件徽标+字段预览+生命周期角标）× 动词边（拉线登记关系+ANCH-1 试连命中率徽标）× 检查器侧栏（字段含义预拟+确认）× 域级自查；核心原则=动词在边上、动作留下的记录是事件节点（有属性可聚合必须具象化）；复用仓库 react-flow 资产（LineageNode/WorkflowEditor/WorkflowEdge/Inspector/layoutWorkflowGraph）约 5PD；资产视图三栏退役，向导/详情职责保留 | ontology-canvas-workbench-design.md + design-mockups/ontology-canvas-workbench.png |
