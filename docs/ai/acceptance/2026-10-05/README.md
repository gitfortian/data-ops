# AI 首版验证记录

日期：2026-10-05。范围：[F-009](../../../product/features/F-009-ai-governance-assistance.md)，产品状态 IMPLEMENTING。验证对象是当前未提交工作区的首版代码，不是已部署的生产版本。

## 1. 结果与证据类型

| 检查 | 结果 | 证明范围 |
|---|---|---|
| Maven Boot 及依赖 reactor 定向测试 | 334 项通过，0 失败、0 错误、0 跳过；BUILD SUCCESS | 编译含 Boot 在内的 69 个 reactor 项目；运行与变更相关的 Java 行为和架构测试，不代表全仓全部测试 |
| 前端定向 Jest | 4 个套件、37 项通过 | 入口目标解析、快捷问题不自动发送、返回来源、新会话清理、既有 SSE / trace 回归 |
| TypeScript debt gate | 通过，139 项既有诊断，无新增 | 遵守仓库现有诊断基线；不是全仓 tsc 零错误 |
| 产品治理基线 | 通过 | 24 个必需文档、19 个词汇、7 个 Decision、10 个 Feature Spec |
| Product surface 检查 | SKIP | 无 GitHub event，检查仅在 PR 环境执行；不计为通过 |
| `git diff --check` | 通过 | 已追踪改动没有空白错误 |
| 浏览器入口访问 | 登录跳转已观察到 | `/ai-agent?assetId=7` 跳转 `/login?returnTo=%2Fai-agent%3FassetId%3D7`；保留入口目标，但没有测试账号登录态，未验证登录后的页面及真实模型调用 |

源测试、命令和汇总数据是可复查证据。自动化源接口替身与固定模型响应明确为测试数据；没有伪造真实业务资产、质量执行、SQL 结果或页面截图。

逐模块计数见 [verification-summary.json](./verification-summary.json)；Java 验证最终于 2026-10-05 10:14:11（Asia/Shanghai）完成。

## 2. 关键验证

- `TrustedUserScopeTest`：工作线程绑定真实身份，错误退出清理，禁用/删除账号与撤销项目拒绝，嵌套作用域恢复调用者。
- `GovernanceToolInvocationTest`：真实 AgentScope Toolkit / ReActAgent 生命周期；入口 middleware 预读与模型实际工具调用均经身份守卫；源 API 看到预期用户和项目，初始证据有观测记录，最终回答含服务器回链。Model、UserDao、授权快照、源 API 为测试替身。
- `GovernanceAnswerGuardTest`：最终事件与官方 StateStore 历史一致；编造引用不能保存为有效解读；普通追问不能沿用旧轮引用。
- `GovernanceEvidenceGatewayTest`：五态/适用矩阵信息和源更新时间保留，源域原始异常不进入模型，非允许字段不透传。
- `AssetDiscoverServiceTest` / `AssetControllerSectionTest`：分区权限、provider 调用、Model 与物理表适用矩阵、共享投影回归。
- `QualityEvidenceQueryAdapterTest`：授权先于读取，只消费历史执行与规则结果，ERROR / NOT_RUN 分开，排除执行 SQL 与原始异常。
- `FieldWhitelistSnapshotTest` / `DatasetQueryGatewayTest`：本轮发现、版本漂移拒绝、真实主体及角色 code、冻结 versionNo、SQL 不透出、异常脱敏、拒绝路径 query audit。
- `DatasetQueryCoordinatorTest`：既有源域主体和安全查询编排回归。
- `AgentChatServiceSubmitTest` / `AgentTurnExecutorTest` / `AgentResumeIntegrationTest`：目标持久化及恢复、归属/单飞/终态回归；本地 fake OpenAI HTTP 服务验证工具挂起、澄清、恢复和 SSE 结果映射。
- `ToolContractGuardTest` 及 Agent / Quality 架构守卫：工具名字、参数 schema、依赖走廊未绕过源域公开 API。

## 3. 可重跑命令

在仓库根目录运行（Java 21、Maven，PowerShell）：

```powershell
mvn -pl data-ops-boot -am `
  '-Dtest=TrustedUserScopeTest,Agent*Test,Governance*Test,FieldWhitelist*Test,DatasetQueryGatewayTest,DatasetQueryCoordinatorTest,ToolContractGuardTest,Quality*Test,Asset*Test' `
  '-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tianxy/code/data-ops' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test

node scripts/product/check-product-baseline.mjs
node scripts/product/check-product-surface.mjs
git diff --check
```

本机 Windows JDK 的默认 socket 临时目录使 `HttpServer` 创建失败；指定本地已有短路径后，网络集成测试正常运行。该参数仅用于测试命令，没有跳过测试、关闭证书检查或修改运行配置。其他机器应按实际路径设置，非受影响环境无需这个参数。

在 `data-ops-ui` 运行：

```powershell
node node_modules/jest/bin/jest.js `
  src/services/agent/governance.test.ts `
  src/pages/ai-agent/index.test.tsx `
  src/pages/ai-agent/stream-runtime.test.ts `
  src/pages/ai-agent/trace-runtime.test.ts --runInBand

node scripts/check-type-baseline.mjs
```

## 4. 尚未完成的产品验收

需要实际测试项目、可读与受限账号、物理表/Model 资产、已发生质量执行及配置好的模型端点，执行 [SCENARIOS](./SCENARIOS.md) 中的题集。运行中的用户服务尚未重启，本次也没有发布。

优先验收实际权限撤销、跨项目拒绝、Security 脱敏、质量历史状态、证据语义与页面回链；随后采集模型效果、费用及延迟。通过后补真实 trace / 截图并按产品治理收口 Feature。现有测试不替代这些真实 E2E，不把本记录升级为 SHIPPED 证据。
