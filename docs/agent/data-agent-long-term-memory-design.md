# 数据智能体长期记忆与自进化 · 功能规划（v1.0）

> 日期：2026-08-28
> 定位：数据 Agent **长期记忆**能力的功能规划与架构裁决。回答三个问题：记什么、怎么记、怎么用记忆让 Agent 越来越聪明、越来越懂业务。
> 配套排期：[data-agent-long-term-memory-development-plan.md](data-agent-long-term-memory-development-plan.md)；总排期仍以 [data-agent-development-plan.md](data-agent-development-plan.md) 为唯一权威。
> 代码基线：`future/data-agent` 分支，Agent 模块 58 测试全绿（turn 化执行 + 框架对齐 Commit A/B 已合入）。

---

## 一、背景与问题定义

### 1.1 现状：只有"会话内记忆"

当前数据 Agent 的记忆体系只有一层（以代码为准）：

| 层 | 载体 | 生命周期 | 说明 |
| --- | --- | --- | --- |
| 会话内上下文 | `MysqlAgentStateStore`（`agentscope_sessions` 表，key=`userId/sessionId`） | 单会话 | 框架 `AgentState.context` 全量消息，`AgentRuntime.history()` 据此重建对话 |
| 会话内压缩 | `CompactionMiddleware` | 单会话 | 跨轮超窗前 LLM 摘要收敛（PI-002 已校准参数），**压缩掉的信息就此丢失** |

由此产生的直接问题：

1. **每个新会话都是"失忆重启"**。用户上周澄清过"营收=含税口径"，今天开新会话问营收，Agent 要么再反问一次（HITL 重复打扰），要么按错误口径取数。
2. **业务知识无法沉淀**。HITL 反问得到的口径答案、Guard 拒绝暴露的字段误用教训、高频查询的成功路径——这些每轮都在产生、每轮都在丢弃。
3. **压缩是有损单向阀**。Compaction 摘要丢弃的细节（用户偏好、口径决定）没有任何下游承接。

### 1.2 需求与裁决变更记录

`docs/agent/agentscope-framework-audit-report.md` §8.3 曾裁决"不引入 MemoryFlushMiddleware / MemoryMaintenanceMiddleware——yak-ops 不需要跨会话记忆持久化"。**本规划显式推翻该裁决**：需求已变，跨会话记忆从"不需要"升级为"自进化"的核心基建。审计报告中其余与记忆相关的结论（框架记忆栈的能力盘点）仍然有效，并在 §二 作为选型输入。

### 1.3 "自进化"的可操作定义

"越来越聪明、越来越懂业务"拆解为三个层次，每层都有可度量的证据：

```text
L1 记得住   跨会话记忆：偏好、事实、口径在下次会话可见可用
            → 证据：同一用户不重复反问同一口径；新会话首问即携带正确默认值
L2 懂业务   业务知识沉淀：HITL 口径答案、字段语义、Guard 拒绝教训自动入库并复用
            → 证据：语义/取数问答通过率随使用时长上升；重复错误率下降
L3 变聪明   行为改进闭环：成功路径固化为可检索示例、失败教训规避、低价值记忆被淘汰
            → 证据：带记忆通道 Token/推理步数相对不带通道下降（复用 D4 口径）；记忆条目的命中-采纳-巩固形成正循环
```

三个层次共享同一条**进化闭环**：`提取 → 存储 → 检索注入 → 影响行为 → 效果反馈 → 巩固/淘汰`。缺任何一环都只是"存了个日志"，不是自进化。

---

## 二、框架能力评估与选型裁决

### 2.1 AgentScope v2.0.2 记忆能力盘点（源码核实）

