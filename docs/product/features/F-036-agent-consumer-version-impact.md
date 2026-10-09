# F-036 — 精确消费来源版本的已知影响说明

Status: IMPLEMENTING
Approval: 用户于 2026-10-09 合并 V26 后授权继续按规划实施。
Product basis: ACCEPTED PD-001/PD-002；APPROVED F-004；IMPLEMENTING F-009/F-011/F-019/F-035。

## 用户与结果

User：Dataset / Data Service 维护者。Problem：资产摘要不能回答某个精确来源版本被哪些已知 Consumer 使用，声明订阅容易被误当版本使用。Capability：在原消费详情的版本影响核对中显式发起只读 AI 说明。Journey：选择精确版本 → AI 消费影响说明 → 核对固定产品与版本并明确发送 → 读取有界证据、核验关键值 → 返回原消费版本核对人工检查。Expected Outcome：区分有效声明、该版本成功使用和未覆盖范围，准备人工兼容性核对，不判定完整影响、消费者已通知或允许发布。

Truth Owner：Dataset/Data Service 拥有来源与版本；Consumption 拥有 Subscription/normalized Usage；Consumer 源域拥有主体；Security 拥有授权；原 turn/StateStore 拥有执行和消息。Producer：Consumption 新窄只读 Query API；Consumer：原 Agent 文本/证据/核验和原消费详情。Reuse：CurrentProject、Asset READ、原源产品 lookup、归一化精确版本证据、有界仓储、预算/HITL/停止/恢复、原证据回链。无新模块、导航、表、业务状态机或第二份真相。

## 范围与失败

- CONSUMER_VERSION_IMPACT 只绑定 productType（DATASET/DATA_SERVICE）、规范十进制正数 productIdentity（字符串，Long 范围）与 sourceVersionIdentity（规范十进制正数，最多30位）；与其他目标互斥，turn/HITL/历史冻结。无模型参数的 get_consumer_version_impact_evidence 及原核验/日期/反问/只读 Skill 可用；普通对话和其他任务不能调用，禁止查询数据、Python、报告、候选、搜索、递归依赖或任何业务写入。
- 每次实际工具读取恢复真实用户/项目，检查 CHAT_RUN 与 Asset READ；源 API 重新检查当前项目及源产品可见身份。当前生效来源引用或该产品精确版本的已归一化成功证据才能确认版本归属，不能用展示版本、客户端 diff 或当前版本代替历史。历史归属仅凭成功证据确认时，不声称读取过不可变版本定义。无法确认时不展示订阅关系或推断不存在。
- 只读持久化：精确版本成功使用按 project/product/version 和 observedAt/id 双降序 LIMIT 10；有效订阅按 project/product/ACTIVE 和 updatedAt/id 双降序 LIMIT 10。先验证源身份与版本归属，再读订阅。缺源、无权、故障和无法确认归属各保留五态与固定说明；不同步、补录、保存或发通知。
- 分别登记来源版本证明、有效声明、精确版本使用三份证据；单侧故障不抹去另一侧，失败侧不提供数值。订阅不绑定版本；各侧 ConsumerRef（type/domain/identity）只表示稳定身份，不提供 Actor、displayHint、联系方式、原始 provider ref、SQL、参数或样本。按稳定身份分组且保留窗口记录数、限额、满窗范围和 NOT_PERFORMED；最多各 10 行，文本字段≤128、每份 JSON≤6000，超限对应来源不可用，不截断后改变语义。窗口已满可能遗漏更早记录，空或未满窗不证明历史完整；不存在全量/实时/原子快照承诺。
- Agent 基于三份本轮证据核验关键字段，按“版本归属 / 声明关系 / 该版本成功使用 / 缺口与人工核对”解释；用户背景与来源文本保持不可信数据。输出仍经原引用守护并写入同一官方历史消息，无建议草稿协议。
- 原消费入口只导航不预读/自动发送；要求当前项目/路由与已加载产品匹配、Asset READ/CHAT_RUN、选中版本有效且来源加载成功。变化立即隔离旧入口。精确版本回链带 reviewVersion；原页面不能把缺失指定版本静默切为当前版本，显示重新选择提示。URL/continuation 严格校验目标及重复/混合参数，坏上下文拒绝普通对话回退。

## 验收与边界

CV01 产品/版本互斥冻结、工具禁止和零源调用；CV02 授权先于仓储、项目/产品/版本/ACTIVE 与数据库限量排序；CV03 当前引用/历史成功证明/未知归属/部分失败；CV04 身份白名单、容量与超限拒绝；CV05 实际 SDK、预算、HITL 与最终历史一致；CV06 原入口、切换/失败/撤权与精确版本回链；CV07 原工具及消费同步回归。

Agent.gateway → consumption.api 为唯一新增依赖走廊；Consumption 不依赖 Agent。F-004 的 Phase 4 不交付 Agent 产品，本文单独授权 AI 只读 consumer，不改 Phase 4 真相。真实模型、登录态撤权、源审计、完整 J2、专家语义及收益按用户安排 PENDING，不标 SHIPPED。

实施说明：[V27](../../ai/IMPLEMENTATION_V27.md)。工程证据：[V27 验收](../../ai/acceptance/2026-10-09-v27/README.md)。
