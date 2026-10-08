# V23 问数澄清验证记录

日期：2026-10-08。合同：[F-033](../../../product/features/F-033-agent-query-clarification.md) IMPLEMENTING；基线 main `11c7ab2f225cc7796651a4f22dae4fe75f759d9c`。实现提交 `743a338f73010dc2b6e14958669826a9467bdb7e`。V22 #360 尚未合并时从 main 独立开发，不依赖其新增指标场景。

| 检查 | 实际证据 | 结果 |
| --- | --- | --- |
| 字段来源与边界 | QueryClarificationRuntimeTest：FIELD/TIME/CALIBER 使用当前执行已发现版本与字段；缺失/伪造/重复/超限引用、治理越界、字段描述及总载荷超限拒绝 | PASS |
| SDK 挂起与恢复 | 同一原 toolCallId 挂起/应答；同批查询被拒绝，同批多个反问不挂起；恢复后的查询先因缺少新发现被拒绝，重新发现 v2 后只查询一次。实际 SDK、模拟模型及工具，不是真实模型或源业务 E2E | PASS |
| 后端回归 | AgentTaskExecutionTest、RequestClarificationToolTest、AgentResumeIntegrationTest、QualityTroubleshootingTest、AgentArchitectureTest、AgentDependencyBoundaryTest、AgentChatServiceSubmitTest、AgentSessionQueryServiceTest、AgentSessionOwnerValidatorTest、DatasetQueryGatewayTest、AgentEventCodecTest、ChatTurnToAguiMapperTest、AgentTurnExecutorTest 及新 runtime 测试，共 115 项，0 失败/错误/跳过；上游 31 reactor 编译与测试编译通过 | PASS |
| 实时/恢复与交互 | 共用严格 parser；源选项匹配、损坏投影、文本渲染、显式回答、权限/活动锁、双击、切会话/项目/待答清理、未知 live 工具身份拒绝 | PASS |
| 前端相关回归 | `src/services/agent src/components/ai src/pages/ai-agent`：29 suites / 357 tests；最后补充 live 工具身份守卫后，该页面重新验证 1 suite / 59 tests。两组有重叠，不相加、不声称全仓回归 | PASS |
| 类型检查 | `npm run check:types`，139 项既有诊断基线，无新增诊断 | PASS |
| 生产构建与产物 | `npm run build`；随后 `node scripts/release/frontend-artifact.mjs` 校验实现提交、源摘要与产物摘要。后续提交仅添加本文档，构建产物不入库 | PASS |
| 架构/发行/CI 脚本测试 | `node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs scripts/ci/*.test.mjs`，40 项 | PASS |
| 静态合同 | 架构 77 reactor / 3196 生产 Java 文件；前端保留 1 个声明的既有 corridor；62 迁移基线；产品基线 24 必需文件/19 术语/7 Decision/34 Feature；产品守卫解析自测及 diff whitespace 检查 | PASS |
| PR 产品守卫 | 使用实际 PR 描述运行 Product Impact（12 个产品源路径）及 Product Surface 检查；现有模块与一级导航范围未扩张 | PASS |

本地后端选定测试命令：

```powershell
mvn -B -ntp -pl data-ops-business/data-ops-business-agent -am test '-Dtest=QueryClarificationRuntimeTest,AgentTaskExecutionTest,RequestClarificationToolTest,AgentResumeIntegrationTest,QualityTroubleshootingTest,AgentArchitectureTest,AgentDependencyBoundaryTest,AgentChatServiceSubmitTest,AgentSessionQueryServiceTest,AgentSessionOwnerValidatorTest,DatasetQueryGatewayTest,AgentEventCodecTest,ChatTurnToAguiMapperTest,AgentTurnExecutorTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Djdk.net.unixdomain.tmpdir=D:\tianxy\code\data-ops\.task-ai-evaluation\v23\tcp-only'
```

末尾 JVM 参数仅绕过本机 Windows JDK 的 Unix-domain 临时路径问题，使用不存在的临时目录让 Surefire 回退 TCP；不修改生产配置。工程日志位于本地忽略目录 `.task-ai-evaluation/v23/`，不包含真实凭据或模型验收数据。

## 待统一真实验收

按用户既有安排，真实模型、实际登录权限与撤权、源查询审计、完整 J2、专家语义评分及耗时/成本收益全部 PENDING。手工试点题目见[清单](../../evaluation/query-clarification.md)，未注册为自动评测套件，也不计入历史 48 题评测。服务端检查字段身份与载荷边界，不能证明模型识别了全部歧义或自然语言回答正确。

本地 PASS 不等同于远端 CI 或完整业务验收；CI 状态以本 PR 实际检查为准，不据此标记 SHIPPED。
