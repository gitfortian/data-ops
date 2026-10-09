# P0 B-01～B-06 全模块正确性最终核销矩阵（2026-10-09）

> **范围**：本文件是四个综合 PR 中的 **PR 1** 的源码/测试/CI 证据账本，不是 Golden E2E 签收文件。
> **基线**：`main@3b9b5ba61172936fb944100810c60fe2b189463c`（已含 #476、#478、#479、#480、#483、#485、#486）。本 PR 的最终 head、GitHub Workflow 与合并后的 main SHA 另由 PR 实际记录决定。
> **判据**：`MERGED/CI_SUCCESS` 只说明修复已合并及对应 **PR head** 的门禁通过；`STATIC_REVIEWED` 不等于线上运行；`PENDING_RUNTIME` 不得写成 `PASS`。只记录可定位事实，不重开已修复旧缺陷，不重做其它 Agent 的架构/AI 任务。

## 一、六个工作包的证据链

| 工作包 | Truth Owner / 确认范围 | 既有已合并修复与回归位置 | 当前代码结论 | 后续实际环境边界 |
| --- | --- | --- | --- | --- |
| **B-01 Asset** | Asset Inventory、Catalog、Overview 服务端事实及前端读取投影 | [#450](https://github.com/gitfortian/data-ops/pull/450)、[#476](https://github.com/gitfortian/data-ops/pull/476)；`data-ops-ui/src/pages/data-asset/overview/index.test.tsx` | `MERGED/CI_SUCCESS`：成功空态不等于 403/500/超时；Overview 防旧 Project 异步结果回写，Catalog 目录树失联可见 | 真实账号登录、项目/权限切换与单个 Section 断连 → **PR 2/4；PENDING_RUNTIME** |
| **B-02 Metadata** | Metadata Harvest 的采集运行与 Presence/GONE 判断 | [#478](https://github.com/gitfortian/data-ops/pull/478)；`MetadataPresenceServiceTest`、`MetadataHarvestServiceTest`、`CatalogPresenceStatementTest` | `MERGED/CI_SUCCESS`：上一轮完整 SUCCESS、同 Project/Job/Scope，异常轮次与部分失败不得作为连续缺席证据；不虚构 per-job 策略 | 真实数据源骤降/部分失败/恢复与 Asset 下游反例 → **PR 2/4；PENDING_RUNTIME** |
| **B-03 Semantic** | Semantic Field、Business Domain、Business Process 版本和行更新结果 | [#479](https://github.com/gitfortian/data-ops/pull/479)；`SemanticFieldServiceTest`、`BusinessDomainServiceTest`、`BusinessProcessServiceTest` | `MERGED/CI_SUCCESS`：启停 expectedVersion CAS；Domain/Process 更新 0 行拒绝伪成功审计 | 两个实际管理员并发写、字段停用引用、跨 Project/受限角色 → **PR 2；PENDING_RUNTIME** |
| **B-04 Metric / Modeling** | Metric 草稿/Published Contract 与 Modeling 源列映射，发布与保存分离 | [#480](https://github.com/gitfortian/data-ops/pull/480)；`MetricRepositoryAdapterTest`、`MappingRepositoryAdapterTest` | `MERGED/CI_SUCCESS`：字段清空明确写 NULL；Metric 保留 Project+ID+version CAS，Mapping 保留 Project+model+target+row 条件及 0 行拒绝 | 实际 DB NULL 回读、旧版本并发、批准版本与发布指针回链 → **PR 2/4；PENDING_RUNTIME** |
| **B-05 Approval / Audit** | Approval 实例/步骤生命周期；Audit 请求/事件脱敏与操作记录 | [#483](https://github.com/gitfortian/data-ops/pull/483)；`AuditPayloadRedactorTest`；**本 PR** 增补 `ApprovalServiceTest` | `MERGED/CI_SUCCESS`（既有）+ **本 PR 待 CI**：实例读写 Project 边界、软删除过滤、分隔符敏感键脱敏；新增发现的待办/已办关联实例也必须按 Project+deleted 再过滤 | 跨项目异常关系、受限审核、真实终态回调幂等及审计事件回读 → **PR 2/4；PENDING_RUNTIME** |
| **B-06 Usage 与其它原有域** | Metric Usage 引用行不等于消费者 Source Audit / observed Usage；MDM 和 Security 的源域事实不移交给 Metric | [#485](https://github.com/gitfortian/data-ops/pull/485)、[#486](https://github.com/gitfortian/data-ops/pull/486)；`MetricUsageServiceTest`；MDM/Security 既有 `MdmRecordServiceTest`、`AccessPolicyApprovalSnapshotTest` | `MERGED/CI_SUCCESS`（Metric Usage）：legacy 同步 JDBC Savepoint 仅回滚局部替换、避免传播外层事务 rollback-only。**MDM/Security 在本批没有新增可重复的已证实 P0 缺陷，也不把既有测试视为全域审查通过。** | PostgreSQL/MySQL 实际事务、权限边界、MDM/Security 源域对账及真实消费 Usage → **PR 2/3/4；PENDING_RUNTIME** |

旧报告 [#283](https://github.com/gitfortian/data-ops/issues/283)、[#295](https://github.com/gitfortian/data-ops/issues/295) 及 [#336](https://github.com/gitfortian/data-ops/issues/336) 是候选清单，不应将其旧判断直接当成当前代码缺陷。B-06 的 MDM/Security/其它域不做无边界功能增强；对没有复现证据的项目保留 `REVIEW_LIMITED`，不是 `PASS`。

## 二、PR head 的最终 CI 回执（不是当前部署验收）

下表是查询过的既有 **PR 最终 head** GitHub workflow。历史曾失败的运行不能被抹去：例如 #483 Product Guard 曾因 PR 模板缺失而失败，后续最终通过。

| PR | head | Product Guard | Architecture Checks | 专项 |
| --- | --- | --- | --- | --- |
| [#476](https://github.com/gitfortian/data-ops/pull/476) | `79041a52d7e3` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37903339225) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37903339296) | [Data Development SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37903339335) |
| [#478](https://github.com/gitfortian/data-ops/pull/478) | `708398f3e6da` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37905388234) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37905388049) | — |
| [#479](https://github.com/gitfortian/data-ops/pull/479) | `7e22dfa1e7a8` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37906288204) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37906288281) | — |
| [#480](https://github.com/gitfortian/data-ops/pull/480) | `36f530a07729` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37907747901) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37907747933) | [Metric SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37907747938) |
| [#483](https://github.com/gitfortian/data-ops/pull/483) | `05a24071187c` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37909698531) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37909048512) | 先前 [失败记录](https://github.com/gitfortian/data-ops/actions/runs/37909454087) 保留 |
| [#485](https://github.com/gitfortian/data-ops/pull/485) | `d791853c0158` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37911180022) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37911145302) | [Metric SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37911145517) |
| [#486](https://github.com/gitfortian/data-ops/pull/486) | `8c2ab49f56c1` | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37912966282) | [SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37912966395) | [Metric SUCCESS](https://github.com/gitfortian/data-ops/actions/runs/37912966247) |

**本 PR 额外发现及修复**：审批 `todo()/handled()` 的步骤检索已经限定 Project，但旧 `toTodoViews()/toHandledViews()` 通过无项目约束的 `selectBatchIds()` 回查实例。在存在异常跨项目关系或已删除实例时，可将不应展示的实例投入结果投影。本批改为按当前 Project、未删除、ID 集合在 SQL 层限定实例，并加二次过滤；回归测试覆盖跨 Project、deleted、正常对象、查询 SQL 作用域及原 `find()` 软删除隔离。**这不是在线越权已发生的声明**，依据是已核对的源代码路径，运行回归结论以本 PR CI 为准。

## 三、四个综合 PR 的不重叠验收出口

- **PR 1（本批）**：B-01～B-06 代码正确性、既有 PR/门禁回执、确认问题修复与测试；不重做别人的模块功能，不凭静态矩阵宣称 E2E。
- **PR 2（后续独立综合批）**：双 Project、受限身份、拒绝/撤权、版本冲突与重复提交的真实账号/API/DB 验证。没有真实环境就 `BLOCKED_EVIDENCE`，不造 Mock PASS。
- **PR 3（后续独立综合批）**：真实 Dataset Query + Data Service Invoke、成功/拒绝、exact Source Audit + normalized Usage、稳定消费者/来源版本回链。
- **PR 4（后续独立综合批）**：已有 Golden R1～R8/Phase4/5 runner 的真实部署构建对应、浏览器、故障恢复、Key/IP、签字及 P0 出口裁决。

## 四、签收条件与阻塞

现阶段只有 **源码审查 + 合并回执 + PR CI**，缺少对应当前部署提交的双 Project/受限账号、真实 Query/Invoke、Source Audit/Usage、登录态浏览器/故障恢复以及 QA/Product/Release/Truth Owner 签字。请在后续三个综合 PR 内按各自范围交付或保留正式外部阻塞；**不得关闭 #336，不得填 GOLDEN=PASS 或 P0_EXIT_SIGNED**。不采集明文 Token、Cookie、API Key，不对生产环境做故障注入。

> 范围冻结：总共只按约定交付四个综合 PR；PR 1 的任何 CI/Review 返工直接修在本 PR，不能增加独立补丁 PR。
