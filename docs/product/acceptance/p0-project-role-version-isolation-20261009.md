# P0 PR 2/4｜双 Project、受限身份与版本隔离综合验收

> **2026-10-09 / #336 / 四个固定综合 PR 中的第二个。**
> 源码核查基线：`main@714e09ecfcf38fa3f8c311a7a4a17b641215d74b`（包含已合并 #487）。此 SHA 不是测试环境中的已部署进程身份。本文件是 **可执行计划与可复查的测试证据索引**，不冒充真实登录态的验收结果。

## 1. 单 PR 全量范围与代码修复

本批覆盖既有 Project Guard、Approval、Semantic、Metric/Modeling 及消费域已有隔离回归，限于既有正确性；不开发新权限模型或 AI 功能，不复制 PR 3 的 Source Audit/Usage，也不实施 PR 4 的 Golden 退出裁决。

- **Project 授权闭环**：`YakSecurityProjectAccessGuard.requireAccessible(requestedProjectId, actor)` 先查项目存在性、账号身份及 owner/member，再确定 `ProjectContext`。本批修复：底层项目详情若错误地返回另一项目 ID（或 null），不得凭另一项目的 owner/member 信息放行、也不得绑定另一个 Project。对外统一隐藏为 `PROJECT_NOT_FOUND`，审计 `PROJECT_NOT_FOUND`（请求 ID）；owner A 不天然具备 B 权限。membership 列表里的 null 记录安全拒绝。
- **审批对象隔离**：承接 #487 的 `ApprovalService` 当前 Project + 未删除实例的查询；新增回归：同一 ID 如果落在另一个 Project，`detail/approve/cancel` 不得更新步骤或实例；在途重复发起即使先验查询无记录，唯一索引并发冲突仍拒绝、不得创建步骤或触发回调。
- **精确版本/重复提交**：复用已合并 #479 的 `SemanticFieldServiceTest.statusChangeUsesFieldVersionAndRefusesConcurrentWrites`、#480 的 `MetricRepositoryAdapterTest` / `MappingRepositoryAdapterTest`（project/version CAS 与 0 行拒绝）、#483/#487 的审批重复处理和 Project 过滤、#486 的 Usage Savepoint。测试链接和判定均是已合并代码的工程证据，**不是两名真人同时编辑的 E2E**。
- **消费域隔离**：既有 `DatasetGoldenCrossProjectIsolationTest` / `DataServiceGoldenCrossProjectIsolationTest` 只证明测试注入的读边界，不等于 Project A/B 两个已登录账号的真实访问；这两条用户消费链路的 Query/Invoke 与 Source Audit 归 PR 3。

## 2. 真实环境读探针：三种登录身份，两个项目，两个 Metric

新增非破坏性脚本：`scripts/product/p0-project-role-version-acceptance.mjs`。**只对已有 Metric 做 GET；唯一 POST 是正常账号登录**。没有自动编辑、审批、撤权、重复发起、发布或数据库写入。

### QA / Release 的先决条件

必须由 QA/Release 提供经授权的**隔离非生产**环境和进程/产物 attest 收据、两个分别有 Metric READ 的项目独立成员、以及属于 A 却无 Metric READ 的第三身份。A 用户**不得是 B 的成员**，B 用户**不得是 A 的成员**，受限用户必须属于 A 且确定未获 READ。Metric A/B 必须是现存可读且分属不同项目的两个合成对象，各提供已存在的精确版本；不要使用生产敏感数据或无授权的“试探”。

环境变量（凭据仅放在 CI Secret、受控终端环境中，禁止写入报告/日志/PR 评论）：

| 环境变量 | 作用 |
| --- | --- |
| `YAK_OPS_BASE_URL` | 已核对部署 URL（HTTP(S)，建议内部 HTTPS） |
| `YAK_OPS_PROJECT_A_ID` / `YAK_OPS_PROJECT_B_ID` | 不同 Project 的数字 ID |
| `YAK_OPS_PROJECT_A_METRIC_ID` / `YAK_OPS_PROJECT_B_METRIC_ID` | 已存在的项目内合成指标 ID |
| `YAK_OPS_PROJECT_A_METRIC_VERSION` / `YAK_OPS_PROJECT_B_METRIC_VERSION` | 各指标已有的精确版本号 |
| `YAK_OPS_PROJECT_A_USERNAME` / `YAK_OPS_PROJECT_A_PASSWORD` | 仅 A 成员、具 A Metric READ |
| `YAK_OPS_PROJECT_B_USERNAME` / `YAK_OPS_PROJECT_B_PASSWORD` | 仅 B 成员、具 B Metric READ |
| `YAK_OPS_RESTRICTED_A_USERNAME` / `YAK_OPS_RESTRICTED_A_PASSWORD` | A 内真实受限账号，无 Metric READ |
| `YAK_OPS_DEPLOYED_COMMIT` / `YAK_OPS_DEPLOYMENT_RECEIPT_REF` | 40 位构建提交 + 非敏感部署证明索引，**脚本本身不能独立验证实际进程** |

命令（具备以上密钥与授权后由 QA 在隔离部署上执行）：

```bash
node --test scripts/product/p0-project-role-version-acceptance.test.mjs
node scripts/product/p0-project-role-version-acceptance.mjs > project-role-version-receipt.json
```

