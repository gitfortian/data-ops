# A0–A8 集成预演与合并准入（#461）

> **⛔ 仅用于隔离验收。所有架构 PR 保持 Draft；严禁自动合并。**

## 唯一验收入口

- 综合 PR：[#461](https://github.com/gitfortian/data-ops/pull/461)，包含 A8.2b–m 历史 Draft 的精确 Git 祖先，以及本次 A8.2n+ 收口。
- 工作流：`.github/workflows/architecture-a8-final-preview.yml`。仅在 #461 目标分支修改对应脚本/工作流后作为 PR CI 执行；允许手动调度，不自动 push/merge。
- 预演脚本：`scripts/architecture/a8-final-integration-preview.mjs`。继承 A7 #395 实测过的只读 Git API + 临时 detached worktree 流程，扩展为 17 个独立前置 PR。
- 保护测试：`scripts/architecture/a8-final-integration-preview.test.mjs`，拒绝 PR ID/分支/SHA/标签/Draft/目标漂移和不完整的回滚证据。

## 精确正序

1. A0–A2.2：#370、#373、#375、#385。
2. A3–A4.2：#379、#389、#380、#387。
3. A5–A6.1：#377、#391、#392。
4. A7–A7.1：#395、#409。
5. A8.1a–c：#417、#420、#423。
6. A8.2 综合：#461。

每个 PR 在执行时从 GitHub Pull API 读取并锁定 `head.sha`，然后用 `refs/pull/<number>/head` fetch；再次核实 fetch SHA 等于元数据 SHA。若在预演中途 PR 更新，直接失败而非自动接受新版本。以运行时最新 `main` 作为基线，仅本地执行 `git merge --no-ff`。如遇冲突，报告确切文件、阶段、SHA，失败并保留 Artifact；**不允许 `-X ours`、强制接受某一方或省略前置 PR**。

## 编译与运行门槛

组合树通过冲突检查后必须执行：

- 全量 Node 架构及工程契约测试、边界检查、Security 架构守卫、Flyway 历史/所有权检查；
- 前端 Jest、类型基线和完整发行构建；
- MySQL 8 完整 Maven Reactor `verify`，PostgreSQL 16 单独 Boot Smoke，不混用 profile；提供 Redis 7 运行服务，用于最新 Security 跨实例共享会话合同回归；
- 前端发行资源与 dist tar 包清单一致性。
- 组合构建成功后，在同一**临时 worktree** 逆序撤销每个本地 merge，核对每一步撤销后的树与该 merge 的第一父节点树一致，并要求最后的 Git Tree 精确等于起始 `main` Tree；将 `rollback.sourceTreeRestored` 和 SHA 保存到 Artifact。

所有步骤仅使用只读 GitHub Token，取消工作目录的持久 Git 凭证，禁止任何 GitHub 写操作。回滚只证明源码树恢复，不代表实际生产数据库/Redis 数据可回滚。

## 证据与剩余合并阻断

成功与失败均上传 `architecture-a8-final-preview-<run>-<attempt>`，包含完整 main SHA、17 PR HEAD、合并 commit SHA、冲突路径和源码树回滚校验。只有该工作流和 #461 同一 HEAD 上的四组 CI 都成功，才可申请人工审查。

需要独立核销的内容：真实非空生产 history 快照升级，旧版独立编译外部二进制调用方，跨进程 Redis 登录和注销、部署环境 HTTP 401/403、真实业务权限 E2E、数据库备份恢复和发布回滚。当前任何静态、MockMvc 或临时 CI 结果都不替代这些证据。**本 PR 不能自动合并，最终由用户手动批准。**
