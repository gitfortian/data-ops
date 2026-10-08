# V24 两次历史质量执行比较工程验证

日期：2026-10-08。合同：[F-034](../../../product/features/F-034-agent-quality-execution-comparison.md) IMPLEMENTING。实现提交 `0c7f4b35`，最终证据投影提交 `02bb92ea84a87576986fc78e671b05fca94076dc`。初始 main `568d6d64`，提交前快进到 `64109d18`（#372 / #374），无冲突；前端源未受主线更新影响。

| 检查 | 实际证据 | 结果 |
| --- | --- | --- |
| 源域比较 | QualityExecutionComparisonQueryAdapterTest：权限先于读取、终态/历史目标、身份缺失与错配、稳定规则 ID、阈值变化、截断、缺失侧、取消/失败语义、SQL/诊断排除及字段/载荷超限 | PASS |
| 数据库有界走廊 | QualityExecutionDaoProjectScopeTest：跨项目归属拒绝后不查询规则；摘要与规则显式字段投影（不选择 SQL/异常原文）、LIMIT 21、稳定记录排序和执行 ID 绑定；QualityExecutionReaderTest：只调用 bounded repository | PASS |
| 网关与目标 | QualityExecutionComparisonTargetTest / GatewayTest：旧 JSON、混合/损坏范围拒绝、严格工具范围、双侧失败状态与固定回链、容量不足不注册半对、各侧最后规则及40项对齐的最后字段均能核验 | PASS |
| 实际 SDK 协议 | QualityExecutionComparisonTest：选定对预读一次、服务器字段核验、最终回答与官方历史一致；原工具 HITL 挂起及同轮预算恢复，换基准拒绝。使用模拟模型/源，不证明自然语言正确性或真实业务 E2E | PASS |
| 完整后端回归 | Agent 全部60个测试类：331项，329通过、2项隔离 MySQL 按既有条件跳过；Quality 全部36个测试类：117项全通过。合计448项，0 failure/error；31 reactor 编译与测试编译通过，包括工具契约和反问签名守卫 | PASS |
| 前端相关回归 | `src/services/agent src/components/ai src/pages/ai-agent src/pages/data-quality`：41 suites / 429 tests；双身份、仅填入不推理、恢复、权限/活动/加载/路由/项目隔离、迟到与失败清理 | PASS |
| 类型 | `npm run check:types`：139项既有诊断，无新增 | PASS |
| 脚本与静态合同 | 架构/发行/CI 42项脚本测试；77 reactor / 3208生产 Java 文件；前端1个既有 corridor、62迁移基线、24必需文档/19术语/7 Decision/36 Feature；产品守卫解析及 whitespace 检查 | PASS |
| 生产构建与产物 | 在最终实现提交 `02bb92ea` 运行 `npm run build`，随后 `node scripts/release/frontend-artifact.mjs` 校验源码修订、工作区源摘要与产物摘要；均通过 | PASS |
| PR 产品守卫 | 使用本 PR 的实际描述运行 Product Impact（26个产品源路径）与 Product Surface 检查，无新增一级能力/模块/导航；whitespace 检查通过 | PASS |

从 Agent 与 Quality 测试目录枚举所有 `*Test.java` 类，运行：

```powershell
mvn -B -ntp -pl data-ops-business/data-ops-business-agent -am test '-Dtest=<全部Agent与Quality测试类名>' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Djdk.net.unixdomain.tmpdir=D:\tianxy\code\data-ops\.task-ai-evaluation\v24\tcp-only'
# 在 data-ops-ui 目录运行：
node node_modules/jest/bin/jest.js src/services/agent src/components/ai src/pages/ai-agent src/pages/data-quality --runInBand --silent --forceExit
```

末尾 JVM 参数仅为本机 Windows JDK/Surefire 回退 TCP；不存在的 tcp-only 目录不创建、不改生产配置。Jest 沿用既有测试的异步句柄，在报告全部完成后退出；不通过退出参数跳过断言。日志位于本地忽略目录 `.task-ai-evaluation/v24/`。

首轮发现并修复 Quality 枚举比较规范、数据库测试配置和前端缺失结束时间夹具；既有候选组件的异步断言首轮超时，单独复跑及最终全量相关目录均通过，没有改该组件或降低断言。最后修正网关最大对齐夹具为双方不重合 ID 后单独复跑；收紧 Quality 摘要与规则字段投影后重新运行全部 Quality 测试及31 reactor编译/测试编译。最终 Agent/Quality 生产与测试源码均有相应回归，不重复累计重跑项。

构建及产物校验完成后仅追加本文档记录，未再修改生产或测试源。本地产物未提交或发布，摘要对应当时工作区；用户已有本地配置和临时资料未纳入提交。

本批新增公共 Quality API，现有 impact-plan 规则会选择 FULL CI；未改 CI 范围或跳过守卫。远端 checks 以实际 PR head 为准，本地通过不等于 CI 完成。

真实模型、登录态撤权、源审计、完整 J2、专家语义与收益按用户安排统一验收，全部 PENDING；[8项人工清单](../../evaluation/quality-execution-comparison.md)未纳入自动题集，不以模拟 SDK 或工程 PASS 标记 SHIPPED。
