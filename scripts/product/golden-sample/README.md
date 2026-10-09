# Golden Sample：物理表治理验收种子包

这是 R1 首批交付工具，复用已批准的来源与治理 API，不新增产品入口、状态机或业务真相。

## 产品范围

- User：治理人员、验收人员。
- Problem：空环境和不一致样本导致资产身份、质量结果及专业回链无法重复验证。
- Capability：初始化隔离样本，执行真实质量检查，核对 Asset 分区及项目隔离。
- User Journey：来源库 → Metadata 采集 → Asset 对账 → 查看证据 → Quality 专业运行记录。
- Expected Outcome：正常检查 PASSED、异常检查 NOT_PASSED、未配置监控 EMPTY；来源身份稳定，跨项目查询不泄露对象。
- Truth Owner / Producer：Datasource 持有连接配置，Metadata 持有采集事实，Quality 持有监控和执行事实，Security 持有 Project；Asset 持有台账并聚合来源证据。
- Consumer / Reuse：验收人员消费脱敏清单与证据；复用现有 Project、Datasource、Metadata、Quality、Asset API。
- E2E acceptance evidence：本批是登录态 API 验证；浏览器、受限角色、故障注入及完整 Phase7 尚待执行。

依据：[PD-001](../../../docs/product/decisions/PD-001-asset-governance-hub.md)、[F-001-A](../../../docs/product/features/F-001-A-asset-section-contract.md)。实施范围与剩余工作见[首批实施记录](../../../docs/product/acceptance/golden-sample/implementation-20261004.md)。

## 样本

| 表 | 数据 | 预期 |
|---|---|---|
| `golden_orders` | 三行合成订单，quantity 全部非空 | 非空监控 PASSED |
| `golden_orders_bad` | 三行合成订单，一行 quantity 为空 | 引擎 SUCCESS，检查 NOT_PASSED |
| `golden_customers` | 三行合成客户，电话为 SYNTHETIC 字符串 | 已纳管未配置监控；Quality EMPTY |

电话仅是敏感字段候选，不声称已完成分类或脱敏验收。物理表 Lifecycle 按现行 Model-only 契约返回 NOT_APPLICABLE。

数据库固定为 `yak_golden_sample`。工具只向这个来源库写入 SQL；平台业务对象一律通过所属领域 API 创建。样本项目为 `Golden Sample 治理验收`，空的隔离对照项目为 `Golden Sample 治理验收 对照`，均带 `yak-golden-sample-v1` 标记。

## 运行

从仓库根目录执行。普通模式只打印计划，不登录、不连接数据库。

```powershell
python -m pip install -r scripts/product/golden-sample/requirements.txt
python scripts/product/golden-sample/bootstrap.py

# PowerShell 7；密码输入不进入命令历史。
$env:YAK_OPS_BASE_URL = 'http://localhost:18081'
$env:YAK_OPS_USERNAME = Read-Host '验收账号'
$env:YAK_OPS_PASSWORD = Read-Host '验收密码' -MaskInput
$env:YAK_GOLDEN_MYSQL_HOST = '127.0.0.1'
$env:YAK_GOLDEN_MYSQL_PORT = '3306'
$env:YAK_GOLDEN_MYSQL_USERNAME = Read-Host '样例库账号'
$env:YAK_GOLDEN_MYSQL_PASSWORD = Read-Host '样例库密码' -MaskInput

python scripts/product/golden-sample/bootstrap.py --apply --dept-id 1 --output docs/product/acceptance/golden-sample/runtime-manifest.local.json
python scripts/product/golden-sample/acceptance.py docs/product/acceptance/golden-sample/runtime-manifest.local.json --output docs/product/acceptance/golden-sample/physical-acceptance.local.json
python -m unittest discover -s scripts/product/golden-sample -p 'test_*.py'
```

