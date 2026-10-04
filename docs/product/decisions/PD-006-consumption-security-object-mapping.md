# PD-006 — 消费安全对象与调用平面映射

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-10-04  
Owner: Product

## Context

PD-002/F-004 要求 Access 显示与真实消费动作一致。Dataset 已使用 SQL 投影后的物理列
安全键；Data Service 外部 Consumer/API Key 与 Console USER 权限是独立平面。
需要冻结扩展对象映射和前置裁决范围，而不能通过产品展示名或 Asset 联系人补齐授权。

## Current Behavior

Dataset 功能权限不代表物理列 READ 许可，实际 query 再做血缘、裁决、脱敏、审计。
Data Service 已配置 Consumer grant 时，公网调用强制 Consumer Key；旧 authMode=NONE
不能覆盖这个 gate。登录用户不能证明外部 Key 可调用。源域 owner/visibility 尚未暴露，
Asset governance 联系人与 owning owner 不能混用。

## Decision

候选规则，接受后才实现：

- Security 拥有 canonical object/action/principal mapping，来源域提供 exact source identity。
- Dataset Query 的对象仍为 exact immutable SQL 投影产生的物理列键，动作为 READ；
  ProductKey/assetId 只作导航，不作为物理资源授权替身。
- 预裁决必须携带 DatasetVersion、请求字段/过滤/排序和稳定 USER principal/roles；
  仅功能权限通过时无最终裁决。执行时重新验证，防止策略变化后沿用旧允许结果。
- Data Service 公网对象为 owning serviceId，主体为来源 Consumer/Key identity；
  Console read/manage 使用 USER+Project+RBAC。API Key secret 不进投影/审计。
- Consumer grant、IP policy、Key 状态、配额和源 availability 必须分别解释；
  旧 authMode 只表示 legacy fallback，不等于 effective invocation policy。
- 未冻结的 Model/Metric/MDM/Agent 映射不通过类型转换暗中接入。
- owning owner/visibility 由源域显式维护或由 Security 提供适用策略；没有证据时显示缺口。
  不从 updatedBy、Project owner 或 Asset 联系人猜测这些事实。

## Product Outcome

消费者沿 J3/J5 区分能发现、能管理和能真实消费；被拒绝时看到具体原因和现有处置入口。

## Alternatives Considered

用 ProductKey 统一裁决全部产品：拒绝，Dataset 物理列策略会被绕过。
从登录权限推断 API Key 权限：拒绝，外部主体与调用平面不同。

## Consequences

保持已有安全键兼容；需要来源版本/字段映射、effective policy 只读契约和覆盖诊断。
预裁决仍可能因策略变更与执行结果不同，界面必须说明时间和执行再验证。

## Truth / Ownership Impact

- Truth Owner: Security 对象/策略；Dataset exact query；Data Service grant/Key/IP；
  owning domain 的 owner/release；Consumption 只投影。
- Producers: source metadata、Security decision、真实 Query/Invoke 审计。
- Consumers: Console USER、外部 Consumer、治理 Owner。

## Navigation / UX Impact

复用 Consumption Detail、Dataset Query 与 Consumer access 管理入口。REQUEST_REQUIRED
必须指向真实请求流程或明确联系人；provider 故障不提供“已允许”提示。

## Migration Plan

不改写历史物理键或 Consumer identity。先补 owning read contracts 和映射矩阵，
接受后用增量 Feature 实施；现有 Dataset 执行门禁继续独立生效。

## Acceptance Evidence

字段允许/拒绝/脱敏、血缘缺失、provider 故障、跨 Project、Console 可见但 Query 禁止、
有效/无效/过期 Key、Consumer grant 撤销、IP 拒绝、配额耗尽、旧 authMode 与 effective
policy 差异、预裁决后策略变化，以及权限/订阅不生成 Usage 的真实证据。

## Supersedes

None
