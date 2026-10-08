# A8.2a — Security 入口与持久化兼容冻结

这是 A8.2 Security Platform 迁移的**第一批先决保护**，不是 268 个 Security 生产类的归属迁移，更不代表 `data-ops-framework` 已移除。

## 当前源码确认的迁移不变量

- Starter 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 仅注册一个 `YakSecurityAutoConfiguration`。
- 主配置仍按原开关组装 Database / Web / Audit / OpenAPI，拓展入口仍有 `@ConditionalOnMissingBean` 覆盖能力。
- 独立 Security DataSource、TransactionManager、MyBatis SqlSession、tenant interceptor 和 `classpath:yak-security/db/migration` 不能在搬包时丢失。
- `yak.security` 的四项默认使能开关、公开登录路径与独立认证策略必须维持兼容。
- MySQL 和 PostgreSQL 的基线都包含 Project、Permission 历史表；不得在新 Owner 中以空库初始化代替历史兼容。

## 交付

- `scripts/architecture/check-security-migration-contract.mjs`：针对以上源码入口的 fail-closed 约束，基于实际文件而非口头清单。
- `scripts/architecture/check-security-migration-contract.test.mjs`：真实仓库正例，以及重复自动装配、认证默认值、数据源、PG Project schema、租户拦截器的负例。
- Architecture Checks 同时运行 Node 单测及实际仓库扫描。

## 有意未声称完成的验收

本守卫是**结构检查**，不等价于 Maven effective dependency、Bean 实际装配、历史 Flyway checksum 验证、数据库升级、Session 失效或 401/403/RBAC 集成测试。下一批移入新稳定 Owner 时，应先把此守卫转换为迁移前后双路径验收，删除旧文件之前保持单入口、单 Owner；再分别验证 MySQL、PostgreSQL、Project 权限、Sa-Token Redis/Memory 及真实 Flyway history upgrade。不得为了迁移而直接删除本批测试。

本批不修改任何 Security 生产代码、SQL/POM、API 或运行时开关。保留 Draft、`do-not-merge`，需 A8 全链路正序组合通过且得到明确授权后才可合并。
