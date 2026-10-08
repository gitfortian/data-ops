# A8.2b — Security 权限码合同实际归属迁移

> 追踪总计划 [A8 #412](https://github.com/gitfortian/data-ops/issues/412)。前置冻结 [A8.2a #424](https://github.com/gitfortian/data-ops/pull/424) 已通过精确修复 HEAD 的 Architecture Checks / Product Guard。两批继续保持独立 Draft（基于各自 main），未合并。

## 本批确实迁移了什么

- 新增产品维护的**下层窄合同 Owner**：`io.yak.ops:data-ops-platform-security-contract`，仅持有 Security Permission Codes；生产模块没有引入 Framework、Data-Ops Common/Business/Boot、Spring 或数据库依赖。
- `io.yak.ops.platform.security.contract.SecurityPermissionCode` 成为 28 个现存系统管理权限码的**唯一字面值定义**。
- 原 `io.yak.framework.security.common.constant.SecurityPermissionCode` 保持 public 类型、所有 nested class 名与 28 个 public static final String 字段，但字段从产品 Owner 常量转发，避免外部二进制/反射代码无法解析旧 FQCN，防止双份码值定义。
- 现有 `DeptController`、`PermissionController`、`ProjectController`、`RoleController`、`UserController` 改为 import 新合同类；原注解与权限字符串不变。
- 根 Maven reactor 纳入新模块，BOM 提供版本，旧 Security Starter 只新增对该**低层稳定合同**的依赖；不引入 Framework→Common/Business/Boot 逆向依赖。

## 可执行保护

- 新模块的 `SecurityPermissionCodeTest` 固定 28 个字段**历史名称与字面值**，防止新旧同时漂移。
- Security Starter 的 `SecurityPermissionLegacyFacadeTest` 使用反射核对原 FQCN、nested type 和全部 28 个字段与新 Owner 一致（覆盖反射读取和 Java 类型签名）。
- 新增 `check-security-permission-owner.mjs`，在 Architecture Checks 对 root reactor、旧 Starter→Platform 依赖、Platform 不反向依赖、28 个兼容委托及五组真实控制器消费者进行检查。
- 六组 Node 正反测试覆盖删除产品 Owner、旧类恢复硬编码、字段缺失、平台逆向依赖与控制器回退。

## 不应误读为完成的工作

**这次只搬迁权限码的 canonical owner**，不搬 268 个 Security 生产类，不移动登录/Session/Token/Project 数据访问实现，不触碰 MySQL/PG Flyway、Bean 或配置属性，也不把业务数据安全模块当作平台身份模块。旧 Starter 仍在 Framework 下，旧 FQCN 是临时兼容承诺，必须保留直到所有内外消费者与反射引用完成核验；之后按 A8.5 逐项收口。

下一批才能安排 Project/RBAC/认证能力的真实 Owner 转移，并建立完整 MySQL/PostgreSQL、历史升级、认证/401/403、Session 内存与 Redis、权限注册及自动装配验证。CI 通过不是生产迁移验收，也**不是合并授权**。

## 风险与撤销

本批无数据库改动，部署回滚按 Maven 来源恢复：还原原 SecurityPermissionCode 字段定义、控制器旧 imports，移除新模块与旧 Starter 对其依赖。发布前必须验证旧 FQCN 存在及每个 public constant 取值完全不变；禁止包含两个相同 FQCN 的 class 文件。所有 A8 PR 保持 Draft + `architecture-refactor` + `do-not-merge`，未获明确授权不得合并。
