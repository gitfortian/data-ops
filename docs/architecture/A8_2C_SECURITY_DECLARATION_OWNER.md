# A8.2c — Permission Declaration Contract 物理迁移

> 总计划 [#412](https://github.com/gitfortian/data-ops/issues/412)。依赖 A8.2b [#428](https://github.com/gitfortian/data-ops/pull/428) 提供的 `data-ops-platform-security-contract` 唯一模块；#428 是仍未合并的 Draft。因此本 PR 是**显式堆叠**在 #428 HEAD 上的独立工作分支，目标 main 只是为了触发完整 CI，GitHub PR diff 会暂时包含 #428 内容；**不得将 #428 和本 PR 作为两项可任意乱序合并的独立补丁**。先保持二者 Draft，A8.6 正序组合以精确 head 做逐层去重验收，合并顺序另需用户批准。

## 本轮真实代码归属转移

从 `data-ops-framework/data-security/src/main/java/io/yak/framework/security/permission/` **物理移出**三个生产类型到 `data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/permission/`：

- `YakPermission`：保留原 FQCN，`@Retention(RUNTIME)`，TYPE/METHOD Target，六个声明属性及各自默认值。所有使用此注解的 Business/Boot 编译、Spring 反射扫描不必更换 imports。
- `PermissionDefinition`（包括 nested `Item`）：保留 public Java API、包可见 `fromItems`、输入校验、trim、菜单字段和只读集合。阶段性维持现有 Spring Core `Assert` / `StringUtils`，以避免搬迁同时改变错误和空白字符语义，**不引入业务模块或数据库依赖**。
- `PermissionDefinitionProvider`：保留 `@FunctionalInterface`、返回 `List<PermissionDefinition>` 的签名及 `of` 工厂的 null/只读/顺序规则。

原 Security Starter **已删除**上述三个源文件，保留 `PermissionRegistrationInitializer` / `PermissionRegistrationService` 在原模块，依赖 A8.2b 已提供的 Starter→Platform Maven 箭头。没有把旧包名改为新包名，也没有生成双份 Java class；对应二进制类型现在仅由 Platform Jar 提供。A8.5 之前仍需完整审计外部直接引用旧 Maven 坐标的消费者，JAR 内部 class 位置变动是明确的**制品兼容风险**，不是“完全零行为差异”的推定。

## 本轮可执行验证

1. Platform module Java tests 逐项校验反射可见的旧注解签名/默认值、Declaration 的校验/菜单/只读语义、Provider 静态工厂规则。
2. Security Starter Java tests 验证 `AnnotatedElementUtils.findMergedAnnotation` 仍能发现 TYPE/METHOD 声明、原 FQCN 可加载、三个类型及 Item 都恰有一个 classpath bytecode 来源。
3. Node 架构守卫 `check-security-declaration-owner.mjs` 拒绝原 Framework 类重复复活、源文件缺失、注解 runtime 元信息退化、FQCN 误改、Platform→Starter/Business/Common/Boot 逆向依赖或遗失旧扫描入口。
4. 七项 JS 正反测试并接入 Architecture Checks。现有 Project/RBAC 授权回归、原 Starter CI 与发行构建仍需与当前 HEAD 的真实 GitHub 状态绑定。

## 不触碰的能力与后续关卡

本批不更改 Security 的数据库表、SQL/Flyway history、角色/项目访问策略、401/403、登录/登出/Session/Token、RBAC 注册事务逻辑与 Spring AutoConfiguration imports。不转移 `PermissionRegistrationInitializer`、`PermissionRegistrationService` 或 Controller；它们仍归旧 Starter。历史实例升级、真实 MySQL/PostgreSQL、Sa-Token Redis/Memory、Project/RBAC 与隔离组合 CI 不可由静态或单测冒充。

后续 A8.2d 需要迁移事务服务与权限读写 Owner，同时做完整运行时验证。A8.6 需特别检查 #415 的 Framework 回流守卫对 **为二进制兼容而继续保留的 FQCN** 的迁移例外，证明“旧 package 可过渡”与“新增 Framework 反向耦合”区别，不能扩大允许表掩盖新引用。

## 回滚

完整撤回本批时，将三个 Java 文件恢复到原 Starter 的原路径，删除 Platform 中相同 FQCN 文件并移除本批 `spring-core` 显式依赖；#428 的独立权限码合同可保留。**不要同时在两个 JAR 中放入同名 class**。禁止自动合并；工作分支和 #428 均保持 Draft + `do-not-merge`。