`--dept-id` 必须是现有部门；首次创建需要项目管理权限，后续复用不需要再次传入。账号还需具备 Datasource、Metadata、Quality、Asset 对应权限。样例库账号需有这个隔离库的建库、建表和读写权限。后端与初始化工具看到的 MySQL 主机不同时，用 `YAK_GOLDEN_JDBC_HOST` 指定后端的连接地址。

`YAK_OPS_BASE_URL` 应指向经过核验的运行实例。`18081` 是本次独立验收实例；`18080` 保留给既有 Link-Up 配置，工具默认 API 地址仍为 `8080`。如已核对运行产物，可通过 `YAK_OPS_ARTIFACT_SHA256` 记录其 SHA256；当前源码 HEAD 单独记录，不能当作部署版本。

## 重复运行与失败

来源行使用 `INSERT IGNORE`，保留已纠正或修改的数据。已有表必须全部是本包约定的三张表，并带所有权标记；发现其他表就拒绝初始化。项目、数据源、采集任务及监控同名冲突或来源坐标不一致时拒绝复用，不覆盖既有配置。分页不完整或同名多对象也会失败，不任意选择一个对象。

采集匹配使用 SQL 通配式 `golden_%`，不使用 `*`。采集任务保持停用、监控手动运行、通知关闭；不会向他人发送演示通知。项目仅配置负责人，不把同一账号重复加入普通成员列表。

验收脚本会执行两个样本监控，随后读取三张物理表的八个分区。它验证执行结果、Truth Owner、五状态、来源坐标、稳定查询及真实 Quality 回链。对照项目下的对象详情和分区必须返回明确的 48001；HTTP 500 或其他错误不算隔离成功。

初始化会新增采集历史，验收会新增质量执行历史，因此业务对象身份幂等，执行历史按实际发生追加。修正异常订单后，异常检查预期仍为 NOT_PASSED，验收将失败；工具不会自动把数据改坏来制造通过结果。

输出清单不包含密码、Cookie 或连接参数。`*.local.json` 已忽略；提交证据时使用经检查的日期快照。当前成功结果仅为 `PHYSICAL_SAMPLE_PASSED`，Phase7 / PD-001 保持 PARTIAL。

## R2：Dataset / Data Service 消费样本

依据 ACCEPTED PD-002 / APPROVED F-004，consumption.py 通过来源工作台 API 保存草稿、
发布不可变版本、上线服务、配置独立 Consumer grant，并完成两类 Asset 对账。
真实查询复用 Dataset 物理列安全裁决；公网 Invoke 只发送 Consumer API Key，
不发送 Console Cookie 或 Project Header。订阅不授予访问权限，也不代替 Usage。

先完成物理样本 bootstrap，再执行：

```powershell
python scripts/product/golden-sample/consumption.py --manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json
python scripts/product/golden-sample/consumption.py --apply --accept --manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json --output docs/product/acceptance/golden-sample/consumption-acceptance.local.json
```

需要现有 Data Development 编辑/发布/上线、Dataset 查询、Data Service 访问/观测及
Asset 读/更新权限。YAK_OPS_NODE 可指定 Node 可执行路径。只使用专用标记项目和数据源；
同名节点、Consumer grant 或草稿内容被修改后拒绝覆盖。新节点身份先写入本地进度文件，
后续配置失败可恢复；不依据名称认领既有未配置节点。--apply 必须指定 --output。

重复运行保留 Dataset/Service/Node/Consumer/Subscription 身份，发布遵循源域幂等规则。
工具只轮换其专用 Consumer 的标记 API Key，旧演示 Key 失效；该 Key 不应供业务使用。
secret 仅在本次进程内用于调用，不写清单/证据，不回显失败子进程输出。

--accept 会实际 Query/Invoke、新增真实 Usage、验证 exact evidence 与 Consumer identity，
并两次订阅核对幂等。还会用无效 Key 和匿名管理请求验证 401、在对照 Project
验证产品 NOT_FOUND 与 Asset NOT_INDEXED，
通过来源工作台临时下线专用服务并 finally 恢复，核对版本保留、availability 与调用恢复。
下线现有实现可能返回 HTTP 500；证据记录这个局限，不冒充完整 503 契约。

