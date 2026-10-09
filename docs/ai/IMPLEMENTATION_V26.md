# Agent V26 — 消费证据只读与窗口范围收口

日期：2026-10-08。从 V25 PR #397 合并后的 main d525f49b 开始。
产品指令：[F-035](../product/features/F-035-agent-asset-impact-explanation.md)（IMPLEMENTING），PD-001/PD-002、F-004/F-009 的来源所有权与只读边界不变。

## 用户路径与范围

User：准备变更的资产负责人。Problem：资产使用摘要的消费 Provider 原先调用 ConsumerImpactService.view，读取过程中会同步并保存归一化证据，且订阅不受窗口限制；联合投影丢掉两侧状态，页面把缺失值补零。Capability：原有限影响任务只读现有证据并说明窗口缺口。Journey：原资产详情 → AI 影响说明 → 明确发送 → 分别核验业务摘要状态和窗口 → 返回原消费详情人工核对。Outcome：知道哪些计数是窗口内事实、哪些来源未知，不因读取而创建业务证据。

Truth Owner：Consumption 拥有 Subscription / normalized Usage；Dataset 保留原始成功查询与精确版本；Asset 拥有资产及页面活动；原 Agent turn/StateStore 拥有执行与消息。Producer：Consumption SectionProvider → 原 Asset USAGE；Consumer：原详情与 Agent。Reuse：现有项目/RBAC、SPI、证据核验、预算、HITL、历史、原页面回链。不新增任务、产品类型、模块、导航、业务状态机、表或依赖边。

## 实现边界

- ConsumptionAssetUsageSectionProvider 改用 ConsumerUsageSummaryReader。Reader 只有 Subscription/Usage 仓储读取与 CurrentProject 依赖，没有同步器或写入口；原消费详情 ConsumerImpactService.view 保留来源同步与版本核对流程。
- 订阅新增数据库内的当前项目/固定产品/ACTIVE 条件与 updatedAt/id 双降序 LIMIT；两侧各限制 1～200 行。Reader 拒绝跨项目/跨产品、非有效订阅、超限或畸形返回，不在内存获取全量后截断。
- 两侧 READY/EMPTY/UNAVAILABLE/FORBIDDEN 独立；LIMIT_REACHED 只说明窗口已满，可能有更早记录；WITHIN_LIMIT 不证明原始历史完整。NOT_PERFORMED 永远说明本轮未同步来源，时效未知，不承诺原子快照。
- 已知 Consumer 数量及类型分布仅为可读窗口的稳定身份去重并集。失败侧专属计数/时间为 null；没有原始诊断、身份、凭据或 Provider 明细进入摘要。没有成功使用时不伪造观察时间。
- Asset 联合投影保留所有窗口字段；Agent 类别白名单校验状态、限额、计数与范围一致性，坏来源只降级该类别。失败侧 null 不能核验为零。原普通分区读取也保留这些字段。
- Agent 提示词与最终固定提示保留“未同步 / 有限窗口 / 未知不为零”；原 SDK 预读、引用、预算、HITL 和同一消息落盘不变。原资产详情显示两侧范围及未知数值，Metric 引用展示维持独立。

## 验收证据

见 [V26 验收记录](acceptance/2026-10-08-v26/README.md)。只读查询、窗口/故障、联合投影、实际 SDK/历史与原消费同步回归属于工程证据；真实模型、登录态撤权、源审计、完整 J2、专家语义及收益按用户安排继续 PENDING，不标 SHIPPED。