| 能力 | 位置 | 状态 | 与本需求的关系 |
| --- | --- | --- | --- |
| `LongTermMemory` SPI（record/retrieve） | `agentscope-core` | 🔴 `@Deprecated(forRemoval, since 2.0.0)`，javadoc 明确"跨会话持久化下沉应用层" | **不采用**。在废弃接口上做长期基建是负债 |
| mem0 / bailian / reme 扩展 | `agentscope-extensions-mem/*` | 实现上述废弃接口；需外部记忆服务 | **不采用**。废弃接口 + 外部服务依赖，与内部单机部署前提冲突 |
| `MemoryFlushMiddleware` | `agentscope-harness` | ✅ 活跃 | 思想来源①：轮末 LLM 提取 → 日账本，FlushTrigger(ALWAYS/NEVER/THROTTLED)，IsolationScope(USER/SESSION/AGENT/GLOBAL) |
| `MemoryConsolidator` + `MemoryMaintenanceMiddleware` | `agentscope-harness` | ✅ 活跃 | 思想来源②：日账本定期合并为精选层（curated），保留策略与淘汰 |
| `WorkspaceContextMiddleware` | `agentscope-harness` | ✅ 活跃 | 思想来源③：记忆注入 system prompt 的模板与"记忆检索/保存"工具化指引 |
| `AgentStateStore` SPI | `agentscope-core` | ✅ 已在用（MySQL 扩展） | 证明"框架状态存储 = 应用层可插拔"路线成熟 |

harness 记忆栈的**结构性限制**（为什么不能直接搬）：

1. **文件型真相源**：全部落在 `WorkspaceManager` 的 `memory/YYYY-MM-DD.md` + `MEMORY.md`。多节点部署下是节点本地文件；且与 yak-ops"一切 Agent 事实落 MySQL"的既有纪律（turn/step/event 全表化）相悖。
2. **依赖 workspace 文件工具**：`memory_search/memory_get/memory_save` 工具建立在 `read_file/write_file` 之上。yak-ops 是数据分析 Agent，**不给模型文件工具**（安全边界），这套工具链无立足点。
3. **提取 prompt 面向通用编码助手**（偏好/项目决定/团队结构），数据域需要定制口径/字段/查询模板等提取准则。
4. **无治理界面**：文件记忆只能运维手工改，不符合内部平台的可审计要求。

### 2.2 三路选型对比与裁决

| 维度 | A. harness 文件记忆栈 | B. mem0 等外部记忆服务 | C. 自建 MySQL 记忆栈（借鉴 harness 两层模型） |
| --- | --- | --- | --- |
| 框架接口状态 | 活跃 | 依赖废弃接口 | 不依赖框架接口，Middleware seam 均为活跃能力 |
| 真相源 | 本地文件 | 外部服务 | MySQL（与 turn/step 同库同纪律） |
| 多节点安全 | ❌ | ✅ | ✅ |
| 可治理/可审计 | 弱（手工改文件） | 中 | ✅ 强（SQL + REST + 前端） |
| 数据域定制 | 需覆盖默认 prompt，仍受文件模型约束 | 弱 | ✅ 提取/巩固/检索三处全定制 |
| 额外运维 | 无 | 需部署维护 mem0/ReMe | 无（复用现有 MySQL） |
| Token 成本控制 | 框架内置 | 服务端 | 自控（注入预算硬上限 + 计量） |

**裁决：选 C**。理由：框架废弃接口不可依赖、文件栈与平台纪律冲突、外部服务违反"内部单机、不加新中间件"前提（与 v1.2 §八"不上 RabbitMQ/Kafka"同一取舍逻辑）。harness 栈的**两层记忆模型（raw ledger → curated）与三段管线（flush/consolidate/recall）作为思想原型完整吸收**。

### 2.3 与开发计划的边界

- 本能力为 **Agent 主线的新增平行能力线（记忆线）**，不改变 v1.2 计划的 Phase 1~4 排期含义；建议插入时点见开发计划 §三。
- 本体冻结令（Phase 1~3）不受影响：记忆栈不改语义/本体模块代码，仅运行时消费其只读 API（如需要）。

---

## 三、记什么：记忆类型学与归属

### 3.1 记忆类型（memory_type）

不做一个"什么都装"的记忆池，按**用途**分六类，每类有独立的提取准则、注入位置与治理策略：

