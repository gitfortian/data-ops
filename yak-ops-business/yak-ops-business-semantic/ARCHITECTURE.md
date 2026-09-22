# Semantic Architecture

## 包结构

```
io.yak.ops.business.semantic
├── config/        ConditionalOnSemanticPersistence + SemanticPersistenceConfiguration(Flyway/MapperScan)
├── catalog/       标准目录生命周期:StandardKind/StandardStatus/Standard(域对象)/StandardCatalogService
├── exception/     SemanticException(BusinessException)
├── repository/    SemanticStandardRepository(接口)+ StandardRepositoryAdapter(MyBatis 适配)
├── dao/mapper/    SemanticStandardMapper(BaseMapper)
├── api/           SemanticStandardApi(请求/响应 record;后续 SPI 接口同包演进)
└── controller/v1/ SemanticStandardController + dto/ + vo/ + converter/
```

后续包(按 ticket 增量):`preset/`(31)、`domain/`(33~35 业务域/过程/字段集)、`binding/`(36)、`layer/`(37)、`spi/`(41/42 对外 SPI 实现)。

## 持久化

- 自持 Flyway:`classpath:db/migration/yak-semantic`,历史表 `flyway_schema_history_semantic`,baseline 0。
- 共享数据源:`@Import(BusinessDatabaseConfiguration.class)`(datasource 模块提供 yakBusinessDataSource / yakBusinessSqlSessionFactory / yakBusinessTransactionManager),与 modeling 同池。
- 事务:`@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO 位于 `yak-ops-common` 的 `io.yak.ops.common.bean.po.semantic`(平台惯例)。

## 分层规则

- Controller:参数校验注解 + 权限 + 项目上下文透传,**无业务规则**。
- Service:唯一规则归属地(校验、不变量、审计);操作域对象而非 PO。
- Repository:项目空间绑定(每次读写绑定 `CurrentProject`),live-row 语义,无业务规则。

## SPI 面(消费方边界,按 ticket 增量实现)

`api` 包对 modeling 暴露:StandardQueryApi(30 后随需;52 扩展 labels/existsCodeSet,MDM 属性引用校验与列表标签解析消费)、StandardCaptureApi(40)、StandardUsageApi(42)、StandardRecommendApi(41)、ProcessApi(34/35)、LayerConfigApi(37)。**modeling/mdm 只经这些接口消费,不直读本模块表。**

## 迁移所有权

| 表 | 迁移 | ticket |
| --- | --- | --- |
| yak_semantic_standard | V1 | 30 |
| (modeling 侧)yak_modeling_model_column.std_* 六列 | modeling V8 | 30 |
| yak_semantic_preset_template | V2 | 31 |
| 标准版本快照表(yak_semantic_standard_version) | V3 | 32 |
| 业务域(yak_semantic_domain) | V4 | 33 |
| 业务过程(yak_semantic_process) | V5 | 34 |
| 标准字段(yak_semantic_field)+过程字段引用(yak_semantic_process_field) | V6 | 35 |
| 过程源表关联(yak_semantic_process_source) | V7 | 36 |
| 数仓分层(yak_semantic_layer)+分层模板(yak_semantic_layer_template) | V8/V9 | 37 |

迁移合入后**不可再编辑**(对齐 modeling 纪律)。
