# Data Service Requirements

> 本文件只描述**模块需要什么**，不描述具体类和实现方式。历史讨论和迁移过程由 Issue / PR / Git 保存。

## 1. 模块目标

Data Service 将已发布的数据定义转化为稳定、可调用、可保护、可观测的只读在线数据 API。

它需要同时解决：

- 稳定服务身份与上游 Revision 更新之间的解耦；
- SQL / Datasource 来源可信；
- 调用参数安全绑定；
- API Key 鉴权、调用方识别和集群共享调用配额；
- IP/CIDR 来源白名单、黑名单与可信代理边界；
- Cache / Circuit 等低延迟 Runtime 保护；
- 多实例调用指标、审计生命周期和敏感参数保护；
- 文档、OpenAPI、调用日志和运行概览；
- 不绕过 Datasource 的统一 SQL Execution。

## 2. 服务定义

- Data Service 必须有稳定 ID、所属 Project Space、名称、Path、启用状态、最大返回行数、超时、分页开关和访问模式。
- Path 在模块内唯一；由于 Public Invocation URL 不包含 Project namespace，Path 必须跨 Project 全局唯一。
- 启用/停用不能改变已发布 SQL、Datasource 或 Source Revision。
- 删除服务时必须清理其 API Key、IP 访问策略/规则，并移除当前节点的 Runtime state。
- Runtime/Access 配置可以独立调整，不要求重新发布上游 Revision。
- Data Service 必须维护持久化的单调 Runtime generation，用于区分不同发布/配置代际。

## 3. Publication

```text
Source Provider
  -> immutable published revision
  -> server resolved SQL / datasource / contract
  -> stable Data Service identity
```

- 只有可发布状态的 Source 才能发布。目前要求 Source `status = ONLINE`。
- Source 必须提供有效的 `sourceRevisionId + sourceRevisionNo`。
- SQL 和 `dataSourceId` 必须由服务端 Source Provider 解析，客户端不得直接覆盖。
- 首次 publish 创建稳定 Data Service；republish 更新同一 Data Service，不创建第二个身份。
- republish 必须刷新 SourceReference 和 PublishedRuntimeSnapshot。
- republish 必须保留访问策略、Runtime Policy 和 Project ownership。
- Provider 声明 `managesServiceDefinition=true` 时，name/path/maxRows/timeout/pagination/description 由 Source Revision 管理；Data Service 客户端不能覆盖这些定义。
- Provider 不管理服务定义时，兼容既有服务侧设置，但仍不能由客户端注入 SQL/dataSourceId。
- 历史冻结来源如果已无 Provider，可以继续使用持久化 Runtime Snapshot，但不能伪造重新发布。

## 4. SQL Execution

- Data Service 只支持**单条 SELECT**。
- SQL 必须使用命名参数绑定，不允许把请求值字符串拼接进 SQL。
- SQL 中声明的命名参数必须在调用请求中存在。
- SQL 字符串、注释、类型转换等上下文中的 `:` 不能被误识别为命名参数。
- SQL 物理执行统一调用 `yak-ops-core` 的 `SqlExecutionRuntime`。
- Caller 必须标记为 `DATA_SERVICE`，并携带 Data Service ID 作为调用上下文。
- 返回值必须是 ResultSet；非查询结果视为错误。
- 最大取数和超时使用已发布 Data Service 配置。
- Pagination 控制参数 `pageNum/pageSize/returnTotalNum` 不得作为 SQL 命名参数透传。
- `pageSize` 不能超过服务最大返回行数。

## 5. Access / API Key / IP Policy

API Key：

- 支持 `NONE` 和 `API_KEY` 两种身份认证模式。
- 切换到 `API_KEY` 前必须至少存在一个有效 Key。
- Key create / rotate 时产生的 raw secret 只返回一次。
- raw secret 永不落库；持久化只保存不可逆 hash 和可识别 prefix。
- Key 可以设置名称、每分钟调用上限、过期时间和 enabled 状态。
- 已启用 `API_KEY` 时不能禁用或删除最后一个有效 Key。
- 鉴权失败返回 401；超过调用上限返回 429。
- `rateLimitPerMinute` 必须表示**整个 Yak Ops 集群共享的每 Key 固定窗口配额**，不能按 JVM/Pod 各自放大。
- 共享限流协调存储不可用时必须 fail closed，不能静默绕过配额。
- Key rotate / disable / delete 必须使对应共享限流窗口失效；过期窗口清理由维护任务处理，不能把扫描清理放在调用热路径。
- 成功鉴权后调用日志需要记录 Key ID / Name / Prefix 快照，不记录 raw secret。
- Console 管理 API Key 时必须先确认父 Data Service 属于 CurrentProject；keyId 不能绕过 Project ownership。

