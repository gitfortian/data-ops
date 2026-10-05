# AI 产品规划：围绕数据治理任务形成闭环

日期：2026-10-05  
文档类别：规划提案，待评审，不替代当前 Product / Domain Contract。

本文保留开发前的规划快照。用户随后批准首版范围并开始实施；当前授权范围见 [F-009](../product/features/F-009-ai-governance-assistance.md)，代码与验证状态见 [首版交付说明](./IMPLEMENTATION.md)。第二、三期仍是规划。

## 1. 用户、问题与期望结果

| 必答项 | 本方案的回答 |
|---|---|
| User | 数据治理人员与资产 Owner 为首期主要用户；后续覆盖数据工程师、指标 Owner、分析人员、安全治理人员 |
| Problem | 平台事实分散在治理分区和专业页面；理解资产、定位质量异常、填写描述和规则需要反复查阅，建议与真实处置脱节 |
| Capability | 数据资产与治理、标准指标与建模、开发与运行、受治理消费中的智能辅助 |
| User Journey | 首期改善 J4 和 J6；逐步服务 J1/J2/J3/J5，不新增默认跨域旅程 |
| Expected Outcome | 用户更快得到可核验解释并进入正确处置入口；减少手工编写成本，同时保持人工与领域规则控制业务生效 |
| Truth Owner | 业务事实仍由现有领域拥有；Agent 仅拥有自己的会话、轮次、报告和执行留痕，模型结论不成为业务 Truth |
| Producer / Consumer | Metadata、Asset、Quality、Security、Lineage、Semantic、Metric、Dataset、Consumption 等生产事实；AI 辅助与用户消费授权范围内的事实 |
| Reuse | Asset Sections、稳定 ID、Dataset Query、发布版本、消费证据、Project Space、权限、安全、审批、审计、告警、现有 Agent 运行时 |
| E2E acceptance evidence | 登录用户从真实资产/质量执行发问 → 正确权限下读取来源 → 有证据回答 → 稳定回链；负向场景不泄漏、不伪造、不执行未授权动作 |

首期聚焦一个业务域和一个 Project，建议从已有真实样本中选定；没有用户任务和真实证据的数据对象不进入首期。平台“功能基本都有”可以作为规划起点，但不能代替相关路径的闭环验收。

## 2. 当前产品契约如何约束 AI

| 依据 | 对规划的约束 |
|---|---|
| [产品原则 P11](../product/PRODUCT_PRINCIPLES.md) | AI 输出不是业务事实；发现、解释、规划都必须受数据契约与 Evidence 约束 |
| [PD-001：ACCEPTED](../product/decisions/PD-001-asset-governance-hub.md) | 资产理解从 Asset 治理上下文进入，使用相同身份和专业回链 |
| [PD-002：ACCEPTED](../product/decisions/PD-002-governed-consumption-contract.md) | Dataset / Data Service 保持 owning Truth；Access、Subscription、Usage、Lineage 分开 |
| [PD-003：ACCEPTED](../product/decisions/PD-003-business-semantic-metric-contract.md) | 指标按精确发布版本解释；不能把 Metric 直接变成新的可查询 Data Product |
| [F-001-A：APPROVED](../product/features/F-001-A-asset-section-contract.md) | 质量分区首期仅物理表；生命周期仅 Model；不适用不能误说“没有风险” |
| [F-004：APPROVED](../product/features/F-004-governed-data-consumption.md) | 治理消费必须进入真实动作与证据；Agent 扩展不自动属于该 Feature 的已批准交付 |
| [F-005：APPROVED](../product/features/F-005-business-semantic-metric-productization.md) | 复用已发布指标定义和消费 handoff，不重造 Semantic Query Runtime |
| [Agent Domain](../../data-ops-business/data-ops-business-agent/DOMAIN.md) | 当前取数只允许 Dataset 结构化查询；治理读取、命令、Data Service 调用都属于待设计的扩展 |

[PD-005 质量发布门禁](../product/decisions/PD-005-quality-publication-gate.md)、[PD-006 消费安全映射](../product/decisions/PD-006-consumption-security-object-mapping.md)、[PD-007 生命周期范围](../product/decisions/PD-007-governed-source-lifecycle-scope.md) 仍是 PROPOSED。可以列为依赖和讨论输入，不能将候选规则写成已经生效的 AI 自动治理行为。PD-004 和 F-006 也未授权新的集成产品模型；AI 不借本轮规划扩大该范围。

## 3. 能力地图与优先级

优先级是本次建议；每项进入实施前仍需 Feature 与目标领域契约。P0 是首期，P1 是下一期，P2 依赖更完整闭环，P3 只保留研究方向。

