# Metadata Dependencies

## 本模块依赖（出向）

| 依赖 | 范围 | 原因 |
| --- | --- | --- |
| `data-ops-common` | 编译 | PO（`bean.po.metadata`）、错误码（`enums.metadata`）、权限码/枚举（`constant.metadata`）——平台惯例 |
| `data-security-spring-boot-starter` | 编译 | `Result`/`PagingData`/`@RequiresPermission`/`CurrentUserProvider` |
| `data-ops-core` | 编译 | `CurrentProject`（接口，注入使用）、`@ProjectScope` |
| `data-ops-business-datasource` | 编译（optional） | 基础设施 `BusinessDatabaseConfiguration`（共享数据源/开关） |
| `data-ops-plugins/…/DataSourceCatalog` | 编译（SPI） | **物理采集唯一入口**：`listDatabases/listSchemas/listTables/listColumns`，进程内调用（无连接器进程） |
| `data-ops-business-lineage` | 编译（optional） | `api/LineageRegistrationApi`（GONE 时撤销图节点）+ `LineageAssetType` 枚举（`type_def.lineage_asset_type` 映射校验） |
| `data-ops-business-lifecycle` | 编译（optional，只读） | `api` 包读 `yak_lc_storage_snapshot` 展示存储量（**本模块不建 mapper 查 `yak_lc_*`**） |
| `data-ops-business-semantic` | 编译（optional） | 分层/域字典（`layer_code` 回填与 facet 展示） |
| `data-ops-business-asset` | 编译（optional） | 标签字典复用（`label_code`） |
| `data-ops-spi` | 编译 | 实现 `SectionProvider`，由 Metadata 作为技术元数据事实的 owner 提供物理表/列详情 |
| 调度引擎 `data-schedule-api` | 编译 | 采集/对账 cron，本模块 handler 被回调；未装配时静默跳过 |
| 统计方言 `MetadataStatsProvider` | 模块内 SPI | MySQL/Doris 各自的行数/分区/最后 DDL；`supports()` 路由，返回 null 记 UNKNOWN |

**源域（modeling / semantic / metric）实现本模块 `api/EntityProvider` 并调用 `api/MetadataRegistrationApi`**——
两个方向都是"源域 → metadata.api"，**metadata 不 import 任何源域内部包**。

## 被依赖（入向）

| 消费方 | 通道 | 内容 |
| --- | --- | --- |
| modeling（符合性对账） | `api/MetadataQueryApi` | `listPhysicalColumns` / `findPhysicalTable`（判定规则留在 modeling，复用其 `StandardFieldMatcher`） |
| quality（表/列选择器） | `api/MetadataQueryApi` | 已采集清单，秒开；未覆盖时回落实时 catalog 并标注来源 |
| asset（TABLE provider） | 本模块实现 `asset/api/AssetProvider` | `AssetSourceType.TABLE` + `MetadataTableAssetProvider`，assetKey 与目录同源 |
| asset（技术元数据分区） | 本模块实现 `spi/section/SectionProvider` | `MetadataAssetSectionProvider` 读取 `MetadataQueryApi` 的物理表与列事实；Asset 只聚合并渲染 |
| lineage | 本模块**不调**其内部包 | 共表：lineage 继续 own `asset_key/asset_type/parent_asset_id/properties` |
| 前端目录/详情/筛选 | `GET /api/v1/metadata/types` | 元模型自省 = 渲染的唯一来源（新类型接入不改 `.tsx`） |

## 禁止

- 禁止 import 源域内部包（`business.modeling.catalog.*` 等）——只 `api/` SPI。现状反例：`modeling/governance/StandardFieldMatcher.java:3`。
- 禁止写 lineage 的列：`properties` 永不读写；`asset_type`/`parent_asset_id`/`source_*` INSERT 之后不刷；`asset_key` 只校验不代拼。
- 禁止自建存储量采集（无 `SHOW DATA`、无 `information_schema` 字节量查询）——复用 lifecycle 快照（grep 守护）。
- 禁止把源域业务内容抄进目录（列定义、指标公式、标准字典项）。
- 禁止新增第二张资产表或血缘边表；关系只在 `yak_metadata_relation`。
- 禁止无界查询：游标 ≤500、概览 ≤8 次、列表一律分页。
- 禁止引入 ES/Flowable/JSON Schema 校验器/连接器进程（升级阈值与判据见 plan §4.4、§6.3）。
- 前端 `data-ops-ui/**.md` 只读；新菜单 menuCode 必须过 `navigationMenuContract.test.ts`。
