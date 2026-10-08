# V18 工程验收证据

2026-10-08，分支 codex/ai-batch-standard-assistance。当前合同 F-026/F-027/F-028/F-029；用户明确包含标准→模型→指标→消费完整原页面交接。此记录为 Evidence，不替代活动合同。

## 工程结果

- `mvn -pl data-ops-business/data-ops-business-agent -am test '-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tianxy/code/data-ops' -q`：exit 0；392 个测试集、1849 项、0 failure/error、2 项真实 MySQL 用例跳过（AgentStateStoreUpgradeMysqlTest / AgentToolBudgetMysqlTest），未记为通过。初次未设置 Windows 短 socket 目录时 29 项 SDK HTTP 用例受本机 JDK UnixDomainSockets.connect 失败影响；按仓库已有环境记录重跑后通过。
- 最后新增的“纯数字指标编码不能当成常量交接”源域检查，再运行 MetricDraftQueryAdapterTest / MetricDraftGatewayTest / StandardMatchRuntimeTest / ModelSuggestionQueryAdapterTest / MetricExplanationQueryAdapterTest：33 项通过。其中 SDK 16 项包含四场景的原生/合成结构化输出、预算、未知工具拒绝与原历史交付。
- 前端原页面与 Agent 回归：18 suites / 115 tests 全通过；收口的准备请求作用域/原表单 2 suites / 9 tests 通过；canonical 指标回链协议 8 tests 通过。
- `npm run check:types`：通过，139 项既有诊断无新增。`npm run build` 及发行 manifest：通过。
- 前后端架构边界、Product baseline、暂存 diff whitespace：通过。Product PR metadata/surface 根据实际提交及 PR body 单独校验；GitHub CI 以创建 PR 后的准确 head 为准。
- register.json.content 与对应 SKILL.md 内容一致；未在真实项目自动导入/启用 Skill。

前端第一次批量测试的重复模拟 UUID 已修正。并发构建时一个既有质量规则用例曾超时，独立 17 项和后续完整 115 项均通过；无放宽业务判断。

## 实现范围与限制

最多五个字段，独立原轮、逐项采纳及可选说明。历史解释为精确快照只读；当前版说明仍原保存 CAS。定义草稿只用明确来源字段/上游、既有限定条件编译与有界 token，保存/验证/发布均需原页面人工动作。复合上游纯数字编码在原公式编辑器中与常量歧义，明确拒绝而不错误带入。消费出口仅为与 active publication 精确版本一致的已登记 DATASET 引用；未知版本和 API 未映射如实降级。声明引用不能证明已运行。

真实模型/登录完整 J2、保存与发布审计、消费查询运行及用户收益仍 PENDING，步骤和证据清单见 [真实验收记录](../../../product/acceptance/agent-batch-j2-2026-10-08.md)。本地 fake source/HTTP provider 不能代替真实 E2E；Feature 保持 IMPLEMENTING。

#334/#335 已合并至中间分支，main 只含 #333；本 PR 面向 main 并携带所需 V16/V17 提交，不把中间分支合并当作主线交付。