| 能力 | 具体用户任务与输出 | 入口 / 下一步 | 首期边界与依赖 | 优先级 |
|---|---|---|---|---|
| 资产理解与治理问答 | “这是什么、谁负责、是否可信、来自哪、影响谁？”；事实、证据、未知项、处理入口 | Asset 详情中的提问/解释 → 原专业页 | 读已有适用 Sections；不推断 Quality/安全覆盖完整 | P0 |
| 质量异常解释 | 将 Execution / RuleExecution、规则版本、上游运行事实整理成问题摘要和排查顺序 | 质量执行工作区 → 对应规则/上游实例 | 先解释实际检查不通过与技术错误；根因假设单列 | P0，紧随资产 MVP |
| 受治理问数强化 | “上周订单金额为何下降？”；先澄清口径，再查询并解释结果 | 现有 Agent、消费上下文 → Dataset/证据/报告 | 保留当前工具；先补主体与权限、版本衔接，不新增自由 SQL | P0，现有能力修正 |
| 描述与标准映射建议 | 表/列描述草稿、标准字段候选、命名/类型/单位差异 | Metadata、Semantic、Model 专业编辑面 → 人工修改与保存 | 引用已存在标准 ID；不得自动新建口径或覆盖手工描述 | P1 |
| 质量规则建议 | 根据字段语义、已有标准与历史结果，生成可编辑的非空/唯一/范围/码值规则草稿 | 质量 Monitor 编辑面 → 既有校验、试跑、保存 | 优先既有模板；阈值依据不够就待确认；不自动启用调度 | P1 |
| 敏感分类候选 | 根据列名、注释、既有等级/分类字典提示待确认对象 | 安全发现与分类面 → 现有人工确认 | 默认不读行数据；自动结果只作候选；不覆盖 ACTIVE/REJECTED | P1 |
| 指标口径解释与冲突提示 | 比较发布版本与 Draft、依赖版本；解释单位、粒度与过滤差异 | Metric 详情 → 版本/Validation/Impact | 无映射只解释定义，不伪造指标查询值 | P1 |
| 变更影响解释 | 汇总血缘、订阅、真实使用的已知影响，生成变更检查清单 | Asset/Metric/发布前现有影响面 → 逐项核查 | 不宣称全量影响；不由模型创建技术血缘边 | P1 |
| 开发与运行辅助 | 任务配置建议、报错解释、映射草稿；后续可提 SQL 草稿 | 数据开发工作台、实例页 → 原编辑/校验/发布 | SQL 草稿属于 Development 待批准能力，不能经 Agent 取数工具执行 | P2 |
| 主数据治理辅助 | 疑似重复记录说明、字段冲突、合并候选与保留值依据 | MDM 当前处理路径 → 既有审批 | 涉及行数据和主记录更改，需独立 Feature；不自动覆盖受保护字段 | P2 |
| 生命周期优化建议 | 根据现有 Model TTL 与已知使用证据提示检查项 | 生命周期专业面 → 人工预演与原确认流程 | 无使用证据不能认定闲置；不扩展 Dataset/Table TTL 或自动删除 | P2 |
| 主动治理巡检与有限自动处置 | 在既有调度上产生可追踪的检查建议；逐步允许低风险动作 | 现有待办/告警/审批入口 | 明确执行身份、规则、预算、重试、幂等与撤销；需长期治理决策 | P3 |

不建议按“元数据 Agent、质量 Agent、安全 Agent…”先创建多个独立产品。能力先以窄工具与场景策略承载；只有实测证明并行分工更有效，才在运行时引入多 Agent。

## 4. 建议的产品交互

用户在现有资产页点击“解释这个资产”，默认绑定服务端确认的资产 ID、Project 和允许读取的分区。回答卡片按以下内容组织：

1. 已知事实：对象身份、来源、治理联系人及来源域，适用的质量/安全事实。
2. 证据：来源对象/版本、检查或查询时间、可访问的详情链接。
3. 限制：不适用、服务不可用、证据过期、尚未检查、权限限制分别说明。
4. 建议：按证据列下一步，例如进入某个质量执行详情；不把建议伪装成已经执行。

用户从质量异常进入时，同一辅助组件携带 execution identity，保留回到原执行的路径。现有 Agent 允许跨问题继续对话；默认仍保留 Asset 治理入口和 Consumption 消费入口的职责。

第二期的建议卡片额外展示“现值 → 候选值”、依据、影响对象与可编辑参数。用户点击“带入编辑”先进入原领域表单；用户提交时重新校验权限、版本和领域规则。需要审批的动作走现有审批机制，澄清问题的 HITL 不代替审批。

这是一套交互建议，不新增通用跨域 Suggestion 实体或审批状态机。若确实需要持久候选，先确定现有领域候选模型是否适用，否则走治理流程明确 Owner。

## 5. 四条端到端路径

