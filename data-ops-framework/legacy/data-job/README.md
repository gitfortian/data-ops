# data-job-spring-boot-starter 1.0.31 Java 源码恢复版

本目录由上传的 JAR 文件恢复，并按标准 Maven 工程结构整理。

## 恢复内容

- `src/main/java`：从字节码恢复的 Java 源码，共 97 个顶层 Java 文件。
- `src/main/resources`：JAR 中原有的 6 个 MyBatis Mapper XML、`yak-job.sql` 和 Spring 自动配置文件。
- `pom.xml`：此目录用于独立构建的 Maven POM。
- `docs`：原始 JAR 清单、源码目录、资源目录和反编译摘要。

## 基本信息

- 当前模块 Maven 坐标：`io.yak:data-job-spring-boot-starter:1.0.31`（以本目录 `pom.xml` 为准）。
- 来源 JAR 的内嵌 Maven 元数据路径为 `io.github.zqrferrari/logi-job-spring-boot-starter`，记录在 `docs/original-jar-entries.txt`。
- 原始编译目标：Java 8
- 源码恢复工具：CFR 0.153-SNAPSHOT

## 注意事项

字节码不能完整保留原始注释、源码排版和部分局部变量名称，因此这些信息可能与作者原始源码不同；类结构、字段、方法签名及可恢复的业务逻辑均已保留。部分文件顶部的 `Could not load` 提示表示反编译时未加载第三方依赖，并不代表
Java 文件或逻辑缺失。
