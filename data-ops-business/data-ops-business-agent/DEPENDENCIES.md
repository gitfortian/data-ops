# Agent Dependency Rules

本文件定义 Agent 的长期包依赖 contract。`ARCHITECTURE.md` 解释为什么这样分层，本文件回答：**一个 package 可以依赖谁、跨子系统必须经过哪条走廊、哪些反向依赖绝对禁止。**

这些规则由 `AgentDependencyBoundaryTest` 扫描 `src/main/java` 的真实 import 并执行，文档与测试必须同步修改。

## 1. 原则

Agent 使用**显式、窄、无环**依赖图：

- package 是架构，不把职责藏进 `service / common / helper / utils`；
- 上层可以依赖下层，下层不反向调用 Application；
- 跨子系统优先通过稳定 Facade / Resolver / Gateway / Repository contract；
- 允许的 dependency 不等于鼓励依赖，能留在本子系统就不要跨包；
- 新增一条跨包 import 前，先确认它是否需要成为长期 corridor；
- 所有 Agent top-level package 必须保持无环。

## 2. Top-level dependency matrix

`A -> B` 表示 A 可以 import B。未列出的依赖默认禁止。

| Source | Allowed Agent targets |
| --- | --- |
| `controller` | `conversation`, `config`, `report`, `domain` |
| `conversation` | `runtime`, `repository`, `domain`, `telemetry`, `config`, `memory` |
| `conversation.query` | `repository`, `domain`, `telemetry`（仅只读元数据投影，见第 9 节） |
| `runtime` | `toolset`, `config`, `domain`, `telemetry`, `memory`, `repository` |
| `toolset` | `catalog`, `gateway`, `report`, `domain`, `telemetry` |
| `catalog` | `gateway`, `domain` |
| `gateway` | `config`, `domain`, `catalog`（仅白名单校验，见第 6 节）, `repository`（仅证据留痕 corridor，见第 7 节） |
| `memory` | `repository`, `dao`, `config`（M1 已接线；M2 巩固管线规划中，见 ARCHITECTURE §Memory） |
| `report` | `repository`, `domain` |
| `repository` | `dao`, `domain` |
| `dao` | `config` |
| `telemetry` | `dao` |
| `config` | `domain` |
| `domain` | none |

根包公共类型（`AgentPermissionCode` 权限码常量）与模块装配开关注解
（`config.ConditionalOnAgentEnabled`）视为模块基础设施，允许所有子系统引用，不计入依赖矩阵。

同一 top-level package 内的 subpackage import 不视为跨子系统，例如 `conversation -> conversation.query` 属于同一 Conversation area。

注意两个结构性收敛：

- `conversation` 不 import `toolset / catalog / gateway`：工具由 runtime 的 Toolkit 机制反射触发，调用方向天然收敛，不需要编排层直连；
- `catalog` 与 `toolset` 都不直接触达 dataset：一切 dataset 访问经 gateway。
- `telemetry` 是步骤级执行记录子系统（`yak_agent_step` 唯一写入口）：runtime/conversation 落轮次汇总，toolset 落工具调用证据；记录失败只告警不反向影响执行事实。

## 3. Stable inbound corridors

Controller 不得自行选择内部角色。HTTP inbound 固定进入以下 Application Facade：

```text
AgentController
   ├── conversation.AgentChatService               # POST /chat/turns（提交/续跑）、GET /chat/turns/{id}/events、cancel/rename/delete
   ├── conversation.query.AgentSessionQueryService # sessions / history
   ├── conversation.AgentConfigManageService       # 运行时动态配置治理读/写（第 4 个稳定 Facade，见第 10 节）
   └── report.AgentReportService                   # reports 分页 / 详情 / 删除
```

Controller 可以使用 `domain` 类型完成 transport mapping，但禁止直接依赖 Repository、DAO、Gateway、runtime 内部角色。

## 4. toolset -> report corridor

工具执行正常职责不应感知报告持久化细节。唯一允许的跨边界入口是：

```text
toolset
   -> report.AgentReportService
```

当前用途是 `save_analysis_report` 工具落库。toolset 不得直接 import：

- `report` 包内部角色；
- `repository.*`；
- `dao.*`。

这样报告存储不会出现第二个写入方。

## 5. SDK 白名单边界

以下第三方类型在模块内实行**单一子系统白名单**，白名单外出现即违规：

| SDK / 外部类型 | 允许的子系统 | 说明 |
| --- | --- | --- |
| `io.agentscope.*` | `runtime`（toolset 见下方豁免） | ReActAgent / Toolkit / Model / StateStore / middleware；含 `agentscope-harness` 的 Middleware 全家桶（LlmResilience / ToolAudit / SystemPromptAssembly 在 core 拦截点实现，Compaction 来自 harness），白名单子系统不变 |
| `reactor.core.*` | `runtime`、`toolset` | runtime 订阅事件流；toolset 仅限工具异步执行（Mono + boundedElastic 卸载阻塞调用） |
| `io.yak.ops.business.dataset.*` | `gateway` | 仅公共契约类型；dataset 内部实现类型（dao/dao.model/repository.impl）一律禁止 |

