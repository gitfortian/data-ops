# AI 下一阶段技术方案

日期：2026-10-05
类别/状态：DOCS / PROPOSED；以下接口、输出结构和配置均为候选设计，尚未实现。
对应范围：[产品规划](./NEXT_PHASE_PLAN.md)、[交付清单](./NEXT_PHASE_BACKLOG.md)。

## 1. 继续复用当前运行时

保留 AgentScope Java 2.0.2；模型装配、ReAct、Toolkit、官方 StateStore、SSE、HITL、取消和轮次生命周期不重写。只在现有 Agent 的 runtime/toolset/gateway/domain 边界完善证据输出与候选建议；源域负责允许输入的定义与校验。

调用方向：

```text
原页面目标 / 用户问题
 -> AgentChatService / AgentRuntime
 -> 当前用户与 Project 守卫
 -> 工具 -> Agent Gateway -> 来源公开只读 API
 -> 有界证据 + 候选结构
 -> 校验 / 拒绝 / 澄清 -> 结果与证据卡片
 -> 用户选择 -> 原编辑器未保存内容
 -> 用户明确保存 -> 源域原命令 / 权限 / 校验 / 审计
 -> 用户明确运行 -> Quality 原执行入口
```

不让 Agent Gateway import Quality 的 monitor/template/repository/dao 内部类型，不让模型构造可信身份、项目、回链或业务版本。新增 source-owned API 的 adapter 放在事实 Owner 内部，进入已有 Reader/Policy；依赖走廊改变时同步域契约与架构测试。

## 2. 当前实现复用清单

以下路径相对仓库根目录，可直接定位本阶段切片；不是新建通用组件清单。

| 能力 | 当前代码入口 | 下一阶段责任 |
|---|---|---|
| 入口与会话 | `data-ops-ui/src/pages/ai-agent/`、`services/agent/governance.ts` | 扩展结构化回答/候选展示，保留目标清理、恢复与归属语义 |
| 治理证据 | Agent `toolset/GovernanceEvidenceTools`、`gateway/GovernanceEvidenceGateway` | 新增窄读取契约与参数边界，保持每次真实授权和内容裁剪 |
| 证据与输出 | Agent `domain/GovernanceEvidenceLedger`、`runtime/GovernanceContextMiddleware`、`runtime/GovernanceAnswerGuard` | 事实字段关联、候选校验、拒绝正文与历史一致、证据投影 |
| 质量历史 | Quality `api/QualityEvidenceQueryApi` | 继续只读本次历史 Execution/RuleExecution，不回读当前规则解释过去 |
| 质量定义 | Quality `template/QualityTemplateReader`、`monitor/QualityMonitorReader`、`monitor/QualityRulePolicy` | 公开经授权的建议上下文与纯定义校验，复用唯一规则语义 |
| 字段来源 | Quality `gateway/QualityDataCatalogGateway` | 由 Quality adapter 提供目标字段，只读元数据；Agent 不访问数据源连接与 SQL 执行器 |
| 规则编辑与保存 | `data-ops-ui/src/pages/data-quality/monitor/editor/`、Quality `QualityMonitorManager` | 白名单映射与人工合并；补源域并发契约后才能保证旧建议拒绝 |
| 描述编辑 | `data-ops-ui/src/pages/data-asset/detail/index.tsx`、Asset `AssetAppService.updateSnapshot` | 仅带入 description；当前值冲突与原保存审计 |
| 配置与观测 | `application-ai.yaml`、Agent `config/AgentProperties`、`telemetry/AgentStepRecorder`、`AgentConfigManageService` | 实际生效配置快照、耗时/用量与失败分类；不另建日志真相 |

主要契约：[Agent](../../data-ops-business/data-ops-business-agent/DOMAIN.md)、[Quality](../../data-ops-business/data-ops-business-quality/DOMAIN.md)、[Asset](../../data-ops-business/data-ops-business-asset/DOMAIN.md)、[Security](../../data-ops-business/data-ops-business-security/DOMAIN.md)。

## 3. 证据与回答校验

在现有证据登记上增加受控投影：来源 Owner、对象引用、读取时间、源更新时间或 unknown、版本或 unknown、状态、截断标志、可引用字段和服务端回链。保存的是回答生成时的证据材料与执行记录，不能反向作为当前源域查询或授权的 Truth。

候选回答结构：

```json
{
  "facts": [
    {
      "text": "本次规则未执行",
      "evidenceRefs": ["<本轮登记的证据ID>"],
      "factRefs": [{"ref": "<同一证据ID>", "field": "<允许字段路径>"}]
    }
  ],
  "hypotheses": [],
  "recommendations": [],
  "missingEvidence": []
}
```

