# P0 第 3/4 个综合 PR：Dataset Query / Data Service Invoke 真实 Source Audit 与 Usage 闭环

> **范围**：[#336](https://github.com/gitfortian/data-ops/issues/336) 的第三个、且仅有一个的综合 PR。以 #489 已合并的 `main@656e94ed445740fcced5ae1e274d535ae9b6d157` 为代码核销基线；此 commit 是仓库源码基线，**不是当前运行的部署身份**。
> **状态**：代码/静态证据收口；没有获得受控部署账号、真实查询与调用报告或 QA 签收时，一律为 `BLOCKED_REAL_ENV_EVIDENCE`，不得假报 `E2E_PASS`。

## 1. 两条消费链路的事实主权

| 来源 | 真实成功触发 | 来源事实主权 | Consumption 归一化事实 | 正反例界限 |
| --- | --- | --- | --- | --- |
| Dataset | 现有 `POST /api/v1/datasets/{id}/query`，返回稳定 `queryId` | Dataset `DatasetQueryPerformance` 必须持久化 `SUCCESS`，携 `datasetId + datasetVersionId + versionNo + queryId + subject` | `DATASET_QUERY_PERFORMANCE:query:<queryId>` 去重，`DATASET:<id>` 产品，固定版本、稳定 USER Consumer 和 QUERY 模式；Impact 可由精确 provider evidence ref 回链 | `REJECTED/FAILED/TIMEOUT` 是诊断事实，**不是成功 Usage**；旧版本审计不能被新版本窗口挤走 |
| Data Service | 现有公开 runtime `GET` + 有效 API Key，**不携后台 Cookie/Project Header** | Data Service `InvocationRecord` 持久化 `success=true`，记录 `id + projectId + apiId + consumerId + apiKeyId + sourceRevisionId` | `DATA_SERVICE_INVOCATION:invocation:<id>` 去重，`DATA_SERVICE:<id>` 产品，精确 revision + API_INVOKE，稳定 managed Consumer；Impact 回链 | 拒绝/无效 Key/失败调用仅为 Source Audit，不产生虚假成功 Usage |

### 本批代码修复（全部位于一个 PR）

1. **Dataset Usage 历史恢复**：`DatasetUsageEvidenceSynchronizer.recoverSuccessfulVersionPage` 在归一化之前验证所有持久化 audit ID 必须以 exclusive cursor **严格递减**，不得重复、倒序错误或超过 page limit；不让异常页生成部分 Usage 或虚假继续游标。兼容 Java `Long.MAX_VALUE` 正当首条 ID。
2. **Data Service Usage 历史恢复**：`DataServiceUsageEvidenceSynchronizer.recoverSuccessfulRevisionPage` 补上与 Dataset 一致的严格递减/越界/页长保护，保留既有缺失 durable ID 拒绝，按字符串保留 BIGINT 身份。
3. **Dataset 事件域隔离**：`DatasetUsageEvidenceNormalizer.normalize(projectId, event)` 如果当前 Project 与来源事件的 Project 不一致，则返回 `GAP`，不得用任意恢复上下文将另一项目的成功事实写成 Usage。
4. **回归**：Dataset、Data Service 恢复分页各覆盖重复 ID 与 exclusive cursor 不一致；Dataset 正反 Project 事件，原成功、失败、幂等和精确版本测试保留。

## 2. 实际执行入口：沿用现有 Phase 4，不制造伪调用

不引入另一套 Source Audit/Usage 真相，不增加新消费业务 API 或 Sample 数据写入。现有脚本已经完成真实请求：

- `scripts/product/phase4-real-env-dataset-evidence.mjs`：登录、已有 ONLINE Dataset 的精确版本 Query、回读稳定 `queryId` 对应的 QueryPerformance。
- `scripts/product/phase4-real-env-data-service-evidence.mjs`：管理登录仅作配置/审计查询；公开面有效 Key 真实调用、回读持久化 InvocationRecord、调用现有 Usage 同步接口并回读归一化结果。
- `scripts/product/phase4-real-env-negative-evidence.mjs`：已有无效 Key 拒绝，按可用环境扩展跨 Project/受限账号。
- `scripts/product/phase4-real-env-acceptance.mjs`：运行上述三条已有入口，再核对两个 Product 的 Canonical Detail / Source Navigation / Impact。本批给该脚本增加 `p0-source-usage-evidence-contract.mjs` 精确比对，不接受“有 Usage 就算成功”。

新增证据校验分为：
- **Dataset**：`Dataset.query.result.queryId == Dataset.QueryPerformance.queryId`，审计状态必须 `SUCCESS`；来源 Dataset ID、不可变版本 ID、版本号与 Canonical activeVersion 均必须一致；Consumer Impact 必须带同一 `DATASET_QUERY_PERFORMANCE:query:<queryId>`。
- **Data Service**：InvocationRecord 必须 `success=true` 且有明确 Project、apiId、sourceRevisionId、sourceRevisionNo、consumerId、apiKeyId；必须与源定义及 Canonical activeVersion 对齐；Usage 必须 `NORMALIZED/SUCCESS`、Project/Consumer/SourceRevision 与来源审计一致；Usage 和 Impact 均指向原 invocation ID。
- **精度和缺口**：比较 BIGINT / exact version 按字符串进行，不使用有精度损失的 Number 转换。缺失、UNAVAILABLE、失败、错误来源项目、错误审计 ID、旧版本都必须阻断验收，不创建假成功记录。
- **离线测试**：`node --test scripts/product/p0-source-usage-evidence-contract.test.mjs`，覆盖成功、失败来源状态、版本漂移、丢失回链、BIGINT ID、跨 Project/Consumer、Impact UNAVAILABLE。

真实环境由 QA/Release 在**授权的非生产 Golden 专用 Project**执行（保持当前部署与受控来源）：

```bash
# 由授权环境预先注入 YAK_OPS_USERNAME、YAK_OPS_PASSWORD、YAK_OPS_PROJECT_ID、
# YAK_OPS_DATASET_ID、YAK_OPS_DATA_SERVICE_ID、YAK_OPS_DATA_SERVICE_API_KEY；
# YAK_OPS_BASE_URL 与可选 YAK_OPS_PUBLIC_BASE_URL 指向已核验部署
node --test scripts/product/p0-source-usage-evidence-contract.test.mjs
node scripts/product/phase4-real-env-acceptance.mjs > p0-source-usage-live-receipt.json
```

**注意**：虽然本批新增的纯证据校验函数不写业务数据，但 Phase4 的真实运行脚本会执行 **真实 Dataset Query、Data Service Invoke 与已有 Usage normalize POST**，产生真实源审计及 Usage；因此绝不能未经 QA/数据 Owner 授权就运行，也不能称“只读”。不保存原始 API Key、Cookie、Token 或生产业务行；关闭 `YAK_OPS_EVIDENCE_INCLUDE_ROWS` / `YAK_OPS_EVIDENCE_INCLUDE_PARAMS`。如需保存回执，使用受控存储路径并人工核验脱敏。

## 3. 本 PR 测试、CI 与真实验收不可混淆

| 验收段 | 本批交付证据 | 当前判定 |
| --- | --- | --- |
| 两条历史来源页严格 cursor/limit 验证 | Java 单测、源代码差异，最终 GitHub Actions head | `PENDING_PR_CI` |
| Dataset 成功事件 Project 一致性 | 正负 JUnit，不能凭错误上下文生成 Usage | `PENDING_PR_CI` |
| 两条真实来源 + Usage 回链检查 | 现有 Phase4 runner + 新增 6 个离线断言测试 | `PENDING_PR_CI` |
| 真正成功 Source Audit 与稳定 Consumer 实例 | 必须由已授权部署真实 Query/Invoke 生成，匹配 source version / Project | `BLOCKED_REAL_ENV_EVIDENCE` |
| 无效 Key、受限身份、失败/拒绝及重试 | 现有 negative runner；QA 回读源审计与 Usage 前后数量一致性 | `BLOCKED_REAL_ENV_EVIDENCE` |
| Source Audit 历史损坏/Reader 失联/Usage 恢复幂等 | 按 Golden 现有 R3/R5/R6 安全故障和恢复流程，禁止虚造历史记录 | `BLOCKED_APPROVED_FAULT_INJECTION` |
| 跨真实浏览器、构建签收、退出决定 | 固定第 4 个综合 PR | `NOT_STARTED` |

QA 回执必须带 `deployedCommit / runtimeProcessAttestation / backend+frontend artifactHash / Project / PermissionSnapshot / exactDatasetVersion + queryId + persistedStatus / exactServiceRevision + invocationId + consumerId / normalizedUsageIdentity + providerEvidenceRef + idempotenceBeforeAfter / sourceNavigation + canonicalProductKey / executionTime / reviewer`。**main SHA、Mock 成功、工作流绿色、历史 10-04 报告均不能单独替代上述实际证据**。

结论：本批只修既有正确性和实际验收证据合同。后续只剩第 4 个综合 PR，不再开补丁 PR；当前 `#336 OPEN / GOLDEN=BLOCKED_EVIDENCE / P0_EXIT_NOT_SIGNED`。
