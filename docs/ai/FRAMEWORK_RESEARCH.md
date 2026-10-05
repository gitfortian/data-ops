# AI 框架选型调研证据

> 状态：调研证据与选型建议，不是 ACCEPTED Product Decision 或 APPROVED Feature Spec。
> 调研 / 官方来源访问日期：2026-10-05（Asia/Shanghai）。所有外链均在该日核验；版本页面会继续变化。
> 方法：仓库静态阅读 + 官方文档、固定版本源码及发布记录。没有升级依赖、跑框架性能测试或验证真实模型 E2E；文档支持与项目交付状态严格区分。

## 1. 推荐结论

**继续使用 AgentScope Java，保持现有 `ReActAgent` 为唯一对话推理运行时。第一阶段投入受治理工具、身份传递、证据、结构化建议和评测，而不是整体换框架。** 这是结合当前实现与迁移成本的工程判断，不是官方结论。

- 保留当前 `2.0.2` 基线；独立评估 `2.0.3` 的修复与状态并发能力，不与 AI 产品扩展一起升级。[AgentScope 发布记录](https://github.com/agentscope-ai/agentscope-java/releases)
- 把 Spring AI 作为未来模型 / 检索 / MCP 基础设施候选，把 LangChain4j 作为 Java 替换对照候选；两者都先解决当前 Spring Boot 版本兼容，禁止未经验证引入第二套会话运行时。
- 只有真实需求需要跨日中断、人审恢复和确定性多步骤控制时，再对 Spring AI Alibaba Graph 做小规模验证。当前 stable 的 AgentScope 包装器依赖 **AgentScope 1.0.9**，不能直接假设兼容现有 `2.0.2`。[包装器固定版本 POM](https://raw.githubusercontent.com/alibaba/spring-ai-alibaba/v1.1.2.2/spring-boot-starters/spring-ai-alibaba-starter-agentscope/pom.xml)
- LangGraph 官方 Python / JS 可以作为独立计算服务的复杂编排候选；引入额外语言、部署与授权链路的成本，需由真实需求证明。Java 社区 LangGraph4j 单独评估，不把官方 LangGraph 的承诺自动归给它。

## 2. 先从用户结果定位框架职责

| 必答项 | 本次选型的定位 |
| --- | --- |
| User | 数据资产使用者、数据治理人员、数据开发与运行维护人员 |
| Problem | 找到可信资产、理解治理规则、构建规则草稿、解释运行证据耗时；AI 容易引用无权访问或过期的事实 |
| Capability | 基于当前治理事实的检索、解释、结构化建议及有限工具协助 |
| User Journey | 进入现有资产 / 质量 / 安全 / 任务页面 → 读取授权证据 → 获取建议 → 校验 → 用户在现有流程中采纳 → 回看结果 |
| Expected Outcome | 缩短完成任务时间，保持事实可追溯、权限有效、变更可审查 |
| Truth Owner | 原业务域拥有资产、字段语义、规则、审批、执行结果；AI 拥有推理运行记录，模型文本不是业务事实 |
| Producer / Consumer | 业务域 API 产出当前事实；受治理工具读取；模型产出建议；领域校验器和用户消费建议 |
| Existing capabilities to reuse | Dataset 查询、Project Space、权限、Audit、Approval、任务与工作流、Agent 现有轮次 / 事件 / 报告能力 |
| E2E acceptance evidence | 跨用户 / 项目授权、拒绝越权、引用有效版本、规则草稿经原领域校验、失败有证据、恢复不重复业务副作用 |

质量门禁、消费安全映射、生命周期治理等新增产品规则，必须按产品治理流程确认；框架自带 HITL、memory 或 graph，不构成新增产品状态机的授权。

## 3. 仓库现状与选型约束

本地文件链接只代表本次工作区的静态实现事实，不代表已上线或验收通过。

| 已核实事实 | 对选型的影响 | 本地证据 |
| --- | --- | --- |
| 根 POM 为 Java 21、Spring Boot **3.3.13** | 不需要为 Java 17 候选降级；最新 Spring AI / LangChain4j starter 不能当作当前 Boot 的受支持即插即用组件 | [根 POM](../../pom.xml) |
| AgentScope BOM **2.0.2**；已引入 harness、OpenAI、MySQL / PostgreSQL、Studio、AG-UI | 已有依赖与迁移资产可复用；引入 harness 依赖不代表已使用 HarnessAgent 全部能力 | [Agent POM](../../data-ops-business/data-ops-business-agent/pom.xml) |
| 手工装配 `ReActAgent`，OpenAI provider；Toolkit、Middleware、StateStore、RuntimeContext 接线 | Provider 配置目前不是框架支持列表的完整实现；只在运行边界替换 SDK | [AgentRuntime](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/AgentRuntime.java) |
| 对话消息 / 推理状态由官方 StateStore 拥有；轮次、事件投递日志、报告、查询证据各有单一 Owner | 不能让新框架 memory / checkpoint 再成为第二份消息或业务真相 | [架构契约](../../data-ops-business/data-ops-business-agent/ARCHITECTURE.md)、[领域契约](../../data-ops-business/data-ops-business-agent/DOMAIN.md) |
| Dataset 是结构化取数边界；模型不直接生成或执行 SQL；HITL 为用户澄清 | “框架有 SQL Agent / 审批 hook”不等于本平台应开放 SQL 或审批业务写入 | [需求契约](../../data-ops-business/data-ops-business-agent/REQUIREMENTS.md) |
| 当前崩溃后的 RUNNING 收敛为 INTERRUPTED；排队轮次重新分发；SSE 日志可补播 | **事件重放、会话持久化、业务断点恢复是三件事**；不能因框架宣称 resume 而宣称项目已实现崩溃续跑 | [需求契约](../../data-ops-business/data-ops-business-agent/REQUIREMENTS.md) |
| Skill 热加载、memory 召回代码已有接线，需求 / 领域文档仍保留部分“规划中”描述 | 当前有文档与实现漂移；先核对范围与验收，不把源码存在当产品交付证据 | [AgentRuntime](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/AgentRuntime.java)、[需求契约](../../data-ops-business/data-ops-business-agent/REQUIREMENTS.md) |

### 3.1 应优先验证的实现风险

1. **查询主体未显式传递。** `DatasetQueryGateway` 调用两参 `query(datasetId, request)`；Coordinator 两参入口把主体置为 null；SecurityGate 拒绝 null 主体。推断：完整安全装配下 Agent 取数可能被拒绝。需真实装配测试确认，不能用换框架解决，也不能绕过安全门禁。[Gateway](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/gateway/DatasetQueryGateway.java)、[Coordinator](../../data-ops-business/data-ops-business-dataset/src/main/java/io/yak/ops/business/dataset/query/DatasetQueryCoordinator.java)、[SecurityGate](../../data-ops-business/data-ops-business-dataset/src/main/java/io/yak/ops/business/dataset/query/DatasetQuerySecurityGate.java)
2. **SQL 证据回喂模型。** 工具把实际执行 SQL 附到结果里；这不等于模型生成 SQL，但可能暴露物理结构或字面量。应审查模型出口的数据最小化与脱敏策略，保留受控 queryId 回链即可作为一种候选设计。[RunDatasetQueryTool](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/toolset/RunDatasetQueryTool.java)
3. **Python 隔离尚非 OS 沙箱。** 当前网关是临时目录、`python -I`、超时与信号量；官方 `-I` 说明只涉及导入路径和 Python 环境变量。推断：不能据此证明文件、网络、进程和凭据隔离；维持默认关闭，启用前验证独立受限身份 / 容器方案。[PythonRunnerGateway](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/gateway/PythonRunnerGateway.java)、[Python 官方命令说明](https://docs.python.org/3/using/cmdline.html#cmdoption-I)
4. **运行安全边界必须再审计。** 当前项目上下文 middleware 已存在，但用户主体、权限撤回、长期记忆、工具结果、报告、观测内容是否在每条路径遵守同一权限边界，不能由框架的 session 隔离推导；需功能 E2E 与负向案例证明。

## 4. 版本、运行基线与许可证

“当前”只表示访问日官方页面状态；核心、starter、checkpoint、CLI 必须分别锁版本。

| 候选 | 官方版本 / 基线证据 | 对当前仓库的含义 | 开源许可证 |
| --- | --- | --- | --- |
| AgentScope Java | 项目用 2.0.2，官方最新发布 2.0.3；2.0.2 POM Java17；2.0 有 API breaking changes。[发布记录](https://github.com/agentscope-ai/agentscope-java/releases)、[2.0.2 POM](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.2/pom.xml)、[2.0 概述](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/index.md) | 当前 JDK21 满足编译基线；已有手工装配；不据此声称所有 starter 与 Boot3.3 都兼容 | Apache-2.0，固定版本 POM 声明 |
| Spring AI | 官网 stable 2.0.1 / 1.1.8 / 1.0.9；2.0 支持 Boot4.0/4.1；1.1.8 固定源码注明 Boot3.4/3.5。[版本列表](https://docs.spring.io/spring-ai/reference/spring-projects.html)、[2.0 入门](https://docs.spring.io/spring-ai/reference/getting-started.html)、[1.1.8 入门源码](https://raw.githubusercontent.com/spring-projects/spring-ai/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc) | 当前 Boot3.3.13 不在上述支持范围；先做平台兼容计划；不用新 BOM 强制覆盖平台版本 | [Apache-2.0](https://github.com/spring-projects/spring-ai/blob/main/LICENSE.txt) |
| LangChain4j | 入门示例 core 1.21.0；starter 1.21.0-beta31；最低 JDK17；官方 starter 支持 Boot3.5+ / Boot4。[入门](https://docs.langchain4j.dev/get-started/)、[starter 源码说明](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/spring-boot-integration.md) | 可在独立 Java PoC 比较；当前 Boot3.3 不在 starter 支持范围；core stable 不等于所有 agentic / starter 组件 stable | [Apache-2.0](https://github.com/langchain4j/langchain4j/blob/main/LICENSE) |
| Spring AI Alibaba | latest stable 1.1.2.2；2.0.0-M1.1 为预发布；stable POM JDK17、Spring AI1.1.2 / Boot3.5.8。[发布记录](https://github.com/alibaba/spring-ai-alibaba/releases)、[stable POM](https://raw.githubusercontent.com/alibaba/spring-ai-alibaba/v1.1.2.2/pom.xml) | 不是直接替换 AgentScope 的兼容升级；要验证 Spring 基线和包装器版本；不选 M1 作为首阶段生产基线 | [Apache-2.0](https://github.com/alibaba/spring-ai-alibaba/blob/main/LICENSE) |
| 官方 LangGraph | Python 与 JS / TS 官方路线；Python 发布页包含 langgraph 1.2.12；CLI / SDK / checkpoint 包独立发布。[官方栈说明](https://www.langchain.com/oss-overview)、[Python 发布页](https://github.com/langchain-ai/langgraph/releases) | Java17 不是它的嵌入基线；需独立服务边界。JS 精确版本及运行最低版本本次未锁定，不作部署结论 | [MIT](https://github.com/langchain-ai/langgraph/blob/main/LICENSE)；商业 LangSmith / Agent Server 条款须另核验 |
| LangGraph4j（社区） | 官方项目自述 inspired by LangGraph；README 列 1.9.3（2026-10-01）、JDK17+；1.9 含实验特性，1.8 分支声明 LTS。[项目说明](https://github.com/langgraph4j/langgraph4j) | 独立社区项目，不能当 LangChain 官方 Java runtime；维护承诺以该项目为准 | MIT，项目仓库声明 |

许可证为各项目主要源码许可证；模型 API、云服务、传递依赖与企业支持条款需按实际使用单独检查。本次没有做完整依赖 SBOM 或漏洞扫描。

## 5. 能力矩阵：支持机制不等于治理闭环

### 5.1 Java 运行时候选

| 能力 | AgentScope Java 2.0.2 | Spring AI | LangChain4j | Spring AI Alibaba |
| --- | --- | --- | --- | --- |
| Agent / 工具 | ReActAgent、Toolkit、Middleware，已有本地接线；[官方 Agent API](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/building-blocks/agent.md) | ChatClient / Advisors / Tool API；是否需要额外持久工作流引擎取决于需求；[API 概述](https://docs.spring.io/spring-ai/reference/api/) | AI Services + tools；agentic 支持编排但官方明确 experimental；[Agentic 文档](https://docs.langchain4j.dev/tutorials/agents/) | ReactAgent + StateGraph + Hooks / Interceptors；[快速开始](https://java2ai.com/docs/quick-start/) |
| MCP | Toolkit 可注册 MCP Client，需要官方 MCP SDK；本地尚无业务 MCP 编排；[2.0.2 入门](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/quickstart.md) | 官方客户端 / 服务端 starter 与注解；[1.1 GA 说明](https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/) | MCP client、tool provider、resource / prompt 等；[MCP 文档](https://docs.langchain4j.dev/tutorials/mcp/) | 通过 Spring AI MCP Client 接入；[官方示例说明](https://java2ai.com/en/agents/deepresearch/agentic/quick-start/) |
| 检索增强生成（RAG） | 可以用受治理检索工具提供证据；具体原生向量适配器与版本本次未完整核验，不宣称不存在或已交付 | VectorStore / ETL / RAG，适合作为基础设施候选；[1.0 官方概述](https://docs.spring.io/spring-ai/reference/1.0/index.html) | ContentRetriever / RetrievalAugmentor 等；[RAG 文档](https://docs.langchain4j.dev/tutorials/rag/) | 官方发布有 RAG graph 示例；底层复用 Spring AI，权限索引仍由应用负责；[发布记录](https://github.com/alibaba/spring-ai-alibaba/releases) |
| 结构化输出 | Java class / JSON Schema，native 与 synthetic tool 两条路径；[2.0.2 API](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/building-blocks/agent.md) | OutputConverter / native；converter 是输出转换机制，不能替代领域校验；[输出转换](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html) | POJO / JSON Schema，模型支持差异需实测；[结构化输出](https://docs.langchain4j.dev/tutorials/structured-outputs/) | outputSchema / outputType；[官方快速开始](https://java2ai.com/docs/quick-start/) |
| 持久化 / 恢复 | AgentStateStore；官方会话恢复不改变项目当前 INTERRUPTED 契约；[2.0.2 Agent API](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/building-blocks/agent.md) | ChatMemoryRepository 存消息；不能等同业务步骤 checkpoint；[Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html) | ChatMemory 与 AgenticScopeStore；agentic 文档有步骤 checkpoint / recoverability；experimental 状态需接受；[Agentic](https://docs.langchain4j.dev/tutorials/agents/) | Checkpointer / MemoryStore；MemorySaver 是内存示例，人审生产需持久后端；[官方快速开始](https://java2ai.com/docs/quick-start/) |
| 人在回路（HITL） | allow / ask / deny、external tool pause；项目只接澄清；[权限系统](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/building-blocks/permission-system.md) | Tool API 可插入应用控制；本次未核验通用持久化人审工作流保障，不声称内置等价能力 | HumanInTheLoop 与 deferred response，结合 scope store 恢复；[Agentic](https://docs.langchain4j.dev/tutorials/agents/) | HumanInTheLoopHook approve / edit / reject + RedisSaver 示例；[官方 hooks 源码](https://github.com/spring-ai-alibaba/website/blob/main/docs/frameworks/agent-framework/tutorials/hooks.md) |
| 观测 | 官方生产指南 OtelTracingMiddleware；项目已有步骤观测与投递日志，先做脱敏 / 关联验证；[生产指南](https://raw.githubusercontent.com/agentscope-ai/agentscope-java/v2.0.2/docs/v2/en/docs/others/going-to-production.md) | Micrometer，prompt / completion / tool 内容默认不导出；[Observability](https://docs.spring.io/spring-ai/reference/observability/index.html) | listeners / metrics 等；[Observability](https://docs.langchain4j.dev/tutorials/observability/) | graph observation starter 在 stable POM 存在；是否与当前 trace 契约完整兼容待 PoC；[POM](https://raw.githubusercontent.com/alibaba/spring-ai-alibaba/v1.1.2.2/pom.xml) |
| 测试 / 评测 | 官方 POM 有 Reactor Test / JUnit / Mockito；项目测试存在，不代表本次已通过；[官方 POM](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.2/pom.xml) | Evaluator 等；模型裁判不是安全 / 权限验收替代；[Evaluation Testing](https://docs.spring.io/spring-ai/reference/api/testing.html) | 官方测试 / 评测指导；[Testing](https://docs.langchain4j.dev/tutorials/testing-and-evaluation/) | stable POM 有测试工具依赖；本次未证明存在平台所需的治理评测闭环；[POM](https://raw.githubusercontent.com/alibaba/spring-ai-alibaba/v1.1.2.2/pom.xml) |

### 5.2 官方 LangGraph 与社区 Java 路线

官方 LangGraph 的定位是持久的 graph 执行引擎；模型、tools、MCP、RAG 通常由 LangChain 或自定义节点供给，不应把所有能力当作 LangGraph core 自带。[官方栈分层](https://www.langchain.com/oss-overview)

- Checkpointer 拥有线程内状态，Store 拥有跨线程应用数据；内存 saver 重启会丢失，生产需持久后端。[Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)
- `interrupt()` / `Command(resume=...)` 允许人审恢复；恢复节点会重执行相关代码，外部副作用要幂等，不能宣称“恰好一次业务写入”。[Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)
- 图节点粒度影响恢复时重复工作；JS 官方示例也明确 checkpoint 在节点边界，业务副作用和缓存仍由应用设计。[JS 工作流设计](https://docs.langchain.com/oss/javascript/langgraph/thinking-in-langgraph)
- 测试可覆盖节点、整图与 checkpoint 场景；观测可与 LangSmith 集成，采用商业托管时额外检查内容上传与部署条款。[官方测试](https://docs.langchain.com/oss/python/langgraph/test)、[官方栈说明](https://www.langchain.com/oss-overview)
- LangGraph4j 的 shared state、cycle、checkpoint 和 Java 集成，应在自己的版本与文档下验证；本次未做它与官方 Python / JS 的序列化、恢复、隔离语义一致性测试。[LangGraph4j](https://github.com/langgraph4j/langgraph4j)

## 6. 安全边界：框架共同不能替平台完成的工作

以下是结合本平台契约提出的设计建议，不是声称某框架已替平台实现。

| 边界 | 要求与证据方式 |
| --- | --- |
| 身份 | 服务端冻结 user / project / session / turn；模型参数不得决定当前身份；线程切换、重试与恢复均传相同受信上下文 |
| 授权 | 发现、检索、取数、读报告、读 trace、memory 召回分别校验；最终业务 API 再授权；框架 tool allow 不等于数据授权 |
| RAG | 索引保存稳定 ID 与版本；检索先限定授权范围；回源确认有效性与权限；删除 / 撤权处理索引、缓存、memory 副本 |
| 提示注入 | 文档、字段描述、工具返回是数据；不提升为系统指令，不动态扩大工具集合；攻击样本必须进入评测 |
| 输出 | 解析 JSON Schema 后再执行类型、枚举、字段引用、当前版本、业务不变量校验；无法确认时输出“证据不足” |
| 人审 / 写入 | 建议先进入已有草稿 / 校验流程；审批由平台 Approval 拥有；恢复前重校验权限、目标版本、审批有效性；幂等命令限制副作用 |
| Python / MCP | OS 隔离、凭据不注入、网络限制、资源上限、工具清单准入；不因官方支持 sandbox / MCP 自动开放执行 |
| 日志与成本 | 只采必要内容；token / 请求 / 时延 / 错误分类 / queryId 关联；正文脱敏与保留策略；限制迭代、上下文和并发 |

## 7. 可复核的对照实验与迁移门槛

### 7.1 第一轮实验

先验证“继续 AgentScope 是否足以满足首批用户任务”；候选框架只有在具体缺口出现时才投入对照，避免全平台多框架试建。

| 对照任务 | 固定输入与证据 | 验证重点 |
| --- | --- | --- |
| 资产解释 | 同一组授权目录、字段、血缘和规则快照；包含无权限资产与过期描述 | 检索授权、引用 ID / version、证据不足时拒绝猜测 |
| 质量规则建议 | 同一字段语义和脱敏概要；既有规则类型及领域校验器 | schema 合法、规则可校验、建议不自动启用、错误可解释 |
| Dataset 分析 | 相同 DSL、queryId / 结果、工具目录；主体缺失与越权案例 | 主体透传、上限、证据与回答一致、不会退回任意 SQL |
| 恢复与重放 | 工具前 / 后故障、SSE 断线、用户澄清、服务重启 | 当前 INTERRUPTED 契约准确；投递去重；未来写工具不重复提交 |
| 安全与运行 | 提示注入、权限撤回、项目切换、provider 超时、malformed JSON、大返回值 | 无越权副作用、敏感信息出口、预算和取消、失效明确 |

固定模型版本、temperature、工具、样本、预算、测试环境；框架差异不能与模型差异混淆。重复运行并记录成功率、引用正确率、规则校验通过率、p50 / p95、tokens、框架适配代码与故障恢复成本；零越权副作用是必须满足的验收条件。量化阈值由试点基线与产品成功指标制定，本调研不虚构性能数据。

### 7.2 何时升级或切换

1. **小版本升级**：独立验证 AgentScope 2.0.3 的已使用 API、StateStore schema / serialization、消息投影、事件 codec、skill、middleware、取消与并发；完成状态备份、回滚演练后再决定。[2.0.3 修复记录](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.3)
2. **引入 Spring AI 基础设施**：存在具体模型 / 向量 / MCP 集成缺口；平台 Boot 兼容已验证；只在边界适配，继续只有一个消息 StateStore 和一个对话执行 Owner。
3. **切换 LangChain4j**：AgentScope 出现无法合理修复的关键阻塞，且相同任务集证明候选满足安全、恢复、事件、结构化输出与成本；明确接受需要的 beta / experimental 组件。先迁移 runtime adapter，新会话灰度；旧会话只读或提供明确转换，禁止两个 SDK 并写同一状态。
4. **引入 Graph 编排**：批准的用户旅程需要多步骤确定性执行 / 跨日人审；先复用已有 Workflow / Approval；候选必须证明 checkpoint、业务命令幂等、版本绑定和恢复再授权。不得为“多 Agent 看起来先进”创建新运行真相。
5. **引入 Python / JS 服务**：存在 Java 路线不能满足的真实需求；明确协议、部署、权限与状态 Owner；LangGraph 不直接连治理库绕过业务 API。

## 8. 已知、推断和未核验项

**已知**：仓库 JDK21 / Boot3.3.13 / AgentScope2.0.2；候选的官方支持范围、starter 预发布标识、AgentScope / SAA 固定 POM、LangChain4j agentic experimental；SAA stable 包装器使用 AgentScope1.0.9。

**推断**：保留当前 runtime 成本更低；查询主体缺失可能导致安全装配后取数失败；`python -I` 不足以证明 OS 沙箱；官方会话恢复不能证明当前产品崩溃续跑或业务恰好一次执行。这些推断需要本平台实验与验收确认。

**未核验**：真实模型质量与时延、框架生产吞吐、集群写冲突、完整 SBOM / CVE、AgentScope 全部原生 RAG 适配、SAA 与 AgentScope2.0 的包装兼容、LangGraph JS 精确部署版本与商业服务条款、各框架对本平台长期记忆撤权 / 删除的完整行为。本次不作相应生产保证。

所有选型建议先进入技术评审；产品能力扩大、状态机与事实归属改变，按仓库产品治理流程形成 Product Decision / Feature Spec 后实施。