### R2 当前 main 的 Golden 证据校验约束

`--accept` 必须证明 **成功执行记录 → 同一稳定 Consumer → 实际使用的精确不可变版本 ID → 同一版本的 ProviderEvidenceRef**，不能只检查 Consumer 级别出现了某个来源证据 ID。这个校验使用现有 Consumption Impact 的 `observedVersions`，不是重新记录执行事实。

Data Service 审计 ID 可能是超过 JavaScript 安全整数范围的 BIGINT：证据脚本通过 `lossless-json.mjs` 解析并以 `BigInt` 比较新旧调用 ID；必须找到**同一 API、成功调用、精确来源 Revision**，不能回退选用别的 Revision。无效 Key / 服务下线拒绝后，Golden Sample 复查成功 Usage 引用保持不变；服务恢复并真实 Invoke 后，必须出现同一 Revision 的**新增**成功 Usage 引用，否则拒绝出具恢复通过结论。

针对以上断言的可重复离线单测由 `.github/workflows/golden-consumption-evidence.yml` 在脚本相关变更时独立执行，只校验源码与模拟证据契约，**不连接真实应用、不制造浏览器/跨 Project 验收已完成的结论**。本地可以分别运行：

```powershell
python -m unittest discover -s scripts/product/golden-sample -p 'test_*.py'
node --test scripts/product/golden-invocation-identity.test.mjs
```

### 精确不可变版本 Impact 的 Golden 验证

`consumption.py --apply --accept` 现在会在两类专属 Sample 真实 Query/Invoke 后，分别调用带 `sourceVersionIdentity` 的 Consumption Impact 两次。检查内容：

- 本次返回的每条**实际成功 Usage** 都属于请求的精确 DatasetVersion / Data Service Revision，并且保留正确的 Consumer、模式和原始 Query/Invocation EvidenceRef。
- 相同精确版本重复读取不能增加成功 Usage 次数、引入重复 EvidenceRef；来源失联、归一化 GAP、200 条窗口达到上限必须拒绝给样本出具全量结论。
- 使用隔离对照 Project 读取相同 `productKey + sourceVersionIdentity`，不能看到原 Project 的精确版本 Usage 或 EvidenceRef（Subscription 不能代替实际 Usage）。

`test_consumption.py` 提供纯离线 fixture 正反例，CI 的 `Golden Consumption Evidence Contract` 只运行这些静态/模拟契约，不调用真实部署。

**已验收范围须严格区分：** 当前脚本仅验证专属 Sample 的**当前已执行版本**、重复读取和 Project 隔离；**不会自动生成旧版审计缺少 normalized Usage、再产生 200+ 新版本调用的历史反例**。要验证 PR #413 / #416 的核心历史补偿，需在 #336 隔离测试环境另行准备旧版 V1 未归一化的成功审计及 200+ 次新版 V2 调用，记录部署 SHA 和前后持久化证据。不可据本检查通过宣称全历史恢复或生产 E2E 已通过。

实际部署验证仍需运行 `consumption.py --apply --accept`，记录 `repositoryCommit` 和经核对的部署产物哈希；历史 `deploymentCommit: null` 不能作为当前部署通过的证明。拒绝调用不计入成功 Usage 的本次验证局限于专用 Sample 的受管 Consumer 和最多 200 条来源窗口，并不等价于全平台历史消费计数。

每个治理分区保留来源范围、Owner 与五态。Quality 对两类产品仍为 NOT_APPLICABLE，
不继承物理表通过结果。源 owner/visibility 缺口、精确查询预裁决、受限角色、
normalization 故障重试、废弃/退休及浏览器完整矩阵仍需验收，输出 F-004=PARTIAL。

## R3：精确旧版 200+ 保留审计的真实 API + DB 验收工具

