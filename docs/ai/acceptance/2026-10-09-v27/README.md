# V27 工程验收记录

日期：2026-10-09。基线 main a9e6bca3（V26 PR #431 已合并）。产品合同：[F-036](../../../product/features/F-036-agent-consumer-version-impact.md)，实施说明：[V27](../../IMPLEMENTATION_V27.md)。

## 结果

| 范围 | 结果 | 证据 |
| --- | --- | --- |
| Consumption | 138 unique tests，通过 | 原完整回归 137 项；最终 adapter 7 项复验补入 1 个身份分隔符碰撞回归 |
| Agent | 357 unique tests，355 通过、2 条件跳过 | 原完整回归 356 项；最终 projection/runtime 9 项复验补入 1 个 JSON 转义膨胀容量回归；MySQL StateStore upgrade / tool budget 本地条件不满足 |
| 前端相关范围 | 469 unique tests / 45 suites，通过 | services/agent、components/ai、pages/ai-agent、pages/data-analysis/consumption 共 468 项；最终 Agent 页面 7 项复验补入 1 个非法上下文拒绝回归 |
| 类型债务门禁 | 通过 | 139 项既有诊断，无新增；不更改基线 |
| 架构 / CI / 发布脚本 | 45 tests，通过 | Node architecture/release/ci；Java 边界 77 reactor entries / 3220 production files，前端依赖边界、62 条迁移基线、38 份产品 Feature 基线、Product Guard 自测通过 |
| 前端构建及产物 | 通过 | 代码提交 c4904b019c18ddc3e40cb3cdb4a76f011740956e 的 npm run build 和 frontend-artifact.mjs 版本/源摘要/产物校验通过 |
| PR 产品 / 范围门禁 | 通过 | Product Impact / E2E 声明有效，无新增 Maven 模块或一级导航 |
| CI 路由 | full（预期） | impact-plan 首个原因：Maven, migration or published Java API changed: data-ops-business/data-ops-business-agent/pom.xml；本批还新增 Consumption public Java API |

后端共 495 unique tests，493 通过、2 条件跳过。完整回归仅选择 Agent / Consumption 测试，在 33 模块 reactor 内执行；依赖模块编译不记为测试通过。最终新增/修改测试类复验与完整回归重叠部分不重复计数。初次编译发现源权限接口方法应为 requirePermission，修正后完整回归通过，没有忽略失败。

本验收提交仅增加 Markdown；本地产物 manifest 固定上述代码 SHA，CI 从最终 PR SHA 重建。源摘要包含用户已有的本地 Boot 配置，不能作为干净发行包证明；该配置及用户未跟踪资料未提交。

## CV01–CV07 工程链路

- 目标 JSON roundtrip 保留字符串身份与 30 位版本。混合目标、非法数字、错误 purpose 和重复 URL 拒绝；普通对话不可调用新工具，任务不能搜索、查数、写报告或生成治理候选。
- Source adapter 在仓储前重查 Asset READ / CurrentProject / 产品身份，拒绝跨项目与错版本。实际仓储调用固定 listByVersion(..., 10) / listRecentActive(..., 10)，无全量读取、保存或同步器依赖。原数据库 SQL 条件/排序/限量测试及原同步 golden 回归通过。
- 当前来源引用与历史精确成功证据分别确认版本归属，未知历史版本不读订阅。已知版本单侧读取失败保留另一侧 EMPTY/OK；原异常和私有展示字段不进入模型投影。
- 三项 Consumer 身份分组不依赖拼接字符串，分隔符碰撞不会误合并。网关对错目标、缺归属、重复身份、超长字段、错计数/满窗状态及 JSON 转义后超预算分别拒绝；不截断身份，单侧故障只影响该侧。
- 实际 AgentScope SDK 完成预读、事实核验、引用守护与同一 StateStore 历史消息持久化；预读只占一次预算。真实 SDK HITL 挂起后恢复精确版本和原预算，拒绝换版本或扩大预算。脚本模型仅证明工程链路，不证明真实模型语义。
- 原消费详情入口验证项目/路由/权限/加载/选定版本；显式点击只导航。Agent 准备按钮只填输入框，不发请求，回链复用精确 reviewVersion；缺 purpose 的消费上下文不退回普通聊天。原精确版本加载与禁止静默切换逻辑继续复用。

## 范围限制

仅读取已归一化的持久化窗口，每侧最多 10 条。LIMIT_REACHED 表示可能遗漏更早记录；WITHIN_LIMIT / EMPTY 不证明来源历史完整。有效订阅不绑定版本，Consumer 身份不表示负责人、联系方式或授权；历史成功证明不等于已读取不可变版本定义。NOT_PERFORMED 表示本次未同步来源，时效未知，跨侧不承诺原子快照。原消费页面仍可人工补核来源。

构建完成后检查了最新 origin/main 2c0cd0c5，git merge-tree 无冲突；本地测试基线仍是 a9e6bca3，不将无冲突检查冒充最新合并态验收。

真实模型、真实登录态撤权、源审计、完整 J2、专家语义与收益验收按用户安排继续 PENDING。Feature 保持 IMPLEMENTING，2 个本地 MySQL 条件跳过不代替 CI / 环境验收。
