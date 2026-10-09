# Yak Ops

**开源、可自托管的一体化数据运营平台**，覆盖数据连接、集成、开发、编排、质量、资产、分析、数据服务与治理。

[English](README.md) · [文档索引](docs/README.md) · [问题反馈](https://github.com/gitfortian/data-ops/issues) · [Pull Requests](https://github.com/gitfortian/data-ops/pulls)

> **项目状态：** 持续演进中。源码中存在模块或接口，并不代表对应用户旅程已经完成真实环境端到端验收或具备生产成熟度。部署前请核实实际环境与当前产品契约。

## 项目定位

Yak Ops 提供覆盖数据全生命周期的统一控制面：连接数据源和执行引擎，定义、调度并监控数据任务，执行质量检查，管理数据资产，最终向使用者提供可治理的数据消费能力，而不把产品限定为某一种执行引擎的管理界面。

```text
数据源 / 元数据
      │
离线同步 / CDC / 数据开发
      │
工作流 / 调度 / 执行记录
      │
数据质量 / 数据资产 / 血缘 / 语义与指标
      │
数据集 / 分析 / 仪表盘 / 数据服务
      │
项目空间 · 身份权限 · 审批审计 · 运行运维
```

## 当前功能范围

下表描述**当前仓库承载的功能领域**，不将所有领域一概宣称为已完成端到端验收。

| 领域 | 主要能力 |
| --- | --- |
| 数据源与元数据 | 数据连接管理与测试、目录和元数据探索、数据源插件扩展。 |
| 数据集成 | 单表、多表及脚本化离线同步任务配置、执行管理、实时 CDC 管道集成。 |
| 数据开发 | 开发任务、版本与发布、执行记录、SQL / Python / Shell / Java 任务插件契约。 |
| 工作流与调度 | 工作流定义与调度、执行实例、节点状态、执行历史。 |
| 数据质量 | 数据表监控、规则模板、质量校验、执行结果与查看。 |
| 数据资产与血缘 | 文件资源、资产与数据集管理、元数据关联及血缘。 |
| 语义与指标建模 | 语义、指标、建模等业务领域能力；具体可用场景和成熟度以对应领域文档与验收证据为准。 |
| 数据分析与消费 | 数据集分析、仪表盘、可视化大屏、数据服务 API 及运行记录。 |
| 治理与运维 | 项目空间、身份与角色权限、审批审计、通知和告警扩展。 |
| AI 相关 | 仓库中包含 AI 相关领域；具体支持范围需以生效产品规格及验收结果为准。 |

### 执行引擎与插件

仓库包含 **Link-Up** 离线任务集成、**Flink CDC / Flink REST** 实时处理集成、**JDBC / Doris** 数据源插件、**本地文件系统 / MinIO / HDFS** 存储插件、**SQL / Python / Shell / Java** 任务插件，以及 **钉钉** 告警集成。实际可用能力取决于外部引擎、驱动和运行环境配置。

## Docker Compose 快速体验

需要 Docker Engine 与 Docker Compose 插件。默认 [compose.yaml](compose.yaml) 包含 **MySQL 8.0**、后端和基于 Nginx 的前端。

```bash
git clone https://github.com/gitfortian/data-ops.git
cd data-ops
cp .env.example .env
```

启动前修改 `.env` 中的示例口令，尤其是 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`YAK_SECURITY_BOOTSTRAP_PASSWORD` 和 `YAK_OPS_DATASOURCE_MASTER_KEY`。数据源加密密钥应随机生成、妥善保管，已有加密凭据时不能随意更换。**示例凭据不适合暴露在公网的部署。**

```bash
docker compose pull
docker compose up -d
docker compose ps
```

使用示例端口时访问 **http://localhost:9001**。后端的 9527 端口仅在默认 Compose 网络中提供服务，前端为外部访问入口。

```bash
docker compose logs -f yak-ops-backend
docker compose logs -f yak-ops-frontend
docker compose down
```

已有 MySQL 时可参考 [compose.without-mysql.yaml](compose.without-mysql.yaml) 和 [.env.without-mysql.example](.env.without-mysql.example)；PostgreSQL 部署参考 [对应指南](docs/deployment/postgresql.md) 及专用 Compose/Profile 示例。接入已有数据库前，请先验证兼容性和迁移行为。

> `docker compose pull` 使用 `.env` 指定的镜像标签，能否拉取取决于对应版本的发布情况。需要从源码构建容器镜像时，使用 `docker compose build`，再执行 `docker compose up -d`。

## 源码构建

需要 **JDK 21**、**Node.js 20+**、**Yarn Classic**、仓库自带 Maven Wrapper；本地运行后端还需要配置数据库。前端主要使用 **React / Umi Max / Ant Design**，后端采用 **Java / Spring Boot 3** 多模块 Maven 工程。

```bash
cd data-ops-ui
yarn install
yarn build
cd ..
./mvnw clean verify
```

Windows 使用 `mvnw.cmd clean verify`。发行包在 `data-ops-dist/target/` 下生成。前端本地开发可运行 `cd data-ops-ui && yarn dev`，并根据环境配置后端服务。不要为了本地启动擅自删除或压缩数据库历史迁移 SQL。

## 仓库结构

```text
data-ops-framework/    纳入 Maven Reactor 的基础框架源码
data-ops-bom/          依赖版本管理
data-ops-common/       公共基础与兼容契约
data-ops-spi/          扩展与运行时接口
data-ops-core/         平台核心能力
data-ops-business/     业务领域模块
data-ops-plugins/      数据源、存储、任务、告警适配器
data-ops-boot/         Spring Boot 应用装配
data-ops-ui/           React / Umi 前端
data-ops-dist/         发行包组装
docs/                  产品、架构、运行和验收文档
```

模块边界仍在演进中；实现级依赖应以当前 POM、领域 `DOMAIN.md`、`ARCHITECTURE.md` 和 `DEPENDENCIES.md` 为准，而不是把此概览当成不可变的架构合同。

## 项目隔离与部署安全

项目成员关系决定用户**在哪个空间**操作，权限决定**能做什么**，业务数据的项目归属决定**数据属于哪里**。详见[项目空间设计](docs/architecture/PROJECT_SCOPE.md)。

非本地测试部署必须更换全部示例凭据，限制数据库和执行引擎网络访问，保护数据源密钥，制定数据库备份及审计保留策略，并在目标环境验证数据库迁移、权限及完整用户流程。切勿在公共演示环境填写生产凭据。

## 文档、贡献与发布

- [文档索引](docs/README.md)、[产品基线](docs/product/README.md) 和 [产品规范](PRODUCT_STYLE.md) 定义当前权威材料与历史计划的边界。
- [Agent 指引](AGENTS.md)、[代码规范](CODE_STYLE.md) 和 [前端规范](data-ops-ui/FRONTEND_CODE_STYLE.md) 说明开发约束。
- [发行指南](docs/release/RELEASING.md) 说明 Tag、版本元数据检查与构建发布流程；不能把尚未发布的 main 自动视作已发行版本。
- 通过当前仓库的 [Issues](https://github.com/gitfortian/data-ops/issues) 与 [Pull Requests](https://github.com/gitfortian/data-ops/pulls) 反馈问题和贡献改进。

基于 [Apache License 2.0](LICENSE) 开源。
