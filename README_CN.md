<p align="center">
  <img
    src="https://github.com/user-attachments/assets/74def480-8353-45d3-982e-54606cd54474"
    width="100%"
    alt="Yak Ops 横幅"
  />
</p>

<h1 align="center">Yak Ops</h1>

<p align="center">
  <strong>一个面向数据集成、工作流编排、数据质量与治理的开源数据运维平台。</strong>
</p>

<p align="center">
  把数据源、同步、开发任务、工作流、质量检查、数据服务和治理，放进同一个可自托管的工作空间。
</p>

<p align="center">
  <a href="./README.md">English</a>
  ·
  <a href="./README_CN.md">简体中文</a>
</p>

<p align="center">
  <a href="https://yak-ops.com/">官网</a>
  ·
  <a href="https://demo.yak-ops.com/">在线体验</a>
  ·
  <a href="https://doc.yak-ops.com/">项目文档</a>
  ·
  <a href="https://github.com/weifuwan/yak-ops/issues">问题反馈</a>
  ·
  <a href="https://github.com/weifuwan/yak-ops/pulls">Pull Requests</a>
</p>

<p align="center">
  <a href="https://github.com/weifuwan/yak-ops/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/weifuwan/yak-ops?style=flat-square" alt="开源许可证" />
  </a>
  <a href="https://github.com/weifuwan/yak-ops/stargazers">
    <img src="https://img.shields.io/github/stars/weifuwan/yak-ops?style=flat-square" alt="GitHub Stars" />
  </a>
  <a href="https://github.com/weifuwan/yak-ops/issues">
    <img src="https://img.shields.io/github/issues/weifuwan/yak-ops?style=flat-square" alt="GitHub Issues" />
  </a>
  <img src="https://img.shields.io/badge/Java-21-blue?style=flat-square" alt="Java 21" />
  <img src="https://img.shields.io/badge/Node.js-%3E%3D20-blue?style=flat-square" alt="Node.js 20+" />
</p>

---

## Yak Ops 是什么？

真实的数据运维很少只有一个动作。

一条数据链路往往从连接数据源开始，经过同步和开发，进入工作流调度，需要质量检查和运行可观测，最终还可能形成数据集、分析结果或 API，交给其他人继续使用。同时，权限、项目空间、审计和告警又贯穿整个过程。

现实中，这些事情经常散落在脚本、执行引擎控制台、调度页面、元数据工具、监控系统和各种内部管理后台里。**Yak Ops 想做的，是把这些上下文重新放回一个统一、开放的数据运维控制面。**

Yak Ops 最早从数据集成出发，但现在它已经不再把自己定义成“给某一个执行引擎套一层 Web UI”。项目真正关注的是数据从接入到运行、再到消费和治理的完整生命周期：**连接、同步、开发、编排、校验、服务、治理。**

执行引擎只是控制面背后的运行时能力，而不是 Yak Ops 的产品边界。

```text
数据源
  │
  ▼
数据集成 ─────► 数据开发
  │               │
  └───────┬───────┘
          ▼
        工作流
          │
          ▼
        数据质量
          │
          ▼
  数据集 / 分析 / API
          │
          ▼
 项目空间 / RBAC / 审计
```

## 现在可以做什么？

| 领域 | 当前能力 |
| --- | --- |
| **数据源** | 统一管理和复用数据连接，测试连通性，读取元数据与目录信息，并通过插件扩展数据源能力。 |
| **数据集成** | 离线同步支持单表、多表和脚本化配置；实时同步提供 CDC 任务定义、部署与运行控制。 |
| **数据开发** | 管理开发任务、发布和执行生命周期，并提供 SQL、Python、Shell、Java 等任务插件基础。 |
| **工作流** | 可视化定义工作流，配置调度，查看执行实例、节点状态和运行历史。 |
| **数据质量** | 质量总览、数据表监控、规则模板、质量规则、执行记录和结果查看。 |
| **数据资产与消费** | 文件资源、数据集、血缘、分析、仪表盘、数字化大屏，以及数据服务 API 和运行记录。 |
| **治理与运维** | 项目空间、用户、部门、角色与权限、操作日志、审计能力，以及通知和告警基础设施。 |

项目仍在快速演进，不同模块的成熟度并不完全一致。Yak Ops 会继续收敛产品边界，让这些能力最终组成更连贯的端到端工作流，而不是简单堆叠更多菜单。

