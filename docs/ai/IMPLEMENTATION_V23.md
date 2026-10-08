# V23 问数字段、时间与口径澄清

2026-10-08，用户授权按后续规划继续实施。合同 [F-033](../product/features/F-033-agent-query-clarification.md) 为 IMPLEMENTING。基线 main `11c7ab2f`；V22 #360 尚未合并时独立开发，本版不依赖其新指标场景。真实模型与测试环境验收按既有约定延期。

提交 PR #365 时 main 已前进到 `730d2a6d`，包含 V22 #360 与已合并的架构/消费修复；已合并该基线。原指标变更场景的工具策略、恢复目标及合同完整保留，V23 仍只增强普通问数反问。

User：Dataset 分析人员。Problem：同名字段、多时间字段、金额/数量及去重规则存在歧义，泛化反问缺少核对依据。Capability：原问数流程的字段/时间/口径澄清。Journey：提出问题 → 授权字段发现 → 同轮反问 → 人工回答 → 重新发现/核对 → 原结构化查询与证据。Expected Outcome：明确缺项及回答范围，减少未经确认的业务假设；实际收益待试点。

Truth Owner：Dataset 持字段/版本/查询，Security 持权限，原 turn/SDK StateStore 持生命周期/pending/应答。原字段发现与受控反问投影是 Producer，原 Agent 页面与恢复路径是 Consumer；复用预算、项目/归属、白名单、精确停止、原查询审计，无第二份事实。

## 使用与行为

普通问数先读取可读 Dataset 与字段；关键歧义通过原 request_clarification 提出 FIELD/TIME/CALIBER。面板显示数据集 ID、发现版本及1–8个相关真实字段；FIELD 的选项由服务端装配，TIME/CALIBER 选项明确是 AI 提问建议。用户选择或填写≤2000字答案，明确点击后续跑原轮；读取/活动/权限状态不允许时禁用，切项目/会话/待答清理未发送答案。

查询仍由原 run_dataset_query → DatasetQueryGateway 完成。恢复后的新执行上下文没有旧字段发现，需重新 get_dataset_fields；原授权主体、字段白名单与版本核对仍负责实际访问。不支持把 Metric 公式临时翻成 SQL，也不新增多 Dataset 联查。

## 边界验证

- runtime 的可选参数必须引用本执行已发现的字段；不接受模型传入字段名称/描述作为事实。非法、重复、缺失及超限返回固定错误，不挂起、不查询。
- SDK ActingInput 保留同调用 ID/原入参，携带受控问题投影；实际工具在原预算预占之后再次校验。事件及 continuation 延用原字符串协议；没有新表或第二 pending 状态。
- 同批有反问时不执行查询，同批多个反问不挂起。原普通纯文本/question-options 与治理反问继续支持，治理任务不能借问数参数扩大范围。
- 实时与恢复共用严格 parser，坏来源不能显示为可信字段或可回答问题；纯文本渲染不执行 HTML。输入及源字段有界，超限拒绝，不截断后解释。
- 提示明确时间字段、范围/边界/时区/粒度、聚合/去重/单位/退款/状态等缺项，已明确内容不反复问。服务端验证身份和协议，不保证模型发现全部歧义或自然语言正确。

Domain Impact：原 Session/Turn/PendingClarify/DatasetFields，无生命周期变化。Architecture/Dependency Impact：runtime 内部消费 domain 快照、原 toolset 扩展参数；前端原页与共用 parser/card。Dataset 不改动，无跨域反向边、导航、权限或数据库迁移。

工程结果见 [V23 验证记录](acceptance/2026-10-08-v23/README.md)，真实题目准备见 [问数澄清试点清单](evaluation/query-clarification.md)。真实模型、登录态撤权、源查询审计、完整 J2、语义评分及收益仍 PENDING，Feature 不标 SHIPPED。
