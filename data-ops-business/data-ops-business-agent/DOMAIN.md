# Agent Domain

> 本文件只保留**实现必须遵守的硬规则**，不记录设计过程。历史演进看 Git / PR。
>
> `REQUIREMENTS.md` 定义“需要什么”；`DOMAIN.md` 定义“不能违反什么”；`ARCHITECTURE.md` 定义“代码边界”；`DEPENDENCIES.md` 定义“允许依赖谁”；`CODE_STYLE.md` 定义“怎么写”；`REVIEW.md` 定义“怎么判卷”。

## 核心模型

```text
AgentSession (会话元数据)
      │ Start
      ▼
ReasoningTurn (一轮 ReAct 推理, 状态由 AgentScope StateStore 持有)
      │ ToolCall
      ├── list_datasets / get_dataset_fields  -> 目录证据
      ├── run_dataset_query                   -> QueryEvidence
      ├── current_date_info                   -> 日期事实
      ├── analyze_with_python                 -> 统计结论
      ├── request_clarification                   -> PendingClarify(HITL)
      └── save_analysis_report                -> AgentReport
```

```text
Session != ReasoningTurn != Evidence != Report
LLM Output != Trusted Input
```

## 10 条硬规则

1. **净室实现。** 本模块禁止包含任何 DataAgent（AGPL-3.0）衍生代码、机械翻译或改写；实现以本目录契约文档为唯一蓝本。引入第三方依赖只允许 Apache-2.0 及兼容许可。
2. **Dataset 是分析取数的唯一事实入口。** 禁止 import 物理数据源 API、JDBC 或 SQL 执行器；模型不产出 SQL 文本，一切取数经 `DatasetQueryGateway` 的结构化参数完成。治理解读消费 Asset / Quality 等源域的只读证据，不替代分析取数入口。
3. **LLM 输出是不可信输入。** 数据集 ID 与字段引用必须先经 catalog 白名单校验才可触达 Gateway；校验失败以结构化错误回喂模型自纠，不得直接透传底层异常。
4. **Truth 单一 owner。** 消息历史 = 官方 StateStore 表；会话归属与标题 = `yak_agent_session`；报告 = `yak_agent_report`；查询证据 = `yak_agent_query_log`；推理轮次生命周期 = `yak_agent_turn`。任何一方不得代持另一方的事实。
5. **OFFLINE 数据集一律拒绝查询。** 不提供任何绕行开关；“临时查下线数据”属于需求变更，走 Requirement Gap 流程。
6. **敏感配置短生命周期。** 模型 API 密钥只在 runtime 模型装配边界存在：不入库、不写入日志、不出现在 SSE 事件与异常文本。
7. **HITL 反问状态机不可绕过。** `request_clarification` 使本轮进入 pending；恢复只能携带匹配的 toolCallId 与工具名结果；同一时刻至多一个 pending；伪造或复用旧 toolCallId 恢复必须被拒绝。
8. **SSE 是传输通道不是存储。** 断连仅停止订阅补发，不终止后台执行、不回滚已发生事实。事件帧持久化（`yak_agent_turn_event`）是投递投影：可删除、可重建，绝不是第二事实源；事实只认 StateStore / message / step / query_log / turn。
9. **工具失败不中断推理。** 工具执行错误以结构化结果回喂模型继续 ReAct 循环；只有运行时致命错误（装配失败、流取消）才终止流并以 ERROR 终态收尾。
10. **无法映射现有模型就是 `Domain Gap`。** 先讨论模型并修订契约，不用临时字段、boolean、enum 或新的 `*Service` 大桶绕过去。

## 轮次生命周期硬规则

```text
QUEUED --claim(CAS)--> RUNNING --finish--> COMPLETED
                        │      └--clarify--> WAITING_INPUT --resume-submit(CAS)--> QUEUED
                        ├--error----> FAILED(error_code)
                        └--cancel(CAS/显式)--> CANCELLED
进程启动时遗留 RUNNING 孤儿 ——> INTERRUPTED（诚实终态，事实保留；不支持 mid-turn 断点续跑）
```

- 状态转移一律走带前置状态的条件 UPDATE（乐观并发，天然单赢家）；非法转移的 UPDATE 影响行数为 0 且不算失败。
- `kill -9` 后的重启恢复承诺是：未启动的 QUEUED 自动被执行；执行中的标记 INTERRUPTED 并保留全部证据。不在框架约束内伪造"断点续跑"语义。
- 停止生成 = 显式命令：RUNNING 轮 dispose 上游并 CAS 至 CANCELLED；QUEUED 轮 CAS 至 CANCELLED 由调度器自然跳过。

## 关键不变量

- 会话归属校验先于任何读写：非本人会话返回归属错误，不存在静默降级。
- 每次通过任务工具准入并进入 DatasetQueryGateway 的 `run_dataset_query` 必须落一条 `yak_agent_query_log`（成功与失败都落），携带 datasetId、queryId、请求投影、行数、耗时与状态。
- 查询行数上限 ≤ 模块配置上限；配置上限变更不追溯已产生的 Evidence。
- Python 执行并发数受信号量约束；超时进程必须被强杀且临时目录清理完成后才释放许可。
- 报告内容为 Markdown + ECharts 配置块文本；保存时不做 HTML 净化以外的二次加工，渲染侧由前端沙箱负责。

