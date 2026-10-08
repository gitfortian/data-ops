# A7：架构 PR 组合验收（隔离集成预演，不合并）

> [架构治理 Issue #368](https://github.com/gitfortian/data-ops/issues/368)
> 当前针对 data-ops 项目生产代码；**不涉及历史项目的业务迁移、上游代码导入或新产品需求**。

## 目标

11 个架构 PR 均来自不同日期的 `main`，分别通过静态与运行时测试，不等于一次性组合部署可用。A7 应验证 **组合树** 而非复用单 PR 绿色状态，特别是：

| 组合关注点 | 关联 PR | 必须在组合树验证 |
|---|---|---|
| Boot 反向依赖与 Maven topology | #370 + #373 | 基础模块不反向指向 Boot，无新依赖环 |
| Boot DataSource / MyBatis Session | #375 + #385 | 16 个 Spring 别名、一套 Session/事务、MySQL/PG |
| ThreadLocal Project / MVC Scope | #379 + #389 | 无跨 Project 泄漏；拒绝/异常清理 Audit Context |
| Flyway 归属与执行顺序 | #380 + #387 | 每个模块独立 history，运行时先后依赖和已发布 checksum |
| 前端分层、HTTP 错误与 SSE | #377 + #391 | Project Header、401/403、Cookie、SSE cursor、Abort |
| Offline/Realtime SPI 合同 | #392 | Source/Sink 多表和 Project 隔离、幂等身份/UNKNOWN-CONFLICT |

## 本 PR 交付

新增纯 Node `scripts/architecture/a7-integration-preview.mjs`（附 `.test.mjs`）及隔离验收 workflow `.github/workflows/architecture-a7-preview.yml`：

1. **安全门槛：** GitHub REST *只读*检索每个 PR 当前 metadata，要求 11 个全部为目标 repo 的 main PR、Open、Draft、`do-not-merge` 和 `architecture-refactor` 标签、受保护的 ⛔ 标题，以及预期架构分支；任何一项变化失败关闭（fail closed）。
2. **快照：** 每次运行单独读取当前 `origin/main`，将所有候选 PR 的完整 40 字符 head SHA 及合并顺序保存为 JSON，后续每次 `git fetch refs/pull/<n>/head` 时比对 SHA，避免 CI 跑到半途 PR 提交发生变化而形成不可复现的绿色验收。
3. **严格隔离：** 只在 `$RUNNER_TEMP` 路径新建 `git worktree add --detach`，使用本地 `git merge --no-ff` 逐项模拟合并。不修改真实 `main`、不推送、不创建分支、不修改或关闭任何现存 PR；GitHub Actions token 只授予 `contents:read`、`pull-requests:read`。
4. **冲突即阻断：** 任一 PR 合并冲突、PR metadata 不匹配、head SHA 不匹配立即失败；输出当时树 SHA、目标 PR SHA、冲突文件到 Evidence，不会视为通过。
5. **运行合同：** 在组合源码树运行所有 `scripts/architecture/*.test.mjs`、`scripts/ci/*.test.mjs`、迁移 history 和 Flyway ownership、Boot 依赖护栏、前端边界、持久化消费者清单。
6. **完整测试：** 安装前端锁定依赖，执行 Jest 全集 + 类型 baseline + build；MySQL 8 与 PostgreSQL 16 为本次 CI 创建两个隔离空库，运行 Maven reactor `verify`（包含 Boot 迁移 smoke / 权限 / Offline 与 Realtime 合同），校验 frontend build manifest 和发行包文件。
7. **证据保存：** `a7-combination-snapshot.json/.md`、`a7-persistence-consumers.json`、`a7-flyway-ownership.json` 通过 GitHub Actions artifact 保存 30 天，包含失败前的真实 PR head 精确 SHA。

## CI 触发与范围

- 只为 **`architecture/yak-a7-integration-acceptance-preview` 的 Pull Request** 自动执行；将来工作流进入默认分支后可手工 workflow_dispatch 复验。
- 这不是普通 PR 的强制 Gate，也不修改产品开发分支的 CI；它是架构批量合并前明确执行的一次独立组合验收。
- 组合 PR 随时可变，因此每次运行都要重新读取精确 heads；一次绿色结果只证明记录下来的那组 SHA，不证明未来其他代码自动兼容。
- 由于还没有合并权限授权，**禁止根据该结果自动切换 Draft/合并/关闭 Issue**。

## 首次组合预演真实发现（2026-10-08）

- A7 工作流首次成功按序模拟集成全部 **11 个 PR**，组合树 `14e39e0e51027afe35826c7fe2f9789545500c41`，基础 main `ffb9d8eff1afba16a6aa81721d8d3dad5dd5918a`；Git 层无合并冲突。
- 组合 Node 测试 **56 号用例失败**：#385 的 `persistence-consumers.test.mjs` 仅读取旧 Boot `BusinessDatabaseConfiguration`，未识别 #375 把 SqlSessionFactory/SqlSessionTemplate 声明移到被 `@Import` 的 `BusinessMybatisSessionConfiguration`。
- 已在 #385 **自身分支**调整测试为“两个配置均存在时一起审计；只有主类时仍兼容”，不改 #375 生产代码、不修改单 PR 产品行为。A7 后续重跑会读取 #385 新精确 Commit SHA，防止从失败状态直接宣称全部合格。
- 此信息是首次组合验收事实，不代表数据库/前端/发行包集成已完成；后续阶段仍需 CI 逐项验证。

## 第二轮 CI 实测问题与修复（2026-10-08）

- 组合模拟、全部 Node 架构测试以及前端 Jest（171 suites / 954 tests）、类型校验和 Build 均实际通过。MySQL 侧 `DatabaseMigrationSmokeTest` 的两项测试通过。
- 失败发生在单次 `mvn verify` 同时存在 `ARCHITECTURE_MYSQL_URL` 和 `ARCHITECTURE_POSTGRESQL_URL` 时：PostgreSQL Smoke 测试切换 URL，但 Spring Boot 的默认 `mysql` Profile 将 `com.mysql.cj.jdbc.Driver` 注入 PostgreSQL URL，导致 `PostgresqlStorageSmokeTest` Spring 容器无法启动。
- 已在 A7 Workflow 中**隔离两个数据库测试进程**：`env -u ARCHITECTURE_POSTGRESQL_URL SPRING_PROFILES_ACTIVE=mysql bash ./mvnw -B -ntp verify` 完成 MySQL reactor 全量；随后使用 `env -u ARCHITECTURE_MYSQL_URL SPRING_PROFILES_ACTIVE=postgresql` 和 Maven `-Dtest=PostgresqlStorageSmokeTest` 专项运行 PostgreSQL 容器及真实 PG 应用 Profile。没有禁用或伪造 PostgreSQL 验收结果。
- `scripts/architecture/a7-integration-preview.test.mjs` 追加工作流断言，避免未来两库的环境变量混在同一 JVM 构建回退。修复后的 A7 结果必须以新提交 CI 为准，不能据此提前标记 PASS。

## 不应越权宣称的范围

- 双数据库全新环境迁移 Smoke 与 SQL 版本校验并不等于**现网历史数据库的备份/升级/回滚演练**。
- 组合 CI 执行 Engine Gateway 单元/契约测试不等于已连接真实生产 Flink/Link-Up Worker；真实数据面性能、断网恢复、Checkpoint 和完整 Golden E2E 仍需具备隔离测试环境和人工审阅 Evidence。
- UI Jest / 类型检查 / build 不等于真实浏览器使用体验和 RBAC 端到端验收，必要时仍需浏览器测试环境。
- 所有基于测试数据库的操作仅针对 CI 临时容器和临时工作树，不运行到用户数据库。

## 用户最终授权前的合并准入

- [ ] 11 个 PR 各自 CI 无失败且 Draft/标签未改变
- [ ] A7 组合合并不存在冲突；组合树 `git` SHA Evidence 已记录
- [ ] 全量 Maven reactor、MySQL & PostgreSQL、Frontend Jest/Build、Dist 校验通过
- [ ] A7 UI/数据库/执行环境剩余真实 E2E 明确列出，无法验证的项目不标记完成
- [ ] 选择显式回滚策略：无 DB Schema 变更的架构 PR 可以按提交回退，但不能靠 `git revert` 逆转历史 Migration 数据变更
- [ ] 由用户在功能开发完成后明确确认合并，绝不由自动工作流执行

## 如何复验

Pull Request 打开后 GitHub 自动触发 `Architecture A7 Integration Preview`。结果以该 workflow 的运行日志和 `a7-combination-snapshot.json` 为准。若冲突，**修各自的架构 PR 分支后，再重新执行 A7**，不要在真实 main 上试错。
