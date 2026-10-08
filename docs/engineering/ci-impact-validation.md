# DataOps CI：PR 增量验证与 main 完整验收

关联 Issue [#342](https://github.com/gitfortian/data-ops/issues/342)，实施文件：`.github/workflows/architecture-checks.yml`、`scripts/ci/impact-plan.mjs`、`scripts/ci/check-architecture-gate.mjs`。

## 目标及基线

原 PR 检查在任何改动下都运行 `data-ops-boot -am verify`、全部前端 Jest/TypeScript/build，随后 Distribution 再执行一次根 reactor `verify`。这是高风险发布检查，而不是快速的日常改动回馈。新策略保留**产品治理（Product Guard）与 Architecture gate 原名称**，只优化 Architecture 的 PR 执行内容。

| 触发 | 强制执行 | 后端 | 前端 | Distribution |
| --- | --- | --- | --- | --- |
| PR：业务 Java 实现或测试 | impact/self-test + 静态架构、导入边界、数据库迁移历史 | 修改的 Maven 业务模块（实现变更再包含其 Maven 反向消费者）及 `-am test` | 无前端修改则跳过 | 跳过 |
| PR：领域 UI | 同上 | 无后端修改则跳过 | 相关 `src/pages/<domain>` / `src/services/<domain>` Jest + 全局 TypeScript baseline | 跳过 |
| PR：仅文档 | 同上 | 跳过 | 跳过 | 跳过 |
| PR：公共基础、API、POM、迁移、CI、未知路径 | 同上 | 原有 `data-ops-boot -am verify` | 完整 Jest / TS / build | 根 reactor `verify` + 发行校验 |
| `main` push / 手动触发 | 同上 | 原全量验证 | 原全量验证 | 原全量验证 |

### 变更识别与失效保护

1. `actions/checkout` 获取历史，`git diff -z <PR base SHA> HEAD` 与合并结果对比，包含新增、修改、重命名、删除；基础 SHA 缺失、无法获取 diff、空 diff、非预期路径，一律升级 FULL。
2. Maven 模块由仓库实际 `data-ops-business/data-ops-business-*/pom.xml` 识别；扫描 POM 的模块依赖，后端生产源码修改带出反向依赖闭包，再用 Maven `-am` 验证必要上游。独立单元测试修改只执行归属模块与其上游。反向依赖仅来自业务 POM，不把完整 Boot/Distribution 带入轻量档。
3. 发布 API、数据库版本迁移、聚合 POM、公共核心/插件、Boot、CI 工作流、脚本、前端共享组件/HTTP/构建配置影响范围难以安全界定，自动 FULL。不再用“没匹配到路径”推导为安全跳过。
4. UI 局部改动执行目录关联 Jest + TypeScript baseline，不做发布构建；涉及 shared UI 则 FULL。Semantic / Metric UI 交叉纳入两类页面测试。现存 `metric-checks.yml` 等专项工作流保持并行、互不替代。
5. Runtime 不获取外部 GitHub secrets，不运行 `eval` 或把文件路径拼接为 Shell 命令；Java 路径由实际业务 module + POM 验证，Jest 选择仅由固定 `src/pages/` 和受控 service mapping 得到。
6. `Architecture gate` 只在 impact 和所需任务成功时通过；对于显式不需要的任务，**仅接受其 `skipped`**。完整档全部三个大任务必须成功；不能用 Job 层 `continue-on-error` 绕过。

### 维护及更改模块

新增业务模块必须有自己的 Maven POM。新增前端跨业务共享设施时，保持不匹配→FULL 的保守默认；确认所有消费者的合同后，才将其升级为受控的局部路径。任何发布/生产前仍需验证同一 commit 的完整构建和 Golden Sample E2E，PR 部分校验不代表此项完成。

### 验收与回滚

- `node --test scripts/ci/*.test.mjs` 验证具体选择范围及 Gate 负例（非法跳过、基础检查失败、缺失计划等必须失败）。
- CI 自身改动触发 FULL，可以核验没有损坏完整发行路径。合并到 `main` 后的主线 Push 仍执行完整 Build、Jest、Maven reactor 和发行一致性检查。
- 查 `Architecture impact and static contracts` 的 Plan JSON 和 Summary 可定位选中的模块及原因。
- 如果出现漏测风险，首先将新路径加到 FULL 类别，或临时回退此工作流，不取消 Product Guard / Architecture gate。
- GitHub Required Checks 以稳定 `Architecture gate` 为必选条件；如仓库另有单独 Required job，必须在 GitHub 仓库保护配置中核查它们的可跳过行为，不允许出现“检查未创建但 Required 永久 Pending”。

**限制：** 该策略不缩短高风险 PR 和 `main` 的完整发行验收；加速来自避免不相关模块、完整前端和 Distribution 在普通 PR 上重复执行。真实运行时长需采集 GitHub Actions 数据后对比，不预先声称具体比例。
