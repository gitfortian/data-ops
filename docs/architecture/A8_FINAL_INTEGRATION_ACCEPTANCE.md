# A0–A8 集成预演与合并准入（#461）

> **⛔ 仅用于隔离验收。所有架构 PR 保持 Draft；严禁自动合并。**

## 唯一验收入口

- 综合 PR：[#461](https://github.com/gitfortian/data-ops/pull/461)，包含 A8.2b–m 历史 Draft 的精确 Git 祖先，以及本次 A8.2n+ 收口。
- 工作流：现有 `.github/workflows/architecture-checks.yml` 中的 `a8-integration` Job。该 Job 仅在 #461 的 PR 上启用；沿用已注册 Architecture Checks，不额外创建独立的 CI 工作流、不自动 push/merge。
- 预演脚本：`scripts/architecture/a8-final-integration-preview.mjs`。继承 A7 #395 实测过的只读 Git API + 临时 detached worktree 流程，扩展为 17 个独立前置 PR。
- 保护测试：`scripts/architecture/a8-final-integration-preview.test.mjs`，拒绝 PR ID/分支/SHA/标签/Draft/目标漂移和不完整的回滚证据。

## 精确正序

1. A0–A2.2：#370、#373、#375、#385。
2. A3–A4.2：#379、#389、#380、#387。
3. A5–A6.1：#377、#391、#392。
4. A7–A7.1：#395、#409。
5. A8.1a–c：#417、#420、#423。
6. A8.2 综合：#461。

每个 PR 在执行时从 GitHub Pull API 读取并锁定 `head.sha`，然后用 `refs/pull/<number>/head` fetch；再次核实 fetch SHA 等于元数据 SHA。若在预演中途 PR 更新，直接失败而非自动接受新版本。以运行时最新 `main` 作为基线，仅本地执行 `git merge --no-ff`。如遇冲突，报告确切文件、阶段、SHA 并保留 Artifact；默认失败关闭。**唯一已审查的例外**：#423 精确 SHA `60c5378d7ba90894526e4ff0dfa06f173c4bca52`，且唯一冲突文件为 `.github/workflows/architecture-checks.yml`，并且上游文件确实包含预期的 `check-common-security-corridor.mjs` 两行检查时，只把该检查显式插入当前组合树，记录 resolution 和 merge SHA。其他任何 SHA、路径、内容漂移全部失败。绝不使用 `-X ours`、宽泛强制覆盖或跳过前置 PR。

## #461 综合工作流冲突的审计收敛（2026-10-09）

- A0–A8 read-only CI artifact `11610412360` 显示：前十六个受保护 PR 已精确 SHA 正序临时合并；#423 的唯一工作流冲突已经通过受限插入其 Common/Security corridor 检查解决；最后 #461 的 `.github/workflows/architecture-checks.yml` 再度出现独立冲突。
- 仅允许在**第 17 步、PR #461、唯一该工作流文件**且阶段二 Git 内容严格等于当次 `main` 加三条已核实前置守卫时合并：#385 的 `check-persistence-consumers.mjs`、#380 的 `check-flyway-ownership.mjs`、#423 的 `check-common-security-corridor.mjs`。合并结果为 #461 原工作流加这三条守卫；移除三条后必须与 #461 原内容逐字一致。
- 任一来源 SHA/分支、冲突路径、上游内容或 Security 守卫漂移，均失败关闭并上传冲突快照；不自动接受未知冲突，也绝不修改任何远端 Draft PR。新校验需要以 #461 最新 exact HEAD 实际 CI 结果为准。

## 最终发行包 Security classpath 验收（新增自动化门禁）

- 在既有 Architecture Checks 的 `distribution` Job **完成 Maven reactor verify 和 dist tar 打包之后**，执行 `scripts/architecture/check-security-distribution-classpath.mjs`，且在 A0–A8 隔离正序组合树的完整构建后重复运行。**没有增加第五个独立 CI Workflow**。
- 探针 `scripts/architecture/probe-security-distribution.py` 直接读取 Boot 可执行 JAR 与 dist 内 `libs/yak-ops-api.jar` 的 SHA-256，要求完全相同。逐一扫描 Boot 内全部嵌套依赖 JAR 的 `io/yak/framework/security/**/*.class`，而非假定源码迁移就意味着发布 classpath 正确。
- 复用现有 Security API 75 类、Persistence 36 类、身份上下文、SPI/Runtime 和权限声明的迁移清单，逐一确认这些历史 FQCN 仅在其应有的 Contract、Runtime、Persistence JAR 中存在，且旧 Starter 不重复携带同名类型。四个 JAR 各必须恰好出现一次；任意 Security FQCN 重复、发行包 Jar 字节不一致、缺失或错误归属均失败。
- `check-security-distribution-classpath.test.mjs` 覆盖正确清单、缺失/重复/错误模块、篡改发行包、隐蔽重复与空清单等反向路径。
- **严格验收边界**：该检查可证实编译与最终发行制品包含唯一类归属，**不能证明**外部历史客户端以旧依赖编译过的二进制能在新发行版链接成功；后者仍需独立旧 JAR 消费者的真实二进制 smoke，不能仅凭 FQCN/classpath 通过而勾选。

## 迁移前独立编译消费者 ABI smoke（新增，待当前 CI 验收）

- 使用 PR 事件精确、不可变的 `main` 基线 SHA；在**临时 detached worktree** 中单独构建迁移前 `data-security-spring-boot-starter:0.1.0` 原始 JAR。构建过程不允许写回 `main` 或任何受保护 Draft 分支，完成后清理该 worktree。
- `scripts/architecture/fixtures/SecurityLegacyConsumer.java` 只使用旧 JAR 通过 `javac --release 21` 生成消费者 `.class`，从不拿新 Contract 或 Runtime 参与编译；覆盖 `PageParamDTO` 的 Lombok accessors、`User/BaseEntity`、`PermissionDefinition`、`YakPermission` 反射注解及 `AuthenticationManager` 默认方法。
- 运行阶段**不重新编译消费端**，也不加入旧版 JAR：从本 PR 实际可执行 `data-ops-boot-1.0.0.jar` 提取 `BOOT-INF/classes` 与新 `BOOT-INF/lib/*.jar`，直接执行原消费者字节码。任何 `NoClassDefFoundError`、`NoSuchMethodError`、`AbstractMethodError` 或行为不兼容均使现有 Architecture Distribution Job 失败。
- 仅 #461 的 PR 触发此项，复用已经生成的 Boot 发行包；不增加独立 Workflow。不把本次 smoke 冒称全部 142 个 FQCN 的外部生态保证；**独立第三方已发行消费者 JAR**及其真实依赖树仍需用户提供受信任的制品才能按相同方法验证。

## 编译与运行门槛

组合树通过冲突检查后必须执行：

- 全量 Node 架构及工程契约测试、边界检查、Security 架构守卫、Flyway 历史/所有权检查；
- 前端 Jest、类型基线和完整发行构建；
- MySQL 8 完整 Maven Reactor `verify`，PostgreSQL 16 单独 Boot Smoke，不混用 profile；提供 Redis 7 运行服务，用于最新 Security 跨实例共享会话合同回归；
- 前端发行资源与 dist tar 包清单一致性。
- 组合构建成功后，在同一**临时 worktree** 逆序撤销每个本地 merge，核对每一步撤销后的树与该 merge 的第一父节点树一致，并要求最后的 Git Tree 精确等于起始 `main` Tree；将 `rollback.sourceTreeRestored` 和 SHA 保存到 Artifact。

所有步骤仅使用只读 GitHub Token，取消工作目录的持久 Git 凭证，禁止任何 GitHub 写操作。回滚只证明源码树恢复，不代表实际生产数据库/Redis 数据可回滚。

## 证据与剩余合并阻断

成功与失败均上传 `architecture-a8-final-preview-<run>-<attempt>`，包含完整 main SHA、17 PR HEAD、合并 commit SHA、冲突路径和源码树回滚校验。只有 `a8-integration` Job 和 #461 同一 HEAD 上的四组 CI 都成功，才可申请人工审查。

需要独立核销的内容：真实非空生产 history 快照升级，旧版独立编译外部二进制调用方，跨进程 Redis 登录和注销、部署环境 HTTP 401/403、真实业务权限 E2E、数据库备份恢复和发布回滚。当前任何静态、MockMvc 或临时 CI 结果都不替代这些证据。**本 PR 不能自动合并，最终由用户手动批准。**
