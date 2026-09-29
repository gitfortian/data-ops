# D03 — F-001 覆盖矩阵与差距审计

日期：2026-09-23
审计基线：`data-ops/main` / `84f979a2b48068dbe6807468635a11f808591404`（包含 #35–#40）
输入：PD-001、F-001、F-001-A；main 上 #35–#40 diff；D01 执行记录及页面移交清单；D02 定向测试报告。
范围：静态产品契约、API/单元测试源码证据盘点。不执行页面功能测试、不重跑测试；不改业务代码。

## 结论摘要

F-001 已有可审阅的 Section 读取骨架、五态契约、独立查询 API、前端并发读取/状态适配，以及 Physical Table 的 Metadata Provider。#38 补充了 Section owner/evidence/权限隔离、Model 来源身份及回链上下文；#39 保留 Metadata 可空事实；#40 区分血缘节点缺失与查询异常。它们构成已合入 main 的实现切片，不等于 F-001 全量交付或页面验收通过。

可由代码直接确认的主要差距：真实业务消费读侧仍为 `UNAVAILABLE`；Provider 诊断目前可见各 `AssetSourceType` 是否注册和最近对账状态，尚不能证明 Section 覆盖能力/Section Provider 故障诊断完整；D01 登录态和所有页面场景未执行。D02 报告中的两项 lineage 架构失败是既有架构测试失败，不能归因于 #39/#40；其名称、命令和日志定位应以 D02 原报告为准，本审计不猜测补写。

## Section 覆盖矩阵

状态集合统一为 `OK / EMPTY / NOT_APPLICABLE / UNAVAILABLE / PERMISSION_DENIED`。下表“适用性”是 F-001-A MVP 矩阵；“当前实现”是源码路径可见的返回路径，不声称样本运行结果。Security 在产品矩阵为“可适用”，但当前代码对缺少分类记录返回 `UNAVAILABLE` 文案，是否应为 `EMPTY`/`NOT_APPLICABLE` 属需核对的契约差距。

| Section | 类型 | 契约适用性 | Truth Owner | 当前静态实现/证据状态 | 专业域回链 | Provider 诊断状态 |
|---|---|---|---|---|---|---|
| Overview | Table / Model / Metric / Dataset | 必须 | Asset | 可构造 `OK` 身份摘要；API/单测代码可查，页面未验 | 无额外域动作 | N/A：Asset 自有，不由 Section Provider 提供 |
| Technical Metadata | Table | 必须 | Metadata | 有 Provider；Metadata 无实体时 `EMPTY`，有实体时 `OK`；缺 Provider/异常的外层处理为 `UNAVAILABLE` | `OK` 时提供 Metadata 工作台动作；返回上下文/实际 UX 未验 | 仅能从 Bean/Provider 静态注册和缺失时 `UNAVAILABLE` 判断；没有此 Section 专属运营诊断证据 |
| Technical Metadata | Model / Metric / Dataset | 明确不适用 | Metadata | `NOT_APPLICABLE` 分支；#38 对 Model 保留来源身份 | 不显示技术元数据动作 | N/A：按适用性短路，不应查询 Provider |
| Quality | Table | 必须 | Quality | 依赖缺失或定位/查询失败为 `UNAVAILABLE`；确认未纳管为 `EMPTY`；有监控为 `OK`。静态单测源码覆盖部分分支 | 目标为 Quality 专业页；当前 AssetController `sectionActions` 未为 QUALITY 定义动作，差距 | Quality Reader 缺失有原因；无可见健康状态/最后成功时间诊断 |
| Quality | Model / Metric / Dataset | 明确不适用 | Quality | `NOT_APPLICABLE` 短路 | 无 | N/A：适用性短路 |
| Security | Table / Model / Metric / Dataset | 可适用 | Security | 有分类为 `OK`；权限拒绝为 `PERMISSION_DENIED`；服务缺失/异常为 `UNAVAILABLE`。空分类当前代码文字是“未定级，或无对应对象”并返回 `UNAVAILABLE`，需统一空事实语义 | 目标 Security 专业页；当前 AssetController 没有 SECURITY 动作 | API 可检查读侧是否装配；无覆盖/失败运营诊断证据 |
| Lineage | Table / Model / Metric / Dataset | 必须 | Lineage | 未登记节点 `EMPTY`（#40）；查询失败/服务缺失 `UNAVAILABLE`；图存在 `OK` | `OK` 时回到图谱且传 Asset 上下文；页面未验 | 静态检查依赖是否可用；无专属注册覆盖诊断页证据 |
| Usage | Table / Model / Metric / Dataset | 必须 | 联邦归属：Asset 页面活动、Lineage 结构依赖、消费域业务消费 | 页面活动和结构引用子事实分别携带状态；Section 主体仍可 `OK`，`businessConsumption` 固定 `UNAVAILABLE`。静态可核 | 无已实现消费域入口证据 | 业务消费未接入明确可见；没有逐消费域 Provider 诊断 |
| Lifecycle / TTL | Model | 必须 | Lifecycle | 策略未命中 `EMPTY`，TTL API 缺失/解析异常 `UNAVAILABLE`，有策略 `OK` | 需 Lifecycle 专业处理入口；当前 AssetController 未提供 LIFECYCLE 动作 | 能区分 API 未装配/查询失败提示；无运营诊断记录 |
| Lifecycle / TTL | Table / Metric / Dataset | 明确不适用 | Lifecycle | `NOT_APPLICABLE` 短路 | 无 | N/A：适用性短路 |
| Governance | Table / Model / Metric / Dataset | 必须 | Asset | `OK` 返回治理状态、owner、目录等；代码可查 | Asset 自身治理动作/页面范围，不是跨域回链 | N/A：Asset 自有 |

