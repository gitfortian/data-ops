# A8.1b — Data-Ops BOM 脱离 Framework 父 POM 的依赖治理

> A8 主计划：[Issue #412](https://github.com/gitfortian/data-ops/issues/412)。前置 [A8.0 PR #415](https://github.com/gitfortian/data-ops/pull/415)（依赖清单与守卫）、[A8.1a PR #417](https://github.com/gitfortian/data-ops/pull/417)（未消费 File 制品退出应用 Reactor）。全部受保护 Draft，尚未合并。

## 动机 / 已核实的构建事实

Data-Ops 的 `data-ops-bom/pom.xml` 原来通过 `dependencyManagement` **import** `io.github.weifuwan:data-ops-framework-parent:0.1.0`。该父 POM 在此入口下只提供六项第三方 Managed 版本：

| 第三方坐标 | 固定版本 |
| --- | --- |
| `com.baomidou:mybatis-plus-core` | `3.5.16` |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | `3.5.16` |
| `com.baomidou:mybatis-plus-jsqlparser-4.9` | `3.5.16` |
| `com.alibaba:druid` | `1.2.28` |
| `org.checkerframework:checker-qual` | `3.37.0` |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | `2.6.0` |

仅为这六项版本而导入整个 Framework 聚合父 POM，不符合 A8 最终去除 Framework 作为产品构建边界的目标。**不可以直接移除 import 而让 Maven 从其他来源猜版本**：那会造成有效依赖版本漂移或依赖无法解析。

## 本批实际迁移

- 从 Data-Ops BOM 删除上述 Framework 父 POM 的 import 坐标。
- 将六项第三方版本约束及其四组属性精确地移到**现有** `data-ops-bom` 内，保持当前版本值和 Maven Managed scope/type 语义，不创建新的基础模块。
- 增加 `scripts/architecture/framework-bom-ownership.mjs` 与七组自动执行的正反测试：实际 BOM 必须显式拥有六个坐标且版本与原 Framework 父 POM 相同；禁止重新 import 旧父 POM；检测漏项、重复项、显式 scope 和版本漂移。
- 当前 `scripts/architecture/*.test.mjs` 已由 Architecture Checks 自动执行，无需扩展永久全量 CI；BOM 文件更改会触发完整后端/前端/发行验证。

## 与 Common/Security 的正确关系

旧 `data-common` 包含 Assert、BaseResult、BusinessException、CommonErrorCode、ErrorCode、PageData、PagingData、Result、JdbcDatabase 共九个生产类型。现在 Security Starter 还**直接依赖** `io.github.weifuwan:data-common`，而产品 `data-ops-common` 也依赖该制品及 `data-schedule-api`。

因此这次**只拆走 Framework 父 POM 的版本管理耦合**，**没有改动** `data-common` 的旧包、Security 的业务身份、HTTP JSON/异常、认证、Spring Bean/配置、Flyway 历史，也没有让 Framework Security 反向依赖 Data-Ops Common。后续 Common 类的最终归属需要与 A8.2 Security Platform 迁移作具体配合；不能把相同类型复制两份来假装完成。

## 验收准入与局限

- [ ] 七组正反测试在 PR HEAD 通过，实际 BOM 与旧 Framework 的版本 parity 测试通过。
- [ ] Product Guard、Architecture Checks、MySQL/PG 与 Maven Reactor、前端合同及发行检查按最新 PR SHA 完成。
- [ ] 与 A8.0、A8.1a 及 A0～A7 真实 HEAD 在相同最新 main 上正序组合，另采集独立证据，不能重复使用历史成功日志。
- [ ] 在 A8.2 / A8.5 移除 Framework 父源码之前，把本 PR 对父 POM 的**过渡 parity 对照测试**调整为相应的绝对 BOM 自拥有验证，仍须保持确切版本和异常负例；禁止仅删除测试来求绿。
- [ ] A8 结束须零 Framework Maven/JVM 有效依赖；本 PR 只减少一处父 POM import，不是全部 Common 完成。

**回滚**：恢复 BOM 父 POM import，删除新引入的六项自管 managed 版本后执行 Maven 完整验证。无数据库、业务行为或用户数据回滚步骤。