| 类型 | 内容举例 | 来源信号 | 进化层次 |
| --- | --- | --- | --- |
| `PREFERENCE` 偏好 | "该用户默认看近 30 天""输出偏好表格+结论两句" | 用户对展示形式/默认条件的反复选择 | L1 |
| `FACT` 事实 | "订单宽表ods_order所在库每日 T+1 更新" | 工具返回的稳定事实、用户陈述 | L1 |
| `GLOSSARY` 口径 | "营收=含税 GMV，不含退款"（HITL 反问的用户答案） | **RequestClarificationTool 的 resume 反馈**（当前完全丢弃） | L1→L2 |
| `LESSON` 教训 | "ds_order 的 refund_status 是编码值，需先查字典再过滤" | 工具报错→修正成功的轨迹、Guard 拒绝原因 | L2 |
| `TEMPLATE` 查询模板 | "'昨日各渠道销量'→ list_datasets→run_dataset_query(时间=昨日, group=channel)" | 高频成功问答的压缩路径 | L2→L3 |
| `EXAMPLE` 示例 | 完整的"用户问→工具调用序列→答案"精简样本 | 高质量轮次（评审通过/被采纳） | L3 |

**红线（继承 v1.2 §七 A1 并扩展）**：

1. 记忆**不是数据真相源**：任何数字必须来自当轮工具返回；记忆只影响"怎么问、怎么解读"，不影响"数从哪来"。
2. 记忆**不存查询结果明细**（防数据泄露面扩大）：记口径与路径，不记数值。
3. `GLOSSARY` 类记忆永远标注"用户口径陈述"，与本体层 PUBLISHED 口径冲突时**以本体为准**并在注入时明示冲突。

### 3.2 归属范围（scope）× 作用域键

对齐框架 `IsolationScope` 思想，但映射到 yak-ops 的 Project Space 契约：

| scope | scope_key | 语义 | 典型条目 |
| --- | --- | --- | --- |
| `USER` | userId | 个人跨会话记忆 | 偏好、个人常用数据集 |
| `PROJECT` | projectId | 项目级业务知识（项目成员共享） | 项目口径、项目数据集事实、项目查询模板 |
| `GLOBAL` | `-` | 平台通用知识（运营维护，默认只读） | 平台级使用指引、通用字段字典说明 |

提取默认落 `USER`；HITL 口径答案默认 `USER`，当问题上下文含明确项目归属且答案具共性时由巩固层晋升 `PROJECT`（晋升永远走人工评审，见 §六）。**不做 SESSION scope**——会话内记忆已由 StateStore 承担，重复建设。

### 3.3 两层模型（layer）

完整吸收 harness 的两层思想，落库化：

```text
LEDGER 层（yak_agent_memory, layer=LEDGER）
  轮末 LLM 提取的原始条目：append-only、带来源 turn、带 keywords、
  保留期短（默认 14 天），过期未晋升即 ARCHIVED
        │  巩固任务（定时 + 阈值触发）
        ▼
CURATED 层（layer=CURATED）
  合并去重后的稳定条目：带置信度、命中计数、最后命中时间；
  检索注入的主要来源；规模受配额约束（per scope+type 上限）
```

为什么坚持两层而不是"提取即入正式记忆"：单轮提取的 LLM 输出有噪声，直接入精选层会**记忆污染且难回滚**；账本层让巩固有合并视野（跨轮去重）、让错误记忆天然过期，是防污染的第一道闸。

---

## 四、怎么记：提取管线（写入侧）

### 4.1 触发点与触发策略

```text
AgentTurnExecutor.finishCompleted()          ← 唯一触发点（自然完成轮）
  └─ MemoryFlushService.enqueue(turnRef)     ← 异步解耦：终态不被记忆流程阻塞/拖垮
       └─ 条件闸门：
          ① 轮次有实质内容（answer 非空 或 存在工具调用）——纯寒暄不提取
          ② THROTTLED：同会话提取最小间隔（默认 5min）——借鉴框架 FlushTrigger
          ③ feature flag yak.agent.memory.enabled 关闭时直接跳过
```

设计纪律（全部继承 v1.2 §七不变式）：

