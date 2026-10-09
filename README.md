# Yak Ops

**Open-source, self-hostable data operations platform** spanning data connectivity, integration, development, orchestration, data quality, assets, analytics, data services and governance.

[简体中文](README_CN.md) · [Documentation index](docs/README.md) · [Issues](https://github.com/gitfortian/data-ops/issues) · [Pull requests](https://github.com/gitfortian/data-ops/pulls)

> **Project status:** Actively evolving. A module or API existing in source does not imply production-readiness or end-to-end acceptance. Validate your own environment and consult current product contracts before deployment.

## Overview

Yak Ops is a unified control plane for the data lifecycle. It connects sources and runtime engines, defines and schedules data work, monitors execution, validates results, manages data assets, and makes governed data available to consumers. The platform is not tied to a single execution engine.

```text
Data sources / metadata
        |
Offline sync / CDC / data development
        |
Workflows / schedules / executions
        |
Quality / assets / lineage / semantic & metrics
        |
Datasets / analysis / dashboards / data service
        |
Project spaces · identity & permissions · audit · operations
```

## Capabilities

The following describes the **functional areas represented in this repository**, not a blanket claim that every journey is fully validated.

| Area | Scope |
| --- | --- |
| Data sources & metadata | Datasource connection management, connectivity checks, catalog / metadata exploration and reusable datasource plugin contracts. |
| Data integration | Offline synchronization definitions (including single-table, multi-table and script configurations), execution management and realtime CDC pipeline integration. |
| Data development | Development tasks, versions / releases, execution records and task plugin contracts for SQL, Python, Shell and Java. |
| Workflows & scheduling | Workflow definitions, scheduling, task instances, node status and execution history. |
| Data quality | Table monitoring, reusable quality rule templates, checks, execution results and inspection. |
| Data assets & lineage | Files / resource management, asset and dataset management, metadata relationships and lineage. |
| Semantic & metric modeling | Semantic / metric / modeling domain capabilities; consult domain documentation and acceptance evidence for supported journeys and maturity. |
| Analysis & consumption | Dataset-based analysis, dashboards, visual displays and data-service API management / runtime visibility. |
| Governance & operations | Project-space boundaries, identity, RBAC, approvals / audit, notifications and alert extension contracts. |
| AI-related capabilities | AI-oriented domains may be present in the repository; availability and supported workflows must be verified against their active product specifications. |

### Runtime integrations

The repository includes integrations or extension foundations for **Link-Up** (offline work), **Flink CDC / Flink REST** (realtime processing), **JDBC / Doris** datasource plugins, **local filesystem / MinIO / HDFS** storage plugins, **SQL / Python / Shell / Java** task plugins and **DingTalk** alerts. Engine deployments, driver availability and configuration affect which functions are usable.

## Quick start with Docker Compose

Prerequisites: Docker Engine and the Compose plugin. The default [compose.yaml](compose.yaml) provides **MySQL 8.0**, the backend and the Nginx-served frontend.

```bash
git clone https://github.com/gitfortian/data-ops.git
cd data-ops
cp .env.example .env
```

Before starting, replace development credentials in `.env`, particularly `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `YAK_SECURITY_BOOTSTRAP_PASSWORD` and `YAK_OPS_DATASOURCE_MASTER_KEY`. Generate and protect a strong random datasource key and keep it stable for existing encrypted credentials. The example values are **not** safe for exposed deployments.

```bash
docker compose pull
docker compose up -d
docker compose ps
```

With the sample port setting, open **http://localhost:9001**. The backend listens on port 9527 **inside** the Compose network; the frontend is the public entry point.

```bash
docker compose logs -f yak-ops-backend
docker compose logs -f yak-ops-frontend
docker compose down
```

The project also contains [compose.without-mysql.yaml](compose.without-mysql.yaml) with [.env.without-mysql.example](.env.without-mysql.example) for an external MySQL deployment. For PostgreSQL consult [the PostgreSQL guide](docs/deployment/postgresql.md) and its dedicated Compose/profile examples. Confirm compatibility and migration behavior before reusing an existing database.

> `docker compose pull` uses the image tags configured in `.env`; published image availability depends on the release. For local source images use `docker compose build` followed by `docker compose up -d` instead.

## Build from source

Prerequisites: **JDK 21**, **Node.js 20+**, **Yarn Classic**, Maven Wrapper (included), and a configured database for running the backend. Frontend: **React / Umi Max / Ant Design**. Backend: **Java / Spring Boot 3**, Maven multi-module reactor.

```bash
cd data-ops-ui
yarn install
yarn build
cd ..
./mvnw clean verify
```

Windows: `mvnw.cmd clean verify`. Maven assembles the distribution under `data-ops-dist/target/`. To work on the frontend locally run `cd data-ops-ui && yarn dev` and configure the backend according to your environment. Database schemas and migrations should be managed by the provided application / migration flow, not by deleting historical SQL.

## Repository structure

```text
data-ops-framework/    Shared framework sources in the Maven reactor
data-ops-bom/          Dependency alignment
data-ops-common/       Shared primitives and compatibility contracts
data-ops-spi/          Runtime/plugin extension interfaces
data-ops-core/         Core platform functionality
data-ops-business/     Business capability modules
data-ops-plugins/      Datasource, storage, task and alert adapters
data-ops-boot/         Spring Boot application assembly
data-ops-ui/           React / Umi frontend
data-ops-dist/         Distribution assembly
docs/                  Product, domain, architecture, operation and acceptance docs
```

Architectural boundaries are evolving. Use the actual Maven POMs and each domain's `DOMAIN.md`, `ARCHITECTURE.md` and `DEPENDENCIES.md` as the reference for implementation-level dependencies rather than treating this overview as a fixed architecture contract.

## Project-space security and deployment

Project membership defines **where** a user operates, permissions define **what** they can do, and project ownership associates business data with a workspace. See [project scope](docs/architecture/PROJECT_SCOPE.md).

For deployments beyond local testing: replace all sample credentials, restrict network access to databases and execution engines, protect datasource credential keys, define backup and audit retention policies, and test schema migrations, authorization and complete workflows in your own environment. Never use a public demo with production credentials.

## Docs, contribution and release

- [Documentation index](docs/README.md), [product baseline](docs/product/README.md) and [product style](PRODUCT_STYLE.md) explain what is authoritative versus historical planning.
- [Agent guidance](AGENTS.md), [code style](CODE_STYLE.md) and [frontend style](data-ops-ui/FRONTEND_CODE_STYLE.md) describe contributor constraints.
- [Release guide](docs/release/RELEASING.md) documents tag-driven builds, metadata checks and publication; do not assume untagged main is a released artifact.
- Report issues and propose changes through this repository's [Issues](https://github.com/gitfortian/data-ops/issues) and [Pull requests](https://github.com/gitfortian/data-ops/pulls).

Licensed under [Apache License 2.0](LICENSE).