补充约束：

- 官方 MySQL / PostgreSQL 扩展的 `MysqlAgentStateStore` / `PostgresAgentStateStore` 装配类位于 `runtime`（`AgentStateStoreWiring`），DataSource 注入来自平台共享数据源 Bean；消息历史继续由 SDK StateStore 单独拥有；
- 模型 Provider 扩展（openai / dashscope 等）只被 runtime 引用；
- **toolset 豁免**：工具注解 `io.agentscope.core.tool.Tool`、`ToolParam` 与上下文注入参数
  `io.agentscope.core.agent.RuntimeContext` 允许出现在 `toolset`——它们是框架注册工具与传递会话身份的声明式机制，
  其余 agentscope 类型仍仅限 runtime；
- gateway 对上暴露的签名不允许出现 dataset 类型，必须以 domain 值对象承接。

## 6. Gateway -> Repository 证据留痕 corridor

Gateway 通常只依赖 `config` 和 `domain`。存在一个刻意保留的窄 persistence corridor：

```text
gateway.DatasetQueryGateway
   -> repository.QueryLogRepository
   -> catalog.FieldWhitelistValidator   # 白名单校验下沉：拒绝路径与证据留痕同边界（REJECTED）
```

原因与 realtime 的 engine -> runtime identity corridor 同构：**查询证据必须在执行边界恰好落一条（成功或失败都落），不能依赖上层调用方自觉。**

白名单校验下沉到执行边界内完成（工具保持薄壳）：`FieldWhitelistValidator` 拒绝（`[FIELD_WHITELIST_REJECTED]`）与数据集未上线（`[DATASET_OFFLINE]`）都在网关内落一条 `REJECTED` 的 query_log，之后才上抛回喂模型自纠。

因此允许 Gateway import `QueryLogRepository` 与 `FieldWhitelistValidator`，但不允许把这条例外扩展成：

```text
gateway -> SessionRepository
gateway -> DAO
gateway -> dataset 内部实现类型
```

## 7. Runtime Environment corridor

Agent 无自有运行环境概念；模型与状态存储配置全部来自 `config.AgentProperties`。

规则：

- 其他子系统读取自身行为配置时只允许 import `config`（见矩阵）；
- 不允许任何子系统绕过 config 直接读 Spring Environment / application.yml 原始键；
- 密钥字段只存在于 runtime 装配路径上（见 DOMAIN 硬规则 6）。

## 8. Persistence boundary

目标方向：

```text
Application / Internal roles
        ↓
Repository contracts
        ↓
Repository adapters
        ↓
DAO
```

规则：

- Repository contract 不暴露 MyBatis PO / Mapper / Controller DTO；
- Repository implementation 可以使用 DAO / domain / module config；
- DAO 只处理 persistence primitives，不调用 Application、Gateway、Repository；
- Core Domain 不依赖 Repository / DAO / Gateway / Controller / Spring；
- `query_log.request_json` 的 JSON codec 位于 `repository.support`，服务于持久化投影，不是第二套业务契约。

## 9. Query / read-side rule

`conversation.query` 与报告分页查询可以组合：

```text
Repository projection + domain 值对象 + StateStore 只读证据（历史回放走廊）
```

历史真相由 runtime 持有的官方 StateStore 承载，历史回放无法绕开它。因此存在一条刻意保留的窄只读 corridor：

```text
conversation.query.AgentSessionQueryService
   -> runtime.AgentRuntime#history（仅此只读方法）
```

另有一条只读元数据走廊：trace/观测视图组装（`TraceViewAssembler`）消费
`telemetry.AgentKindRegistry / RenderHint / PayloadEnvelope` 等**只读投影常量**，
不触发任何写入（见矩阵 `conversation.query` 行）。kind 真值的单一真相仍在
`telemetry.AgentKindRegistry`，read side 只读不写。

它与 realtime 的 observability 读取 Flink evidence 同构：read side 可以组合外部证据，
但不得依赖 runtime 的命令角色（stream / resume / 装配），也不得因读取失败反向写任何状态。

仍不得依赖：

```text
toolset / catalog / gateway
DAO / Mapper PO
```

read side 的失败作为读取失败返回，不能反向写会话元数据或报告状态。

## 10. `@Service` reservation

`@Service` 只允许稳定 Application Facade：

```text
conversation/AgentChatService.java
conversation/query/AgentSessionQueryService.java
conversation/AgentConfigManageService.java   # 运行时动态配置治理（Phase2-B 治理 API）
report/AgentReportService.java
```

Skill 管理已有独立 use-case conversation/AgentSkillManageService.java，与现行测试白名单一致；新增其他稳定入口需证明真实用例。内部角色仍使用 `@Component` 或普通对象。

## 11. Forbidden buckets

production Agent 不允许重新出现以下 top-level 业务桶：

```text
service/
common/
helper/
utils/
```

真正通用的技术能力应该有明确边界，例如 `runtime`、`gateway`、`controller` 的 transport mapper、`repository.support`；不要用模糊目录逃避角色命名。

## 12. Change protocol

