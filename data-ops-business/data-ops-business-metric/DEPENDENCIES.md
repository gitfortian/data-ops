# Metric Dependencies

## 本模块依赖（出向）

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `data-ops-common` | 编译 | 、权限码（`constant.metric`）、错误码（`enums.metric`）——平台惯例 |
| `data-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`BusinessException`/`@RequiresPermission`/`CurrentUserProvider` |
| `data-ops-business-audit` | 编译 | `BusinessAuditService`/`AuditTransactions` 审计门面（fail-open） |
| `data-ops-business-datasource` | 编译（optional） | 现有持久化开关注解；共享数据库由 Boot 装配 |
| `data-ops-business-semantic` | 编译 | **仅经 `api` 包 SPI**（`StandardQueryApi`/`ProcessApi`），引用口径标准、业务域、字段库 |
| `data-ops-business-modeling` | 编译 | **仅经 `api` 包 SPI**（`ModelingModelApi`），引用 DWS/ADS 模型 |
| `data-ops-business-lineage` | 编译 | 调用 `LineageAssetRegistrar`/`LineageRelationRegistrar`/`LineageGraphReader`，血缘注册与可视化 |
| `data-ops-business-asset` | 编译 | 实现 AssetProvider，并向 Asset Usage Section 提供 Metric-owned 消费引用摘要 |
| `data-ops-spi` | 编译 | 实现 SectionProvider，供 Asset 聚合 Metric Usage Truth |

`data-ops-business-data-development` 只能通过 `MetricUsageApi` 读写 Dataset Reference Usage；消费方不依赖 Metric mapper、PO 或数据库表。

## 被依赖（入向，规划）

| 模块 | 方式 | ticket |
| --- | --- | --- |
| `data-ops-business-dataset` | **仅经 `api` 包 SPI**（`MetricQueryApi`/`MetricUsageApi`），禁止直读本模块表 | 52+ |
| `data-ops-business-dashboard` | 同上 | 52+ |
| `data-ops-business-data-service` | 复用 API Key 鉴权与调用记录 | 54 |

## 禁止

- **禁止 import `io.yak.ops.business.modeling.*` 的内部实现**——仅经 SPI 调用。
- **禁止 import `io.yak.ops.business.semantic.*` 的内部实现**——仅经 `api` 包 SPI。
- 禁止反向读取本模块表/绕过 SPI 暴露内部实现类型（dao/dao.model/repository.impl 一律不对外）。


## F-025 指标版本口径 Skill

MetricExplanationQueryApi 是 Metric-owned 授权只读投影：固定当前版本，读取不可变快照并复用 digest；仅白名单有界事实，超界/缺快照不可用。Agent 仅 gateway → metric.api，源域不反向依赖 Agent。复用 SDK 场景执行与原表单，候选仅 businessDesc，人工保存复用 expectedVersion、校验、审计和回读；验证/发布仍独立。引用校验不等于自然语言正确，真实模型验收 PENDING。合同见 docs/product/features/F-025-skill-metric-caliber.md。


## 场景辅助与 J2 原页面交接（F-027/F-028/F-029）

精确历史版本解释保持不可变快照，与当前验证/发布/影响事实分开。定义辅助只生成类型适配的白名单草稿，人工原保存、精确版本验证/发布仍独立。消费出口只使用已登记目标与版本，缺失/未知/不可用不伪造完成。

依赖仍是 Agent runtime → toolset → gateway → 源域 api；Modeling/Semantic/Metric 不依赖 Agent。复用现有保存、权限、项目与审计，无新业务状态机/事实库。精确合同见 docs/product/features 下相应 Feature。

MetricDraftQueryApi 由 catalog.MetricDraftQueryAdapter 持有：先 Metric READ，读取当前项目的精确指标版本/启用上游，再经 ModelSuggestionQueryApi.fields 读取获授权的有界字段。digest 绑定用户需求、类型、来源结构和上游版本；validate 重读 digest 并检查实际字段、限定条件编译及引用 token 表达式，不写 Metric、不执行 SQL。POST /api/v1/metrics/draft-context 供原编辑器显式准备；Agent gateway 使用同一 API。requireSnapshot 与 snapshot-explanation-context 只读指定不可变版本，不展开今日依赖。

UI 在原表单逐项采纳后走原 create/update expectedVersion 保存，保存回执提供精确 ID 的详情入口。原发布面板使用已登记 DATASET 引用且 metricVersion 与 active publication 一致时映射 canonical Consumption productKey；API/未知版本/未映射目标明确降级，不等同运行事实。
