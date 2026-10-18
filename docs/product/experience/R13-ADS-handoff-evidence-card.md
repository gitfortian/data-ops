# R13-C3｜ADS 只读交接证据卡实现合同

状态：PR #611 实施中。本文描述本次证据卡 API/前端的实际范围，不代表所有下游域已经接通。

## API

- 路由：GET /api/v1/modeling/models/{modelId}/ads-application/evidence
- 权限：当前项目作用域 + Modeling READ；Metric 引用状态另由 Metric-owned SPI 执行 Metric READ / 当前项目核验。
- 写入行为：无。该端点不执行 SQL，不刷新或启动 Runtime，不写质量结果，不发布 Dataset/Service，不同步 Usage，不授予消费权限。
- 读取的合同证据：当前 ADS modelId、合同 revision、contractSha256、服务端 checkedAt。
- 域证据：domainCode、status、sourceStatus、sourceDomain、summary，以及可安全展示时的 evidenceId/version/digest。

## 状态派生

| 域 | 状态 | 派生依据 |
|---|---|---|
| Metric | NOT_CONFIGURED | 无已保存合同，或合同明确没有 Metric 引用 |
| Metric | UNKNOWN / LEGACY_INCOMPLETE | 旧合同只有 metricVersionId，没有完整 publication pin |
| Metric | VERIFIED | Metric provider 返回 VERIFIED_ACTIVE |
| Metric | STALE | Metric provider 返回 WITHDRAWN、NOT_CURRENTLY_PUBLISHED、REFERENCE_MISMATCH、VERSION_MISMATCH 或 DIGEST_MISMATCH |
| Metric | FORBIDDEN | Metric provider 明确拒绝当前用户读取 |
| Metric | UNKNOWN | Metric provider 返回 NOT_FOUND / UNAVAILABLE、异常或无法核验 |
| DataDev / Runtime | NOT_CONNECTED | 当前无 ADS contract revision/hash 到 TaskRevision/execution 的获批稳定关联 |
| Quality | NOT_CONNECTED | 当前无 ADS 合同到质量规则/运行的稳定关联 |
| Dataset | NOT_CONNECTED | 当前 ADS 合同没有稳定 Dataset 身份引用 |
| Data Service | NOT_CONNECTED | 当前 ADS 合同没有稳定 Data Service 引用 |
| Consumption authorization | NOT_CONNECTED | 当前没有消费方 principal 与授权决策的稳定引用 |

NOT_CONNECTED 表示集成/关联尚未建立，不表示源域没有对象或没有运行记录。NOT_CONFIGURED 只用于当前合同确实未配置的引用。请求失败显示错误，不能降级为空数组、NO_RECORD 或成功。

对于 Metric 的 NOT_FOUND / FORBIDDEN / UNAVAILABLE，不向证据卡返回发布事件 ID、版本和摘要，避免从不可核验状态额外泄露来源域信息。历史合同不自动改写或补齐。

## 工作台

证据卡位于 ADS 六步工作台的“历史修订 / 交接状态”步骤，支持手动刷新，展示：
- ADS 模型、合同修订和合同哈希；
- 各真相域状态与说明；
- Metric 核验成功时的发布事件 ID、版本和摘要短指纹；
- 最近一次证据读取时间；
- API 失败时的错误和重试入口。

## 验收

- 有效 Metric pin 必须通过 Metric-owned provider 核验后才能显示 VERIFIED。
- 旧 ID-only 合同显示 UNKNOWN / LEGACY_INCOMPLETE，不自动补齐。
- provider 不可用不能显示 VERIFIED。
- DataDev/Runtime、Quality、Dataset、Data Service、Consumption authorization 均显示 NOT_CONNECTED，直到各自稳定关联经 Owner 批准并真实实现。
- 前端请求失败不显示空证据成功态。
- 测试与 CI 通过不等于真实环境验收；C3 总 Issue #602 在所有下游域尚未闭环前保持打开。

## 下一步边界

DataDev/Runtime 的关联身份与权限提案见 Issue #609 / PR #610。PR #611 不依赖该提案建立猜测性关联，不改变 DR-07 #557，也不启用 data-ops.dwd.binding-write-authorized。