任何改变本文件依赖矩阵或 corridor 的 PR 必须同时：

1. 说明为什么现有边界无法表达该需求；
2. 更新 `ARCHITECTURE.md`（如果架构语义发生变化）；
3. 更新 `DEPENDENCIES.md`；
4. 更新 `AgentDependencyBoundaryTest`；
5. 证明依赖图仍然无环；
6. 确认没有建立第二个 truth owner。

只为了绕过 architecture test 而扩大白名单，不是可接受的修复。


## F-009 外部只读 API 走廊

仅 gateway 可 import `io.yak.ops.business.asset.api.*` / `io.yak.ops.business.quality.api.*`。任何 Agent 包禁止引用它们的 application/execution/repository/dao/controller 实现。toolset 可消费 SPI SectionType 参数枚举，真实读取停在 gateway。身份恢复只经 core UserExecutionScope，不进入 IAM DAO；Boot 是唯一身份适配器，源域业务仍只读取认证上下文。

## F-010 候选读取

沿用 gateway -> asset.api / quality.api；QualitySuggestionQueryApi 为新增窄只读契约，不引入反向依赖。

collector 调用方新增精确登记 runtime/GovernanceContextMiddleware 与 runtime/EffectiveConfigMiddleware，分别拥有入口预读 trace 和有效配置 trace；CollectorBoundaryGuardTest 按类名锁定，toolset/gateway 继续禁止触达采集入口。

## 任务范围、Skill 与预算

本次沿用既有依赖图，不新增 top-level 包或走廊。SDK 的预算 State record、TaskScopedTool、模型过滤和 Skill 加载适配仅在 runtime；domain 的策略/额度不依赖 SDK；toolset 只经现有 RuntimeContext 读取 domain 并保持零采集依赖。

既有官方 Skill 类型豁免为 runtime/conversation/repository（管理 Facade 与官方 SPI 适配器），与 AgentDependencyBoundaryTest 一致。runtime -> memory/repository 为已接线记忆/动态配置走廊，conversation -> memory 为轮次完成提取走廊；不扩展到源域 DAO。


## F-023 场景 Skill 标准匹配

F-023 单字段 TYPE 标准匹配：用户从 Modeling 原字段草稿发起现有持久化轮次。源域授权、有界候选、Skill 同轮快照与活版本核验、SDK 结构化 call；候选经源域复核后才发布/带入。待确认项是完成轮次的问题清单，补充后重新生成，不引入新的 HITL 状态。草稿不是事实，AI 不保存业务。

依赖：Agent runtime → toolset → gateway → semantic.api / modeling.api；源域不依赖 Agent，不新增状态机或第二业务真相。合同见 [F-023](../../docs/product/features/F-023-skill-standard-match.md)。


## F-024 模型来源映射 Skill

Modeling 拥有单列来源映射与目标字段，授权 MappingSuggestionQueryApi 读取固定源表的 fresh 元数据并有界交付。Agent 仅 gateway → modeling.api 消费，不直接读取 Datasource 内部实现。复用原轮次、SDK Skill 与结构化调用；候选仅进入原表单，人工 If-Match 保存。映射写路径持有模型行锁，字段及单列映射用 locking read；保留标准字段关联，冲突先于写入。外部 DDL 校验只证明读取时刻，不宣称跨库原子性。详细边界见 [F-024](../../docs/product/features/F-024-skill-model-mapping.md)。


## F-025 指标版本口径 Skill

MetricExplanationQueryApi 是 Metric-owned 授权只读投影：固定当前版本，读取不可变快照并复用 digest；仅白名单有界事实，超界/缺快照不可用。Agent 仅 gateway → metric.api，源域不反向依赖 Agent。复用 SDK 场景执行与原表单，候选仅 businessDesc，人工保存复用 expectedVersion、校验、审计和回读；验证/发布仍独立。引用校验不等于自然语言正确，真实模型验收 PENDING。合同见 docs/product/features/F-025-skill-metric-caliber.md。


## 场景辅助与 J2 原页面交接（F-026/F-027/F-028/F-029）

原轮次与 SDK 状态仍拥有执行/消息。批量是最多五项的客户端顺序选择；每项有独立原轮，提交或终态不确定时停止后续项。类型与说明候选、指标定义草稿均需活权限/源版本/Skill 重查。精确历史指标解释仅阅读，CURRENT 仍拒绝漂移。场景不开放业务写工具。

依赖仍是 Agent runtime → toolset → gateway → 源域 api；Modeling/Semantic/Metric 不依赖 Agent。复用现有保存、权限、项目与审计，无新业务状态机/事实库。精确合同见 docs/product/features 下相应 Feature。

MetricDraftTarget/METRIC_DRAFT 固定 metric-definition-draft Skill；runtime 复用 structuredScenario（AgentScope 2.0.3），仅 load_skill_through_path/get_metric_draft_context/generate_response，纳入同一轮预算与历史交付。MetricDraftGateway 只依赖 MetricDraftQueryApi，源域校验与 Skill 指纹复核都通过才可交付/带入。历史说明显式 view=SNAPSHOT；旧 target 无 view 仍只允许当前版本。