`historical_recovery.py` 是供 #336 在**隔离测试部署**中显式执行的验收工具，覆盖 #422 DatasetVersion 与 #426 Data Service Revision 的排他持久化审计游标。普通运行只打印计划，不连库、不登录、更不制造历史结果。

```powershell
python scripts/product/golden-sample/historical_recovery.py

# 在独立隔离环境，先完成 bootstrap.py 和 consumption.py --apply --accept。
# 另外预备同一旧版 201+ 个来源 SUCCESS 审计，至少一个第 201 条以后的
# 原始成功审计尚未形成 normalized Usage；脚本不会伪造、清理或删除数据。
$env:YAK_GOLDEN_APP_MYSQL_HOST = '127.0.0.1'
$env:YAK_GOLDEN_APP_MYSQL_PORT = '3306'
$env:YAK_GOLDEN_APP_MYSQL_DATABASE = Read-Host '应用库名'
$env:YAK_GOLDEN_APP_MYSQL_USERNAME = Read-Host '只读验收账号'
$env:YAK_GOLDEN_APP_MYSQL_PASSWORD = Read-Host '只读验收密码' -MaskInput

python scripts/product/golden-sample/historical_recovery.py --apply `
  --physical-manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json `
  --consumption-report docs/product/acceptance/golden-sample/consumption-acceptance.local.json `
  --kind BOTH --max-pages 10 `
  --output docs/product/acceptance/golden-sample/historical-recovery.local.json
```

`YAK_OPS_BASE_URL`、`YAK_OPS_USERNAME`、`YAK_OPS_PASSWORD` 与现有 R2 的登录方式相同；应用库 MySQL 账号应只有 SELECT 权限，不得使用来源 Sample 的建表账号。严格验证两个 Project 均有 `yak-golden-sample-v1` 所有权标记，且 ID 与原 R1/R2 清单一致。

- 首先由应用库只读查询验证原始审计确实 **>200**、归属于正确 Project + 产品 + 精确版本、成功状态及可归因 Consumer；必须至少有一条较早（超过首 200 条窗口）的成功审计缺少 normalized Usage。缺少场景则退出码 **2 / PENDING**，不会报告通过。
- 用 **POST** 按当前保留审计 ID 降序、每页 200 逐步补偿；每页的 `visitedAuditCount`、归一化数、响应游标和耗尽状态与实际数据库行逐项吻合。Dataset 与 Data Service 的 BIGINT 游标在 JSON 中**均是十进制字符串**（或 null），不能转换为 JavaScript Number。
- 完成后从 Usage 数据库核对**所有已保留成功审计**的 Consumer、模式、EvidenceRef 已归一化，旧记录已补齐；重新请求首个恢复页，持久化 Usage 行 ID 必须完全不变；对照 Project 发相同恢复请求不能获得或写入这些证据。
- Source 审计在跑测期间不允许有其它写入，若读前后不一致直接失败。每类最多 50 页（通过 `--max-pages` 调小）；无后台任务，无绕过原接口的 Usage INSERT/UPDATE/DELETE。报告不保存认证凭据、原始 API Key 或业务 SQL。

**限制：** 只覆盖仍保留的原始明细审计，不证明已被保留策略清理的历史恢复；普通 Impact 仍是 200 条窗口。输出 `result=REAL_API_DB_RECOVERY_VERIFIED` 仅意味着部署实例当前实际 API + 数据库事实验证通过；由于该工具不能从接口独立核验部署产物身份，`deploymentCommit=null`、`deploymentIdentity=UNVERIFIED`、`productAcceptance=PARTIAL` 均保留，不把 #336 整体验收标记为通过。浏览器、受限角色与发布产物独立补验。

离线 `test_historical_recovery.py` 包含 >200 条门槛、早期缺失证据、BIGINT、跨页、满页后空页、游标伪造、来源 SQL 精确谓词及 normalized Usage Consumer 不一致反例；由现有 Golden Consumption Evidence Contract 工作流执行。**CI 不运行真实 POST 或应用数据库**。

