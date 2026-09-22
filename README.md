<p align="center">
  <img
    src="https://github.com/user-attachments/assets/74def480-8353-45d3-982e-54606cd54474"
    width="100%"
    alt="Yak Ops Banner"
  />
</p>

<h1 align="center">Yak Ops</h1>

<p align="center">
  <strong>Open-source data operations platform for data integration, workflow automation, data quality, and governance.</strong>
</p>

<p align="center">
  Connect data sources, move data, build tasks, orchestrate workflows, validate quality, and operate data from one self-hosted workspace.
</p>

<p align="center">
  <a href="./README.md">English</a>
  ·
  <a href="./README_CN.md">简体中文</a>
</p>

<p align="center">
  <a href="https://yak-ops.com/">Website</a>
  ·
  <a href="https://demo.yak-ops.com/">Live Demo</a>
  ·
  <a href="https://doc.yak-ops.com/">Documentation</a>
  ·
  <a href="https://github.com/weifuwan/yak-ops/issues">Issues</a>
  ·
  <a href="https://github.com/weifuwan/yak-ops/pulls">Pull Requests</a>
</p>

<p align="center">
  <a href="https://github.com/weifuwan/yak-ops/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/weifuwan/yak-ops?style=flat-square" alt="License" />
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

## What is Yak Ops?

Data operations are rarely a single job. A real workflow usually starts with a connection, moves through integration and development, becomes a scheduled workflow, needs quality checks and operational visibility, and eventually has to be exposed or governed for other people to use.

Those steps are often scattered across scripts, engine consoles, scheduler pages, metadata tools, monitoring systems, and internal admin panels. **Yak Ops brings that operating context into one open-source control plane.**

Yak Ops started from data integration, but the project is no longer positioned as a thin Web UI for one execution engine. The product boundary is the lifecycle around data: **connect, sync, build, orchestrate, validate, serve, and govern**. Runtime engines are integrations behind that control plane, not the definition of the product itself.

```text
Data Sources
     │
     ▼
Data Integration ──► Data Development
     │                    │
     └────────┬───────────┘
              ▼
          Workflows
              │
              ▼
        Data Quality
              │
              ▼
   Datasets / Analysis / APIs
              │
              ▼
   Project Space / RBAC / Audit
```

## What you can do today

| Area | Current capabilities |
| --- | --- |
| **Data Sources** | Reusable datasource connections, connection testing, metadata and catalog discovery, plugin-based datasource capabilities. |
| **Data Integration** | Offline synchronization with single-table, multi-table and script-oriented configuration; realtime CDC definitions, deployments and runtime control. |
| **Data Development** | Development tasks, release and execution lifecycle, with task plugin foundations for SQL, Python, Shell and Java. |
| **Workflow Automation** | Visual workflow definitions, scheduling, execution instances, node-level execution state and operational history. |
| **Data Quality** | Quality overview, table monitors, reusable rule templates, rules, executions and result inspection. |
| **Data Assets & Consumption** | Managed files, datasets, lineage, analysis, dashboards, digital screens, and data-service APIs with runtime records. |
| **Governance & Operations** | Project spaces, users, departments, roles and permissions, operation logs, audit capabilities, notifications and alert foundations. |

The repository is evolving quickly. Some areas are more mature than others, and product boundaries will continue to be refined as the workflows become more coherent.

## How we think about the product

Yak Ops is guided by a few principles:

**1. The control plane should be coherent.**  
A datasource, task, workflow, quality check, dataset, API and audit event should not feel like unrelated admin pages. They should form one understandable operating lifecycle.

**2. Execution engines should remain replaceable.**  
Yak Ops should own product concepts, lifecycle, permissions, observability and orchestration. Engines should do what they are good at: execute work.

**3. Important state should be visible.**  
Long-running work, schedules, retries, failures, runtime events and cross-module operations should be inspectable instead of hidden behind a button that only says "running" or "failed".

**4. Extensibility should be a contract, not a fork.**  
Datasource, storage, task and alert integrations are modeled as plugin capabilities so new integrations do not have to rewrite the core product.

**5. Open source is the product.**  
Yak Ops is intended to be useful as a complete open-source project. The repository is not a reduced demo whose main purpose is to push users toward a closed edition.

## Current integrations

The current codebase includes these runtime and plugin integrations:

- **Offline synchronization:** Link-Up integration for task definitions, execution and reconciliation.
- **Realtime synchronization:** Flink CDC pipeline submission with Flink runtime control through its REST API.
- **Datasource plugins:** JDBC-based integrations and Doris support.
- **Storage plugins:** Local filesystem, MinIO and HDFS.
- **Task plugins:** SQL, Python, Shell and Java foundations.
- **Alert plugins:** DingTalk integration and a common alert SPI.

These are the integrations implemented today, not permanent limits on the platform.

## Quick start

### Docker Compose

For local evaluation, Docker Compose is the shortest path. The default compose stack contains MySQL, the Yak Ops backend, and the frontend/reverse proxy.

```bash
git clone https://github.com/weifuwan/yak-ops.git
cd yak-ops
cp .env.example .env
```

Before starting, edit `.env` and replace the example datasource master key with your own random secret:

```env
YAK_OPS_DATASOURCE_MASTER_KEY=replace_with_your_own_random_secret
```

Then start the stack with the images referenced by `.env`:

```bash
docker compose pull
docker compose up -d
```

With the current `.env.example`, open:

