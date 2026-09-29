# Ticket 01：数据源删除/变更引用守卫（P0）

**对应需求：** 数据源模块缺失能力盘点（docs/v1/modules/数据源-现状与能力分析.md §6 第 1 行） | **优先级：** P0 | **模块：** data-ops-business-datasource + 各下游模块

**What to build：** 删除数据源前先跨模块查询「谁在引用这个 ID」，被引用则拒绝删除并在错误信息里返回引用清单（模块 + 条数）。新增 `GET /api/v1/data-source/{id}/references` 只读接口供前端主动展示。

**机制（复用仓内既有反向 SPI 模式，模板 `semantic/api/LayerUsageReader` + `modeling/api/ModelingLayerUsageReader`）：**
- 新包 `io.yak.ops.business.datasource.api`：`DataSourceReferenceProvider`（`String moduleName()` + `long countReferences(Long dataSourceId)`），接口放数据源模块本体——全部下游模块 pom 已依赖它，**零 pom 改动**。
- `DataSourceManager.delete`（现 L132-158，零校验）在 `repository.delete` 前注入 `ObjectProvider<DataSourceReferenceProvider>` 流式聚合；非零则抛 `DataSourceException(DataSourceErrorCode.DATASOURCE_REFERENCED)`（新错误码 **41014**，消息含「模块A n 项；模块B m 项」，参照 `SemanticLayerService.java:219-225` 计数入消息的模板）。
- 新 `GET /{id}/references` → `DataSourceReferencesVO { List<ModuleReference{moduleName,count}> }`，READ 权限，不阻断。

**下游 Provider 落点（列已核实，均为 BIGINT/字符串直查）：**
- sync-offline：`yak_offline_job_definition.source_datasource_id/sink_datasource_id`
- quality：`yak_quality_table_asset.data_source_id`、`yak_quality_monitor.data_source_id`（execution 是历史快照，不算引用）
- modeling：`yak_modeling_model.source_datasource_id`、`yak_modeling_column_mapping.source_datasource_id`
- mdm：`yak_mdm_source.datasource_id`、`yak_mdm_collect_link.datasource_id`
- dataset：`yak_dataset.draft_data_source_id`、`yak_dataset_version.data_source_id`（VARCHAR，用 `String.valueOf(id)` 查）
- data-service：`yak_ops_data_service_api.data_source_id`
- metadata：`yak_md_collect_job.data_source_id`
- semantic：`yak_semantic_process_source.datasource_id`、`yak_semantic_layer.datasource_id`

**已知边界（本票不做，票尾记录）：** realtime/job/workflow/development 的引用嵌在 spec_json/config_json LONGTEXT 里，无列可查；LIKE 扫描易假阳性，留作后续票。`yak_ops_sql_execution`/lifecycle 派活记录是历史事实，不阻断。

**验收清单**（2026-09-22 后端全部落地，provider 测试 8/8 绿）
- [x] api 包接口 + `DataSourceErrorCode.DATASOURCE_REFERENCED(41014,…)`
- [x] delete 前置聚合校验；错误消息含模块+条数清单（引用检查失败同样拦删除，QUERY_FAILED 包装）
- [x] `GET /{id}/references` 端点（READ 权限、PROJECT_REQUIRED 类级注解自然继承）
- [x] 8 个下游模块各出 1 个 `@Component` Provider（QueryWrapper selectCount，零 pom 改动）
- [x] 单测：Manager 守卫 3 例 + references 清单 1 例 + 每 Provider 至少 1 例；架构矩阵加 api 桶
- [ ] 前端：删除失败时引用清单经全局错误通知透出（`businessErrorMode reject` 链路已具备，无需改造；如消息过长在 DataSourceCard 删除确认文案处补提示）
- [x] `./mvnw -q -o -pl data-ops-business/data-ops-business-datasource -am test` 绿（common 先 `install -DskipTests`，见项目记忆）

**验证边界：** 新 Java 类不由 Agent 重启验证；真机删除拦截待用户 IntelliJ 重启后确认。
