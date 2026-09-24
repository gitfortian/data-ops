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
- 共享数据源：`@Import(BusinessDatabaseConfiguration.class)`；事务 `@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO/权限码/错误码/枚举位于 `yak-ops-common`（`bean.po.asset` / `constant.asset` / `enums.asset`）——平台惯例。
- 菜单注册在 yak-ops-boot 的 yak-security 迁移 `V2032__register_data_asset_menu.sql`。

## 关键设计

- **纯函数优先**：HealthScorer、content_hash 判定、规则匹配（AND/优先级/通配）均为 static 纯函数，单测不打 Spring 上下文。
- **SPI 纪律**：源域实现只读本域 Service；asset 只 import 各域 `api/` 包；provider 单个失败不影响其余（分区容错）。
- **Asset Section Query**：`GET /api/v1/assets/{id}/sections/{sectionType}` 只读取请求分区；跨域事实标记 Owner / Provenance，状态严格遵循 SPI 五态；权限由 Asset 与事实 Owner 的 read 权限共同约束。
- **Section Provider**：Metadata / Quality / Security / Lifecycle 事实由其 Owner 模块实现 `yak-ops-spi` `SectionProvider`；Asset 解析并透传 source coordinates 与 return context，不保存分区真相。Provider 日志只记录 identity、类型、状态、耗时和异常类别。
- **Usage**：Asset page activity、Lineage structural references 与 consumer-domain business consumption 分别声明 Owner / 范围 / 状态；缺少稳定 consumer read API 时返回 UNAVAILABLE，不当作零消费。
- **调度**：`YakScheduleNamespaces.DATA_ASSET`；每日 02:00 对账、03:00 健康度重算+浏览聚合+流水清理、04:00 快照（P2）。
- **Controller 层**：参数校验 + `@RequiresPermission` + 透传 operator，无业务规则；返回 service record 直接序列化。