- **异步**：提取走独立线程池（复用 turn worker 池模式），失败重试 ≤1 次后放弃并落 step 告警。
- **失败也记账**：提取成功/失败/跳过均落 `yak_agent_step`（`KIND_MEMORY_FLUSH`），Token 消耗计入调用级计量。
- **不阻塞终态**：提取失败不影响轮次已交付的结果，用户无感。

### 4.2 提取调用（数据域定制 prompt）

一次独立 LLM 调用（非主推理链），输入 = 本轮 USER 消息 + 最终回答 + 工具调用摘要 + **该用户/项目现有相关记忆（去重视野）**，输出 = 结构化候选条目（JSON：type/content/keywords/confidence），解析失败即放弃本轮提取（不做回喂重试——提取是机会性的，不值得为其引入复杂度，与 v1.2 §10.4 结构化输出闭环暂缓同一裁决）。

数据域提取准则（覆盖框架 DEFAULT_FLUSH_PROMPT 的通用准则）：

- 记口径与语义，不记数值结果；记"为什么这样查"，不记"查到了什么"；
- HITL resume 反馈中的口径答案**优先级最高**（用户亲口澄清，置顶 GLOSSARY）；
- 工具"报错→修正→成功"轨迹记 LESSON（含错误码与修正方式）；
- 相对时间表述（"上周""本月"）还原为绝对日期区间后记录，防记忆随时间腐烂；
- 输出自包含、可独立检索的原子条目，一条一事实。

### 4.3 脱敏与安全

提取 prompt 明确禁止输出：查询返回的明细数值、行级数据、字段样例值。提取结果入库前过一道**内容校验**（复用 FieldWhitelistValidator 思路的轻量正则：拒绝超长、拒绝明显的数据行模式），校验拒绝也落 step。记忆条目可含数据集名/字段名（这些本就是工具白名单内容）。

---

## 五、怎么用：检索注入管线（读取侧）

### 5.1 注入点与查询构造

```text
SystemPromptAssemblyMiddleware 管道（已就位）
  └─ 新增 LongTermMemoryPromptMiddleware（order 排在语义/能力域贡献者之后）
       ├─ 查询构造：本轮 USER 消息 + 最近 1 条用户历史消息
       │   （防"继续""换个维度"这类短指代检索扑空）
       ├─ 检索域：该用户 USER 层 + 所属项目 PROJECT 层 + GLOBAL 层（顺序合并）
       ├─ 注入预算：≤8 条、总字符 ≤2000（硬上限，超则按相关度截断）
       └─ 输出段落模板：
          ## 长期记忆（历史沉淀，仅供参考；数字必须来自当轮工具结果）
          - [口径] 营收=含税GMV，不含退款（来源:2026-08-20 会话澄清）
          ...
```

为什么用 onSystemPrompt 而不是框架的"记忆工具化"路线（让模型 memory_search）：数据分析是**短平快高频**场景，每轮省一次工具往返的价值大于模型自主检索的灵活性；且工具化路线需要给模型文件工具或新增 DB 工具，扩大攻击面。注入式对 Token 可预算、对计量可归因。

### 5.2 检索实现（分两步走）

| 阶段 | 实现 | 理由 |
| --- | --- | --- |
| MVP | SQL `LIKE` on keywords/content + 类型权重（GLOSSARY/LESSON 优先）+ 时间衰减排序 | 内部数据规模小（单用户记忆条目预期 < 百级）；与语义层 `SchemaSearchReader` MVP LIKE 级实现同一务实基准 |
| 可选增强 | OpenAI 协议 embeddings 端点 + 向量列（`embedding JSON/BLOB`） | 仅当 LIKE 检索被实证召回不足时立项（条件触发项），不在本期做 |

召回排序公式（MVP）：`score = type_weight × confidence × decay(last_hit_at)`，decay 半衰期默认 30 天。命中即更新 `hit_count/last_hit_at`（异步批量，不阻塞推理）。

### 5.3 命中计量与效果归因