## 我们怎么看这个产品？

Yak Ops 的设计会尽量遵循下面几个原则。

**1. 控制面应该是一体的。**  
数据源、任务、工作流、质量检查、数据集、API 和审计事件，不应该像一组彼此无关的后台页面。用户应该能够理解它们之间的关系，并沿着同一条链路完成操作和排查。

**2. 执行引擎应该可以替换。**  
Yak Ops 负责产品语义、生命周期、权限、可观测和编排；具体引擎负责执行它擅长的工作。平台不应该因为绑定某一个引擎，而把自己的边界限制死。

**3. 重要状态必须能被看见。**  
长任务、调度、重试、失败、运行事件和跨模块操作，不应该被藏在一个只会显示“运行中”或“失败”的按钮后面。运维系统首先应该帮助人理解正在发生什么。

**4. 扩展应该依赖契约，而不是依赖 Fork。**  
数据源、存储、任务和告警能力通过 SPI / Plugin 进行扩展，新能力应该尽量通过稳定契约接入，而不是反复修改核心代码。

**5. 开源本身就是产品。**  
Yak Ops 希望作为一个完整的开源项目被真正使用，而不是把开源仓库做成一个功能受限的演示版，再依赖关闭功能来推动商业版本。

## 当前集成能力

目前代码仓库已经包含以下运行时和插件能力：

- **离线同步：** 通过 Link-Up 完成任务定义、执行与状态协调。
- **实时同步：** 通过 Flink CDC 提交 Pipeline，并使用 Flink REST API 管理运行状态。
- **数据源插件：** JDBC 通用能力以及 Doris 支持。
- **存储插件：** Local、MinIO、HDFS。
- **任务插件：** SQL、Python、Shell、Java 基础能力。
- **告警插件：** DingTalk 集成与通用 Alert SPI。

这些只是当前已经实现的集成，不代表 Yak Ops 未来只能支持这些技术栈。

## 快速开始

### Docker Compose

如果只是本地体验，Docker Compose 是最短的启动路径。默认 Compose 会运行 MySQL、Yak Ops 后端以及前端 / 反向代理。

```bash
git clone https://github.com/weifuwan/yak-ops.git
cd yak-ops
cp .env.example .env
```

启动前，请编辑 `.env`，把示例中的数据源主密钥替换成你自己的随机密钥：

```env
YAK_OPS_DATASOURCE_MASTER_KEY=replace_with_your_own_random_secret
```

使用 `.env` 中配置的镜像启动：

```bash
docker compose pull
docker compose up -d
```

按照当前 `.env.example` 的默认端口，可以访问：

```text
http://localhost:9001
```

常用命令：

```bash
docker compose ps
docker compose logs -f yak-ops-api
docker compose down
```

> `.env.example` 中的密码和密钥只是开发示例。在本地体验之外使用 Yak Ops 时，请务必更换数据库密码和数据源主密钥。

如果已经有自己的 MySQL，可以使用 `.env.without-mysql.example` 和 `compose.without-mysql.yaml`，不再启动默认 MySQL 容器。

### 从源码构建

源码构建当前需要：

- JDK 21
- Node.js 20+
- Yarn Classic
- Maven，或项目自带的 Maven Wrapper
- 本地运行时使用 MySQL 8.0
- 在同一个 Maven 本地仓库中安装 `yak-framework:1.0.0-SNAPSHOT`

先构建前端：

```bash
cd yak-ops-ui
yarn install
yarn build
cd ..
```

再构建完整 Maven Reactor 和发行包：

```bash
./mvnw clean package -DskipTests
```

Windows：

```cmd
mvnw.cmd clean package -DskipTests
```

最终发行包生成在：

```text
yak-ops-dist/target/
```

