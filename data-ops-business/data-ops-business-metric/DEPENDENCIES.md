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
