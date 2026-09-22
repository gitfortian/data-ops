# Ticket 70：数据安全模块骨架与菜单权限接入

**目标**：新建 `yak-ops-business-security`，贯通 契约 → Maven 装配 → Flyway 链 → 菜单注册。

**交付**：
- 模块根契约文件集 README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW。
- `pom.xml` + 4 处装配（business 聚合、bom、boot 依赖）。
- `config`：`ConditionalOnSecurityPersistence` + `SecurityPersistenceConfiguration`（Flyway `db/migration/yak-security`，历史表 `flyway_schema_history_security`）。
- `V1__baseline_dsec.sql`。
- `SecurityErrorCode`（45001~）、`SecurityException`、`SecurityPermissionCode`。
- 菜单注册 `V2030__register_data_security_menu.sql`：组 `data-security`(sort 8) + `data-security-overview`。

**验收**：模块进入 reactor；`mvnw -o -pl ...-security test` 绿；菜单 SQL 幂等。
