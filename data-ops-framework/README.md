# data-ops-framework

Yak Framework 使用小型 Maven 模块保持能力边界清晰：

- 根项目 `data-ops-framework-parent`：统一管理 JDK 21、Spring Boot、依赖版本、编译、测试、格式检查和打包标准；
- `data-common`：跨模块共享的响应、分页、错误码与业务异常契约；
- `data-security`：用户、认证、角色、权限和操作审计能力，依赖 `data-common`；
- `data-schedule`：插件化统一调度能力，提供稳定 API、核心路由、Quartz 与 XXL-JOB 插件，以及聚合全部插件的 Starter；
- `data-workflow/data-workflow-engine`：纯 Java DAG 工作流执行内核；
- `data-file`：兼容 Java 8 的本地、MinIO、OSS 统一文件服务。

模块目录表达维护和聚合位置，artifactId 表达发布角色，因此两者按角色区分：

| 目录 | Maven 模块角色 | artifactId 规则 |
| --- | --- | --- |
| `data-common`、`data-file` | 独立库 | 与目录同名 |
| `data-security` | Spring Boot Starter | 保留 `data-security-spring-boot-starter` |
| `data-schedule`、`data-workflow` | 子模块聚合父 POM | 使用 `*-parent`；可发布制品位于各自子目录 |

历史兼容模块 `data-job` 使用 Spring Boot 2 和 Java 8，放在本目录的
[`legacy/data-job`](./legacy/data-job/README.md)，不属于当前 Boot 3 Maven Reactor。它的 POM、Java 包、制品坐标和 `/v1/yak-job/**` 接口保持兼容。

`legacy/` 统一收纳历史兼容代码，不注册为当前聚合工程的 Maven module，也不继承当前框架父 POM。当前应用不得依赖旧 `data-job-spring-boot-starter` 或导入 `com.yak.job.*`；这些边界由架构检查保护。

`data-common` 不接收业务 DTO、实体或领域工具，新增内容前应确认它确实是所有业务模块共享的稳定契约。

当前首个公开发布版本为 `0.1.0`，Git Tag 建议使用 `v0.1.0`。

Maven Central 发布坐标统一使用 GitHub Namespace `io.github.weifuwan`。Java 源码包名仍保持 `io.yak.framework.*`，Maven `groupId` 的调整不会要求业务代码修改 import。

## 作为父 POM 使用

新增模块应继承统一父 POM，而不是直接继承 Spring Boot Parent：

```xml
<parent>
    <groupId>io.github.weifuwan</groupId>
    <artifactId>data-ops-framework-parent</artifactId>
    <version>0.1.0</version>
</parent>
```

## 独立构建

```shell
mvn clean verify
```

需要把制品提供给仓库外部的 Maven 项目时，再执行 `mvn clean install`。

在 Yak Ops 仓库中，`data-ops-framework` 已作为根 Maven Reactor 的子工程。直接从 Yak Ops 根目录构建即可，无需先在本目录执行 `install`：

```shell
./mvnw clean verify
```

如需仅构建框架，在 Yak Ops 根目录执行：

```shell
./mvnw -f data-ops-framework/pom.xml clean verify
```

## 发布到 Maven Central

项目通过 Sonatype Central Publisher Portal 发布。`central-release` Profile 会自动生成 Sources/Javadoc、执行 GPG 签名，并通过 `central-publishing-maven-plugin` 上传、验证和自动发布制品。

发布前需要完成两项本地配置：

1. 在 Central Portal 中注册并验证 `io.github.weifuwan` Namespace，并生成 User Token。
2. 本机安装可用的 GPG Key，并将公钥发布到公开 Key Server。

将 Central Portal 的 User Token 写入 Maven `settings.xml`：

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>YOUR_TOKEN_USERNAME</username>
            <password>YOUR_TOKEN_PASSWORD</password>
        </server>
    </servers>
</settings>
```

发布 `0.1.0`：

```shell
D:\baize-works\baize-tools\apache-maven-3.9.16\bin\mvn clean deploy -Pcentral-release -DskipTests -Dspotless.check.skip=true
```

`central-release` 已启用自动发布并等待到 `published` 状态，因此命令成功结束后无需再到 Central Portal 手工点击 Publish。Maven Central 同步完成后，使用方无需配置仓库地址或下载凭证即可直接引用，例如：

```xml
<dependency>
    <groupId>io.github.weifuwan</groupId>
    <artifactId>data-file</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 正式版本混淆

普通构建默认不启用 ProGuard。只有显式启用 `release-obfuscated` Profile 时，才会在 `package` 阶段对各个 JAR 模块执行混淆：

```shell
D:\baize-works\baize-tools\apache-maven-3.9.16\bin\mvn clean package -Prelease-obfuscated -DskipTests -Dspotless.check.skip=true
```

如果发布到 Maven Central 时也需要混淆，可同时启用两个 Profile：

```shell
D:\baize-works\baize-tools\apache-maven-3.9.16\bin\mvn clean deploy -Pcentral-release,release-obfuscated -DskipTests -Dspotless.check.skip=true
```

第一版混淆策略只进行名称混淆，不执行 shrink/optimize，并保留公共 API 与 Spring/Jackson 等运行时反射所需的元数据。各模块会在 `target/` 下生成 `proguard-mapping.txt` 和 `proguard-seeds.txt`，用于问题排查和堆栈反混淆；这些文件属于内部发布元数据，不应作为 Maven 制品发布或对外提供。
