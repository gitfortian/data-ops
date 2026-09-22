# Ticket 80：生命周期模块骨架与菜单权限接入

**对应需求：** 数据生命周期（工程底座） | **阶段：** P1 | **模块：** yak-ops-business-lifecycle（新建）

**What to build：** 平台出现"数据生命周期"一级菜单组（策略管理/TTL 监控/存储统计三个页面路由可达但内容为空态）。后端新模块随应用启动：自持 Flyway、错误码、权限、异常出口。本票不含任何 TTL 业务。

**Blocked by：** 无

**硬性约束：** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md)——契约先行（契约文件集是本票第一个交付物）；前端契约只读；project_id 只取服务端可信上下文；不建物理外键；菜单注册取 yak-security 链 V2031；错误码 47001+。

**验收清单**
- [ ] 契约文件集：`yak-ops-business-lifecycle/{README,DOMAIN,ARCHITECTURE,DEPENDENCIES,REQUIREMENTS,REVIEW}.md`
- [ ] `pom.xml` + 三处装配：business `<modules>`、bom dependencyManagement、boot dependency
- [ ] `LifecyclePersistenceConfiguration`：Flyway `classpath:db/migration/yak-lifecycle`，历史表 `flyway_schema_history_lifecycle`，bean `yakLifecycleFlyway`，`@MapperScan(...dao.mapper, sqlSessionFactoryRef=yakBusinessSqlSessionFactory)`
- [ ] `V1__create_lifecycle_tables.sql`：`yak_lc_policy`、`yak_lc_model_binding`、`yak_lc_dispatch_record`、`yak_lc_storage_snapshot`、`yak_lc_setting`（按 design.md §1）
- [ ] common：`LifecycleErrorCode(47001~47010)`、`LifecyclePermissionCode`、枚举 `TtlGranularity/TtlDispatchStatus/TtlModelState/TtlPolicyScopeType`、5 个 PO+Mapper
- [ ] `LifecycleException` + `LifecycleExceptionHandler`（@RestControllerAdvice, basePackages=lifecycle.controller，模板=MetricExceptionHandler）
- [ ] 菜单注册 `V2031__register_data_lifecycle_menu.sql`（组+3 页+4 权限+root 授权，幂等，模板=V2029）
- [ ] 前端：securityMenuCodes + navigation 组/3 路由 + `./data-lifecycle/{policy,monitor,storage}` 空态页；`navigationMenuContract.test.ts` 登记 V2031 通过
- [ ] `./mvnw -o -q -pl yak-ops-business/yak-ops-business-lifecycle -am compile` 绿；tsc 不超 199 基线
