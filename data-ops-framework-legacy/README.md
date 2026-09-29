# data-ops-framework-legacy

这里存放从旧 `data-ops-framework` Reactor 中隔离的兼容模块。

## data-job

`data-job` 基于 Spring Boot 2 和 Java 8，保留旧的 Maven 坐标 `io.yak:data-job-spring-boot-starter:1.0.31`、Java 包名及 `/v1/yak-job/**` 路由。它不参与当前 Spring Boot 3 / Java 21 的框架聚合构建；需要维护或构建时，请进入 `data-job` 目录单独处理。

模块目录名用于仓库内组织，不会改变对外发布的 artifactId 或 Java API。
