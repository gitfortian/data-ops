# Lifecycle Dependencies

## 本模块依赖（出向）

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `yak-ops-common` | 编译 | PO（`bean.po.lifecycle`）、权限码（`constant.lifecycle`）、错误码（`enums.lifecycle`）——平台惯例 |
| `yak-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`@RequiresPermission`/`CurrentUserProvider` |
| `yak-ops-core` | 编译 | `CurrentProject`（接口，注入使用）、`@ProjectScope` |
| `yak-ops-business-datasource` | 编译（optional） | 基础设施 `BusinessDatabaseConfiguration` + SPI `DataSourceExecutionProvider`（SHOW PARTITIONS / ALTER 执行唯一通道） |
| `yak-ops-business-modeling` | 编译 | **仅经 `api` 包 SPI**（`ModelTtlQueryApi` 只读模型清单：表名/方言/分层/分区） |
| `yak-ops-business-semantic` | 编译 | **仅经 `api` 包 SPI**（`LayerConfigApi.listLayers/resolveByCode` 分层配置；`lifecycle_days` 兜底） |
| `yak-ops-business-audit` | 编译 | `BusinessAuditService` 审计门面（fail-open），事件 `LIFECYCLE_*` |
| 调度引擎（`YakScheduleGateway`） | 编译 | 按项目登记重试/快照 alarm，本模块 handler 被回调 |
| `yak-ops-spi` | 编译 | 实现 Lifecycle-owned `SectionProvider` / `SectionContract`，向 Asset 提供只读 TTL 摘要 |

## 被依赖（入向）

Asset 经 `yak-ops-spi` 消费本模块提供的 Lifecycle Section。TTL 事实仍由 Lifecycle 拥有；SPI 不引入 Asset 模块依赖或第二份真相。

## 禁止

- 禁止 import `io.yak.ops.business.modeling.*` / `semantic.*` 的内部实现——仅经 `api` 包 SPI。
- 禁止绕过 `TtlSqlGateway` 直连 JDBC 或自建 DataSource。
- 禁止在 `writable=false` 语句上下发（网关双保险拒绝）。
- dao/preview/dispatch 等内部类型不出现在 `SectionProvider` 对外契约。
