# V28 工程验收记录

日期：2026-10-09。基线 main 5b3a1716（V27 PR #441 已合并）。产品合同：[F-037](../../../product/features/F-037-agent-model-structure-review.md)，实施说明：[V28](../../IMPLEMENTATION_V28.md)。

## 结果

| 范围 | 结果 | 证据 |
| --- | --- | --- |
| Modeling | 212 tests / 47 suites，通过 | 完整模块回归；新增结构比较、授权/项目/版本/快照、指纹漂移、映射 SQL 限量与白名单测试 |
| Agent | 364 tests / 67 suites，362 通过、2 条件跳过 | 完整模块回归；新增实际 SDK 预读/核验/交付复核/官方历史、HITL 冻结、预算与目标隔离；本地 MySQL StateStore upgrade / tool budget 条件未满足 |
| 前端相关范围 | 474 tests / 41 suites，通过 | services/agent、components/ai、pages/ai-agent、pages/modeling、services/modeling；最终差异展示文案后 panel 14 项复验通过，不重复计数 |
| 类型债务门禁 | 通过 | 139 项既有诊断，无新增；未修改基线 |
| 架构 / CI / 发布脚本 | 48 tests，通过 | Node architecture/release/ci；Java 边界 77 reactor entries / 3226 production files，前端依赖边界、62 条迁移基线、39 份产品 Feature 基线、Product Guard 自测通过 |
| 前端构建及产物 | 通过 | 代码提交 bed75458da34893ba4697fb33fc8aa86e9370a8d 的 npm run build 和 frontend-artifact.mjs 版本/源摘要/产物校验通过 |
| PR 产品 / 范围门禁 | 通过 | Product Impact / E2E 声明有效，无新增业务模块或一级导航 |
| CI 路由 | full（预期） | published Java API changed: modeling/api/ModelStructureReviewQueryApi.java；未修改 CI 规则 |

后端共 576 项，574 通过、2 条件跳过。33 模块 reactor 内只选择 Agent / Modeling 测试；依赖模块编译不计为测试通过。初次发现遗留映射同时被列为变更目标，调整为遗留目标单独检查；测试中的 Mockito 重新设桩触发旧异常，改为顺序结果后复验。最终完整回归通过，无忽略失败。

本记录提交只增加 Markdown，产物 manifest 固定上述代码 SHA，CI 从最终 PR SHA 重建。源摘要包含用户原有的本地 Boot 配置，不能作为干净发行包证明；该配置及用户未跟踪资料未提交。

## MS01–MS07 工程链路

- 源域权限检查先于项目/结构/版本/映射读取。原模型锁先于精确版本和映射读取；映射 Wrapper 验证 project_id/model_id、id ASC、LIMIT 101 FOR UPDATE。指定版本缺失、结构损坏、错模型、跨项目映射及超限都拒绝，不回退为当前版本或空清单。
- 表/字段/主键/索引/分区白名单差异独立对齐；大小写名称变化、删除/新增、不推断重命名、默认值仅变化不作结构解释均有断言。当前映射的未映射、遗留目标、变更目标与表达式人工复核分别验证。原始表达式、源库表列、操作者和默认值不在输出。
- definition 同时冻结保存结构、基准行/原始快照和当前映射；即使只改未投影默认值或转换表达式，旧任务也要求重新准备。当前映射没有历史快照，源字段存在性/类型兼容和未比较项保留明确缺口。
- 新目标 JSON roundtrip 保留 Long 范围字符串身份。坏 ID、指纹、版本、purpose、混合目标、重复/混合 URL 和 continuation 拒绝；普通聊天/其他任务不能调用新工具，查询、搜索、报告或候选工具被拒绝。
- 实际 AgentScope SDK 完成预读、事实核验、交付复核与同一官方历史消息保存。源输入变化、交付前 CHAT_RUN 撤权或错项目使最终说明被固定提示替代，旧事实/证据卡片移除。原预读缓存及两次模型工具调用预算保持一致；交付复核为内部检查。真实 SDK HITL 保留固定 definition 和原预算，不能换目标或扩大预算。
- 原版本页显式准备后才展示 Agent 链接；进入 Agent 只准备问题、不自动发送。准备后换版本立即撤下旧链接；项目/模型/版本/权限变化、重新准备失败、错项目返回及迟到结果不恢复旧入口。精确版本缺失/非法/重复不静默切为当前版本；回链带 tab=version/reviewVersion，另可回原映射页人工检查。
- 原结构保存/发布/回滚、映射编辑及 Agent 原工具/技能/会话回归全部保留。没有新的业务写入、源目录或数据查询调用链。

## 复现与范围

本地详细日志位于忽略目录 `.task-ai-evaluation/v28/`。后端通过 Maven `-pl data-ops-business/data-ops-business-agent -am test`，`-Dtest` 选择上述两模块全部 `*Test.java` 类名，`-Dsurefire.failIfNoSpecifiedTests=false`；Windows SDK 测试设置 `jdk.net.unixdomain.tmpdir` 为本忽略目录下未创建的 `tcp-only` 路径以使用 TCP。前端执行 `npm run jest -- src/services/agent src/components/ai src/pages/ai-agent src/pages/modeling src/services/modeling --runInBand --silent --forceExit`；另执行 `npm run check:types`、`npm run build` 和原架构/产品/迁移/产物脚本。

构建后检查最新 origin/main 263e036c，git merge-tree 无冲突；本地验证基线仍为 5b3a1716，不将无冲突检查视为最新合并态验收。

脚本模型与模拟撤权只证明工程链路。真实模型、真实登录态撤权、源审计、完整 J2、专家语义及收益验收按用户安排仍 PENDING，Feature 保持 IMPLEMENTING；本地条件跳过不替代远端 CI 或环境验收。
