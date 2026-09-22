# Agent

Agent 是 Yak Ops 的自然语言数据分析智能体：业务人员用自然语言提问，Agent 在**平台数据集（Dataset）**上完成取数、统计与报告生成，并以流式对话返回结果与证据。

## Read First

本目录只维护**当前有效 contract**，历史设计与迁移过程以 Git / PR 为准。

建议按顺序阅读：

| Document | Answers |
| --- | --- |
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | 模块需要什么 |
| [`DOMAIN.md`](./DOMAIN.md) | 哪些领域规则不能违反 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 子系统、truth ownership 与角色如何协作 |
| [`DEPENDENCIES.md`](./DEPENDENCIES.md) | package 可以依赖谁、跨子系统走哪条 corridor |
| [`CODE_STYLE.md`](./CODE_STYLE.md) | 类、方法、Spring stereotype 与重构按什么风格写 |
| [`REVIEW.md`](./REVIEW.md) | PR 按什么标准判卷 |

当前状态：契约基线 v1 已冻结，代码尚未落地。首个实现 PR 必须同时携带本目录契约要求的 architecture test 与 dependency boundary test。

结构调整不得借机改变现有 REST / DB / Domain runtime behavior；真正的领域或接口变化必须单独评审。

## Core Model

```text
UserQuestion
      │ Start
      ▼
ReasoningLoop (ReAct, AgentScope)
      │ ToolCall
      ▼
DatasetQuery ──► Evidence（结构化结果）
      │
      ▼
Answer / Clarify(HITL) / Report
```

核心关系：

```text
Dataset != Evidence != Answer != Report
LLM Output != Trusted Input
```

数据集是唯一数据事实；LLM 产出只是待校验输入；Evidence 是查询运行时的客观结果；Report 是用户可见的沉淀物。

## Runtime Truth

```text
ChatMessage history           = AgentScope StateStore（官方管理表，auto-DDL）
SessionMeta (归属/标题/绑定)   = yak_agent_session
Report                        = yak_agent_report
QueryEvidence                 = yak_agent_query_log
Dataset / 字段语义             = 外部事实（dataset 模块拥有）
SSE 连接                      = 传输通道，不是状态存储
```

任何结构重构都不能把消息历史搬进业务表、把查询证据搬回会话元数据，或让 SSE 事件成为事实来源。

## Baseline

Agent 维护一个 Flyway 基线加前向修正迁移：

```text
db/migration/yak-agent/V1__baseline_agent.sql
db/migration/yak-agent/V2__rename_elapsed_ms_column.sql
```

V1 描述初始表结构；V2 为列名修正（elapsed_ms → elapsed_millis，与 PO 驼峰映射对齐）。
已初始化的数据库按顺序前向迁移即可，无需重建。

显式豁免：对话消息持久化使用官方 `MysqlAgentStateStore` 的 auto-DDL 表（默认 `agentscope_sessions`），不纳入 Flyway 管理。库名与表名通过 `yak.agent.state-store` 配置指定，必须指向平台业务库，避免运行账号需要建库权限。

## Data Access Boundary

Agent 对数据的唯一访问路径是 dataset 模块的公共查询契约：

```text
toolset -> catalog（视图格式化 / 白名单校验）-> gateway.DatasetQueryGateway -> DatasetQueryService
```

本模块禁止 import 物理数据源 API、JDBC、SQL 执行器；LLM 不生成 SQL 文本，只产出结构化查询参数。

## Out Of Scope

当前模块不负责：

- 物理数据源的连接、测试与管理（归 datasource 插件体系）；
- 数据集与字段语义的定义、上线、治理（归 dataset 模块）；
- 生成或执行 SQL 文本；
- 向量召回 / embedding 索引等检索基础设施；
- 通用聊天、闲聊与非数据分析类助手能力；
- 数据集级细粒度授权（复用平台权限体系的联动属于独立设计）；
- MCP Server 编排；
- Python 执行环境的沙箱产品化（当前仅提供进程级限流与超时）。

如果需要以上能力，先在对应模块立项，再回到本模块做 corridor 设计。

## Engineering Rule

新增或移动代码前至少回答：

1. 属于 conversation、runtime、toolset、catalog、gateway、report 还是 persistence？
2. 是什么 role，是否符合 `CODE_STYLE.md`？
3. 谁拥有它读写的 runtime truth？
4. 新 import 是否符合 `DEPENDENCIES.md`？是否触碰 agentscope / dataset 双白名单边界？
5. 哪个 behavior test 与 architecture test 证明改动没有破坏现有 contract？

答不清楚时，不要创建新的 `service / common / helper / utils` 业务大桶，也不要通过扩大 architecture-test 白名单绕过边界设计。
