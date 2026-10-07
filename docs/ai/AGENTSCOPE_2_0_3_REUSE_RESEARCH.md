# AgentScope Java 2.0.3 复用调研：通用 Agent 与场景 Skill

调研日期：2026-10-07。性质：官方资料与固定版本源码调研、后续建设建议；不替代 ACCEPTED Product Decision 或 APPROVED / IMPLEMENTING Feature Spec。真实模型与登录态 E2E 验收仍为 **PENDING**。

## 1. 结论与适用边界

继续使用 AgentScope Java，升级到 2.0.3。把标准、建模、指标等工作拆为有明确输入、输出和人工交接点的场景，Skill 维护业务方法、说明和示例；Agent 复用 SDK 推理、工具、状态、事件、中间件和人机交互机制。平台保留认证、项目、对象权限、预算、证据核验与原模块保存约束。

截至调研日，官方 `releases/latest` 指向 **v2.0.3**。固定 tag 对应 commit 为 `1b8e3dcd2338550ae5198bdb2a7bae56df5bf2e0`，经 `git ls-remote` 的 peeled tag 核对；其 POM revision 为 2.0.3、Java 基线为 17。[官方最新发布](https://github.com/agentscope-ai/agentscope-java/releases/latest)、[固定发布](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.3)、[固定 POM](https://github.com/agentscope-ai/agentscope-java/blob/1b8e3dcd2338550ae5198bdb2a7bae56df5bf2e0/pom.xml)

**本阶段不为采用 Skill 而整体迁移 HarnessAgent。** 现有 ReActAgent 已能接入 SkillRepository、DynamicSkillMiddleware、RuntimeContext 与结构化输出。Harness 的价值主要在文件工作区、沙箱、文件成果、子 Agent 和长期记忆组合；固定治理表单的候选辅助首先需要可靠的领域工具与采纳流程。[固定 Skill 迁移说明](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/change-log.md#b1-skillbox--skillrepository)、[固定 Harness 源码](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java)

本报告明确区分三层：

| 层次 | 负责内容 | 约束形式 |
| --- | --- | --- |
| SDK 运行机制 | 推理循环、工具分发、结构化协议、状态存取、事件、挂起恢复 | Java API 与执行逻辑 |
| 平台业务与安全 | 用户/项目/对象授权、场景准入、预算、证据、并发条件保存、审计 | 服务端执行守卫与 owning domain 命令 |
| Skill 方法 | 任务步骤、领域表达、检查清单、正反示例、何时澄清 | 模型指令；需要测试和真实评测 |

“Skill 中写了禁止”不能代替服务端禁止；“SDK 有审批”不能替代平台的发布审批；“JSON 格式正确”不能证明标准绑定或指标口径正确。这是本项目已有 [Agent Domain](../../data-ops-business/data-ops-business-agent/DOMAIN.md)、[F-010](../product/features/F-010-ai-governance-suggestions.md)、[F-011](../product/features/F-011-agent-task-execution-controls.md) 对不可信模型输出与 Truth Owner 的要求。

## 2. 产品起点

| 必答项 | 本次方向 |
| --- | --- |
| User | 数据标准维护人员、建模人员、指标开发人员，以及现有资产/质量治理用户 |
| Problem | 固定工作流程中大量字段理解、匹配、补充、对照和校验准备依靠手工；单纯聊天回答难以进入原工作台完成任务 |
| Capability | 将重复的知识判断与材料准备做成场景 Skill，生成可核对候选和检查结果 |
| User Journey | 原模块选择对象与任务 → 授权上下文 → 场景 Skill → 候选与依据 → 用户修改/采纳 → 原模块校验保存 → 回读结果 |
| Expected Outcome | 减少准备时间和返工，提高原工作流程完成率，保留业务责任与真实结果 |
| Truth Owner | 标准、模型、指标及其版本仍由各原域拥有；Agent 消费事实，不建立第二套定义 |
| Producer / Consumer | 原域公开 API 生产授权事实和校验结果；Agent/Skill 消费；原编辑器消费候选，原命令处理保存 |
| Existing reuse | AgentScope、原会话/轮次/事件/trace、用户执行上下文、源域权限/校验/CAS/审计与原编辑器 |
| E2E evidence | 原工作台到候选、冲突/撤权/停止、人工保存及回读；真实模型依据支持和任务耗时另行记录 |

产品依据是 [PD-001](../product/decisions/PD-001-asset-governance-hub.md)、[PD-002](../product/decisions/PD-002-governed-consumption-contract.md) 以及上列活动 Feature。标准、建模、指标的新场景仍须读取各域当前产品契约后形成独立 Feature；本报告没有授权新增发布规则、批量写入、一级导航或业务状态机。

## 3. 资料可信度与版本风险

用户提供的 [快速开始](https://java.agentscope.io/v2/zh/docs/quickstart) 和 [文档索引](https://java.agentscope.io/llms.txt) 用于发现能力。官网 `/v2/` 是滚动文档，不能把当前页面全部视为已发布 2.0.3 的 API。因此本报告的可用性结论以 `v2.0.3` 固定源码/固定文档为准；没有以 Python AgentScope、社区评测服务或其他版本替代 Java SDK 的事实。

2.0.3 有实际升级收益：状态读取错误不再静默变成新会话，中断时补齐未匹配工具结果，结构化输出保留 token usage，另有 Skill 缓存清理和 Windows 路径修复。新增 `FinalAnswerFilterMiddleware`、状态版本/CAS、事件总线等能力并不表示平台必须全部启用。[2.0.3 发布说明](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.3)

### 3.1 这次升级包含 SDK 状态表变化

MySQL Store 构造最后调用 `ensureVersionColumn()`，包括 `createIfNotExist=false` 路径：检查 `INFORMATION_SCHEMA.COLUMNS`，缺列则执行 `ALTER TABLE ... ADD COLUMN version BIGINT NOT NULL DEFAULT 0`。其检查后修改没有 `IF NOT EXISTS`；多实例同时首次升级存在竞争窗口，需验证部署顺序及权限。[固定 MySQL Store](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-extensions/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/state/MysqlAgentStateStore.java)

PostgreSQL Store 同样在构造时执行补列，使用 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0`。已有列也会执行该 DDL，不能据此假设业务账号只需要 DML 权限。[固定 PostgreSQL Store](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/state/PostgresAgentStateStore.java)

这是 **SDK-owned StateStore 表** 的兼容升级，不是 Agent 新增业务事实表。上线前需核对实际表名、备份和部署账号权限；可在维护窗口由受控迁移账号预先处理 MySQL 补列，避免运行账号长期持有 ALTER 权限。PostgreSQL 因构造仍执行 DDL，需按实测决定权限/装配方案。本次调研未连接、未修改生产库。

### 3.2 默认 CAS 不等于默认拒绝并发覆盖

`AgentStateStore` 的版本接口是可选能力；默认 `supportsVersioning=false` 时仍可无条件保存。MySQL/PostgreSQL 的 2.0.3 实现支持版本；ReActAgent 的冲突策略默认是 **OVERWRITE**，冲突后尝试无条件保存，而非自动拒绝。[Store 接口](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java)、[ConflictPolicy](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/state/ConflictPolicy.java)、[ReActAgent 源码](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java)

由固定源码可推导一个迁移细节：旧表补列后已有行的版本为 0，而 SQL Store 的 `saveIfVersion(..., 0)` 走 create-if-absent；既有行会产生 CAS 冲突。默认 OVERWRITE 可随后推进版本，但不能在此次依赖升级中直接切为 FAIL 并声称无兼容风险。应覆盖“旧历史读取、首轮继续、预算保留、版本推进”的隔离数据库回归；严格 FAIL 策略及跨节点执行单飞另立验收。这个推导来自上述两个固定 SQL Store 和 ReActAgent 的调用组合，不是已执行的生产观察。

## 4. 框架能力与仓库复用矩阵

| 需要的能力 | 2.0.3 已有机制 | 仓库现状与建议 |
| --- | --- | --- |
| 推理/工具循环 | ReActAgent | 已复用；不写第二套 Agent 循环 |
| 请求身份与扩展参数 | RuntimeContext 的 userId/sessionId、typed/string attributes | 已复用；继续传本轮可信任务，避免共享实例存当前请求 |
| Skill 仓储 | AgentSkillRepository；Classpath/FileSystem 及扩展仓储 | 已有 DB Adapter；沿用已有 Skill 定义 owner |
| 动态目录与内容更新 | DynamicSkillMiddleware + SkillFilter | 已有子类；增加场景可见性与实际版本证据，不重写通用扫描器 |
| 按需正文/资源加载 | SDK load_skill_through_path | 平台有意限制为当前启用 SKILL.md；未来资源开放须独立设计 |
| 工具 Schema/注册/分组 | Toolkit、ToolBase/@Tool、ToolGroup | 已复用；原域工具薄壳继续负责调用授权公开 API |
| 结构化候选 | ReActAgent 结构化 call；native response_format / synthetic generate_response | 新场景优先使用；仍做领域校验和证据核验 |
| 生命周期扩展 | 五个 Middleware stage | 已复用；任务守卫、预算和观测使用该 seam |
| 会话状态与历史 | AgentStateStore、AgentState | 已复用官方表；不新建消息真相 |
| 人机交互 | pending/external tool/confirm 与后续结果恢复 | 已接澄清；保留原 turn/toolCallId/目标/预算校验 |
| 同会话串行 | AgentBase 按 ReAct slot key 的内存 gate | 复用单实例机制；不能替代平台持久化轮次 CAS 或跨节点准入 |
| 事件流 | streamEvents、AgentEvent | 当前映射与投递日志保留；SSE 断线不改业务执行事实 |
| 上下文压缩 | Harness CompactionMiddleware | 已选择性复用，不必整体 Harness 迁移 |
| 工程 tracing | OtelTracingMiddleware、Studio 扩展 | 可导出标准 trace；不替代平台审计、执行合同与脱敏策略 |
| 长程文件工作区/沙箱 | HarnessAgent + filesystem/sandbox | 未来有明确文件任务时试点；不为表单候选默认开放 shell |
| 确定性工作流 | 2.0 不再提供旧 core Pipeline API | 固定步骤留给现有领域用例/任务编排；Agent 处理其中的知识判断 |

上表 SDK 事实对应 [ReActAgent](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java)、[RuntimeContext](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/agent/RuntimeContext.java)、[Repository](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/repository/AgentSkillRepository.java)、[MiddlewareBase](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java)、[固定迁移说明](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/change-log.md)。仓库对应 [AgentRuntime](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/AgentRuntime.java)、[RuntimeSkillMiddleware](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/RuntimeSkillMiddleware.java)、[Repository Adapter](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/repository/AgentSkillRepositoryAdapter.java)。

## 5. Skill 可以如何灵活适配业务

### 5.1 现有接线已经复用了框架

`RuntimeSkillMiddleware` 继承 SDK `DynamicSkillMiddleware`，由框架生成技能目录；`AgentSkillRepositoryAdapter` 实现 SDK Repository，现读 DB 当前启用集。现有管理版本与启停由 `yak_agent_skill` 拥有。`docs/ai/skills` 中三份正文只是候选评测材料，未默认注册；不能把文件存在当成运行已加载。[运行时接线](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/RuntimeSkillMiddleware.java)、[仓储](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/repository/AgentSkillRepositoryAdapter.java)、[候选说明](./skills/README.md)

SDK `SkillBox` 在 2.0.3 仍用于内部装配，但已标记 deprecated-for-removal；新建设应面向 Repository 和 Middleware API，不把 `getCurrentSkillBox()` 扩展为平台公开管理合同。[固定 SkillBox](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/SkillBox.java)

### 5.2 目录按场景可见，正文按需读取

固定版本提供 `SkillFilter.only/none/except` 及 `enable/disable` overlay，DynamicSkillMiddleware 从本次 `RuntimeContext` 读取 `SkillFilter.class`。可让“标准匹配”只看匹配与候选核对的目录，“模型字段映射”只看字段映射目录。它解决模型关注范围，不是安全准入。[固定 SkillFilter](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/SkillFilter.java)、[固定 DynamicSkillMiddleware](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java)

建议顺序：

1. 原模块明确指定已登记场景和对象；服务器核验本轮身份、目标、purpose 与可用 Skill。
2. 把同轮允许的 Skill 身份/版本/实际内容指纹放在 RuntimeContext，使用 SDK 过滤提示目录。
3. 加载正文时同时检查本轮 allowlist 与当前启用状态；停用/删除不能经旧加载器重新生效。
4. 对显式必需 Skill，加载失败应给诚实的不可用结果；不能因仓储故障自动退回无限制通用任务。
5. 只给当前场景必要的工具 schema；真实执行仍由服务端任务策略及源域权限把关。

其中步骤 1/2/3/4 的产品规则是后续提案，现有实现仍只保证管理员启用正文加载，并未完成场景版本冻结。治理目标也不等于权限。[F-011 当前执行合同](../product/features/F-011-agent-task-execution-controls.md)

### 5.3 不能把提示过滤当成授权

2.0.3 的 `filterVisible` 是扩展 hook，但异常或返回 null 会回退为 merged 集合；仓储异常记录后可继续处理其他仓储。Repository 的 `getAllSkills()` 不接 RuntimeContext。对硬隔离需求，应先在平台执行入口和加载器建立拒绝策略，不能只覆写这个 hook。[固定 DynamicSkillMiddleware](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java)、[固定 Repository](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/repository/AgentSkillRepository.java)

SDK 的内部内容签名覆盖 name、正文、资源内容与 originDir，未覆盖 description/metadata/version；不能把它当完整 Skill 发布版本证明。平台应记录实际使用的版本与目录/正文 hash，并明确同轮热编辑的处理语义，防止目录来自旧版本而正文来自新版本。[签名实现](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java)

### 5.4 现有受控加载器不是应立即删除的重复实现

SDK 原始 `load_skill_through_path` 可读正文及扩展资源，成功加载会激活相应 Skill 与绑定工具组。平台现有 `TaskScopedTool` 特意不调用该 helper：它只读当前启用 SKILL.md、不激活额外工具，并在真实调用前预占本轮预算。这是本项目授权等价所需的窄适配。[SDK SkillToolFactory](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/SkillToolFactory.java)、[平台 TaskScopedTool](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/TaskScopedTool.java)

需要扩展 references/templates 时，优先复用 SDK 的 AgentSkill resources 与仓储解析；先限定只读文本、大小/路径/类型、实际内容版本与场景允许集合，再验证能否在安全薄壳后委托官方实现。脚本执行、shell 和工具自动激活属于另外的执行能力，不能因为 Skill 携带文件就自动开启。

**并发限制：** 同一个 Agent 的 Toolkit 跨该实例多个调用共享；DynamicSkillMiddleware 持有可变的 `currentSkillBox`、仓储内容签名和 toolkit 引用。不能为请求 A 全局启用标准组、请求 B 全局关闭标准组来实现隔离。若未来需要差异资源或动态工具组，先验证调用级隔离；必要时使用受控 Agent factory/独立 middleware+Toolkit，继续共享 Model/StateStore，不自写推理引擎。[固定 ReActAgent Toolkit 说明](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java)、[固定 DynamicSkillMiddleware](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java)

## 6. 场景 Skill 合同建议

一份 Skill 对应一个能被原工作台接住的任务，例如“为这些字段匹配已有标准”，不要把“负责所有标准治理”写成一个大 Skill。业务表达和例子可快速调整；能力边界与结果协议由服务器登记、校验和评测。

| 合同项 | Skill 负责 | 平台/原域负责 |
| --- | --- | --- |
| 任务说明 | 何时使用、适用/不适用、完成标准 | 已登记场景与入口准入 |
| 输入要求 | 需要哪些信息；不足时如何澄清 | 对象身份、源版本、授权读、输入长度 |
| 方法与顺序 | 匹配策略、解释方式、检查清单、例子 | 必须执行的读取/校验/权限步骤 |
| 工具使用 | 如何使用已经提供的工具 | 工具是否允许、目标是否匹配、预算和撤权 |
| 结果形状 | 解释字段、依据、缺口、不确定项 | Schema、候选数量/字段白名单、证据校验 |
| 采纳交接 | 提示用户核对与修改 | 原编辑器带入、条件保存、发布/运行与审计 |
| 版本与评测 | 修订说明、场景样例 | 管理版本、实际 hash、回归与真实评测记录 |

该表是平台场景合同建议，不声称 AgentScope 自动解释这些字段，也不要求为此创建新 YAML 解析语言或第二个 Skill 注册中心。使用 SDK 现有 name/description/content/resources/metadata 及已有管理员管理面承载适合的部分；安全规则保留在 Java 和领域合同中。[固定 AgentSkill](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/skill/AgentSkill.java)、[固定 Skill 格式与仓储说明](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/harness/skill.md)

建议首批逐个闭环：

| 场景 | 可灵活调整的 Skill 方法 | 必需确定性保障 | 原页面交接 |
| --- | --- | --- | --- |
| 字段标准匹配 | 名称/描述/类型结合匹配、歧义解释、无匹配说明 | 已有标准目录授权、候选 ID 存在、当前版本、禁止创造绑定事实 | 用户逐项核对并调用原绑定命令 |
| 标准草稿整理 | 术语/定义/示例整理、冲突清单 | 分类与属性合同、已有标准重复核对、原保存校验 | 草稿带入标准编辑器 |
| 模型字段映射 | 来源选择理由、类型差异说明、缺口澄清 | 来源对象授权、字段存在/类型规则、模型版本与字段指纹 | 字段/映射表对照后人工保存 |
| 指标口径准备 | 澄清粒度/时间/去重/过滤、定义解释 | 指标类型/依赖/可用字段与既有验证器；不直接执行生成 SQL | 定义草稿进入原指标编辑/验证流程 |
| 质量规则建议 | 使用已有模板、业务阈值澄清 | 模板白名单、最多候选数、目标固定、默认停用与条件保存 | 沿用现有 Quality 建议面板 |

前三个新领域场景需先形成当前域 Feature；指标还需核对 PD-003 的实际状态，不能将未 ACCEPTED 决策提升为产品事实。本报告不扩大当前 Agent 对物理数据源和 SQL 的访问范围。

## 7. 结构化输出优先复用，业务校验继续保留

2.0.3 已有带 RuntimeContext 的结构化 `call` 重载，支持 Java Class 或 JSON Schema 路径。框架按模型能力选择原生 response_format；不支持时用本次 CallExecution 中的 `generate_response` 合成工具，结果放入结构化 metadata。结构化工具不是在共享 Toolkit 上逐次注册。[固定 ReActAgent 结构化实现](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java)

后续标准/模型/指标候选优先消费结构化对象，再由已有领域校验器验证对象 ID、字段、类型、版本与业务关系；解释正文只是用户阅读材料。不要让 Skill 自定义一种 fenced JSON 文本并要求每个页面各写一套正则解析器。

接入前需核对当前任务工具策略：`generate_response` 是否经过 onActing、是否按本轮预算计数、模型拒绝/畸形结果/原生回退如何呈现、HITL 后是否保持同一目标。SDK 合成工具与当前“未知工具拒绝”守卫不能靠全局放宽解决。还要验证本地模型/provider 原生能力声明与真实响应是否一致。

SDK 的格式机制不负责领域正确性，也不保证每次模型都能交付合法候选。格式失败、证据不足、旧版本及无权限仍应成为明确不可采纳结果。沿用 [GovernanceAnswerGuard](../../data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/runtime/GovernanceAnswerGuard.java) 与源域候选校验思想，按新场景建立等价回归。

## 8. 固定流程怎么编排

**不要使用 v1 的 Pipeline 示例建设新功能。** 2.0.3 固定迁移说明明确删除 `io.agentscope.core.pipeline.*`，包括 Pipeline、Pipelines、SequentialPipeline、FanoutPipeline、MsgHub，推荐 middleware/子 Agent/event stream 组合。[固定迁移说明](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/change-log.md)

对 DataOps，固定流程应先复用现有确定性应用用例：读取 → 准备上下文 → AI 候选 → 原验证 → 用户采纳 → 原保存。Skill 表达其中需要语言/知识判断的步骤；权限、发布、执行与版本规则仍由原流程强制。简单任务不需要先拆多个 Agent；真实出现不同权限边界、独立专家上下文或足够并行价值时，再复用 SDK 子 Agent 工具。

官网中的 AgentScope Service workflow 属于独立服务控制平面的产品；调研时页面明确标注 preview、正式发布尚不可用，界面介绍使用固定演示数据。不能将它列为 2.0.3 SDK 可直接复用的稳定工作流能力，也不能因此替换平台现有 Task/Approval/业务状态机。[官网 Service 工作流（滚动预览文档）](https://java.agentscope.io/v2/en/service/workflows)

## 9. HarnessAgent 迁移判断

固定版本 Harness 包装 ReActAgent，组合 workspace、filesystem、sandbox、技能、子 Agent、memory、plan mode/MCP；默认工作区和文件状态位置也会介入运行。它并非另一种更强的治理业务模型。[固定架构说明](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/harness/architecture.md)

| 需求 | 当前选择 |
| --- | --- |
| 表单候选、标准匹配、口径澄清 | 保留 ReActAgent + Repository/Middleware/受控工具 |
| 有界聊天压缩 | 继续选择性复用已有 CompactionMiddleware |
| 长程文档分析、需要文件成果与隔离计算 | 单独试点 Harness 文件系统/沙箱 |
| 子 Agent 专家任务 | 先核对权限、目标与预算传播，再采用官方子 Agent 工具 |
| 自动维护/推广 Skill | 暂不启用；先管理员管理、版本和评测 |

未来试点 Harness 必须避免引入第二套平台真相：明确注入现有 StateStore，核对 transcript/文件记忆与当前 DB owner；关闭不需要的文件/shell/记忆/动态工具能力；验证 middleware 次序、HITL、中断、持久化和源域守卫。固定源码虽有 `Builder.fromAgent` 辅助和各 `disable*` 开关，仍不等于迁移后行为自动等价。[固定 Harness Builder](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java)

## 10. 状态、HITL、观测与评测

SDK 同会话串行由 AgentBase 实例的内存 `callGates` 实现，ReAct 使用 `(userId, sessionId)` key。不同 Agent 实例/副本不共用这个 Map；共享数据库和 CAS 也不能阻止重复业务副作用。平台现有提交认领、turnId 精确取消、持久化预算以及重启 INTERRUPTED 语义继续保留。[固定 AgentBase](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/agent/AgentBase.java)、[现有生命周期合同](../../data-ops-business/data-ops-business-agent/DOMAIN.md)

SDK pending/confirm/external-execution 负责暂停和结果恢复，平台仍检查用户、项目、原 turn、toolCallId、工具名与固定目标。人机澄清、工具权限确认、业务发布审批分别是不同动作；不可用一个通用“已批准”替代全部语义。[固定 ReActAgent HITL 实现](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java)、[F-011](../product/features/F-011-agent-task-execution-controls.md)

工程 tracing 优先复用 OtelTracingMiddleware，其覆盖 Agent、模型、工具阶段，读取 GlobalOpenTelemetry；导出 SDK/OTLP 由应用装配。Studio 可作为开发调试消费端，但不能默认启用全量私有提示词采集或另立线上审计。[固定 OtelTracingMiddleware](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/agentscope-core/src/main/java/io/agentscope/core/tracing/OtelTracingMiddleware.java)、[固定 Studio 接入](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/integration/ecosystem/studio.md)

`FinalAnswerFilterMiddleware` 过滤产生工具调用的中间轮文本，只输出最终轮文本；它不验证事实、引用或候选。因此不能直接替代 GovernanceAnswerGuard，且启用前须核对历史正文与 SSE 的一致性。[固定 Middleware 文档](https://github.com/agentscope-ai/agentscope-java/blob/v2.0.3/docs/v2/zh/docs/building-blocks/middleware.md#finalanswerfiltermiddleware)

Skill 质量需要用任务结果评测，而非加载成功率：固定输入/授权源快照/Skill 版本/hash/模型配置，比较无 Skill 与有 Skill 的依据支持、澄清质量、候选有效率、实际保存成功、返工、耗时/token。复用现有离线/真实评测与 trace，不因 SDK 能输出 span 就宣称业务收益已达成。[F-011 验收合同](../product/features/F-011-agent-task-execution-controls.md)、[现有评测 Skill 说明](./skills/README.md)

## 11. 推荐实施顺序与交付标准

1. **升级基线：** 所有已使用 AgentScope artifact 同步到 2.0.3；核对依赖收敛和版本展示；回归旧状态、pending、取消、Skill 热更新与权限；对旧 MySQL schema 做隔离升级测试。生产数据库/真实模型未验证项诚实列待办。
2. **场景合同：** 先做一个“标准匹配”闭环，定义入口、允许工具、输出 Schema、版本/指纹、人工交接和负面路径；按产品治理批准后实施。
3. **Skill 按需装配：** 优先复用 RuntimeContext + SkillFilter + 既有 Repository，补同轮加载 allowlist 与实际版本/hash；在并发回归前不开放全局工具组切换。
4. **结构化交付：** 复用 SDK 结构化调用，接入任务预算/守卫和原域校验；处理 provider 回退、坏结果及历史一致性。
5. **复制到模型/指标：** 复用稳定机制，增加各域公开只读工具和场景 Skill；每个场景以原页面采纳/保存/回读完成为验收终点。
6. **按证据扩展：** 有真实文件任务后再试点 Harness/沙箱/只读资源；有明确收益后再引入子 Agent/更自动化执行。

依赖升级是本次可完成的工程变更；本报告中的新场景、资源、调用路径和 Harness 试点为后续提案，未声称已经实现。真实模型准确性、费用和用户收益继续 **PENDING**，按用户已确认的“先代码与 CI，真实模型验收另记”执行。