## 缺口清单与排序

证据标签：**静态/API**=源码接口、分支或契约可复核；**单测**=已有测试源码，但本任务没有执行；**E2E**=页面及登录态运行证据。`未验证` 不代表失败。

| 优先级 | 缺口与用户结果 | 现行契约 | E2E / API / 单元证据状态 | 依赖 / 建议 |
|---|---|---|---|---|
| P0 | 登录态页面验收未执行。用户无法据此确认身份、owner、权限和分区状态能被正确理解 | D01 明确不宣称验收通过；F-001-A 要求场景 A–E 与专业回链 | E2E 未执行；API 静态存在；D01 有代码/测试源码核验，不等于运行 | 依赖可用前后端和真实应用账号/权限。执行 D01 移交清单后记录环境、截图及脱敏响应 |
| P0 | Table/Model/Metric/Dataset 全覆盖与 Provider coverage 诊断仍缺可交付证明。用户/管理员不能判断类型支持哪些 Section、缺 Provider 是配置问题还是故障 | F-001 §6.J 要求 source type 注册、失败/缺失、支持 Section 可诊断；F-001-A §9 禁止 Provider 成为 Truth Owner | E2E 未执行；API 静态有 `/reconcile/status` 的 source provider registered/lastRun；没有 Section 覆盖矩阵/Section Provider 诊断的证据；对应单测未核实 | 依赖产品/运维确认诊断呈现位置；优先补覆盖盘点证据和受控 API/管理端验收，不改变业务 Truth |
| P1 | Usage 的真实业务消费尚不可见。用户仍无法回答“哪些下游产品真实使用” | F-001-A §7 区分页面活动、结构依赖、真实消费；真实消费归消费域，Asset 只聚合 | API 静态代码明确返回 `businessConsumption=UNAVAILABLE`；页面呈现未验；D02 不涉及 | 依赖 Dashboard / Data Service / Agent / Dataset 等消费域提供只读来源与权限。按域逐步接入并验证，不新建统一 Usage Truth |
| P1 | Quality / Security / Lifecycle 专业域后续动作缺少 API 回链动作证据 | F-001-A §3、§11 要求可操作事实回到 Truth Owner；AssetController 当前仅明确生成 Technical Metadata 与 Lineage actions | 静态 Controller switch 可复核；API 未运行；页面未验；对应回链单测未确认 | 各域稳定 detail URL、对象 identity、权限规则；逐域实现/审查回链 |
| P1 | Security 对“未定级/找不到对象”返回 `UNAVAILABLE`，用户难以区分确认无分类与读取失败 | 五态要求“确认空”与“系统不知道”分离；矩阵规定按事实 `OK/EMPTY/UNAVAILABLE/PERMISSION_DENIED` | 静态分支可复核；当前无法区分返回 null 是确认空还是键不匹配；API/E2E 未执行；相关测试未运行 | Security owner 确认 read-side 能否区分无记录和对象映射缺失，再修正结果语义/测试 |
| P1 | Section 级 `ownerDomain / provenance / evidence / actions / capability` 在部分通用映射分支可能不完整；需验证实际生产响应一致性 | F-001-A §8 及 approved `SectionContract` 要求这些字段语义；#38/#39 修复边界案例 | 类型和静态映射存在；#39 有 nullable catalog facts 定向单测改动；所有 API/E2E 未执行。本审计不将 D04 Model 来源字段契约问题纳入实现 | 依赖 API contract review/受控响应；另立 D04 跟踪 Model 来源字段契约，不在 D03 扩大范围 |
| P2 | Metadata 返回上下文动作使用目录入口，并由 UI 解析 returnAssetId；真实往返及身份保持未验 | F-001-A §11：进入 owning domain 并能返回原 Asset identity | #38/#40 对回链上下文的代码切片可查；UI、URL、浏览器历史未验 | 依赖页面验收；按 D01 的专业回链场景记录从 Asset→专业域→Asset 的完整证据 |
| P2 | Table Technical Metadata 实体缺失时的 `EMPTY` 与其 evidence/provenance 语义需 API 级核实 | `EMPTY` 必须表示查询成功确认无记录；来源信息应诚实 | Metadata Provider 源码将空记录转成 EMPTY；它的 response helper 仍构造 provenance；未运行 Provider 测试/API；这不是 #39 的空字段差异 | 核对 API 返回并确认“无记录”与“查询成功”的证据表达；不改字段契约 |

