# A8.2e — Permission 持久化端口与实体的物理归属迁移

> 总任务 [A8 #412](https://github.com/gitfortian/data-ops/issues/412)。本批堆叠依赖尚未合并的 A8.2d [PR #432](https://github.com/gitfortian/data-ops/pull/432)，其又依赖 #430、#428。该分支从 #432 HEAD `22414e56c806123bfbed28683c40fdb5b7960014` 创建；以 main 为 PR target 运行完整 GitHub CI 时 diff 包含上游 Draft，**不得单独或乱序合并**。

## 真实源码迁移

将两个生产 Java 类型从 `data-ops-framework/data-security` **物理移入** `data-ops-platform/data-ops-platform-security-contract`：

1. `io.yak.framework.security.common.entity.Permission`（旧路径 `common/entity/Permission.java`）：保留完整原始源码与包名，Lombok `@Data` 生成的公开 Bean getter/setter、boolean 包装字段、`transient parentCode`、menuCode、active/declared 等字段完全不变。Platform 明确声明编译用 optional Lombok 依赖。
2. `io.yak.framework.security.dao.PermissionDao`（旧路径 `dao/PermissionDao.java`）：原样保留四个接口方法签名：`selectAllAndAscOrderByLevel`、`insertBatch`、`deleteById`、`synchronizeDeclared`。

旧 Framework Security Starter 源码中对应两个类已经删除，新 Platform JAR 成为唯一字节码 Owner；旧 FQCN、公开 Java API、旧 Starter 对该独立 Platform 合同的 Maven 依赖继续保留。**没有复制一份同名 Java Class**。

## 界限明确：没有搬迁数据库执行

- `PermissionDaoImpl`、`PermissionMapper`、`PermissionPO`、其他 Project/Role/RBAC DAO/Service、`PermissionRegistrationService`、`yakSecurityTransactionManager`、独立 Security DataSource、Flyway History 和 Spring 自动装配仍归原 Starter。
- MyBatis 层 **仍按原顺序**创建父权限并使用生成 ID 关联子权限；未声明 menuCode 时保留存量菜单绑定；声明项恢复 active=true；不再声明的权限仅对 `declared=true && active=true` 的行做 soft disable；不删除角色授权记录。
- 权限实体、DAO 接口移动到一个下层合同模块，而 ORM 和事务仍留在上层，避免 Platform 反向依赖 MyBatis 实现、Boot 或 Data-Ops Business。

## 自动验证

- Platform 新增 `PermissionPersistencePortContractTest`：校验旧 FQCN、四个接口方法反射签名、Entity 的公开 Bean 属性、`parentCode` transient 状态和 Lombok 值语义。
- Starter 新增 `PermissionPersistencePortIntegrationTest`：校验 `PermissionDaoImpl` 仍实现移动后的接口，Mockito 模拟 MyBatis 的 soft disable / 未声明手工权限保留、旧菜单绑定保留、已禁用权限恢复、组先于叶插入和生成父 ID 使用。不替代真实数据库验收。
- CI 新增 `check-security-permission-persistence-owner.mjs` 和 8 项正反测试，阻止旧源码复活、FQCN 变更、DAO 合同丢失、Platform→Starter 逆向 Maven 依赖、失活与父 ID 处理改写。
- #428→#430→#432 的既有 Java 合同和 Architecture Checks 继续在本批堆叠 HEAD 上运行。

## 尚未完成的高风险验收

**本 PR 不能证明 MySQL/PostgreSQL 历史实例升级或实库事务回滚**：尚未对真实 Flyway history/历史账号密码哈希、Sa-Token Memory/Redis、Session/Project 切换、401/403、缓存失效及 RBAC 权限记录做数据库级迁移演练。外部消费者若直接依赖旧 Maven Starter JAR 的 `Permission` 和 `PermissionDao` class 文件（未自动解析新 Platform 依赖），可能存在分发/二进制兼容风险，后续 A8.5 需按真实消费和 JAR classpath 证据处理。

回滚此 PR：原样恢复这两个文件到旧 Starter 的旧路径、移除 Platform 的对应源码及其 optional Lombok 声明与本轮测试/守卫；**不能两端同时保留同名 class**。上游 #428/#430/#432 的草稿改造不随本批自动回滚或合并。

⛔ 所有架构 PR 必须保持 Open + Draft + `architecture-refactor` + `do-not-merge`，未经用户明确授权禁止合并。
