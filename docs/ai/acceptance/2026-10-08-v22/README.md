# V22 指标版本变更核对验证

日期：2026-10-08。开发基线 main `0088fb8c`，前版 #354 已由用户合并。生产实现提交 `e9255308`，当前合同 [F-032](../../../product/features/F-032-metric-change-review.md)，状态 IMPLEMENTING。本记录仅为 Evidence。

## 已执行工程验证

- 前端 `node node_modules/jest/bin/jest.js src/services/agent src/components/ai src/pages/ai-agent --runInBand --silent`：29 套件、340 项全部通过。覆盖显式生成、无基准/无差异、版本/证据漂移撤下、重新准备、活动输入锁定、权限/项目切换及迟到回调、失败重试保留重点、只读历史与原场景兼容。此前两轮并行负载下各有一个既有异步测试超过其默认 1 秒等待；相关 5 套件 100 项复核与最终完整 340 项均通过，没有放宽超时。
- `node scripts/check-type-baseline.mjs`：通过，139 项既有诊断，无新增。
- `npm run build`：生产构建通过，生成构建清单；`node scripts/release/frontend-artifact.mjs` 校验 revision、source digest、asset digest 通过，对应实现提交 `e9255308`。后续验证记录提交只修改文档，不重新声明构建提交身份。
- Maven 31 模块 reactor test-compile 通过。最终 Agent 及其依赖选定回归共 94 项全部通过：Metric 源查询 12、原单版本解释 6、Agent 变更核对 gateway 5、SDK runtime 20、两组架构边界 6、提交 8、会话 owner 5、会话查询 32。
- `node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs scripts/ci/*.test.mjs`：40 项全部通过。
- 前后端架构边界通过：77 reactor entries、3204 production Java files；前端保留既有 1 条声明走廊。迁移历史 62 个固定基线脚本通过。Product Guard 自测与产品治理基线通过（24 必需文件、19 术语、7 决策、34 Feature）。以拟提交的实际 PR 正文执行产品影响检查通过（30 个产品源文件）；产品表面检查通过，没有新业务 Maven 模块或一级导航。

后端最终命令（仓库根目录，Java 21）：

```powershell
mvn -B -ntp -pl data-ops-business/data-ops-business-agent -am test '-Dtest=MetricChangeReviewQueryAdapterTest,MetricChangeReviewGatewayTest,MetricExplanationQueryAdapterTest,StandardMatchRuntimeTest,AgentArchitectureTest,AgentDependencyBoundaryTest,AgentChatServiceSubmitTest,AgentSessionQueryServiceTest,AgentSessionOwnerValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Djdk.net.unixdomain.tmpdir=D:\tianxy\code\data-ops\.task-ai-evaluation\v22\tcp-only'
```

本机 Windows JDK 的默认 ProcessHandle 通道初始化报 Unix domain loopback `Invalid argument: connect`，导致 SDK runtime 初次无法启动。经本地 JDK 实现与最小通道探针确认，以上仅测试 JVM 参数把 Unix domain 临时路径指向不存在目录，使 JDK 退回 TCP 通道；没有修改产品代码或跳过 SDK 测试。最终 94 项包含全部 20 项 runtime 测试。CI Linux 不需要此本地参数。

## 事实与交付证据

源查询测试证明 Metric READ 先于读取、发布 pointer/ledger 与不可变版本一致、类型差异稳定、读取时间不改变指纹、最新失败验证不会被旧通过替换、声明引用仅有界读取 21 条/展示 20 条、未知引用版本显式标记、异常及未授权证据不暴露原错误。证据身份变化和交付前版本/发布漂移拒绝，超限拒绝而不截断公式。

Gateway/runtime 测试证明工具仅固定本轮目标、生成内容只能引用本轮源事实、重复/未知/空事实键和超长内容拒绝、合成与原生结构化交付均可持久化、预算及工具授权不能被绕过、重复证据导致交付超过 60000 时拒绝。前端解析器同时限制目标、证据、覆盖和回执范围。

## 首版覆盖与真实验收边界

已接入白名单版本差异、最新精确验证和有界声明引用。完整 readiness、当前依赖健康及血缘缺少本场景安全有界投影，明确 UNAVAILABLE；实际消费 provider 未注册为 NOT_APPLICABLE，已注册但未接入为 UNAVAILABLE。原治理面板/影响页面继续承担完整核对，生成完成不决定发布资格。

用户已明确真实模型与测试环境验收后续统一做。本批未运行真实模型、真实登录态、源保存/验证/发布、完整 J2、专家语义评分或人工收益评估，这些仍 PENDING。上述工程证据不证明自然语言正确、业务发布成功或真实收益；Feature 不标 SHIPPED。固定 Skill 包需管理员在原 Skill 管理登记后使用，部署不会自动注册。
