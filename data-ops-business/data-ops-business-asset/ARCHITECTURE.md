# Asset Architecture

## 包结构

```
io.yak.ops.business.asset
├── config/       ConditionalOnAssetPersistence + AssetPersistenceConfiguration(Flyway/MapperScan)
├── api/          AssetProvider(接口,源域实现) · AssetDescriptor/AssetCursorQuery/AssetPage · AssetCatalogApi(对外提供)
│                 · AssetSectionResult(SectionContract 五态响应)
├── application/  AssetAppService(台账/状态机) · AssetDiscoverService(搜索/详情聚合) · AssetOverviewService(驾驶舱)
├── reconcile/    AssetReconcileService(编排+互斥锁) · AssetProviderRegistry(多 bean 收集) · ChangeRecorder
├── catalog/      DirectoryService(树/物化路径/模板) · AssignRuleService(匹配+dry-run) · TagService
├── health/       HealthScorer(纯函数) · HealthRecomputeService(定时入口)
├── stat/         ViewRecorder(限流写+聚合) · HealthSnapshotJob
├── schedule/     AssetScheduleEngineBridge + AssetScheduleHandler(对账/健康度/快照/流水清理)
├── support/      AuditTransactions 复用封装
├── exception/    AssetException + AssetExceptionHandler(48001~48016)
├── dao/mapper/   9 个 BaseMapper(item/directory/tag/tag_rel/assign_rule/change_record/view_record/health_snapshot/setting)
└── controller/v1/ AssetController · AssetDirectoryController · AssetRuleController · AssetTagController
                   · AssetInventoryController · AssetOverviewController + dto/AssetRequests
```

## 持久化

- 自持 Flyway：`classpath:db/migration/yak-asset`，历史表 `flyway_schema_history_asset`，bean `yakAssetFlyway`。
- 表：`yak_asset_item` / `_directory` / `_tag` / `_tag_rel` / `_assign_rule` / `_change_record` / `_view_record` / `_health_snapshot` / `_setting`。
- 共享数据源：Boot 的 `config.persistence.BusinessDatabaseConfiguration` 应用装配；事务 `@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO 由本模块 `dao.model` 拥有；权限码、错误码和枚举保留 common 的稳定共享契约。
- 菜单注册在 data-ops-boot 的 yak-security 迁移 `V2__boot_security_baseline.sql（Source: V2032__register_data_asset_menu.sql）`。

## 关键设计

F-004 的 Data Service AssetProvider 在 Consumption 的 source adapter 注册，
只读取 DataServiceReader 的有界 Project cursor 和 source-owned identity key。
Asset 继续通过 AssetProvider SPI 获取事实，不反向依赖 Consumption 或 Data Service。

- **纯函数优先**：HealthScorer、content_hash 判定、规则匹配（AND/优先级/通配）均为 static 纯函数，单测不打 Spring 上下文。
- **SPI 纪律**：源域实现只读本域 Service；asset 只 import 各域 `api/` 包；provider 单个失败不影响其余（分区容错）。
- **Asset Section Query**：`GET /api/v1/assets/{id}/sections/{sectionType}` 只读取请求分区；跨域事实标记 Owner / Provenance，状态严格遵循 SPI 五态；权限由 Asset 与事实 Owner 的 read 权限共同约束。
- **Section Provider**：Metadata / Quality / Security / Lifecycle 事实由其 Owner 模块实现 `data-ops-spi` `SectionProvider`；Asset 解析并透传 source coordinates 与 return context，不保存分区真相。Provider 日志只记录 identity、类型、状态、耗时和异常类别。
- **Usage**：Asset page activity、Lineage structural references 与 consumer-domain business consumption 分别声明 Owner / 范围 / 状态；缺少稳定 consumer read API 时返回 UNAVAILABLE，不当作零消费。
- **调度**：`YakScheduleNamespaces.DATA_ASSET`；每日 02:00 对账、03:00 健康度重算+浏览聚合+流水清理、04:00 快照（P2）。
- **Controller 层**：参数校验 + `@RequiresPermission` + 透传 operator，无业务规则；返回 service record 直接序列化。

手动对账在异步线程内通过 `ProjectContextScope` 恢复请求已验证的 Project ID，
与调度入口使用同一上下文机制；源域 Reader 仍校验 `CurrentProject`，不开放跨项目读取。


## F-009 治理消费者只读入口

api.AssetGovernanceQueryApi 由 application.AssetGovernanceQueryAdapter 实现：台账搜索/require 和分区查询只复用现有 AssetAppService / AssetDiscoverService；每次检查 data-asset:read。AssetSectionProjector 是 HTTP 与消费者共用投影，分区 permission/provider/适用矩阵只有 DiscoverService 一个判断入口。AssetFact 不含 accessUri / 连接配置，调用者不得把台账事实当源域质量或安全事实。

## F-010 建议边界

Agent 仅消费源域 api 包的授权只读契约；Quality monitor 的 SuggestionQueryAdapter 进入既有 Reader/Policy 和 Quality-owned Catalog Gateway，DefinitionFingerprint 属于 domain；Asset 条件更新仍在 AssetAppService。候选仅存在本轮上下文与官方消息历史，不新增业务建议表。
