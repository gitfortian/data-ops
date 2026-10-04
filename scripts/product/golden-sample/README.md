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

## 后续批次

补齐 Model、Metric、Dataset 和 F-007 MDM 样本，再执行受限角色、故障隔离和浏览器旅程。F-004 沿已批准的 Dataset/Data Service 契约推进；Metric 作为新 Data Product 来源、完整质量问题状态机、质量发布门禁和生命周期对象扩展须遵循产品治理。
