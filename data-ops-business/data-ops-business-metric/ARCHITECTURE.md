# Metric Architecture

## 包结构

```
io.yak.ops.business.metric
├── config/        ConditionalOnMetricPersistence + MetricPersistenceConfiguration(Flyway/MapperScan)
├── catalog/       Metric/MetricStatus/MetricType(域对象)/MetricCatalogService
├── validation/    exact-version definition validation and append-only evidence
├── publication/   fail-closed gates, explicit publication ledger and active pointer
├── impact/        dependency, Reference Usage, Lineage and Observed Usage projection
├── usage/         MetricUsageService + project-scoped MetricUsageRepository
├── exception/     MetricException(BusinessException)
├── repository/    MetricRepository(接口)+ MetricRepositoryAdapter(MyBatis 适配)
├── dao/mapper/    MetricMapper(BaseMapper)
├── api/           MetricQueryApi/MetricUsageApi/MetricLineageApi(SPI 接口;请求/响应 record)
└── controller/v1/ MetricController + dto/ + vo/ + converter/
```

Existing tag/version/lineage packages retain their ownership; the Phase 5 packages extend the existing Metric aggregate and do not introduce a second Semantic, Modeling, Lineage or Consumption truth.

## 持久化

- 自持 Flyway：`classpath:db/migration/yak-metric`，历史表 `flyway_schema_history_metric`，baseline 0。
- Metric Flyway 在 Dataset Flyway 之后运行；V6 需以 `yak_dataset.id` 归一历史 Dataset Reference Usage 的消费方标识。
- 共享数据源：Boot 的 `config.persistence.BusinessDatabaseConfiguration` 应用装配（Boot 应用装配提供 yakBusinessDataSource / yakBusinessSqlSessionFactory / yakBusinessTransactionManager），与 semantic/modeling 同池。
- 事务：`@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO 位于本模块 `io.yak.ops.business.metric.dao.model`；common 保留稳定共享契约。

## 分层规则

- Controller：参数校验注解 + 权限 + 项目上下文透传，**无业务规则**。
- Service：唯一规则归属地（校验、不变量、引用阻断）；操作域对象而非 PO。
- Repository：项目空间绑定（每次读写绑定 `CurrentProject`），无业务规则。

## SPI 面（消费方边界）

`api` 包对外暴露：

| SPI | 消费方 | 说明 |
| --- | --- | --- |
| `MetricQueryApi` | dataset/dashboard | 查询指标元数据、批量解析展示名 |
| `MetricUsageApi` | dataset/dashboard/data-service | 消费方上报引用事件（异步容错） |
| `MetricLineageApi` | 内部使用 | 指标 CRUD 时触发血缘注册 |
| `SectionProvider(USAGE)` | Asset | 通过 `MetricUsageApi.summary` 读取消费引用数，不复制 Metric 使用真相 |
| `SectionProvider(USAGE)` | Asset | 通过 `MetricUsageApi` 读取消费引用数；不复制 Metric 使用真相 |

**消费方只经这些接口消费，不直读本模块表。**

## 迁移所有权

| 表 | 迁移 | ticket |
| --- | --- | --- |
| yak_metric | V1 | 45 |
| yak_metric_tag | V1 | 45 |
| yak_metric_tag_rel | V1 | 45 |
| yak_metric_version | V1 | 45 |
| yak_metric_dependency | V1 | 45 |
| yak_metric_composition | V1 | 45 |
| yak_metric_usage | V1 | 45 |
| yak_metric_validation_evidence | V4 | F-005 |
| yak_metric_publication_event | V5 | F-005 |
| yak_metric_active_publication | V5 | F-005 |
| yak_metric_usage.metric_version | V6 | F-005 |
| yak_metric_validation_evidence.provider_state | V7 | F-005 |

迁移合入后**不可再编辑**（对齐 modeling/semantic 纪律）。

`scripts/db/phase5-metric-productization-upgrade.sql` 是不运行 Flyway 时的手动等价路径；手动脚本和模块 Flyway 迁移只能选择一条。配套 verify 脚本依据 Flyway 最终列与索引形状检查，不使用手工脚本私有的列名或索引顺序。


## F-025 指标版本口径 Skill

MetricExplanationQueryApi 是 Metric-owned 授权只读投影：固定当前版本，读取不可变快照并复用 digest；仅白名单有界事实，超界/缺快照不可用。Agent 仅 gateway → metric.api，源域不反向依赖 Agent。复用 SDK 场景执行与原表单，候选仅 businessDesc，人工保存复用 expectedVersion、校验、审计和回读；验证/发布仍独立。引用校验不等于自然语言正确，真实模型验收 PENDING。合同见 docs/product/features/F-025-skill-metric-caliber.md。


## 场景辅助与 J2 原页面交接（F-027/F-028/F-029）

精确历史版本解释保持不可变快照，与当前验证/发布/影响事实分开。定义辅助只生成类型适配的白名单草稿，人工原保存、精确版本验证/发布仍独立。消费出口只使用已登记目标与版本，缺失/未知/不可用不伪造完成。

依赖仍是 Agent runtime → toolset → gateway → 源域 api；Modeling/Semantic/Metric 不依赖 Agent。复用现有保存、权限、项目与审计，无新业务状态机/事实库。精确合同见 docs/product/features 下相应 Feature。

MetricDraftQueryApi 由 catalog.MetricDraftQueryAdapter 持有：先 Metric READ，读取当前项目的精确指标版本/启用上游，再经 ModelSuggestionQueryApi.fields 读取获授权的有界字段。digest 绑定用户需求、类型、来源结构和上游版本；validate 重读 digest 并检查实际字段、限定条件编译及引用 token 表达式，不写 Metric、不执行 SQL。POST /api/v1/metrics/draft-context 供原编辑器显式准备；Agent gateway 使用同一 API。requireSnapshot 与 snapshot-explanation-context 只读指定不可变版本，不展开今日依赖。

UI 在原表单逐项采纳后走原 create/update expectedVersion 保存，保存回执提供精确 ID 的详情入口。原发布面板使用已登记 DATASET 引用且 metricVersion 与 active publication 一致时映射 canonical Consumption productKey；API/未知版本/未映射目标明确降级，不等同运行事实。
