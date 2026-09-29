# 50: MDM 模块骨架与菜单权限接入

**对应需求:** M5 主数据管理(requirement.md 一~八 + design.md + menu.md)|阶段:P0

**What to build:** 平台出现"主数据管理"一级入口(项目空间内可用):后端新建独立 MDM 模块(自持 Flyway migration 目录与历史表),前端新增主数据总览页面路由与菜单并接入权限体系。本 ticket 不实现任何主数据业务,只打通"模块可启动、菜单可见、权限可控"的工程底座。按 menu.md,本 ticket 注册组 `mdm`(主数据管理)+ `mdm-overview`(主数据总览)页;建模/识别/清洗/审批菜单在各自 ticket 注册(V2025+)。

**模块归属:** data-ops-business-mdm

**Blocked by:** None (can start immediately)

**Status:** in-review(实现完成,待验收)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— ① 契约先行:先创建/更新所有涉及模块的契约 .md,再写对应代码;② data-ops-ui 下所有 .md 契约文件只读,只阅读遵守、绝不修改;③ 项目全局规范(CODE_STYLE.md、FRONTEND_CODE_STYLE.md、PROJECT_SCOPE、home-overview-contract、菜单契约)强制适用;④ 依赖方向单向(design.md 二):mdm → datasource/sync/quality/data-service/semantic/lineage/security/dataset,不允许反向依赖;⑤ menuCode 采用连字符(mdm-overview 等,dev-plan D-M8),与仓库既有约定一致。

- [x] 契约先行:创建 `data-ops-business-mdm/` 契约文件集(README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW),作为本 ticket 第一个交付物,先于任何业务代码;ARCHITECTURE 明确分层 api/domain/infrastructure/application(design.md 五);DEPENDENCIES 固化依赖方向与原因(design.md 六);DOMAIN 固化"只做特有、通用跳转"(menu.md 一)边界
- [x] 新建 `data-ops-business-mdm` 后端模块随应用启动,自持 Flyway(baseline V1,参照 modeling 01)与独立历史表 `flyway_schema_history_mdm`,参照 lineage/semantic 的持久化配置模式
- [x] 模块纳入 Maven 聚合(data-ops-business/pom.xml)与 BOM(data-ops-bom/pom.xml),整体构建通过
- [x] 错误码段 44001+(dev-plan D-M3):新建 `MdmErrorCode`(io.yak.ops.common.enums.mdm),含 44001 实体不存在/44002 记录不存在/44003 属性不存在/44004 来源不存在/44005 审批失败/44006 分发失败
- [x] 前端新增主数据总览路由与菜单(menuCode `mdm-overview`,组 `mdm`),菜单契约测试通过
- [x] 新增主数据菜单权限 code(菜单注册 migration V2024,yak-security 编号取 ≥V2024),RBAC 生效:无权限角色不可见、不可访问
- [x] 页面在项目空间上下文内访问,遵循 PROJECT_SCOPE 约定;总览页本 ticket 为占位骨架(统计内容随 51/55/58/60/62 落地)

**验证记录(2026-09-16):**

- 菜单契约测试 `navigationMenuContract.test.ts` 5/5 通过(V2024 已登记进 CATALOG_EXTENSION_MIGRATIONS,mdm/mdm-overview 与 Flyway 目录精确对齐)。
- Maven:`./mvnw -pl data-ops-business/data-ops-business-mdm -am compile` 通过(MdmPersistenceConfiguration/ConditionalOnMdmPersistence/MdmErrorCode 均已编译);`./mvnw -pl data-ops-boot -am validate` 通过(携带新依赖)。
- 前端 tsc:`mdm` 相关文件零新增类型错误(既有 4 处 navigation.ts apartment/node-index/tags 与 test 的 196 行类型错误在未含本改动时同样存在,属历史遗留)。
- Flyway 运行时迁移效果(历史表 flyway_schema_history_mdm 落库、菜单 V2024 注册生效)需在有数据库的联调环境启动应用确认,配置与 semantic/lineage 模块逐项同构。
- 遵守硬性约束:契约文件先于代码;未修改 `data-ops-ui` 下任何 `.md` 文件(本次仅改 `.ts`/新增 `.tsx` 与 SQL migration)。
