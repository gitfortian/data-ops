# Lifecycle Architecture

## 包结构

```
io.yak.ops.business.lifecycle
├── config/      ConditionalOnLifecyclePersistence + LifecyclePersistenceConfiguration(Flyway/MapperScan)
├── policy/      TtlPolicyService(策略 CRUD/层默认初始化) + LayerTtlTemplate(预置模板,D1 覆盖语义)
├── binding/     ModelTtlBindingService(继承+覆盖解析,deriveState 状态机) + ModelTtlResolution
├── generate/    TtlStatementGenerator(纯函数,Doris/Paimon 语句) + TtlStatement
├── preview/     TtlPreviewService(classify 分区归类纯函数) + ConfirmTokenService(HMAC 令牌)
├── dispatch/    TtlDispatchService(下发+重试) + TtlSqlGateway(接口) + DataSourceTtlSqlGateway(实现)
├── monitor/     TtlMonitorService(summary/模型分页/重新下发)
├── stats/       StorageSnapshotService(日快照) + StorageStatsService(统计/趋势/成本/单价设置)
├── schedule/    LifecycleScheduleEngineBridge(按项目 alarm) + LifecycleTtlScheduleHandler(重试/快照)
├── support/     AuditTransactions 复用封装
├── exception/   LifecycleException + LifecycleExceptionHandler(47001~47012)
├── dao/mapper/  5 个 BaseMapper(policy/binding/dispatch_record/storage_snapshot/setting)
└── controller/v1/ 5 个 Controller + dto/LifecycleRequests
```

## 持久化

- 自持 Flyway：`classpath:db/migration/yak-lifecycle`，历史表 `flyway_schema_history_lifecycle`，bean `yakLifecycleFlyway`。
- 表：`yak_lc_policy` / `yak_lc_model_binding` / `yak_lc_dispatch_record` / `yak_lc_storage_snapshot` / `yak_lc_setting`。
- 共享数据源：`@Import(BusinessDatabaseConfiguration.class)`；事务 `@Transactional(transactionManager = "yakBusinessTransactionManager")`。
- PO/权限码/错误码位于 `data-ops-common`（`bean.po.lifecycle` / `constant.lifecycle` / `enums.lifecycle`）——平台惯例。
- 菜单注册在 data-ops-boot 的 yak-security 迁移 `V2031__register_lifecycle_menu.sql`。

## 关键设计

- **纯函数优先**：语句生成/分区归类/模板换算均为 static 纯函数，单测不打 Spring 上下文。
- **网关抽象**：`TtlSqlGateway`（available/query/execute）隔离 datasource SPI，测试可替换；执行仅允许 `writable=true` 语句。
- **调度**：不建全局 job；`LifecycleScheduleEngineBridge.ensureProjectAlarms` 在层默认初始化与首次下发时按项目登记 `retry-project-{id}`(30min) / `snapshot-project-{id}`(每日 02:00)。
- **Controller 层**：参数校验 + `@RequiresPermission` + 透传 operator，无业务规则；返回 service record 直接序列化。