模型输出全部按不可信输入处理：校验结构、长度、类型、引用作用域、来源状态和可引用字段；关键数值/状态由服务器读取证据并渲染，不能让模型自报值后当作已验证值。字段路径只访问白名单事实，不提供任意表达式或对象反射能力。

确定性校验拒绝伪造 ID、身份、错误状态和数值；任意自由文本与证据是否语义相符仍需专家题集。模型 judge 仅辅助质量评估，不能放行权限、安全、版本或参数校验。不能把自报 confidence 映射为业务可信等级。

证据卡片先显示 Owner/时间/状态/范围，再展示允许的简短摘要与原页面入口；不向客户端直接透出完整工具参数、原始异常、SQL或连接配置。查看历史仍先执行会话归属；源页面回链重新授权。权限撤销后对已持久化敏感摘要如何处理需在 Feature 中明确，不能承诺能撤回用户已经看到的内容；不允许旧 trace 成为新的绕权读入口。

HITL 恢复、新轮追问重新读取需要的源事实并建立本次登记；不得把旧 invocation 的引用 ID 当作当前可读证据。引用作用域和结构化材料留痕放在现有 StateStore/step 所属记录，SSE 仅为投递投影，不新增“AI 治理证据真相表”。读取材料缺失时明确无法恢复该摘要，不伪造原快照。

正文继续完成校验后发布，进度独立流式展示。当前所有 TextBlockDeltaEvent 都被缓冲，普通问数也可能受到延迟影响；若要优化，先设计生成前可确认的输出模式与混合工具场景。不能在先流出正文后才发现治理工具并切换校验，已发送文本无法撤回。

## 4. Quality 拥有候选输入与校验语义

建议在 Quality `api` 增加窄的建议上下文/校验契约（名称待实施时确定），以 source-neutral record 对外暴露：

- 当前 Project 下已注册的物理目标、monitorId（已有时）、现有规则的安全投影；排除 custom SQL、连接配置、通知目标及原始异常。
- 经授权读取的字段名、类型和有限注释；精确 database/schema/table identity，字段快照摘要与读取时间，无法读取时明确状态。
- 当前检查范围的安全说明/服务器指纹；存在过滤条件时只说明范围限制，不把原始 whereClause 交给模型。历史 metric 绑定历史执行范围，不能推成当前全表统计。
- 获准用于建议的 built-in template ID/code/type/scope/单位/支持的参数，模板只读。当前内置模板接口使用 LEGACY_GLOBAL，新的契约必须明确其共享定义与权限边界，不能暗示已有项目私有模板机制。
- Quality-owned 无副作用候选校验结果：合法字段/类型、模板语义、operator、threshold、thresholdEnd、enumValues、目标仍存在。沿用 `QualityRulePolicy.normalize`，补齐它目前未证明覆盖的实际字段存在/类型约束，不能让 Agent 自己复制规则语义。

工具只接受目标引用、筛选和有界候选，不接受执行身份/项目/SQL。每次调用同时核验 Agent 执行权限及源域对应读取权限；源域 adapter 不能只依赖 HTTP Controller 注解。候选生成与最终保存权限分离，能读不代表能改或能运行。

先建议上限：候选 5 条、字段 200 项、模板按白名单取用、历史执行范围明确、模型自纠最多 2 次。数量上限/总字节预算在实现契约冻结；达到限制时标截断并要求选择字段，不能宣称完成全表评估。

### 候选数据与表单映射

| 字段 | 生成/校验责任 |
|---|---|
| 目标 identity / base definition / evidence refs | 服务器依据授权上下文绑定；模型不可更换 |
| templateId / columnName | 必须在本次上下文和 Quality 校验允许集合内 |
| name / operator / threshold / thresholdEnd / enumValues | 模型提出，Quality 校验；缺业务依据则追问或移入待确认，不能作为可带入合法候选 |
| rationale / assumptions | 解释建议而非检测结论；引用本轮证据 |
| enabled | 带入新规则固定 false，由用户原页面明确决定 |
| customSql / schedule / owner / target / notification | 不从候选映射；禁止用整个模型 JSON 替换 SaveRequest |

客户端逐条追加到未保存规则，按本轮候选标识防重复带入；重新生成或用户改参数后不依赖模糊名称去重。切换表、Project、监控或会话，旧候选失效。非法候选不能只靠隐藏按钮保护，服务端校验必须参与交付。

保存仍走既有 `QualityMonitorController` → converter → `QualityMonitorManager`。读到旧 execution 不代表有权改当前 monitor；保存时再次授权、校验目标及候选适用性。保留当前规则，不能用生成集合替换整套 rules。

## 5. 并发、保存副作用与版本

源码中 `QualityMonitorDTO.SaveRequest` 没有 expected revision 字段，`QualityMonitorManager.update` 的行锁保护更新过程，但没有比较用户读取时的定义。**有锁不等于防止旧表单覆盖新编辑。** Asset 描述编辑也不能仅凭前端回读声称原子冲突保护。