**不得提供带认证 query string 的部署收据链接**，报告只允许稳定的内部索引。身份、Cookie、Token、返回体、SQL 均不写入 JSON。

脚本检查九条真实 HTTP 读路径：A/B 同项目 Metric 读取、A/B 精确版本读取、A 用户读取 B 项目资源拒绝、B 用户读取 A 项目资源拒绝、跨作用域同 ID 查找不得拿到原资源、A 内受限用户无 READ 必须被拒绝。它严格区分 `FOUND`、`DENIED`、`ABSENT`、`AUTH_FAILURE`、`UNAVAILABLE`、`INDETERMINATE`、`WRONG_ID`；401/登录重定向、500/内部错误、非结构化 200 **不能冒充“已拒绝”**。

- 脚本报告 verdict 为 `PROBES_PASS_PENDING_QA` 仅表示读探针断言通过；**不表示 Product E2E 通过**，且 `deployment.independentlyVerified=false` 必须由 Release 另行验证运行中进程身份。
- 任一反例暴露或正例不可读：`FAIL`，退出码 1；缺环境/登录失败/超时等：`BLOCKED_OR_EXECUTION_FAILED`，退出码 2。
- 本 Agent 未获得部署实例、真实 A/B/R 凭据或审核许可，故本 PR **不记录任何直播验收 PASS**；只交脚本及在仓库静态测试可跑的入口。

## 3. 版本并发 / 重试真实验证（人工授权的受控样本）

这些属于修改事实的动作，**不自动运行，不允许在生产执行**。QA 先确认两个测试账号的有效权限、同一合成对象 ID 与 expectedVersion，后台应用库 SQL 只读观测，按现有接口对照：

| 场景 | 操作与预期 | 工程现存保护 | 当前真实判定 |
| --- | --- | --- | --- |
| V-01 Semantic 同字段并发启停 | A 内两位有权维护者读 N；其中一个提交 N→N+1，另一个按旧版本提交须报 VERSION_CONFLICT，不得覆盖 | #479 / `SemanticFieldServiceTest` | `BLOCKED_REAL_ENV` |
| V-02 Metric 草稿旧版本 | 同项目两维护者从 N 编辑，先者成功，后者按旧 N 更新应 CAS=0；清空引用需在真实数据库回读 NULL | #480 / `MetricRepositoryAdapterTest` | `BLOCKED_REAL_ENV` |
| V-03 Modeling 字段映射来源变更 | 一方删除/修改源列，另一方旧版提交应拒绝 0 行/CONFLICT，不得回填旧引用 | #480 / `MappingRepositoryAdapterTest` | `BLOCKED_REAL_ENV` |
| V-04 Approval 重复与权限 | 同一项目同一业务对象并发发起：仅一笔在途；两人对同一待办重复决定：仅一次终态及回调；未参与者/跨项目用户无法审批 | #483/#487 + 本 PR 审批测试 | `BLOCKED_REAL_ENV` |
| V-05 撤权与切项目 | 使用已有受控账号撤销成员或相关 READ/APPROVE 权限，原 Cookie 再查询/动作不得泄露旧项目数据，恢复后正常 | Project Guard 与业务 `@RequiresPermission` | `BLOCKED_REAL_ENV` |

对于以上涉及 SQL update、重复 POST、撤权操作的人工验收，必须使用独立可清理的合成资源、明确授权/恢复步骤及操作前后版本+Audit 对账；PR 2 不为实现 E2E 偷增新产品能力。

## 4. 本批验收回执与退出边界

| 证据 | 状态 | Owner / 下一步 |
| --- | --- | --- |
| 已合并 #487（B-01～B-06 代码/回归合账） | `MERGED + CI_SUCCESS`；不代表在线 E2E | Repo/模块 Owner |
| Project Guard 详情 ID 绑定修复 + Approval 负向回归 | `PR_2_PENDING_CI`（须使用本 PR **最终 head**） | 后端 Owner + GitHub Actions |
| 真实 A/B/R 登录读探针 | `BLOCKED_DEPLOYMENT_OR_CREDENTIALS` | QA/Security/Release 提供非生产部署、角色与密钥 |
| 版本冲突 / 并发重复 / 撤权实测 | `BLOCKED_SAFE_FIXTURES_AND_OPERATORS` | QA + 对应 Truth Owner |
| 真实 Dataset/Service Source Audit/Usage | **PR 3 范围，未开始** | QA/Consumption Owner |
| Golden 浏览器、故障恢复、正式签收 | **PR 4 范围，未开始** | QA/Release/Product |

签收所需：`mainSHA / buildHash / processDeployedCommit / roleMembershipPermissionReceipt / syntheticProjectA+B / exactVersion / requestStatus / rejectionReason / auditId / beforeAfterDB / negativeCase / actualEvidenceArtifact / QA reviewer`。实际部署 SHA 不可只依赖操作者提供的环境变量；Release 必须把运行中的进程、JAR 和前端构建进行独立匹配。

**目前 #336 状态不变：`OPEN / GOLDEN=BLOCKED_EVIDENCE / P0_EXIT_NOT_SIGNED`**。本阶段始终保持四个 PR 的粒度，CI 失败只回修本 PR，用户手动合并。后续仅实施第 3、4 个综合 PR，不拆出“跨项目一条规则一个 PR”。