## R4：历史恢复拒绝路径的真实 HTTP + 数据库不变性验收

`historical_recovery_denials.py` 专门验证 #336 的两类精确版本恢复 **失败、权限、跨 Project、HTTP 方法及游标输入反例**。与 R3 不同：它不需要准备同版本的 201+ 条 SUCCESS 审计，只要求原 R2 专属 Sample 仍有至少一条真实成功 Query / Invoke 的保留审计，因此可独立执行。默认只打印 PLAN、不登录、不访问数据库。

```powershell
# R1 bootstrap 和 R2 consumption.py --apply --accept 完成后，在隔离部署执行。
# 复用前文 YAK_OPS_* 登录态设置和 YAK_GOLDEN_APP_MYSQL_* 只读账号。
python scripts/product/golden-sample/historical_recovery_denials.py

# 可选：真实的 Project 成员、但不具有 Asset.READ 权限的受限测试账号。
$env:YAK_GOLDEN_RESTRICTED_USERNAME = Read-Host '受限验收账号'
$env:YAK_GOLDEN_RESTRICTED_PASSWORD = Read-Host '受限验收密码' -MaskInput

python scripts/product/golden-sample/historical_recovery_denials.py --apply `
  --physical-manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json `
  --consumption-report docs/product/acceptance/golden-sample/consumption-acceptance.local.json `
  --kind BOTH `
  --output docs/product/acceptance/golden-sample/recovery-denials.local.json
```

**实际执行的检查**：首先通过原 R1/R2 manifest 验证两个真实 Project 均带 `yak-golden-sample-v1` 所有权标记，用只读 SQL 固定两个 Project **全部 Usage** 与精确版本成功来源审计的有界快照。控制 Project 没有对应审计时，正常 POST 必须返回 0 行、无下一游标且明确保留审计耗尽。随后逐项拒绝：

- 不携带 Cookie 的匿名 POST，即使伪造 Project Header；
- 已登录但缺失 Project Header 的 POST；GET 到恢复路径（不得靠 GET 执行投影写入）；
- 将 Dataset/ProductType 与 Data Service 路由颠倒、非 canonical 的版本 `01`；
- 非法十进制 BIGINT 游标 `0`、`01`、`-1`、`1.0`、溢出 `9223372036854775808`；
- **可选**缺乏 Asset.READ 的受限账号在真实 Project 内调用 POST；未提供受限账号则单独 `PENDING_NO_RESTRICTED_TEST_ACCOUNT`，不是通过。

每一个请求都要返回 HTTP 4xx 或确切的非成功应用错误码，**不接受 HTTP 5xx / 服务器内部异常伪装成权限拒绝**；每次请求后只读复核两个隔离 Project 的 Usage 行及来源审计未发生变化。为避免无界扫描，任一快照超过 10,000 行便 exit 2 / `PENDING`。应用数据库账号必须只有 SELECT，不会通过 SQL 改动数据；所有探针调用的也是预期拒绝或无来源的 POST，不造假消费、不删审计、不清理缓存。

运行结果只记录场景名、HTTP 状态或脱敏应用错误码和整体不变性；不输出 Cookie、Authorization、API Key、用户名、响应体或原始行。受限账号若不是同 Project 的合法成员，也可能先被 Project 门禁拒绝：该状态只能声称“受限身份被拒绝”，**不能单独证明 Asset.READ 授权细分**，成员身份与授权码需在受控环境另行查证。

**真实验收边界**：本脚本即使通过，也仅证明当前部署环境的这些负向 HTTP/SQL 路径，报告仍为 `productAcceptance=PARTIAL`、`deploymentCommit=null`、`deploymentIdentity=UNVERIFIED`。来源缺少真实 SUCCESS、Project 不受本 Sample 所有、SQL 快照超过预算时不生成 PASS。受限角色未准备、>200 历史恢复（另见 R3）、来源 GAP/存储失联后的原页重试、浏览器和可核验的部署产物 SHA 都保持 #336 的独立 PENDING。CLI 离线单元测试位于 `test_historical_recovery_denials.py`，由既有 Golden Evidence CI 执行，**不等于真实登录执行**。

