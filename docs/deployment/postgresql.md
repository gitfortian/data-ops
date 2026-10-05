# PostgreSQL 新库部署

平台管理员可使用 PostgreSQL 16 或更高版本部署空库。业务库、Yak Security 权限库和 Quartz 调度存储均支持 PostgreSQL；原有 MySQL 部署继续使用原来的配置与迁移链。此方式不搬迁已有 MySQL 数据。

## 部署与首次登录

1. 创建 UTF-8 数据库和专用账号，使该账号拥有目标 schema。业务库默认使用 `public`。Flyway 会创建表、索引、函数和触发器；数据库中须可安装 `citext` 和 `pg_trgm` 扩展。若运行账号不能安装扩展，由数据库管理员事先在目标数据库执行：

   ```sql
   CREATE EXTENSION IF NOT EXISTS citext;
   CREATE EXTENSION IF NOT EXISTS pg_trgm;
   ```

2. 使用下列环境变量启动后端。账号和密码由部署者提供：

   ```text
   SPRING_PROFILES_ACTIVE=postgresql
   YAK_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/data_ops
   YAK_DATABASE_USERNAME=data_ops
   YAK_DATABASE_PASSWORD=<数据库密码>
   YAK_SECURITY_BOOTSTRAP_USERNAME=root
   YAK_SECURITY_BOOTSTRAP_PASSWORD=<首次管理员密码>
   YAK_OPS_DATASOURCE_MASTER_KEY=<固定保存的随机凭证加密密钥>
   ```

   ```bash
   java -jar data-ops-boot/target/data-ops-boot-*.jar --spring.profiles.active=postgresql
   ```

3. 启动时自动执行各模块 PostgreSQL Flyway 迁移，创建权限菜单、首次管理员和默认项目空间。使用首次管理员登录，通过项目空间进入原有业务流程。初始化完成后关闭 `yak.security.bootstrap.enabled`；后续启动不会覆盖既有用户。

业务和权限默认共用一个数据库。如需独立权限库，在另外的空库中提供 `YAK_SECURITY_DATASOURCE_URL`、`YAK_SECURITY_DATASOURCE_USERNAME` 和 `YAK_SECURITY_DATASOURCE_PASSWORD`。两个数据源的事务边界与原部署一致。

Docker 镜像使用仓库构建出的发布包。先按 [README 的源码构建步骤](../../README.md#build-from-source)安装前端依赖、运行 `yarn build` 和 Maven reactor，确认 `data-ops-dist/target/yak-ops-*.tar.gz` 已生成。然后复制 `.env.postgresql.example` 为 `.env.postgresql`，填写密码和加密密钥后运行：

```bash
docker compose --env-file .env.postgresql -f compose.postgresql.yaml up --build -d
```

## 存储约束

- 模块继续拥有原有业务真相与独立 Flyway 历史表。选择 `migration-postgresql` 迁移目录，不重写或混用已经发布的 MySQL 基线。新增迁移应提供两个数据库的对应实现。
- PostgreSQL 使用 `jsonb`、identity 自增、数字型布尔标志和原子 `ON CONFLICT`。软删除唯一性、项目作用域、CAS 与事务边界保留。`citext` 保持标识符不区分大小写；其排序和重音规则以 PostgreSQL 数据库 locale 为准。
- 元数据搜索使用绑定的字面量词与 `pg_trgm` 索引；空格分词按 OR 匹配，保留短词、中文与特殊字符的检索路径。检索行、分面和列命中使用同一谓词，查询次数不随类型数增加。MySQL 保留其全文索引实现。
- 主数据落地、加工和分发 SQL 使用平台库方言。来源刷新保留审批/清洗覆盖值，来源 ID 按数据源键替换，无变化重放不增加版本。外部数据源与用户提供的 SQL 继续由各自插件/方言处理。
- PostgreSQL profile 默认启用 Quartz JDBC 存储。Flyway 初始化 `QRTZ_*`，`initialize-schema=never` 避免每次启动重建；工作流使用独占 PostgreSQL advisory lock，仍只支持单 Master。持久化不扩展为多实例容错承诺。
- PostgreSQL JDBC 与 openGauss JDBC 使用各自命名空间，可同时装配。openGauss 仍是外部数据源能力，不能替代平台 PostgreSQL 驱动。
- 启用 AI Agent 时使用官方 PostgreSQL StateStore。`yak.agent.state-store.database` 表示该业务数据库内的 schema（默认 `agentscope`），SDK 自动创建 schema 与状态表，因此账号需要数据库 CREATE 权限。消息历史仍只有官方 StateStore 一份真相。

## 验收

`PostgresqlStorageSmokeTest` 必须指向测试专用空库，通过 `ARCHITECTURE_POSTGRESQL_URL`、`ARCHITECTURE_POSTGRESQL_USERNAME`、`ARCHITECTURE_POSTGRESQL_PASSWORD` 设置连接。测试启动完整应用，检查迁移与管理员初始化，验证登录、项目隔离、共享目录字段归属、中文/OR/字面量搜索、业务 UPSERT 与回滚、运行聚合、主数据幂等与属性覆盖、单 Master 排他、Quartz 与官方 Agent StateStore 的重启恢复。每次执行提供新的空库。

设置 `ARCHITECTURE_POSTGRESQL_SECURITY_URL` 可额外验证独立权限库；对应的 `ARCHITECTURE_POSTGRESQL_SECURITY_USERNAME` / `ARCHITECTURE_POSTGRESQL_SECURITY_PASSWORD` 默认复用测试业务库账号。未提供测试 URL 时，此集成测试跳过；普通单元测试不能替代实库验收。Windows 若 JDK 的 Unix domain socket 回环检查失败，可给 Maven 提供 `-DargLine=-Djdk.net.unixdomain.tmpdir=<不存在的目录>` 以使用 TCP 回环。

```bash
mvn -pl data-ops-boot -am test -Dtest=PostgresqlStorageSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
node scripts/db/check-migration-history.mjs
```

本次实库结果记录在 [PostgreSQL 存储验收](postgresql-acceptance.md)。

参考：[PostgreSQL 扩展](https://www.postgresql.org/docs/16/sql-createextension.html)、[Flyway PostgreSQL 支持](https://documentation.red-gate.com/fd/postgresql-database-277579325.html)。
