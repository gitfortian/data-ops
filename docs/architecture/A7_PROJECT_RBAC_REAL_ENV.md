# A7.1：真实环境 Project / RBAC 只读验收

跟踪：[架构 Issue #368](https://github.com/gitfortian/data-ops/issues/368)。前置：[A7 组合验收 PR #395](https://github.com/gitfortian/data-ops/pull/395)。

## 真实代码依据

- 前端 `SecurityProjectSwitcher` 通过 `selectProject` 保存当前工作空间并刷新页面，`applyCurrentProjectHeader` 对 Project-required API 携带 `X-YAK-SECURITY-PROJECT-ID`。
- 后端当前身份 `GET /yak-security/api/v1/account/current` 的 `projectList` 是用户可进入 Project 的真相。
- 现有 `scripts/product/phase5-real-env-acceptance.mjs` 跨 Project / RBAC 探针是可选项，且未在**同一 Cookie 会话**下执行 A → B → A。新入口必须全部采集、不得跳过。
- 以真实的 `GET /api/v1/metrics/{id}` 作为隔离样本，不新建接口或修改数据。本批交付的是**真实部署 HTTP/API 验收入口**，**不是实际浏览器交互 E2E**。

## 执行方式与可信环境

受控执行器可调用 `node scripts/architecture/a7-project-rbac-real-env.mjs`。要求以下环境变量（缺失即失败）：

- `YAK_OPS_BASE_URL`：可信 HTTPS 根 URL（localhost 可 HTTP），不能携带 URL 密码、查询或路径。
- `YAK_OPS_A7_PRIMARY_PROJECT_ID` / `YAK_OPS_A7_SECONDARY_PROJECT_ID`：两个不同的 Project，同一 owner 属于两者。
- `YAK_OPS_A7_METRIC_ID`：primary 中的 Golden Metric，secondary 无权看到相同事实。
- `YAK_OPS_USERNAME` / `YAK_OPS_PASSWORD`：有两个 Project 成员身份，并可读取 primary 的 Metric。
- `YAK_OPS_DENIED_USERNAME` / `YAK_OPS_DENIED_PASSWORD`：**属于 primary 但无 Metric 读权限**的独立账号。不是成员不能将 403 当作 RBAC 的证据。
- `YAK_OPS_ACCEPTANCE_COMMIT` 可选，记录被验收的提交。

配套手动 workflow `architecture-a7-project-rbac-real-env.yml` 复用现有 Phase5 GitHub Environment 的目标地址 `vars.YAK_OPS_PHASE5_BASE_URL` 和专用 `secrets.YAK_OPS_PHASE5_*`；不接受手动任意目标 URL 和密码输入；可使用已有 `phase5-acceptance` 自托管执行器。

**注意：** GitHub 手动 `workflow_dispatch` 通常要求工作流已经存在于默认分支；本批保持 Draft、不合并，因此创建工作流不等于已经在远程实际执行。也可以从有信任边界的测试机直接执行 CLI，生成真实证据。

## 强制通过条件（Fail Closed）

1. owner Cookie 登录成功，`account/current` 包含两个 Project。
2. 同一 Cookie 会话依次查询 Project A → B → A；在 B 中**不能**读取 A 的 Golden Metric，返回 A 时事实必须稳定。
3. 不携带 Cookie 的匿名请求必须被 401/403 拒绝；仅提供 Project Header 不构成认证。
4. 受限用户**属于 A 的成员**，但携带 A Header 的 Metric GET 仍必须返回 403，才能记作 RBAC 拒绝证据。
5. 缺少所需身份、数据样本、权限拒绝或无法解析事实时，直接失败；无 SKIPPED 伪通过。
6. 输出 JSON 仅记录断言结果、样本 ID、HTTP 和业务状态；不记录 Cookie、口令或实体正文；Artifact 保存 30 天。

## 测试/后续边界

- `scripts/architecture/a7-project-rbac-real-env.test.mjs` 覆盖输入、Cookie 会话、同一人 Project 切换、匿名拒绝、低权限成员拒绝、数据泄漏 fail-closed。由现有 `scripts/architecture/*.test.mjs` 纳入 Architecture Checks。
- 未完成且不可冒称通过：真实浏览器下拉选择/刷新/403 UI、实际生产库历史迁移与回滚、Flink/Link-Up 真正执行与恢复。
- 按仓库既定策略：独立 Draft PR，保护标签，不合并、不变更 main、不修改既有架构 PR。
