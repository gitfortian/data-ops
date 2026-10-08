# 构建兼容性边界

`data-ops-framework` 目录是 Yak Ops 主仓库 Maven Reactor 中的嵌套聚合工程。其自身根
POM 聚合 `data-common`、`data-security`、`data-schedule` 和 `data-workflow`；
上述在 Reactor 中的模块继承独立的 `data-ops-framework-parent`，不继承 Data-Ops 根 POM。`data-file` 从 A8.1 起不再进入 Reactor / BOM，但旧源码与 POM 暂保留，待外部制品兼容性核查后由 A8.5 决定最终处置。主仓库构建框架与
业务时无需先把框架安装到本地 Maven 仓库。

Framework Reactor 以 Spring Boot 3.3.13 和 Java 21 为统一基线；`data-security` 使用
Jakarta Servlet API 和面向 Spring Boot 3 的 MyBatis-Plus starter。

历史兼容模块位于 `data-ops-framework/legacy/data-job`，不在该嵌套 reactor 的
Boot 3 聚合构建边界内。它仍是一个遗留的
Spring Boot 2 / Java 8 模块，并包含 Springfox、旧 MyBatis starter 等与 Boot 3
不兼容的依赖。隔离只避免在同一个 reactor 中混用两套基线，并不表示该模块已经
完成升级；迁移前应当从该目录独立维护和构建它。此选择不改变其业务逻辑、制品坐标或 `/v1/yak-job/**` 接口。

目录收纳与 Maven 聚合是两个边界：`legacy/` 下的 POM 不得注册到当前 reactor；
当前 production 模块不得依赖 `data-job-spring-boot-starter` 或导入 `com.yak.job.*`。
架构检查排除旧源码，并验证这两个边界，防止目录嵌套使旧依赖进入当前应用。

Spring Boot 版本解析应以 Maven Central 为准。升级基线时可使用以下命令验证父 POM
确实存在，而不是仅依赖镜像缓存：

```shell
mvn -U dependency:get \
  -Dartifact=org.springframework.boot:spring-boot-starter-parent:3.3.13:pom \
  -Dtransitive=false \
  -f /tmp/empty-pom.xml
```
