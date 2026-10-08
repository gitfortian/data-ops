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

## 后续批次

补齐 Model、Metric、Dataset 和 F-007 MDM 样本，再执行受限角色、故障隔离和浏览器旅程。F-004 沿已批准的 Dataset/Data Service 契约推进；Metric 作为新 Data Product 来源、完整质量问题状态机、质量发布门禁和生命周期对象扩展须遵循产品治理。
补齐 Model、Metric 和 F-007 MDM 样本，再执行受限角色、故障隔离和浏览器旅程。F-004 沿已批准的 Dataset/Data Service 契约推进；Metric 作为新 Data Product 来源、完整质量问题状态机、质量发布门禁和生命周期对象扩展须遵循产品治理。