来源 IP 访问策略：

- 每个 Data Service 独立支持 `NONE / ALLOWLIST / DENYLIST` 三种 IP 访问模式；该模式与 `NONE / API_KEY` 身份认证模式正交。
- 规则只表达 `ALLOWLIST` 或 `DENYLIST`，支持单 IP、IPv4 CIDR、IPv6 CIDR、enabled、可选过期时间和说明。
- IP/CIDR 保存前必须规范化；相同 Data Service、相同名单类型、相同规范化网络不能重复创建。
- `ALLOWLIST` 模式只允许命中至少一条 enabled 且未过期白名单规则的来源；空白名单等价于拒绝所有外部来源。
- `DENYLIST` 模式拒绝命中任一 enabled 且未过期黑名单规则的来源；没有命中则继续调用。
- IP 访问判定必须发生在 API Key 查询、Hash 校验和共享限流之前；来源被拒绝时返回 403。
- 启用白名单或黑名单时，如果无法可信解析客户端 IP，必须 fail closed，不能静默绕过来源策略。
- 默认只信任 TCP 直接对端地址；不能无条件信任客户端提供的 `X-Forwarded-For` 或 `X-Real-IP`。
- 只有直接上游命中显式配置的 `yak.data-service.access.trusted-proxies` CIDR 时，才能读取 Forwarded Header。
- `X-Forwarded-For` 必须从右向左按可信代理链解析，在第一个非可信 Hop 停止；客户端预置的伪造地址不能覆盖该结果。
- Forwarded Header 格式异常时必须回退到直接对端，而不是继续信任另一个可伪造 Forwarded Header。
- Console 管理 IP 策略/规则时必须先确认父 Data Service 属于 CurrentProject；ruleId 不能绕过 Project ownership。

## 6. Runtime Resilience

`RuntimePolicy` 持久化以下策略：

- cache enabled
- cache TTL
- cache max entries
- circuit breaker enabled
- failure threshold
- recovery seconds

要求：

- Result Cache 与 Circuit state 可以保持 node-local，以避免把请求数据面强耦合到共享协调存储。
- Runtime Policy 变化时必须清理当前节点受影响的缓存/熔断状态。
- 服务停用、删除、重新发布关键 Runtime 配置时必须使当前节点旧状态失效。
- Circuit 打开时返回 503。
- Cache key 必须覆盖 compiled SQL 和绑定值；分页控制也必须进入 key，避免页间串数据。
- Cache identity 必须包含持久化 `runtime_generation`、Source Revision 以及影响执行/缓存的 Runtime shape；即使另一 JVM 未收到主动 invalidation，也不能命中新代际下的旧结果。
- Runtime generation 必须是持久化单调值，不能依赖时间戳精度作为唯一代际标识。
- Runtime 状态接口必须区分**集群调用指标**与**当前实例 resilience evidence**；不能把 node-local cache/circuit 冒充集群状态。
- 集群调用指标至少覆盖 total/success/failure、success rate、average duration、最近成功/失败；P95 如果基于有限样本，必须明确是 bounded recent sample，而不是宣称全历史精确分位数。

## 7. Documentation / OpenAPI

- 当前 SQL 中的命名参数集合是请求参数文档的唯一事实来源。
- 文档不能声明 SQL 中不存在的参数。
- SQL 新增参数但文档未填写时，必须生成安全默认描述，而不是丢掉参数。
- SQL 指纹变化时文档需要可识别 `schemaStale`。
- Response field 文档是显式业务契约，不从单次查询结果自动永久推断。
- OpenAPI 必须反映当前 runtime path、鉴权模式、参数和响应字段。
- 文档 schema type 必须来自明确 allowlist；未知类型不能静默降级。

## 8. Observability

正常情况下，每次真实调用必须能记录：

- Data Service ID / Project ID / 名称 / Path 快照；
- Caller 类型；
- API Key ID / 名称 / Prefix（如果存在）；
- 请求参数 JSON（受长度限制且在持久化前脱敏）；
- 成功/失败；
- duration；
- row count；
- error message；
- create time。

调用审计是 evidence，不是业务结果 Truth：