- 每次召回落 `KIND_MEMORY_RECALL` step：hit 数、注入字符数、检索延迟——D4 口径（Token/步数下降）因此能区分"记忆通道"的贡献；
- TURN_FINISHED 事件的 turn 摘要中带 `memoryHitCount`，前端可展示"本次回答参考了 N 条历史记忆"（透明化，防用户困惑于 Agent "怎么知道"）；
- **效果反馈信号**（L3 闭环的关键）：轮内出现"修正取数条件重查"记为候选负信号，轮次被用户追问深入记为候选正信号；信号只影响 confidence 微调与巩固排序，不做自动删除（删除永远人工或过期策略）。

---

## 六、怎么变聪明：巩固管线与治理（进化侧）

### 6.1 巩固任务（Consolidation）

借鉴 harness `MemoryConsolidator`，落库化：

```text
触发：@Scheduled 每日凌晨一次 + LEDGER 层条数阈值触发（默认 50 条）
输入：同 scope 分组的 LEDGER 条目（含现有 CURATED 相关条目作合并视野）
LLM 合并准则：
  - 相似条目合并为一条（保留最完整表述，旧条目标 MERGED + merged_into）
  - 冲突口径并列时：新时间戳优先但保留旧条目引用，标记 CONFLICT 待人工裁决
  - 超配额（per scope+type 默认 50 条）时按 score 淘汰尾部 → ARCHIVED
输出：CURATED 层更新 + 巩固报告落 step（KIND_MEMORY_CONSOLIDATE）
```

PROJECT 晋升：巩固层发现同口径事实被 ≥3 个不同用户独立陈述时，生成"晋升候选"条目（status=PROMOTION_PENDING），**仅运营在管理界面确认后才生效为 PROJECT/GLOBAL 条目**——共享知识的写权限永远有人把关，这是 3.2 节"默认只读"的执行机制。

### 6.2 治理能力（REST + 前端）

| 能力 | API | 说明 |
| --- | --- | --- |
| 记忆列表 | `GET /agent/v1/memory`（分页/筛选 scope/type/status/关键词） | 复用现有分页与权限模型 |
| 记忆详情/编辑 | `PATCH /agent/v1/memory/{id}` | 编辑 content/keywords/status（DISABLED 即禁用不删除） |
| 删除 | `DELETE /agent/v1/memory/{id}` | 软删（→ARCHIVED），保留审计链 |
| 晋升审批 | `POST /agent/v1/memory/{id}/promote` | PROMOTION_PENDING → PROJECT/GLOBAL |
| 权限 | 新增 `agent:memory:manage` 权限码 | 查看自己的 USER 记忆无需管理权限；PROJECT/GLOBAL 治理需权限 |

前端（低优先，可后置到 M4）：记忆管理页（列表/编辑/晋升审批）+ 回答中"参考记忆"的展示卡。**没有治理界面的记忆系统不敢对业务放开**——记忆污染的兜底永远是"人能看见、能改、能关"。

---

## 七、总体架构图

```text
┌────────────────────────── 轮次生命周期（现有） ──────────────────────────┐
│  提交 turn → Executor.claim → ReActAgent 推理 → 终态收敛                  │
│                    │                        │                            │
│          （读）检索注入                  （写）finishCompleted             │
│                    ▼                        ▼                            │
│  ┌──────────────────────────┐   ┌──────────────────────────────────┐    │
│  │ LongTermMemoryPrompt     │   │ MemoryFlushService（异步）        │    │
│  │ Middleware               │   │  闸门→LLM 提取→校验→LEDGER 入库    │    │
│  │  查询构造→MySQL 检索      │   └──────────────────────────────────┘    │
│  │  →预算截断→system prompt │                                            │
│  └──────────────────────────┘                                            │
└──────────────────────────────────────────────────────────────────────────┘
          ▲ 检索                                    │ 原始条目
          │                                        ▼
│  yak_agent_memory（MySQL）────────────► MemoryConsolidationJob          │
│    layer=LEDGER/CURATED                   定时/阈值触发：合并去重、       │
│    scope=USER/PROJECT/GLOBAL              冲突标记、配额淘汰、            │
│    type×6、confidence、hit_count          晋升候选生成                    │
│    status=ACTIVE/MERGED/ARCHIVED/…                     │                │
│          ▲                                             ▼                │
│  ┌──────┴──────────────────────────────────────────────────────┐        │
│  │ 治理：REST API + 记忆管理页 + 晋升审批 + KIND_MEMORY_* 计量    │        │
│  └──────────────────────────────────────────────────────────────┘        │
```

