# F-035 — 有限资产影响说明

Status: IMPLEMENTING
Approval: 用户于 2026-10-08 合并 V24 后授权继续实施。
Product basis: ACCEPTED PD-001/PD-002；APPROVED F-001-A；IMPLEMENTING F-009/F-011/F-019。

## 用户、问题与结果

User：资产负责人及变更申请人。Problem：结构引用、声明订阅、成功使用和资产页浏览容易混为完整影响。Capability：在原资产详情发起只读的有限影响摘要说明。User Journey：已加载且授权的资产详情 → AI 影响说明 → 显示固定资产与摘要范围 → 明确发送 → 核对三类证据与缺口 → 返回资产使用分区及源域入口人工检查。Expected Outcome：理解当前已知引用和使用范围，知道还需核对什么；不作完整影响或发布安全判断。

Truth Owner：Lineage 拥有有向结构关系；Asset 拥有页面访问；Metric/Consumption 拥有各自已接入的引用、声明订阅和成功使用证据；Security 拥有授权；原 turn/StateStore 拥有执行和消息。Producer：既有 AssetGovernanceQueryApi USAGE 分区；Consumer：原 Agent 文本/事实/证据及资产详情。Existing capabilities to reuse：项目/RBAC、原分区权限及五态、UsageSummary、预算、HITL、停止/恢复、核验与原页面回链。无新模块、一级导航、表、业务状态机、权限或第二份业务真相。

## 范围与失败

- GovernanceTarget 的资产任务增加 ASSET_IMPACT purpose，只绑定一个正数 assetId；在 turn/HITL/历史恢复中冻结，模型不能更换资产或递归读取下游对象。
- 仅允许无参数 get_asset_impact_evidence 及原核验/日期/反问/只读 Skill 工具。普通对话不能调用此工具。禁止其他资产分区、搜索、Dataset/Python/报告、候选和写入；现有任务维持原范围。
- 每次工具读取重新经 UserExecutionScope、CHAT_RUN、Asset READ 与源 USAGE 权限。只读原 USAGE 摘要，不读取 LINEAGE 完整图。结构数量保持原“一跳下游关系条数”语义，不等于不同消费者数量；Lineage 对根对象按当前项目 COUNT 聚合，缺项目拒绝，不加载邻接节点/边/表达式。
- 三类摘要分别登记 owner/status/sourceUpdatedAt/reference；顶层拒绝或失败不得带出 payload，子来源不可读不得暴露其数值。保留 OK/EMPTY/UNAVAILABLE/PERMISSION_DENIED/NOT_APPLICABLE，未知或畸形子摘要降为 UNAVAILABLE。单个来源失败不抹去其他证据，EMPTY 不证明全平台无使用。
- 投影只保留各类别已定义的标量字段：页面窗口/次数/时间/含义；结构方向/hop/关系数；业务 scope/计数/时间/coverageNote。禁止任意属性、原异常、每日用户明细、SQL、连接、样本或用户标识。文本≤512、每类 JSON≤6000 UTF-16 units，超限该类不可用，不截断后改变语义。证据容量按三项原子预留。
- 已记录的 Metric 引用不是实际调用；Dataset 声明订阅和已归一化成功使用保持独立计数，coverageNote 的范围限制必须保留。源更新时间未提供保持 unknown，不用读取时间伪造源更新；读取不承诺跨来源原子快照。
- 先核验关键值与状态，再按“已知结构引用 / 已记录业务使用 / 页面活动 / 缺口与人工检查”表达。模型引用校验不证明语义正确。最终消息、历史和恢复使用原协议，并追加固定范围/人工核对提示；不生成可带入候选，不通知、不修复、不确认安全或完整影响。
- 原入口只导航，不预读/自动发送。详情加载失败、撤权、项目/路由切换或迟到响应不能沿用旧详情发起新任务；原问题准备不覆盖输入。旧治理解读和描述候选兼容。

## 验收与依赖

AI01 固定目标/工具范围及坏上下文拒绝；AI02 当前项目计数、不加载图与原关系计数语义；AI03 三类 owner/五态/范围、缺失及部分失败；AI04 投影白名单/文本与容量上限；AI05 实际 SDK 预读、核验、预算、HITL 与最终历史一致；AI06 原入口、明确发送、项目/路由/权限/失败隔离；AI07 全部原工具与场景回归。

依赖仍 Agent.gateway → Asset.api → 原 Discover/源域；Lineage 在原 Query facade 内增加聚合读取，不增加依赖边。历史连续路线将此项列为候选，不作为当前产品真相；本 Feature 承接用户本次授权。真实模型、登录态撤权、源审计、完整 J2、专家语义及收益仍按用户安排 PENDING，不标 SHIPPED。