更详细的环境配置与部署方式请查看 [Yak Ops 项目文档](https://doc.yak-ops.com/)。

## 系统架构

Yak Ops 尽量把产品领域、运行时契约和具体集成分开，避免某一个执行引擎的细节渗透到整个系统。

```text
┌─────────────────────────────────────────────────────┐
│                    yak-ops-ui                       │
│              React / Umi / Ant Design               │
└───────────────────────┬─────────────────────────────┘
                        │ HTTP / WebSocket
                        ▼
┌─────────────────────────────────────────────────────┐
│                   yak-ops-boot                      │
│                Spring Boot Runtime                  │
└───────────────────────┬─────────────────────────────┘
                        │
          ┌─────────────┴─────────────┐
          ▼                           ▼
┌──────────────────────┐   ┌──────────────────────────┐
│       业务领域        │   │ 安全 / 项目空间          │
│ 数据源 / 数据集成     │   │ RBAC / 审计 / 上下文     │
│ 数据开发 / Job       │   │ 业务数据隔离边界         │
│ 工作流 / 数据质量     │   └──────────────────────────┘
│ 数据集 / 血缘         │
│ 分析 / 仪表盘         │
│ 数据服务 / 告警       │
└──────────┬───────────┘
           │ SPI
           ▼
┌─────────────────────────────────────────────────────┐
│ Plugin：数据源 / 存储 / 任务 / 告警                 │
└───────────────────────┬─────────────────────────────┘
                        ▼
       数据库 / 存储 / Link-Up / Flink CDC / ...
```

仓库主要模块：

```text
yak-ops
├── yak-ops-bom           依赖版本对齐
├── yak-ops-common        公共基础能力
├── yak-ops-spi           扩展契约
├── yak-ops-core          平台核心能力
├── yak-ops-business      各产品业务域
├── yak-ops-plugins       数据源 / 存储 / 任务 / 告警插件
├── yak-ops-boot          Spring Boot 应用入口
├── yak-ops-ui            Web 前端
└── yak-ops-dist          发行包组装
```

Yak Ops 同时基于 [yak-framework](https://github.com/weifuwan/yak-framework) 复用安全、调度、工作流运行时等通用基础设施。

## Project Space 与治理

Yak Ops 把 **Project Space** 定义为应用内部的业务工作空间和数据隔离边界：

```text
角色 / 权限   → 决定“能做什么”
项目成员关系  → 决定“能在哪个空间做”
project_id    → 决定“业务数据属于哪个空间”
```

这套边界正在统一应用到数据源、同步任务、数据开发、工作流、数据质量、数据集、分析、仪表盘和数据服务等链路，目标不是只过滤列表，而是让详情、编辑、删除、发布、执行以及异步运行记录都遵守同一套项目隔离规则。

完整设计基线见 [`docs/architecture/PROJECT_SCOPE.md`](docs/architecture/PROJECT_SCOPE.md)。

## 安全说明

Yak Ops 使用 Yak Security 提供身份与权限抽象，并默认使用 Sa-Token 作为认证后端。

数据源连接和数据处理任务可能访问外部系统。如果准备在生产环境使用，至少需要明确检查：

- 项目空间与功能权限；
- 网络访问和出站限制；
- 数据源密钥管理和主密钥保护；
- 审计与操作日志保留策略；
- 查询和任务运行限制；
- 数据库备份、Schema 迁移与升级流程。

不要在公共体验环境中填写真实生产凭据或敏感数据。

## 项目状态

Yak Ops 仍处于活跃开发阶段。随着端到端产品流程继续收敛，API、数据库结构、导航以及部分模块边界仍可能发生变化。

这种变化是有意的：现阶段比起尽早冻结一大批功能，我们更希望不断把零散的数据工程操作重新整理成更少、更清晰、更容易理解的工作流。

如果你准备把 Yak Ops 用在生产环境，建议先从非生产环境验证，并根据自己的基础设施要求检查安全、部署、备份和运行时假设。

## 参与贡献

Yak Ops 在 GitHub 上公开开发。Bug、产品建议、设计讨论、文档改进和代码贡献都非常欢迎。

建议按照这个方式参与：

1. 先搜索已有 [Issues](https://github.com/weifuwan/yak-ops/issues)；
2. 对 Bug、产品缺口或设计想法提交 Issue；
3. Pull Request 尽量只解决一个清晰问题，并说明它改变了什么用户行为；
4. 如果修改引入了新的契约，同步补充测试和文档。

提交代码前请阅读 [`CODE_STYLE.md`](CODE_STYLE.md)。前端修改还应遵循 [`yak-ops-ui/FRONTEND_CODE_STYLE.md`](yak-ops-ui/FRONTEND_CODE_STYLE.md)。

如果 Yak Ops 对你有帮助，一个 ⭐ 可以让更多人看到这个项目。

<img width="160" height="244" alt="image" src="https://github.com/user-attachments/assets/54df60b1-781d-4815-a2c5-a6db3179d740" />

## 开源许可证

Yak Ops 使用 [Apache License 2.0](LICENSE) 开源。
