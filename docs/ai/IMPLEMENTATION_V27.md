# Agent V27 — 精确消费来源版本的已知影响说明

日期：2026-10-09。基线 main a9e6bca3（V26 PR #431 已合并）。产品合同：[F-036](../product/features/F-036-agent-consumer-version-impact.md)，保持 IMPLEMENTING。

## 用户路径与复用

User：Dataset / Data Service 维护者。Problem：资产摘要不能回答某个来源版本的已知使用，订阅声明容易被误认为该版本的实际成功消费。Capability：原消费版本核对中的只读 AI 说明。Journey：选定版本 → AI 消费影响说明 → 用户明确发送 → 分别核验版本归属、有效订阅和该版本成功使用 → 回到同一精确版本人工检查。Outcome：理解已知范围和缺口，准备兼容性核查。

Truth Owner：Dataset/Data Service 拥有来源与版本，Consumption 拥有声明与归一化成功证据，Consumer 源域拥有主体，Security 拥有权限，原 turn/StateStore 拥有执行和消息。Producer：Consumption 窄 Query API；Consumer：原 Agent 和原消费详情。复用当前项目、真实身份恢复、权限、源产品查找、有界仓储、工具预算/HITL、事实核验、官方历史及既有 reviewVersion 回链。

## 实现

- CONSUMER_VERSION_IMPACT 固定产品类型、字符串产品 ID 与精确来源版本；与原任务互斥。无参数工具不能由模型换目标，普通对话不可调用，原辅助工具之外的查询、候选和写入被拒绝。
- Source adapter 检查 Asset READ、当前项目、产品可见性及身份。版本归属仅由当前生效来源引用或该产品精确版本的归一化成功记录证明；历史成功证明不等于读取不可变定义。无法确认时不读取订阅。
- 只读最近 10 条有效声明及该版本最近 10 条成功使用。复用已存在的 listRecentActive / listByVersion 数据库条件与排序；没有同步器、保存或源审计补录依赖。原消费详情同步流程保留。
- 三份证据独立：versionMembership / subscriptions / versionUsage。部分失败保留其他侧，未知不补零，声明不绑定版本。按 tagged Consumer 身份分组并保留记录数、窗口上限、满窗状态和 NOT_PERFORMED。只交付身份、计数及观察时间；没有 displayHint、Actor、联系方式、Provider 原始引用、SQL 或样本。
- Agent 网关对固定目标、状态、身份类型/文本长度、计数一致性和 JSON 容量做白名单投影，失败只降级对应侧，不截断后改变含义。提示词与固定下一步说明空窗/未满窗不证明历史完整；返回原精确版本人工核对。
- 原消费入口要求当前项目、路由与产品一致且版本已加载、权限有效。只导航；Agent 准备按钮只填问题，不自动开始。坏 URL / 混合目标 / 重复参数拒绝，旧 continuation 保持兼容。SDK 预算、HITL 固定版本与最终官方消息一致。

新增依赖仅 Agent gateway → consumption.api，Consumption 不反向依赖 Agent。没有新模块、一级导航、表、业务状态机或第二份业务真相。

## 验证与 CI

见 [V27 工程验收](acceptance/2026-10-09-v27/README.md)。新公共 Java API 和 Maven 依赖边命中现有 impact-plan 的 full 条件，因此本 PR 预期运行完整 CI；没有修改 CI 规则。

真实模型、真实登录撤权、源审计、完整 J2、专家语义与收益验收按用户安排继续 PENDING。