- 调用日志持久化失败不能把已经成功的查询变成失败响应；
- 调用日志持久化失败不能覆盖原始 SQL / IP 策略 / 鉴权 / 限流异常；
- 审计故障允许降级为内部告警/日志，业务调用语义保持原状；
- password/token/authorization/API Key/credential 等 Secret 参数必须在 JSON 序列化前完全脱敏；手机号、证件号、邮箱等常见个人标识必须按规则掩码；
- 单个服务详情的调用记录必须支持按 Data Service ID 有界读取，不能依赖“读取全局最近日志后在浏览器过滤”；
- Console 的日志、Overview、热点和失败聚合必须只读取 CurrentProject 的调用 evidence。

调用证据必须有生命周期：

- raw invocation log 有有限 retention，默认 30 天；
- 超过 raw retention 的完整小时必须先聚合到 hourly rollup，再删除对应 raw 行；
- 同一小时的 rollup 与 raw delete 必须在同一事务中完成，避免双算或证据丢失；
- hourly rollup 也必须有有限 retention，默认 365 天；
- 单轮维护必须有上限，不能用一个长事务一次处理全部历史积压。

运行概览需要支持 `24h / 7d / 30d`，至少提供 API 数量、启停数量、调用量、成功率、平均耗时、返回行数、趋势、热点 API 和近期失败。

## 9. Project / Permission Governance

Data Service 明确区分两个入口平面：

```text
Management Plane -> Yak Project membership + Data Service RBAC
Invocation Plane -> global service path + IP policy + NONE/API_KEY
```

要求：

- `/api/v1/data-service` 下的 Console 管理能力为 `PROJECT_REQUIRED`；
- API 集市、详情、发布、配置、API Key、IP 访问规则、Runtime 管理、Documentation、Overview、Logs 都不能跨 Project 读取或修改；
- 管理权限至少拆分为 `read / publish / manage / delete / access / runtime / observe`；
- Project membership 与 RBAC 是两个独立 gate，不能用其中一个替代另一个；
- `/api/v1/data-service/runtime/{servicePath}` 是 Public Invocation Plane，不要求也不信任 Yak Project Header；
- Public Invocation 只能通过全局唯一 runtime path 找到服务，再按已发布 IP 访问策略与 `NONE/API_KEY` 契约鉴权；
- Repository 只允许明确声明的全局窄 corridor，例如 Public Invocation 的 global-by-path 和首页只读 count；其他 management read/write 必须使用 CurrentProject；
- Source-managed Data Service 仍必须从 owning authoring context 发起定义变更，Project/RBAC 不能绕过该 owner boundary。

## 10. 模块边界

本模块负责：

- Data Service definition；
- Source publication / republish；
- Data Service invocation；
- API Key / auth / cluster-wide rate limit；
- Data Service 级 IP/CIDR allowlist / denylist 与可信代理解析；
- node-local cache / circuit；
- cluster invocation metric projection；
- API documentation / OpenAPI；
- sanitized invocation audit / overview / retention / hourly rollup；
- Data Service Management Plane 的 Project ownership / RBAC boundary。

本模块不负责：

- Data Development 的草稿/Revision 创作；
- Datasource connection/catalog/plugin 管理；
- Offline / Realtime Sync 调度；
- 任意 DML/DDL；
- shared result-cache Truth；
- distributed circuit-breaker state machine；
- 通用 API Gateway / WAF；
- 数据血缘或质量计算；
- 用 Project Header 替代 Public Runtime API Key 鉴权。

## 11. 兼容性要求

结构重构默认保持：

```text
/api/v1/data-service REST 路径
/api/v1/data-service/runtime/{servicePath} 外部调用地址
现有主要 JSON shape（允许向 Runtime status 增加兼容字段）
yak_ops_data_service_* 表的既有业务字段与 Flyway 历史
yak-ops-core SqlExecutionRuntime contract
Datasource Plugin / Catalog contract
Data Development SourceProvider 业务语义
401 / 403 / 429 / 503 HTTP 状态语义
```

Project cutover 可以通过 Expand + Backfill 增加 `project_id`，Runtime coordination / rollup / access-policy schema 通过 Data Service 自有 Flyway namespace 演进，不能改写已发布 Flyway 历史。

## 12. 需求变更协议

本文件没有描述的新业务行为统一报告：

```text
Requirement Gap
- requested behavior:
- affected API/domain:
- compatibility impact:
- proposed requirement update:
```

需求确认并更新本文件之后再改生产行为；Reviewer / AI 不得从实现细节反推并偷偷补需求。
