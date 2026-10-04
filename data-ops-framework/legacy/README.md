# Framework 历史兼容代码

这里统一收纳与当前框架构建基线隔离的历史兼容模块。`legacy/` 是源码维护目录，不是 Maven 聚合模块，不参与当前框架的构建与发布。

## data-job

`data-job` 基于 Spring Boot 2 和 Java 8，保留旧的 Maven 坐标 `io.yak:data-job-spring-boot-starter:1.0.31`、Java 包名及 `/v1/yak-job/**` 路由。它不参与当前 Spring Boot 3 / Java 21 的框架聚合构建；需要维护或构建时，请进入 `data-job` 目录单独处理。

模块目录名用于仓库内组织，不会改变对外发布的 artifactId 或 Java API。

使用 Java 8 的独立构建环境，从仓库根目录编译旧模块：

```shell
./mvnw -f data-ops-framework/legacy/data-job/pom.xml compile
```

该命令使用旧模块自身的 POM，不继承当前框架父 POM，也不构建当前 Boot 3 模块。旧模块的验证、签名与发布仍使用其自身构建配置。
