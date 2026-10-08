# A2 · Boot 持久化装配解耦（第一批，无行为变化）

> 关联 [Issue #368](https://github.com/gitfortian/data-ops/issues/368)。
> 从提交时的最新 `main` 创建独立分支；仅提交 PR，不合并。
> 本批不改 `data-ops-framework`、任何业务 Service、Mapper/XML、Flyway SQL、API、配置键、Project/RBAC 或前端页面。

## 原代码事实

`data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/BusinessDatabaseConfiguration.java` 同时承担：

- Hikari 业务连接池与 PostgreSQL JDBC 参数；
- 主事务管理器；
- MyBatis-Plus SqlSessionFactory（VendorDatabaseIdProvider、DataSourcePO/Job PO type alias、XML Mapper 路径、PostgreSQL Boolean TypeHandler、PaginationInnerInterceptor）；
- SqlSessionTemplate；
- 以上四种 Bean 的 `yakBusiness*` 主名以及 `ops*`、`offlineSync*` 兼容别名。

`BusinessDatabaseConfigurationTest` 已对四组 Bean 别名、`yak.database.enabled=false`、Hibernate 之外的类型别名解析以及真实 H2 事务回滚建立保护。Quartz 与安全框架的独立 Flyway/事务装配还不能直接替换为上游 Yak 的通用 Starter 行为。

## 本批改造（只调整装配职责）

```text
BusinessDatabaseConfiguration
 ├── yakBusinessDataSource (+ 原有 aliases)
 └── yakBusinessTransactionManager (+ 原有 aliases)
       |
       | @Import / @ConditionalOnProperty: yak.database.enabled
       v
BusinessMybatisSessionConfiguration
 ├── yakBusinessSqlSessionFactory (+ 原有 aliases)
 └── yakBusinessSqlSessionTemplate (+ 原有 aliases)
```

- 两个 Config 使用同一 `yak.database.enabled` 条件；主业务连接池与 SqlSession 使用同一 DataSource。
- 原有主 Bean 名、别名、`@Primary`、`@Qualifier`、Factory 配置代码保持不变。
- 保留 PostgreSQL 类型处理、MySQL/MariaDB 方言、`mapper/**/*.xml` 资源路径、TypeAliasesPackage 与分页插件。
- 暂不改为 Spring Boot Starter 自动装配，因为全仓还有多个独立迁移链、Qu​​artz、历史 Bean 别名和自定义 TypeHandler，必须在兼容矩阵验证后才能删手动 Bean。
- 新增 ContextRunner 回归测试，证明主/兼容 Session 引用同一实例及关闭数据源时 MyBatis 配置不单独启动。

## 验收与退出门槛

- [ ] `mvn -pl data-ops-boot -am test` 及既有 CI 完成。
- [ ] H2 事务回滚与所有历史 Bean alias 测试通过。
- [ ] 原 DataSource 配置属性、Mapper 类型别名、MySQL/PostgreSQL 方言与 XML Mapper 行为不变。
- [ ] Spring Context 无重复 SqlSessionFactory/Template，`yak.database.enabled=false` 不创建上述 Bean。
- [ ] 本次 diff 仅涉及 Boot 持久化配置、对应测试与本说明。
- [ ] 数据库真实 MySQL/PostgreSQL 迁移和更深入自动装配收敛另开 PR，不得由本批源码改动宣称通过。

## 后续风险边界

Spring Boot/MyBatis 的自动装配不是替换手工配置的直接目标；当前配置还拥有业务模块共享连接池、Quartz 存储准备依赖和多个 Flyway Bean 的有序启动约束。后续改动必须先收集 `@Qualifier`、`@Resource(name)`、MapperScan、TransactionManager、Flyway 和环境配置的真实消费者，再分模块实施，不以“新版 Yak 无此类”作为删除依据。
