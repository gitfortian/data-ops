# V26 工程验收记录

实施与验证：2026-10-08～2026-10-09。基线 main d525f49b（PR #397 已合并）。
产品合同：[F-035](../../../product/features/F-035-agent-asset-impact-explanation.md)，实施说明：[V26](../../IMPLEMENTATION_V26.md)。

## 本批结果

| 范围 | 结果 | 证据 |
| --- | --- | --- |
| Consumption（39 类） | 104 tests，通过 | 包含 ConsumerUsageSummaryReaderTest 11 项、SubscriptionEvidenceWindowTest 11 项及原 Dataset/Data Service 来源同步、声明/使用分离、精确版本 golden 回归 |
| Asset（20 类） | 161 tests，通过 | 原分区权限/容错、USAGE 有界关系读取、独立消费窗口与 null 计数经原 API 投影 |
| Agent（63 类） | 348 tests，346 通过、2 条件跳过 | 实际 SDK、固定目标/工具、核验/预算/HITL、官方历史一致；MySQL StateStore upgrade 和 tool budget 本地条件不满足 |
| 前端相关范围（15 suites） | 101 unique tests，通过 | 宽范围 data-asset/components/ai 99 项；最终原页面/新组件 7 项包含新增 2 项，重复项不再计数 |
| 类型债务门禁 | 通过 | 139 项既有诊断，无新增；最终代码复跑通过 |
| 架构/CI/发布脚本 | 42 tests，通过 | architecture/release/ci Node 测试；Java 边界 77 reactor entries / 3212 production files、前端边界、62 条迁移基线、产品基线和 Product Guard 自测通过 |
| 前端构建与产物核验 | 通过 | 代码提交 198d36fafe2f566a09c8252c86471900a8919345 的 npm run build 与 frontend-artifact.mjs 版本/源摘要/产物校验通过 |
| PR 产品与 CI 路径检查 | 通过 | 产品影响与范围门禁通过；impact-plan 为 scoped，前端目标 src/pages/data-asset，后端覆盖改动模块及 Asset 的反向依赖消费者 |

后端合计 613 unique tests，611 通过、2 条件跳过。完整模块回归在一次 33 模块 reactor 中执行（只选择 Agent/Asset/Consumption 测试类）；不将依赖模块只编译记为测试通过。初次针对性运行有 3 个新测试在覆盖已抛异常的 Mockito stub 时错误触发旧 stub，改用 doReturn/doThrow 后完整回归通过；没有忽略失败。

构建后的补充提交只更新本验收 Markdown，不修改代码；本地产物 manifest 固定上述代码 SHA，CI 需要从最终 PR SHA 重建。源摘要按当时工作区计算，包括用户已有的本地 Boot 配置，不作为干净发行包的证明；该本地配置未提交。

## AI08 场景

- 原 ConsumptionAssetUsageSectionProvider 使用真实 ConsumerUsageSummaryReader，mock 仓储验证只调用有界读取，没有全量 list、保存或 normalization 查找；Reader 无同步器依赖。原 ConsumerImpactService.view 的同步/golden 测试通过，未替换原消费页旅程。
- SQL Wrapper 捕获实际 selectList 条件，验证 project/product/ACTIVE、updatedAt/id 与 observedAt/id 双降序、数据库 LIMIT；上下限及最大 int 均受 1～200 约束；缺项目/产品拒绝。
- 两侧独立故障/拒绝、空窗/满窗、稳定 Consumer 去重、跨项目/跨产品/非 ACTIVE/超限 payload 拒绝；单侧可读证据保留，另一侧计数/时间为 null；无原异常、演员身份或 Provider 引用进入摘要。
- Asset 原联合投影传递两侧状态/限额/范围及 NOT_PERFORMED。Agent 只核验原可读事实，null 不可核验为零；伪造满窗/状态/限额或缺范围时只降级业务类别，其他两类保持独立。
- 实际 AgentScope SDK 在预读后核验消费窗口范围，保留原调用预算，最终带固定范围说明且与官方 StateStore 消息一致。脚本模型仅证明工程链路，不证明真实模型语义。
- 原详情实际 section 加载路径分别展示 EMPTY 与部分 UNAVAILABLE；空窗口仍显示范围，失败侧显示“未知”，保留上限、满窗/未满窗及未同步说明。入口授权、项目/路由切换与迟到响应原测试继续通过。

## 适用限制

这是已持久化证据窗口，读取不触发来源同步；新成功使用尚未归一化时可能未出现，需返回原消费详情人工核对。LIMIT_REACHED 表示可能遗漏更早记录，不证明确切截断；WITHIN_LIMIT/EMPTY 不证明完整历史。跨来源不承诺原子快照。

真实模型、真实登录态撤权、源审计、完整 J2、专家语义及收益按用户安排继续 PENDING；Feature 保持 IMPLEMENTING。本地 MySQL 条件跳过不代替 CI 或环境验收。