## 命令语义

```text
SubmitTurn(sessionId, message)    -> 归属校验 -> 落库 QUEUED -> 立即返回 turnId（后台执行）
ResumeTurn(sessionId, toolResults)-> 归属校验 + WAITING 轮匹配 -> CAS 回 QUEUED（同一 turnId 续跑）
OpenEventStream(turnId, cursor)   -> 归属校验 -> 从 event_id>cursor 增量补发至终态
Cancel(sessionId)                 -> 归属校验 -> 停止生成：dispose 上游 / 排队取消
DeleteSession(sessionId)         -> 归属校验 -> StateStore 删除 + 元数据删除（报告保留）
SaveReport                       -> 归属校验(经会话) -> 落库 -> 返回报告 ID
```

## Truth Ownership

```text
agentscope_sessions(官方表)     = 对话消息与推理状态 truth
yak_agent_session               = 会话身份 / 归属 / 标题 truth
yak_agent_turn                  = 推理轮次生命周期 truth（提交与执行状态机）
yak_agent_report                = 报告内容 truth
yak_agent_step                 = 步骤级执行事实 truth（LLM/工具记账单写入口 AgentStepRecorder；parent 内存映射进程重启即清零——接受边界：重启即 INTERRUPTED 不续跑）；kind 枚举单一真相 = telemetry.AgentKindRegistry（未注册拒绝落库），载荷以 PayloadEnvelope 四档信封落库（SUMMARY_HASH 档原文不落库）
yak_agent_memory               = 长期记忆 truth（召回/提取已接线，巩固管线未接线）
yak_agent_query_log             = 查询证据 trace truth
yak_agent_turn_event            = 事件投递日志（可重建投影，非事实源）
dataset 模块                     = 数据集与字段语义 truth（外部）
```

任何结构重构都不能把消息历史搬进业务表、不能让 query_log 反向成为业务查询入口、不能让 SSE 事件流成为事实来源。

## 安全能力必须保留

```text
会话归属校验（user_id 绑定）
pending 反问单飞 + toolCallId 匹配
字段白名单校验（catalog resolver）
查询行数上限
Python 并发信号量 + 超时强杀 + 临时目录清理
密钥短生命周期 / 日志脱敏
显式停止生成取消传播（dispose 上游）；SSE 断连仅结束订阅
query_log 全量留痕（成功与失败）
```

这些不是实现细节，可以换内部角色，但不能被删除、弱化或绕过。

## Architecture Boundary

领域模型与工程结构的关系以 `ARCHITECTURE.md` 为准，依赖方向以 `DEPENDENCIES.md` 为准。长期业务子系统固定为：

```text
conversation (+ query)
runtime
toolset
catalog
gateway
report
persistence boundaries (repository / dao)
```

任何后续结构调整必须满足：

- 不新增第二套对话状态或查询证据模型；
- 不把 LLM 输出提升为可信事实；
- 不把 AgentScope 类型、dataset 类型泄漏到各自白名单边界之外；
- 不重新创建 `service/common/helper/utils` 业务大桶；
- 不为了性能合并归属校验与白名单校验的先后顺序；
- 不通过扩大 dependency whitelist 掩盖真实架构循环。

## 修改代码前后

修改前先确认 `REQUIREMENTS.md` 中已有对应能力，再写：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Layer / subsystem:
- Domain Gap: yes/no
```

涉及 package / role / dependency 调整时，再回答：

```text
Architecture Impact Analysis
- Target subsystem:
- Stable entry / gateway:
- Runtime truth owner:
- Dependency direction changed: yes/no

Dependency Impact Analysis
- New edge:
- Existing corridor or new corridor:
- Cycle impact:
- DEPENDENCIES / guard updated: yes/no
```

修改后写：

```text
Domain Compliance Report
- Rule changed/implemented:
- Safety/tests:
- Known gaps:
```

## 自动护栏

长期架构护栏：

```text
AgentArchitectureTest
   -> stable Application Facade / role stereotype
   -> Core Domain purity
   -> conversation.query read-side

AgentDependencyBoundaryTest
   -> top-level package dependency matrix
   -> acyclic dependency graph
   -> agentscope / dataset / reactor SDK 白名单边界
   -> @Service allowlist
   -> no broad service/common/helper/utils bucket
