# Metric Architecture

## 包结构

```
io.yak.ops.business.metric
├── config/        ConditionalOnMetricPersistence + MetricPersistenceConfiguration(Flyway/MapperScan)
├── catalog/       Metric/MetricStatus/MetricType(域对象)/MetricCatalogService
├── exception/     MetricException(BusinessException)
├── repository/    MetricRepository(接口)+ MetricRepositoryAdapter(MyBatis 适配)
├── dao/mapper/    MetricMapper(BaseMapper)
├── api/           MetricQueryApi/MetricUsageApi/MetricLineageApi(SPI 接口;请求/响应 record)
├── usage/         MetricUsageService + MetricAssetUsageSectionProvider（Metric-owned 统计与 Asset 只读投影）
└── controller/v1/ MetricController + dto/ + vo/ + converter/
```

后续包（按 ticket 增量）：`tag/`（49 标签）、`version/`（50 版本）、`lineage/`（51 血缘登记）、`impact/`（53 影响分析）、`service/`（54 元数据 API）。

## 持久化

- 自持 Flyway：`classpath:db/migration/yak-metric`，历史表 `flyway_schema_history_metric`，baseline 0。
- 共享数据源：`@Import(BusinessDatabaseConfiguration.class)`（datasource 模块提供 yakBusinessDataSource / yakBusinessSqlSessionFactory / yakBusinessTransactionManager），与 semantic/modeling 同池。
- 事务：`@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO 位于 `yak-ops-common` 的 `io.yak.ops.common.bean.po.metric`（平台惯例）。

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

迁移合入后**不可再编辑**（对齐 modeling/semantic 纪律）。
