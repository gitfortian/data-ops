# Metric Domain

## 核心概念

### 指标（Metric）

面向业务的度量，回答"业务看什么"。三种类型：

| 类型 | 语义 | 示例 |
| --- | --- | --- |
| `ATOMIC` | 原子指标，直接聚合 | GMV = SUM(order_amount) |
| `DERIVED` | 派生指标，原子 + 维度限定 | 支付GMV = SUM(order_amount) WHERE pay_status=1 |
| `COMPOSITE` | 复合指标，多指标运算 | 转化率 = 订单量 / UV |

### 不变量

1. **编码唯一**：`(project_id, metric_code)` 唯一，DB 唯一键兜底；编码创建后不可改。
2. **编码自动生成**：从名称自动生成（如 `GMV`→`gmv`），用户可改，创建后不可改。
3. **项目空间归属**：全部业务行带 `project_id`，只取服务端可信上下文（`CurrentProject`），不建物理外键。
4. **删除 = 物理删除**：指标删除前校验下游引用；存在 Definition Validation 或 Publication ledger 时禁止物理删除，保留可追溯证据。
5. **停用是软路径**：被引用的指标应先停用；停用被引用指标时给出阻断提示。
6. **口径引用**：`caliber_id` 引用 semantic 口径标准（松散 ID），`cal_rule` 从标准带出（只读）；口径未覆盖时 `cal_rule` 手填。
7. **血缘登记自动写入**：`metric_dependency` 由主表变更时自动写入（服务层保证一致性），不手工维护。
8. **复合指标组成**：`metric_composition` 记录子指标引用和运算表达式，供影响分析追溯。
9. **复合快照**：immutable `MetricVersion` 固化组成 token 及每个引用的精确子指标版本；Validation 不读取可变组成表补写历史事实。

### Definition Validation

Validation 绑定精确的 immutable `MetricVersion` 和 snapshot digest，并追加保存问题、执行主体、provider 覆盖状态与时间。结果使用 `PASSED` / `FAILED` / `NOT_APPLICABLE`；provider 覆盖单独使用 `READY` / `UNAVAILABLE` / `FORBIDDEN`。引用已确认删除或定义不完整属于 `FAILED`；provider 不可读时属于 `NOT_APPLICABLE`，不能伪装成通过或对象删除。

### Published Metric Contract

显式发布账本记录被发布的 `MetricVersion`、digest 和 publication-time gate evidence；active publication 是当前生效版本指针。创建/编辑 Draft 不自动发布，不移动 active pointer。Withdraw 追加事件并清除 active pointer，历史账本继续保留。

### 标签（Tag）

运营标记（如"核心指标""一级指标"），平铺无层级。业务域组织通过 `metric.domain_id` 引用 semantic 业务域，不重复建分类树。

### 对外引用契约（消费方视角）

- 消费方（dataset/dashboard）通过 Metric SPI 保存引用；新 governed reference 绑定 active Published Metric 的精确版本。历史仅有 metricId 的记录保留并显式表现为版本未知；展示名经 SPI 批量解析。
- 消费方**不解析**指标内部结构、不 join 本模块表。
- Reference Usage 只证明下游保存了引用；Observed Usage 由 Consumption owning domain 提供，二者不能合并。


## F-025 指标版本口径 Skill

MetricExplanationQueryApi 是 Metric-owned 授权只读投影：固定当前版本，读取不可变快照并复用 digest；仅白名单有界事实，超界/缺快照不可用。Agent 仅 gateway → metric.api，源域不反向依赖 Agent。复用 SDK 场景执行与原表单，候选仅 businessDesc，人工保存复用 expectedVersion、校验、审计和回读；验证/发布仍独立。引用校验不等于自然语言正确，真实模型验收 PENDING。合同见 docs/product/features/F-025-skill-metric-caliber.md。


## 场景辅助与 J2 原页面交接（F-027/F-028/F-029）

精确历史版本解释保持不可变快照，与当前验证/发布/影响事实分开。定义辅助只生成类型适配的白名单草稿，人工原保存、精确版本验证/发布仍独立。消费出口只使用已登记目标与版本，缺失/未知/不可用不伪造完成。

依赖仍是 Agent runtime → toolset → gateway → 源域 api；Modeling/Semantic/Metric 不依赖 Agent。复用现有保存、权限、项目与审计，无新业务状态机/事实库。精确合同见 docs/product/features 下相应 Feature。


## 指标发布前版本变更解释（F-032）

原 Metric-owned 只读投影提供当前已保存草稿与 active publication 精确版本对、白名单差异、最新精确验证和最多20条声明引用；无安全有界读取的治理/关系分区明确覆盖缺口。准备指纹绑定版本、发布事件及证据，交付重读核对；原 turn/StateStore 与源域保持唯一 owner，AI 不保存/验证/发布。依赖沿用 runtime → toolset → gateway → metric.api，Metric 内部复用原 repository，无新反向边、业务表或状态机。精确边界与真实验收待办见 docs/product/features/F-032-metric-change-review.md。