```text
http://localhost:9001
```

Useful commands:

```bash
docker compose ps
docker compose logs -f yak-ops-api
docker compose down
```

> The values in `.env.example` are development examples. Change database passwords and the datasource master key before using Yak Ops outside a local evaluation environment.

If you already have MySQL, use `.env.without-mysql.example` together with `compose.without-mysql.yaml` instead of starting the bundled database.

### Build from source

Source builds currently require:

- JDK 21
- Node.js 20+
- Yarn Classic
- Maven, or the included Maven Wrapper
- MySQL 8.0 for a local runtime
- `yak-framework:1.0.0-SNAPSHOT` installed in the same local Maven repository

Build the frontend first:

```bash
cd yak-ops-ui
yarn install
yarn build
cd ..
```

Then build the full reactor and distribution:

```bash
./mvnw clean package -DskipTests
```

On Windows:

```cmd
mvnw.cmd clean package -DskipTests
```

The assembled distribution is generated under:

```text
yak-ops-dist/target/
```

See the [project documentation](https://doc.yak-ops.com/) for environment-specific configuration and deployment details.

## Architecture

Yak Ops separates product domains, runtime contracts, and integrations so that execution details do not leak through every layer of the application.

```text
┌─────────────────────────────────────────────────────┐
│                    yak-ops-ui                       │
│              React / Umi / Ant Design               │
└───────────────────────┬─────────────────────────────┘
                        │ HTTP / WebSocket
                        ▼
┌─────────────────────────────────────────────────────┐
│                   yak-ops-boot                      │
│                Spring Boot runtime                  │
└───────────────────────┬─────────────────────────────┘
                        │
          ┌─────────────┴─────────────┐
          ▼                           ▼
┌──────────────────────┐   ┌──────────────────────────┐
│   Business domains   │   │ Security / project      │
│ datasource / sync    │   │ RBAC / audit / context  │
│ development / job    │   │ operational boundaries  │
│ workflow / quality   │   └──────────────────────────┘
│ dataset / lineage    │
│ analysis / dashboard │
│ data service / alert │
└──────────┬───────────┘
           │ SPI
           ▼
┌─────────────────────────────────────────────────────┐
│ Plugins: datasource / storage / task / alert        │
└───────────────────────┬─────────────────────────────┘
                        ▼
       Databases / storage / Link-Up / Flink CDC / ...
```

At repository level, the main modules are:

```text
yak-ops
├── yak-ops-bom           dependency alignment
├── yak-ops-common        shared primitives
├── yak-ops-spi           extension contracts
├── yak-ops-core          core platform capabilities
├── yak-ops-business      product business domains
├── yak-ops-plugins       datasource / storage / task / alert plugins
├── yak-ops-boot          Spring Boot application
├── yak-ops-ui            web application
└── yak-ops-dist          release distribution assembly
```

Yak Ops also builds on [yak-framework](https://github.com/weifuwan/yak-framework) for shared infrastructure such as security, scheduling and workflow runtime capabilities.

## Project Space and governance

Yak Ops treats **Project Space** as the business workspace and data-isolation boundary inside the application. Roles answer *what a user can do*; project membership answers *where they can do it*; project ownership on business data answers *which workspace the data belongs to*.

This boundary is being applied across datasource, synchronization, development, workflow, quality, dataset, analysis, dashboard and data-service flows so list, detail, mutation and runtime paths follow the same isolation rules.

For the design baseline, see [`docs/architecture/PROJECT_SCOPE.md`](docs/architecture/PROJECT_SCOPE.md).

## Security

Yak Ops uses Yak Security for identity and permission abstractions and uses Sa-Token as the default authentication backend.

Datasource connections and data-processing tasks can reach external systems. A production deployment should explicitly review:

- project and functional permissions;
- network access and egress restrictions;
- datasource secret management and master-key handling;
- audit and operation-log retention;
- query and runtime limits;
- database backup, schema migration and upgrade procedures.

Do not put real production credentials or sensitive data into a public demo environment.

## Project status

Yak Ops is under active development. APIs, database schemas, navigation and module boundaries may continue to change as the project converges on cleaner end-to-end product flows.

That pace is intentional: the goal is not to freeze a large collection of features early, but to keep turning disconnected data-engineering operations into a smaller number of understandable workflows.

If you are evaluating Yak Ops for production, start with a non-production environment and validate the security, deployment and runtime assumptions that matter to your infrastructure.

## Contributing

Yak Ops is developed in the open. Bug reports, design discussions, documentation improvements and code contributions are welcome.

A good way to contribute is to:

1. search the existing [issues](https://github.com/weifuwan/yak-ops/issues);
2. open an issue for a bug, product gap or design proposal;
3. keep a pull request focused on one problem and explain the user-visible behavior it changes;
4. add or update tests and documentation where the change introduces a new contract.

Before contributing code, please read [`CODE_STYLE.md`](CODE_STYLE.md). Frontend changes should also follow [`yak-ops-ui/FRONTEND_CODE_STYLE.md`](yak-ops-ui/FRONTEND_CODE_STYLE.md).

If Yak Ops is useful to you, a ⭐ helps more people discover the project.

<img width="180" height="264" alt="image" src="https://github.com/user-attachments/assets/a4c596d4-61eb-4bdf-b452-28d19c5b7423" />

## License

Yak Ops is licensed under the [Apache License 2.0](LICENSE).