## R5：真实 GAP/UNAVAILABLE 保留原游标、故障恢复与同页幂等

`historical_recovery_retry.py` 是 #336 的 **两阶段真实部署验收**，不接入生产定时任务，不生成源审计、不删除或更改任何 Usage。使用 R1/R2 已归属的 Golden Sample Project 与空对照 Project、应用数据库 **SELECT-only** 账号。只处理一个精确不可变版本的**已保留 SUCCESS 原始审计**，每次 POST 限定 `limit=1`，因此可清楚证明同一条审计被阻断、相同持久化 ID 排他游标重试成功且幂等。

**必要前置，由隔离环境 Owner 执行并独立留证：**

- 预备来源审计仍存在、Consumer/QueryId 等来源归属字段完整、目标审计 **尚无归一化 Usage**。显式记录 audit ID、DatasetVersion 或 SourceRevision、Project、来源/Consumer 归属及已授权 Console 测试账号。不得伪造源成功审计、用 SQL 删除 Usage 制造缺口或改写原审计。
- 通过**环境管理者批准的可逆故障开关/正常来源依赖断开与修复流程**，使该审计的 Consumption normalization 在一次请求内真实返回 `GAP`（含归一器的 IGNORED 归类）或 `UNAVAILABLE`。脚本本身没有故障注入器，也没有任何自动制造或修复故障的动作。
- 仍需能用独立只读 MySQL 账号读取 Project Scope 内的真实来源和 Usage。若停掉了同一套 App MySQL 导致无法取证，本 R5 **不能**给出完成结果；来源 Reader 抛 HTTP 5xx 时不能记为受控的 GAP/UNAVAILABLE，须在 #336 单独做环境故障和日志证据验收。

```powershell
python scripts/product/golden-sample/historical_recovery_retry.py

# 环境 Owner 已经有真实归一化故障；显式确认后才发送可能写 Usage 的 POST。
# 沿用 R3 的 YAK_OPS_*、YAK_GOLDEN_APP_MYSQL_* 登录与只读 App DB 环境变量。
python scripts/product/golden-sample/historical_recovery_retry.py --apply `
  --stage blocked --confirm-fault-staged `
  --kind DATASET --audit-id 9007199254740993 `
  --physical-manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json `
  --consumption-report docs/product/acceptance/golden-sample/consumption-acceptance.local.json `
  --output docs/product/acceptance/golden-sample/r5-dataset-blocked.local.json

# 必须先由 Owner 确认原故障已在外部恢复，原来源审计未变；
# 第二步使用前次 blocked report 核验同一 Project+Product+Revision+auditId+cursor。
python scripts/product/golden-sample/historical_recovery_retry.py --apply `
  --stage recovered --kind DATASET --audit-id 9007199254740993 `
  --physical-manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json `
  --consumption-report docs/product/acceptance/golden-sample/consumption-acceptance.local.json `
  --blocked-report docs/product/acceptance/golden-sample/r5-dataset-blocked.local.json `
  --output docs/product/acceptance/golden-sample/r5-dataset-recovered.local.json
```

对 Data Service Revision 用 `--kind DATA_SERVICE` 和其**真实 Invocation 行 ID**，两种来源游标都是 canonical 十进制字符串（或第一页 `null`），不会经 JavaScript Number 转换。以上 audit ID 只是调用参数示例，不能把示例数字当成实际测试结果。