建议在对应源域批准一个窄的条件更新契约：读取时产生当前可编辑定义的服务器指纹/修订号；保存时提交 expected 值；在拥有更新事务和锁的边界重新比较，失配拒绝并要求重新读取/审核。Quality 比较内容须覆盖 Monitor/Rules/Settings，而不只是 Monitor.updatedAt；Asset 覆盖当前描述及会被原请求同时更新的字段。hash 只能用于原子比较内容，不替代权限或源域版本。

该变更属于源域行为与 API 兼容扩展，需要 Feature、Domain/Requirements 和行为测试，不是已经存在的能力。保留旧客户端兼容规则，但 AI 带入后的更新不能通过省略 expected 值降级；如何绑定该路径由源域 transport 契约明确。如果暂不实施此契约，只能提供人工参考/复制，不能交付“安全覆盖既有配置”的采纳承诺。

Quality 保存会调用 `scheduleLifecycle.sync` 和 `taskPublisher.sync`。UI 展示当前与待保存规则/启用配置的差异，说明已有自动运行受到哪些变更影响；AI 不调用保存、发布、运行或调度工具。定义校验不调用 Manager，不发布 Task revision、不落 Execution、不触发 Schedule。保存后的运行保持原 admission、不可变计划和运行权限。

## 6. Asset 描述辅助

复用现有 Asset API 的授权台账和 Section 证据；只对适用、获准读取的事实提炼描述。台账描述是治理展示事实，不能改写源域物理备注。

输出是 description + evidenceRefs + 缺口说明，描述长度取 Asset DTO 的实际上限；模型生成不得新增任意链接/HTML。带入时只设置 description，保留原 name/accessUri 和其他未保存修改；提交兼容映射不能让缺失字段清空其他内容。来源注释中出现“忽略规则/执行工具”等文本按数据处理，不进入系统指令。

并发保护由 Asset 更新边界实现；已有原保存审计继续记录真实成功写入。AI 输出留痕可记录来源 turnId 与候选指纹，是否扩展业务审计关联字段在 Feature 中确定；不能宣称现有审计已经能完整追踪 AI 来源。

## 7. 配置、记忆与运行预算

所有新增启动项继续放 `data-ops-boot/src/main/resources/application-ai.yaml`，绑定现有 AgentProperties；业务源域规则不移入 AI 配置。下一阶段能力开关、候选上限、自纠次数等只在实现确定后加，文档不预写未生效配置。

当前 startup model.name/base-url/api-key/reasoning-effort 已有配置；不要新增一个未被 runtime 消费的 llm 配置树。动态配置的键名、实际消费者与覆盖优先级见[交付说明](./IMPLEMENTATION.md)，记录实际运行时值，而不是只显示 YAML 默认值。凭据保持环境变量注入、runtime 短生命周期；不显示原值或将密钥放入配置快照。

一次执行记录模型/provider、prompt/Skill/tool契约版本或指纹、有效预算、来源范围、LLM/工具耗时、usage 与失败类型。复用 AgentStepRecorder，记录失败不能改写业务执行结果。变更模型、prompt、Skill、记忆或压缩策略复跑同一题集；没有 usage 或价格时费用显示 unknown。

已有长期记忆可表达偏好，但历史质量状态、分级、访问许可和旧建议不能作为当前事实。治理问题重新授权取证；压缩保留目标/时点/来源限制，不把摘要提升为 Truth。治理事实是否允许进入长期记忆及留存/删除语义是待冻结的策略；在未批准、未验收前，建议对试点治理路径禁用该类写入/召回，不能仅靠 prompt 承诺隔离。

每轮总时限、LLM 超时、最大迭代、队列容量和候选重试共同受预算控制；重复 provider 读取先限制次数，再考虑请求内去重。跨用户/项目缓存不得沿用旧权限判断，当前阶段优先不新增共享治理事实缓存。

## 8. 验证与回退

重点测试真实身份守卫、源域窄 API 权限、候选 Policy、HITL 恢复、最终回答/历史一致、表单不误写、并发条件更新和保存副作用。纯渲染文案不用增加镜像实现的测试；核心边界用有行为意义的集成测试。

真实模型 E2E 包含用户登录、源数据/规则、候选、手工保存、显式运行、审计与拒绝路径；自动化替身不替代产品验收。停止或断连不能误调用源域命令；旧候选回放不能重新采纳到不同目标。

灰度分别关闭建议生成/带入入口，保留现有普通编辑；服务端同样限制新能力，不能只隐藏按钮。回退不删除已由用户保存的源域规则与描述，也不回滚真实 Execution；修复已采纳内容继续走源域正常编辑与审计。
