# Ticket 06：数据源变更/删除对外事件（P2）

**对应需求：** 数据源缺失能力盘点 §6 第 6 行 | **优先级：** P2 | **模块：** yak-ops-business-datasource

**What to build：** `DataSourceChangedEvent`（现为 `record(Long dataSourceId)`）只在模块内被 catalog 缓存监听器消费，下游对「源已变更/已删除」无感知。将其升级为对外契约事件。**不做**下游强制改造，只把契约立起来。

**设计：**
- 事件 enrich：`DataSourceChangedEvent(Long dataSourceId, String dbType, String name, ChangeType changeType)`，`ChangeType` 枚举 CREATED/UPDATED/DELETED（内嵌或独立 enum）。事件类挪到 `io.yak.ops.business.datasource.api` 包（全下游模块已依赖数据源模块，零 pom 改动，与 Ticket 01 的 api 包同一逻辑）。
- 发布：`DataSourceManager` create/update/delete 三命令均发布（现仅 update/delete 发；create 补上），保持事务内 `publishEvent` + 消费侧 `@TransactionalEventListener(AFTER_COMMIT)` 模式（模板 `DataSourceCatalogCacheInvalidationListener.java:20-29`）。
- 既有模块内监听器改签名适配。
- 契约文档：在数据源模块 `REQUIREMENTS.md`/api 包 Javadoc 写明「跨模块订阅姿势：@TransactionalEventListener(phase=AFTER_COMMIT)，勿用普通 @EventListener（会看到回滚前状态）」。

**验收清单**
- [x] 事件 record 扩展 + ChangeType；移包后原 import 点全部更新 —— `api/DataSourceChangedEvent(dataSourceId, dbType, name, changeType)`；`dbType` 用共享枚举 `DataSourceDbType` 而非票面写的 `String`（下游无需再解析字符串，且枚举已在 common，零额外依赖）；全仓 Java 引用点仅数据源模块内 4 个文件，已全部改到新包
- [x] create/update/delete 三处发布 —— `DataSourceManager.publishChanged(...)` 统一收口；创建能拿到 `dataSourceId` 依赖 `DataSourceRepositoryAdapter.insert` 把数据库主键回填到聚合（新增 `DataSourceDefinition.assignId`，语义同 `assignProject`；DigitalScreen 模块已有同样的「insert 后回读主键」先例）
- [x] catalog 缓存监听器适配（DELETED→invalidate、UPDATED→invalidate、CREATED 无操作）
- [x] 模块单测更新 + `mvn test` 绿 —— `./mvnw -o -pl yak-ops-business/yak-ops-business-datasource test`：Tests run 151, Failures 0, Errors 0；新增/改判：create 发布 CREATED、delete 发布 DELETED、CREATED 不清缓存、主键回填契约
- [x] 契约文档 + 架构护栏同步 —— `api/package-info.java` 与 `REQUIREMENTS.md`「对外事件契约」写明「只用 `@TransactionalEventListener(AFTER_COMMIT)`，普通 `@EventListener` 会看到回滚前状态」；`DEPENDENCIES.md` 矩阵补 `api` 行并把 `catalog -> api` 记为合法 edge（`DataSourceDependencyBoundaryTest` 同步放宽）

**Blocked by：** Ticket 01（同文件 `DataSourceManager` 改动，先 01 后 06 避免冲突）。

**遗留：** 下游（metadata/sync/质量/资产）尚无订阅者——本票只立契约，按 §6 结论「不做下游强制改造」；接入姿势见 `REQUIREMENTS.md`。
