# DataOps 架构收敛基线：提炼 Yak 架构思想，不迁移上游业务

> 状态：工程重构工作基线（A0 已提议，A1～A7 需逐批审查与验收）
>
> Issue: [#368](https://github.com/gitfortian/data-ops/issues/368) · 产品母路线：[#180](https://github.com/gitfortian/data-ops/issues/180)
>
> 对照快照（2026-10-08）：data-ops `324771ec51dbc0e5548a1437070470ac9db5687a`；参考 yak-ops `9fe490c1fddbba1869acd4b934438a79814bd8d9`。对照结果只代表上述源码审查，不代替运行时验收。

## 1. 目的和边界

目的是让当前 **data-ops** 的模块职责、依赖方向、基础设施装配和可替换能力更加清晰，而不是复刻另一个产品。

- **保留**：当前各业务域的用户能力、真实 Domain/Truth Owner、Application Facade、Repository Port、Project Space、RBAC、API/DTO、数据库主键与 migration 历史、Task/Execution/Attempt/Cursor 运行语义。
- **吸收思想**：Boot composition root、依赖只能单向流动、接口和具体实现隔离、Spring Boot/Starter 优先、Plugin/SPI 作为稳定边界、Architecture Guard、前端 App/Service/UI 分层。
- **延后评估**：DAO 是否合并为物理 Maven 模块、Framework 是否收敛为 Platform、YakFlow 是否成为候选 Runtime、UI 是否升级构建工具。
- **禁止机械迁移**：复制上游 `DataSourceService`/`DataSyncService`/数据库 `V1__baseline.sql`/String 主键/Workspace 权限模型/Vite 页面；不为减小目录数而删除现有能力。

现有仓库 `PRODUCT_STYLE.md`、`docs/product/**`、`CODE_STYLE.md` 与各业务域 `DOMAIN.md / ARCHITECTURE.md / DEPENDENCIES.md` 继续具有原来的效力。本文**不引入第二套产品事实或业务状态机**，仅描述跨模块工程架构演进策略。

## 2. 依赖架构（目标原则，不要求本轮迁目录）

```text
UI
 ↓ HTTP
Business HTTP Adapter（当前允许领域内 Controller）
 ↓ Application Facade / Use Case
Business Domain（Facts / Invariants / Lifecycle / Read Model）
 ├── Repository Port → owning Persistence Adapter → MyBatis / Schema
 ├── Gateway Port    → Plugin / Engine / External Adapter
 └── Cross-domain    → other domain's stable API / SPI

Boot Composition Root
 ├── Web / Security / Project request binding
 ├── Datasource / Transaction / MyBatis / Quartz wiring
 └── Business / Plugin / Runtime assembly

Dist → Boot（仅用于产品发行包）
```

**不可跨越的方向：** Core/Common/SPI/Plugin/Business 的生产代码不得 import `io.yak.ops.boot.*`；非 Boot、非 Dist Maven 模块不得依赖 `data-ops-boot`。发行包 `data-ops-dist` 依赖 Boot 是装配上的明确例外。Boot 可以依赖业务和基础模块，但不能成为它们的回调接口或业务 truth owner。

**Controller 位置不强制对齐上游：** 当业务域已有经过验收的 HTTP Adapter 时，维持在业务模块。统一 Web 异常、安全、拦截、应用装配，不要求把所有 Controller 同时搬进 Boot。

**DAO 不强制集中：** 当前业务数量远大于参考项目。先统一连接池、事务/SqlSession、MapperScan 和 Migration 的执行边界，再根据真实跨域依赖与部署要求判断物理 DAO 归属；防止单体 DAO 成为第二个 Shared Business。

## 3. 不可破坏的不变量

1. Project/RBAC：当前 `X-YAK-SECURITY-PROJECT-ID`、Project Long ID、细粒度 RBAC、项目归属与异步上下文恢复不变。
2. 持久化：已有独立 Flyway history/checksum 不动，必须通过正式 forward-only migration。不得将上游 SQL 当成当前 baseline。
3. 运行态：Task != Batch != Attempt；DefinitionVersion、Execution、RuntimeEnvironmentSnapshot、UNKNOWN/CONFLICT 与 Recovery Safety 由当前领域维护。
4. 跨域：只通过显式 API/Gateway/SPI 共享业务事实；禁跨域 Mapper/PO，禁引入新的 shared business truth。
5. 前端：当前 Umi/Ant Design/API/菜单与真实用户旅程不因架构优化退化；优先可执行 import boundary，不将替换工具链当作重构成果。
6. 结构改造与产品行为变化原则上拆 PR，避免一次变更同时修改 Maven、REST、Flyway、Domain Lifecycle 和 UI。

## 4. 实际代码锚点与待审内容

| 已确认事实 | 当前代码锚点 | 对应架构任务 |
| --- | --- | --- |
| 存在基于文件/import 的架构护栏与 Maven cycle 检查 | `scripts/architecture/check-boundaries.mjs` | A0 增量补 Boot 反向依赖 |
| Boot 手工注册 DataSource、TransactionManager、SqlSessionFactory、SqlSessionTemplate 与旧 Bean 别名 | `data-ops-boot/.../BusinessDatabaseConfiguration.java` | A2 统计消费者后收敛装配 |
| Asset 等业务域自有 Mapper 和 Flyway history | `data-ops-business/data-ops-business-asset/.../AssetPersistenceConfiguration.java` | A4 先库存盘点再试点，不能强行合并 |
| Project Context 已具备认证与 Project 校验 | `data-ops-boot/.../ProjectScopeInterceptor.java` | A3 保留现行安全语义 |
| 前端基于 Umi Max，已存在 import 检查脚本 | `data-ops-ui/package.json`、`scripts/architecture/check-frontend-boundaries.mjs` | A5 强化应用/服务层边界 |

## 5. 执行顺序和退出 Gate

- **A0（本 PR）**：新增 Boot 反向依赖机械护栏及正反单测。本 PR 不修改业务代码、POM、API、Schema、前端。
- **A1**：获得实际 Maven/reactor/package/import 依赖拓扑，列出现有结构、每个拟变更边界的消费者与测试；禁止先建空壳新模块。
- **A2**：Boot/持久化装配重构仅在确认消费者后启动。MySQL、PostgreSQL、Quartz、MapperScan、事务与历史 Bean 名兼容全部列入验收。
- **A3**：整合 Platform/Framework 的重复机制，不重写 Project、RBAC、审计等已发布产品语义。
- **A4**：Persistence Ownership 独立试点和 schema upgrade 回滚验证，按业务域逐一处理。
- **A5**：前端服务 HTTP 单入口/域间 import 守卫，保持原页面、菜单、路由可用。
- **A6**：YakFlow 作为独立数据面候选进行对照测试，未经吞吐、写入、恢复、安全与 E2E 证明，不替换 Link-Up/Flink。
- **A7**：全量编译、架构检查、数据库升级、兼容 API、Project/RBAC 和 Golden E2E 分别给出真实证据，未运行标记 NOT RUN。

## 6. PR Review 必答

1. 更改的真实 owning boundary 是什么？破坏了哪个既有结构性约束？
2. 直接消费者有哪些？是否存在合法的装配/兼容例外？
3. 当前业务事实、API、DB、RBAC、恢复状态是否保持不变？
4. 是否仅在必要时增加抽象，还是制造了额外的 Manager/Service/DAO/Runtime？
5. 是否同时补充行为测试与架构测试？现有检查有无被放宽？
6. 迁移失败如何恢复？本批 PR 能否独立撤销？

> 核心结论：**Yak 是参考架构，不是要迁入的业务产品；data-ops 的领域能力与运行真相是不可随意重写的主体。**
