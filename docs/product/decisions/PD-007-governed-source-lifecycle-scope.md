# PD-007 — Governed 来源生命周期范围

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-10-04  
Owner: Product

## Context

PD-002 规定 NOT_PUBLISHED/PUBLISHED/DEPRECATED/RETIRED，与 availability、access、
discoverability 正交。Dataset 当前 ONLINE/OFFLINE、Data Service enabled/runtime 不足以
表达废弃与退休。不能在 Consumption 根据停用或运行异常自行发明生命周期。

## Current Behavior

DatasetVersion 和 Data Service Revision 是冻结事实。下线/停用保留发布版本并使调用
不可用。Asset 上架状态独立；Model TTL 是既有独立范围，未授权扩展到消费产品。

## Decision

候选规则，接受后才实现：

- 首期 owning lifecycle 仅覆盖 PD-002 的 Dataset/Data Service；Metric/MDM/Model 不自动加入。
- 没有发布版本为 NOT_PUBLISHED；有效发布版本为 PUBLISHED，运行停用不改发布事实。
- DEPRECATED 是来源 Owner 声明“仍可消费但建议迁移”，必须有原因、可选替代来源和时间。
- RETIRED 是来源 Owner 明确结束消费，保留版本/审计/已知影响读侧；执行门禁拒绝新消费。
- 来源域拥有 transition 与审计，Consumption 只读取；跨 Product 的状态独立，不能同步猜测。
- Subscription 不等于权限：退休不改写历史声明；不自动生成/删除 Usage。
  已知影响同时标注订阅状态、历史成功消费与证据覆盖。
- 物理删除、历史 retention、TTL 自动退休与强制撤销依赖不纳入首期。

## Product Outcome

Owner 沿 J4 预览 known impact，消费者沿 J3 理解迁移与不可用原因；源停用后仍能定位
此前使用的 exact version。不会把 runtime 故障错误解释为退休。

## Alternatives Considered

OFFLINE=RETIRED：拒绝，临时下线和结束服务不同。
由 Asset TTL 自动退休所有产品：拒绝，超出 Model TTL 已批准范围。

## Consequences

需要两个来源域显式补充生命周期契约和增量持久化；保留兼容状态/版本。
退休可能影响真实消费者，因此复用现有预览确认与审计，不把有限 known impact 声称为全部依赖。

## Truth / Ownership Impact

- Truth Owner: Dataset/Data Service lifecycle；Asset governance state；Consumption 历史关系；
  Lifecycle 保持既有 Model TTL 范围。
- Producers: source Owner command、来源版本、消费 evidence。
- Consumers: 数据消费者、API 集成方、Owner/Steward。

## Navigation / UX Impact

复用来源详情/发布管理与 Consumption Detail，分别展示 lifecycle、availability 和 access。
废弃展示替代路径；退休保留治理/历史证据和稳定回链，不新增一级导航。

## Migration Plan

既有发布版本映射为 PUBLISHED，不从 ONLINE/OFFLINE/异常推断 DEPRECATED/RETIRED。
接受后补 APPROVED Feature、两个来源 Domain/Requirement、迁移与状态转换测试再实施。

## Acceptance Evidence

未发布、已发布、停用与恢复、废弃迁移、退休拒绝、并发/重复命令、跨 Project、
历史版本不变、订阅/Usage 不重写、runtime/provider 故障与退休的独立显示。

## Supersedes

None
