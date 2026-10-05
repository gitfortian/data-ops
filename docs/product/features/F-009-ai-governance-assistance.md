# F-009 AI 治理解读首版

Status: IMPLEMENTING
- Approval: 用户于 2026-10-05 授权按 docs/ai 规划完成第一版能力升级。
- Product basis: ACCEPTED PD-001 / PD-002；APPROVED F-001-A；不采用 PROPOSED PD-005/006/007。

## User / Problem / Capability

数据负责人和治理运营人员需要从现有资产分区、质量执行结果中理解对象与问题；目前要逐页阅读并手工汇总。首版提供授权范围内的只读 AI 解读，引用实际证据并回到源页面核验。

## User Journey / Entry

资产详情“AI 解读资产”、质量执行详情“AI 解读结果”进入现有 AI 对话，显示目标与快捷问题；用户明确发送后开始推理。普通对话可以搜索资产、读取分区和质量执行。切换历史会话清除入口上下文，HITL 恢复继承已持久化目标。

## Expected Outcome / Truth Owner / Producer / Consumer

- Asset 台账拥有资产身份、描述与负责人；Metadata、Quality、Security、Lineage、Lifecycle、Consumption 保持各自证据真相。
- 源域只读 API 是 producer；Agent 是 consumer，不写治理状态、规则、分级、审批或业务数据。
- 回答明确区分事实、推测与建议；无证据、拒绝访问和分区不可用时不能宣称“健康”“无风险”。
- Quality 保持 PASSED / NOT_PASSED / ERROR / RUNNING / NOT_RUN，解释历史执行不得回读当前规则覆盖旧结果。

## Existing capabilities to reuse

复用 AssetDiscoverService 分区五态与适用矩阵、QualityExecutionReader、DatasetQueryService 安全与脱敏、现有会话归属、Project scope、AgentScope 2.0.2 StateStore / SSE / HITL / 取消。

## Security / Evidence contract

- 工具只从服务器 RuntimeContext 取持久化用户和项目；每次工具调用重新确认账号启用、项目成员关系与 action permission。模型不能指定执行身份。
- 证据含源域、对象引用、读取时间、源更新时间（未知明确为 unknown）、状态和内部回链。引用 ID 属于本轮，未知引用拒绝发布为可信答案。
- 治理解读正文完成校验后发布；工具进度仍可流式展示。链接由服务端证据登记生成，模型输出不直接获得任意跳转链接。
- 元数据/源域文本是数据，不是指令；不向模型提供执行 SQL、连接凭据、原始异常或业务样本。
- Dataset 发现字段记录实际版本；取数传入认证用户主体并指定该版本，版本变化需重新发现。成功与拒绝仍落查询审计。

## Scope / Non-goals

现有导航内添加入口和只读工具；不新增 Maven module、业务状态机或第二份业务真相。不迁移 Agent 框架，不引入 Python 执行，不做自动修复、规则草稿、全库扫描或知识库平台。

## E2E acceptance evidence

1. 资产详情进入对话，读取同项目资产并解释含状态与回链；Model 的 Quality / Metadata 保持 NOT_APPLICABLE。
2. 分区权限拒绝不调用 provider；部分失败仍保留其他可读证据。
3. 质量解读引用实际 executionNo、规则结果、metric / expectedValue，ERROR 与 NOT_RUN 分开解释。
4. 后台线程有真实用户主体；撤销权限、禁用用户、跨项目对象被拒绝；作用域退出清理上下文。
5. Dataset 无字段发现/版本漂移被拒绝；执行请求带用户主体和冻结 versionNo；SQL 不进入工具回复。
6. 编造证据 ID / 任意链接不能进入可信结果；上下文不在会话之间串用；旧输入 JSON 兼容。

验证记录写入 docs/ai/acceptance。Mock/契约测试和真实环境 E2E 分别记录，缺少环境不得标为 SHIPPED。

## Metrics / Rollout

沿用工具 trace 与查询审计，记录本轮证据数量/状态、耗时、失败与引用拒绝。发布前用固定资产/质量题集核验引用和拒绝行为；模型质量、成本及延迟阈值须由真实部署样本确定，首版不虚构达成率。