- **Blocked 阶段验真**：只读核对来源记录、原始来源身份、使用者版本、两个 Project 的当前 Usage；必须在真正的 API 返回中看到 `visitedAuditCount=1`、`retryRequired=true`、`normalizedOrAlreadyPresentCount=0`、`normalizationGapCount + normalizationUnavailableCount=1`、`nextBeforeAuditId/nextBeforeInvocationId=null`、`retainedAuditExhausted=false`；POST 后整个 Project Usage 与来源审计不变。不满足条件就不能生成成功报告。注意：`normalizationGapCount` 汇总 `GAP` 与 `IGNORED`，不能从聚合字段单独宣称根因。
- **Recovered 阶段验真**：读入上述真实 blocked 报告，比较两个 Project、精确 Source、Audit ID、排他游标、来源记录 SHA-256 摘要与 EvidenceRef 摘要；确认被阻断那条 Usage 在重试前仍缺失。以**相同游标**再次 POST，使用来源 Truth 校验必须恰好新增一条 correct Consumer / Mode / SUCCESS 的 Usage，不删改其它证据。重放同一请求时 Usage 行标识及其它所有行不变，来源审计与对照 Project 也不得变化。
- 数据读取有界：两个 Project Usage 与来源审计超过 10,000 行均报告 exit 2 / `PENDING`；来源不存在或之前已有 normalized Usage 也为 `PENDING`。若错误地在故障未布置的情况下运行 blocked POST，接口**可能按正常业务路径写入 Usage**：因此必须先外部确认故障及传入 `--confirm-fault-staged`；脚本检测意外持久化变化会 **FAIL**，但不会撤销业务写入。
- **证据等级不夸大**：两个阶段分别输出 `REAL_BLOCKED_PAGE_OBSERVED`、`REAL_SAME_CURSOR_RECOVERY_VERIFIED`，仍标 `productAcceptance=PARTIAL`、`deploymentCommit=null`、`deploymentIdentity=UNVERIFIED`。本工具不证明 HTTP 5xx/Reader outage、防火墙/权限/浏览器、被保留策略清理的全历史、故障开关来源与生产恢复 SLA。部署身份和故障原因需额外独立留证。本地 `test_historical_recovery_retry.py` / GitHub Golden CI 只是**离线反例，不是实测 PASS**。

## R6：来源 Reader 失联不能伪装为空页，恢复后读回同一成功审计

`historical_recovery_reader_outage.py` 是 **R6 两阶段真实部署验收工具**，独立于 R5 的归一化 GAP/UNAVAILABLE。恢复 POST 若**来源读取本身异常**，不应输出成功的 `visitedAuditCount=0` / `retainedAuditExhausted=true`；依赖恢复后必须能以原排他审计 ID 游标读回**相同不可变版本的 1 条 SUCCESS 审计**。默认 PLAN 模式不登录、不查询数据库、不发送 POST。

**特别安全约束：** R6 要求目标保留成功审计在开始前**已有稳定的 normalized Usage**，且 R1/R2 所属两个隔离 Project 在只读应用 MySQL 可读；因此无论故障是否生效，同样的 `limit=1` 恢复调用都只应幂等读取，**整个过程不得新增、删除或改写 Usage**。若目标审计尚未 normalized，返回 `PENDING`，不能拿 R5 的缺失 Usage 样本代替。Operator 只可在隔离环境**自行采用批准的可逆来源 Reader 故障手段**，并保留受限运维日志；脚本自身没有故障开关，不停止服务、不改 DB 账号、不修改来源审计、不读取或保存 Cookie/API Key/原始审计内容。

```powershell
# 前提：已按 R1/R2 准备两个带标记的 Golden Project、真实登录账号与
# YAK_GOLDEN_APP_MYSQL_* 只读应用库账号；选已经 normalized 的真实成功来源 auditId。
python scripts/product/golden-sample/historical_recovery_reader_outage.py

# Operator 已在隔离环境按权限布置可逆 Reader 读取异常，仍能独立 SELECT 数据库。
python scripts/product/golden-sample/historical_recovery_reader_outage.py --apply `
  --stage outage --confirm-reader-outage `
  --kind DATASET --audit-id 9007199254740993 `
  --physical-manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json `
  --consumption-report docs/product/acceptance/golden-sample/consumption-acceptance.local.json `
  --output docs/product/acceptance/golden-sample/r6-dataset-outage.local.json

