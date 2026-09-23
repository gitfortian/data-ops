# Asset Dependencies

## 本模块依赖（出向）

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `yak-ops-common` | 编译 | PO（`bean.po.asset`）、权限码（`constant.asset`）、错误码/枚举（`enums.asset`）——平台惯例 |
| `yak-ops-spi` | 编译 | `SectionContract` / `SectionStatus` 与跨域只读分区数据契约 |
| `yak-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`@RequiresPermission`/`CurrentUserProvider` |
| `yak-ops-core` | 编译 | `CurrentProject`（接口，注入使用）、`@ProjectScope` |
| `yak-ops-business-datasource` | 编译（optional） | 基础设施 `BusinessDatabaseConfiguration`（数据源开关/Flyway 共享） |
| `yak-ops-business-semantic` | 编译 | **仅经 `api` 包 SPI**（`LayerConfigApi` 分层/域字典，目录模板与健康度输入） |
| `yak-ops-business-audit` | 编译 | `BusinessAuditService` 审计门面（fail-open），事件 `ASSET_*` |
| `yak-ops-business-security` | 编译（ticket 96+） | **仅 `SecurityClassificationQueryApi`**（定级快照刷新与详情安全块） |
| `yak-ops-business-lineage` | 编译（ticket 97） | **仅 `LineageQueryService` 只读**（详情血缘块；本模块不回写血缘） |
| `yak-ops-business-quality` | 编译（optional） | Asset 仅通过 `yak-ops-spi` `SectionProvider` 消费 Quality-owned Quality Section；不调用 Quality 内部 Reader/Repository/DAO |
| lifecycle SPI | ticket 98 | TTL 摘要；未就绪时对应分区降级 UNAVAILABLE，不阻塞 |
| `yak-ops-business-approval` | 编译（M2-5） | 上架审批：发起走 `ApprovalApi`、终态回调实现 `ApprovalFlowHandler`（同 modeling/mdm 先例）；状态条消费经 `ObjectProvider` 优雅缺省。handler 只向下依赖 `AssetLifecycleService`，不回依赖 `ApprovalApi`（防 ApprovalService→Registry→handler bean 循环） |
| 调度引擎（`YakScheduleGateway`） | 编译 | 每日对账/健康度闹钟，本模块 handler 被回调 |

源域（modeling/metric/dataset/dashboard/task-catalog）**实现本模块 `api/AssetProvider` 并注册为 Spring Bean**——依赖方向是"源域 → asset 接口"，asset 不 import 源域内部包。

## 被依赖（入向）

| 消费方 | 通道 | 内容 |
| --- | --- | --- |
| modeling/metric/dataset/dashboard/task-catalog | `api/AssetProvider` 接口定义 | 实现只读 provider |
| home / data-service（后续） | `AssetCatalogApi` | 只读摘要 |

## 禁止

- 禁止 import 任何源域内部实现（`business.modeling.catalog.*` 等）——仅 `api/` 包 SPI。
- 禁止写 `yak_metadata_asset`（D6）与任何源域表；本模块只拥有 `yak_asset_*`。
- 禁止复制存储业务事实作为读取源（D1）——快照列仅供过滤，详情永远实时读源域。
- 禁止无界查询：概览/排序固定查询次数 + LIMIT（D11）。
