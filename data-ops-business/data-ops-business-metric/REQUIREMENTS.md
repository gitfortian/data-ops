# Metric Requirements

> 只描述模块需要什么，不描述怎么实现。按 ticket 追加；行为变更先改本文件再写代码。

## Ticket 45：模块骨架 + 菜单权限

- 管理员进入"指标中心"菜单，能看到菜单项但功能为空。
- Maven 模块接线完成，Flyway V1 建表（7 张表），菜单注册，错误码定义。
- 契约文件集（README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW）已创建。
- SPI 接口定义（`MetricQueryApi`/`MetricUsageApi`/`MetricLineageApi`）。

## Ticket 46：指标实体 CRUD

- 管理员在"指标管理"菜单创建/编辑/删除/查看指标。
- 指标编码从名称自动生成（用户可改），创建后不可改；项目内唯一。
- 引用校验：业务域（经 `ProcessApi`）、口径标准（经 `StandardQueryApi`，kind=CALIBER）、模型（经 `ModelingModelApi`）、单位标准（经 `StandardQueryApi`，kind=UNIT）。
- 复合指标：选择子指标 + 运算方式，写入 `yak_metric_composition`。
- 血缘登记：指标创建/更新时自动写入 `yak_metric_dependency`，并调用 `LineageAssetRegistrar`/`LineageRelationRegistrar` 注册血缘。
- 审计：创建/编辑/删除全部落审计（fail-open）。
- 交互原则：统计周期默认 DAY，状态默认 ENABLED。

## Ticket 47：指标列表 + 搜索筛选

- 列表页：指标编码、名称、业务域、类型、口径引用、依赖模型、状态、负责人、更新时间、操作。
- 搜索：按编码/名称。筛选：业务域、类型、状态、负责人。
- 分页、排序，统计服务端聚合（禁止无界 list() 后内存统计）。

## Ticket 48：指标详情页

- 多 Tab 一站式视图：概览、血缘（依赖 51）、使用情况（依赖 52）、变更记录（依赖 50）、数据预览（可选）。
- 口径/单位/模型/业务域可点击跳转。
- 概览必须展示全部定义字段（业务域、业务过程、口径、单位、模型、度量表达式、过滤条件、维度、维度限定、统计周期、负责人等），引用类字段同时给出名称（服务端经 SPI 批量解析，与列表一致）。

## Ticket 49：指标标签

- 标签本体 CRUD（名称、排序）；标签可按指标挂载/移除（`assign`/`remove` 端点，前端提供入口）。
- 列表页支持按标签筛选与批量打标（后续迭代）。

## Ticket 50：指标版本

- 每次创建/更新/状态变更都写版本快照；主表 `version` 严格递增并与版本表一一对应（状态变更也记快照，杜绝漂移）。
- 版本列表 + 单版本快照查询；两版本对比与回滚为 P1 后续项。

## Ticket 51：指标血缘（复用 lineage）

- `yak_metric_dependency` 自动登记：MODEL/CALIBER/UNIT/REF_METRIC/COMPOSITION 五类；
  每行同时冗余 `dependency_code` 与 `dependency_version`（引用时刻的上游快照，供 53 比对），由服务端解析，用户无感知。
- 全局血缘图：指标注册为 METRIC Asset；模型→指标 CONSUMES 边、派生→原子 DERIVES_FROM 语义边。
- **登记时机**：指标 CRUD 事务提交后触发（afterCommit），保证血缘读到的依赖与主表一致；登记失败 fail-open 不阻断 CRUD。删除时同样在提交后清理。

## Ticket 52：指标使用

- `MetricUsageApi.record` 由消费方（dataset/dashboard/data-service）在保存引用时上报；`/{id}/usage` 提供明细。
- Metric Asset 的 Usage Section 复用 `MetricUsageApi.summary`，按引用类型展示服务端聚合计数，并明确这是已记录引用数，不等于实时 API 调用量；Asset 不存储该统计。

## Ticket 53：影响分析（主动触发）

- 比对 `dependency_version` 与上游当前版本：UP_TO_DATE/OUTDATED/REMOVED；上游版本经 SPI 批量解析。

## Phase 5：Definition Validation / Publication / governed references / Impact

- Definition Validation 对 immutable MetricVersion 快照校验 Metric 必填项、Domain / Process / Caliber / Unit / Model 引用，以及 ATOMIC / DERIVED / COMPOSITE 类型约束；复合公式必须固化 token、精确上游版本并拒绝环。
- Validation evidence 必须绑定精确 `metric_version_id` 与 snapshot digest；结果为 PASSED / FAILED / NOT_APPLICABLE，provider coverage 为 READY / UNAVAILABLE / FORBIDDEN。Unavailable / Forbidden 不得降级成通过、删除或空结果。
- Published Metric Contract 必须由有 `metric:publish` 权限的用户显式发布精确当前 MetricVersion。Draft 编辑不移动 active publication；Withdraw 追加账本事件并清除 active pointer。
- 新下游 governed reference 默认选择 active Published Metric，并保存 `(metricId, metricVersion)`；legacy null version 明确表示未知，不得伪造历史版本。
- Reference Usage、Lineage 与 Consumption Observed Usage 分开呈现。Reference Usage / Observed Usage provider 均提供 coverage 状态；Unavailable / Forbidden 不是 EMPTY 或 0。
- 删除存在 Validation evidence 或 Publication ledger 的 Metric 必须阻断；校验、发布、撤回、停用与删除围绕 Metric 行锁定，避免并发产生孤立证据或越过状态检查。
- Canonical detail 展示当前 active publication、当前 Draft drift、validation evidence、readiness gate 与受权限控制的 Validate / Publish / Withdraw 操作。

---

## 决策记录（2026-09-18 深度评审）

1. **复合指标公式 = token 流**：`compositions` 允许运算符/括号项（`operator ∈ ADD/SUB/MUL/DIV/LPAREN/RPAREN`，`subMetricId` 传 0 或 null，仅存 `expression` 符号）与引用项（`operator=REF` 且 `subMetricId` 为真实启用指标）。校验：至少 1 个 REF；REF 不得自引用；沿组成图做环检测。`sub_metric_id` 列放宽为允许 0（符号占位）。
2. **引用完整性**：删除原子指标时若被派生（ref_metric_id）或复合（composition REF）引用则阻断；停用被引用指标同样阻断并提示先处理下游；存在使用记录（`yak_metric_usage`）时删除阻断并提示。
3. **展示名解析只经 SPI**：controller/service 禁止 import semantic/modeling 的 dao/内部类；口径与单位名走 `StandardQueryApi.labels`，模型名与版本走 modeling 新增 `ModelQueryApi`，域/过程走 `ProcessApi`。详情接口与列表接口同样 enrich。
4. **ATOMIC 必填 `measureExpr` + `modelId`、DERIVED 必填 `refMetricId` 且只能引用 ATOMIC**（`validateByType`）；存量数据编辑时由表单引导补齐。