# Operator 外部修复并保留关联事件/应用受限日志后，重放相同审计与游标。
python scripts/product/golden-sample/historical_recovery_reader_outage.py --apply `
  --stage restored --kind DATASET --audit-id 9007199254740993 `
  --physical-manifest docs/product/acceptance/golden-sample/runtime-manifest.local.json `
  --consumption-report docs/product/acceptance/golden-sample/consumption-acceptance.local.json `
  --outage-report docs/product/acceptance/golden-sample/r6-dataset-outage.local.json `
  --output docs/product/acceptance/golden-sample/r6-dataset-restored.local.json
```

使用 `--kind DATA_SERVICE` 验证拥有该真实 Invocation SUCCESS 的精确 SourceRevision。上述数字仅为参数示例，不是实际执行记录。每种来源均使用纯十进制字符串形式的 BIGINT 排他游标（极大值且为第一条时允许首请求无游标）。

- **Stage outage**：精确 Project + Product + SourceVersion 的当前 source-owned 成功审计及 Usage 先读出快照、校验 Consumer+Mode+SUCCESS+EvidenceRef。携带合法登录态、Project、SourceVersion、limit=1、精确游标发 POST，必须出现 **HTTP 500～599** 或 HTTP 200 且 `Result.code=999`、`data=null` 的通用服务内部失败。HTTP 200/成功空页、HTTP 4xx、跳转或其他业务错误码都 **FAIL**。请求后只读核对原来源审计和两个 Project **所有 Usage** 不变。
- **Stage restored**：使用上阶段报告的 Project ID、版本、Audit ID、游标、来源记录 SHA-256、EvidenceRef SHA-256、既有 Usage ID 核对。然后发同一单行 POST，必须返回 1 条成功已归一化记录而不是成功空页、下一游标与实际审计 ID 一致；复读同页后所有 Usage 行 ID 和内容、两个 Project 来源审计均不变。
- **根因边界**：HTTP 500 或全局异常 `999` 只证明 **实际恢复端点发生了服务器级失败**，不能单独证明具体失败的是 Source Reader（也可能是其它内部依赖）。报告采用 `REAL_SERVER_FAILURE_OBSERVED`、`REAL_RESTORED_SOURCE_PAGE_VERIFIED`，保留 `SOURCE_READER_CAUSE_NOT_INDEPENDENTLY_VERIFIED`；必须由环境 Owner 用隔离故障切换操作/相关受限日志独立确认 Source Reader 原因，才能将该子场景评为最终 PASS。脚本及 CI **不**替代部署身份/浏览器/RBAC 证明。
- 任一快照超过 10,000 行、目标来源行未保留、Consumer 归属不能验证、当前 Usage 不存在或 Project 所有权不符，均不会声明 R6 PASS。失败输出不包含请求响应 body、账号、密钥或业务 SQL。即使两个阶段输出结果，`deploymentCommit=null`、`deploymentIdentity=UNVERIFIED`、`productAcceptance=PARTIAL`；真实隔离部署仍需另行执行，#336 保持 PENDING。

## 后续批次

补齐 Model、Metric、Dataset 和 F-007 MDM 样本，再执行受限角色、故障隔离和浏览器旅程。F-004 沿已批准的 Dataset/Data Service 契约推进；Metric 作为新 Data Product 来源、完整质量问题状态机、质量发布门禁和生命周期对象扩展须遵循产品治理。
补齐 Model、Metric 和 F-007 MDM 样本，再执行受限角色、故障隔离和浏览器旅程。F-004 沿已批准的 Dataset/Data Service 契约推进；Metric 作为新 Data Product 来源、完整质量问题状态机、质量发布门禁和生命周期对象扩展须遵循产品治理。
