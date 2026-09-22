# MDM Architecture

## 包结构(design.md 五)

```
io.yak.ops.business.mdm
├── config/        ConditionalOnMdmPersistence + MdmPersistenceConfiguration(Flyway/MapperScan)
├── api/           对外 API(EntityApi/RecordApi/CollectApi/CleanApi/ApprovalApi/DistributeApi/ServiceApi/AnalysisApi;请求/响应 record)
├── application/   应用层服务(EntityService/RecordService/CollectService/CleanService/ApprovalService/DistributeService/AnalysisService)
├── domain/        领域层
│   ├── entity/    主数据实体
│   ├── attribute/ 主数据属性
│   ├── record/    主数据记录
│   ├── source/    主数据来源(识别 + 采集配置)
│   ├── clean/     清洗规则(去重/标准化/补全)
│   ├── change/    主数据变更与审批
│   └── distribution/ 主数据分发
├── infrastructure/ 基础设施层
│   ├── repository/ 仓储(接口 + MyBatis 适配)
│   ├── datasource/ 数据源适配(复用 datasource)
│   ├── sync/       同步适配(复用 sync)
│   ├── quality/    质量适配(复用 quality)
│   ├── data-service/ API 适配(复用 data-service)
│   ├── semantic/   标准适配(复用 semantic)
│   ├── lineage/    血缘适配(复用 lineage)
│   └── security/   权限适配(复用 security)
├── dao/mapper/    Mapper(BaseMapper)
└── controller/v1/ Controller + dto/ + vo/ + converter/
```

> 后续包按 ticket 增量落:`domain/entity`(51)、`domain/attribute`(52)、`domain/source`(53/54)、`domain/record`(55)、`domain/clean`(56/57)、`domain/distribution`(58)、`application/Service`(59/61/62)、`domain/change`(60)、`infrastructure/*`(随依赖 ticket)。

## 持久化

- 自持 Flyway:`classpath:db/migration/yak-mdm`,历史表 `flyway_schema_history_mdm`,baseline 0。
- 共享数据源:`@Import(BusinessDatabaseConfiguration.class)`(datasource 模块提供 yakBusinessDataSource / yakBusinessSqlSessionFactory / yakBusinessTransactionManager),与 modeling/semantic 同池。
- 事务:`@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO 位于 `yak-ops-common` 的 `io.yak.ops.common.bean.po.mdm`(平台惯例)。

## 分层规则

- Controller:参数校验注解 + 权限 + 项目上下文透传,**无业务规则**。
- Application/Service:唯一规则归属地(校验、不变量、审计);操作域对象而非 PO。
- Repository:项目空间绑定(每次读写绑定 `CurrentProject`),live-row 语义,无业务规则。
- Infrastructure 适配层:对 datasource/sync/quality/data-service/semantic/lineage/security/dataset 的**唯一** import 点,业务层不直接依赖被复用模块内部类型。

## 迁移所有权(规划,合入后不可再编辑)

| 表 | 迁移 | ticket |
| --- | --- | --- |
| (V1 基线,只立历史表,`SELECT 1`) | V1 | 50 |
| yak_mdm_entity | V2 | 51 |
| yak_mdm_attribute | V3 | 52 |
| yak_mdm_source | V4 | 53 |
| yak_mdm_record | V6 | 55 |
| yak_mdm_clean_rule(DEDUP) | V7 | 56 |
| yak_mdm_merge_log | V8 | 56 |
| (57 复用 V7 表,rule_type 已含 STANDARDIZE/COMPLETE,无新迁移) | — | 57 |
| yak_mdm_distribution | V9 | 58 |
| yak_mdm_subscription | V10 | 59 |
| yak_mdm_change | V11 | 60 |
| (61/62 复用现有表,无新迁移,仅服务端聚合) | — | 61/62 |

## 菜单注册

按 menu.md,只注册 5 个特有菜单(组 `mdm` + 总览/建模/识别/清洗/审批),走 yak-security Flyway(V2024 起);采集/服务/治理/分析为隐藏路由,无独立 menuCode。

## SPI 面(消费方边界,规划)

`api` 包对 modeling 暴露:EntityQueryApi(主数据实体与记录查询)、MdmServiceApi(对外查询,59)。modeling 只经这些接口消费,不直读本模块表。
