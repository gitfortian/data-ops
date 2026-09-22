# Ticket 110：元数据模块骨架、Flyway 与契约文件集

**对应需求：** 元数据中心（工程底座） | **阶段：** P0 | **模块：** yak-ops-business-metadata（新建）

**What to build：** 新模块随应用可编译可启动：自持 Flyway、错误码段、权限码、调度 namespace、异常出口。根目录先落**契约文件集 6 份**（契约 diff 先于代码 diff，plan §0.1）。本票不含任何采集/检索业务。

**Blocked by：** 无

**硬性约束：** 遵守 [plan.md《0. 硬性开发约束》](../plan.md)——契约先行；跨模块只走 `api` 包；写别人的表=死罪；project_id 只取服务端可信上下文、不建物理外键；分页一律 `Result<PagingData<…>>`。

**验收清单**
- [ ] 契约文件集：`yak-ops-business-metadata/{README,DOMAIN,ARCHITECTURE,DEPENDENCIES,REQUIREMENTS,REVIEW}.md`（参照 asset 同名 6 文件；`ARCHITECTURE.md` 必须含共表列 steward 清单，见 ticket 133）
- [ ] `pom.xml` + 三处装配：`yak-ops-business/pom.xml` `<modules>`、BOM `dependencyManagement`、`yak-ops-boot` dependency
- [ ] `MetadataPersistenceConfiguration`：Flyway `classpath:db/migration/yak-metadata`、历史表 `flyway_schema_history_metadata`、bean `yakMetadataFlyway`、`@MapperScan(...dao.mapper, sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")`（模板 `asset/config/AssetPersistenceConfiguration.java:25-35`）+ `ConditionalOnMetadataPersistence`
- [ ] `MetadataException` + `MetadataExceptionHandler`（`@RestControllerAdvice(basePackages = "io.yak.ops.business.metadata.controller")`，模板 `asset/exception/AssetExceptionHandler.java`）
- [ ] common：`enums/metadata/MetadataErrorCode`（**49001~49099**）、`constant/metadata/MetadataPermissionCode`（`data-metadata:read/create/update/delete`）
- [ ] `YakScheduleNamespaces.DATA_METADATA = "yak-ops-metadata"`（现有 5 个常量中无此项）
- [ ] **真机验证 49001 能透传**：打一个必失败请求确认没被兜成 999（plan §9 T3——`basePackages` 与 controller 实际包不符是本仓库真实事故形态）
- [ ] `./mvnw -q -o -pl yak-ops-business/yak-ops-business-metadata test` 绿；`-pl yak-ops-common install -DskipTests` 先跑（plan §9 T7）

**验证边界：** 新 Java 类与新迁移**不由本 Agent 重启验证**（plan §9 T11），启动期表现待用户 IntelliJ 重启后确认，不得写成 verified。