| 路径 | 用户步骤 | 可验收结果 | 失败/阻断表现 |
|---|---|---|---|
| A：资产理解，首期 | Asset → 解释 → 来源事实与证据 → Quality/Lineage/Metadata → 返回 Asset | 回答不混用模型与来源表，不猜负责人，不把未知当安全 | 某分区故障仅说明该事实不可取，其它回答仍可用 |
| B：质量定位，首期后半 | Execution → 解释 → RuleExecution → 上游来源与已知影响 → 人工处理 | 区分 NOT_PASSED/ERROR/NOT_RUN；根因假设有支持和缺失证据 | 上游日志未接入时给排查顺序，不声称根因已确认 |
| C：规则辅助，第二期 | 物理目标 → 规则建议 → 参数编辑 → Quality 校验/试跑 → 保存 → 实际 Execution | 建议对应真实字段与模板；最终结果由 Quality 写入 | 试跑失败不自动保存/启用；未授权用户不能提交 |
| D：问数，保留并强化 | 问题 → 口径澄清 → 授权 Dataset 发现 → 精确版本结构化查询 → 结果证据 → 报告 | 有 queryId、实际版本、时间范围、行数/截断说明，数字可复核 | 下线、拒绝、服务故障、无记录含义不同；不换数据绕过 |

模型报告中的结论仍是分析产物，引用的业务事实来自 owning domain。数字必须对应查询结果，根因与预测必须标成推断；“缺少证据”是有效结果。

## 6. 事实归属与复用

| AI 用到的内容 | Truth Owner | 读取/生效边界 | 不能做什么 |
|---|---|---|---|
| 资产身份、治理联系人、目录/业务标签、上架状态 | Asset | 既有 Asset/Section 读取与原治理动作 | 把治理联系人当查询权限或源域 owner |
| 物理结构、采集时间、元数据标签 | Metadata；目录投影保留源域归属 | 既有 Query API/Section；候选流程按领域确认 | 模型推断直接覆盖物理采集事实 |
| 标准、业务域、过程 | Semantic | 公共 Query SPI 与专业编辑入口 | 自造第二套指标口径/字段标准库 |
| 模型与指标版本、验证、发布 | Modeling / Metric | 已发布定义与精确版本；原编辑/验证/发布 | Draft 编辑静默移动发布版本；Metric 冒充 query runtime |
| 规则与执行结果 | Quality | 既有模板、Monitor 与 Execution read-side/command | 把 AI 评分写成 PASSED 或默认启用通用发布门禁 |
| 等级、分类、访问策略、脱敏、授权裁决 | Security 及对应执行入口 | 现有等级字典、候选确认、策略审批与执行裁决 | 模型 confidence 替代敏感度 rank 或批准权限 |
| 技术血缘 | Lineage | 有界 graph query | 推断关系直接入正式图；Usage 冒充血缘 |
| 消费关系与真实使用 | Consumption；原始事实仍由执行来源提供 | 订阅和规范化 Usage Evidence | 从问答/浏览/权限许可生成成功消费证据 |
| 会话、轮次、报告、Agent 查询 trace | Agent，按现有各自存储边界 | 原 Agent 入口与留痕 | SSE 或新向量索引成为业务事实来源 |

## 7. 为什么这样排序

资产解释可以大量复用已经明确的治理读取契约，先验证 AI 是否能正确理解事实而不需要写操作。质量解释紧贴高频治理问题，容易用真实 Execution 核验价值。规则、语义、分类建议有明确人工采纳点，适合第二期形成效率收益。

问数已经有实现基础，应强化契约而不是重新立项。自动修复、主数据合并、跨数据源自由查询和生命周期删除的风险与依赖更大；先证明建议有效、权限与动作可控，再讨论自动执行。

行业资料支持“上下文 + 评测 + 人工审核”先行的方向：DataHub 官方指南把语义上下文、评测和审核放在数据 Agent 建设主线，并建议从少数业务域启动；其中部分能力属于 Cloud Context 公测，不能等价为 OSS 已可用，也不证明适用于本仓库。这里只借鉴产品方法。[官方指南](https://docs.datahub.com/docs/managed-datahub/build-a-data-agent/overview)（访问：2026-10-05）。

OpenMetadata 官方 AI SDK 文档列出了搜索、详情和血缘等 MCP 工具，展示了向 AI 暴露治理上下文的方式。DataOps 可沿现有 API 构建窄工具，无需为了采用该方式替换治理平台或首期引入 MCP。[官方文档](https://docs.open-metadata.org/v1.12.x/api-reference/sdk/ai-sdk)（访问：2026-10-05；版本化页面的检索内容可读，直接打开失败，故不以此承诺部署可用性）。

## 8. 成功指标与未决问题

验收证明“行为符合契约”，成功指标证明“工作更容易完成”，两者分别记录。首期采集资产理解与异常排查的人工耗时基线；试点按相同任务记录 AI 耗时、回答正确性、证据可定位比例、专业页继续处理比例和用户修改原因。第二期记录建议采纳率、编辑幅度、试跑通过率及采纳后撤回率。目标值在首轮基线后由 Owner 确认；本方案不宣称已有百分比收益。

需要在立项会上确定：首个业务域与数据 Owner、最频繁的十个问题、现有模型供应与数据外发边界、试点人数与研发投入，以及现有 Agent 功能哪些已经真实验收。默认假设复用现有 Java21/Spring Boot 服务和模型网关，先做单域内部试点；如果要求全内网，应先验证本地模型的工具调用与结构化输出，再确定同一套验收门槛。