## 页面测试移交清单

以下均为测试计划，不是执行结果。**D01 页面、登录态状态：未执行。执行者待分配；实际结果未验证。** 页面测试只由后续执行者完成，本次没有运行页面功能测试。

| 优先级 / 页面场景 | 前置条件 | 测试数据 | 步骤 | 预期 | 需采集证据 | 执行者 / 状态 |
|---|---|---|---|---|---|---|
| P0 Table Asset 身份、技术元数据、Quality、Security、Lineage、Usage、Governance | 登录账号具备 `data-asset:read` 及需验证的域权限；前后端可用 | 已登记的 Physical Table，Metadata 实体存在；Quality 样本各准备已纳管与未纳管 | 从 Asset Catalog 搜索并打开；逐个打开八 Section；使用可用专业动作跳转并返回 | 身份/Owner来源清楚；正确区分 `OK/EMPTY/UNAVAILABLE/PERMISSION_DENIED`；各域事实标明 owner；回链对象正确 | 登录态截图、URL、每分区脱敏 API 响应/HTTP 状态、动作目标与返回后 Asset ID | 待分配；未执行；实际未验证 |
| P0 Model Technical Metadata / Quality 不适用、Lifecycle 可用 | 登录有 `data-asset:read`、`modeling:read`；Lifecycle read 权限按需 | D01 Asset 8 / Model 49 可作为只读候选；勿修改现有事实 | 打开详情；检查来源身份；依次查看 Technical Metadata、Quality、Lifecycle | 前两者按矩阵 `NOT_APPLICABLE`；Lifecycle 按实际策略状态；Model 来源事实及 Q2 不扩展为本次实现要求 | 截图、各 Section 脱敏响应，确认 status/reason/owner/provenance | 待分配；未执行；实际未验证 |
| P0 Metric / Dataset 的不适用和跨域 Section | 有各自 read 权限 | 1 个有效 Metric Asset、1 个有效 Dataset Asset | 分别从目录打开；核对 Technical Metadata、Quality、Lifecycle、Security、Lineage、Usage、Governance | 三类明确不适用返回 N/A；其余按真实域事实诚实呈现，业务消费未接入明确显示不可用 | 页面截图、各 Section 脱敏响应、assetKey 与专业域目标 | 待分配；未执行；实际未验证 |
| P0 权限拒绝和隔离 | 已登录的低权限账号；另备域权限不完整账号 | 同一 Table 或 Security/Metadata 受保护事实 | 分别打开资产和受限 Section；查看页面和网络响应 | 无资产读取权限时受控；域权限拒绝不泄漏 summary/provenance/evidence/actions；表现符合 owning domain 策略 | 用户权限清单（不含凭据）、页面截图、HTTP 状态及响应字段审查 | 待分配；未执行；实际未验证 |
| P1 Lineage 节点缺失与真实查询故障 | 可控测试环境、可观察 Lineage 返回；不能污染生产 | 一个未登记 Lineage 的 Asset；一个受控 Lineage 查询失败案例 | 各自打开 Lineage Section；必要时通过测试环境故障开关制造查询失败 | 节点缺失是 `EMPTY`；查询故障是 `UNAVAILABLE`；其它 Section 可继续使用 | 样本身份、故障条件、脱敏 API、页面截图、恢复后复测记录 | 待分配；未执行；实际未验证 |
| P1 Usage 子事实与业务消费缺口 | 有浏览记录/结构关系的 Asset；消费域测试数据若已可用 | 已浏览且有/无一跳下游关系；当前无消费域接入时记录现状 | 打开 Usage；核对 Activity、结构引用、Business Consumption 三子项 | Activity 不冒充真实消费；结构关系由 Lineage 标注；未接入业务消费继续明确不可用 | 截图、响应各子项 ownerDomain/status/reason、对应事实来源 | 待分配；未执行；实际未验证 |
| P1 Security 空事实与依赖故障表达 | Security 测试账号和受控数据；允许的依赖故障模拟 | 一个已分类对象、一个确定无分类对象、一个对象映射缺失或安全服务不可用样本 | 分别打开 Security Section，对照 API 响应和事实来源 | 有事实 `OK`；无权限 `PERMISSION_DENIED`；服务失败 `UNAVAILABLE`；确定空值不得与映射未知混淆 | 截图、脱敏响应、Security owner 对空值解释、依赖状态 | 待分配；未执行；实际未验证 |
| P1 专业域回链往返 | 具备 Asset 与目标专业域访问权限；专业页可打开 | Table 的 Metadata/Lineage，以及存在目标链接的 Quality/Security/Lifecycle 样本 | 从 Asset 点击每个可用动作；核对目标 identity；使用返回动作回到原资产 | 链接目标匹配事实对象；返回保留原 Asset 身份；不可用/N/A/拒绝时没有虚假成功动作 | 点击前后截图、URL、assetKey/source identity、浏览器返回及页面状态 | 待分配；未执行；实际未验证 |
| P1 单 Section 依赖故障隔离 | 测试环境可以只对一个 owning domain 注入故障 | 任选一个有多个可用 Section 的资产 | 先正常加载；使一个域依赖超时；刷新/重试并观察其余 Section | 目标 Section 有原因地 `UNAVAILABLE`；其它分区仍正常；不把故障显示成 EMPTY | 故障注入记录、各分区请求时序/脱敏响应、故障前中后截图 | 待分配；未执行；实际未验证 |
| P2 Provider coverage 诊断 | 管理/盘点权限；可查看 Provider 状态页面/API | 各 source type 有 Provider、无 Provider、最近对账失败/成功的受控状态 | 打开盘点 Provider 状态；对照 Section Provider/适用覆盖的可诊断信息 | 注册与否、最后对账状态和 Section 支持范围可区分；失败不解释成无数据 | 页面/脱敏 API 响应、source type 对照表、失败原因/时间 | 待分配；未执行；实际未验证 |