模块归属：全部新代码落在 `yak-ops-business-agent`（新 `memory` 包：service/middleware/repository/dao/controller），不新增 Maven 模块，不动 semantic/ontology。

---

## 八、验收指标（北极星）

| # | 指标 | 目标 | 度量方式 |
| --- | --- | --- | --- |
| NM-1 | 跨会话口径复用 | 同一用户同一口径的重复反问率（14 天窗口）下降 ≥60% | query_log + clarify 事件时间序列 |
| NM-2 | 新会话热启动 | 新会话首问的澄清/纠错轮次占比下降 ≥30% | 轮次元数据统计 |
| NM-3 | Token 效率 | 带记忆注入通道 vs 关闭通道，同题 Token/步数不劣化且整体呈下降趋势 | KIND_LLM_CALL 计量（复用 D4 口径） |
| NM-4 | 记忆质量 | 抽样评审：注入条目与问题相关且正确率 ≥85% | 月度人工抽样（治理页导出） |
| NM-5 | 进化正循环 | CURATED 条目月度"晋升率 ≥ 淘汰率"且 hit 集中度上升（二八分化） | 巩固任务报告统计 |

NM-5 是"自进化"成立的判据：如果记忆只进不出、命中随机分布，说明只是个日志系统。

---

## 九、明确不做（防过度设计清单）

1. **不引入向量数据库 / 图数据库**；embedding 检索仅在 LIKE 实证召回不足后按条件触发项立项（对齐 v1.2 §八"不引入 Neo4j/RDF"同一逻辑）；
2. **不采用已废弃的 `LongTermMemory` SPI 与 mem0/bailian/reme 扩展**；
3. **不做 SESSION scope**——与 StateStore 会话记忆重复；
4. **不做模型自主记忆写工具**（memory_save 类）——写入只走轮末提取管线，防模型滥用与攻击面扩大；
5. **不做自动删除**——淘汰只到 ARCHIVED，物理清理是运维脚本的事；
6. **不做跨用户记忆共享**（USER 间不可见），共享只经 PROJECT 晋升审批；
7. **不改 Compaction**——会话内压缩与长期记忆是互补关系，不做双向联动（提取管线已能从含压缩历史的轮次提取，够了）。

---

## 十、风险登记

| 风险 | 等级 | 对策 |
| --- | --- | --- |
| 记忆污染（错误记忆长期误导取数口径） | 高 | 两层模型隔离噪声 + GLOSSARY 冲突显式标记 + 治理界面可见可改 + 注入段落明示"仅供参考" + A1 红线（数字必须来自工具） |
| Token 膨胀反噬（记忆注入比省下的还贵） | 中 | 注入预算硬上限（≤8 条/≤2000 字符）+ KIND_MEMORY_RECALL 计量 + NM-3 监控 |
| 提取 LLM 调用成本/延迟拖累主链路 | 中 | 异步独立池 + THROTTLED 闸门 + 失败不影响终态 + 成本落计量可见 |
| 记忆随时间腐烂（相对时间、版本口径过期） | 中 | 提取时还原绝对日期 + decay 衰减 + 巩固层定期重述 + 本体口径优先级高于记忆 |
| 用户隐私敏感（记忆里出现业务明细） | 中 | 提取禁令 + 入库内容校验 + 记忆不存查询结果明细红线 + USER scope 隔离 |
| 与未来语义层口径卡（Phase 4）职能重叠 | 低 | 划界：口径卡=当轮回答的当轮口径声明（本体派生）；GLOSSARY 记忆=用户口述的历史沉淀；冲突时本体优先已在注入模板中声明 |

---

> 下一步：执行排期、任务拆解、每期验收 DoD 见 [data-agent-long-term-memory-development-plan.md](data-agent-long-term-memory-development-plan.md)。