```

行为回归测试继续保护归属校验、HITL pending 状态机、白名单自纠、limit 上限、Python 超时强杀、显式取消与 SSE 断线续播等 runtime contract。

**不要因为功能或结构调整被护栏拦住就删护栏。** 如果规则真的变化，同一个 PR 中同步修改 Requirement/Domain/Architecture/Dependencies contract 和对应测试。

## 已知独立 Gap

```text
analyze_with_python 沙箱化（容器 / 受限运行身份）
数据集级可见性授权联动
数据集分组发现
Skill 作用域、评测与扩展资源加载
报告分享与订阅
```

这些 Gap 需要单独做 Requirement / Domain 设计，不在纯架构治理中顺手解决。


## 首版治理证据与执行身份（F-009）

治理目标是对象选择，不是授权。每轮 RuntimeContext 持有独立证据登记和 Dataset 字段版本发现记录；用户/项目只来自认证提交落库的轮次。每个源域工具在实际执行线程通过 UserExecutionScope 恢复当前有效账号和项目，重新检查 agent:chat:run 与源域 action 权限。Dataset 消费认证 USER 主体及 Security 角色编码，禁止无主体取数；字段发现、白名单与实际 versionNo 一致，版本变化需重新发现。

Asset 分区五态保持原义；Quality 历史执行结果与规则证据仍由 Quality 拥有。输出不携带执行 SQL、原始异常或连接配置。最终正文缓冲至证据引用校验完成，进度仍流式展示；回链只从本轮登记生成。校验后的正文更新同一官方 StateStore 消息，历史与当前输出一致。没有可读证据或无有效引用时发布明确的降级结果。首版无治理命令及未校验治理报告保存。

## F-010 人工采纳建议

候选不是业务事实。只读建议及校验不触发业务写入；人工保存继续由本域命令拥有。条件更新在本域事务内比较服务器定义指纹，拒绝旧值覆盖；治理 AI 不拥有源域状态。

## 任务工具约束与执行预算

GovernanceTarget/purpose 是服务器从已持久化轮次恢复的任务范围，不授予源域权限。显式治理任务只读取所选对象，禁止 Dataset 发现/取数、Python、报告保存及其他任务候选；普通对话不按自由文本自动切换任务。未知工具默认拒绝。

工具调用必须在实际执行前预占预算；官方 StateStore 的 yak_tool_budget 键只保留本会话当前轮的辅助额度（turnId、target、冻结限额、已消费数、各工具累计失败数），不保存参数/源事实、不替代轮次生命周期。HITL 恢复同轮额度，存储故障停止新调用；取消后阻止新工具进入。策略拒绝走 Agent 观测，不伪造 Dataset 查询审计。

Skill 定义/启停由 yak_agent_skill 拥有；运行时只提供当前启用的管理员维护 Skill 正文，停用/删除不能通过旧 SDK 加载器重新激活。前端更新携带期望版本，旧版本不得覆盖当前内容；旧客户端无版本调用保持兼容。

逻辑 skillId 是稳定框架 name，展示名使用 displayName metadata，不能写入 SDK 保留的 name。管理更新成功后持久化与响应同时推进版本，并发删除后旧编辑不能复活条目。

## 历史质量排查表达

排查文字是本轮证据的消费结果，不拥有 Quality 结果或处置事实。服务器固定的核对提示与校验后的正文写入同一 StateStore 消息，不新增排查状态。用户提供的背景保持来源标识，不能补成缺失的历史指标/阈值。缺失模板语义、错误类别、样本或冻结失败策略时，数值含义、具体根因和 STOP 因果保持未证实；NOT_RUN 不代表通过或不通过。

## 候选与当前表单

候选带入不等于保存。当前表单对照只用于用户核对，不升级为源定义或历史执行证据。重复识别不授予写权限、不改候选任务范围；新增规则固定停用，实际定义仍以 Quality 条件保存成功为准。

## 会话继续投影

历史正文按原 USER 分组。服务端写入官方消息 metadata 的原 turnId 仅为关联引用；读取必须核对唯一性及原 turn 的用户/项目/session/完成状态后才能附 trace。旧消息或引用缺失、冲突不得按序号、文本或时间猜测，不改变正文 truth；失败作为原 turn 独立记录，不宣称与正文的顺序关系。

恢复范围只取最新持久化轮次的 TurnInput，不从消息、标题或更旧任务推断；损坏输入不能降为普通对话。continuation、历史与 trace 读取先校验本人及当前项目。反问帧仅重现 WAITING_INPUT 的问题，真正 pending 仍在 StateStore；继续应答保持原 turnId/目标/预算并经原 CAS/SDK 校验。读取不得触发推理或修改任何状态；活动轮不自动重放历史全文，不承诺崩溃断点续跑。

恢复页面可有限只读跟随活动状态；调度是客户端读策略，不拥有生命周期。按轮次停止冻结 turnId，先校验轮次及会话的本人/当前项目，只取消该 ID 的排队或运行，不作用于后来轮次；认领与取消串行，真实终态仍经原 CAS/Executor 收尾。WAITING_INPUT 不通过此命令取消。命令应答或网络失败均不能代替状态证据。

## 终态问题草稿
终态不能原地恢复。原输入/唯一 StateStore USER 引用仅作为可核对草稿，读取失败或冲突不猜测；投影上限 8000 UTF-16 units，不截断。新轮来源核对在原提交互斥区检查最新终态 ID、归属及目标，拒绝陈旧来源。错误说明使用固定白名单，不透传异常。

## 治理提问准备
未发送问题表单仅为页面内存；用户补充始终标识口述且待证据核对，不授予权限、不改变 GovernanceTarget/purpose，不补成源事实或自动保存候选。