## D01 / D02 记录边界

- D01 运行环境与登录态验收：前后端不可用、没有真实 DataOps 应用会话；数据库只读核对不等于页面验收。页面和登录态一律记为未执行。具体样本、权限、证据要求见 `asset-understanding-user-info.md` 的 D01 执行记录及页面测试移交清单。
- D02-B 干净基线复现：在未修改 worktree `C:\Users\tianxy105\AppData\Local\Temp\yak-ops-d02b-baseline`（HEAD=`84f979a2b48068dbe6807468635a11f808591404`，执行前 `git status` 干净）分别运行以下定向命令，均复现失败：
  - `./mvnw -pl data-ops-business/data-ops-business-lineage -Dtest=LineageMavenDependencyBoundaryTest test`：`LineageMavenDependencyBoundaryTest.sharedDatabaseModuleOwnsMybatisAndFlywayRuntimeAdapters` 失败。断言 `data-ops-business-datasource` 中 `mybatis-plus-jsqlparser-4.9` 应为 `runtime`，实际为 `compile`；同类其余 2 项通过。
  - `./mvnw -pl data-ops-business/data-ops-business-lineage -Dtest=LineagePublicApiBoundaryTest test`：`LineagePublicApiBoundaryTest.neighboringProductionModulesUseOnlyDeclaredPublicTypes` 失败。报告 Modeling 的 `ModelingLineageController.java` 导入 `io.yak.ops.business.lineage.controller.v1.converter.LineageViewConverter`，越过 Lineage 公开 API 边界；同类其余 2 项通过。
  复现发生在 #39/#40 合入后的目标基线上，但失败断言针对既有 datasource Maven scope 与 Modeling→Lineage 包边界，不是 #39 的 nullable Metadata facts 或 #40 的“缺失节点 vs 查询失败”改动；因此不能归因于 #39/#40。该复现只运行 lineage 架构单测，没有执行页面测试。
- #39 仅修复 Metadata catalog nullable facts 的保留语义及相应定向单测，不涵盖 Model 来源字段产品契约；D04 该契约问题保持独立跟踪，不纳入 D03 实现或差距“已修复”结论。

## 文档状态建议

| 对象 | 状态建议 | 说明 |
|---|---|---|
| PD-001 | `ACCEPTED` | 产品决策已接受；不代表 F-001 实现完成 |
| F-001 / F-001-A | `APPROVED` | 产品契约已批准；契约仍是验收依据 |
| #35–#40 | `merged / 进入 main` | 代码切片进入 `data-ops/main`；不能据此标成 F-001 `SHIPPED` |
| D01 | `部分完成` | 静态/只读核对已记录；页面与登录态未执行，验收未通过 |
| D02 定向证据 | `已报告；本任务未重跑` | 两项 lineage 架构失败单独记录，不归因 #39/#40 |
| D03 本审计 | `待审阅` | 审阅覆盖矩阵和优先级后再更新产品追踪状态 |
| F-001 整体 | `未验收 / 不可标 SHIPPED` | E2E、页面、登录态、Usage 真消费与 Provider coverage 证据尚不充分 |

本文件为产品追踪/审计文档，不改变业务代码或现行契约，不新增产品概念、状态机、入口或 API 契约。
