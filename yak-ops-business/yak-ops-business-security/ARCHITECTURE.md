# Data Security Architecture

## 包结构

```
io.yak.ops.business.security
├── config/         ConditionalOnSecurityPersistence + SecurityPersistenceConfiguration
│                   (@Import(BusinessDatabaseConfiguration) + @MapperScan + Flyway "yakDataSecurityFlyway")
├── api/            对外 SPI 与跨模块契约
│                   SecurityClassificationQueryApi / SecurityMaskingApi / SecurityAccessDecisionApi
│                   契约 record:ClassificationView / MaskingDirective / AccessDecision
├── application/    应用层服务(唯一规则归属地)
│                   SecurityLevelService / DataCategoryService / ClassificationService(impl SPI)
│                   DiscoveryService / AccessPolicyService / AccessDecisionService(impl SPI)
│                   MaskingService(impl SPI) / MaskingEngine(纯函数) / AccessLogService
│                   ComplianceService / SecurityOverviewService
├── domain/         领域 record:DiscoverableField / ComplianceRunResult / LevelCount / SecurityOverview
├── dao/mapper/     10 个 BaseMapper(Dsec*PO)
├── exception/      SecurityException(继承 BusinessException)
├── support/audit/  SecurityAudit(门面) + AuditTransactions(提交后落审计,fail-open)
└── controller/v1/  11 个 REST Controller(请求体用嵌套 record)
```

> PO/枚举/权限码位于 `yak-ops-common`(平台惯例):`bean.po.security`(10 个 `Dsec*PO`)、`enums.security.SecurityErrorCode`(45xxx 段)、`constant.security.SecurityPermissionCode`(`data-security:read/create/update/delete`)。

## 持久化

- 自持 Flyway:`classpath:db/migration/yak-security`,历史表 `flyway_schema_history_security`,bean `yakDataSecurityFlyway`(`initMethod="migrate"`),V1 基线(`SELECT 1`)+ V2 建 10 张 `yak_dsec_*` 表。
- 共享数据源:`@Import(BusinessDatabaseConfiguration.class)`(datasource 模块提供 yakBusinessDataSource / yakBusinessSqlSessionFactory / yakBusinessTransactionManager),与 modeling/mdm 同池。
- 事务:`@Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)`。
- 表清单:security_level / data_category / classification / discovery_rule / access_policy / masking_algorithm / masking_policy / access_log / compliance_rule / compliance_finding。

## 分层规则

- **Controller**:参数校验注解(`@Valid`) + `@RequiresPermission` + `@ProjectScope(PROJECT_REQUIRED)` + `CurrentUserProvider` 取操作人,无业务规则;返回 `Result<T>`/`PageData<T>`。
- **Service**:校验、不变量、引用阻断、审计的唯一归属地;直接以 `Dsec*PO` + BaseMapper 读写(务实,不额外堆 adapter/domain-record 层)。
- **api/SPI**:对下游只暴露接口与契约 record;ClassificationService/MaskingService/AccessDecisionService 实现之。
- **MaskingEngine**:纯函数无状态,便于单测与跨层复用。

## 迁移所有权(合入后不可再编辑)

| 表 | 迁移 | ticket |
| --- | --- | --- |
| (V1 基线,只立历史表) | V1__baseline_dsec.sql | 70 |
| 10 张 `yak_dsec_*` | V2__create_dsec_tables.sql | 70(建表),功能 71~79 |

菜单注册走框架 yak-security Flyway:`yak-ops-boot/.../yak-security/db/migration/V2030__register_data_security_menu.sql`(组 `data-security` sort_order 8 + 6 页 + 权限授予根角色)。

## SPI 面(消费方边界)

| 接口 | 方法 | 消费方 |
| --- | --- | --- |
| SecurityClassificationQueryApi | find/findMany/findByTable | modeling/metric/lineage |
| SecurityMaskingApi | resolve/mask | data-service/查询链路 |
| SecurityAccessDecisionApi | decide | 数据服务/开发/BI 鉴权前置 |

下游仅经这三接口消费,禁止直读 `yak_dsec_*` 表或 import `application`/`dao` 内部类型。
